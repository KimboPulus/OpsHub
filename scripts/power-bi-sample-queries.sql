-- Open issue backlog
SELECT
    issue_id,
    title,
    severity,
    status,
    assigned_team,
    machine_code,
    production_line,
    sap_order_number,
    age_hours,
    downtime_minutes
FROM rpt_issue_aging
WHERE open_flag = 1
ORDER BY severity, age_hours DESC;

-- Downtime Pareto by machine
SELECT
    machine_code,
    machine_name,
    production_line,
    issue_count,
    downtime_minutes,
    open_issue_count
FROM rpt_downtime_by_machine
ORDER BY downtime_minutes DESC;

-- Daily OEE proxy
SELECT
    report_date,
    production_line,
    issue_count,
    downtime_minutes,
    availability_proxy_percent,
    oee_proxy_percent
FROM rpt_oee_daily
ORDER BY report_date DESC, production_line;

-- Energy per produced unit
SELECT
    report_date,
    shift_name,
    machine_code,
    production_line,
    total_energy_kwh,
    produced_units,
    energy_kwh_per_unit
FROM rpt_energy_per_unit
ORDER BY report_date DESC, production_line, machine_code;

