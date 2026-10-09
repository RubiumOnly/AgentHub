package com.agenthub.agent.application;

import com.agenthub.agent.domain.provider.model.*;
import com.agenthub.agent.domain.provider.router.DynamicProviderRouter;
import com.agenthub.agent.domain.provider.spi.LlmProvider;
import com.agenthub.agent.dto.*;
import com.agenthub.agent.infrastructure.entity.ProviderEntity;
import com.agenthub.agent.infrastructure.repository.ProviderRepository;
import com.agenthub.agent.infrastructure.security.SecretMasker;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.agenthub.identity.infrastructure.security.ResourceAccessGuard;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class ProviderApplicationService implements ProviderApplication {

    private final ProviderRepository providerRepository;
    private final DynamicProviderRouter router;
    private final Environment environment;
    private final ResourceAccessGuard accessGuard;

    public ProviderApplicationService(ProviderRepository providerRepository,
                                    DynamicProviderRouter router,
                                    Environment environment) {
        this(providerRepository, router, environment, null);
    }

    @Autowired
    public ProviderApplicationService(ProviderRepository providerRepository,
                                    DynamicProviderRouter router,
                                    Environment environment,
                                    @Autowired(required = false) ResourceAccessGuard accessGuard) {
        this.providerRepository = providerRepository;
        this.router = router;
        this.environment = environment;
        this.accessGuard = accessGuard;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProviderView> listProviders() {
        List<ProviderEntity> entities = providerRepository.findAll();
        return entities.stream().map(this::toView).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ProviderView getProviderById(String id) {
        ProviderEntity entity = providerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROVIDER_NOT_FOUND, "Provider not found: " + id));
        return toView(entity);
    }

    @Override
    @Transactional
    public ProviderView createProvider(ProviderCreateCommand cmd) {
        if (cmd == null || cmd.getProviderType() == null || cmd.getModel() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Provider type and model must not be null");
        }

        String userId = RequestContext.get() != null ? RequestContext.get().getUserId() : null;
        if (userId == null || userId.isBlank()) {
            if (accessGuard != null && !accessGuard.isAllowSuperuserBypass()) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to create provider");
            }
            userId = "user-1";
        }

        String id = "prov-" + UUID.randomUUID().toString().substring(0, 8);
        ProviderEntity entity = new ProviderEntity();
        entity.setId(id);
        entity.setOwnerId(userId);
        entity.setProviderType(cmd.getProviderType().toUpperCase());
        entity.setBaseUrl(cmd.getBaseUrl());
        entity.setSecretRef(cmd.getSecretRef());
        entity.setModel(cmd.getModel());
        entity.setStatus("ACTIVE");
        entity.setPriority(cmd.getPriority() > 0 ? cmd.getPriority() : 100);
        entity.setWeight(cmd.getWeight() > 0 ? cmd.getWeight() : 1);
        entity.setCapabilities(cmd.getCapabilities() != null ? cmd.getCapabilities() : "general");
        entity.setCostPerMillionInput(cmd.getCostPerMillionInput() != null ? cmd.getCostPerMillionInput() : 0.0);
        entity.setCostPerMillionOutput(cmd.getCostPerMillionOutput() != null ? cmd.getCostPerMillionOutput() : 0.0);
        entity.setCircuitStatus("CLOSED");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        providerRepository.save(entity);
        router.syncFromDatabase();

        return toView(entity);
    }

    @Override
    @Transactional
    public ProviderView updateProvider(String id, ProviderUpdateCommand cmd) {
        ProviderEntity entity = providerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROVIDER_NOT_FOUND, "Provider not found: " + id));

        if (accessGuard != null && entity.getOwnerId() != null) {
            accessGuard.checkOwnership(entity.getOwnerId(), RequestContext.get().getUserId());
        }

        if (cmd.getBaseUrl() != null) entity.setBaseUrl(cmd.getBaseUrl());
        if (cmd.getSecretRef() != null) entity.setSecretRef(cmd.getSecretRef());
        if (cmd.getModel() != null) entity.setModel(cmd.getModel());
        if (cmd.getPriority() != null) entity.setPriority(cmd.getPriority());
        if (cmd.getWeight() != null) entity.setWeight(cmd.getWeight());
        if (cmd.getCapabilities() != null) entity.setCapabilities(cmd.getCapabilities());
        if (cmd.getStatus() != null) entity.setStatus(cmd.getStatus());
        if (cmd.getCostPerMillionInput() != null) entity.setCostPerMillionInput(cmd.getCostPerMillionInput());
        if (cmd.getCostPerMillionOutput() != null) entity.setCostPerMillionOutput(cmd.getCostPerMillionOutput());
        entity.setUpdatedAt(LocalDateTime.now());

        providerRepository.save(entity);
        router.syncFromDatabase();

        return toView(entity);
    }

    @Override
    @Transactional
    public void deleteProvider(String id) {
        ProviderEntity entity = providerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROVIDER_NOT_FOUND, "Provider not found: " + id));
        if (accessGuard != null && entity.getOwnerId() != null) {
            accessGuard.checkOwnership(entity.getOwnerId(), RequestContext.get().getUserId());
        }
        providerRepository.deleteById(id);
        router.unregisterProvider(id);
    }

    @Override
    public RouteDecisionView evaluateRoute(ChatRequest request) {
        List<LlmProvider> candidates = router.selectCandidates(request);
        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_AVAILABLE_PROVIDER, "No available provider matching routing requirements");
        }
        LlmProvider primary = candidates.get(0);
        ModelPricing.PriceRate rate = ModelPricing.getRateForModel(primary.getModel());
        double inPrice = primary.getCostPerMillionInput() > 0 ? primary.getCostPerMillionInput() : rate.inputPerMillion();
        double outPrice = primary.getCostPerMillionOutput() > 0 ? primary.getCostPerMillionOutput() : rate.outputPerMillion();

        List<String> candidateIds = candidates.stream().map(LlmProvider::getId).collect(Collectors.toList());

        return new RouteDecisionView(
                primary.getId(),
                primary.getName(),
                primary.getProviderType(),
                primary.getModel(),
                primary.getPriority(),
                inPrice,
                outPrice,
                candidateIds
        );
    }

    @Override
    public ProviderTestResultView testProvider(String id, String testPrompt) {
        LlmProvider provider = router.getProviderById(id);
        if (provider == null) {
            throw new BusinessException(ErrorCode.PROVIDER_NOT_FOUND, "Provider not registered in runtime router: " + id);
        }

        String prompt = testPrompt != null && !testPrompt.isBlank() ? testPrompt : "Hello from AgentHub test ping";
        ChatRequest req = ChatRequest.of(provider.getModel(), prompt);

        long start = System.currentTimeMillis();
        ChatResponse resp = provider.chat(req);
        long latency = System.currentTimeMillis() - start;

        String snippet = resp.getContent();
        if (snippet.length() > 200) {
            snippet = snippet.substring(0, 200) + "...";
        }

        return new ProviderTestResultView(
                provider.getId(),
                "OK",
                latency,
                snippet,
                resp.getUsage(),
                resp.getEstimatedCost()
        );
    }

    private ProviderView toView(ProviderEntity entity) {
        LlmProvider runtimeProvider = router.getProviderById(entity.getId());
        String circuitStatus = runtimeProvider != null ? runtimeProvider.getCircuitBreaker().getStatus().name() : entity.getCircuitStatus();
        long avgLatency = runtimeProvider != null ? runtimeProvider.checkHealth().getLatencyMs() : (entity.getAvgLatencyMs() != null ? entity.getAvgLatencyMs() : 0L);

        // Mask secretRef for API safety
        String maskedSecret = SecretMasker.maskSecret(entity.getSecretRef());

        return new ProviderView(
                entity.getId(),
                entity.getOwnerId(),
                entity.getProviderType(),
                entity.getBaseUrl(),
                maskedSecret,
                entity.getModel(),
                entity.getStatus(),
                entity.getPriority(),
                entity.getWeight(),
                entity.getCapabilities(),
                entity.getCostPerMillionInput(),
                entity.getCostPerMillionOutput(),
                circuitStatus,
                avgLatency,
                entity.getCreatedAt()
        );
    }
}
