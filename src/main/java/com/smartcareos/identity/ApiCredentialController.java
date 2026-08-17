package com.smartcareos.identity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/api-credentials")
@Tag(name = "01 身份与授权", description = "租户 API 凭据签发与撤销；仅 ADMIN 可操作")
public class ApiCredentialController {
    private final ApiCredentialService service;
    public ApiCredentialController(ApiCredentialService service) { this.service = service; }
    @PostMapping
    @Operation(summary = "签发 API 凭据", description = "API Key 仅在本次响应中返回，请安全保存")
    ResponseEntity<ApiCredentialService.IssuedCredential> issue(
            @Valid @RequestBody IssueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.issue(request.tenantId(),
                request.principalId(), request.expiresAt(), request.role()));
    }
    @DeleteMapping("/{credentialId}")
    @Operation(summary = "撤销 API 凭据")
    ResponseEntity<Void> revoke(@PathVariable String credentialId) {
        service.revoke(credentialId); return ResponseEntity.noContent().build();
    }
    record IssueRequest(@NotBlank @Size(max=64) String tenantId,
            @NotBlank @Size(max=64) String principalId, Instant expiresAt,
            @NotNull TenantContext.Role role) {}
}
