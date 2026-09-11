package com.merchtyl.sales;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.cash.CashLedgerDirection;
import com.merchtyl.cash.CashLedgerEntryCommand;
import com.merchtyl.cash.CashLedgerService;
import com.merchtyl.cash.CashLedgerSourceType;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.common.PageResponse;
import com.merchtyl.idempotency.IdempotencyOperationResponse;
import com.merchtyl.idempotency.IdempotencyResult;
import com.merchtyl.idempotency.IdempotencyService;
import com.merchtyl.inventory.InventoryService;
import com.merchtyl.inventory.InventoryStockChangeRequest;
import com.merchtyl.inventory.InventoryTransactionType;
import com.merchtyl.foodmenu.FoodMenuItem;
import com.merchtyl.foodmenu.FoodMenuItemRepository;
import com.merchtyl.discount.DiscountDefinitionService;
import com.merchtyl.discount.DiscountDefinition;
import com.merchtyl.discount.DiscountEngine;
import com.merchtyl.discount.PromotionDomain;
import com.merchtyl.product.Product;
import com.merchtyl.payments.CashRoundingResult;
import com.merchtyl.payments.CashRoundingService;
import com.merchtyl.product.ProductRepository;
import com.merchtyl.product.ProductAvailabilityScope;
import com.merchtyl.product.ProductVariant;
import com.merchtyl.product.ProductVariantRepository;
import com.merchtyl.product.StoreProduct;
import com.merchtyl.product.StoreProductRepository;
import com.merchtyl.registersession.RegisterSession;
import com.merchtyl.registersession.RegisterSessionRepository;
import com.merchtyl.registersession.RegisterSessionStatus;
import com.merchtyl.register.RegisterType;
import com.merchtyl.register.RegisterCapabilityService;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.security.PermissionCode;
import com.merchtyl.security.StoreAccessService;
import com.merchtyl.tax.TaxCalculationRequest;
import com.merchtyl.tax.TaxCalculationResponse;
import com.merchtyl.tax.TaxEngine;
import com.merchtyl.tax.TaxCategoryRepository;
import jakarta.persistence.OptimisticLockException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.slf4j.MDC;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class SaleService {
    private static final int MONEY_SCALE = 2;
    private static final int QUANTITY_SCALE = 4;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String COMPLETE_ENDPOINT = "POST /api/v1/sales/{id}/complete";
    private static final String SALE_REFERENCE_TYPE = "SALE";

    private final SaleRepository saleRepository;
    private final RegisterSessionRepository registerSessionRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final SaleItemHandlerRegistry saleItemHandlerRegistry;
    private final TaxEngine taxEngine;
    private final AuditService auditService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final InventoryService inventoryService;
    private final CashLedgerService cashLedgerService;
    private final CashRoundingService cashRoundingService;
    private final TransactionOperations transactions;
    private final Clock clock;
    @Autowired
    private SaleAdjustmentRepository saleAdjustmentRepository;
    @Autowired
    private StoreAccessService storeAccessService;
    @Autowired
    private StoreProductRepository storeProductRepository;
    @Autowired
    private ProductVariantRepository productVariantRepository;
    @Autowired
    private FoodOrderTokenService foodOrderTokenService;
    @Autowired
    private FoodMenuItemRepository foodMenuItemRepository;
    @Autowired
    private RegisterCapabilityService registerCapabilityService;
    @Autowired
    private DiscountDefinitionService discountDefinitionService;
    @Autowired
    private DiscountEngine discountEngine;
    @Autowired
    private TaxCategoryRepository taxCategoryRepository;

    @Autowired
    public SaleService(
            SaleRepository saleRepository,
            RegisterSessionRepository registerSessionRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            SaleItemHandlerRegistry saleItemHandlerRegistry,
            TaxEngine taxEngine,
            AuditService auditService,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper,
            InventoryService inventoryService,
            CashLedgerService cashLedgerService,
            TransactionOperations idempotencyTransactionOperations) {
        this(saleRepository, registerSessionRepository, productRepository, userRepository, saleItemHandlerRegistry, taxEngine, auditService,
                idempotencyService, objectMapper, inventoryService, cashLedgerService, idempotencyTransactionOperations, Clock.systemUTC());
    }

    SaleService(
            SaleRepository saleRepository,
            RegisterSessionRepository registerSessionRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            SaleItemHandlerRegistry saleItemHandlerRegistry,
            TaxEngine taxEngine,
            AuditService auditService,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper,
            InventoryService inventoryService,
            CashLedgerService cashLedgerService,
            TransactionOperations transactions,
            Clock clock) {
        this.saleRepository = saleRepository;
        this.registerSessionRepository = registerSessionRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.saleItemHandlerRegistry = saleItemHandlerRegistry;
        this.taxEngine = taxEngine;
        this.auditService = auditService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.inventoryService = inventoryService;
        this.cashLedgerService = cashLedgerService;
        this.cashRoundingService = new CashRoundingService();
        this.transactions = transactions;
        this.clock = clock;
    }

    @Transactional
    public SaleResponse createDraft(SaleCreateDraftRequest request, Authentication authentication) {
        User actor = actor(authentication);
        RegisterSession session = findOpenSession(request.registerSessionId());
        validateUserCanUseSession(actor, session, authentication);
        Sale sale = new Sale(
                session.getStore(),
                session.getRegister(),
                session,
                actor,
                request.customerId(),
                session.getBusinessDay() == null
                        ? Instant.now(clock).atZone(ZoneId.of(session.getStore().getTimezone())).toLocalDate()
                        : session.getBusinessDay().getBusinessDate(),
                cleanOptional(request.saleChannel()),
                session.getStore().getCurrencyCode(),
                session.getStore().isPricesIncludeTax());
        Sale saved = save(sale);
        SaleResponse response = SaleResponse.from(saved);
        audit(actor, AuditAction.SALE_DRAFT_CREATED, response, null);
        return response;
    }

    @Transactional
    public SaleResponse checkout(SaleCheckoutRequest request, Authentication authentication) {
        User actor = actor(authentication);
        RegisterSession session = findOpenSession(request.registerSessionId());
        validateUserCanUseSession(actor, session, authentication);
        Sale sale = new Sale(
                session.getStore(), session.getRegister(), session, actor, null,
                session.getBusinessDay() == null
                        ? Instant.now(clock).atZone(ZoneId.of(session.getStore().getTimezone())).toLocalDate()
                        : session.getBusinessDay().getBusinessDate(),
                cleanOptional(request.saleChannel()), session.getStore().getCurrencyCode(),
                session.getStore().isPricesIncludeTax());

        List<DiscountEngine.Line> discountLines=new java.util.ArrayList<>();
        List<DiscountEngine.PromotionLine> promotionLines=new java.util.ArrayList<>();
        for (SaleCheckoutItemRequest line : request.items()) {
            if (!line.isValidShape()) throw new BadRequestException("INVALID_CHECKOUT_ITEM");
            if (line.resolvedLineType() == SaleLineType.CUSTOM_ITEM) {
                if (session.getRegister().getType() != RegisterType.RETAIL) {
                    throw new BadRequestException("RETAIL_REGISTER_REQUIRED");
                }
                requireCustomItemPermission(authentication);
                String description = cleanOptional(line.description());
                if (description == null) throw new BadRequestException("CUSTOM_ITEM_DESCRIPTION_REQUIRED");
                if (line.unitPrice() == null) throw new BadRequestException("CUSTOM_ITEM_PRICE_REQUIRED");
                BigDecimal price = normalizeMoney(line.unitPrice(), "unitPrice");
                if (price.signum() <= 0) throw new BadRequestException("CUSTOM_ITEM_PRICE_INVALID");
                if (line.taxTreatment() == null) throw new BadRequestException("CUSTOM_ITEM_TAX_TREATMENT_REQUIRED");
                String categoryCode = line.taxTreatment() == CustomItemTaxTreatment.TAXABLE ? "STANDARD" : "EXEMPT";
                UUID categoryId = taxCategoryRepository.findByCodeIgnoreCase(categoryCode)
                        .filter(category -> category.isActive())
                        .orElseThrow(() -> new ConflictException("CUSTOM_ITEM_TAX_CATEGORY_NOT_CONFIGURED"))
                        .getId();
                SaleItem item = SaleItem.customItem(sale, description, normalizeQuantity(line.quantity()), price,
                        line.taxTreatment(), categoryId);
                sale.addItem(item);
                discountLines.add(new DiscountEngine.Line(item.getId(), null, null, null, item.getQuantity(),
                        money(price.multiply(item.getQuantity())), true));
                continue;
            }
            ResolvedCheckoutItem resolved = resolveCheckoutItem(session, sale, line);
            Product product = resolved.product();
            ProductVariant variant = resolved.variant();
            BigDecimal unitPrice = resolved.unitPrice();
            SaleItem item = new SaleItem(sale, product, variant, normalizeQuantity(line.quantity()),
                    normalizeMoney(unitPrice, "unitPrice"), moneyZero(), false,
                    Boolean.TRUE.equals(line.ageVerified()), null, cleanOptional(line.preparationInstructions()), null, null);
            if(resolved.menuItemId()!=null){item.snapshotFoodSource(resolved.menuItemId(),resolved.menuItemVariantId(),resolved.menuItemName(),resolved.menuVariantName());item.snapshotFoodModifiers(resolved.modifierNames());item.snapshotFoodComponents(resolved.componentSnapshots());}
            saleItemHandlerRegistry.validate(item.validationRequest());
            sale.addItem(item);
            discountLines.add(new DiscountEngine.Line(item.getId(),product.getId(),resolved.sourceItemId(),resolved.categoryId(),item.getQuantity(),money(unitPrice.multiply(item.getQuantity())),product.hasCapability(com.merchtyl.product.ProductCapability.ALLOW_DISCOUNT)));
            promotionLines.add(new DiscountEngine.PromotionLine(item.getId(), product.getId(), variant==null?null:variant.getId(),
                    resolved.productCategoryId(), resolved.menuItemId(), resolved.menuItemVariantId(), resolved.menuCategoryId(), item.getQuantity(), unitPrice,
                    product.hasCapability(com.merchtyl.product.ProductCapability.ALLOW_DISCOUNT)));
        }
        ResolvedDiscount resolvedDiscount = resolveCheckoutDiscount(sale, request.discount());
        BigDecimal automaticDiscount=applyAutomaticMultiBuy(sale, session, promotionLines, actor,resolvedDiscount!=null);
        BigDecimal checkoutDiscount = automaticDiscount.add(applyCheckoutDiscount(sale, resolvedDiscount, discountLines, authentication));
        if (resolvedDiscount != null) sale.applyDiscountSnapshot(resolvedDiscount.definitionId(), resolvedDiscount.name(), resolvedDiscount.type(), resolvedDiscount.value(), resolvedDiscount.reason());
        recalculate(sale, authentication);
        Sale saved = save(sale);
        if (request.discount() != null) {
            BigDecimal subtotal = saved.getSubtotalAmount();
            saleAdjustmentRepository.save(new SaleAdjustment(saved, null, resolvedDiscount.type(), subtotal,
                    subtotal.subtract(checkoutDiscount),
                    resolvedDiscount.type() == SaleAdjustmentType.DISCOUNT_PERCENTAGE ? resolvedDiscount.value() : null,
                    resolvedDiscount.definitionId() == null ? "CUSTOM_ORDER_DISCOUNT" : "SAVED_ORDER_DISCOUNT",
                    resolvedDiscount.name() == null ? resolvedDiscount.reason() : resolvedDiscount.name(), actor, actor,
                    Instant.now(clock), MDC.get("correlationId")));
            audit(actor, AuditAction.SALE_DISCOUNT_APPLIED, SaleResponse.from(saved), "POS_ORDER_DISCOUNT");
        }
        SaleResponse response = SaleResponse.from(saved);
        audit(actor, AuditAction.SALE_DRAFT_CREATED, response, "checkout cart items=" + request.items().size());
        return response;
    }

    private ResolvedDiscount resolveCheckoutDiscount(Sale sale, SaleCheckoutDiscountRequest request) {
        if (request == null) return null;
        if (request.discountDefinitionId() != null) {
            var definition = discountDefinitionService.requireActive(request.discountDefinitionId(), sale.getStore().getTenantId());
            return new ResolvedDiscount(definition.getId(), definition.getName(), definition.getType(), definition.getValue(), definition.getDescription(),definition);
        }
        return new ResolvedDiscount(null, "Custom Discount", request.type(), request.value(), cleanOptional(request.reason()),null);
    }

    private BigDecimal applyCheckoutDiscount(Sale sale, ResolvedDiscount request,List<DiscountEngine.Line> lines, Authentication authentication) {
        if (request == null) return moneyZero();
        boolean permitted = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> PermissionCode.POS_SALE_DISCOUNT.name().equals(authority.getAuthority()));
        if (!permitted) throw new ForbiddenOperationException("POS_SALE_DISCOUNT is required");
        var currentDiscounts=sale.getItems().stream().collect(java.util.stream.Collectors.toMap(SaleItem::getId,SaleItem::getDiscountAmount));
        var remainingLines=lines.stream().map(line->new DiscountEngine.Line(line.lineId(),line.productId(),line.sourceItemId(),line.categoryId(),line.quantity(),
                money(line.subtotal().subtract(currentDiscounts.getOrDefault(line.lineId(),moneyZero()))),line.discountAllowed())).toList();
        var result=discountEngine.evaluate(request.type(),request.value(),request.definition(),sale.getStore().getId(),remainingLines,Instant.now(clock));
        var amounts=result.allocations().stream().collect(java.util.stream.Collectors.toMap(DiscountEngine.Allocation::lineId,DiscountEngine.Allocation::amount));
        sale.getItems().forEach(item->{var amount=amounts.get(item.getId());if(amount!=null)item.addDiscount(amount);});
        return result.amount();
    }

    private record ResolvedDiscount(UUID definitionId, String name, SaleAdjustmentType type, BigDecimal value, String reason, DiscountDefinition definition) {}

    private BigDecimal applyAutomaticMultiBuy(Sale sale, RegisterSession session,
                                               List<DiscountEngine.PromotionLine> lines, User actor, boolean stackingRequired) {
        PromotionDomain domain = session.getRegister().getType() == RegisterType.FOOD_SERVICE
                ? PromotionDomain.FOOD_SERVICE : PromotionDomain.RETAIL;
        Instant now = Instant.now(clock);
        DiscountDefinition selected = null;
        DiscountEngine.Result selectedResult = null;
        for (DiscountDefinition definition : discountDefinitionService.automaticMultiBuyForStore(
                sale.getStore().getTenantId(), sale.getStore().getId(), domain, now)) {
            if(stackingRequired&&!definition.isStackable())continue;
            DiscountEngine.Result result = discountEngine.evaluateMultiBuy(definition, sale.getStore().getId(), domain, lines, now);
            if (result.amount().signum() > 0 && (selectedResult == null || result.amount().compareTo(selectedResult.amount()) > 0)) {
                selected = definition;
                selectedResult = result;
            }
        }
        if (selected == null) return moneyZero();
        var amounts = selectedResult.allocations().stream().collect(java.util.stream.Collectors.toMap(
                DiscountEngine.Allocation::lineId, DiscountEngine.Allocation::amount));
        DiscountDefinition promotion = selected;
        sale.getItems().forEach(item -> {
            BigDecimal amount = amounts.get(item.getId());
            if (amount != null) item.applyPromotion(promotion.getId(), promotion.getName(), promotion.getBuyQuantity(),
                    promotion.getBundlePrice(), amount);
        });
        sale.applyDiscountSnapshot(promotion.getId(), promotion.getName(), promotion.getType(), promotion.getBundlePrice(),
                promotion.getBuyQuantity() + " for " + promotion.getBundlePrice());
        saleAdjustmentRepository.save(new SaleAdjustment(sale, null, SaleAdjustmentType.MULTI_BUY_FIXED_PRICE,
                sale.getItems().stream().map(item -> money(item.getUnitPrice().multiply(item.getQuantity()))).reduce(moneyZero(),BigDecimal::add),
                sale.getItems().stream().map(item -> money(item.getUnitPrice().multiply(item.getQuantity()).subtract(item.getDiscountAmount()))).reduce(moneyZero(),BigDecimal::add),
                null,"AUTOMATIC_MULTI_BUY",promotion.getName(),actor,actor,now,MDC.get("correlationId")));
        return selectedResult.amount();
    }

    private ResolvedCheckoutItem resolveCheckoutItem(RegisterSession session, Sale sale, SaleCheckoutItemRequest line) {
        if (line.foodMenuItemId() != null) {
            if (session.getRegister().getType() != RegisterType.FOOD_SERVICE) {
                throw new BadRequestException("FOOD_SERVICE_REGISTER_REQUIRED");
            }
            registerCapabilityService.requireEnabled(session.getStore(), RegisterType.FOOD_SERVICE);
            if (line.variantId() != null) {
                throw new BadRequestException("INVALID_CHECKOUT_ITEM: variants are not valid for food menu items");
            }
            FoodMenuItem menuItem = foodMenuItemRepository
                    .findByIdAndStoreId(line.foodMenuItemId(), session.getStore().getId())
                    .orElseThrow(() -> new NotFoundException("INVALID_MENU_ITEM"));
            if (!menuItem.isAvailable()) {
                throw new ConflictException("MENU_ITEM_NOT_AVAILABLE");
            }
            Product product = menuItem.getProduct();
            if (line.productId() != null && !line.productId().equals(product.getId())) {
                throw new BadRequestException("INVALID_CHECKOUT_ITEM: product does not match food menu item");
            }
            var foodVariant=line.foodMenuItemVariantId()==null?null:menuItem.getVariants().stream().filter(v->v.getId().equals(line.foodMenuItemVariantId())&&v.isAvailable()).findFirst().orElseThrow(()->new NotFoundException("MENU_ITEM_VARIANT_NOT_AVAILABLE"));
            if(!menuItem.getVariants().isEmpty()&&foodVariant==null)throw new BadRequestException("MENU_ITEM_VARIANT_REQUIRED");
            var optionIds=line.foodMenuModifierOptionIds()==null?java.util.Set.<UUID>of():new java.util.HashSet<>(line.foodMenuModifierOptionIds());
            var selectedOptions=menuItem.getModifierGroups().stream().flatMap(g->g.getOptions().stream()).filter(o->optionIds.contains(o.getId())&&o.isAvailable()).toList();
            if(selectedOptions.size()!=optionIds.size())throw new BadRequestException("INVALID_MENU_MODIFIER");
            for(var group:menuItem.getModifierGroups()){long count=selectedOptions.stream().filter(o->o.getGroup().getId().equals(group.getId())).count();if(count<group.getMinimumSelections()||count>group.getMaximumSelections())throw new BadRequestException("INVALID_MODIFIER_SELECTION");}
            var componentRequests=line.foodMenuComponentSelections()==null?java.util.List.<com.merchtyl.foodmenu.FoodMenuDtos.ComponentSelectionRequest>of():line.foodMenuComponentSelections();
            if(componentRequests.stream().map(com.merchtyl.foodmenu.FoodMenuDtos.ComponentSelectionRequest::componentId).distinct().count()!=componentRequests.size())throw new BadRequestException("DUPLICATE_MENU_COMPONENT");
            var activeComponents=menuItem.getComponents().stream().filter(com.merchtyl.foodmenu.FoodMenuItemComponent::isActive).collect(java.util.stream.Collectors.toMap(com.merchtyl.platform.persistence.BaseUuidEntity::getId,java.util.function.Function.identity()));
            var componentSnapshots=new java.util.ArrayList<FoodComponentSnapshot>();
            for(var selection:componentRequests){var component=activeComponents.get(selection.componentId());if(component==null)throw new BadRequestException("INVALID_MENU_COMPONENT");if(selection.state()==com.merchtyl.foodmenu.FoodComponentSelectionState.REMOVED&&(!component.isIncludedByDefault()||!component.isRemovable()))throw new BadRequestException("MENU_COMPONENT_NOT_REMOVABLE");if(selection.state()==com.merchtyl.foodmenu.FoodComponentSelectionState.EXTRA&&!component.isAllowExtra())throw new BadRequestException("MENU_COMPONENT_EXTRA_NOT_ALLOWED");componentSnapshots.add(new FoodComponentSnapshot(component.getId(),selection.state(),component.getName(),selection.state()==com.merchtyl.foodmenu.FoodComponentSelectionState.EXTRA?component.getExtraPrice():BigDecimal.ZERO));}
            BigDecimal foodPrice=foodVariant==null?menuItem.getPrice():foodVariant.getPrice();
            foodPrice=selectedOptions.stream().map(com.merchtyl.foodmenu.FoodMenuModifierOption::getPriceAdjustment).reduce(foodPrice,BigDecimal::add);
            foodPrice=componentSnapshots.stream().map(FoodComponentSnapshot::priceAdjustment).reduce(foodPrice,BigDecimal::add);
            return new ResolvedCheckoutItem(product, null, foodPrice,menuItem.getId(),
                    menuItem.getCategory()==null?null:menuItem.getCategory().getId(),
                    product.getCategory()==null?null:product.getCategory().getId(),menuItem.getId(),
                    menuItem.getCategory()==null?null:menuItem.getCategory().getId(),menuItem.getDisplayName(),foodVariant==null?null:foodVariant.getId(),foodVariant==null?null:foodVariant.getName(),selectedOptions.stream().map(o->"+ "+o.getName()).toList(),componentSnapshots);
        }

        if (line.productId() == null) {
            throw new BadRequestException("INVALID_CHECKOUT_ITEM");
        }
        ResolvedStoreProduct storeProduct = storeProduct(sale, line.productId());
        ProductVariant variant = line.variantId() == null ? null : productVariantRepository.findById(line.variantId())
                .filter(candidate -> candidate.getProduct().getId().equals(storeProduct.product().getId()) && candidate.isActive())
                .orElseThrow(() -> new NotFoundException("PRODUCT_VARIANT_NOT_AVAILABLE"));
        return new ResolvedCheckoutItem(storeProduct.product(), variant,
                variant == null ? storeProduct.sellingPrice() : variant.getPrice(),null,
                storeProduct.product().getCategory()==null?null:storeProduct.product().getCategory().getId(),
                storeProduct.product().getCategory()==null?null:storeProduct.product().getCategory().getId(),null,null,null,null,null,java.util.List.of(),java.util.List.of());
    }

    private record ResolvedCheckoutItem(Product product, ProductVariant variant, BigDecimal unitPrice,UUID sourceItemId,
                                        UUID categoryId, UUID productCategoryId, UUID menuItemId, UUID menuCategoryId, String menuItemName,UUID menuItemVariantId,String menuVariantName,java.util.List<String> modifierNames,java.util.List<FoodComponentSnapshot> componentSnapshots) {}

    @Transactional(readOnly = true)
    public SaleResponse get(UUID id) {
        return SaleResponse.from(findSale(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<SaleResponse> search(SaleSearchRequest request) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, request.size()));
        var page = saleRepository.findAll(
                specification(request),
                PageRequest.of(pageNumber, pageSize,
                        Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        return new PageResponse<>(
                page.getContent().stream().map(SaleResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    @Transactional
    public SaleResponse addItem(UUID saleId, SaleAddItemRequest request, Authentication authentication) {
        return addItem(saleId, request, authentication, null, null);
    }

    @Transactional
    public SaleResponse addFoodMenuItem(UUID saleId, UUID storeId, UUID productId, BigDecimal quantity,
                                        BigDecimal menuPrice, Authentication authentication) {
        return addItem(saleId, new SaleAddItemRequest(productId, quantity, null, null, false, false,
                null, null, null, null), authentication, storeId, menuPrice);
    }

    private SaleResponse addItem(UUID saleId, SaleAddItemRequest request, Authentication authentication,
                                 UUID requiredStoreId, BigDecimal trustedUnitPrice) {
        User actor = actor(authentication);
        Sale sale = findSale(saleId);
        if (requiredStoreId != null && !sale.getStore().getId().equals(requiredStoreId)) {
            throw new ForbiddenOperationException("Food menu does not belong to the sale store");
        }
        validateUserCanUseSession(actor, sale.getRegisterSession(), authentication);
        requireDraft(sale);
        requireNoPayments(sale);
        ResolvedStoreProduct storeProduct = storeProduct(sale, request.productId());
        Product product = storeProduct.product();
        ProductVariant variant = request.variantId() == null ? null : productVariantRepository.findById(request.variantId())
                .filter(candidate -> candidate.getProduct().getId().equals(product.getId()) && candidate.isActive())
                .orElseThrow(() -> new NotFoundException("PRODUCT_VARIANT_NOT_AVAILABLE"));
        SaleItem existing = sale.getItems().stream()
                .filter(candidate -> candidate.getProduct().getId().equals(product.getId()))
                .filter(candidate -> java.util.Objects.equals(
                        candidate.getVariant() == null ? null : candidate.getVariant().getId(), request.variantId()))
                .findFirst().orElse(null);
        if (existing != null) {
            existing.updateQuantity(existing.getQuantity().add(normalizeQuantity(request.quantity())));
            saleItemHandlerRegistry.validate(existing.validationRequest());
            recalculate(sale, authentication);
            SaleResponse response = SaleResponse.from(save(sale));
            audit(actor, AuditAction.SALE_ITEM_UPDATED, response, existing.getProductName());
            return response;
        }
        SaleItem item = new SaleItem(
                sale,
                product,
                variant,
                normalizeQuantity(request.quantity()),
                normalizeMoney(trustedUnitPrice != null ? trustedUnitPrice : (variant == null ? storeProduct.sellingPrice() : variant.getPrice()), "unitPrice"),
                moneyZero(),
                false,
                Boolean.TRUE.equals(request.ageVerified()),
                cleanOptional(request.serialNumber()),
                cleanOptional(request.externalReference()),
                request.customerId() == null ? sale.getCustomerId() : request.customerId(),
                cleanOptional(request.paymentMethodCode()));
        saleItemHandlerRegistry.validate(item.validationRequest());
        sale.addItem(item);
        recalculate(sale, authentication);
        SaleResponse response = SaleResponse.from(save(sale));
        audit(actor, AuditAction.SALE_ITEM_ADDED, response, item.getProductName());
        if (product.hasCapability(com.merchtyl.product.ProductCapability.REQUIRE_AGE_VERIFICATION)) {
            audit(actor, AuditAction.AGE_VERIFICATION_CONFIRMED, response,
                    "productId=" + product.getId() + ",variantId=" + (variant == null ? "" : variant.getId())
                            + ",minimumAge=" + product.getMinimumAge());
        }
        return response;
    }

    @Transactional
    public SaleResponse updateQuantity(UUID saleId, UUID itemId, SaleUpdateQuantityRequest request, Authentication authentication) {
        User actor = actor(authentication);
        Sale sale = findSale(saleId);
        validateUserCanUseSession(actor, sale.getRegisterSession(), authentication);
        requireDraft(sale);
        requireNoPayments(sale);
        SaleItem item = findItem(sale, itemId);
        item.updateQuantity(normalizeQuantity(request.quantity()));
        saleItemHandlerRegistry.validate(item.validationRequest());
        recalculate(sale, authentication);
        SaleResponse response = SaleResponse.from(save(sale));
        audit(actor, AuditAction.SALE_ITEM_UPDATED, response, item.getProductName());
        return response;
    }

    @Transactional
    public SaleResponse removeItem(UUID saleId, UUID itemId, Authentication authentication) {
        User actor = actor(authentication);
        Sale sale = findSale(saleId);
        validateUserCanUseSession(actor, sale.getRegisterSession(), authentication);
        requireDraft(sale);
        requireNoPayments(sale);
        SaleItem item = findItem(sale, itemId);
        String productName = item.getProductName();
        sale.removeItem(item);
        recalculate(sale, authentication);
        SaleResponse response = SaleResponse.from(save(sale));
        audit(actor, AuditAction.SALE_ITEM_REMOVED, response, productName);
        return response;
    }

    @Transactional
    public SaleResponse overridePrice(UUID saleId, UUID itemId, PriceOverrideRequest request, Authentication authentication) {
        User actor = actor(authentication);
        Sale sale = findSale(saleId);
        validateUserCanUseSession(actor, sale.getRegisterSession(), authentication);
        requireDraft(sale);
        requireNoPayments(sale);
        SaleItem item = findItem(sale, itemId);
        BigDecimal original = item.getUnitPrice();
        BigDecimal adjusted = normalizeMoney(request.unitPrice(), "unitPrice");
        item.overrideUnitPrice(adjusted);
        saleItemHandlerRegistry.validate(item.validationRequest());
        saleAdjustmentRepository.save(new SaleAdjustment(sale, item, request.type(), original, adjusted, null,
                cleanRequired(request.reasonCode(), "reasonCode"), cleanOptional(request.reason()), actor,
                actor, Instant.now(clock), MDC.get("correlationId")));
        recalculate(sale, authentication);
        SaleResponse response = SaleResponse.from(save(sale));
        audit(actor, AuditAction.PRICE_OVERRIDE_APPROVED, response, request.reasonCode());
        return response;
    }

    @Transactional
    public SaleResponse applyLineDiscount(UUID saleId, UUID itemId, LineDiscountRequest request, Authentication authentication) {
        User actor = actor(authentication);
        Sale sale = findSale(saleId);
        validateUserCanUseSession(actor, sale.getRegisterSession(), authentication);
        requireDraft(sale);
        requireNoPayments(sale);
        SaleItem item = findItem(sale, itemId);
        BigDecimal lineBase = item.getUnitPrice().multiply(item.getQuantity());
        BigDecimal discount = request.type() == SaleAdjustmentType.DISCOUNT_PERCENTAGE
                ? lineBase.multiply(request.value()).divide(new BigDecimal("100"), MONEY_SCALE, RoundingMode.HALF_UP)
                : normalizeMoney(request.value(), "value");
        if (discount.compareTo(lineBase) > 0) {
            throw new BadRequestException("Discount cannot exceed the line subtotal");
        }
        item.applyDiscount(discount);
        saleItemHandlerRegistry.validate(item.validationRequest());
        saleAdjustmentRepository.save(new SaleAdjustment(sale, item, request.type(), BigDecimal.ZERO, discount,
                request.type() == SaleAdjustmentType.DISCOUNT_PERCENTAGE ? request.value() : null,
                cleanRequired(request.reasonCode(), "reasonCode"), cleanOptional(request.reason()), actor,
                actor, Instant.now(clock), MDC.get("correlationId")));
        recalculate(sale, authentication);
        SaleResponse response = SaleResponse.from(save(sale));
        audit(actor, AuditAction.LINE_DISCOUNT_APPLIED, response, request.reasonCode());
        return response;
    }

    @Transactional
    public SaleResponse hold(UUID saleId, Authentication authentication) {
        User actor = actor(authentication);
        Sale sale = findSale(saleId);
        validateUserCanUseSession(actor, sale.getRegisterSession(), authentication);
        requireDraft(sale);
        requireNoPayments(sale);
        sale.hold(Instant.now(clock));
        SaleResponse response = SaleResponse.from(save(sale));
        audit(actor, AuditAction.SALE_HELD, response, null);
        return response;
    }

    @Transactional
    public SaleResponse resume(UUID saleId, Authentication authentication) {
        User actor = actor(authentication);
        Sale sale = findSale(saleId);
        validateUserCanUseSession(actor, sale.getRegisterSession(), authentication);
        if (sale.getStatus() != SaleStatus.HELD) {
            throw new ConflictException("Only held sales can be resumed");
        }
        sale.resume();
        recalculate(sale, authentication);
        SaleResponse response = SaleResponse.from(save(sale));
        audit(actor, AuditAction.SALE_RESUMED, response, null);
        return response;
    }

    @Transactional
    public SaleResponse cancel(UUID saleId, Authentication authentication) {
        User actor = actor(authentication);
        Sale sale = findSale(saleId);
        validateUserCanUseSession(actor, sale.getRegisterSession(), authentication);
        requireDraft(sale);
        requireNoPayments(sale);
        sale.cancel(Instant.now(clock));
        SaleResponse response = SaleResponse.from(save(sale));
        audit(actor, AuditAction.SALE_CANCELLED, response, null);
        return response;
    }

    @Transactional
    public SaleResponse forceCloseDraft(UUID saleId, SaleForceCloseRequest request, Authentication authentication) {
        User actor = actor(authentication);
        Sale sale = findSaleForUpdate(saleId);
        storeAccessService.requireStoreManagement(authentication, sale.getStore().getId());
        if (sale.getVersion() != request.version()) throw new ConflictException("SALE_STATE_CHANGED");
        if (sale.getStatus() != SaleStatus.DRAFT && sale.getStatus() != SaleStatus.HELD) {
            throw new ConflictException("SALE_NOT_DRAFT");
        }
        String reasonCode = cleanOptional(request.reasonCode());
        String note = cleanOptional(request.note());
        if (reasonCode == null || ("OTHER".equals(reasonCode) && note == null)) {
            throw new BadRequestException("SALE_FORCE_CLOSE_REASON_REQUIRED");
        }
        SaleResponse before = SaleResponse.from(sale);
        sale.forceClose(actor, Instant.now(clock), reasonCode, note);
        SaleResponse response = SaleResponse.from(save(sale));
        auditService.record(new CreateAuditRecordCommand(actor.getId(), AuditAction.SALE_DRAFT_FORCE_CLOSED,
                "SALE", sale.getId(), sale.getStore().getId(), sale.getRegister().getId(), before, response,
                reasonCode + (note == null ? "" : ": " + note)));
        return response;
    }

    @Transactional
    public SaleResponse recalculate(UUID saleId, Authentication authentication) {
        User actor = actor(authentication);
        Sale sale = findSale(saleId);
        validateUserCanUseSession(actor, sale.getRegisterSession(), authentication);
        if (sale.getStatus() != SaleStatus.DRAFT && sale.getStatus() != SaleStatus.HELD) {
            throw new ConflictException("Only draft or held sales can be recalculated");
        }
        recalculate(sale, authentication);
        SaleResponse response = SaleResponse.from(save(sale));
        audit(actor, AuditAction.SALE_RECALCULATED, response, null);
        return response;
    }

    public IdempotencyResult completeIdempotently(UUID saleId, String idempotencyKey, Authentication authentication) {
        User actor = actor(authentication);
        String requestBody = "{\"saleId\":\"" + saleId + "\"}";
        return idempotencyService.execute(actor.getId(), COMPLETE_ENDPOINT, idempotencyKey, requestBody, () -> {
            SaleResponse response = transactions.execute(status -> complete(saleId, actor, authentication));
            return new IdempotencyOperationResponse(
                    200,
                    MediaType.APPLICATION_JSON_VALUE,
                    responseBody(response));
        });
    }

    @Transactional
    SaleResponse complete(UUID saleId, User actor, Authentication authentication) {
        Sale sale = findSaleForUpdate(saleId);
        validateUserCanUseSession(actor, sale.getRegisterSession(), authentication);
        requireDraft(sale);
        requireOpenRegisterSession(sale);
        if (sale.getItems().isEmpty()) {
            throw new ConflictException("Sale must have at least one item before completion");
        }

        sale.getItems().stream().filter(item -> !item.isCustomItem())
                .forEach(item -> saleItemHandlerRegistry.validate(item.validationRequest()));
        requireSufficientPayments(sale);

        Instant completedAt = Instant.now(clock);
        List<SaleItem> items = sale.getItems();
        for (SaleItem item : items) {
            item.snapshotForCompletion();
            deductInventory(sale, item, completedAt, authentication);
        }
        appendCashLedgerEntries(sale, actor, completedAt);
        if (foodOrderTokenService != null
                && sale.getRegister().getType() == RegisterType.FOOD_SERVICE
                && sale.getFoodOrderToken() == null) {
            sale.assignFoodOrderToken(foodOrderTokenService.nextToken(sale.getRegisterSession()));
        }
        sale.complete(actor, completedAt);
        SaleResponse response = SaleResponse.from(save(sale));
        audit(actor, AuditAction.SALE_COMPLETED, response, null);
        return response;
    }

    @Transactional
    public SaleResponse recordPayment(UUID saleId, SalePaymentRequest request, Authentication authentication) {
        User actor = actor(authentication);
        Sale sale = findSaleForUpdate(saleId);
        validateUserCanUseSession(actor, sale.getRegisterSession(), authentication);
        requireDraft(sale);
        if (sale.getItems().isEmpty() || sale.getTotalAmount().signum() <= 0) {
            throw new ConflictException("Sale must have a payable total before recording payment");
        }

        if (request.amount() == null || request.amount().signum() <= 0) {
            throw new BadRequestException("PAYMENT_AMOUNT_INVALID");
        }
        BigDecimal amount = normalizeMoney(request.amount(), "amount");
        BigDecimal balanceDue = balanceDue(sale);
        if (balanceDue.signum() <= 0) {
            throw new ConflictException("SALE_ALREADY_FULLY_PAID");
        }
        if (amount.compareTo(balanceDue) > 0) {
            throw new BadRequestException("PAYMENT_AMOUNT_EXCEEDS_REMAINING");
        }

        PaymentMethod method = request.method();
        if (method == null) {
            throw new BadRequestException("method is required");
        }
        BigDecimal cashTendered = null;
        BigDecimal cashRoundingAdjustment = moneyZero();
        BigDecimal cashSettlementAmount = null;
        BigDecimal changeDue = moneyZero();
        if (method == PaymentMethod.CASH) {
            if (request.cashTendered() == null) {
                throw new BadRequestException("cashTendered is required for cash payments");
            }
            cashTendered = normalizeMoney(request.cashTendered(), "cashTendered");
            boolean finalSettlement = amount.compareTo(balanceDue) == 0;
            CashRoundingResult rounding = finalSettlement
                    ? cashRoundingService.round(amount, sale.getCurrencyCode())
                    : cashRoundingService.round(amount, "");
            cashRoundingAdjustment = rounding.adjustment();
            cashSettlementAmount = rounding.roundedAmount();
            if (cashTendered.compareTo(cashSettlementAmount) < 0) {
                throw new BadRequestException("cashTendered must be greater than or equal to amount");
            }
            changeDue = money(cashTendered.subtract(cashSettlementAmount));
        } else if (request.cashTendered() != null) {
            throw new BadRequestException("cashTendered is only allowed for cash payments");
        }

        String reference = cleanOptional(request.reference());

        Payment payment = new Payment(
                sale,
                method,
                amount,
                sale.getCurrencyCode(),
                cashTendered,
                changeDue,
                cashRoundingAdjustment,
                cashSettlementAmount,
                reference,
                cleanOptional(request.notes()),
                actor,
                Instant.now(clock));
        sale.addPayment(payment);
        SaleResponse response = SaleResponse.from(save(sale));
        audit(actor, AuditAction.SALE_PAYMENT_RECORDED, response, method.name());
        return response;
    }

    private void recalculate(Sale sale, Authentication authentication) {
        BigDecimal subtotal = moneyZero();
        BigDecimal discount = moneyZero();
        BigDecimal tax = moneyZero();
        BigDecimal total = moneyZero();
        for (SaleItem item : sale.getItems()) {
            if (!item.isCustomItem()) saleItemHandlerRegistry.validate(item.validationRequest());
            TaxCalculationResponse taxResponse = taxEngine.calculate(new TaxCalculationRequest(
                    sale.getStore().getId(),
                    null,
                    null,
                    item.isCustomItem() ? null : item.getProduct().getId(),
                    item.isCustomItem() ? item.getTaxCategorySnapshotId() : item.getProduct().getTaxCategoryId(),
                    false,
                    sale.getBusinessDate(),
                    sale.getSaleChannel(),
                    item.getUnitPrice(),
                    item.getQuantity(),
                    item.getDiscountAmount(),
                    sale.isPricesIncludeTax(),
                    sale.getCurrencyCode()), authentication);
            item.applyVariantDeposit();
            BigDecimal lineSubtotal = money(taxResponse.netAmount().add(item.getDiscountAmount()));
            BigDecimal depositTotal = item.getDepositTotal();
            item.setCalculatedAmounts(lineSubtotal.add(depositTotal), taxResponse.taxAmount(), taxResponse.grossAmount().add(depositTotal));
            subtotal = subtotal.add(lineSubtotal).add(depositTotal);
            discount = discount.add(item.getDiscountAmount());
            tax = tax.add(taxResponse.taxAmount());
            total = total.add(taxResponse.grossAmount()).add(depositTotal);
        }
        sale.setTotals(money(subtotal), money(discount), money(tax), money(total));
    }

    private Sale findSale(UUID id) {
        if (id == null) {
            throw new BadRequestException("sale id is required");
        }
        return saleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Sale not found"));
    }

    private Sale findSaleForUpdate(UUID id) {
        if (id == null) {
            throw new BadRequestException("sale id is required");
        }
        return saleRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Sale not found"));
    }

    private RegisterSession findOpenSession(UUID registerSessionId) {
        if (registerSessionId == null) {
            throw new BadRequestException("registerSessionId is required");
        }
        RegisterSession session = registerSessionRepository.findById(registerSessionId)
                .orElseThrow(() -> new NotFoundException("Register session not found"));
        if (session.getStatus() != RegisterSessionStatus.OPEN) {
            throw new ConflictException("Register session is not open");
        }
        if (session.isTillSecured()) {
            throw new ConflictException("TILL_SECURED");
        }
        if (session.getBusinessDay() != null && !session.isBusinessDayOperational()) {
            throw new ConflictException("BUSINESS_DAY_NOT_OPEN");
        }
        return session;
    }

    private ResolvedStoreProduct storeProduct(Sale sale, UUID productId) {
        if (productId == null) {
            throw new BadRequestException("productId is required");
        }
        if (storeProductRepository == null) {
            Product product = productRepository.findById(productId).orElseThrow(() -> new NotFoundException("Product not found"));
            return new ResolvedStoreProduct(product, product.getPrice());
        }
        Product product = productRepository.findByIdAndTenantId(productId, sale.getStore().getTenantId())
                .filter(Product::isActive)
                .orElseThrow(() -> new BadRequestException("PRODUCT_NOT_AVAILABLE_AT_STORE"));
        if (product.getAvailabilityScope() == ProductAvailabilityScope.ALL_STORES) {
            return new ResolvedStoreProduct(product, product.getPrice());
        }
        StoreProduct mapping = storeProductRepository.findByTenantIdAndStore_IdAndProduct_IdAndActiveTrueAndSellableTrue(
                        sale.getStore().getTenantId(), sale.getStore().getId(), productId)
                .orElseThrow(() -> new BadRequestException("PRODUCT_NOT_AVAILABLE_AT_STORE"));
        return new ResolvedStoreProduct(mapping.getProduct(), mapping.getSellingPrice());
    }

    private record ResolvedStoreProduct(Product product, BigDecimal sellingPrice) {}

    private static SaleItem findItem(Sale sale, UUID itemId) {
        if (itemId == null) {
            throw new BadRequestException("item id is required");
        }
        return sale.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Sale item not found"));
    }

    private User actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ForbiddenOperationException("Authenticated user is required");
        }
        User user = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ForbiddenOperationException("Authenticated user is required"));
        if (!user.isEnabled() || user.isLocked()) {
            throw new ForbiddenOperationException("User is not active");
        }
        return user;
    }

    private static void validateUserCanUseSession(User actor, RegisterSession session, Authentication authentication) {
        if (hasAuthority(authentication, "ROLE_OWNER") || hasAuthority(authentication, "ROLE_TENANT_OWNER")
                || hasAuthority(authentication, "ROLE_MANAGER") || hasAuthority(authentication, "ROLE_STORE_MANAGER")) {
            return;
        }
        if (!session.getAssignedCashier().getId().equals(actor.getId())) {
            throw new ForbiddenOperationException("Sale user must be assigned to this register session");
        }
    }

    private static void requireDraft(Sale sale) {
        if (sale.getStatus() != SaleStatus.DRAFT) {
            throw new ConflictException("Sale must be in draft status");
        }
    }

    private static void requireOpenRegisterSession(Sale sale) {
        if (sale.getRegisterSession().getStatus() != RegisterSessionStatus.OPEN) {
            throw new ConflictException("Register session is not open");
        }
        if (sale.getRegisterSession().isTillSecured()) {
            throw new ConflictException("TILL_SECURED");
        }
    }

    private static void requireSufficientPayments(Sale sale) {
        if (paidAmount(sale).compareTo(sale.getTotalAmount()) < 0) {
            throw new ConflictException("Sale has insufficient payments");
        }
    }

    private static void requireNoPayments(Sale sale) {
        if (!sale.getPayments().isEmpty()) {
            throw new ConflictException("SALE_HAS_RECORDED_PAYMENTS");
        }
    }

    private static BigDecimal paidAmount(Sale sale) {
        return money(sale.getPayments().stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal balanceDue(Sale sale) {
        return money(sale.getTotalAmount().subtract(paidAmount(sale)).max(BigDecimal.ZERO));
    }

    private void deductInventory(Sale sale, SaleItem item, Instant completedAt, Authentication authentication) {
        if (item.isCustomItem()) return;
        Product product = item.getProduct();
        if (!product.isInventoryTrackingEnabled()) {
            return;
        }
        inventoryService.recordStockChange(new InventoryStockChangeRequest(
                sale.getStore().getId(),
                product.getId(),
                InventoryTransactionType.SALE,
                item.getQuantity().negate(),
                SALE_REFERENCE_TYPE,
                sale.getId(),
                item.getProductName(),
                completedAt,
                null,item.getVariant()==null?null:item.getVariant().getId()), authentication);
    }

    private void appendCashLedgerEntries(Sale sale, User actor, Instant completedAt) {
        for (Payment payment : sale.getPayments()) {
            if (payment.getMethod() != PaymentMethod.CASH) {
                continue;
            }
            cashLedgerService.append(new CashLedgerEntryCommand(
                    sale.getStore(),
                    sale.getRegister(),
                    sale.getRegisterSession(),
                    CashLedgerSourceType.SALE_CASH_RECEIPT,
                    payment.getId(),
                    CashLedgerDirection.IN,
                    payment.getCashTendered(),
                    sale.getCurrencyCode(),
                    sale.getBusinessDate(),
                    completedAt,
                    actor,
                    operationId(sale.getId(), "cash-receipt", payment.getId()),
                    "Sale cash tender"));
            if (payment.getChangeDue().signum() > 0) {
                cashLedgerService.append(new CashLedgerEntryCommand(
                        sale.getStore(), sale.getRegister(), sale.getRegisterSession(),
                        CashLedgerSourceType.SALE_CHANGE_GIVEN, payment.getId(), CashLedgerDirection.OUT,
                        payment.getChangeDue(), sale.getCurrencyCode(), sale.getBusinessDate(), completedAt, actor,
                        operationId(sale.getId(), "cash-change", payment.getId()), "Sale change given"));
            }
        }
    }

    private static UUID operationId(UUID saleId, String operation, UUID paymentId) {
        return UUID.nameUUIDFromBytes((saleId + ":" + operation + ":" + paymentId).getBytes(StandardCharsets.UTF_8));
    }

    private Sale save(Sale sale) {
        try {
            return saleRepository.saveAndFlush(sale);
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
            throw new ConflictException("Sale was modified by another transaction");
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Sale could not be saved");
        }
    }

    private void audit(User actor, AuditAction action, SaleResponse response, String notes) {
        auditService.record(new CreateAuditRecordCommand(
                actor.getId(),
                action,
                "SALE",
                response.id(),
                response.storeId(),
                response.registerId(),
                null,
                response,
                notes));
    }

    private static BigDecimal normalizeQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new BadRequestException("quantity must be greater than zero");
        }
        try {
            return quantity.setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BadRequestException("quantity may include no more than 4 decimal places");
        }
    }

    private static BigDecimal normalizeMoney(BigDecimal value, String fieldName) {
        if (value == null || value.signum() < 0) {
            throw new BadRequestException(fieldName + " must be zero or greater");
        }
        try {
            return value.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BadRequestException(fieldName + " may include no more than 2 decimal places");
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal moneyZero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE);
    }

    private static String cleanOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String cleanRequired(String value, String field) {
        String cleaned = cleanOptional(value);
        if (cleaned == null) {
            throw new BadRequestException(field + " is required");
        }
        return cleaned;
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals(authority));
    }

    private static void requireCustomItemPermission(Authentication authentication) {
        if (!hasAuthority(authentication, PermissionCode.POS_CUSTOM_ITEM.name())) {
            throw new ForbiddenOperationException("CUSTOM_ITEM_NOT_ALLOWED");
        }
    }

    private String responseBody(SaleResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize sale response", exception);
        }
    }

    private static Specification<Sale> specification(SaleSearchRequest request) {
        return Specification
                .where(equalReference("store", request.storeId()))
                .and(equalReference("register", request.registerId()))
                .and(equalReference("registerSession", request.registerSessionId()))
                .and(equalReference("createdBy", request.createdBy()))
                .and(equalEnum("status", request.status()));
    }

    private static Specification<Sale> equalReference(String field, UUID value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field).get("id"), value);
    }

    private static Specification<Sale> equalEnum(String field, Enum<?> value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }
}
