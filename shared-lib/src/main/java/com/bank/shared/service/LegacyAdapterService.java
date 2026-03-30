package com.bank.shared.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

public interface LegacyAdapterService {

    ResponseEntity<Object> forwardRequest(HttpServletRequest request, String legacyPath);

    boolean isModuleMigrated(String moduleName);
}
