package com.agenthub.agent.application;

import com.agenthub.agent.dto.AgentDefinitionView;
import com.agenthub.agent.dto.AgentInstanceView;
import com.agenthub.agent.dto.CreateAgentInstanceCommand;
import com.agenthub.agent.dto.ProviderView;
import com.agenthub.agent.infrastructure.entity.AgentDefinitionEntity;
import com.agenthub.agent.infrastructure.entity.AgentInstanceEntity;
import com.agenthub.agent.infrastructure.entity.ProviderEntity;
import com.agenthub.agent.infrastructure.repository.AgentDefinitionRepository;
import com.agenthub.agent.infrastructure.repository.AgentInstanceRepository;
import com.agenthub.agent.infrastructure.repository.ProviderRepository;
import com.agenthub.domain.agent.model.AgentExecutionRequest;
import com.agenthub.domain.agent.model.AgentExecutionResult;
import com.agenthub.domain.agent.model.AgentPlatformType;
import com.agenthub.domain.agent.service.AgentAdapterFactory;
import com.agenthub.domain.agent.spi.UnifiedAgentAdapter;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AgentApplicationService implements AgentApplication {

    private final AgentDefinitionRepository definitionRepository;
    private final AgentInstanceRepository instanceRepository;
    private final ProviderRepository providerRepository;
    private final AgentAdapterFactory adapterFactory;

    public AgentApplicationService(AgentDefinitionRepository definitionRepository,
                                   AgentInstanceRepository instanceRepository,
                                   ProviderRepository providerRepository,
                                   AgentAdapterFactory adapterFactory) {
        this.definitionRepository = definitionRepository;
        this.instanceRepository = instanceRepository;
        this.providerRepository = providerRepository;
        this.adapterFactory = adapterFactory;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentDefinitionView> listDefinitions() {
        return definitionRepository.findAll().stream()
                .map(this::toView)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public AgentDefinitionView getDefinitionByKey(String defKey) {
        AgentDefinitionEntity def = definitionRepository.findByDefKey(defKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND, "Agent definition not found: " + defKey));
        return toView(def);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentInstanceView> listCurrentUserInstances() {
        String userId = RequestContext.get().getUserId();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to list agent instances");
        }
        return instanceRepository.findByOwnerId(userId).stream()
                .map(this::toView)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AgentInstanceView createInstance(CreateAgentInstanceCommand cmd) {
        if (cmd == null || cmd.getDefinitionId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Definition ID must not be null");
        }
        String userId = RequestContext.get().getUserId();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to create agent instance");
        }
        String id = "inst-" + UUID.randomUUID().toString().substring(0, 8);
        AgentInstanceEntity entity = new AgentInstanceEntity(
                id,
                userId,
                cmd.getProjectId(),
                cmd.getDefinitionId(),
                cmd.getRuntimeType() != null ? cmd.getRuntimeType() : "CLI",
                cmd.getProviderId(),
                "ACTIVE"
        );
        instanceRepository.save(entity);
        return toView(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProviderView> listCurrentUserProviders() {
        String userId = RequestContext.get().getUserId();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to list providers");
        }
        return providerRepository.findByOwnerId(userId).stream()
                .map(this::toView)
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getSupportedPlatforms() {
        Map<AgentPlatformType, Boolean> availability = adapterFactory.checkAllPlatformAvailability();
        return availability.entrySet().stream()
                .map(entry -> {
                    UnifiedAgentAdapter adapter = adapterFactory.getAdapter(entry.getKey());
                    return Map.<String, Object>of(
                            "code", entry.getKey().getCode(),
                            "name", entry.getKey().getDescription(),
                            "available", entry.getValue(),
                            "version", adapter != null ? adapter.checkVersion() : "N/A"
                    );
                })
                .collect(Collectors.toList());
    }

    @Override
    public AgentExecutionResult execute(AgentExecutionRequest request) {
        UnifiedAgentAdapter adapter = adapterFactory.getAdapter(request.getPlatformType());
        return adapter.execute(request);
    }

    private AgentDefinitionView toView(AgentDefinitionEntity entity) {
        return new AgentDefinitionView(
                entity.getId(),
                entity.getDefKey(),
                entity.getName(),
                entity.getRole(),
                entity.getManifest(),
                entity.getVersion(),
                entity.getCreatedAt()
        );
    }

    private AgentInstanceView toView(AgentInstanceEntity entity) {
        return new AgentInstanceView(
                entity.getId(),
                entity.getOwnerId(),
                entity.getProjectId(),
                entity.getDefinitionId(),
                entity.getRuntimeType(),
                entity.getProviderId(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }

    private ProviderView toView(ProviderEntity entity) {
        String maskedSecret = com.agenthub.agent.infrastructure.security.SecretMasker.maskSecret(entity.getSecretRef());
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
                entity.getCircuitStatus(),
                entity.getAvgLatencyMs() != null ? entity.getAvgLatencyMs() : 0L,
                entity.getCreatedAt()
        );
    }
}
