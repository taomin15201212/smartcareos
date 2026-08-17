package com.smartcareos.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcareos.device.application.DeviceRiskEventHandler;
import com.smartcareos.device.infrastructure.MqttRiskEventAdapter;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MqttRiskEventAdapterTest {
    @Test void validatesTopicIdentityBeforeDelegating() {
        DeviceRiskEventHandler handler=mock(DeviceRiskEventHandler.class);
        MqttRiskEventAdapter adapter=new MqttRiskEventAdapter(new ObjectMapper().findAndRegisterModules(),handler);
        byte[] valid="{\"eventId\":\"e1\",\"tenantId\":\"t1\",\"deviceId\":\"d1\",\"elderId\":\"elder\",\"severity\":\"HIGH\",\"observedAt\":\"2026-08-16T00:00:00Z\"}".getBytes();
        adapter.accept("smartcare/t1/devices/d1/risk-events",valid);
        verify(handler).handle(any());
        assertThatThrownBy(()->adapter.accept("smartcare/other/devices/d1/risk-events",valid))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mismatch");
    }
}
