-- Push-notification schema: per-user device_tokens + notifications, plus a
-- SECURITY DEFINER trigger that records an agent-plan-update notification when an
-- agent (`source='agent'`) edits a plan-structure row (see
-- docs/specs/spec-push-notifications.md, Data contract).
--
-- Both tables are user-owned and ship their `user_id = auth.uid()` owner policy in
-- this same migration. The notification-producing trigger fires only on the three
-- plan-structure tables (plans, plan_days, plan_exercises), never on set_logs or
-- artifacts, and only for agent writes; it resolves user_id/plan_id by walking up
-- to plans and writes only to notifications (no recursion). A Database Webhook on
-- INSERT of public.notifications -> the push-on-notification Edge Function fans the
-- row out over FCM; that webhook is a hosted-deploy prerequisite documented in
-- docs/hosted-deploy.md and is not created by this migration.

-- device_tokens: one row per signed-in device, holding its FCM registration token.
create table public.device_tokens (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  token text not null unique,
  platform text not null default 'android',
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.device_tokens enable row level security;

create policy "device_tokens_owner" on public.device_tokens
  for all
  to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create trigger device_tokens_set_updated_at
  before update on public.device_tokens
  for each row
  execute function public.set_updated_at();

grant select, insert, update, delete on public.device_tokens to authenticated;

-- notifications: audit/feed of agent plan-structure updates; the webhook source.
create table public.notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  type text not null,
  plan_id uuid not null,
  title text not null,
  body text,
  source edit_source not null default 'agent',
  created_at timestamptz not null default now()
);

alter table public.notifications enable row level security;

create policy "notifications_owner" on public.notifications
  for all
  to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

grant select, insert, update, delete on public.notifications to authenticated;

-- Resolve the owning user_id, plan_id, and plan name for an agent-sourced
-- plan-structure write and insert a notifications row. SECURITY DEFINER so the
-- insert is not blocked by the notifications owner RLS policy; it writes only to
-- notifications, never back to a plan table, so it cannot recurse. A NULL guard
-- skips a malformed row instead of erroring.
create or replace function public.notify_agent_plan_update()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  resolved_user_id uuid;
  resolved_plan_id uuid;
  resolved_plan_name text;
begin
  if new.source <> 'agent' then
    return new;
  end if;

  if tg_table_name = 'plans' then
    resolved_plan_id := new.id;
    resolved_user_id := new.user_id;
    resolved_plan_name := new.name;
  elsif tg_table_name = 'plan_days' then
    resolved_plan_id := new.plan_id;
    select p.user_id, p.name into resolved_user_id, resolved_plan_name
    from public.plans p where p.id = new.plan_id;
  elsif tg_table_name = 'plan_exercises' then
    select d.plan_id, p.user_id, p.name
    into resolved_plan_id, resolved_user_id, resolved_plan_name
    from public.plan_days d join public.plans p on p.id = d.plan_id
    where d.id = new.day_id;
  end if;

  if resolved_user_id is null or resolved_plan_id is null then
    return new;
  end if;

  insert into public.notifications (user_id, type, plan_id, title, body, source)
  values (
    resolved_user_id,
    'agent_plan_update',
    resolved_plan_id,
    'Your agent updated your plan',
    resolved_plan_name,
    'agent'
  );

  return new;
end;
$$;

-- Fire on INSERT and UPDATE of the three plan-structure tables only. The
-- source='agent' filter lives inside the function so all three triggers share one
-- definition; set_logs and artifacts deliberately carry no such trigger.
create trigger plans_notify_agent_plan_update
  after insert or update on public.plans
  for each row
  execute function public.notify_agent_plan_update();

create trigger plan_days_notify_agent_plan_update
  after insert or update on public.plan_days
  for each row
  execute function public.notify_agent_plan_update();

create trigger plan_exercises_notify_agent_plan_update
  after insert or update on public.plan_exercises
  for each row
  execute function public.notify_agent_plan_update();
