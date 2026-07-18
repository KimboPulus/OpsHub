# Deployment notes

Repository attributes keep `mvnw` on LF line endings so Linux container builds
also work from Windows clones.

## Local demo

Default `local` profile uses H2 file database and seeded data. Suitable for
screenshots and workflow demonstration; not durable production configuration.

## Compose stack

```bash
cp .env.example .env
docker compose up --build
```

Compose starts PostgreSQL, Spring API, and nginx-served frontend. Backend waits
for migration and exposes `/actuator/health`. Readiness check includes database
schema access through custom `opshubReadiness` indicator.

Required production values:

- unique `DATABASE_USERNAME` and `DATABASE_PASSWORD`;
- strong `OPSHUB_SECURITY_PASSWORD`;
- exact `OPSHUB_SECURITY_ALLOWED_ORIGINS`;
- backed-up persistent PostgreSQL volume;
- external TLS/reverse proxy.

Repository does not configure TLS, secret manager, centralized logs, object
storage, alerting, or backup scheduler. Compose demonstrates service wiring; it
is not turnkey factory deployment.
