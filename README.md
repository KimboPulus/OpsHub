# OpsHub

Small production issue tracker/demo app. I made it as a Java + React version of my earlier Fortaco Ops Hub project.

It is about a factory floor: machines, production orders, downtime, issue handoff, comments, attachments, reports and a small ERP-style API.

## What is inside

- Java Spring Boot backend
- H2 local database
- React frontend in `frontend`
- seeded demo data on first run
- CSV and PDF exports
- image upload for issue attachments
- simple Cloudflare tunnel script for showing the app outside localhost
- 44 backend tests

## Screens

![screen 1](docs/screenshots/screenshot-01.png)
![screen 2](docs/screenshots/screenshot-02.png)
![screen 3](docs/screenshots/screenshot-03.png)
![screen 4](docs/screenshots/screenshot-04.png)
![screen 5](docs/screenshots/screenshot-05.png)
![screen 6](docs/screenshots/screenshot-06.png)
![screen 7](docs/screenshots/screenshot-07.png)
![screen 8](docs/screenshots/screenshot-08.png)
![screen 9](docs/screenshots/screenshot-09.png)
![screen 10](docs/screenshots/screenshot-10.png)
![screen 11](docs/screenshots/screenshot-11.png)

## Run locally

Backend:

```powershell
cd C:\Users\Max\IdeaProjects\OpsHub
.\mvnw.cmd spring-boot:run
```

Frontend:

```powershell
cd C:\Users\Max\IdeaProjects\OpsHub\frontend
npm install
npm run dev
```

Open:

```text
http://127.0.0.1:5173
```

The frontend proxies `/api`, `/exports` and `/uploads` to the backend on port `8080`.

## Stop it

In each terminal press:

```text
Ctrl+C
```

If something is still holding the ports, close the Java or Node process from Task Manager.

## Tests

Backend:

```powershell
cd C:\Users\Max\IdeaProjects\OpsHub
.\mvnw.cmd test
```

Frontend build check:

```powershell
cd C:\Users\Max\IdeaProjects\OpsHub\frontend
npm run build
```

## Cloudflare temporary tunnel

This is for a quick free public link, not a real production deployment.

```powershell
cd C:\Users\Max\IdeaProjects\OpsHub
.\scripts\Start-OpsHubCloudflareTunnel.ps1
```

The script starts the backend, starts the frontend, downloads `cloudflared` if it is missing, and opens a temporary `trycloudflare.com` tunnel to the frontend.

Keep that terminal open while using the public link. Stop it with `Ctrl+C`.
