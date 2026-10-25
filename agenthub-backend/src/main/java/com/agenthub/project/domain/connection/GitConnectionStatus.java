package com.agenthub.project.domain.connection;

/**
 * Git connection lifecycle states (Phase 11).
 *
 * ACTIVE  -> REVOKED : idempotent user-initiated revocation; terminal for the row.
 * A revoked row keeps repo metadata for audit but its credential reference is cleared;
 * re-authorization of the same repository creates a new ACTIVE row.
 */
public enum GitConnectionStatus {
    ACTIVE,
    REVOKED
}
