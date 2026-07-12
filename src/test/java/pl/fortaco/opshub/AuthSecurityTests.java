package pl.fortaco.opshub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:security-tests;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "opshub.security.password=opshub"
})
@AutoConfigureMockMvc
class AuthSecurityTests {
    @Autowired
    MockMvc mvc;

    private final ObjectMapper objectMapper = new ObjectMapper();


    @Test
    void apiRequiresLogin() throws Exception {
        mvc.perform(get("/api/state"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void liveOperationsStreamRequiresLogin() throws Exception {
        mvc.perform(get("/api/events/operations"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        mvc.perform(post("/api/auth/login")
                .param("username", "lider")
                .param("password", "wrong"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void loginCreatesSessionForKnownUser() throws Exception {
        mvc.perform(post("/api/auth/login")
                .param("username", "lider")
                .param("password", "opshub"))
            .andExpect(status().isNoContent())
            .andExpect(request().sessionAttribute("SPRING_SECURITY_CONTEXT", notNullValue()));
    }

    @Test
    void currentUserEndpointReturnsUserProfile() throws Exception {
        MockHttpSession session = loginAs("lider");

        mvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("lider"))
            .andExpect(jsonPath("$.displayName").value("Lider zmiany"))
            .andExpect(jsonPath("$.roles", hasItem("ROLE_LEADER")))
            .andExpect(jsonPath("$.capabilities.canResolveIssue").value(true))
            .andExpect(jsonPath("$.capabilities.canDeleteIssue").value(true));
    }

    @Test
    void operatorProfileExposesRestrictedCapabilities() throws Exception {
        MockHttpSession session = loginAs("operator");

        mvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roles", hasItem("ROLE_OPERATOR")))
            .andExpect(jsonPath("$.capabilities.canCreateIssue").value(true))
            .andExpect(jsonPath("$.capabilities.canStartWork").value(true))
            .andExpect(jsonPath("$.capabilities.canResolveIssue").value(false))
            .andExpect(jsonPath("$.capabilities.canDeleteIssue").value(false));
    }

    @Test
    void logoutAcceptsAuthenticatedSession() throws Exception {
        MockHttpSession session = loginAs("operator");

        mvc.perform(post("/api/auth/logout").session(session))
            .andExpect(status().isNoContent());
    }

    @Test
    void apiWorksAfterLoginSessionIsReused() throws Exception {
        MockHttpSession session = loginAs("lider");

        mvc.perform(get("/api/state").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.machines").isArray());
    }

    @Test
    void operatorCannotCreateResolvedIssue() throws Exception {
        MockHttpSession session = loginAs("operator");

        mvc.perform(post("/api/issues")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(issuePayload("RESOLVED")))
            .andExpect(status().isForbidden());
    }

    @Test
    void operatorCanStartWorkButCannotResolveIssue() throws Exception {
        MockHttpSession session = loginAs("operator");
        int issueId = createIssue(session, "NEW");

        mvc.perform(patch("/api/issues/{id}/status", issueId)
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.acknowledgedAt").exists());

        mvc.perform(patch("/api/issues/{id}/status", issueId)
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void leaderCanResolveAndDeleteClosedIssue() throws Exception {
        MockHttpSession session = loginAs("lider");
        int issueId = createIssue(session, "NEW");

        mvc.perform(patch("/api/issues/{id}/status", issueId)
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RESOLVED"))
            .andExpect(jsonPath("$.resolvedAt").exists());

        mvc.perform(delete("/api/issues/{id}", issueId).session(session))
            .andExpect(status().isOk());
    }

    @Test
    void downtimeSyncRequiresLeaderRole() throws Exception {
        MockHttpSession operatorSession = loginAs("operator");
        MockHttpSession leaderSession = loginAs("lider");

        mvc.perform(post("/api/issues/1/downtime-sync").session(operatorSession))
            .andExpect(status().isForbidden());

        mvc.perform(post("/api/issues/1/downtime-sync").session(leaderSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.target").exists());
    }

    private MockHttpSession loginAs(String username) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login")
                .param("username", username)
                .param("password", "opshub"))
            .andExpect(status().isNoContent())
            .andReturn()
            .getRequest()
            .getSession(false);
    }

    private int createIssue(MockHttpSession session, String issueStatus) throws Exception {
        MvcResult result = mvc.perform(post("/api/issues")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(issuePayload(issueStatus)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.createdBy").exists())
            .andExpect(jsonPath("$.responseDueAt").exists())
            .andExpect(jsonPath("$.resolutionDueAt").exists())
            .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asInt();
    }

    private static String issuePayload(String status) {
        return """
            {
              "title":"Workflow security test",
              "description":"Issue created from an authenticated workflow test",
              "category":"MACHINE_FAILURE",
              "severity":"HIGH",
              "status":"%s",
              "downtimeMinutes":12,
              "assignedTeam":"Mechanicy",
              "notificationChannel":"Panel produkcji",
              "machineId":1,
              "workOrderId":1
            }
            """.formatted(status);
    }
}
