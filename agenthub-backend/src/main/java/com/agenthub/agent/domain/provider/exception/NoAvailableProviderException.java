package com.agenthub.agent.domain.provider.exception;

import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;

public class NoAvailableProviderException extends BusinessException {
    public NoAvailableProviderException(String message) {
        super(ErrorCode.NO_AVAILABLE_PROVIDER, message);
    }
}
