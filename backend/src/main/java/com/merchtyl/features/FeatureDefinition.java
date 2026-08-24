package com.merchtyl.features;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "feature_definitions",
        uniqueConstraints = @UniqueConstraint(name = "uq_feature_definitions_code", columnNames = "code"))
public class FeatureDefinition extends BaseUuidEntity {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private FeatureCode code;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false)
    private boolean defaultEnabled;

    protected FeatureDefinition() {
    }

    FeatureDefinition(FeatureCode code, String name, String description, boolean defaultEnabled) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.defaultEnabled = defaultEnabled;
        initializeIdAndTimestamps();
    }

    public FeatureCode getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isDefaultEnabled() {
        return defaultEnabled;
    }
}
