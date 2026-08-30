package com.merchtyl.store;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.common.PageResponse;
import com.merchtyl.platform.admin.PlatformAdministrationService;
import com.merchtyl.platform.billing.CommercialCapability;
import com.merchtyl.platform.billing.SubscriptionEntitlementService;
import com.merchtyl.reference.ReferenceDataService;
import com.merchtyl.reference.StoreGeographySelection;
import com.merchtyl.security.AuthorizationService;
import com.merchtyl.security.PermissionCode;
import com.merchtyl.security.RoleName;
import com.merchtyl.security.StoreAccessService;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class StoreService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ISO_COUNTRY_CODES = Set.of(Locale.getISOCountries());

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ReferenceDataService referenceDataService;
    private final AuthorizationService authorizationService;
    private final PlatformAdministrationService platformAdministrationService;
    private final StoreAccessService storeAccessService;
    private final JdbcTemplate jdbcTemplate;
    private final SubscriptionEntitlementService entitlements;

    public StoreService(
            StoreRepository storeRepository,
            UserRepository userRepository,
            AuditService auditService,
            ReferenceDataService referenceDataService,
            AuthorizationService authorizationService,
            PlatformAdministrationService platformAdministrationService,
            StoreAccessService storeAccessService,
            JdbcTemplate jdbcTemplate,
            SubscriptionEntitlementService entitlements) {
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.referenceDataService = referenceDataService;
        this.authorizationService = authorizationService;
        this.platformAdministrationService = platformAdministrationService;
        this.storeAccessService = storeAccessService;
        this.jdbcTemplate = jdbcTemplate;
        this.entitlements = entitlements;
    }

    @Transactional
    public StoreResponse create(StoreRequest request, Authentication authentication) {
        StoreValues values = values(request, authentication);
        if (storeRepository.existsByCodeIgnoreCase(values.code())) {
            throw duplicateCode();
        }

        Store store = new Store(
                values.code(),
                values.name(),
                values.legalName(),
                values.countryCode(),
                values.countryId(),
                values.administrativeAreaCode(),
                values.administrativeDivisionId(),
                values.address(),
                values.phone(),
                values.email(),
                values.currencyCode(),
                values.currencyId(),
                values.locale(),
                values.timezone(),
                values.timezoneId(),
                values.timezoneName(),
                values.taxRegionId(),
                values.taxRegionCode(),
                values.pricesIncludeTax(),
                values.negativeStockAllowed(),
                values.active());
        UUID tenantId = currentTenantId(authentication);
        if (request.capabilities() != null && request.capabilities().contains(StoreCapability.FOOD_SERVICE)) {
            entitlements.requireOrActivate(tenantId, CommercialCapability.FOOD_SERVICE);
        }
        store.assignTenant(tenantId);
        store.configureOperations(validCapabilities(request.capabilities()), kitchenDisplayName(request.name(), request.capabilities(), request.kitchenDisplayName()));
        StoreResponse response = response(save(store));
        if (platformAdministrationService != null) {
            platformAdministrationService.markFirstStoreCreated(tenantId);
        }
        audit(authentication, AuditAction.STORE_CREATED, response.id(), null, response, null);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<StoreResponse> search(StoreSearchRequest request, Authentication authentication) {
        User actor = currentTenantUser(authentication);
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, request.size()));
        var pageable = PageRequest.of(pageNumber, pageSize,
                Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id")));
        var page = storeRepository.findAll(specification(request, actor), pageable);
        return new PageResponse<>(
                page.getContent().stream().map(StoreResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    @Transactional(readOnly = true)
    public PageResponse<StoreResponse> search(StoreSearchRequest request) {
        return search(request, null);
    }

    @Transactional(readOnly = true)
    public StoreResponse get(UUID id, Authentication authentication) {
        storeAccessService.requireStoreAccess(authentication, id);
        return response(find(id));
    }

    @Transactional(readOnly = true)
    public StoreDefaultsResponse defaults(Authentication authentication) {
        UUID tenantId = currentTenantId(authentication);
        return jdbcTemplate.query("""
                select tenant.country_code,
                       tenant.administrative_division_code,
                       tenant.default_currency_code,
                       country.default_language_code,
                       tenant.primary_timezone,
                       tenant.default_tax_region_code
                from tenants tenant
                left join countries country on country.id = tenant.country_id
                where tenant.id = ?
                """, rs -> {
            if (!rs.next()) {
                throw new NotFoundException("Tenant defaults not found");
            }
            Set<StoreCapability> capabilities = Set.copyOf(jdbcTemplate.queryForList(
                    "select capability from tenant_store_operation_defaults where tenant_id = ?", String.class, tenantId)
                    .stream().map(StoreCapability::valueOf).toList());
            String kitchenName = jdbcTemplate.queryForList("select kitchen_display_name from tenant_store_operation_defaults where tenant_id = ? and capability = 'FOOD_SERVICE'", String.class, tenantId)
                    .stream().findFirst().orElse(null);
            return new StoreDefaultsResponse(
                    rs.getString("country_code"),
                    rs.getString("administrative_division_code"),
                    rs.getString("default_currency_code"),
                    rs.getString("default_language_code"),
                    rs.getString("primary_timezone"),
                    rs.getString("default_tax_region_code"), capabilities.isEmpty() ? Set.of(StoreCapability.RETAIL) : capabilities, kitchenName);
        }, tenantId);
    }

    @Transactional
    public StoreResponse update(UUID id, StoreUpdateRequest request, Authentication authentication) {
        storeAccessService.requireStoreManagement(authentication, id);
        Store store = find(id);
        requireCurrentVersion(store, request.version());
        StoreValues values = values(request, authentication);
        if (storeRepository.existsByCodeIgnoreCaseAndIdNot(values.code(), id)) {
            throw duplicateCode();
        }

        StoreResponse before = response(store);
        boolean previouslyFoodEnabled = store.getCapabilities().contains(StoreCapability.FOOD_SERVICE);
        UUID tenantId = currentTenantId(authentication);
        if (request.capabilities() != null && request.capabilities().contains(StoreCapability.FOOD_SERVICE) && !previouslyFoodEnabled) {
            entitlements.requireOrActivate(tenantId, CommercialCapability.FOOD_SERVICE);
        }
        store.update(values);
        store.configureOperations(validCapabilities(request.capabilities()), kitchenDisplayName(request.name(), request.capabilities(), request.kitchenDisplayName()));
        StoreResponse after = response(save(store));
        if (previouslyFoodEnabled && !after.capabilities().contains(StoreCapability.FOOD_SERVICE)) {
            entitlements.deactivateIfUnused(tenantId, CommercialCapability.FOOD_SERVICE);
        }
        audit(authentication, AuditAction.STORE_UPDATED, id, before, after, null);
        auditGeographyChanges(authentication, id, before, after);
        return after;
    }

    @Transactional
    public StoreResponse updateStatus(UUID id, StoreStatusRequest request, Authentication authentication) {
        storeAccessService.requireStoreManagement(authentication, id);
        Store store = find(id);
        requireCurrentVersion(store, request.version());

        StoreResponse before = StoreResponse.from(store);
        store.setActive(request.active());
        StoreResponse after = StoreResponse.from(save(store));
        audit(authentication, AuditAction.STORE_STATUS_CHANGED, id, before, after, null);
        return after;
    }

    private Store save(Store store) {
        try {
            return storeRepository.saveAndFlush(store);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateCode();
        }
    }

    private StoreResponse response(Store store) {
        Long kitchenUsers = jdbcTemplate.queryForObject("""
                select count(distinct user_id) from security_user_store_assignments
                where store_id = ? and tenant_id = ? and active = true and assignment_role = 'KITCHEN'
                """, Long.class, store.getId(), store.getTenantId());
        return StoreResponse.from(store).withKitchenUsersCount(kitchenUsers == null ? 0 : kitchenUsers);
    }

    private Set<StoreCapability> validCapabilities(Set<StoreCapability> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            throw new BadRequestException("At least one store capability is required");
        }
        return Set.copyOf(capabilities);
    }

    private String kitchenDisplayName(String storeName, Set<StoreCapability> capabilities, String requestedName) {
        if (capabilities == null || !capabilities.contains(StoreCapability.FOOD_SERVICE)) return null;
        String cleaned = cleanOptional(requestedName);
        return cleaned == null ? cleanRequired(storeName, "name") + " Kitchen" : cleaned;
    }

    private Store find(UUID id) {
        return storeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Store not found"));
    }

    private Specification<Store> specification(StoreSearchRequest request, User actor) {
        return Specification
                .where(equalUuid("tenantId", actor.getTenantId()))
                .and(accessibleTo(actor))
                .and(equalString("code", normalizeCodeFilter(request.code())))
                .and(containsString("name", request.name()))
                .and(equalString("countryCode", normalizeUpperFilter(request.countryCode())))
                .and(equalString("administrativeAreaCode", normalizeUpperFilter(request.administrativeAreaCode())))
                .and(equalString("currencyCode", normalizeUpperFilter(request.currencyCode())))
                .and(equalBoolean("active", request.active()))
                .and(equalBoolean("pricesIncludeTax", request.pricesIncludeTax()))
                .and(equalBoolean("negativeStockAllowed", request.negativeStockAllowed()));
    }

    private StoreValues values(StoreRequest request, Authentication authentication) {
        String countryCode = normalizeCountryCode(request.countryCode());
        String currencyCode = normalizeCurrencyCode(request.currencyCode());
        String locale = normalizeLocale(request.locale());
        String timezone = normalizeTimezone(request.timezone());
        String administrativeAreaCode = normalizeAdministrativeAreaCode(preferredAdministrativeDivisionCode(
                request.administrativeDivisionCode(),
                request.administrativeAreaCode()));
        StoreGeographySelection geography = validateGeography(
                countryCode,
                administrativeAreaCode,
                currencyCode,
                timezone,
                normalizeTaxRegionCode(request.taxRegionCode()),
                authentication);
        return new StoreValues(
                normalizeCode(request.code()),
                cleanRequired(request.name(), "name"),
                cleanOptional(request.legalName()),
                geography.country().getCode(),
                geography.country().getId(),
                geography.administrativeDivision().getCode(),
                geography.administrativeDivision().getId(),
                cleanRequired(request.address(), "address"),
                cleanOptional(request.phone()),
                normalizeEmail(request.email()),
                geography.currency().getCode(),
                geography.currency().getId(),
                locale,
                geography.timezone().getIanaName(),
                geography.timezone().getId(),
                geography.timezone().getIanaName(),
                geography.taxRegion().getId(),
                geography.taxRegion().getCode(),
                request.pricesIncludeTax(),
                request.negativeStockAllowed(),
                request.active());
    }

    private StoreValues values(StoreUpdateRequest request, Authentication authentication) {
        String countryCode = normalizeCountryCode(request.countryCode());
        String currencyCode = normalizeCurrencyCode(request.currencyCode());
        String locale = normalizeLocale(request.locale());
        String timezone = normalizeTimezone(request.timezone());
        String administrativeAreaCode = normalizeAdministrativeAreaCode(preferredAdministrativeDivisionCode(
                request.administrativeDivisionCode(),
                request.administrativeAreaCode()));
        StoreGeographySelection geography = validateGeography(
                countryCode,
                administrativeAreaCode,
                currencyCode,
                timezone,
                normalizeTaxRegionCode(request.taxRegionCode()),
                authentication);
        return new StoreValues(
                normalizeCode(request.code()),
                cleanRequired(request.name(), "name"),
                cleanOptional(request.legalName()),
                geography.country().getCode(),
                geography.country().getId(),
                geography.administrativeDivision().getCode(),
                geography.administrativeDivision().getId(),
                cleanRequired(request.address(), "address"),
                cleanOptional(request.phone()),
                normalizeEmail(request.email()),
                geography.currency().getCode(),
                geography.currency().getId(),
                locale,
                geography.timezone().getIanaName(),
                geography.timezone().getId(),
                geography.timezone().getIanaName(),
                geography.taxRegion().getId(),
                geography.taxRegion().getCode(),
                request.pricesIncludeTax(),
                request.negativeStockAllowed(),
                request.active());
    }

    private StoreGeographySelection validateGeography(
            String countryCode,
            String administrativeAreaCode,
            String currencyCode,
            String timezone,
            String taxRegionCode,
            Authentication authentication) {
        try {
            return referenceDataService.validateStoreGeography(
                    countryCode,
                    administrativeAreaCode,
                    currencyCode,
                    timezone,
                    taxRegionCode,
                    authorizationService.hasPermission(authentication, PermissionCode.STORE_CURRENCY_OVERRIDE));
        } catch (BadRequestException exception) {
            audit(authentication, AuditAction.INVALID_GEOGRAPHIC_ASSIGNMENT_ATTEMPTED, null, null, null, exception.getMessage());
            throw exception;
        }
    }

    private void requireCurrentVersion(Store store, Long requestedVersion) {
        if (requestedVersion == null || requestedVersion != store.getVersion()) {
            throw new ConflictException("Store was modified by another transaction");
        }
    }

    private void audit(
            Authentication authentication,
            AuditAction action,
            UUID storeId,
            Object beforeSnapshot,
            Object afterSnapshot,
            String reason) {
        auditService.record(new CreateAuditRecordCommand(
                actorUserId(authentication),
                action,
                "STORE",
                storeId,
                storeId,
                null,
                beforeSnapshot,
                afterSnapshot,
                reason));
    }

    private void auditGeographyChanges(Authentication authentication, UUID storeId, StoreResponse before, StoreResponse after) {
        if (!same(before.countryCode(), after.countryCode())) {
            audit(authentication, AuditAction.STORE_COUNTRY_CHANGED, storeId, before.countryCode(), after.countryCode(), null);
        }
        if (!same(before.administrativeAreaCode(), after.administrativeAreaCode())) {
            audit(authentication, AuditAction.STORE_PROVINCE_STATE_CHANGED, storeId, before.administrativeAreaCode(), after.administrativeAreaCode(), null);
        }
        if (!same(before.currencyCode(), after.currencyCode())) {
            audit(authentication, AuditAction.STORE_CURRENCY_CHANGED, storeId, before.currencyCode(), after.currencyCode(), null);
            if (authorizationService.hasPermission(authentication, PermissionCode.STORE_CURRENCY_OVERRIDE)) {
                audit(authentication, AuditAction.STORE_CURRENCY_OVERRIDE_USED, storeId, before.currencyCode(), after.currencyCode(), null);
            }
        }
        if (!same(before.timezone(), after.timezone())) {
            audit(authentication, AuditAction.STORE_TIMEZONE_CHANGED, storeId, before.timezone(), after.timezone(), null);
        }
        if (!same(before.taxRegionCode(), after.taxRegionCode())) {
            audit(authentication, AuditAction.STORE_TAX_REGION_CHANGED, storeId, before.taxRegionCode(), after.taxRegionCode(), null);
        }
    }

    private static boolean same(Object left, Object right) {
        return java.util.Objects.equals(left, right);
    }

    private UUID actorUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .map(User::getId)
                .orElse(null);
    }

    private UUID currentTenantId(Authentication authentication) {
        return currentTenantUser(authentication).getTenantId();
    }

    private User currentTenantUser(Authentication authentication) {
        if (storeAccessService != null) {
            return storeAccessService.currentTenantUser(authentication);
        }
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new com.merchtyl.common.ForbiddenOperationException("Authenticated user is required");
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new com.merchtyl.common.ForbiddenOperationException("Authenticated user is required"));
    }

    private Specification<Store> accessibleTo(User actor) {
        if (storeAccessService != null && StoreAccessService.isOwner(storeAccessService.roles(actor))) {
            return null;
        }
        Set<UUID> storeIds = storeAccessService == null ? Set.of() : storeAccessService.getActiveAssignedStoreIds(actor.getId());
        if (storeIds.isEmpty()) {
            return (root, query, criteriaBuilder) -> criteriaBuilder.disjunction();
        }
        return (root, query, criteriaBuilder) -> root.get("id").in(storeIds);
    }

    private static Specification<Store> equalString(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private static Specification<Store> containsString(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, criteriaBuilder) -> criteriaBuilder.like(
                criteriaBuilder.lower(root.get(field)),
                pattern);
    }

    private static Specification<Store> equalBoolean(String field, Boolean value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private static Specification<Store> equalUuid(String field, UUID value) {
        return (root, query, criteriaBuilder) -> value == null
                ? criteriaBuilder.isNull(root.get(field))
                : criteriaBuilder.equal(root.get(field), value);
    }

    private static String normalizeCode(String value) {
        String code = cleanRequired(value, "code").toUpperCase(Locale.ROOT);
        if (!code.matches("^[A-Z0-9][A-Z0-9_-]*$")) {
            throw new BadRequestException("code may contain only letters, numbers, underscores, and hyphens");
        }
        return code;
    }

    private static String normalizeCodeFilter(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeUpperFilter(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeCountryCode(String value) {
        String countryCode = cleanRequired(value, "countryCode").toUpperCase(Locale.ROOT);
        if (!ISO_COUNTRY_CODES.contains(countryCode)) {
            throw new BadRequestException("countryCode must be a valid ISO 3166-1 alpha-2 code");
        }
        return countryCode;
    }

    private static String normalizeAdministrativeAreaCode(String value) {
        String administrativeAreaCode = cleanRequired(value, "administrativeDivisionCode");
        return administrativeAreaCode.toUpperCase(Locale.ROOT);
    }

    private static String preferredAdministrativeDivisionCode(String administrativeDivisionCode, String administrativeAreaCode) {
        String preferred = cleanOptional(administrativeDivisionCode);
        return preferred == null ? administrativeAreaCode : preferred;
    }

    private static String normalizeTaxRegionCode(String value) {
        String taxRegionCode = cleanOptional(value);
        return taxRegionCode == null ? null : taxRegionCode.toUpperCase(Locale.ROOT);
    }

    private static String normalizeCurrencyCode(String value) {
        String currencyCode = cleanRequired(value, "currencyCode").toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("currencyCode must be a valid ISO 4217 code");
        }
        return currencyCode;
    }

    private static String normalizeLocale(String value) {
        String locale = cleanRequired(value, "locale").replace('_', '-');
        if (Locale.forLanguageTag(locale).getLanguage().isBlank()) {
            throw new BadRequestException("locale must be a valid BCP 47 language tag");
        }
        return locale;
    }

    private static String normalizeTimezone(String value) {
        String timezone = cleanRequired(value, "timezone");
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            throw new BadRequestException("timezone must be a valid IANA time zone");
        }
        return timezone;
    }

    private static String normalizeEmail(String value) {
        String email = cleanOptional(value);
        return email == null ? null : email.toLowerCase(Locale.ROOT);
    }

    private static String cleanRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value.trim();
    }

    private static String cleanOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static ConflictException duplicateCode() {
        return new ConflictException("Store code already exists");
    }
}
