package com.agenthub.adapter.web;

import com.agenthub.adapter.common.Result;
import com.agenthub.domain.sandbox.model.DeploymentManifest;
import com.agenthub.sandbox.application.SandboxApplication;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sandbox")
public class SandboxController {

    private final SandboxApplication sandboxApplication;

    public SandboxController(SandboxApplication sandboxApplication) {
        this.sandboxApplication = sandboxApplication;
    }

    @GetMapping(value = "/preview/{projectId}", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> getPreviewHtml(@PathVariable("projectId") String projectId) {
        String html = sandboxApplication.renderPreviewHtml(projectId);
        return ResponseEntity.ok(html);
    }

    @PostMapping("/deploy/{projectId}")
    public Result<DeploymentManifest> deploy(@PathVariable("projectId") String projectId) {
        DeploymentManifest manifest = sandboxApplication.deployProject(projectId);
        return Result.ok(manifest);
    }
}
