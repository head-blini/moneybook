begin;
create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions, pg_temp;
select plan(94);

select has_table('public', 'categories', 'categories table exists');
select has_table('public', 'cards', 'cards table exists');
select has_table('public', 'transactions', 'transactions table exists');
select has_table('public', 'transaction_refunds', 'transaction_refunds table exists');
select has_table('public', 'card_performance_excluded_categories', 'card exclusion table exists');
select ok((select bool_and(relrowsecurity) from pg_class where oid in
    ('public.categories'::regclass, 'public.cards'::regclass,
     'public.transactions'::regclass, 'public.transaction_refunds'::regclass,
     'public.card_performance_excluded_categories'::regclass)), 'RLS enabled on Phase 2B tables');
select ok(not has_function_privilege('anon',
    'public.get_card_performance(uuid,date)', 'EXECUTE'), 'anon cannot read card performance');
select ok(not has_table_privilege('authenticated',
    'public.transaction_refunds', 'INSERT'), 'refund inserts require RPC');
select ok(not has_column_privilege('authenticated',
    'public.transactions', 'scope', 'UPDATE'), 'transaction scope is not client-updatable');
select has_column('public', 'transactions', 'deleted_at', 'transactions support soft deletion');
select ok(not has_table_privilege('authenticated',
    'public.transactions', 'DELETE'), 'transactions cannot be physically deleted by clients');
select ok(not has_function_privilege('anon',
    'public.soft_delete_transaction(uuid)', 'EXECUTE'), 'anon cannot soft-delete transactions');
select ok(not has_function_privilege('anon',
    'public.restore_transaction(uuid)', 'EXECUTE'), 'anon cannot restore transactions');

insert into auth.users(id) values
    ('10000000-0000-0000-0000-000000000001'),
    ('10000000-0000-0000-0000-000000000002'),
    ('10000000-0000-0000-0000-000000000003');

set local role authenticated;
select set_config('request.jwt.claim.sub','10000000-0000-0000-0000-000000000001',true);
create temporary table phase2b_fixture(key text primary key, value text);
grant select, insert, update on phase2b_fixture to authenticated;
insert into phase2b_fixture values ('house_a', public.create_household('Phase 2B House')::text);
insert into phase2b_fixture values ('invite_a', public.create_invitation());

select is((select count(*) from public.categories), 17::bigint,
    'new household receives all default categories');
select is((select count(*) from public.categories where transaction_type='EXPENSE'), 12::bigint,
    'new household receives twelve expense categories');
select is((select count(*) from public.categories where transaction_type='INCOME'), 5::bigint,
    'new household receives five income categories');
update public.categories set is_active=false
where name='식비' and transaction_type='EXPENSE';
set local role postgres;
select moneybook_private.ensure_default_categories(
    (select value::uuid from phase2b_fixture where key='house_a'),
    '10000000-0000-0000-0000-000000000001');
select moneybook_private.ensure_default_categories(
    (select value::uuid from phase2b_fixture where key='house_a'),
    '10000000-0000-0000-0000-000000000001');
set local role authenticated;
select set_config('request.jwt.claim.sub','10000000-0000-0000-0000-000000000001',true);
select is((select count(*) from public.categories), 17::bigint,
    'default category initialization is idempotent');
select is((select is_active from public.categories
    where name='식비' and transaction_type='EXPENSE'), false,
    'default initialization does not reactivate an existing category');
delete from public.categories where name='육아' and transaction_type='EXPENSE';
select is((select count(*) from public.categories), 16::bigint,
    'existing household fixture can have a missing default category');
set local role postgres;
select moneybook_private.ensure_default_categories(
    (select value::uuid from phase2b_fixture where key='house_a'),
    '10000000-0000-0000-0000-000000000001');
set local role authenticated;
select set_config('request.jwt.claim.sub','10000000-0000-0000-0000-000000000001',true);
select is((select count(*) from public.categories), 17::bigint,
    'default initializer backfills a missing category for an existing household');

insert into phase2b_fixture values ('category_expense', (select id::text
    from public.categories where name='식비' and transaction_type='EXPENSE'));
with row as (
    insert into public.categories(household_id, name, transaction_type, created_by)
    values ((select value::uuid from phase2b_fixture where key='house_a'), '실적제외', 'EXPENSE', auth.uid())
    returning id
) insert into phase2b_fixture select 'category_excluded', id::text from row;
insert into phase2b_fixture values ('category_income', (select id::text
    from public.categories where name='급여' and transaction_type='INCOME'));
with row as (
    insert into public.cards(household_id, owner_user_id, display_name, issuer_code,
        last_four, performance_enabled, notification_collection_enabled, monthly_target_amount)
    values ((select value::uuid from phase2b_fixture where key='house_a'), auth.uid(),
        '생활 카드', 'TEST_CARD', '1234', true, false, 100000)
    returning id
) insert into phase2b_fixture select 'card_a', id::text from row;

insert into public.card_performance_excluded_categories(card_id, category_id, created_by)
values ((select value::uuid from phase2b_fixture where key='card_a'),
    (select value::uuid from phase2b_fixture where key='category_excluded'), auth.uid());

with row as (
    insert into public.transactions(household_id, created_by, paid_by, type, scope, amount,
        category_id, card_id, merchant, transaction_at, source)
    values ((select value::uuid from phase2b_fixture where key='house_a'), auth.uid(), auth.uid(),
        'EXPENSE', 'SHARED', 50000,
        (select value::uuid from phase2b_fixture where key='category_expense'),
        (select value::uuid from phase2b_fixture where key='card_a'),
        '환불 대상', '2026-09-10 03:00:00+00', 'MANUAL') returning id
) insert into phase2b_fixture select 'shared_refund', id::text from row;
with row as (
    insert into public.transactions(household_id, created_by, paid_by, type, scope, amount,
        category_id, card_id, merchant, transaction_at, source)
    values ((select value::uuid from phase2b_fixture where key='house_a'), auth.uid(), auth.uid(),
        'EXPENSE', 'PERSONAL', 30000,
        (select value::uuid from phase2b_fixture where key='category_excluded'),
        (select value::uuid from phase2b_fixture where key='card_a'),
        '비공개 제외', '2026-09-11 03:00:00+00', 'MANUAL') returning id
) insert into phase2b_fixture select 'personal_excluded', id::text from row;
with row as (
    insert into public.transactions(household_id, created_by, paid_by, type, scope, amount,
        category_id, card_id, merchant, transaction_at, source, performance_included_override)
    values ((select value::uuid from phase2b_fixture where key='house_a'), auth.uid(), auth.uid(),
        'EXPENSE', 'PERSONAL', 20000,
        (select value::uuid from phase2b_fixture where key='category_excluded'),
        (select value::uuid from phase2b_fixture where key='card_a'),
        '비공개 강제포함', '2026-09-12 03:00:00+00', 'MANUAL', true) returning id
) insert into phase2b_fixture select 'personal_override_in', id::text from row;
with row as (
    insert into public.transactions(household_id, created_by, paid_by, type, scope, amount,
        category_id, card_id, merchant, transaction_at, source, performance_included_override)
    values ((select value::uuid from phase2b_fixture where key='house_a'), auth.uid(), auth.uid(),
        'EXPENSE', 'SHARED', 10000,
        (select value::uuid from phase2b_fixture where key='category_expense'),
        (select value::uuid from phase2b_fixture where key='card_a'),
        '강제제외', '2026-09-13 03:00:00+00', 'MANUAL', false) returning id
) insert into phase2b_fixture select 'shared_override_out', id::text from row;
with row as (
    insert into public.transactions(household_id, created_by, paid_by, type, scope, amount,
        category_id, merchant, transaction_at, source)
    values ((select value::uuid from phase2b_fixture where key='house_a'), auth.uid(), auth.uid(),
        'INCOME', 'SHARED', 100000,
        (select value::uuid from phase2b_fixture where key='category_income'),
        '공동 수입', '2026-09-14 03:00:00+00', 'MANUAL') returning id
) insert into phase2b_fixture select 'shared_income', id::text from row;
with row as (
    insert into public.transactions(household_id, created_by, paid_by, type, scope, amount,
        category_id, merchant, transaction_at, source)
    values ((select value::uuid from phase2b_fixture where key='house_a'), auth.uid(), auth.uid(),
        'EXPENSE', 'SHARED', 1000,
        (select value::uuid from phase2b_fixture where key='category_expense'),
        '삭제 대상', '2026-09-15 03:00:00+00', 'MANUAL') returning id
) insert into phase2b_fixture select 'shared_delete', id::text from row;

set local role postgres;
insert into public.transaction_refunds(transaction_id, amount, status, source, idempotency_key,
    refunded_at, created_by)
values ((select value::uuid from phase2b_fixture where key='personal_excluded'), 1000, 'PENDING', 'MANUAL',
    '20000000-0000-0000-0000-000000000010', '2026-09-16 03:00:00+00',
    '10000000-0000-0000-0000-000000000001');
set local role authenticated;
select set_config('request.jwt.claim.sub','10000000-0000-0000-0000-000000000001',true);

select throws_ok(format($sql$insert into public.transactions(
        household_id, created_by, paid_by, type, scope, amount, category_id,
        transaction_at, source, status)
    values (%L, auth.uid(), auth.uid(), 'EXPENSE', 'SHARED', 1000, %L,
        '2026-09-16 03:00:00+00', 'MANUAL', 'CANCELED')$sql$,
    (select value from phase2b_fixture where key='house_a'),
    (select value from phase2b_fixture where key='category_expense')),
    '42501', 'new row violates row-level security policy for table "transactions"',
    'client cannot insert an already-canceled transaction');

select is((select card_usage_amount from public.get_card_performance(
    (select value::uuid from phase2b_fixture where key='card_a'), '2026-09-01')), 110000::bigint,
    'card usage includes shared and personal transactions');
select is((select expected_performance_amount from public.get_card_performance(
    (select value::uuid from phase2b_fixture where key='card_a'), '2026-09-01')), 70000::bigint,
    'excluded category and per-transaction overrides use the correct precedence');

select set_config('request.jwt.claim.sub','10000000-0000-0000-0000-000000000002',true);
select is(public.join_household((select value from phase2b_fixture where key='invite_a')),
    (select value::uuid from phase2b_fixture where key='house_a'), 'partner joins fixture household');
select is((select count(*) from public.transactions where scope='SHARED'), 4::bigint,
    'partner can read household shared transactions');
select is((select count(*) from public.transactions where scope='PERSONAL'), 0::bigint,
    'partner cannot read personal transaction rows');
select is((select count(*) from public.transaction_refunds), 0::bigint,
    'personal transaction refund follows the parent privacy scope');
select lives_ok($$update public.transactions set merchant='배우자 수정'
    where id=(select value::uuid from phase2b_fixture where key='shared_override_out')$$,
    'partner can update a shared transaction');
select is((select merchant from public.transactions
    where id=(select value::uuid from phase2b_fixture where key='shared_override_out')),
    '배우자 수정', 'partner shared update is persisted');
select is(public.soft_delete_transaction(
    (select value::uuid from phase2b_fixture where key='shared_delete')),
    (select value::uuid from phase2b_fixture where key='shared_delete'),
    'partner can soft-delete a shared transaction');
select is((select count(*) from public.transactions
    where id=(select value::uuid from phase2b_fixture where key='shared_delete')), 0::bigint,
    'soft-deleted shared transaction is hidden');
select is((select shared_expense from public.get_monthly_summary('2026-09-01')), 60000::bigint,
    'soft-deleted shared transaction is excluded from monthly summary');
select is(public.restore_transaction(
    (select value::uuid from phase2b_fixture where key='shared_delete')),
    (select value::uuid from phase2b_fixture where key='shared_delete'),
    'partner can restore a shared transaction');
select is((select shared_expense from public.get_monthly_summary('2026-09-01')), 61000::bigint,
    'restored shared transaction returns to monthly summary');
select is(public.soft_delete_transaction(
    (select value::uuid from phase2b_fixture where key='shared_delete')),
    (select value::uuid from phase2b_fixture where key='shared_delete'),
    'partner can soft-delete the restored shared transaction again');
set local role postgres;
select ok((select deleted_at is not null from public.transactions
    where id=(select value::uuid from phase2b_fixture where key='shared_delete')),
    'soft-deleted shared transaction remains stored');
set local role authenticated;
select set_config('request.jwt.claim.sub','10000000-0000-0000-0000-000000000002',true);
select lives_ok($$update public.transactions set merchant='침해'
    where id=(select value::uuid from phase2b_fixture where key='personal_excluded')$$,
    'unauthorized personal update affects no visible row');
select throws_ok(format($sql$select public.soft_delete_transaction(%L)$sql$,
    (select value from phase2b_fixture where key='personal_excluded')),
    '42501', 'Transaction access denied', 'partner cannot soft-delete a personal transaction');
select throws_ok(format($sql$select public.restore_transaction(%L)$sql$,
    (select value from phase2b_fixture where key='personal_excluded')),
    '42501', 'Transaction access denied', 'partner cannot restore a personal transaction');
select throws_ok($$update public.transactions set paid_by=auth.uid()
    where id=(select value::uuid from phase2b_fixture where key='shared_override_out')$$,
    '42501', 'permission denied for table transactions', 'shared ownership cannot be reassigned');
select throws_ok($$update public.transactions set scope='PERSONAL'
    where id=(select value::uuid from phase2b_fixture where key='shared_override_out')$$,
    '42501', 'permission denied for table transactions', 'shared scope cannot be made personal');
select throws_ok(format($sql$insert into public.transactions(
        household_id, created_by, paid_by, type, scope, amount, category_id, card_id,
        merchant, transaction_at, source)
    values (%L, auth.uid(), auth.uid(), 'EXPENSE', 'SHARED', 1000, %L, %L,
        '잘못된 카드', '2026-09-16 03:00:00+00', 'MANUAL')$sql$,
    (select value from phase2b_fixture where key='house_a'),
    (select value from phase2b_fixture where key='category_expense'),
    (select value from phase2b_fixture where key='card_a')),
    '23514', 'Card owner must match transaction payer', 'card owner must match transaction payer');

select is((select card_usage_amount from public.get_card_performance(
    (select value::uuid from phase2b_fixture where key='card_a'), '2026-09-01')), 110000::bigint,
    'partner can read aggregate card usage');
select is((select expected_performance_amount from public.get_card_performance(
    (select value::uuid from phase2b_fixture where key='card_a'), '2026-09-01')), 70000::bigint,
    'partner can read expected card performance');
select is((select monthly_target_amount from public.get_card_performance(
    (select value::uuid from phase2b_fixture where key='card_a'), '2026-09-01')), 100000::bigint,
    'card performance returns the monthly target');
select is((select achievement_rate from public.get_card_performance(
    (select value::uuid from phase2b_fixture where key='card_a'), '2026-09-01')), 70.00::numeric,
    'achievement rate uses expected performance, not raw usage');
select ok((select not (to_jsonb(p) ?| array['transaction_id','paid_by','merchant','memo','category_id'])
    from public.get_card_performance(
        (select value::uuid from phase2b_fixture where key='card_a'), '2026-09-01') p),
    'card performance RPC exposes no transaction detail fields');

insert into phase2b_fixture values ('refund_one', public.create_transaction_refund(
    (select value::uuid from phase2b_fixture where key='shared_refund'), 10000,
    '20000000-0000-0000-0000-000000000001', '2026-09-17 03:00:00+00', 'MANUAL')::text);
select ok((select value::uuid is not null from phase2b_fixture where key='refund_one'),
    'partner can record a partial refund on a shared transaction');
insert into phase2b_fixture values ('refund_two', public.create_transaction_refund(
    (select value::uuid from phase2b_fixture where key='shared_refund'), 15000,
    '20000000-0000-0000-0000-000000000002', '2026-09-18 03:00:00+00', 'NOTIFICATION')::text);
select is((select coalesce(sum(amount),0)::bigint from public.transaction_refunds
    where transaction_id=(select value::uuid from phase2b_fixture where key='shared_refund')
      and status='CONFIRMED'), 25000::bigint, 'multiple partial refunds accumulate');
select is(public.create_transaction_refund(
    (select value::uuid from phase2b_fixture where key='shared_refund'), 10000,
    '20000000-0000-0000-0000-000000000001', '2026-09-18 03:00:00+00', 'MANUAL'),
    (select value::uuid from phase2b_fixture where key='refund_one'),
    'duplicate refund request is idempotent');
select is((select count(*) from public.transaction_refunds
    where transaction_id=(select value::uuid from phase2b_fixture where key='shared_refund')
      and idempotency_key='20000000-0000-0000-0000-000000000001'), 1::bigint,
    'duplicate refund event is stored once');

set local role postgres;
insert into public.transaction_refunds(transaction_id, amount, status, source, idempotency_key,
    refunded_at, created_by)
values ((select value::uuid from phase2b_fixture where key='shared_refund'), 5000, 'PENDING', 'NOTIFICATION',
    '20000000-0000-0000-0000-000000000003', '2026-09-19 03:00:00+00',
    '10000000-0000-0000-0000-000000000002');
set local role authenticated;
select is((select card_usage_amount from public.get_card_performance(
    (select value::uuid from phase2b_fixture where key='card_a'), '2026-09-01')), 85000::bigint,
    'pending refund is not included in card usage');
select throws_ok(format($sql$select public.create_transaction_refund(%L, 25000, %L,
        '2026-09-20 03:00:00+00', 'MANUAL')$sql$,
    (select value from phase2b_fixture where key='shared_refund'),
    '20000000-0000-0000-0000-000000000004'),
    '23514', null, 'over-refund is rejected');
insert into phase2b_fixture values ('refund_three', public.create_transaction_refund(
    (select value::uuid from phase2b_fixture where key='shared_refund'), 20000,
    '20000000-0000-0000-0000-000000000004', '2026-09-20 03:00:00+00', 'MANUAL')::text);
select is((select status from public.transactions
    where id=(select value::uuid from phase2b_fixture where key='shared_refund')),
    'CONFIRMED', 'pending refund does not cause a full-refund status');

set local role postgres;
update public.transaction_refunds set status='CONFIRMED'
where transaction_id=(select value::uuid from phase2b_fixture where key='shared_refund')
  and idempotency_key='20000000-0000-0000-0000-000000000003';
set local role authenticated;
select is((select status from public.transactions
    where id=(select value::uuid from phase2b_fixture where key='shared_refund')),
    'CANCELED', 'full confirmed refund atomically cancels the original transaction');
select is((select coalesce(sum(amount),0)::bigint from public.transaction_refunds
    where transaction_id=(select value::uuid from phase2b_fixture where key='shared_refund')
      and status='CONFIRMED'), 50000::bigint, 'full refund preserves all refund events');
select is((select card_usage_amount from public.get_card_performance(
    (select value::uuid from phase2b_fixture where key='card_a'), '2026-09-01')), 60000::bigint,
    'card usage is net of confirmed partial and full refunds');
select is((select expected_performance_amount from public.get_card_performance(
    (select value::uuid from phase2b_fixture where key='card_a'), '2026-09-01')), 20000::bigint,
    'performance applies refunds, excluded category, and override precedence');
select is((select achievement_rate from public.get_card_performance(
    (select value::uuid from phase2b_fixture where key='card_a'), '2026-09-01')), 20.00::numeric,
    'performance achievement rate is recalculated from net transactions');
select is((select shared_expense from public.get_monthly_summary('2026-09-01')), 10000::bigint,
    'household summary reflects the full refund');
select is((select personal_expense from public.get_monthly_summary('2026-09-01')), 0::bigint,
    'partner monthly summary does not disclose owner personal total');

select set_config('request.jwt.claim.sub','10000000-0000-0000-0000-000000000001',true);
select throws_ok($$update public.transactions set scope='SHARED'
    where id=(select value::uuid from phase2b_fixture where key='personal_excluded')$$,
    '42501', 'permission denied for table transactions',
    'personal owner cannot expose a transaction by changing its scope');
select is((select merchant from public.transactions
    where id=(select value::uuid from phase2b_fixture where key='personal_excluded')),
    '비공개 제외', 'partner could not update the personal transaction');
select is((select count(*) from public.transactions
    where id=(select value::uuid from phase2b_fixture where key='personal_excluded')), 1::bigint,
    'partner could not delete the personal transaction');
select is((select personal_expense from public.get_monthly_summary('2026-09-01')), 50000::bigint,
    'owner monthly summary includes only own personal transactions');
select is((select shared_balance from public.get_monthly_summary('2026-09-01')), 90000::bigint,
    'shared income, expense, and refund remain consistent');
select is(public.soft_delete_transaction(
    (select value::uuid from phase2b_fixture where key='personal_excluded')),
    (select value::uuid from phase2b_fixture where key='personal_excluded'),
    'owner can soft-delete a personal transaction');
select is((select personal_expense from public.get_monthly_summary('2026-09-01')), 20000::bigint,
    'soft-deleted personal transaction is excluded from monthly summary');
select is((select card_usage_amount from public.get_card_performance(
    (select value::uuid from phase2b_fixture where key='card_a'), '2026-09-01')), 30000::bigint,
    'soft-deleted personal transaction is excluded from card usage');
select is((select count(*) from public.transaction_refunds
    where transaction_id=(select value::uuid from phase2b_fixture where key='personal_excluded')),
    0::bigint, 'refunds of a soft-deleted personal transaction are hidden');
select set_config('request.jwt.claim.sub','10000000-0000-0000-0000-000000000002',true);
select throws_ok(format($sql$select public.restore_transaction(%L)$sql$,
    (select value from phase2b_fixture where key='personal_excluded')),
    '42501', 'Transaction access denied', 'partner cannot restore an owner-deleted personal transaction');
select set_config('request.jwt.claim.sub','10000000-0000-0000-0000-000000000001',true);
select is(public.restore_transaction(
    (select value::uuid from phase2b_fixture where key='personal_excluded')),
    (select value::uuid from phase2b_fixture where key='personal_excluded'),
    'owner can restore a personal transaction');
select is((select personal_expense from public.get_monthly_summary('2026-09-01')), 50000::bigint,
    'restored personal transaction returns to monthly summary');
select is((select card_usage_amount from public.get_card_performance(
    (select value::uuid from phase2b_fixture where key='card_a'), '2026-09-01')), 60000::bigint,
    'restored personal transaction returns to card usage with its existing refund');
select is((select count(*) from public.transaction_refunds
    where transaction_id=(select value::uuid from phase2b_fixture where key='personal_excluded')),
    1::bigint, 'restoring a personal transaction reveals its preserved refund');

with row as (
    insert into public.transactions(household_id, created_by, paid_by, type, scope, amount,
        category_id, merchant, transaction_at, source, notification_provider,
        notification_fingerprint)
    values ((select value::uuid from phase2b_fixture where key='house_a'), auth.uid(), auth.uid(),
        'EXPENSE', 'PERSONAL', 777,
        (select value::uuid from phase2b_fixture where key='category_expense'),
        '중복 알림', '2026-10-01 03:00:00+00', 'NOTIFICATION', 'TEST', 'fingerprint-soft-delete')
    returning id
) insert into phase2b_fixture select 'notification_delete', id::text from row;
select is(public.soft_delete_transaction(
    (select value::uuid from phase2b_fixture where key='notification_delete')),
    (select value::uuid from phase2b_fixture where key='notification_delete'),
    'notification transaction can be soft-deleted');
select throws_ok(format($sql$insert into public.transactions(
        household_id, created_by, paid_by, type, scope, amount, category_id, merchant,
        transaction_at, source, notification_provider, notification_fingerprint)
    values (%L, auth.uid(), auth.uid(), 'EXPENSE', 'PERSONAL', 777, %L, '중복 알림',
        '2026-10-01 03:00:00+00', 'NOTIFICATION', 'TEST', 'fingerprint-soft-delete')$sql$,
    (select value from phase2b_fixture where key='house_a'),
    (select value from phase2b_fixture where key='category_expense')),
    '23505', null, 'soft-deleted notification fingerprint still prevents duplicates');
set local role postgres;
select is((select count(*) from public.transactions
    where paid_by='10000000-0000-0000-0000-000000000001'
      and notification_fingerprint='fingerprint-soft-delete'), 1::bigint,
    'duplicate notification leaves one preserved transaction');
set local role authenticated;
select set_config('request.jwt.claim.sub','10000000-0000-0000-0000-000000000001',true);

select set_config('request.jwt.claim.sub','10000000-0000-0000-0000-000000000003',true);
insert into phase2b_fixture values ('house_b', public.create_household('Other House')::text);
select is((select count(*) from public.transactions), 0::bigint,
    'another household cannot read transactions');
select is((select count(*) from public.categories), 17::bigint,
    'another new household receives its own default categories');
select is((select count(*) from public.categories
    where household_id=(select value::uuid from phase2b_fixture where key='house_a')), 0::bigint,
    'category RLS hides defaults and custom categories from another household');
select throws_ok(format($sql$select * from public.get_card_performance(%L, '2026-09-01')$sql$,
    (select value from phase2b_fixture where key='card_a')),
    '42501', 'Managed card access denied', 'another household cannot read card performance');
select throws_ok(format($sql$insert into public.transactions(
        household_id, created_by, paid_by, type, scope, amount, category_id,
        transaction_at, source)
    values (%L, auth.uid(), auth.uid(), 'EXPENSE', 'SHARED', 1000, %L,
        '2026-09-21 03:00:00+00', 'MANUAL')$sql$,
    (select value from phase2b_fixture where key='house_a'),
    (select value from phase2b_fixture where key='category_expense')),
    '23514', 'Transaction users must belong to its household',
    'another household cannot insert a transaction into the first household');

select set_config('request.jwt.claim.sub','10000000-0000-0000-0000-000000000002',true);
select is(public.soft_delete_transaction(
    (select value::uuid from phase2b_fixture where key='shared_refund')),
    (select value::uuid from phase2b_fixture where key='shared_refund'),
    'partner can soft-delete a shared transaction that has refunds');
select is((select count(*) from public.transactions
    where id=(select value::uuid from phase2b_fixture where key='shared_refund')), 0::bigint,
    'soft-deleted shared transaction is hidden');
select is((select count(*) from public.transaction_refunds
    where transaction_id=(select value::uuid from phase2b_fixture where key='shared_refund')), 0::bigint,
    'refunds of a soft-deleted shared transaction are hidden');
select is(public.create_transaction_refund(
    (select value::uuid from phase2b_fixture where key='shared_refund'), 10000,
    '20000000-0000-0000-0000-000000000001', '2026-09-21 04:00:00+00', 'MANUAL'),
    (select value::uuid from phase2b_fixture where key='refund_one'),
    'existing refund retry remains idempotent after transaction deletion');
select throws_ok(format($sql$select public.create_transaction_refund(%L, 1, %L,
        '2026-09-21 04:00:00+00', 'MANUAL')$sql$,
    (select value from phase2b_fixture where key='shared_refund'),
    '20000000-0000-0000-0000-000000000099'),
    '22023', 'Deleted transaction cannot be refunded',
    'new refunds are rejected for a soft-deleted transaction');
set local role postgres;
select ok((select deleted_at is not null from public.transactions
    where id=(select value::uuid from phase2b_fixture where key='shared_refund')),
    'soft-deleted transaction with refunds remains stored');
select is((select count(*) from public.transaction_refunds
    where transaction_id=(select value::uuid from phase2b_fixture where key='shared_refund')), 4::bigint,
    'all refund events remain stored after soft deletion');
set local role authenticated;
select set_config('request.jwt.claim.sub','10000000-0000-0000-0000-000000000002',true);
select is(public.restore_transaction(
    (select value::uuid from phase2b_fixture where key='shared_refund')),
    (select value::uuid from phase2b_fixture where key='shared_refund'),
    'partner can restore a shared transaction with refunds');
select is((select status from public.transactions
    where id=(select value::uuid from phase2b_fixture where key='shared_refund')),
    'CANCELED', 'restored fully-refunded transaction retains canceled status');
select is((select count(*) from public.transaction_refunds
    where transaction_id=(select value::uuid from phase2b_fixture where key='shared_refund')), 4::bigint,
    'restored transaction exposes all preserved refund events');
select is((select shared_expense from public.get_monthly_summary('2026-09-01')), 10000::bigint,
    'restored transaction uses confirmed refunds when recalculating its net amount');

select * from finish();
rollback;
