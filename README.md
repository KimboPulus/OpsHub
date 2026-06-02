# OpsHub

OpsHub is a small factory-floor operations app.

The app is built around production issues: machines, work orders, downtime, comments, handoff to teams, attachments, reports and a small simulated ERP API. It is not meant to be a finished commercial system, but it is more than just a CRUD table. I wanted it to feel like something that could actually sit on a shift leader's screen.

The IoT tab is wired to a separate local Python/FastAPI service that I keep outside this repo as its own PyCharm project. OpsHub only contains the Spring gateway and UI for it. That keeps the Java app clean, while the telemetry simulator can still run as a separate service on `localhost:8000`.

## Stack

- Spring Boot
- Spring Data JPA
- H2 database for local demo data
- React + Vite
- plain CSS
- Maven wrapper
- npm lockfile

## Main parts

- production issue dashboard
- issue creation flow for operators
- machine QR entry screen
- issue details with status changes and comments
- image attachments
- similar issue lookup
- KPI/reporting page
- CSV export
- weekly PDF export
- simulated ERP schedule endpoint
- external Factory IoT telemetry dashboard through a small Spring gateway
- basic security headers and upload checks
- backend tests for the main app and the IoT gateway

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

## Project shape

```text
src/main/java        backend code
src/test/java        backend tests
frontend             React app
docs/screenshots     screenshots for this README
```

## Notes

The backend seeds a few demo production lines, machines, work orders and issues, so the app has something to show immediately.

The frontend talks to the backend through `/api`, `/exports` and `/uploads`. The IoT page also talks to `/api/iot/...`; those requests go to Spring first, and Spring calls the external Python telemetry service.

The tests cover the important behavior: issue rules, login/session security, seeding, API endpoints, comments, status changes, CSV/PDF exports, upload validation and the IoT gateway.
