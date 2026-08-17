package com.smartcareos.identity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
        "spring.datasource.url=jdbc:h2:mem:smartcareos_oidc_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "smartcareos.security.enabled=true",
        "smartcareos.security.auth-mode=oidc",
        "smartcareos.security.oidc.issuer-uri=https://identity.example.test"})
@AutoConfigureMockMvc
class OidcSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;

    @Test void mapsValidatedOidcClaimsIntoTenantAndRoleContext() throws Exception {
        Jwt jwt=Jwt.withTokenValue("valid").header("alg","RS256")
                .subject("auditor-1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .claim("tenant_id","oidc-tenant").claim("roles", List.of("AUDITOR")).build();
        when(decoder.decode("valid")).thenReturn(jwt);
        mvc.perform(get("/api/v1/dashboard/summary").header("Authorization","Bearer valid"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tenantId").value("oidc-tenant"));
        mvc.perform(get("/api/v1/dashboard/summary").header("Authorization","Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }
}
