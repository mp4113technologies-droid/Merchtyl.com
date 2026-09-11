package com.merchtyl.discount;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record PromotionTarget(
        @Enumerated(EnumType.STRING) @Column(name = "target_type", nullable = false, length = 32) PromotionTargetType targetType,
        @Column(name = "target_id", nullable = false) UUID targetId
) implements Serializable {
}
