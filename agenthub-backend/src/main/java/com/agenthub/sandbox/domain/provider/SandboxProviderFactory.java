package com.agenthub.sandbox.domain.provider;

import com.agenthub.sandbox.domain.security.CommandSecurityGuard;
import com.agenthub.sandbox.domain.security.EnvironmentSanitizer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for resolving SandboxProvider implementations by type.
 */
@Component
public class SandboxProviderFactory {

    private final Map<String, SandboxProvider> providerMap = new ConcurrentHashMap<>();
    private final LocalProcessSandbox defaultLocalSandbox;

    public SandboxProviderFactory() {
        CommandSecurityGuard securityGuard = new CommandSecurityGuard();
        EnvironmentSanitizer sanitizer = new EnvironmentSanitizer();

        LocalProcessSandbox localSandbox = new LocalProcessSandbox(securityGuard, sanitizer);
        DockerSandbox dockerSandbox = new DockerSandbox(securityGuard, sanitizer);

        providerMap.put(localSandbox.getProviderType().toUpperCase(), localSandbox);
        providerMap.put(dockerSandbox.getProviderType().toUpperCase(), dockerSandbox);
        this.defaultLocalSandbox = localSandbox;
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

    public void registerProvider(SandboxProvider provider) {
        if (provider != null) {
            providerMap.put(provider.getProviderType().toUpperCase(), provider);
        }
    }
}
