package com.agenthub.adapter.web;

import com.agenthub.adapter.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemHealthController {

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.ok(Map.of(
                "status", "UP",
                "app", "AgentHub Enterprise Core",
                "version", "1.0.0",
                "javaVersion", System.getProperty("java.version"),
                "os", System.getProperty("os.name")
        ));
    }
}
