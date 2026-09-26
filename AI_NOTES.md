# AI_NOTES

> **Before submitting:** the sections marked *(write this yourself)* must be in your own words and
> describe what actually happened. Reviewers read this file for honesty, not polish.

## Tools used

- **Claude (Anthropic), in a chat conversation.** Used to read the assignment with me, compare hosting
  options (Render vs Railway vs Vercel for a Spring Boot app, Neon for Postgres, Groq for the free AI
  step), and generate the initial full project scaffold: backend, Angular dashboard, Dockerfile, Render
  blueprint, README and this file's structure.
- In that session the assistant could not reach Maven Central, so it compiled and tested only the
  dependency-free classes (signature verification, encryption, rule engine, webhook validation, redaction)
  with a throwaway harness, syntax-checked all Java files, and fully built the Angular app. The Spring
  code was first compiled and run by me: *(write this yourself: what failed on the first `mvn verify` /
  first deploy and what you changed)*.
- `CLAUDE.md` is the context file I used for later work with the assistant.
- *(Add any other tools you used: Copilot, ChatGPT, Claude Code, etc.)*

## Decisions I made myself *(write this yourself)*

Pick 2–3 real ones and say what you chose, what the alternative was, and why. Examples of the kind of
decisions in this project (only use ones that were genuinely yours):
- Stack: Spring Boot + Angular (my strongest stack) over a Node/Next.js setup that would have been simpler to host.
- Hosting: Render with a keep-alive ping instead of Vercel (can't run a long-lived JVM) or Railway (credit-based free tier).
- AI provider: Groq inside the app rather than the Claude API, because the assignment requires free tiers.

## Hardest bug the AI introduced *(write this yourself)*

Describe one bug that came from AI-generated code, how you noticed it, how you found the cause, and the
fix. It must be a real one from your own debugging.

For context only (not a substitute for your own), from the generation session:
- **Actually happened:** an edit put a code comment on the same line as a chained method call, silently
  commenting out `.contentType(...).body(...)` on the Slack/Discord mirror request. Caught by reading the diff.
- **Prevented, not observed:** Angular's production build inlines critical CSS with an inline `onload`
  handler, which the app's strict Content-Security-Policy would block; `inlineCritical` was turned off.
  Spring Security re-checks authorisation on async dispatches, a known cause of broken Server-Sent Events;
  ASYNC/ERROR/FORWARD dispatches are permitted.

## What I would improve with more time *(edit to match your view)*

- Integration tests with Testcontainers Postgres for dedup, job claiming and the full `/report` flow,
  plus a signed-request test harness that exercises the real controller.
- Discord-login for admins so each server's own admins manage it, instead of one shared admin account.
- A transactional outbox for channel posts using Discord's `nonce` + `enforce_nonce` to make them exactly-once.
- Metrics (Micrometer) for request latency against the 3-second budget and job retry rates.
