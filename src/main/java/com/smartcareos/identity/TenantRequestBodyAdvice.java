package com.smartcareos.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import java.lang.reflect.Type;
import java.util.Map;

@ControllerAdvice
public class TenantRequestBodyAdvice extends RequestBodyAdviceAdapter {
    private final ObjectMapper mapper;
    public TenantRequestBodyAdvice(ObjectMapper mapper) { this.mapper = mapper; }
    @Override public boolean supports(MethodParameter p, Type t,
            Class<? extends HttpMessageConverter<?>> c) { return TenantContext.current() != null; }
    @Override public Object afterBodyRead(Object body, HttpInputMessage input, MethodParameter parameter,
            Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        Object tenant = mapper.convertValue(body, Map.class).get("tenantId");
        if (tenant != null && !TenantContext.current().tenantId().equals(tenant.toString())) {
            throw new TenantAccessDeniedException();
        }
        return body;
    }
}
