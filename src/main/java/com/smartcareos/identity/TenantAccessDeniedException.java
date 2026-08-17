package com.smartcareos.identity;
public class TenantAccessDeniedException extends RuntimeException {
    public TenantAccessDeniedException() { super("resource is outside the authenticated tenant boundary"); }
}
