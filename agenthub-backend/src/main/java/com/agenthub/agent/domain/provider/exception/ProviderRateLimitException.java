package com.agenthub.agent.domain.provider.exception;

import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;

public class ProviderRateLimitException extends BusinessException {
    private final String providerId;
    private final String model;

    public ProviderRateLimitException(String providerId, String model, String message) {
        super(ErrorCode.PROVIDER_RATE_LIMIT, message);
        this.providerId = providerId;
        this.model = model;
    }

    public String getProviderId() { return providerId; }
    public String getModel() { return model; }
}
