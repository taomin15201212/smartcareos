package com.smartcareos.system;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.jdbc.core.JdbcTemplate;
import org.flywaydb.core.Flyway;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
public class SystemHealthController {
    private final JdbcTemplate jdbc;
    private final Flyway flyway;
    public SystemHealthController(JdbcTemplate jdbc, Flyway flyway) { this.jdbc = jdbc; this.flyway = flyway; }
    @GetMapping("/health")
    @Operation(tags = "09 运营与系统", summary = "查询应用健康状态",
            description = "匿名接口；同时验证数据库连通性和 Flyway 版本")
    @SecurityRequirements
    public Map<String,Object> health() {
        jdbc.queryForObject("SELECT 1", Integer.class);
        String version = flyway.info().current().getVersion().getVersion();
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("status", "UP"); result.put("database", "UP");
        result.put("schemaVersion", version); result.put("time", Instant.now().toString());
        return result;
    }
}
