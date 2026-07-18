# Database migration story

Flyway owns schema changes for local and PostgreSQL profiles.

- `V1__create_application_schema.sql` creates operational tables and reporting
  baseline.
- `V2__add_issue_lifecycle_metadata.sql` adds actor, acknowledgement, SLA, and
  escalation fields without requiring Hibernate to mutate schema.

Application starts with Hibernate validation after Flyway. New persistent field
must receive forward-only migration plus test. Do not edit applied migration in
deployed environment; add next numbered file.

Local H2 data created before Flyway baseline is incompatible. Delete ignored
local `data/` only when demo data can be discarded. PostgreSQL environments need
backup and tested migration path, never deletion workaround.
