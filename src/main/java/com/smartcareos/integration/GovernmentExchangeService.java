package com.smartcareos.integration;

import com.smartcareos.identity.ApiKeyAuthenticationInterceptor;
import com.smartcareos.identity.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
public class GovernmentExchangeService {
    private final JdbcTemplate jdbc;
    private final ExternalGatewayClient gateway;
    public GovernmentExchangeService(JdbcTemplate jdbc,ExternalGatewayClient gateway) { this.jdbc=jdbc; this.gateway=gateway; }
    @Transactional public Map<String,Object> create(String tenant,String contract,String mapping,
            LocalDate start,LocalDate end,String payload) {
        if(end.isBefore(start)) throw new IllegalArgumentException("periodEnd must not precede periodStart");
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO government_exchange_task(id,tenant_id,contract_code,mapping_version,period_start,period_end,payload_hash,status,created_at) VALUES(?,?,?,?,?,?,?,'PENDING',?)",
                id,tenant,contract,mapping,Date.valueOf(start),Date.valueOf(end),
                ApiKeyAuthenticationInterceptor.sha256(payload),Timestamp.from(Instant.now()));
        return get(id);
    }
    @Transactional public Map<String,Object> submit(String id) {
        if(jdbc.update("UPDATE government_exchange_task SET status='SUBMITTED',submitted_at=? WHERE id=? AND tenant_id=? AND status='PENDING'",Timestamp.from(Instant.now()),id,tenant(id))!=1)
            throw new IntegrationConflictException("government exchange task must be pending");
        return get(id);
    }
    @Transactional public Map<String,Object> dispatch(String id) {
        Map<String,Object> task=get(id);
        if(!"PENDING".equals(column(task,"status"))) throw new IntegrationConflictException("government exchange task must be pending");
        var result=gateway.government((String)column(task,"contract_code"),(String)column(task,"mapping_version"),(String)column(task,"payload_hash"));
        int count=jdbc.update("UPDATE government_exchange_task SET status=?,external_receipt=?,receipt_message=?,submitted_at=?,completed_at=?,dispatch_attempts=dispatch_attempts+1 WHERE id=? AND tenant_id=? AND status='PENDING'",
                result.accepted()?"ACCEPTED":"REJECTED",result.reference(),result.message(),Timestamp.from(Instant.now()),Timestamp.from(Instant.now()),id,tenant(id));
        if(count!=1) throw new IntegrationConflictException("government exchange task must be pending");
        return get(id);
    }
    @Transactional public Map<String,Object> receipt(String id,boolean accepted,String receipt,String message) {
        if(jdbc.update("UPDATE government_exchange_task SET status=?,external_receipt=?,receipt_message=?,completed_at=? WHERE id=? AND tenant_id=? AND status='SUBMITTED'",
                accepted?"ACCEPTED":"REJECTED",receipt,message,Timestamp.from(Instant.now()),id,tenant(id))!=1)
            throw new IntegrationConflictException("government exchange task must be submitted");
        return get(id);
    }
    public Map<String,Object> get(String id) { return jdbc.queryForMap("SELECT * FROM government_exchange_task WHERE id=? AND tenant_id=?",id,tenant(id)); }
    private String tenant(String id) {
        TenantContext.Identity identity=TenantContext.current();
        if(identity!=null) return identity.tenantId();
        return jdbc.queryForObject("SELECT tenant_id FROM government_exchange_task WHERE id=?",String.class,id);
    }
    private static Object column(Map<String,Object> row,String name) {
        return row.entrySet().stream().filter(e->e.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }
}
