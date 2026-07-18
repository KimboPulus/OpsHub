# OpsHub

OpsHub is a factory-floor workflow prototype centered on production issues:
machines, work orders, downtime, comments, role handoff, attachments, and
reports. IoT readings and ERP responses are simulated locally. Power/BI screen
shows reporting mappings; it is not connected to Microsoft tenant or factory
system.

Exact ownership and integration boundaries are documented in
[architecture](docs/architecture.md) and [current limitations](docs/limitations.md).

## Stack

- Spring Boot
- Spring Data JPA
- PostgreSQL production profile
- H2 local profile for quick demo data
- Flyway database migrations
- React + Vite
- plain CSS
- Maven wrapper
- npm lockfile
- Docker Compose
- GitHub Actions CI
- Java/Spring IoT telemetry module
- SQL reporting views for Power BI-style dashboards
- Actuator health/info endpoints and Docker health checks

## Main parts

- production issue dashboard
- issue creation flow for operators
- machine QR entry screen
- issue details with status changes and comments
- operator/leader workflow permissions
- response and resolution SLA tracking
- auditable actor metadata on issue changes
- image attachments
- similar issue lookup
- KPI/reporting page
- CSV export
- weekly PDF export
- simulated ERP schedule endpoint
- Factory IoT telemetry dashboard backed directly by Spring and JPA
- Power Platform / BI readiness screen
- conceptual Dataverse-style table mapping shown in the UI
- example Power Automate flow cards, without live tenant connection
- SQL reporting views for issue aging, downtime, OEE proxy and energy per unit
- reporting API endpoint for the Power/BI screen
- basic security headers and upload checks
- backend tests for the main app and the IoT module

## Screenshots

![OpsHub screenshot 1](docs/screenshots/screenshot-12.png)
![OpsHub screenshot 1](docs/screenshots/screenshot-01.png)
![OpsHub screenshot 2](docs/screenshots/screenshot-02.png)
![OpsHub screenshot 3](docs/screenshots/screenshot-03.png)
![OpsHub screenshot 4](docs/screenshots/screenshot-04.png)
![OpsHub screenshot 5](docs/screenshots/screenshot-05.png)
![OpsHub screenshot 6](docs/screenshots/screenshot-06.png)
![OpsHub screenshot 7](docs/screenshots/screenshot-07.png)
![OpsHub screenshot 8](docs/screenshots/screenshot-08.png)
![OpsHub screenshot 9](docs/screenshots/screenshot-09.png)
![OpsHub screenshot 10](docs/screenshots/screenshot-10.png)
![OpsHub screenshot 11](docs/screenshots/screenshot-11.png)
![OpsHub screenshot 11](docs/screenshots/screenshot-13.png)

## Project shape

```text
src/main/java        backend code
src/test/java        backend tests
frontend             React app
scripts              SQL reporting view scripts and Power BI sample queries
docs/screenshots     screenshots for this README
```

## Evidence and boundaries

Backend seeds demo lines, machines, orders, and issues. Tests cover issue rules,
login/session security, workflow permissions, API, exports, uploads, IoT
analytics, and reporting views. E2E smoke exercises operator/leader boundary in
real Chromium and uploads screenshot artifact.

- [Architecture and ownership](docs/architecture.md)
- [Deployment notes](docs/deployment.md)
- [Flyway migration story](docs/migrations.md)
- [Current limitations](docs/limitations.md)

## Run locally

Backend:

```bash
./mvnw spring-boot:run
```

Frontend:

```bash
cd frontend
npm ci
npm run dev
```

Open `http://127.0.0.1:5173`.

Default local login:

- `lider / opshub`
- `operator / opshub`

The default profile is `local`. It uses H2 at `./data/fortaco-opshub`, Flyway migrations, seeded demo data and the local Vite origins.

If an old local H2 database was created before Flyway was added, delete the ignored `data/` folder and start again.

## Run with Docker Compose

Copy environment template:

```bash
cp .env.example .env
```

Set real values in `.env`, then run:

```bash
docker compose up --build
```

Services:

- frontend: `http://localhost:5173`
- backend API: `http://localhost:8080`
- PostgreSQL: `localhost:5432`

The Compose stack runs the backend with `SPRING_PROFILES_ACTIVE=prod`, PostgreSQL, Flyway migrations and an nginx-served frontend that proxies `/api`, `/exports` and `/uploads` to the backend container.

Compose proves service wiring, not production readiness. See
[deployment notes](docs/deployment.md) for missing TLS, secret management,
backups, and operational services.

## Configuration

Important environment variables:

- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `OPSHUB_SECURITY_PASSWORD`
- `OPSHUB_SECURITY_ALLOWED_ORIGINS`

The `prod` profile requires database credentials and `OPSHUB_SECURITY_PASSWORD`. The `local` profile keeps safe demo defaults for fast development.

## Quality checks

Backend:

```bash
./mvnw test
```

Frontend:

```bash
cd frontend
npm ci
npm run build
```

End-to-end smoke:

```bash
cd frontend
npm ci
npm run e2e:install
npm run e2e
```

GitHub Actions runs backend tests, frontend build and Docker image build checks on push and pull request.
The e2e smoke starts the Spring backend on port `18080`, starts Vite on `5173`, logs in as leader and operator, creates an issue, checks the forbidden operator resolve action, resolves as leader and verifies the audit trail. It also writes a real Chromium screenshot to `frontend/test-results/opshub-workflow-evidence.png`, which CI uploads as an artifact.

## Operational checks

Health endpoint:

```bash
curl http://localhost:8080/actuator/health
```

The Docker image and Compose API service use that endpoint as the readiness check. The custom `opshubReadiness` health indicator verifies that the application schema is reachable through the database connection.

Audit export:

```bash
curl -o audit-events.csv http://localhost:8080/exports/audit-events.csv
```

Live updates:

```bash
curl -N http://localhost:8080/api/events/operations
```

The live stream is session-protected in normal browser usage, so the UI is the easiest way to demo it: log in, open two browser windows, change an issue status in one window and watch the other window refresh through the live operations stream.
