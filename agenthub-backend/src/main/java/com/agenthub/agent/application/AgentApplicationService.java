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
import com.agenthub.agent.domain.context.AssembledContext;
import com.agenthub.agent.domain.context.ContextAssemblyEngine;
import com.agenthub.agent.domain.definition.*;
import com.agenthub.project.domain.context.SecurityLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AgentApplicationService implements AgentApplication {

    private static final Logger log = LoggerFactory.getLogger(AgentApplicationService.class);

    private final AgentDefinitionRepository definitionRepository;
    private final AgentInstanceRepository instanceRepository;
    private final ProviderRepository providerRepository;
    private final AgentAdapterFactory adapterFactory;
    private final ToolRegistry toolRegistry;
    private final ContextAssemblyEngine contextAssemblyEngine;
    private final ObjectMapper objectMapper;

    public AgentApplicationService(AgentDefinitionRepository definitionRepository,
                                   AgentInstanceRepository instanceRepository,
                                   ProviderRepository providerRepository,
                                   AgentAdapterFactory adapterFactory,
                                   ToolRegistry toolRegistry,
                                   ContextAssemblyEngine contextAssemblyEngine,
                                   ObjectMapper objectMapper) {
        this.definitionRepository = definitionRepository;
        this.instanceRepository = instanceRepository;
        this.providerRepository = providerRepository;
        this.adapterFactory = adapterFactory;
        this.toolRegistry = toolRegistry;
        this.contextAssemblyEngine = contextAssemblyEngine;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initCanonicalDefinitions() {
        seedIfMissing("backend-architect", "后端架构师 (Backend Architect)", "ARCHITECT",
                "需求分析、Kahn DAG 计划生成与架构防御守卫",
                "你是一名具备深厚 DDD 与 Spring Boot 架构功底的系统架构师，负责变更范围界定与结构化计划编排。",
                List.of("file_read", "file_tree", "code_analyze"),
                SecurityLevel.INTERNAL);

        seedIfMissing("backend-developer", "后端研发工程师 (Backend Developer)", "DEVELOPER",
                "代码实现、接口编写与缺陷修复",
                "你是一名资深 Java 后端开发工程师，遵循 Clean Code 与守卫从句规范，实现精确的代码变更。",
                List.of("file_read", "file_write"),
                SecurityLevel.INTERNAL);

        seedIfMissing("qa-tester", "测试防御专家 (QA Tester)", "TESTER",
                "五维测试矩阵防御编写与真实回归验证",
                "你是一名严苛的 QA 测试防御专家，负责编写覆盖边界极值、并发竞态与空指针防御的自动化测试用例。",
                List.of("file_read", "file_write", "command_exec"),
                SecurityLevel.RESTRICTED);

        seedIfMissing("security-reviewer", "安全审查专员 (Security Reviewer)", "REVIEWER",
                "JGit Diff 审查、质量门禁审计与越权拦截",
                "你是一名软件安全审查专员，负责对 Git diff 进行逐行安全审计，阻断越权与高危依赖引入。",
                List.of("file_read", "git_operation", "code_analyze"),
                SecurityLevel.INTERNAL);
    }

    private void seedIfMissing(String defKey, String name, String role, String objective, String prompt, List<String> tools, SecurityLevel securityLevel) {
        try {
            if (definitionRepository.findByDefKey(defKey).isEmpty()) {
                AgentDefinitionManifest manifest = new AgentDefinitionManifest();
                manifest.setRole(role);
                manifest.setObjective(objective);
                manifest.setSystemPrompt(prompt);
                manifest.setTools(tools);
                manifest.setSecurityLevel(securityLevel);
                String manifestJson = objectMapper.writeValueAsString(manifest);

                AgentDefinitionEntity entity = new AgentDefinitionEntity(
                        "def-" + defKey,
                        defKey,
                        name,
                        role,
                        manifestJson,
                        "1.0.0"
                );
                definitionRepository.save(entity);
                log.info("Seeded canonical AgentDefinition [{}]", defKey);
            }
        } catch (Exception e) {
            log.warn("Failed to seed agent definition [{}]: {}", defKey, e.getMessage());
        }
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
    public List<AgentToolDefinition> listTools() {
        return toolRegistry.listAllTools();
    }

    @Override
    public AssembledContext assembleContext(List<ContextAssemblyEngine.LayerInput> layerInputs) {
        return contextAssemblyEngine.assemble(layerInputs);
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
