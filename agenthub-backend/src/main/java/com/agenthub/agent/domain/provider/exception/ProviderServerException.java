package com.agenthub.agent.domain.provider.exception;

import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;

public class ProviderServerException extends BusinessException {
    private final String providerId;
    private final int statusCode;

    public ProviderServerException(String providerId, int statusCode, String message) {
        super(ErrorCode.PROVIDER_UNAVAILABLE, message);
        this.providerId = providerId;
        this.statusCode = statusCode;
    }

    public String getProviderId() { return providerId; }
    public int getStatusCode() { return statusCode; }
}
