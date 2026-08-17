package com.smartcareos.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:smartcareos_platform_test;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class PlatformIntegrationTest {
    @Autowired NotificationService notifications; @Autowired GovernmentExchangeService government;
    @Autowired MockMvc mvc;
    @Autowired MeterRegistry metrics;
    @Test void healthReportsLatestSchema() throws Exception {
        mvc.perform(get("/api/v1/system/health").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.schemaVersion").value("10"));
        assertThat(metrics.find("jvm.info").meter()).isNotNull();
    }
    @Test void notificationHasTerminalDeliveryLifecycle() {
        var created=notifications.create("t","ALARM","a","WECHAT","family","alarm summary");
        var sent=notifications.finish((String)created.get("ID"),true,null);
        assertThat(sent.get("STATUS")).isEqualTo("SENT");
        assertThatThrownBy(()->notifications.finish((String)created.get("ID"),false,"late"))
                .isInstanceOf(IntegrationConflictException.class);
    }
    @Test void governmentTaskRecordsReceipt() {
        var created=government.create("t","contract","v1",LocalDate.now(),LocalDate.now(),"{}");
        String id=(String)created.get("ID"); government.submit(id);
        assertThat(government.receipt(id,true,"receipt-1","accepted").get("STATUS")).isEqualTo("ACCEPTED");
    }
    @Test void sandboxGatewaysCompleteOutboundWork() {
        var notification=notifications.create("t","ALARM","a2","SMS","family","summary");
        assertThat(notifications.dispatch((String)notification.get("ID")).get("STATUS")).isEqualTo("SENT");
        var exchange=government.create("t","contract","v1",LocalDate.now(),LocalDate.now(),"{}");
        assertThat(government.dispatch((String)exchange.get("ID")).get("STATUS")).isEqualTo("ACCEPTED");
    }
}
