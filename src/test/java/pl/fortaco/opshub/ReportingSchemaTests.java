package pl.fortaco.opshub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:reporting-tests;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc(addFilters = false)
class ReportingSchemaTests {
    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    @Test
    void issueAgingViewIsAvailableForPowerBi() {
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM rpt_issue_aging", Integer.class);
        Integer openRows = jdbc.queryForObject("SELECT SUM(open_flag) FROM rpt_issue_aging", Integer.class);

        assertNotNull(rows);
        assertNotNull(openRows);
        assertTrue(rows >= 3);
        assertTrue(openRows >= 1);
    }

    @Test
    void downtimeViewGroupsIssuesByMachine() {
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM rpt_downtime_by_machine", Integer.class);
        Integer laserDowntime = jdbc.queryForObject(
            "SELECT downtime_minutes FROM rpt_downtime_by_machine WHERE machine_code = 'LASER-01'",
            Integer.class);

        assertNotNull(rows);
        assertTrue(rows >= 3);
        assertEquals(37, laserDowntime);
    }

    @Test
    void oeeViewReturnsDailyProductionLineRows() {
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM rpt_oee_daily", Integer.class);
        Double oee = jdbc.queryForObject(
            "SELECT oee_proxy_percent FROM rpt_oee_daily WHERE production_line = 'WELD-A'",
            Double.class);

        assertNotNull(rows);
        assertTrue(rows >= 1);
        assertNotNull(oee);
        assertTrue(oee > 0);
    }

    @Test
    void energyPerUnitViewUsesStagedIotReadings() {
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM rpt_energy_per_unit", Integer.class);
        Double energyPerUnit = jdbc.queryForObject(
            "SELECT energy_kwh_per_unit FROM rpt_energy_per_unit WHERE machine_code = 'LASER-01' AND shift_name = 'A'",
            Double.class);

        assertNotNull(rows);
        assertTrue(rows >= 5);
        assertNotNull(energyPerUnit);
        assertTrue(energyPerUnit > 0);
    }

    @Test
    void powerPlatformEndpointReturnsReportingPack() throws Exception {
        mvc.perform(get("/api/reporting/power-platform"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.openIssues", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.views[0].name").value("rpt_issue_aging"))
            .andExpect(jsonPath("$.canvasScreens[0].name").value("Formularz operatora"))
            .andExpect(jsonPath("$.cloudFlows[0].name").value("Krytyczne zgłoszenie"))
            .andExpect(jsonPath("$.dataverseTables[0].name").value("ProductionIssue"));
    }
}
