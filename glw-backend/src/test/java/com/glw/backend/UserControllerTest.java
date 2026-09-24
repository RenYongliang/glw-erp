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
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean java.time.Clock clock;

    @Test
    void expiredChallengeIsRejected() throws Exception {
        String encrypted = encrypt("{\"username\":\"admin\",\"password\":\"Admin123!\"}");
        Instant future = clock.instant().plusSeconds(121);
        org.mockito.Mockito.doReturn(future).when(clock).instant();
        mvc.perform(
                        post("/api/users/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(encrypted))
                .andExpect(status().isBadRequest());
    }

    private String encrypt(String body) throws Exception {
        try {
            var node = json.readTree(body);
            if (!node.has("username") || !node.has("password")) return body;
        } catch (Exception e) {
            return body;
        }
        var result =
                mvc.perform(get("/api/users/login-key"))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        var key = json.readTree(result).path("data");
        var decoder = java.util.Base64.getDecoder();
        var encoder = java.util.Base64.getEncoder();
        var publicKey =
                java.security.KeyFactory.getInstance("RSA")
                        .generatePublic(
                                new java.security.spec.X509EncodedKeySpec(
                                        decoder.decode(key.path("publicKey").asText())));
        var aesKey = javax.crypto.KeyGenerator.getInstance("AES");
        aesKey.init(256);
        var secret = aesKey.generateKey();
        byte[] iv = new byte[12];
        new java.security.SecureRandom().nextBytes(iv);
        var aes = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(
                javax.crypto.Cipher.ENCRYPT_MODE,
                secret,
                new javax.crypto.spec.GCMParameterSpec(128, iv));
        aes.updateAAD(
                (key.path("keyId").asText() + "." + key.path("challenge").asText())
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var rsa = javax.crypto.Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsa.init(
                javax.crypto.Cipher.ENCRYPT_MODE,
                publicKey,
                new javax.crypto.spec.OAEPParameterSpec(
                        "SHA-256",
                        "MGF1",
                        java.security.spec.MGF1ParameterSpec.SHA256,
                        javax.crypto.spec.PSource.PSpecified.DEFAULT));
        return json.writeValueAsString(
                java.util.Map.of(
                        "keyId",
                        key.path("keyId").asText(),
                        "challenge",
                        key.path("challenge").asText(),
                        "encryptedKey",
                        encoder.encodeToString(rsa.doFinal(secret.getEncoded())),
                        "iv",
                        encoder.encodeToString(iv),
                        "ciphertext",
                        encoder.encodeToString(
                                aes.doFinal(
                                        body.getBytes(java.nio.charset.StandardCharsets.UTF_8)))));
    }

    @Test
    void rejectsPlaintextReplayAndTampering() throws Exception {
        String plain = "{\"username\":\"admin\",\"password\":\"Admin123!\"}";
        for (String path : java.util.List.of("/api/users/login", "/api/users/token")) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(plain))
                    .andExpect(status().isBadRequest());
        }
        String encrypted = encrypt(plain);
        mvc.perform(
                        post("/api/users/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(encrypted))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/api/users/token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(encrypted))
                .andExpect(status().isBadRequest());
        var altered = (tools.jackson.databind.node.ObjectNode) json.readTree(encrypt(plain));
        String cipher = altered.path("ciphertext").asText();
        altered.put("ciphertext", (cipher.startsWith("A") ? "B" : "A") + cipher.substring(1));
        mvc.perform(
                        post("/api/users/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(altered)))
                .andExpect(status().isBadRequest());
    }

    private String login(String username, String password, boolean remember) throws Exception {
        String body =
                json.writeValueAsString(
                        java.util.Map.of(
                                "username", username, "password", password, "remember", remember));
        String response =
                mvc.perform(
                                post("/api/users/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(encrypt(body)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(0))
                        .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist())
                        .andExpect(jsonPath("$.traceId").doesNotExist())
                        .andExpect(header().doesNotExist("X-Trace-Id"))
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
        assertThat(Duration.between(Instant.now(), jwt.parseAccess(first).expiresAt()).toMinutes())
                .isBetween(14L, 15L);
        assertThat(Duration.between(Instant.now(), jwt.parseAccess(second).expiresAt()).toMinutes())
                .isBetween(14L, 15L);
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
                                            encrypt(
                                                    json.writeValueAsString(
                                                            java.util.Map.of(
                                                                    "username",
                                                                    name,
                                                                    "password",
                                                                    "wrong")))))
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
                                                encrypt(
                                                        "{\"username\":\"admin\",\"password\":\"Admin123!\"}")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String token = json.readTree(response).path("data").path("accessToken").asText();
        assertThat(Duration.between(Instant.now(), jwt.parseAccess(token).expiresAt()).toMinutes())
                .isBetween(14L, 15L);
    }

    @Test
    void missingAndMalformedCredentialsAreBadRequests() throws Exception {
        for (String body :
                java.util.List.of("{}", "{broken", "{\"username\":\"admin\",\"password\":\"\"}")) {
            mvc.perform(
                            post("/api/users/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(encrypt(body)))
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
                                        encrypt(
                                                json.writeValueAsString(
                                                        java.util.Map.of(
                                                                "username",
                                                                "admin",
                                                                "password",
                                                                "密".repeat(30))))))
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
        String unregistered = jwt.issue(1, false).access().value();
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + unregistered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenForUnknownUserIsRejected() throws Exception {
        var token = jwt.issue(999, false);
        sessions.save(
                token.sessionId(),
                new SessionStore.Session(
                        999,
                        token.access().id(),
                        token.refresh().id(),
                        token.refresh().expiresAt()));
        mvc.perform(
                        get("/api/users/me")
                                .header("Authorization", "Bearer " + token.access().value()))
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

    private tools.jackson.databind.JsonNode pair(String path) throws Exception {
        String body =
                mvc.perform(
                                post(path)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                encrypt(
                                                        "{\"username\":\"admin\",\"password\":\"Admin123!\"}")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(body).path("data");
    }

    @Test
    void refreshRotatesBothTokensAndLogoutRevokesRefresh() throws Exception {
        var first = pair("/api/users/token");
        String refreshBody =
                json.writeValueAsString(
                        java.util.Map.of("refreshToken", first.path("refreshToken").asText()));
        String body =
                mvc.perform(
                                post("/api/users/refresh-token")
                                        .header(
                                                "Authorization",
                                                "Bearer expired-ignored-on-whitelist")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(refreshBody))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        var next = json.readTree(body).path("data");
        assertThat(next.path("refreshToken").asText())
                .isNotEqualTo(first.path("refreshToken").asText());
        assertThat(next.path("refreshExpiresAt").asText())
                .isEqualTo(first.path("refreshExpiresAt").asText());
        mvc.perform(
                        get("/api/users/me")
                                .header(
                                        "Authorization",
                                        "Bearer " + first.path("accessToken").asText()))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/users/refresh-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(refreshBody))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        get("/api/users/me")
                                .header(
                                        "Authorization",
                                        "Bearer " + next.path("refreshToken").asText()))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        get("/api/users/me")
                                .header(
                                        "Authorization",
                                        "Bearer " + next.path("accessToken").asText()))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/api/users/logout")
                                .header(
                                        "Authorization",
                                        "Bearer " + next.path("accessToken").asText()))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/api/users/refresh-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                java.util.Map.of(
                                                        "refreshToken",
                                                        next.path("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void whitelistIsMethodSpecificAndRefreshStillRequiresRefreshToken() throws Exception {
        mvc.perform(get("/api/users/login")).andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/users/refresh-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/users/refresh-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"invalid\"}"))
                .andExpect(status().isUnauthorized());
        String access = login("admin", "Admin123!", false);
        mvc.perform(
                        post("/api/users/refresh-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                java.util.Map.of("refreshToken", access))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/new-business-api")).andExpect(status().isUnauthorized());
    }
}
