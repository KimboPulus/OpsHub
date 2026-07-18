# Current limitations

- No real ERP, PLC, SCADA, OPC UA, Dataverse, Power Automate, or Power BI tenant
  connection.
- IoT readings and alerts are simulated inside application process.
- Local uploads are unsuitable for multiple replicas.
- Session login has two demo roles; no SSO, MFA, directory sync, or fine-grained
  plant/line authorization.
- PDF/CSV exports are synchronous and small-data oriented.
- Server-sent events provide live refresh but no durable event bus.
- CI builds images; it does not deploy or execute recovery exercise.

These gaps keep project useful as workflow prototype while preventing claims of
production factory integration.
