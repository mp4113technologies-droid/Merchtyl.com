package com.merchtyl.features;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.register.Register;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "register_features",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_register_features_register_definition",
                columnNames = {"register_id", "feature_definition_id"}))
public class RegisterFeature extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "register_id", nullable = false)
    private Register register;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feature_definition_id", nullable = false)
    private FeatureDefinition featureDefinition;

    @Column(nullable = false)
    private boolean enabled;

    protected RegisterFeature() {
    }

    RegisterFeature(Register register, FeatureDefinition featureDefinition, boolean enabled) {
        this.register = register;
        this.featureDefinition = featureDefinition;
        this.enabled = enabled;
        initializeIdAndTimestamps();
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Register getRegister() {
        return register;
    }

    public FeatureDefinition getFeatureDefinition() {
        return featureDefinition;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
