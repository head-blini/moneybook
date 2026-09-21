begin;
create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions, pg_temp;
select plan(34);

insert into auth.users(id) values
    ('00000000-0000-0000-0000-000000000001'),
    ('00000000-0000-0000-0000-000000000002'),
    ('00000000-0000-0000-0000-000000000003'),
    ('00000000-0000-0000-0000-000000000004');

select is((select count(*) from public.profiles), 4::bigint, 'auth trigger creates profiles');
select ok((select bool_and(relrowsecurity) from pg_class where oid in
    ('public.profiles'::regclass, 'public.households'::regclass,
     'public.household_members'::regclass, 'public.invitations'::regclass)), 'RLS enabled on all tables');
select ok(not has_function_privilege('anon','public.create_household(text)','EXECUTE'), 'anon cannot create household');
select ok(not has_table_privilege('authenticated','public.invitations','SELECT'), 'invitations cannot be enumerated');
select ok(not has_table_privilege('authenticated','public.household_members','INSERT'), 'direct membership insert denied');
select ok(not has_schema_privilege('authenticated','moneybook_private','CREATE'), 'private helper schema is not writable');

set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select is((select count(*) from public.profiles), 1::bigint, 'user reads own profile only');
select lives_ok($$update public.profiles set display_name='Owner' where id=auth.uid()$$, 'user updates own profile');
select throws_ok($$insert into public.household_members values
    (gen_random_uuid(),auth.uid(),'OWNER',now())$$, '42501', 'permission denied for table household_members',
    'client cannot insert membership');
select throws_ok($$select public.create_household('   ')$$, '22023',
    'Household name must contain 1 to 80 characters', 'blank household rejected');
create temporary table fixture(key text primary key, value text);
grant select, insert on fixture to authenticated;
insert into fixture values ('house_a', public.create_household(' 우리집 ')::text);
select is((select role from public.household_members where user_id=auth.uid()), 'OWNER', 'creator is OWNER');
select is((select name from public.households), '우리집', 'household name is trimmed');
select throws_ok($$select public.create_household('Second')$$, 'P0001', 'Already in a household',
    'user cannot create second membership');
insert into fixture values ('valid_code', public.create_invitation());
select ok((select value ~ '^[0-9A-F]{12}$' from fixture where key='valid_code'), 'code is random-friendly format');
select throws_ok('select * from public.invitations', '42501', 'permission denied for table invitations',
    'owner cannot enumerate invitations directly');

select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000003',true);
select is((select count(*) from public.households), 0::bigint, 'other household is hidden');
select is((select count(*) from public.household_members), 0::bigint, 'other memberships are hidden');
select throws_ok($$select public.join_household('INVALID')$$, 'P0001',
    'Invitation is invalid or unavailable', 'invalid code rejected');
select throws_ok('select public.create_invitation()', '42501', 'Household owner required',
    'non-owner cannot invite');

select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select is(public.join_household((select value from fixture where key='valid_code')),
    (select value::uuid from fixture where key='house_a'), 'valid code joins household');
select is((select role from public.household_members where user_id=auth.uid()), 'MEMBER', 'joiner is MEMBER');
select is((select count(*) from public.household_members), 2::bigint, 'both members are readable');
select is((select count(*) from public.profiles), 1::bigint, 'partner profile remains private');
select throws_ok($$select public.join_household((select value from fixture where key='valid_code'))$$,
    'P0001', 'Invitation is invalid or unavailable', 'used invitation rejected');
select throws_ok('select public.create_invitation()', '42501', 'Household owner required',
    'MEMBER cannot invite');

set local role postgres;
insert into public.invitations(household_id, code, created_by, created_at, expires_at)
values ((select value::uuid from fixture where key='house_a'), 'E0E0E0E0E0E0',
    '00000000-0000-0000-0000-000000000001', now()-interval '2 days', now()-interval '1 day');
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000003',true);
select throws_ok($$select public.join_household('E0E0E0E0E0E0')$$, 'P0001',
    'Invitation is invalid or unavailable', 'expired invitation rejected');
select throws_ok($$select public.join_household((select value from fixture where key='valid_code'))$$,
    'P0001', 'Invitation is invalid or unavailable', 'third member rejected');

select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select throws_ok('select public.create_invitation()', 'P0001', 'Household is full',
    'full household cannot create invitation');

reset role;
select throws_ok($$insert into public.household_members(household_id,user_id,role)
    values ((select value::uuid from fixture where key='house_a'),
    '00000000-0000-0000-0000-000000000003','MEMBER')$$, '23505', null,
    'database constraint enforces two-member maximum');

-- Build a second household to prove cross-household isolation in both directions.
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000003',true);
insert into fixture values ('house_b', public.create_household('다른 집')::text);
select is((select count(*) from public.households), 1::bigint, 'user sees only second household');
select is((select count(*) from public.household_members), 1::bigint, 'user sees only second membership');
select is((select count(*) from public.households where id=(select value::uuid from fixture where key='house_a')),
    0::bigint, 'first household remains inaccessible');
select throws_ok($$update public.household_members set role='OWNER'$$, '42501',
    'permission denied for table household_members', 'direct membership update denied');
select throws_ok('delete from public.household_members', '42501',
    'permission denied for table household_members', 'direct membership delete denied');

select * from finish();
rollback;
