# Deployment and Closed-Pilot Guidance

## Product boundary

This repository is a React/Vite frontend plus Spring Boot API, PostgreSQL,
and Redis. Treat the next release target as a **Closed Pilot** for 3-5 users,
not public production.

Do not equate a successful build with deployment readiness. A release claim
requires runtime evidence: a real PostgreSQL migration, a browser smoke test,
configuration validation, and recorded results.

## Deployment rules

- Keep the frontend's `/api` and credentialed refresh-cookie contract intact.
  Prefer same-domain routing through a reverse proxy. If frontend and API use
  different origins, explicitly configure a production CORS allowlist and
  verify `Secure`, `HttpOnly`, and `SameSite` cookie behaviour in a browser.
- `SPRING_PROFILES_ACTIVE=prod` must be set in the deployment environment.
  Supply `SPRING_DATASOURCE_*`, `JWT_SECRET`, `ADMIN_*`, and Redis settings as
  platform secrets; never commit their real values.
- Use `spring.jpa.hibernate.ddl-auto=validate` and Flyway as the only schema
  writer. `FLYWAY_BASELINE_ON_MIGRATE` must be `false` for a new database. It
  may be true only once for a backed-up, verified legacy database, then must
  be reset to false.
- Add an explicit deploy contract before claiming readiness: platform manifest
  or Dockerfiles, frontend static/SPA fallback hosting, backend start command,
  environment-variable documentation, health-check route, and rollback notes.
- A platform health check must be able to reach the intended health endpoint;
  test its authorization rather than assuming `/actuator/health` is public.
- Managed Redis may require authentication and TLS. Configure and test those
  requirements, including graceful behaviour when Redis is unavailable.

## Closed-pilot gates

1. Run Flyway V1-V10 against a new PostgreSQL database and record the result.
2. Run an end-to-end browser smoke flow with two accounts: OWNER creates a
   project/member/task; MEMBER completes a submission; OWNER/MANAGER reviews
   it. Include refresh, logout, deep-link, and unauthorized-access checks.
3. Finish persistent Notification MVP before adding WebSocket: post-commit
   creation for assignment/submission/request-changes/approval; REST list with
   pagination, unread count, mark-one-read, mark-all-read; recipient-only
   authorization; and UI loading/empty/error states.
4. Decide evidence scope explicitly. For link-only pilot, keep upload controls
   unavailable and document the limitation. For file upload, provide a real
   S3/MinIO-compatible `EvidenceStorage` implementation and verify presigned
   upload, metadata validation, failure, and retry behaviour.
5. Update README with the current Project/Member/Submission/Review workflow,
   deployment setup, pilot limitations, and two-account test instructions.

## Quality work before public release

- Add frontend tests with Vitest and React Testing Library for workflow and
  authorization-sensitive UI.
- Add PostgreSQL Testcontainers coverage for Flyway, constraints, locking, and
  important transactions; H2 does not prove these behaviours.
- Add CI for backend tests, frontend lint/build, and at least one browser E2E
  workflow. Keep deployment artifacts versioned and define rollback.
- Before public access, add invitations/account recovery, backup-and-restore
  verification, monitoring/alerts, rate limiting, audit trail, security review,
  and production-domain cookie/CORS validation.

## Scope discipline

Do not add WebSocket, Kafka/RabbitMQ, microservices, Kubernetes, or AI scoring
to make the project appear more advanced. They are deferred until a measured
product need exists and the persistent core workflow is verified.
