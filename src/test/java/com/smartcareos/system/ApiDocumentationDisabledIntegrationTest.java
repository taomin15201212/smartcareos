package com.smartcareos.system;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:smartcareos_openapi_disabled_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "smartcareos.api-docs.enabled=false"
})
@AutoConfigureMockMvc
class ApiDocumentationDisabledIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void hidesOpenApiAndKnife4jWhenDocumentationIsDisabled() throws Exception {
        mvc.perform(get("/doc.html")).andExpect(status().isNotFound());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
    }
}
