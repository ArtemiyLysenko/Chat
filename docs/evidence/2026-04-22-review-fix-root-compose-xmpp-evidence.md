# Review Fix Evidence: Root Compose XMPP And Landing Page

- Date: 2026-04-22
- Requirement slice: close the reported review findings only
- Status: verified

## Root Cause
- The default root Compose path booted the app with `SPRING_PROFILES_ACTIVE=docker-local`.
- `apps/api/src/main/resources/application.yml` grouped `docker-local` with `local,test` and forced `chat.xmpp.enabled=false`.
- The result was a healthy HTTP app on `localhost:8080` with no live XMPP listener on `localhost:5222`, while README and Milestone 7.1 evidence claimed the opposite.

## Minimal Fix Applied
- Kept the root Compose default on `docker-local`.
- Split the profile overrides so only `local,test` disable XMPP.
- Left `docker-local` responsible only for `chat.auth.secure-cookie=false`, preserving plain-HTTP browser login for local review.
- Aligned `.env.example` with the actual local profile by setting `SPRING_PROFILES_ACTIVE=docker-local`.
- Updated the landing page copy in `apps/api/src/main/resources/static/index.html` so `/` reflects the completed Milestones 1 through 8 product state instead of a Milestone 1-only slice.
- Updated README Compose defaults to describe the real root-compose behavior.

## Verification Run
- Targeted XMPP tests:
  - Command: `./gradlew :apps:api:test --tests '*XmppIntegrationTests' --no-daemon`
  - Result: passed
- Full regression:
  - Command: `./gradlew test --no-daemon`
  - Result: passed
- Compose config validation:
  - Command: `docker compose -f /Users/artemiy/Projects/Chat/compose.yaml config -q`
  - Result: passed
- Root compose runtime:
  - Command: `docker compose -f /Users/artemiy/Projects/Chat/compose.yaml up -d --build`
  - Result: passed
- Health endpoint:
  - Command: `curl -i --max-time 20 http://localhost:8080/actuator/health`
  - Result: `HTTP/1.1 200` with `{"status":"UP"}`
- Landing page:
  - Command: `curl -i --max-time 20 http://localhost:8080/`
  - Result: `HTTP/1.1 200` and HTML headed by `Milestones 1-8 Complete`
- Live XMPP listener proof:
  - Command: `docker compose -f /Users/artemiy/Projects/Chat/compose.yaml logs --tail=200 app`
  - Result: included `XMPP adapter listening on 5222`
- Real XMPP runtime check against `localhost:5222`:
  - Manual probe: opened a raw XMPP stream to `localhost:5222`, received the pre-auth stream and SASL `PLAIN` features, authenticated a throwaway account over SASL `PLAIN`, reopened the post-auth stream, and completed resource bind as `xmppprobe1776854628@localhost/probe`
  - Result: passed
- Adjacent local-browser auth sanity check:
  - Command: login over HTTP after the compose fix
  - Result: `HTTP/1.1 200` with `Set-Cookie: CHAT_SESSION=...; HttpOnly; SameSite=Lax` and no `Secure` attribute, confirming the root local profile still works over plain HTTP
- Teardown:
  - Command: `docker compose -f /Users/artemiy/Projects/Chat/compose.yaml down -v`
  - Result: passed

## Reviewer Pass
- Checked for regressions in local browser auth because changing the compose profile could have reintroduced secure-cookie breakage over HTTP.
- No additional issues found in the final diff.

## Remaining Known Risk
- The XMPP live proof used a raw socket probe rather than a third-party desktop XMPP client, but it exercised the actual root-compose listener through stream open, SASL auth, and bind on `localhost:5222`.
