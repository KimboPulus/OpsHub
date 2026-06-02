package pl.fortaco.opshub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:controller-tests;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc(addFilters = false)
class OpsHubControllerTests {
    @Autowired
    MockMvc mvc;

    @Test
    void stateEndpointReturnsSeededData() throws Exception {
        mvc.perform(get("/api/state"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issues", hasSize(greaterThanOrEqualTo(3))))
            .andExpect(jsonPath("$.machines", hasSize(3)))
            .andExpect(jsonPath("$.workOrders", hasSize(2)));
    }

    @Test
    void issueDetailsEndpointReturnsHistory() throws Exception {
        mvc.perform(get("/api/issues/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activities", hasSize(greaterThan(0))))
            .andExpect(jsonPath("$.title", containsString("Laser")));
    }

    @Test
    void createIssueEndpointSavesIssue() throws Exception {
        mvc.perform(post("/api/issues")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title":"Test API",
                      "description":"Opis zgłoszenia z testu",
                      "category":"MACHINE_FAILURE",
                      "severity":"MEDIUM",
                      "status":"NEW",
                      "downtimeMinutes":12,
                      "assignedTeam":"Mechanicy",
                      "notificationChannel":"Panel produkcji",
                      "machineId":1,
                      "workOrderId":1
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Test API"))
            .andExpect(jsonPath("$.activities", hasSize(1)));
    }

    @Test
    void commentEndpointAddsActivity() throws Exception {
        mvc.perform(post("/api/issues/1/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Sprawdzono czujnik.\",\"createdBy\":\"UR\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activities[*].message").value(org.hamcrest.Matchers.hasItem("Sprawdzono czujnik.")));
    }

    @Test
    void statusEndpointChangesIssueStatus() throws Exception {
        mvc.perform(patch("/api/issues/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RESOLVED"))
            .andExpect(jsonPath("$.resolvedAt").exists());
    }

    @Test
    void openIssueCannotBeDeleted() throws Exception {
        mvc.perform(delete("/api/issues/2"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void attachmentEndpointStoresImageMetadata() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "operator.png",
            "image/png",
            new byte[]{1, 2, 3});

        mvc.perform(multipart("/api/issues/1/attachments").file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.attachments", hasSize(1)))
            .andExpect(jsonPath("$.attachments[0].relativePath", containsString("/uploads/issues/")));
    }

    @Test
    void csvExportReturnsDownloadableFile() throws Exception {
        mvc.perform(get("/exports/production-issues.csv"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("production-issues.csv")))
            .andExpect(result -> assertTrue(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8)
                .contains("Tytuł")));
    }

    @Test
    void pdfExportReturnsPdfBytes() throws Exception {
        mvc.perform(get("/exports/weekly-summary.pdf"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString(".pdf")))
            .andExpect(result -> assertTrue(result.getResponse()
                .getContentAsString(StandardCharsets.ISO_8859_1)
                .startsWith("%PDF")));
    }
}
