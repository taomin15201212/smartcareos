package com.smartcareos.system;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:smartcareos_openapi_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "smartcareos.security.enabled=true"
})
@AutoConfigureMockMvc
class OpenApiDocumentationIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void publishesKnife4jAndCompleteOpenApiContractWithoutBusinessAuthentication() throws Exception {
        mvc.perform(get("/doc.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));

        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("SmartCareOS 企业级智慧养老平台 API"))
                .andExpect(jsonPath("$.paths['/api/v1/api-credentials']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/institutions']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/elders']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/device-risk-events']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/alarms/{alarmId}/acknowledge']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/care-tasks/{taskId}/complete']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/notification-deliveries/{id}/dispatch']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/government-exchanges/{id}/receipt']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/dashboard/summary']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.apiKeyHeader").exists())
                .andExpect(jsonPath("$.components.securitySchemes.oidcBearer").exists());

        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urls[*].name", hasItem("00-全部接口")))
                .andExpect(jsonPath("$.urls[*].name", hasItem("04-设备与IoT")))
                .andExpect(jsonPath("$.urls[*].name", hasItem("08-政务交换")));
    }
}
