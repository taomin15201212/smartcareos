package com.smartcareos.institution.api;

import com.smartcareos.institution.application.InstitutionService;
import com.smartcareos.institution.application.InstitutionStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "02 机构与空间", description = "机构、房间与床位空间资源管理")
public class InstitutionController {

    private final InstitutionService service;

    public InstitutionController(InstitutionService service) {
        this.service = service;
    }

    @PostMapping("/institutions")
    @Operation(summary = "创建养老机构")
    ResponseEntity<InstitutionStore.InstitutionSnapshot> createInstitution(
            @Valid @RequestBody CreateInstitutionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createInstitution(
                request.tenantId(), request.institutionCode(), request.name()));
    }

    @PostMapping("/institutions/{institutionId}/rooms")
    @Operation(summary = "创建机构房间")
    ResponseEntity<InstitutionStore.RoomSnapshot> createRoom(
            @PathVariable String institutionId,
            @Valid @RequestBody CreateRoomRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createRoom(
                institutionId, request.roomCode(), request.name()));
    }

    @PostMapping("/rooms/{roomId}/beds")
    @Operation(summary = "创建房间床位")
    ResponseEntity<InstitutionStore.BedSnapshot> createBed(
            @PathVariable String roomId, @Valid @RequestBody CreateBedRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createBed(roomId, request.bedCode()));
    }

    @GetMapping("/beds/{bedId}")
    @Operation(summary = "查询床位详情")
    InstitutionStore.BedSnapshot getBed(@PathVariable String bedId) {
        return service.getBed(bedId);
    }

    record CreateInstitutionRequest(
            @NotBlank @Size(max = 64) String tenantId,
            @NotBlank @Size(max = 64) String institutionCode,
            @NotBlank @Size(max = 128) String name
    ) {
    }

    record CreateRoomRequest(
            @NotBlank @Size(max = 64) String roomCode,
            @NotBlank @Size(max = 128) String name
    ) {
    }

    record CreateBedRequest(@NotBlank @Size(max = 64) String bedCode) {
    }
}
