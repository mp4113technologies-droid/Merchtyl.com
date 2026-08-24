package com.merchtyl.registersession;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.security.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "register_session_operator_history")
public class RegisterSessionOperatorHistory extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "register_session_id", nullable = false)
    private RegisterSession registerSession;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "previous_operator_user_id", nullable = false)
    private User previousOperator;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "new_operator_user_id", nullable = false)
    private User newOperator;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transferred_by_user_id", nullable = false)
    private User transferredBy;
    @Column(nullable = false, length = 1000)
    private String reason;
    @Column(nullable = false)
    private Instant transferredAt;
    @Column(nullable = false, length = 32)
    private String changeType;

    protected RegisterSessionOperatorHistory() {}

    RegisterSessionOperatorHistory(RegisterSession session, User previousOperator, User newOperator,
                                   User transferredBy, String reason, Instant transferredAt) {
        this(session, previousOperator, newOperator, transferredBy, reason, transferredAt, "TRANSFER");
    }

    RegisterSessionOperatorHistory(RegisterSession session, User previousOperator, User newOperator,
                                   User transferredBy, String reason, Instant transferredAt, String changeType) {
        this.registerSession = session;
        this.previousOperator = previousOperator;
        this.newOperator = newOperator;
        this.transferredBy = transferredBy;
        this.reason = reason;
        this.transferredAt = transferredAt;
        this.changeType = changeType;
        initializeIdAndTimestamps();
    }
}
