# RelayBot

A Discord bot that turns `/report` into a tracked, prioritised report: it answers in Discord, posts the report to your team's channel with **Acknowledge** / **Escalate** buttons, mirrors it to Slack (or a second Discord channel), and shows everything live on an admin dashboard.

It uses Discord's **HTTP Interactions Endpoint** (no gateway connection): Discord POSTs each interaction to this app, which must verify it and answer within 3 seconds.

| | |
|---|---|
| **Live app** | `https://relaybot-7a8u.onrender.com` |
| **Admin login** | username `admin@yopmail.com` / password configured via `ADMIN_PASSWORD` env var |
| **Add the bot to a server** | Sign in, open **Servers**, choose **Connect a server** |
| **Stack** | Spring Boot 3.3 (Java 21), Angular 20, Postgres (Neon), Groq, Render. All free tiers, no card. |

---

## What happens when someone runs `/report`

```
Discord ──POST /api/discord/interactions──▶ RelayBot
                                              │ 1. verify Ed25519 signature on the raw body   (else 401)
                                              │ 2. PING? answer PONG (no database needed)
                                              │ 3. one DB transaction:
                                              │      insert interaction (primary key = dedup)
                                              │      insert report
                                              │      enqueue TRIAGE job
                                              │ 4. answer "type 5: thinking…"            ◀── well inside 3 s
                                              ▼
                                   job worker (retries with backoff)
                                   TRIAGE_REPORT: AI summary (optional) + priority rule
                                     ├─▶ REPLY_TO_USER     edits the "thinking…" message
                                     ├─▶ POST_TO_CHANNEL   report card with buttons
                                     └─▶ MIRROR            Slack / Discord webhook
Button click ──▶ verify, dedup, update status (conditional) ──▶ "type 6" ──▶ REFRESH card (+ MIRROR)
```

Each arrow after the worker is a separate row in the `jobs` table. If Slack is down, only the mirror waits and retries; the reply in Discord is not held up.

## How each requirement is met

| Requirement | Where |
|---|---|
| Verify Ed25519 signature, reject bad ones | `discord/SignatureVerifier.java` (raw bytes, JDK Ed25519, timestamp freshness), `InteractionController` returns 401 |
| PING / PONG | `InteractionController` (answered before any DB access) |
| Types 1, 2, 3, 5; respond within 3 s | `InteractionService` answers 4 (reply), 5 (deferred), 6 (deferred update), 9 (modal) |
| Admin login | `SecurityConfig`, `auth/AuthController` (session cookie, CSRF, login throttling) |
| Connect a server, choose a channel | `discord/ConnectController` (OAuth install with `state` check), server settings page |
| At least 2 slash commands | `/report [text]` and `/status`, registered by `CommandRegistrar` |
| Record, apply a rule, respond, mirror | `interactions` + `reports` tables, `report/RuleEngine`, jobs `REPLY_TO_USER` / `POST_TO_CHANNEL` / `MIRROR` |
| Dashboard: live log + command config | **Live log** (Server-Sent Events), **Reports**, **Queue**, **Servers** pages |
| Deployed publicly | Render (Docker), `render.yaml` |
| **Stretch:** configurable rules UI | Keyword rules editor per server |
| **Stretch:** buttons | Acknowledge / Escalate on the channel card, isolated per server |
| **Stretch:** modal | `/report` with no text opens a form |
| **Stretch:** AI step | Groq triage (summary, category, suggested priority) running from the queue |
| **Stretch:** multi-server | Every table is keyed by `guild_id`; buttons can only touch reports from their own server |
| **Stretch:** observability | Structured JSON logs with `interactionId`/`guildId`/`jobId`, live log, failed-job view with **Retry**, blocked-request counter |

## Reliability and security decisions

- **Forged requests**: signature checked over the exact bytes received, before parsing. Parsing and re-serialising JSON changes the bytes and would break verification.
- **Replays**: the signed timestamp must be within 5 minutes; inside that window the interaction id primary key rejects repeats (`INSERT … ON CONFLICT DO NOTHING`), and a repeat gets "already processed" with no side effects.
- **3-second window**: only fast DB work happens in the request. AI calls, follow-ups and webhooks run from the durable job queue.
- **Don't lose work**: the interaction, the report and its first job commit in one transaction. Jobs survive restarts (`RUNNING` jobs are re-queued on startup; stuck ones are reclaimed after 2 minutes). Failures retry at 5 s, 10 s, 20 s … up to 10 minutes, honouring Discord's `Retry-After` on 429. What still fails appears under **Queue** with a Retry button.
- **Database down during a command**: the transaction rolls back and the user is told, in Discord, that nothing was recorded. Nothing is half-saved.
- **AI down**: triage retries a few times, then the report continues without AI. An optional step never blocks the report.
- **Secrets**: only in environment variables. Mirror webhook URLs are encrypted in the database (AES-GCM) and write-only in the API. Error messages pass through a redactor because HTTP client exceptions include URLs, and Discord interaction URLs contain tokens. Interaction tokens (valid 15 minutes) live only in job payloads, which the API never returns.
- **Other hardening**: `allowed_mentions: none` on everything the bot posts (a report containing `@everyone` pings no one); Slack control characters escaped; mirror URLs restricted to Slack/Discord webhook hosts (no SSRF); OAuth `state` checked; strict CSP; login throttled; 64 KB body limit.
- **Free-tier fit**: the worker polls the database only when a job was just enqueued or a retry is due (plus an hourly safety sweep), and `/healthz` never touches the database, so Neon can scale to zero while the web service is kept warm.

## Setup

### 1. Discord application
1. Create an application at <https://discord.com/developers/applications>.
2. **General Information**: copy the **Application ID** and **Public Key**.
3. **Bot**: *Reset Token* and copy it. No privileged intents are needed.
4. **OAuth2**: copy the **Client Secret**, and add the redirect `https://<your-app>/api/discord/oauth/callback`.
5. After the app is deployed (step 4 below), set **Interactions Endpoint URL** (General Information) to `https://<your-app>/api/discord/interactions` and save. Discord sends a PING and a deliberately bad signature; saving succeeds only if both are handled correctly.

### 2. Database (Neon, free)
Create a project in the same region as Render (Oregon, AWS us-west-2). Take the **direct** connection string (host without `-pooler`) and split it:
`postgresql://USER:PASS@HOST/DB?sslmode=require` → `DATABASE_URL=jdbc:postgresql://HOST/DB?sslmode=require`, `DATABASE_USERNAME=USER`, `DATABASE_PASSWORD=PASS`. Flyway creates the tables on startup.

### 3. AI (optional, Groq, free)
Create a key at <https://console.groq.com> and set `GROQ_API_KEY`. Without it, AI triage is simply off.

### 4. Deploy on Render (free)
**New → Blueprint**, select this repository; `render.yaml` creates a Docker web service. Fill in the secret values (see `.env.example`), with `PUBLIC_BASE_URL` set to the Render URL. Then finish step 1.5.

### 5. Keep it warm
Render's free service sleeps after 15 idle minutes, and a cold start is slower than Discord's 3 seconds. Set the repository variable `RELAYBOT_URL` (Settings → Variables → Actions) so `.github/workflows/keepalive.yml` pings `/healthz` every 10 minutes, or add the same URL to cron-job.org.

### 6. Connect a server
Sign in, **Servers → Connect a server**, pick the server, then choose the report channel, rules and (optionally) a Slack incoming webhook.

## Running locally

```bash
docker compose up -d                      # Postgres on :5432
cp .env.example .env                      # fill in the Discord values, APP_ENCRYPTION_KEY, admin login
set -a; . ./.env; set +a
cd backend && mvn spring-boot:run         # API on :8080
cd frontend && npm install && npm start   # dashboard on :4200, proxies /api to :8080
```
Discord must reach your machine over HTTPS: run a tunnel (`cloudflared tunnel --url http://localhost:8080`), use its URL as `PUBLIC_BASE_URL` and in the Developer Portal. Tests: `cd backend && mvn verify`.

## How to test it (for reviewers)

**Happy path**
1. Sign in with the throwaway admin login above. Open **Live log** in one tab.
2. In the test server (invite: `<fill in>`), run `/report text: the login page has an outage`. You'll see "thinking…", then a reply with priority **High** (keyword rule). The card appears in the report channel and the Slack channel gets a notification. The live log shows each step as it happens.
3. Run `/report` with no text: a form opens. Run `/status` for open counts.
4. Click **Acknowledge** or **Escalate** on the card: the card updates and the change is mirrored.

**Unhappy paths**
```bash
URL=https://<your-app>

# Unsigned request → 401
curl -i -X POST "$URL/api/discord/interactions" -H 'content-type: application/json' -d '{"type":1}'

# Forged signature → 401
curl -i -X POST "$URL/api/discord/interactions" -H 'content-type: application/json' \
  -H "X-Signature-Ed25519: $(printf '0%.0s' $(seq 128))" -H "X-Signature-Timestamp: $(date +%s)" -d '{"type":1}'

# Dashboard API without a session → 401
curl -i "$URL/api/reports"
```
Blocked requests are counted on the Live log page. To see downstream failure handling, save a Slack webhook URL that doesn't exist (e.g. `https://hooks.slack.com/services/T000/B000/nope`) and file a report: the reply and channel post still arrive, the mirror fails, and it shows under **Queue** with a Retry button. An invalid `GROQ_API_KEY` shows the "continuing without AI" path.

## Known limits and trade-offs

- **Single instance.** Job claiming is safe across instances (`FOR UPDATE SKIP LOCKED`), but "re-queue RUNNING jobs on startup" assumes one instance, which is what the free tier runs.
- **Mirror is at-least-once.** Webhooks have no idempotency key; a crash between Slack accepting a message and the job being marked done can send it twice. Channel posts avoid this by storing the message id.
- **If the whole service is down, Discord does not retry.** The user sees "The application did not respond". Keep-alive and a fast in-request path make this rare; it can't be fixed from our side.
- **One admin account** from environment variables. Enough for this scope; real use would need Discord-login for server admins, with each admin seeing only their servers.

## Project layout

```
backend/   Spring Boot app (package dev.relaybot)
  discord/   signature check, interaction endpoint, OAuth connect, Discord REST client
  jobs/      durable queue, worker, one handler per step
  report/    rule engine, report storage, message formatting
  guild/     per-server settings API     mirror/  webhook target + client
  ai/        Groq triage                 activity/ live log + SSE
  resources/db/migration/V1__init.sql
frontend/  Angular dashboard (built into the backend's static files by the Dockerfile)
CLAUDE.md  context file used with the AI coding assistant
AI_NOTES.md
```
