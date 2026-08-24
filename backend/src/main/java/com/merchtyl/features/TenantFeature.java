package com.merchtyl.features;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "tenant_features",
        uniqueConstraints = @UniqueConstraint(name = "uq_tenant_features_definition", columnNames = "feature_definition_id"))
public class TenantFeature extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feature_definition_id", nullable = false)
    private FeatureDefinition featureDefinition;

    @Column(nullable = false)
    private boolean enabled;

    protected TenantFeature() {
    }

    TenantFeature(FeatureDefinition featureDefinition, boolean enabled) {
        this.featureDefinition = featureDefinition;
        this.enabled = enabled;
        initializeIdAndTimestamps();
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public FeatureDefinition getFeatureDefinition() {
        return featureDefinition;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
