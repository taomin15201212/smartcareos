package com.smartcareos.system;

import com.smartcareos.identity.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "09 运营与系统", description = "租户运营汇总与平台运行状态")
public class OperationsDashboardController {
    private final JdbcTemplate jdbc;
    public OperationsDashboardController(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @GetMapping("/summary")
    @Operation(summary = "查询租户运营摘要", description = "汇总老人、设备、告警、护理、通知及政务任务数量")
    Map<String,Object> summary(HttpServletRequest request){
        TenantContext.Identity identity=TenantContext.current();
        String tenant=identity==null?request.getHeader("X-SmartCare-Tenant"):identity.tenantId();
        if(tenant==null||tenant.isBlank()) throw new IllegalArgumentException("tenant is required");
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("tenantId",tenant);
        result.put("elders",count("elder",tenant));
        result.put("activeDevices",countWhere("device","status='ACTIVE'",tenant));
        result.put("openAlarms",countWhere("alarm","status<>'CLOSED'",tenant));
        result.put("pendingCareTasks",countWhere("care_task","status IN ('PENDING','IN_PROGRESS')",tenant));
        result.put("failedNotifications",countWhere("notification_delivery","status='FAILED'",tenant));
        result.put("pendingGovernmentExchanges",countWhere("government_exchange_task","status IN ('PENDING','SUBMITTED')",tenant));
        return result;
    }
    private long count(String table,String tenant){return countWhere(table,"1=1",tenant);}
    private long countWhere(String table,String condition,String tenant){
        return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE tenant_id=? AND "+condition,Long.class,tenant);
    }
}
