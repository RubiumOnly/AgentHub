package com.agenthub.agent.api;

import com.agenthub.adapter.common.Result;
import com.agenthub.agent.application.ProviderApplication;
import com.agenthub.agent.domain.provider.model.ChatRequest;
import com.agenthub.agent.dto.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/providers")
public class ProviderController {

    private final ProviderApplication providerApplication;

    public ProviderController(ProviderApplication providerApplication) {
        this.providerApplication = providerApplication;
    }

    @GetMapping
    public Result<List<ProviderView>> listProviders() {
        return Result.ok(providerApplication.listProviders());
    }

    @GetMapping("/{id}")
    public Result<ProviderView> getProvider(@PathVariable String id) {
        return Result.ok(providerApplication.getProviderById(id));
    }

    @PostMapping
    public Result<ProviderView> createProvider(@RequestBody ProviderCreateCommand cmd) {
        return Result.ok(providerApplication.createProvider(cmd));
    }

    @PutMapping("/{id}")
    public Result<ProviderView> updateProvider(@PathVariable String id, @RequestBody ProviderUpdateCommand cmd) {
        return Result.ok(providerApplication.updateProvider(id, cmd));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteProvider(@PathVariable String id) {
        providerApplication.deleteProvider(id);
        return Result.ok(null);
    }

    @PostMapping("/route")
    public Result<RouteDecisionView> evaluateRoute(@RequestBody ChatRequest request) {
        return Result.ok(providerApplication.evaluateRoute(request));
    }

    @PostMapping("/{id}/test")
    public Result<ProviderTestResultView> testProvider(@PathVariable String id,
                                                       @RequestParam(required = false) String prompt) {
        return Result.ok(providerApplication.testProvider(id, prompt));
    }

    @GetMapping("/health")
    public Result<List<ProviderHealthView>> getClusterHealth() {
        return Result.ok(providerApplication.getClusterHealth());
    }

    @PostMapping("/{id}/probe")
    public Result<ProviderProbeResultView> probeProvider(@PathVariable String id) {
        return Result.ok(providerApplication.probeProvider(id));
    }

    @GetMapping("/{id}/circuit-breaker")
    public Result<CircuitBreakerView> getCircuitBreaker(@PathVariable String id) {
        return Result.ok(providerApplication.getCircuitBreaker(id));
    }

    @PostMapping("/{id}/circuit-breaker/reset")
    public Result<CircuitBreakerView> resetCircuitBreaker(@PathVariable String id) {
        return Result.ok(providerApplication.resetCircuitBreaker(id));
    }
}
