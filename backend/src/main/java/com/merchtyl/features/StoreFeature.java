package com.merchtyl.features;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.store.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "store_features",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_store_features_store_definition",
                columnNames = {"store_id", "feature_definition_id"}))
public class StoreFeature extends BaseUuidEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feature_definition_id", nullable = false)
    private FeatureDefinition featureDefinition;

    @Column(nullable = false)
    private boolean enabled;

    protected StoreFeature() {
    }

    StoreFeature(Store store, FeatureDefinition featureDefinition, boolean enabled) {
        this.store = store;
        this.featureDefinition = featureDefinition;
        this.enabled = enabled;
        initializeIdAndTimestamps();
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Store getStore() {
        return store;
    }

    public FeatureDefinition getFeatureDefinition() {
        return featureDefinition;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
