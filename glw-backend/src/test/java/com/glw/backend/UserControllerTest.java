package com.glw.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.glw.backend.auth.JwtService;
import com.glw.backend.auth.SessionStore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mock")
class UserControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @Autowired SessionStore sessions;

    private String login(String username, String password, boolean remember) throws Exception {
        String body =
                json.writeValueAsString(
                        java.util.Map.of(
                                "username", username, "password", password, "remember", remember));
        String response =
                mvc.perform(
                                post("/api/users/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(0))
                        .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist())
                        .andExpect(jsonPath("$.traceId").isNotEmpty())
                        .andExpect(header().exists("X-Trace-Id"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(response).path("data").path("accessToken").asText();
    }

    @Test
    void loginReadProfileLogoutAndRejectOldToken() throws Exception {
        String token = login("admin", "Admin123!", false);
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"));
        mvc.perform(post("/api/users/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
        mvc.perform(post("/api/users/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void remembersForSevenDaysAndLogoutOnlyRevokesCurrentSession() throws Exception {
        String first = login("demo", "Demo123!", false);
        String second = login("demo", "Demo123!", true);
        assertThat(Duration.between(Instant.now(), jwt.parse(first).expiresAt()).toHours())
                .isBetween(7L, 8L);
        assertThat(Duration.between(Instant.now(), jwt.parse(second).expiresAt()).toHours())
                .isBetween(167L, 168L);
        mvc.perform(post("/api/users/logout").header("Authorization", "Bearer " + first))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + second))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndUnknownUsernameUseSameError() throws Exception {
        for (String name : java.util.List.of("admin", "unknown")) {
            mvc.perform(
                            post("/api/users/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            json.writeValueAsString(
                                                    java.util.Map.of(
                                                            "username",
                                                            name,
                                                            "password",
                                                            "wrong"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(40101));
        }
    }

    @Test
    void omittedRememberDefaultsToNormalLogin() throws Exception {
        String response =
                mvc.perform(
                                post("/api/users/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"username\":\"admin\",\"password\":\"Admin123!\"}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String token = json.readTree(response).path("data").path("accessToken").asText();
        assertThat(Duration.between(Instant.now(), jwt.parse(token).expiresAt()).toHours())
                .isBetween(7L, 8L);
    }

    @Test
    void missingAndMalformedCredentialsAreBadRequests() throws Exception {
        for (String body :
                java.util.List.of("{}", "{broken", "{\"username\":\"admin\",\"password\":\"\"}")) {
            mvc.perform(
                            post("/api/users/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40000));
        }
    }

    @Test
    void rejectsPasswordAboveBcryptByteLimit() throws Exception {
        mvc.perform(
                        post("/api/users/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                java.util.Map.of(
                                                        "username",
                                                        "admin",
                                                        "password",
                                                        "密".repeat(30)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void requiresBearerTokenAndActiveSession() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer broken"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
        mvc.perform(get("/api/users/me").header("Authorization", "Basic abc"))
                .andExpect(status().isUnauthorized());
        String unregistered = jwt.issue(1, false).value();
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + unregistered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenForUnknownUserIsRejected() throws Exception {
        var token = jwt.issue(999, false);
        sessions.save(token.id(), 999, token.expiresAt());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token.value()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticated404And405UseUnifiedEnvelope() throws Exception {
        String token = login("admin", "Admin123!", false);
        mvc.perform(get("/api/no-such-resource").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
        mvc.perform(get("/api/users/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(40500));
    }

    @Test
    void allowsConfiguredFrontendPreflight() throws Exception {
        mvc.perform(
                        options("/api/users/me")
                                .header("Origin", "http://localhost:5173")
                                .header("Access-Control-Request-Method", "GET")
                                .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(
                        options("/api/users/me")
                                .header("Origin", "https://untrusted.example")
                                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
