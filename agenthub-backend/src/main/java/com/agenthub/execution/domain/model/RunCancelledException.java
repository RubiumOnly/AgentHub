package com.agenthub.execution.domain.model;

import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;

/**
 * Exception thrown when an execution token indicates cancellation.
 */
public class RunCancelledException extends BusinessException {

    public RunCancelledException(String message) {
        super(ErrorCode.RUN_CANCELLED, message);
    }
}
