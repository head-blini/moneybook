begin;

create table public.categories (
    id uuid primary key default gen_random_uuid(),
    household_id uuid not null references public.households(id) on delete cascade,
    name text not null check (char_length(btrim(name)) between 1 and 40),
    transaction_type text not null check (transaction_type in ('EXPENSE', 'INCOME')),
    is_active boolean not null default true,
    created_by uuid not null references auth.users(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index categories_household_name_type_key
    on public.categories (household_id, lower(btrim(name)), transaction_type);

create table public.cards (
    id uuid primary key default gen_random_uuid(),
    household_id uuid not null references public.households(id) on delete cascade,
    owner_user_id uuid not null references auth.users(id),
    display_name text not null check (char_length(btrim(display_name)) between 1 and 40),
    issuer_code text not null check (issuer_code ~ '^[A-Z0-9_]{2,40}$'),
    last_four text check (last_four is null or last_four ~ '^[0-9]{4}$'),
    performance_enabled boolean not null default false,
    notification_collection_enabled boolean not null default false,
    monthly_target_amount bigint not null default 0 check (monthly_target_amount >= 0),
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index cards_household_idx on public.cards (household_id);
create index cards_owner_idx on public.cards (owner_user_id);

create table public.transactions (
    id uuid primary key default gen_random_uuid(),
    household_id uuid not null references public.households(id) on delete cascade,
    created_by uuid not null references auth.users(id),
    paid_by uuid not null references auth.users(id),
    type text not null check (type in ('EXPENSE', 'INCOME')),
    scope text not null check (scope in ('PERSONAL', 'SHARED')),
    amount bigint not null check (amount > 0),
    category_id uuid not null references public.categories(id),
    card_id uuid references public.cards(id),
    merchant text check (merchant is null or char_length(merchant) <= 120),
    memo text check (memo is null or char_length(memo) <= 500),
    transaction_at timestamptz not null,
    source text not null check (source in ('MANUAL', 'NOTIFICATION', 'IMPORT')),
    status text not null default 'CONFIRMED' check (status in ('PENDING', 'CONFIRMED', 'CANCELED')),
    performance_included_override boolean,
    notification_provider text,
    notification_fingerprint text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check (performance_included_override is null or card_id is not null),
    check (source <> 'NOTIFICATION' or notification_fingerprint is not null)
);

create index transactions_household_date_idx
    on public.transactions (household_id, transaction_at desc);
create index transactions_payer_date_idx
    on public.transactions (paid_by, transaction_at desc);
create index transactions_card_date_idx
    on public.transactions (card_id, transaction_at desc) where card_id is not null;
create unique index transactions_notification_fingerprint_key
    on public.transactions (paid_by, notification_fingerprint)
    where source = 'NOTIFICATION' and notification_fingerprint is not null;

create table public.transaction_refunds (
    id uuid primary key default gen_random_uuid(),
    transaction_id uuid not null references public.transactions(id) on delete cascade,
    amount bigint not null check (amount > 0),
    status text not null default 'CONFIRMED' check (status in ('PENDING', 'CONFIRMED', 'CANCELED')),
    source text not null check (source in ('MANUAL', 'NOTIFICATION', 'IMPORT')),
    idempotency_key uuid not null,
    refunded_at timestamptz not null,
    created_by uuid not null references auth.users(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (transaction_id, idempotency_key)
);

create index transaction_refunds_transaction_idx
    on public.transaction_refunds (transaction_id, status);

create table public.card_performance_excluded_categories (
    card_id uuid not null references public.cards(id) on delete cascade,
    category_id uuid not null references public.categories(id),
    created_by uuid not null references auth.users(id),
    created_at timestamptz not null default now(),
    primary key (card_id, category_id)
);

alter table public.categories enable row level security;
alter table public.cards enable row level security;
alter table public.transactions enable row level security;
alter table public.transaction_refunds enable row level security;
alter table public.card_performance_excluded_categories enable row level security;

revoke all on public.categories, public.cards, public.transactions,
    public.transaction_refunds, public.card_performance_excluded_categories
    from public, anon, authenticated;

grant select, insert, delete on public.categories to authenticated;
grant update (name, is_active) on public.categories to authenticated;
grant select, insert, delete on public.cards to authenticated;
grant update (display_name, issuer_code, last_four, performance_enabled,
    notification_collection_enabled, monthly_target_amount, is_active)
    on public.cards to authenticated;
grant select, insert, delete on public.transactions to authenticated;
grant update (type, amount, category_id, card_id, merchant, memo, transaction_at,
    performance_included_override) on public.transactions to authenticated;
grant select on public.transaction_refunds to authenticated;
grant select, insert, delete on public.card_performance_excluded_categories to authenticated;

create function moneybook_private.set_updated_at()
returns trigger language plpgsql set search_path = '' as $$
begin
    new.updated_at := clock_timestamp();
    return new;
end;
$$;
revoke all on function moneybook_private.set_updated_at() from public, anon, authenticated;

create trigger categories_set_updated_at before update on public.categories
    for each row execute function moneybook_private.set_updated_at();
create trigger cards_set_updated_at before update on public.cards
    for each row execute function moneybook_private.set_updated_at();
create trigger transactions_set_updated_at before update on public.transactions
    for each row execute function moneybook_private.set_updated_at();
create trigger transaction_refunds_set_updated_at before update on public.transaction_refunds
    for each row execute function moneybook_private.set_updated_at();

create function moneybook_private.validate_category()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    new.name := btrim(new.name);
    if not exists (
        select 1 from public.household_members
        where household_id = new.household_id and user_id = new.created_by
    ) then
        raise exception using errcode = '23514', message = 'Category creator must belong to its household';
    end if;
    if tg_op = 'UPDATE'
       and (new.household_id, new.created_by) is distinct from (old.household_id, old.created_by) then
        raise exception using errcode = '42501', message = 'Category ownership fields are immutable';
    end if;
    return new;
end;
$$;
revoke all on function moneybook_private.validate_category() from public, anon, authenticated;
create trigger validate_category before insert or update on public.categories
    for each row execute function moneybook_private.validate_category();

create function moneybook_private.validate_card()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    new.display_name := btrim(new.display_name);
    new.issuer_code := upper(btrim(new.issuer_code));
    if not exists (
        select 1 from public.household_members
        where household_id = new.household_id and user_id = new.owner_user_id
    ) then
        raise exception using errcode = '23514', message = 'Card owner must belong to its household';
    end if;
    if tg_op = 'UPDATE'
       and (new.household_id, new.owner_user_id) is distinct from (old.household_id, old.owner_user_id) then
        raise exception using errcode = '42501', message = 'Card ownership fields are immutable';
    end if;
    return new;
end;
$$;
revoke all on function moneybook_private.validate_card() from public, anon, authenticated;
create trigger validate_card before insert or update on public.cards
    for each row execute function moneybook_private.validate_card();

create function moneybook_private.validate_transaction()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
    category_household uuid;
    category_type text;
    card_household uuid;
    card_owner uuid;
    confirmed_refunds bigint;
begin
    if tg_op = 'UPDATE'
       and (new.household_id, new.created_by, new.paid_by, new.scope)
           is distinct from (old.household_id, old.created_by, old.paid_by, old.scope) then
        raise exception using errcode = '42501', message = 'Transaction ownership fields are immutable';
    end if;

    if not exists (
        select 1 from public.household_members
        where household_id = new.household_id and user_id = new.created_by
    ) or not exists (
        select 1 from public.household_members
        where household_id = new.household_id and user_id = new.paid_by
    ) then
        raise exception using errcode = '23514', message = 'Transaction users must belong to its household';
    end if;

    select household_id, transaction_type into category_household, category_type
    from public.categories where id = new.category_id;
    if category_household is distinct from new.household_id or category_type is distinct from new.type then
        raise exception using errcode = '23514', message = 'Category must match transaction household and type';
    end if;

    if new.card_id is not null then
        select household_id, owner_user_id into card_household, card_owner
        from public.cards where id = new.card_id;
        if card_household is distinct from new.household_id then
            raise exception using errcode = '23514', message = 'Card must belong to transaction household';
        end if;
        if card_owner is distinct from new.paid_by then
            raise exception using errcode = '23514', message = 'Card owner must match transaction payer';
        end if;
    end if;

    if tg_op = 'UPDATE' and new.amount is distinct from old.amount then
        select coalesce(sum(r.amount) filter (where r.status = 'CONFIRMED'), 0)
        into confirmed_refunds
        from public.transaction_refunds r where r.transaction_id = old.id;
        if confirmed_refunds > new.amount then
            raise exception using errcode = '23514', message = 'Transaction amount cannot be less than confirmed refunds';
        end if;
        if old.status in ('CONFIRMED', 'CANCELED') then
            new.status := case when confirmed_refunds = new.amount then 'CANCELED' else 'CONFIRMED' end;
        end if;
    end if;
    return new;
end;
$$;
revoke all on function moneybook_private.validate_transaction() from public, anon, authenticated;
create trigger validate_transaction before insert or update on public.transactions
    for each row execute function moneybook_private.validate_transaction();

create function moneybook_private.validate_refund()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
    original_amount bigint;
    original_type text;
    active_other bigint;
begin
    select amount, type into original_amount, original_type
    from public.transactions
    where id = new.transaction_id
    for update;
    if original_amount is null or original_type <> 'EXPENSE' then
        raise exception using errcode = '23514', message = 'Refund requires an expense transaction';
    end if;
    if tg_op = 'UPDATE' and new.transaction_id is distinct from old.transaction_id then
        raise exception using errcode = '42501', message = 'Refund transaction is immutable';
    end if;
    select coalesce(sum(amount), 0) into active_other
    from public.transaction_refunds
    where transaction_id = new.transaction_id
      and status in ('PENDING', 'CONFIRMED')
      and (tg_op = 'INSERT' or id <> old.id);
    if new.status in ('PENDING', 'CONFIRMED') and active_other + new.amount > original_amount then
        raise exception using errcode = '23514', message = 'Active refunds cannot exceed transaction amount';
    end if;
    return new;
end;
$$;
revoke all on function moneybook_private.validate_refund() from public, anon, authenticated;
create trigger validate_refund before insert or update on public.transaction_refunds
    for each row execute function moneybook_private.validate_refund();

create function moneybook_private.sync_transaction_refund_status()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
    target_transaction uuid := coalesce(new.transaction_id, old.transaction_id);
    original_amount bigint;
    original_status text;
    confirmed_refunds bigint;
begin
    select amount, status into original_amount, original_status
    from public.transactions where id = target_transaction for update;
    if original_amount is null then
        return coalesce(new, old);
    end if;
    select coalesce(sum(amount), 0) into confirmed_refunds
    from public.transaction_refunds
    where transaction_id = target_transaction and status = 'CONFIRMED';
    if original_status in ('CONFIRMED', 'CANCELED') then
        update public.transactions
        set status = case when confirmed_refunds = original_amount then 'CANCELED' else 'CONFIRMED' end
        where id = target_transaction;
    end if;
    return coalesce(new, old);
end;
$$;
revoke all on function moneybook_private.sync_transaction_refund_status() from public, anon, authenticated;
create trigger sync_transaction_refund_status
    after insert or update or delete on public.transaction_refunds
    for each row execute function moneybook_private.sync_transaction_refund_status();

create function moneybook_private.validate_excluded_category()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
    card_household uuid;
    category_household uuid;
    category_type text;
begin
    select household_id into card_household from public.cards where id = new.card_id;
    select household_id, transaction_type into category_household, category_type
    from public.categories where id = new.category_id;
    if card_household is null or category_household is distinct from card_household
       or category_type <> 'EXPENSE' then
        raise exception using errcode = '23514', message = 'Excluded category must be an expense category in the card household';
    end if;
    return new;
end;
$$;
revoke all on function moneybook_private.validate_excluded_category() from public, anon, authenticated;
create trigger validate_excluded_category before insert or update
    on public.card_performance_excluded_categories
    for each row execute function moneybook_private.validate_excluded_category();

create policy categories_select_household on public.categories for select to authenticated
    using (household_id = (select moneybook_private.current_household_id()));
create policy categories_insert_household on public.categories for insert to authenticated
    with check (household_id = (select moneybook_private.current_household_id())
        and created_by = (select auth.uid()));
create policy categories_update_household on public.categories for update to authenticated
    using (household_id = (select moneybook_private.current_household_id()))
    with check (household_id = (select moneybook_private.current_household_id()));
create policy categories_delete_household on public.categories for delete to authenticated
    using (household_id = (select moneybook_private.current_household_id()));

create policy cards_select_household on public.cards for select to authenticated
    using (household_id = (select moneybook_private.current_household_id()));
create policy cards_insert_owner on public.cards for insert to authenticated
    with check (household_id = (select moneybook_private.current_household_id())
        and owner_user_id = (select auth.uid()));
create policy cards_update_owner on public.cards for update to authenticated
    using (owner_user_id = (select auth.uid()))
    with check (household_id = (select moneybook_private.current_household_id())
        and owner_user_id = (select auth.uid()));
create policy cards_delete_owner on public.cards for delete to authenticated
    using (owner_user_id = (select auth.uid()));

create policy transactions_select_visible on public.transactions for select to authenticated
    using (household_id = (select moneybook_private.current_household_id())
        and (scope = 'SHARED' or paid_by = (select auth.uid())));
create policy transactions_insert_own on public.transactions for insert to authenticated
    with check (household_id = (select moneybook_private.current_household_id())
        and created_by = (select auth.uid()) and paid_by = (select auth.uid())
        and status in ('PENDING', 'CONFIRMED'));
create policy transactions_update_visible on public.transactions for update to authenticated
    using (household_id = (select moneybook_private.current_household_id())
        and (scope = 'SHARED' or paid_by = (select auth.uid())))
    with check (household_id = (select moneybook_private.current_household_id())
        and (scope = 'SHARED' or paid_by = (select auth.uid())));
create policy transactions_delete_visible on public.transactions for delete to authenticated
    using (household_id = (select moneybook_private.current_household_id())
        and (scope = 'SHARED' or paid_by = (select auth.uid())));

create policy refunds_select_visible on public.transaction_refunds for select to authenticated
    using (exists (
        select 1 from public.transactions t
        where t.id = transaction_id
          and t.household_id = (select moneybook_private.current_household_id())
          and (t.scope = 'SHARED' or t.paid_by = (select auth.uid()))
    ));

create policy excluded_categories_select_household
    on public.card_performance_excluded_categories for select to authenticated
    using (exists (
        select 1 from public.cards c
        where c.id = card_id
          and c.household_id = (select moneybook_private.current_household_id())
    ));
create policy excluded_categories_insert_owner
    on public.card_performance_excluded_categories for insert to authenticated
    with check (created_by = (select auth.uid()) and exists (
        select 1 from public.cards c
        where c.id = card_id and c.owner_user_id = (select auth.uid())
          and c.household_id = (select moneybook_private.current_household_id())
    ));
create policy excluded_categories_delete_owner
    on public.card_performance_excluded_categories for delete to authenticated
    using (exists (
        select 1 from public.cards c
        where c.id = card_id and c.owner_user_id = (select auth.uid())
    ));

create function public.create_transaction_refund(
    transaction_id uuid,
    amount bigint,
    idempotency_key uuid,
    refunded_at timestamptz default clock_timestamp(),
    source text default 'MANUAL'
)
returns uuid language plpgsql security definer set search_path = '' as $$
declare
    caller uuid := auth.uid();
    caller_household uuid;
    original public.transactions%rowtype;
    existing public.transaction_refunds%rowtype;
    refund_id uuid;
begin
    if caller is null then
        raise exception using errcode = '42501', message = 'Authentication required';
    end if;
    select household_id into caller_household
    from public.household_members where user_id = caller;
    select * into original from public.transactions
    where id = transaction_id for update;
    if original.id is null or original.household_id is distinct from caller_household
       or (original.scope = 'PERSONAL' and original.paid_by <> caller) then
        raise exception using errcode = '42501', message = 'Transaction access denied';
    end if;
    select * into existing from public.transaction_refunds
    where transaction_refunds.transaction_id = create_transaction_refund.transaction_id
      and transaction_refunds.idempotency_key = create_transaction_refund.idempotency_key;
    if existing.id is not null then
        if existing.amount <> create_transaction_refund.amount
           or existing.source <> create_transaction_refund.source then
            raise exception using errcode = '23505', message = 'Refund idempotency key already used';
        end if;
        return existing.id;
    end if;
    if original.type <> 'EXPENSE' or original.status not in ('CONFIRMED', 'CANCELED') then
        raise exception using errcode = '22023', message = 'Only confirmed expense transactions can be refunded';
    end if;
    if amount is null or amount <= 0 then
        raise exception using errcode = '22023', message = 'Refund amount must be positive';
    end if;
    if source not in ('MANUAL', 'NOTIFICATION', 'IMPORT') then
        raise exception using errcode = '22023', message = 'Invalid refund source';
    end if;
    insert into public.transaction_refunds(
        transaction_id, amount, status, source, idempotency_key, refunded_at, created_by
    ) values (
        original.id, amount, 'CONFIRMED', source, idempotency_key, refunded_at, caller
    ) returning id into refund_id;
    return refund_id;
end;
$$;

create function public.get_monthly_summary(month_start date)
returns table (
    shared_income bigint,
    shared_expense bigint,
    shared_balance bigint,
    personal_income bigint,
    personal_expense bigint,
    personal_balance bigint
) language plpgsql stable security definer set search_path = '' as $$
declare
    caller uuid := auth.uid();
    caller_household uuid;
    period_start timestamptz;
    period_end timestamptz;
begin
    if caller is null then
        raise exception using errcode = '42501', message = 'Authentication required';
    end if;
    if month_start is null or month_start <> date_trunc('month', month_start)::date then
        raise exception using errcode = '22023', message = 'month_start must be the first day of a month';
    end if;
    select household_id into caller_household
    from public.household_members where user_id = caller;
    if caller_household is null then
        raise exception using errcode = '42501', message = 'Household membership required';
    end if;
    period_start := month_start::timestamp at time zone 'Asia/Seoul';
    period_end := (month_start + interval '1 month')::timestamp at time zone 'Asia/Seoul';
    return query
    with effective as (
        select t.type, t.scope,
            greatest(t.amount - coalesce((
                select sum(r.amount) from public.transaction_refunds r
                where r.transaction_id = t.id and r.status = 'CONFIRMED'
            ), 0), 0)::bigint as amount
        from public.transactions t
        where t.household_id = caller_household
          and (t.scope = 'SHARED' or t.paid_by = caller)
          and t.status in ('CONFIRMED', 'CANCELED')
          and t.transaction_at >= period_start and t.transaction_at < period_end
    ), totals as (
        select
            coalesce(sum(amount) filter (where scope = 'SHARED' and type = 'INCOME'), 0)::bigint si,
            coalesce(sum(amount) filter (where scope = 'SHARED' and type = 'EXPENSE'), 0)::bigint se,
            coalesce(sum(amount) filter (where scope = 'PERSONAL' and type = 'INCOME'), 0)::bigint pi,
            coalesce(sum(amount) filter (where scope = 'PERSONAL' and type = 'EXPENSE'), 0)::bigint pe
        from effective
    )
    select si, se, si - se, pi, pe, pi - pe from totals;
end;
$$;

create function public.get_card_performance(p_card_id uuid, p_month_start date)
returns table (
    card_id uuid,
    month_start date,
    monthly_target_amount bigint,
    card_usage_amount bigint,
    expected_performance_amount bigint,
    achievement_rate numeric
) language plpgsql stable security definer set search_path = '' as $$
declare
    caller uuid := auth.uid();
    caller_household uuid;
    selected_card public.cards%rowtype;
    period_start timestamptz;
    period_end timestamptz;
begin
    if caller is null then
        raise exception using errcode = '42501', message = 'Authentication required';
    end if;
    if p_month_start is null or p_month_start <> date_trunc('month', p_month_start)::date then
        raise exception using errcode = '22023', message = 'month_start must be the first day of a month';
    end if;
    select household_id into caller_household
    from public.household_members where user_id = caller;
    select * into selected_card from public.cards c
    where c.id = p_card_id and c.household_id = caller_household
      and c.performance_enabled and c.is_active;
    if selected_card.id is null then
        raise exception using errcode = '42501', message = 'Managed card access denied';
    end if;
    period_start := p_month_start::timestamp at time zone 'Asia/Seoul';
    period_end := (p_month_start + interval '1 month')::timestamp at time zone 'Asia/Seoul';
    return query
    with effective as (
        select t.id, t.category_id, t.performance_included_override,
            greatest(t.amount - coalesce((
                select sum(r.amount) from public.transaction_refunds r
                where r.transaction_id = t.id and r.status = 'CONFIRMED'
            ), 0), 0)::bigint as net_amount
        from public.transactions t
        where t.card_id = selected_card.id and t.type = 'EXPENSE'
          and t.status in ('CONFIRMED', 'CANCELED')
          and t.transaction_at >= period_start and t.transaction_at < period_end
    ), totals as (
        select coalesce(sum(net_amount), 0)::bigint usage,
            coalesce(sum(case
                when performance_included_override is true then net_amount
                when performance_included_override is false then 0
                when exists (
                    select 1 from public.card_performance_excluded_categories e
                    where e.card_id = selected_card.id and e.category_id = effective.category_id
                ) then 0
                else net_amount
            end), 0)::bigint expected
        from effective
    )
    select selected_card.id, p_month_start,
        selected_card.monthly_target_amount, totals.usage, totals.expected,
        case when selected_card.monthly_target_amount = 0 then 0::numeric
             else round(totals.expected::numeric * 100 / selected_card.monthly_target_amount, 2)
        end
    from totals;
end;
$$;

revoke all on function public.create_transaction_refund(uuid, bigint, uuid, timestamptz, text),
    public.get_monthly_summary(date), public.get_card_performance(uuid, date)
    from public, anon, authenticated;
grant execute on function public.create_transaction_refund(uuid, bigint, uuid, timestamptz, text),
    public.get_monthly_summary(date), public.get_card_performance(uuid, date)
    to authenticated;

commit;
