package com.merchtyl.eod;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.security.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "end_of_day_sign_offs")
public class EndOfDaySignOff extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false, updatable = false, unique = true, foreignKey = @ForeignKey(name = "fk_eod_sign_offs_report"))
    private EndOfDayReport report;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manager_user_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_eod_sign_offs_manager"))
    private User manager;

    @Column(nullable = false, updatable = false)
    private Instant signedAt;

    @Column(updatable = false, length = 1000)
    private String notes;

    @Column(updatable = false, length = 1000)
    private String varianceExplanation;

    @Column(nullable = false, updatable = false)
    private boolean confirmationAccepted;

    protected EndOfDaySignOff() {
    }

    public EndOfDaySignOff(EndOfDayReport report, User manager, Instant signedAt, String notes, String varianceExplanation, boolean confirmationAccepted) {
        this.report = report;
        this.manager = manager;
        this.signedAt = signedAt;
        this.notes = notes;
        this.varianceExplanation = varianceExplanation;
        this.confirmationAccepted = confirmationAccepted;
        initializeIdAndTimestamps();
    }

    public User getManager() { return manager; }
    public Instant getSignedAt() { return signedAt; }
    public String getNotes() { return notes; }
    public String getVarianceExplanation() { return varianceExplanation; }
    public boolean isConfirmationAccepted() { return confirmationAccepted; }
}
