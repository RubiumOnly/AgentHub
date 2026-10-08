package com.agenthub.agent.domain.provider.exception;

import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;

public class ProviderUnavailableException extends BusinessException {
    private final String providerId;

    public ProviderUnavailableException(String providerId, String message) {
        super(ErrorCode.PROVIDER_UNAVAILABLE, message);
        this.providerId = providerId;
    }

    public String getProviderId() { return providerId; }
}
