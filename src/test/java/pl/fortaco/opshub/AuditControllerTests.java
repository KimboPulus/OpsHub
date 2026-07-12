package pl.fortaco.opshub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:audit-tests;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc(addFilters = false)
class AuditControllerTests {
    @Autowired
    MockMvc mvc;

    @Test
    void auditEndpointReturnsSeededActivity() throws Exception {
        mvc.perform(get("/api/audit/events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(greaterThan(0))))
            .andExpect(jsonPath("$.items[0].issueId").exists())
            .andExpect(jsonPath("$.items[0].actor").exists());
    }

    @Test
    void auditEndpointFiltersByTypeSeverityAndText() throws Exception {
        mvc.perform(get("/api/audit/events")
                .param("type", "SYSTEM")
                .param("severity", "CRITICAL")
                .param("text", "alert"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(greaterThan(0))))
            .andExpect(jsonPath("$.items[0].type").value("SYSTEM"))
            .andExpect(jsonPath("$.items[0].issueSeverity").value("CRITICAL"));
    }

    @Test
    void auditEndpointRejectsInvalidFilter() throws Exception {
        mvc.perform(get("/api/audit/events").param("type", "not-a-type"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void auditCsvExportReturnsDownloadableFile() throws Exception {
        mvc.perform(get("/exports/audit-events.csv"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("audit-events.csv")))
            .andExpect(result -> org.junit.jupiter.api.Assertions.assertTrue(result.getResponse().getContentAsString()
                .contains("issueTitle")));
    }
}
