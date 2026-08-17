package com.smartcareos.alarm.api;

import com.smartcareos.alarm.application.AlarmNotFoundException;
import com.smartcareos.alarm.domain.AlarmDomainException;
import com.smartcareos.care.application.CareNotFoundException;
import com.smartcareos.care.domain.CareConflictException;
import com.smartcareos.device.application.DeviceConflictException;
import com.smartcareos.device.application.DeviceEventRejectedException;
import com.smartcareos.device.application.DeviceNotFoundException;
import com.smartcareos.device.domain.DeviceDomainException;
import com.smartcareos.elder.application.ElderConflictException;
import com.smartcareos.elder.application.ElderNotFoundException;
import com.smartcareos.elder.domain.AdmissionConflictException;
import com.smartcareos.institution.application.InstitutionConflictException;
import com.smartcareos.institution.application.InstitutionNotFoundException;
import com.smartcareos.integration.IntegrationConflictException;
import com.smartcareos.identity.TenantAccessDeniedException;
import com.smartcareos.messaging.application.InboxPayloadConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(TenantAccessDeniedException.class)
    ResponseEntity<Map<String, Object>> handleTenantDenied(TenantAccessDeniedException exception) {
        return error(HttpStatus.FORBIDDEN, "TENANT_ACCESS_DENIED", exception.getMessage());
    }

    @ExceptionHandler(AlarmNotFoundException.class)
    ResponseEntity<Map<String, Object>> handleNotFound(AlarmNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "ALARM_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(DeviceNotFoundException.class)
    ResponseEntity<Map<String, Object>> handleDeviceNotFound(DeviceNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "DEVICE_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler({ElderNotFoundException.class, InstitutionNotFoundException.class,
            CareNotFoundException.class})
    ResponseEntity<Map<String, Object>> handleBusinessResourceNotFound(
            RuntimeException exception
    ) {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(AlarmDomainException.class)
    ResponseEntity<Map<String, Object>> handleConflict(AlarmDomainException exception) {
        return error(HttpStatus.CONFLICT, "ALARM_STATE_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler({DeviceConflictException.class, DeviceDomainException.class})
    ResponseEntity<Map<String, Object>> handleDeviceConflict(RuntimeException exception) {
        return error(HttpStatus.CONFLICT, "DEVICE_STATE_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler({
            ElderConflictException.class,
            AdmissionConflictException.class,
            InstitutionConflictException.class,
            CareConflictException.class,
            IntegrationConflictException.class
    })
    ResponseEntity<Map<String, Object>> handleBusinessConflict(RuntimeException exception) {
        return error(HttpStatus.CONFLICT, "BUSINESS_STATE_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(DeviceEventRejectedException.class)
    ResponseEntity<Map<String, Object>> handleDeviceEventRejected(
            DeviceEventRejectedException exception
    ) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "DEVICE_EVENT_REJECTED", exception.getMessage());
    }

    @ExceptionHandler(InboxPayloadConflictException.class)
    ResponseEntity<Map<String, Object>> handleInboxPayloadConflict(
            InboxPayloadConflictException exception
    ) {
        return error(HttpStatus.CONFLICT, "EVENT_PAYLOAD_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class
    })
    ResponseEntity<Map<String, Object>> handleBadRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "request validation failed");
    }

    private static ResponseEntity<Map<String, Object>> error(
            HttpStatus status,
            String code,
            String message
    ) {
        return ResponseEntity.status(status).body(Map.of(
                "code", code,
                "message", message
        ));
    }
}
