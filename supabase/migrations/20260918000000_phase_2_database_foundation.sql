begin;

create schema moneybook_private;
revoke all on schema moneybook_private from public, anon, authenticated, service_role;
grant usage on schema moneybook_private to authenticated;
alter default privileges in schema moneybook_private revoke execute on functions from public;

create extension if not exists pgcrypto with schema extensions;

create table public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    display_name text,
    created_at timestamptz not null default now()
);

create table public.households (
    id uuid primary key default gen_random_uuid(),
    name text not null check (char_length(btrim(name)) between 1 and 80),
    created_by uuid not null references auth.users(id),
    created_at timestamptz not null default now()
);

create table public.household_members (
    household_id uuid not null references public.households(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    role text not null check (role in ('OWNER', 'MEMBER')),
    joined_at timestamptz not null default now(),
    primary key (household_id, user_id),
    unique (user_id),
    -- Only OWNER and MEMBER are valid, and each role occurs at most once.
    -- This makes three members impossible even under concurrent requests.
    unique (household_id, role)
);

create table public.invitations (
    id uuid primary key default gen_random_uuid(),
    household_id uuid not null references public.households(id) on delete cascade,
    code text not null unique check (code ~ '^[0-9A-F]{12}$'),
    created_by uuid not null references auth.users(id),
    expires_at timestamptz not null,
    used_at timestamptz,
    created_at timestamptz not null default now(),
    check (expires_at > created_at)
);

alter table public.profiles enable row level security;
alter table public.households enable row level security;
alter table public.household_members enable row level security;
alter table public.invitations enable row level security;

revoke all on public.profiles, public.households, public.household_members, public.invitations
    from public, anon, authenticated;
grant select on public.profiles, public.households, public.household_members to authenticated;
grant update (display_name) on public.profiles to authenticated;

create function moneybook_private.create_profile()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    insert into public.profiles(id) values (new.id);
    return new;
end;
$$;
revoke all on function moneybook_private.create_profile() from public, anon, authenticated;
create trigger create_moneybook_profile
    after insert on auth.users
    for each row execute function moneybook_private.create_profile();

-- Backfill users that existed before this migration.
insert into public.profiles(id)
select id from auth.users
on conflict (id) do nothing;

-- This caller-only helper prevents recursive household_members RLS evaluation.
create function moneybook_private.current_household_id()
returns uuid language sql stable security definer set search_path = '' as $$
    select household_id
    from public.household_members
    where user_id = auth.uid()
$$;
revoke all on function moneybook_private.current_household_id() from public, anon, authenticated;
grant execute on function moneybook_private.current_household_id() to authenticated;

create policy profiles_select_own on public.profiles
    for select to authenticated using (id = (select auth.uid()));
create policy profiles_update_own on public.profiles
    for update to authenticated
    using (id = (select auth.uid())) with check (id = (select auth.uid()));
create policy households_select_own on public.households
    for select to authenticated
    using (id = (select moneybook_private.current_household_id()));
create policy household_members_select_own on public.household_members
    for select to authenticated
    using (household_id = (select moneybook_private.current_household_id()));
-- Invitations intentionally have no client SELECT policy. Codes are consumed by RPC.

create function public.create_household(name text)
returns uuid language plpgsql security definer set search_path = '' as $$
declare
    caller uuid := auth.uid();
    household uuid;
    clean_name text := btrim(name);
begin
    if caller is null then
        raise exception using errcode = '42501', message = 'Authentication required';
    end if;
    perform 1 from auth.users where id = caller for update;
    if not found then
        raise exception using errcode = '42501', message = 'Authentication required';
    end if;
    if exists (select 1 from public.household_members where user_id = caller) then
        raise exception using errcode = 'P0001', message = 'Already in a household';
    end if;
    if clean_name is null or char_length(clean_name) not between 1 and 80 then
        raise exception using errcode = '22023', message = 'Household name must contain 1 to 80 characters';
    end if;
    insert into public.households(name, created_by)
        values (clean_name, caller) returning id into household;
    insert into public.household_members(household_id, user_id, role)
        values (household, caller, 'OWNER');
    return household;
end;
$$;

create function public.create_invitation()
returns text language plpgsql security definer set search_path = '' as $$
declare
    caller uuid := auth.uid();
    household uuid;
    invitation_code text;
begin
    if caller is null then
        raise exception using errcode = '42501', message = 'Authentication required';
    end if;
    select household_id into household
    from public.household_members
    where user_id = caller and role = 'OWNER';
    if household is null then
        raise exception using errcode = '42501', message = 'Household owner required';
    end if;
    perform 1 from public.households where id = household for update;
    if (select count(*) from public.household_members where household_id = household) >= 2 then
        raise exception using errcode = 'P0001', message = 'Household is full';
    end if;
    invitation_code := upper(substr(encode(extensions.gen_random_bytes(9), 'hex'), 1, 12));
    insert into public.invitations(household_id, code, created_by, expires_at)
        values (household, invitation_code, caller, clock_timestamp() + interval '24 hours');
    return invitation_code;
end;
$$;

create function public.join_household(code text)
returns uuid language plpgsql security definer set search_path = '' as $$
declare
    caller uuid := auth.uid();
    normalized_code text := upper(btrim(code));
    household uuid;
    invitation_id uuid;
begin
    if caller is null then
        raise exception using errcode = '42501', message = 'Authentication required';
    end if;
    perform 1 from auth.users where id = caller for update;
    if not found then
        raise exception using errcode = '42501', message = 'Authentication required';
    end if;
    if exists (select 1 from public.household_members where user_id = caller) then
        raise exception using errcode = 'P0001', message = 'Invitation is invalid or unavailable';
    end if;
    select household_id into household
    from public.invitations
    where invitations.code = normalized_code;
    if household is null then
        raise exception using errcode = 'P0001', message = 'Invitation is invalid or unavailable';
    end if;
    -- Serialize all joins for one household, then re-check the invitation.
    perform 1 from public.households where id = household for update;
    select id into invitation_id
    from public.invitations
    where invitations.code = normalized_code
      and household_id = household
      and used_at is null
      and expires_at > clock_timestamp()
    for update;
    if invitation_id is null
       or (select count(*) from public.household_members where household_id = household) >= 2 then
        raise exception using errcode = 'P0001', message = 'Invitation is invalid or unavailable';
    end if;
    insert into public.household_members(household_id, user_id, role)
        values (household, caller, 'MEMBER');
    update public.invitations set used_at = clock_timestamp() where id = invitation_id;
    return household;
exception
    when unique_violation then
        raise exception using errcode = 'P0001', message = 'Invitation is invalid or unavailable';
end;
$$;

revoke all on function public.create_household(text), public.create_invitation(),
    public.join_household(text) from public, anon, authenticated;
grant execute on function public.create_household(text), public.create_invitation(),
    public.join_household(text) to authenticated;

commit;
