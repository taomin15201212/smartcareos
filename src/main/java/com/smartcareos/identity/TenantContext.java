package com.smartcareos.identity;

public final class TenantContext {
    private static final ThreadLocal<Identity> CURRENT = new ThreadLocal<>();
    private TenantContext() {}
    public static void set(Identity identity) { CURRENT.set(identity); }
    public static Identity current() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }
    public record Identity(String tenantId, String principalId, String credentialId, Role role) {}

    public enum Role {
        ADMIN, OPERATOR, CAREGIVER, AUDITOR, DEVICE_INGEST;

        public static Role parse(String value) {
            try { return Role.valueOf(value); }
            catch (RuntimeException exception) { return OPERATOR; }
        }
    }
}
