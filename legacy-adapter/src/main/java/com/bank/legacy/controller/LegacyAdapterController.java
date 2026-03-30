package com.bank.legacy.controller;

import com.bank.legacy.service.LegacyAdapterService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
public class LegacyAdapterController {

    private final LegacyAdapterService legacyAdapterService;

    public LegacyAdapterController(LegacyAdapterService legacyAdapterService) {
        this.legacyAdapterService = legacyAdapterService;
    }

    @RequestMapping("/{module}/**")
    public CompletableFuture<ResponseEntity<Object>> route(
            @PathVariable String module,
            HttpServletRequest request) {
        return legacyAdapterService.routeRequest(module, request);
    }
}
