package com.smartcareos.identity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TenantResourceResolver {
    private final JdbcTemplate jdbc;
    private final List<Route> routes = List.of(
            route("^/api/v1/alarms/([^/]+)(?:/.*)?$", "SELECT tenant_id FROM alarm WHERE id=?"),
            route("^/api/v1/care-plans/([^/]+)(?:/.*)?$", "SELECT tenant_id FROM care_plan WHERE id=?"),
            route("^/api/v1/care-tasks/([^/]+)(?:/.*)?$", "SELECT tenant_id FROM care_task WHERE id=?"),
            route("^/api/v1/devices/([^/]+)(?:/.*)?$", "SELECT tenant_id FROM device WHERE id=?"),
            route("^/api/v1/institutions/([^/]+)(?:/.*)?$", "SELECT tenant_id FROM institution WHERE id=?"),
            route("^/api/v1/rooms/([^/]+)(?:/.*)?$", "SELECT i.tenant_id FROM institution_room r JOIN institution i ON i.id=r.institution_id WHERE r.id=?"),
            route("^/api/v1/beds/([^/]+)(?:/.*)?$", "SELECT i.tenant_id FROM institution_bed b JOIN institution_room r ON r.id=b.room_id JOIN institution i ON i.id=r.institution_id WHERE b.id=?"),
            route("^/api/v1/elders/([^/]+)(?:/.*)?$", "SELECT tenant_id FROM elder WHERE id=?"),
            route("^/api/v1/admissions/([^/]+)(?:/.*)?$", "SELECT tenant_id FROM admission WHERE id=?"),
            route("^/api/v1/notification-deliveries/([^/]+)(?:/.*)?$", "SELECT tenant_id FROM notification_delivery WHERE id=?"),
            route("^/api/v1/government-exchanges/([^/]+)(?:/.*)?$", "SELECT tenant_id FROM government_exchange_task WHERE id=?"),
            route("^/api/v1/api-credentials/([^/]+)(?:/.*)?$", "SELECT tenant_id FROM api_credential WHERE id=?")
    );
    public TenantResourceResolver(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<String> resolve(String path) {
        for (Route route : routes) {
            Matcher matcher = route.pattern.matcher(path);
            if (matcher.matches()) {
                return jdbc.query(route.sql, (rs, row) -> rs.getString(1), matcher.group(1))
                        .stream().findFirst();
            }
        }
        return Optional.empty();
    }
    private static Route route(String pattern, String sql) { return new Route(Pattern.compile(pattern), sql); }
    private record Route(Pattern pattern, String sql) {}
}
