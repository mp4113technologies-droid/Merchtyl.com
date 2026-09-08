package com.merchtyl.discount;

import com.merchtyl.platform.persistence.BaseUuidEntity;
import com.merchtyl.sales.SaleAdjustmentType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "discount_definitions", uniqueConstraints = @UniqueConstraint(name = "uq_discount_definitions_tenant_name", columnNames = {"tenant_id", "name"}))
public class DiscountDefinition extends BaseUuidEntity {
    @Column(name = "tenant_id", nullable = false, updatable = false) private UUID tenantId;
    @Column(nullable = false, length = 120) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private SaleAdjustmentType type;
    @Column(nullable = false, precision = 12, scale = 4) private BigDecimal value;
    @Column(length = 500) private String description;
    @Column(name="minimum_purchase_amount",precision=12,scale=4) private BigDecimal minimumPurchaseAmount;
    @Column(name="maximum_purchase_amount",precision=12,scale=4) private BigDecimal maximumPurchaseAmount;
    @Column(name="maximum_discount_amount",precision=12,scale=4) private BigDecimal maximumDiscountAmount;
    @Column(name="minimum_quantity") private Integer minimumQuantity;
    @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="discount_definition_categories",joinColumns=@JoinColumn(name="discount_definition_id")) @Column(name="category_id") private Set<UUID> eligibleCategoryIds=new LinkedHashSet<>();
    @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="discount_definition_products",joinColumns=@JoinColumn(name="discount_definition_id")) @Column(name="product_id") private Set<UUID> eligibleProductIds=new LinkedHashSet<>();
    @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="discount_definition_stores",joinColumns=@JoinColumn(name="discount_definition_id")) @Column(name="store_id") private Set<UUID> storeIds=new LinkedHashSet<>();
    @Column(name="all_stores",nullable=false) private boolean allStores;
    @Column(name="starts_at") private Instant startsAt;
    @Column(name="ends_at") private Instant endsAt;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_by", nullable = false, updatable = false) private UUID createdBy;
    @Column(name = "updated_by", nullable = false) private UUID updatedBy;

    protected DiscountDefinition() {}
    DiscountDefinition(UUID tenantId, DiscountDefinitionRequest request, UUID actorId) {
        this.tenantId = tenantId; this.createdBy = actorId; update(request, actorId); initializeIdAndTimestamps();
    }
    void update(DiscountDefinitionRequest request, UUID actorId) {
        this.name = request.name().trim(); this.type = request.type(); this.value = request.value();
        this.description = request.description() == null || request.description().isBlank() ? null : request.description().trim();
        this.minimumPurchaseAmount=request.minimumPurchaseAmount(); this.maximumPurchaseAmount=request.maximumPurchaseAmount();
        this.maximumDiscountAmount=request.maximumDiscountAmount(); this.minimumQuantity=request.minimumQuantity();
        replace(eligibleCategoryIds,request.eligibleCategoryIds()); replace(eligibleProductIds,request.eligibleProductIds());
        replace(storeIds,request.storeIds()); this.allStores=request.allStores(); this.startsAt=request.startsAt(); this.endsAt=request.endsAt();
        this.active = request.active(); this.updatedBy = actorId;
    }
    private static void replace(Set<UUID> target,Set<UUID> values){target.clear();if(values!=null)target.addAll(values);}
    public UUID getTenantId(){return tenantId;} public String getName(){return name;} public SaleAdjustmentType getType(){return type;}
    public BigDecimal getValue(){return value;} public String getDescription(){return description;} public boolean isActive(){return active;}
    public UUID getCreatedBy(){return createdBy;} public UUID getUpdatedBy(){return updatedBy;}
    public BigDecimal getMinimumPurchaseAmount(){return minimumPurchaseAmount;} public BigDecimal getMaximumPurchaseAmount(){return maximumPurchaseAmount;}
    public BigDecimal getMaximumDiscountAmount(){return maximumDiscountAmount;} public Integer getMinimumQuantity(){return minimumQuantity;}
    public Set<UUID> getEligibleCategoryIds(){return Set.copyOf(eligibleCategoryIds);} public Set<UUID> getEligibleProductIds(){return Set.copyOf(eligibleProductIds);}
    public Set<UUID> getStoreIds(){return Set.copyOf(storeIds);} public boolean isAllStores(){return allStores;}
    public Instant getStartsAt(){return startsAt;} public Instant getEndsAt(){return endsAt;}
}
