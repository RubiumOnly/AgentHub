package com.agenthub.adapter.web;

import com.agenthub.adapter.common.Result;
import com.agenthub.domain.sandbox.model.DeploymentManifest;
import com.agenthub.domain.sandbox.service.PreviewSandboxService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sandbox")
public class SandboxController {

    private final PreviewSandboxService sandboxService;

    public SandboxController(PreviewSandboxService sandboxService) {
        this.sandboxService = sandboxService;
    }

    @GetMapping(value = "/preview/{projectId}", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> getPreviewHtml(@PathVariable("projectId") String projectId) {
        String html = sandboxService.renderPreviewHtml(projectId);
        return ResponseEntity.ok(html);
    }

    @PostMapping("/deploy/{projectId}")
    public Result<DeploymentManifest> deploy(@PathVariable("projectId") String projectId) {
        DeploymentManifest manifest = sandboxService.deployProject(projectId);
        return Result.ok(manifest);
    }
}
