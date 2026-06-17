# Hosted deploy

> Setup steps for the parts of Rack that are deferred to the managed/hosted
> environment and have no local substitute. Local development needs none of
> these (see `docs/workflow.md` -> Local development); they are run once, by a
> human, against the managed Supabase project and Firebase.

## Push notifications: Database Webhook on `notifications`

The agent-update push chain (`docs/specs/spec-push-notifications.md`) is driven
by a row in `public.notifications` (written locally by the
`notify_agent_plan_update` trigger; migration
`20260617190000_create_device_tokens_and_notifications.sql`). In production, an
INSERT on that table must fan out over FCM via the `push-on-notification` Edge
Function. Supabase does not create that wiring from a migration — it is a
managed Database Webhook configured once per project.

Prerequisites (also tracked in the spec's Human prerequisites — deferred):

- Managed Supabase project with Edge Functions enabled and the Supabase CLI
  authenticated (`SUPABASE_ACCESS_TOKEN`, `SUPABASE_PROJECT_REF`).
- The `push-on-notification` Edge Function deployed
  (`supabase functions deploy push-on-notification`).
- A Firebase project + FCM service-account JSON set as the Supabase secret
  `FCM_SERVICE_ACCOUNT`, and a webhook verification secret set as
  `PUSH_WEBHOOK_SECRET` (`supabase secrets set ...`).

Webhook setup (Supabase Dashboard -> Database -> Webhooks -> Create a new hook):

- Name: `push_on_notification`.
- Table: `public.notifications`.
- Events: `INSERT` only.
- Type: `Supabase Edge Functions` (or HTTP request).
- URL: the deployed function URL,
  `https://<project-ref>.functions.supabase.co/push-on-notification`.
- HTTP headers: add the verification header the function checks, carrying the
  `PUSH_WEBHOOK_SECRET` value, so a forged/replayed call is rejected.

The function reads the owning user's `active=true` `device_tokens` rows
(service-role — managed server infra, not the rack-MCP) and sends an FCM HTTP v1
`notification`+`data` message carrying `plan_id` for tap deep-linking; it
hard-deletes any token FCM reports as `UNREGISTERED`/`NOT_FOUND`.
