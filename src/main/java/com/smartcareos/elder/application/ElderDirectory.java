package com.smartcareos.elder.application;

public interface ElderDirectory {
    void requireActiveElder(String tenantId, String elderId);
}

