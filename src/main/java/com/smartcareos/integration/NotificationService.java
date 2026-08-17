package com.smartcareos.integration;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartcareos.identity.TenantContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {
    private final JdbcTemplate jdbc;
    private final ExternalGatewayClient gateway;
    public NotificationService(JdbcTemplate jdbc,ExternalGatewayClient gateway) { this.jdbc = jdbc; this.gateway=gateway; }
    @Transactional public Map<String,Object> create(String tenant, String type, String businessId,
            String channel, String recipient, String summary) {
        String id=UUID.randomUUID().toString(); Instant now=Instant.now();
        jdbc.update("INSERT INTO notification_delivery(id,tenant_id,business_type,business_id,channel,recipient,summary,status,attempt_count,created_at) VALUES(?,?,?,?,?,?,?,'PENDING',0,?)",
                id,tenant,type,businessId,channel,recipient,summary,Timestamp.from(now));
        return get(id);
    }
    @Transactional public Map<String,Object> finish(String id, boolean sent, String error) {
        String tenant=tenant(id);
        int count=jdbc.update("UPDATE notification_delivery SET status=?,attempt_count=attempt_count+1,last_error=?,completed_at=? WHERE id=? AND tenant_id=? AND status='PENDING'",
                sent?"SENT":"FAILED",error,Timestamp.from(Instant.now()),id,tenant);
        if(count!=1) throw new IntegrationConflictException("notification must be pending");
        return get(id);
    }
    @Transactional public Map<String,Object> dispatch(String id) {
        Map<String,Object> delivery=get(id);
        if(!"PENDING".equals(column(delivery,"status"))) throw new IntegrationConflictException("notification must be pending");
        var result=gateway.notify((String)column(delivery,"channel"),(String)column(delivery,"recipient"),(String)column(delivery,"summary"));
        int count=jdbc.update("UPDATE notification_delivery SET status=?,attempt_count=attempt_count+1,last_error=?,external_reference=?,completed_at=? WHERE id=? AND tenant_id=? AND status='PENDING'",
                result.accepted()?"SENT":"FAILED",result.accepted()?null:result.message(),result.reference(),Timestamp.from(Instant.now()),id,tenant(id));
        if(count!=1) throw new IntegrationConflictException("notification must be pending");
        return get(id);
    }
    public Map<String,Object> get(String id) {
        return jdbc.queryForMap("SELECT id,tenant_id,business_type,business_id,channel,recipient,summary,status,attempt_count,last_error,external_reference,created_at,completed_at FROM notification_delivery WHERE id=? AND tenant_id=?",id,tenant(id));
    }
    private String tenant(String id) {
        TenantContext.Identity identity=TenantContext.current();
        if(identity!=null) return identity.tenantId();
        return jdbc.queryForObject("SELECT tenant_id FROM notification_delivery WHERE id=?",String.class,id);
    }
    private static Object column(Map<String,Object> row,String name) {
        return row.entrySet().stream().filter(e->e.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }
}
