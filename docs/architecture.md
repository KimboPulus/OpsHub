# Architecture and ownership

## Request path

React frontend calls Spring Boot API. Spring owns authentication, workflow
rules, persistence, exports, IoT simulation, and reporting projections. Local
profile uses H2; Compose production profile uses PostgreSQL behind Flyway.

## Business boundaries

- Issue workflow is source of truth for status, actor, acknowledgement,
  response deadline, resolution deadline, and escalation time.
- IoT module is local simulator stored through same Spring/JPA process. It is
  not connected to PLC, SCADA, OPC UA, or physical machine.
- ERP endpoint is simulated schedule/sync boundary. It does not write to SAP.
- Power/BI screen consumes reporting API and SQL views. Dataverse and Power
  Automate cards are mapping examples, not live Microsoft integrations.

## Security boundary

Browser uses server session. Operator and leader roles enforce workflow actions
in backend, not only UI. Local demo credentials are intentionally weak and must
not be used by production profile. Uploaded images receive content and size
checks; storage remains local filesystem.

## Evidence

Backend behavior tests, frontend build, browser role-workflow smoke, and Docker
image builds run in CI. Browser smoke creates issue as operator, verifies
forbidden resolve, resolves as leader, and captures audit trail screenshot.
