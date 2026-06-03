package pl.fortaco.opshub.web;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class ReportingController {
    private final JdbcTemplate jdbc;

    public ReportingController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/reporting/power-platform")
    public Map<String, Object> powerPlatformOverview() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("summary", Map.of(
            "openIssues", intValue("SELECT COALESCE(SUM(open_flag), 0) FROM rpt_issue_aging"),
            "criticalOpenIssues", intValue("SELECT COUNT(*) FROM rpt_issue_aging WHERE severity = 'CRITICAL' AND open_flag = 1"),
            "downtimeMinutes", intValue("SELECT COALESCE(SUM(downtime_minutes), 0) FROM rpt_issue_aging"),
            "averageOee", doubleValue("SELECT COALESCE(ROUND(AVG(oee_proxy_percent), 1), 0) FROM rpt_oee_daily"),
            "energyPerUnit", doubleValue("SELECT COALESCE(ROUND(AVG(energy_kwh_per_unit), 3), 0) FROM rpt_energy_per_unit")
        ));
        body.put("views", reportingViews());
        body.put("issueAging", issueAging());
        body.put("downtimeByMachine", downtimeByMachine());
        body.put("oeeDaily", oeeDaily());
        body.put("energyPerUnit", energyPerUnit());
        body.put("canvasScreens", canvasScreens());
        body.put("cloudFlows", cloudFlows());
        body.put("dataverseTables", dataverseTables());
        return body;
    }

    private List<Map<String, Object>> reportingViews() {
        return List.of(
            view("rpt_issue_aging", "backlog, status, age and downtime by issue"),
            view("rpt_downtime_by_machine", "machine downtime Pareto for maintenance"),
            view("rpt_oee_daily", "daily OEE proxy by production line"),
            view("rpt_energy_per_unit", "energy per produced unit by shift and machine")
        );
    }

    private Map<String, Object> view(String name, String purpose) {
        return Map.of(
            "name", name,
            "purpose", purpose,
            "rows", intValue("SELECT COUNT(*) FROM " + name)
        );
    }

    private List<Map<String, Object>> issueAging() {
        return jdbc.query("""
            SELECT issue_id, title, severity, status, assigned_team, machine_code, production_line, age_hours, downtime_minutes
            FROM rpt_issue_aging
            WHERE open_flag = 1
            ORDER BY
                CASE severity WHEN 'CRITICAL' THEN 4 WHEN 'HIGH' THEN 3 WHEN 'MEDIUM' THEN 2 ELSE 1 END DESC,
                age_hours DESC
            LIMIT 6
            """, (rs, rowNum) -> map(
            "issueId", rs.getInt("issue_id"),
            "title", rs.getString("title"),
            "severity", rs.getString("severity"),
            "status", rs.getString("status"),
            "assignedTeam", rs.getString("assigned_team"),
            "machineCode", rs.getString("machine_code"),
            "productionLine", rs.getString("production_line"),
            "ageHours", rs.getInt("age_hours"),
            "downtimeMinutes", rs.getInt("downtime_minutes")
        ));
    }

    private List<Map<String, Object>> downtimeByMachine() {
        return jdbc.query("""
            SELECT machine_code, machine_name, production_line, issue_count, downtime_minutes, open_issue_count
            FROM rpt_downtime_by_machine
            ORDER BY downtime_minutes DESC
            LIMIT 6
            """, (rs, rowNum) -> map(
            "machineCode", rs.getString("machine_code"),
            "machineName", rs.getString("machine_name"),
            "productionLine", rs.getString("production_line"),
            "issueCount", rs.getInt("issue_count"),
            "downtimeMinutes", rs.getInt("downtime_minutes"),
            "openIssueCount", rs.getInt("open_issue_count")
        ));
    }

    private List<Map<String, Object>> oeeDaily() {
        return jdbc.query("""
            SELECT report_date, production_line, issue_count, downtime_minutes, availability_proxy_percent, oee_proxy_percent
            FROM rpt_oee_daily
            ORDER BY report_date DESC, production_line
            LIMIT 8
            """, (rs, rowNum) -> map(
            "reportDate", rs.getString("report_date"),
            "productionLine", rs.getString("production_line"),
            "issueCount", rs.getInt("issue_count"),
            "downtimeMinutes", rs.getInt("downtime_minutes"),
            "availabilityProxy", rs.getDouble("availability_proxy_percent"),
            "oeeProxy", rs.getDouble("oee_proxy_percent")
        ));
    }

    private List<Map<String, Object>> energyPerUnit() {
        return jdbc.query("""
            SELECT report_date, shift_name, machine_code, production_line, total_energy_kwh, produced_units, energy_kwh_per_unit
            FROM rpt_energy_per_unit
            ORDER BY report_date DESC, production_line, machine_code
            LIMIT 8
            """, (rs, rowNum) -> map(
            "reportDate", rs.getString("report_date"),
            "shiftName", rs.getString("shift_name"),
            "machineCode", rs.getString("machine_code"),
            "productionLine", rs.getString("production_line"),
            "totalEnergyKwh", rs.getDouble("total_energy_kwh"),
            "producedUnits", rs.getInt("produced_units"),
            "energyKwhPerUnit", rs.getDouble("energy_kwh_per_unit")
        ));
    }

    private List<Map<String, String>> canvasScreens() {
        return List.of(
            Map.of("name", "Formularz operatora", "scope", "Canvas App", "detail", "szybkie zgłoszenie z maszyny, zdjęcie i kanał alertu"),
            Map.of("name", "Dashboard lidera", "scope", "Canvas App", "detail", "otwarte zgłoszenia, eskalacje i ryzyka zleceń SAP"),
            Map.of("name", "Triage alertów IoT", "scope", "Canvas App", "detail", "alert z telemetryki można zamienić w zgłoszenie produkcyjne"),
            Map.of("name", "KPI managera", "scope", "Power BI", "detail", "OEE proxy, przestoje, aging zgłoszeń i energia na sztukę")
        );
    }

    private List<Map<String, String>> cloudFlows() {
        return List.of(
            Map.of("name", "Krytyczne zgłoszenie", "trigger", "nowy rekord ProductionIssue", "action", "Teams/email + wpis audytu"),
            Map.of("name", "Alert IoT -> zgłoszenie", "trigger", "otwarty alert telemetryczny", "action", "utworzenie zgłoszenia i przypisanie zespołu"),
            Map.of("name", "Podsumowanie zmiany", "trigger", "koniec zmiany", "action", "SharePoint/Excel/PDF z KPI"),
            Map.of("name", "Odświeżenie BI", "trigger", "harmonogram", "action", "refresh datasetu i digest dla managera")
        );
    }

    private List<Map<String, String>> dataverseTables() {
        return List.of(
            Map.of("name", "ProductionIssue", "purpose", "zgłoszenia, status, priorytet, przestój"),
            Map.of("name", "Machine", "purpose", "maszyny, krytyczność, linia produkcyjna"),
            Map.of("name", "WorkOrder", "purpose", "zlecenia SAP, materiał, ilość, termin"),
            Map.of("name", "IssueActivity", "purpose", "komentarze, statusy i audit trail"),
            Map.of("name", "IoTAlert", "purpose", "alerty z telemetryki i link do zgłoszenia"),
            Map.of("name", "FlowRunLog", "purpose", "wyniki automatyzacji Power Automate")
        );
    }

    private int intValue(String sql) {
        Number value = jdbc.queryForObject(sql, Number.class);
        return value == null ? 0 : value.intValue();
    }

    private double doubleValue(String sql) {
        Number value = jdbc.queryForObject(sql, Number.class);
        return value == null ? 0 : value.doubleValue();
    }

    private Map<String, Object> map(Object... values) throws SQLException {
        if (values.length % 2 != 0) {
            throw new SQLException("Odd number of map arguments");
        }

        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            row.put(values[i].toString(), values[i + 1]);
        }
        return row;
    }
}
