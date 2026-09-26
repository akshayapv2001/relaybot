# CLAUDE.md

Context for AI coding assistants working in this repository.

## What this is
RelayBot: a Discord bot using the HTTP Interactions Endpoint (no gateway) plus an admin dashboard.
Spring Boot 3.3 / Java 21 backend in `backend/`, Angular 20 dashboard in `frontend/`. The Dockerfile
builds the dashboard into the backend's static files so both are served from one origin on Render.
Postgres (Neon) via Flyway migrations and `JdbcClient` with explicit SQL (no JPA).

## Commands
- Backend tests: `cd backend && mvn verify`
- Run backend: `set -a; . ./.env; set +a; cd backend && mvn spring-boot:run`
- Dashboard dev server: `cd frontend && npm start` (proxies `/api` to :8080)
- Dashboard build: `cd frontend && npx ng build`

## Rules that must not be broken
1. **Verify before parsing.** `/api/discord/interactions` binds the body as `byte[]` and verifies the
   Ed25519 signature on those exact bytes. Never bind it to a DTO/JsonNode first.
2. **Nothing slow inside the interaction request.** Discord allows 3 seconds. The request path may only
   do local DB work and must answer with type 4/5/6/9. AI calls, follow-ups and webhooks go through `JobQueue`.
3. **PING must not need the database.** It is handled in the controller before any transaction starts.
4. **Dedup is the `interactions` primary key.** New side effects for an interaction must happen in the
   same transaction as `InteractionRepository.tryInsert`, and only when it returns true.
5. **Job handlers must be safe to re-run.** A crash after the side effect but before `markDone` re-runs
   the job. Prefer idempotent calls (PATCH @original) or record what was done (channel_message_id).
6. **Throw the right exception.** `RetryableException` for timeouts/5xx/429, `PermanentException` for
   other 4xx. Use `HttpCalls.execute` for every outbound HTTP call.
7. **Never log or return secrets.** No URLs of webhooks or interaction follow-ups in messages (they contain
   tokens); pass error text through `Redactor`. Mirror URLs are encrypted with `SecretBox` and never returned
   by the API. Job payloads are never exposed by the API.
8. **Every query about server data is scoped by `guild_id`.** Buttons must check the report belongs to
   the interaction's guild.
9. **Everything the bot posts sets `allowed_mentions: {parse: []}`.** User text must never ping anyone.
10. **`/healthz` must not touch the database**, and the job worker must not poll on a fixed short interval
    (Neon free tier scales to zero; see `JobSignal`).

## Conventions
- New schema changes: add `V<n>__description.sql`; never edit an applied migration.
- User-facing text (Discord replies, dashboard copy): plain, specific, sentence case; errors say what to do.
- Activity log entries (`ActivityService`) are for humans reading the dashboard; structured log fields
  come from MDC (`interactionId`, `guildId`, `reportId`, `jobId`).
