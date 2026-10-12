package com.agenthub.sandbox.domain.provider;

import com.agenthub.sandbox.domain.security.CommandSecurityGuard;
import com.agenthub.sandbox.domain.security.EnvironmentSanitizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for resolving SandboxProvider implementations by type.
 */
@Component
public class SandboxProviderFactory {

    private final Map<String, SandboxProvider> providerMap = new ConcurrentHashMap<>();
    private final LocalProcessSandbox defaultLocalSandbox;
    private final DockerSandbox dockerSandbox;

    public SandboxProviderFactory() {
        this(null, false, "", "");
    }

    @Autowired
    public SandboxProviderFactory(
            @Autowired(required = false) Environment environment,
            @Value("${agenthub.sandbox.docker.worker-enabled:false}") boolean dockerWorkerEnabled,
            @Value("${agenthub.sandbox.docker.host:}") String remoteDockerHost,
            @Value("${agenthub.sandbox.docker.context:}") String dockerContext) {
        CommandSecurityGuard securityGuard = new CommandSecurityGuard();
        EnvironmentSanitizer sanitizer = new EnvironmentSanitizer();

        LocalProcessSandbox localSandbox = new LocalProcessSandbox(securityGuard, sanitizer);
        DockerSandbox docker = new DockerSandbox(securityGuard, sanitizer);

        boolean isProd = false;
        if (environment != null) {
            isProd = Arrays.stream(environment.getActiveProfiles())
                    .anyMatch(p -> "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p));
        }
        docker.configureIsolationPolicy(isProd, dockerWorkerEnabled, remoteDockerHost, dockerContext);

        providerMap.put(localSandbox.getProviderType().toUpperCase(), localSandbox);
        providerMap.put(docker.getProviderType().toUpperCase(), docker);
        this.defaultLocalSandbox = localSandbox;
        this.dockerSandbox = docker;
    }

    public SandboxProvider getProvider(String providerType) {
        if (providerType == null || providerType.isBlank()) {
            return defaultLocalSandbox;
        }
        SandboxProvider provider = providerMap.get(providerType.toUpperCase());
        if (provider == null) {
            return defaultLocalSandbox;
        }
        return provider;
    }

    public LocalProcessSandbox getLocalProcessSandbox() {
        return defaultLocalSandbox;
    }

    public DockerSandbox getDockerSandbox() {
        return dockerSandbox;
    }

    public void registerProvider(SandboxProvider provider) {
        if (provider != null) {
            providerMap.put(provider.getProviderType().toUpperCase(), provider);
        }
    }
}
