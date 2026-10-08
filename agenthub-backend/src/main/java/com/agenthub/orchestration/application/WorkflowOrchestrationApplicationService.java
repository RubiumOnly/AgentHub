package com.agenthub.orchestration.application;

import com.agenthub.orchestration.domain.dsl.TopologicalSortResult;
import com.agenthub.orchestration.domain.dsl.WorkflowDsl;
import com.agenthub.orchestration.domain.dsl.WorkflowDslValidator;
import com.agenthub.orchestration.dto.CreateWorkflowDefinitionCommand;
import com.agenthub.orchestration.dto.ValidateDslResultView;
import com.agenthub.orchestration.dto.WorkflowDefinitionView;
import com.agenthub.orchestration.infrastructure.entity.WorkflowDefinitionEntity;
import com.agenthub.orchestration.infrastructure.repository.WorkflowDefinitionRepository;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WorkflowOrchestrationApplicationService implements WorkflowOrchestrationApplication {

    private static final Logger log = LoggerFactory.getLogger(WorkflowOrchestrationApplicationService.class);

    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final ObjectMapper objectMapper;

    public WorkflowOrchestrationApplicationService(WorkflowDefinitionRepository workflowDefinitionRepository,
                                                   ObjectMapper objectMapper) {
        this.workflowDefinitionRepository = workflowDefinitionRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public WorkflowDefinitionView createDefinition(CreateWorkflowDefinitionCommand cmd) {
        if (cmd == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Create command must not be null");
        }

        WorkflowDsl dsl = cmd.getDsl();
        if (dsl == null && cmd.getDslJson() != null && !cmd.getDslJson().isBlank()) {
            try {
                dsl = objectMapper.readValue(cmd.getDslJson(), WorkflowDsl.class);
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.WORKFLOW_INVALID, "Malformed DSL JSON: " + e.getMessage());
            }
        }

        if (dsl == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Workflow DSL or dslJson must be provided");
        }

        if (dsl.getId() == null || dsl.getId().isBlank()) {
            dsl.setId("wf-" + UUID.randomUUID().toString().substring(0, 8));
        }
        if (cmd.getName() != null && !cmd.getName().isBlank()) {
            dsl.setName(cmd.getName());
        }
        if (cmd.getDescription() != null) {
            dsl.setDescription(cmd.getDescription());
        }

        // Validate strictly with Kahn's topological cycle detection
        WorkflowDslValidator.validate(dsl);

        String json;
        String checksum;
        try {
            json = objectMapper.writeValueAsString(dsl);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(json.getBytes(StandardCharsets.UTF_8));
            checksum = HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.WORKFLOW_INVALID, "Failed to serialize DSL: " + e.getMessage());
        }

        String defId = dsl.getId();
        String version = cmd.getVersion() != null ? cmd.getVersion() : (dsl.getVersion() != null ? dsl.getVersion() : "1.0.0");
        String schemaVersion = dsl.getSchemaVersion() != null ? dsl.getSchemaVersion() : "v1";

        WorkflowDefinitionEntity entity = new WorkflowDefinitionEntity(
                defId,
                dsl.getName(),
                dsl.getDescription(),
                version,
                schemaVersion,
                json,
                checksum
        );

        WorkflowDefinitionEntity saved = workflowDefinitionRepository.save(entity);
        log.info("Saved workflow definition [{}] (version: {})", defId, version);

        return toView(saved, dsl);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkflowDefinitionView getDefinitionById(String id) {
        WorkflowDefinitionEntity entity = workflowDefinitionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKFLOW_DEFINITION_NOT_FOUND, "Workflow definition not found: " + id));

        WorkflowDsl dsl = null;
        try {
            dsl = objectMapper.readValue(entity.getDslJson(), WorkflowDsl.class);
        } catch (Exception e) {
            log.warn("Failed to parse DSL JSON for definition [{}]: {}", id, e.getMessage());
        }

        return toView(entity, dsl);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowDefinitionView> listDefinitions() {
        return workflowDefinitionRepository.findAll().stream()
                .map(entity -> {
                    WorkflowDsl dsl = null;
                    try {
                        dsl = objectMapper.readValue(entity.getDslJson(), WorkflowDsl.class);
                    } catch (Exception ignored) {}
                    return toView(entity, dsl);
                })
                .collect(Collectors.toList());
    }

    @Override
    public ValidateDslResultView validateDsl(WorkflowDsl dsl) {
        TopologicalSortResult result = WorkflowDslValidator.validate(dsl);
        return new ValidateDslResultView(
                true,
                "Workflow DSL is valid. Kahn topological sort succeeded.",
                result.getSortedNodeIds(),
                dsl.getNodes().size()
        );
    }

    @Override
    public WorkflowDsl parseAndValidateDslJson(String dslJson) {
        if (dslJson == null || dslJson.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "DSL JSON must not be blank");
        }
        WorkflowDsl dsl;
        try {
            dsl = objectMapper.readValue(dslJson, WorkflowDsl.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.WORKFLOW_INVALID, "Malformed DSL JSON: " + e.getMessage());
        }
        WorkflowDslValidator.validate(dsl);
        return dsl;
    }

    private WorkflowDefinitionView toView(WorkflowDefinitionEntity entity, WorkflowDsl dsl) {
        return new WorkflowDefinitionView(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getVersion(),
                entity.getSchemaVersion(),
                entity.getDslJson(),
                dsl,
                entity.getChecksum(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
