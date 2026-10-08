package com.agenthub.runtime.port;

import java.util.List;

/**
 * Metadata descriptor for an Agent Runtime.
 */
public class RuntimeDescriptor {
    private final String runtimeType;
    private final String displayName;
    private final String version;
    private final String provider;
    private final List<String> capabilities;
    private final boolean simulated;

    public RuntimeDescriptor(String runtimeType, String displayName, String version, String provider, List<String> capabilities, boolean simulated) {
        this.runtimeType = runtimeType;
        this.displayName = displayName;
        this.version = version;
        this.provider = provider;
        this.capabilities = capabilities;
        this.simulated = simulated;
    }

    public String getRuntimeType() { return runtimeType; }
    public String getDisplayName() { return displayName; }
    public String getVersion() { return version; }
    public String getProvider() { return provider; }
    public List<String> getCapabilities() { return capabilities; }
    public boolean isSimulated() { return simulated; }
}
