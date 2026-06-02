package pl.fortaco.opshub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    void apiRequiresLogin() throws Exception {
        mvc.perform(get("/api/state"))
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
            .andExpect(jsonPath("$.roles", hasItem("ROLE_LEADER")));
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

    private MockHttpSession loginAs(String username) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login")
                .param("username", username)
                .param("password", "opshub"))
            .andExpect(status().isNoContent())
            .andReturn()
            .getRequest()
            .getSession(false);
    }
}
