package com.smartcareos.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties={
        "spring.datasource.url=jdbc:h2:mem:smartcareos_security_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "smartcareos.security.enabled=true",
        "smartcareos.security.bootstrap-key=test-secret",
        "smartcareos.security.bootstrap-tenant=tenant-secure",
        "smartcareos.security.bootstrap-principal=tester"})
@AutoConfigureMockMvc
class ApiKeySecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Test void healthIsPublicButBusinessApisRequireValidTenantCredential() throws Exception {
        mvc.perform(get("/api/v1/system/health")).andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(header().string("X-Content-Type-Options","nosniff"));
        mvc.perform(get("/api/v1/care-tasks/missing")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/care-tasks/missing")
                        .header("X-SmartCare-Tenant","tenant-secure")
                        .header("X-SmartCare-Principal","tester")
                        .header("X-SmartCare-Api-Key","wrong"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/care-tasks/missing")
                        .header("X-SmartCare-Tenant","tenant-secure")
                        .header("X-SmartCare-Principal","tester")
                        .header("X-SmartCare-Api-Key","test-secret"))
                .andExpect(status().isNotFound());
    }

    @Test void enforcesTenantBoundaryRotatesCredentialsAndWritesAudit() throws Exception {
        String suffix=UUID.randomUUID().toString();
        String elderJson=mvc.perform(post("/api/v1/elders")
                        .headers(managerHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"tenant-secure\",\"elderNo\":\"e-"+suffix+"\",\"name\":\"Secure Elder\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String elderId=mapper.readTree(elderJson).get("id").asText();

        mvc.perform(post("/api/v1/elders").headers(managerHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"tenant-other\",\"elderNo\":\"blocked-"+suffix+"\",\"name\":\"Blocked\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_ACCESS_DENIED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM elder WHERE elder_no=?",Long.class,"blocked-"+suffix)).isZero();

        String otherKey="other-secret-"+suffix;
        jdbc.update("INSERT INTO api_credential(id,tenant_id,principal_id,key_hash,status,created_at,can_manage_credentials) VALUES(?,?,?,?,?,?,TRUE)",
                UUID.randomUUID().toString(),"tenant-other","other-manager",
                ApiKeyAuthenticationInterceptor.sha256(otherKey),"ACTIVE",Timestamp.from(Instant.now()));
        mvc.perform(get("/api/v1/elders/{id}",elderId)
                        .header("X-SmartCare-Tenant","tenant-other")
                        .header("X-SmartCare-Principal","other-manager")
                        .header("X-SmartCare-Api-Key",otherKey))
                .andExpect(status().isForbidden());

        String issuedJson=mvc.perform(post("/api/v1/api-credentials").headers(managerHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"tenant-secure\",\"principalId\":\"rotated-client\",\"role\":\"AUDITOR\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.apiKey").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String credentialId=mapper.readTree(issuedJson).get("id").asText();
        String apiKey=mapper.readTree(issuedJson).get("apiKey").asText();
        mvc.perform(get("/api/v1/care-tasks/missing")
                        .header("X-SmartCare-Tenant","tenant-secure")
                        .header("X-SmartCare-Principal","rotated-client")
                        .header("X-SmartCare-Api-Key",apiKey)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/elders")
                        .header("X-SmartCare-Tenant","tenant-secure")
                        .header("X-SmartCare-Principal","rotated-client")
                        .header("X-SmartCare-Api-Key",apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"tenant-secure\",\"elderNo\":\"denied-"+suffix+"\",\"name\":\"Denied\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/v1/dashboard/summary")
                        .header("X-SmartCare-Tenant","tenant-secure")
                        .header("X-SmartCare-Principal","rotated-client")
                        .header("X-SmartCare-Api-Key",apiKey))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tenantId").value("tenant-secure"));
        mvc.perform(delete("/api/v1/api-credentials/{id}",credentialId).headers(managerHeaders()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/care-tasks/missing")
                        .header("X-SmartCare-Tenant","tenant-secure")
                        .header("X-SmartCare-Principal","rotated-client")
                        .header("X-SmartCare-Api-Key",apiKey)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE tenant_id='tenant-secure'",Long.class)).isGreaterThanOrEqualTo(4);
    }

    private org.springframework.http.HttpHeaders managerHeaders() {
        org.springframework.http.HttpHeaders headers=new org.springframework.http.HttpHeaders();
        headers.add("X-SmartCare-Tenant","tenant-secure");
        headers.add("X-SmartCare-Principal","tester");
        headers.add("X-SmartCare-Api-Key","test-secret");
        return headers;
    }
}
