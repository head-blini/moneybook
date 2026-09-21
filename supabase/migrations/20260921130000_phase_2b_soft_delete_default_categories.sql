begin;

alter table public.transactions
    add column deleted_at timestamptz;

revoke delete on public.transactions from authenticated;

drop policy transactions_select_visible on public.transactions;
drop policy transactions_update_visible on public.transactions;
drop policy transactions_delete_visible on public.transactions;
drop policy refunds_select_visible on public.transaction_refunds;

create policy transactions_select_visible on public.transactions for select to authenticated
    using (deleted_at is null
        and household_id = (select moneybook_private.current_household_id())
        and (scope = 'SHARED' or paid_by = (select auth.uid())));
create policy transactions_update_visible on public.transactions for update to authenticated
    using (deleted_at is null
        and household_id = (select moneybook_private.current_household_id())
        and (scope = 'SHARED' or paid_by = (select auth.uid())))
    with check (deleted_at is null
        and household_id = (select moneybook_private.current_household_id())
        and (scope = 'SHARED' or paid_by = (select auth.uid())));

create policy refunds_select_visible on public.transaction_refunds for select to authenticated
    using (exists (
        select 1 from public.transactions t
        where t.id = transaction_id
          and t.deleted_at is null
          and t.household_id = (select moneybook_private.current_household_id())
          and (t.scope = 'SHARED' or t.paid_by = (select auth.uid()))
    ));

create function public.soft_delete_transaction(transaction_id uuid)
returns uuid language plpgsql security definer set search_path = '' as $$
declare
    caller uuid := auth.uid();
    caller_household uuid;
    original public.transactions%rowtype;
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
    if original.deleted_at is null then
        update public.transactions
        set deleted_at = clock_timestamp()
        where id = original.id;
    end if;
    return original.id;
end;
$$;

create function public.restore_transaction(transaction_id uuid)
returns uuid language plpgsql security definer set search_path = '' as $$
declare
    caller uuid := auth.uid();
    caller_household uuid;
    original public.transactions%rowtype;
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
    if original.deleted_at is not null then
        update public.transactions
        set deleted_at = null
        where id = original.id;
    end if;
    return original.id;
end;
$$;

create or replace function moneybook_private.validate_refund()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
    original_amount bigint;
    original_type text;
    original_deleted_at timestamptz;
    active_other bigint;
begin
    select amount, type, deleted_at into original_amount, original_type, original_deleted_at
    from public.transactions
    where id = new.transaction_id
    for update;
    if original_amount is null or original_type <> 'EXPENSE' then
        raise exception using errcode = '23514', message = 'Refund requires an expense transaction';
    end if;
    if original_deleted_at is not null then
        raise exception using errcode = '23514', message = 'Deleted transaction cannot be refunded';
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

create or replace function public.create_transaction_refund(
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
    if original.deleted_at is not null then
        raise exception using errcode = '22023', message = 'Deleted transaction cannot be refunded';
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

create or replace function public.get_monthly_summary(month_start date)
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
          and t.deleted_at is null
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

create or replace function public.get_card_performance(p_card_id uuid, p_month_start date)
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
          and t.deleted_at is null
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

create function moneybook_private.ensure_default_categories(
    p_household_id uuid,
    p_creator uuid
)
returns void language plpgsql security definer set search_path = '' as $$
begin
    insert into public.categories(household_id, name, transaction_type, created_by)
    select p_household_id, defaults.name, defaults.transaction_type, p_creator
    from (values
        ('식비', 'EXPENSE'),
        ('카페·간식', 'EXPENSE'),
        ('쇼핑', 'EXPENSE'),
        ('교통', 'EXPENSE'),
        ('주거·관리비', 'EXPENSE'),
        ('육아', 'EXPENSE'),
        ('의료·건강', 'EXPENSE'),
        ('통신', 'EXPENSE'),
        ('문화·여가', 'EXPENSE'),
        ('보험·금융', 'EXPENSE'),
        ('경조사', 'EXPENSE'),
        ('기타', 'EXPENSE'),
        ('급여', 'INCOME'),
        ('부수입', 'INCOME'),
        ('이자·배당', 'INCOME'),
        ('환급', 'INCOME'),
        ('기타', 'INCOME')
    ) as defaults(name, transaction_type)
    on conflict do nothing;
end;
$$;
revoke all on function moneybook_private.ensure_default_categories(uuid, uuid)
    from public, anon, authenticated;

select moneybook_private.ensure_default_categories(h.id, h.created_by)
from public.households h;

create or replace function public.create_household(name text)
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
    perform moneybook_private.ensure_default_categories(household, caller);
    return household;
end;
$$;

revoke all on function public.soft_delete_transaction(uuid),
    public.restore_transaction(uuid) from public, anon, authenticated;
grant execute on function public.soft_delete_transaction(uuid),
    public.restore_transaction(uuid) to authenticated;

commit;
