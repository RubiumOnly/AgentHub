package com.agenthub.agent.domain.definition;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class ProviderPolicy implements Serializable {

    private String preferredProviderType;
    private String preferredModel;
    private Set<String> allowedProviderTypes = new HashSet<>();
    private Double maxCostPerCall;
    private Double temperature;
    private boolean requireLive = false;
    private String dataPolicy = "ALLOW_CLOUD"; // NO_DATA_LOGGING, LOCAL_ONLY, ALLOW_CLOUD

    public ProviderPolicy() {}

    public ProviderPolicy(String preferredProviderType, String preferredModel, boolean requireLive) {
        this.preferredProviderType = preferredProviderType;
        this.preferredModel = preferredModel;
        this.requireLive = requireLive;
    }

    public String getPreferredProviderType() { return preferredProviderType; }
    public void setPreferredProviderType(String preferredProviderType) { this.preferredProviderType = preferredProviderType; }
    public String getPreferredModel() { return preferredModel; }
    public void setPreferredModel(String preferredModel) { this.preferredModel = preferredModel; }
    public Set<String> getAllowedProviderTypes() { return allowedProviderTypes; }
    public void setAllowedProviderTypes(Set<String> allowedProviderTypes) { this.allowedProviderTypes = allowedProviderTypes != null ? allowedProviderTypes : new HashSet<>(); }
    public Double getMaxCostPerCall() { return maxCostPerCall; }
    public void setMaxCostPerCall(Double maxCostPerCall) { this.maxCostPerCall = maxCostPerCall; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public boolean isRequireLive() { return requireLive; }
    public void setRequireLive(boolean requireLive) { this.requireLive = requireLive; }
    public String getDataPolicy() { return dataPolicy; }
    public void setDataPolicy(String dataPolicy) { this.dataPolicy = dataPolicy; }
}
