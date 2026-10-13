package com.agenthub.adapter.web;

import com.agenthub.adapter.common.Result;
import com.agenthub.domain.sandbox.model.DeploymentManifest;
import com.agenthub.sandbox.application.SandboxApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Deprecated
@Profile("!prod")
@RestController
@RequestMapping("/api/sandbox")
public class SandboxController {

    private final SandboxApplication sandboxApplication;

    public SandboxController(SandboxApplication sandboxApplication) {
        this.sandboxApplication = sandboxApplication;
    }

    @GetMapping("/preview/{projectId}")
    public ResponseEntity<String> getPreviewHtml(@PathVariable("projectId") String projectId) {
        String html = sandboxApplication.renderPreviewHtml(projectId);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, java.nio.charset.StandardCharsets.UTF_8))
                .body(html);
    }

    @PostMapping("/deploy/{projectId}")
    public Result<DeploymentManifest> deploy(@PathVariable("projectId") String projectId) {
        DeploymentManifest manifest = sandboxApplication.deployProject(projectId);
        return Result.ok(manifest);
    }
}
