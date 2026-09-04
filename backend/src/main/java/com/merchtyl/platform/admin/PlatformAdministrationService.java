package com.merchtyl.platform.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.common.PageResponse;
import com.merchtyl.common.TooManyRequestsException;
import com.merchtyl.config.JwtProperties;
import com.merchtyl.config.PlatformAdministrationProperties;
import com.merchtyl.email.EmailProperties;
import com.merchtyl.email.EmailTemplateCode;
import com.merchtyl.email.MerchantNotificationEmailEvent;
import com.merchtyl.email.OwnerInvitationEmailEvent;
import com.merchtyl.email.OwnerTemporaryCredentialsEmailEvent;
import com.merchtyl.platform.admin.PlatformDtos.MerchantOnboardingRequest;
import com.merchtyl.platform.admin.PlatformDtos.MerchantGeographyValidationRequest;
import com.merchtyl.platform.admin.PlatformDtos.MerchantGeographyValidationResponse;
import com.merchtyl.platform.admin.PlatformDtos.NamedCode;
import com.merchtyl.platform.admin.PlatformDtos.OnboardingResponse;
import com.merchtyl.platform.admin.PlatformDtos.OnboardingStageResponse;
import com.merchtyl.platform.admin.PlatformDtos.OwnerActivationRequest;
import com.merchtyl.platform.admin.PlatformDtos.OwnerActivationStatusResponse;
import com.merchtyl.platform.admin.PlatformDtos.OwnerInviteResponse;
import com.merchtyl.platform.admin.PlatformDtos.OwnerInvitationDeliverySummary;
import com.merchtyl.platform.admin.PlatformDtos.OwnerInvitationResendRequest;
import com.merchtyl.platform.admin.PlatformDtos.OwnerInvitationResendResponse;
import com.merchtyl.platform.admin.PlatformDtos.PlatformDashboardResponse;
import com.merchtyl.platform.admin.PlatformDtos.PlatformLoginRequest;
import com.merchtyl.platform.admin.PlatformDtos.PlatformSettingsResponse;
import com.merchtyl.platform.admin.PlatformDtos.PlatformUserCreateRequest;
import com.merchtyl.platform.admin.PlatformDtos.PlatformUserResponse;
import com.merchtyl.platform.admin.PlatformDtos.PlatformUserStatusRequest;
import com.merchtyl.platform.admin.PlatformDtos.PlatformUserUpdateRequest;
import com.merchtyl.platform.admin.PlatformDtos.SubscriptionResponse;
import com.merchtyl.platform.admin.PlatformDtos.SubscriptionUpdateRequest;
import com.merchtyl.platform.admin.PlatformDtos.TenantDeleteRequest;
import com.merchtyl.platform.admin.PlatformDtos.TenantDeletionBlockerResponse;
import com.merchtyl.platform.admin.PlatformDtos.TenantDeletionEligibilityResponse;
import com.merchtyl.platform.admin.PlatformDtos.TenantDetailResponse;
import com.merchtyl.platform.admin.PlatformDtos.TenantStatusHistoryResponse;
import com.merchtyl.platform.admin.PlatformDtos.TenantSummaryResponse;
import com.merchtyl.platform.admin.PlatformDtos.TenantUpdateRequest;
import com.merchtyl.platform.admin.PlatformDtos.VersionRequest;
import com.merchtyl.auth.AuthResponse;
import com.merchtyl.auth.JwtService;
import com.merchtyl.reference.ReferenceDataService;
import com.merchtyl.reference.StoreGeographySelection;
import com.merchtyl.security.PermissionCode;
import com.merchtyl.security.RefreshTokenService;
import com.merchtyl.security.RoleName;
import com.merchtyl.config.SecurityProperties;
import com.merchtyl.security.TemporaryPasswordGenerator;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Duration;
import java.util.Base64;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Autowired;
import com.merchtyl.auth.PasswordPolicyService;
import com.merchtyl.portal.MerchantPortalService;

@Service
public class PlatformAdministrationService {
    private static final Logger log = LoggerFactory.getLogger(PlatformAdministrationService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final TypeReference<Map<String, Boolean>> FEATURE_MAP = new TypeReference<>() {
    };
    private static final ConcurrentMap<UUID, String> DEVELOPMENT_INVITATION_TOKENS = new ConcurrentHashMap<>();

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PlatformUserRepository platformUserRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final JwtProperties jwtProperties;
    private final PlatformAdministrationProperties properties;
    private final AuditService auditService;
    private final ReferenceDataService referenceDataService;
    private final ApplicationEventPublisher eventPublisher;
    private final EmailProperties emailProperties;
    private final Environment environment;
    private final TemporaryPasswordGenerator temporaryPasswordGenerator;
    private final SecurityProperties securityProperties;
    @Autowired
    private PasswordPolicyService passwordPolicyService;
    @Autowired
    private MerchantPortalService merchantPortalService;

    public PlatformAdministrationService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            PlatformUserRepository platformUserRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            JwtProperties jwtProperties,
            PlatformAdministrationProperties properties,
            AuditService auditService,
            ReferenceDataService referenceDataService,
            ApplicationEventPublisher eventPublisher,
            EmailProperties emailProperties,
            Environment environment,
            TemporaryPasswordGenerator temporaryPasswordGenerator,
            SecurityProperties securityProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.platformUserRepository = platformUserRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.jwtProperties = jwtProperties;
        this.properties = properties;
        this.auditService = auditService;
        this.referenceDataService = referenceDataService;
        this.eventPublisher = eventPublisher;
        this.emailProperties = emailProperties;
        this.environment = environment;
        this.temporaryPasswordGenerator = temporaryPasswordGenerator;
        this.securityProperties = securityProperties;
    }

    @Transactional
    public AuthResponse login(PlatformLoginRequest request) {
        PlatformUserAccount user = platformUserRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(PlatformAdministrationService::badCredentials);
        if (!user.enabled() || user.locked() || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw badCredentials();
        }
        Instant now = Instant.now();
        jdbcTemplate.update("update platform_users set last_login_at=?, updated_at=now() where id=?", timestamp(now), user.id());
        Instant accessExpiresAt = now.plusSeconds(jwtProperties.expirationMinutes() * 60);
        return new AuthResponse(
                "AUTHENTICATED",
                jwtService.issuePlatformAccessToken(user, now, accessExpiresAt),
                "",
                "Bearer",
                accessExpiresAt,
                now,
                user.id(),
                user.email(),
                user.displayName(),
                List.of(user.role()),
                null,
                null,
                null);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TenantDetailResponse createMerchant(MerchantOnboardingRequest request, Authentication authentication) {
        PlatformUserAccount actor = platformActor(authentication);
        StoreGeographySelection geography = validateMerchantGeography(
                request.countryCode(),
                request.administrativeDivisionCode(),
                request.defaultCurrencyCode(),
                request.primaryTimezone(),
                request.defaultTaxRegionCode(),
                request.currencyOverrideReason(),
                authentication);
        UUID tenantId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID onboardingId = UUID.randomUUID();
        String tenantCode = requestedTenantCode(request);
        String merchantSlug = merchantPortalService.nextAvailableSlug(request.operatingName());
        Instant now = Instant.now();
        String ownerEmail = normalizeEmail(ownerEmail(request));
        String ownerDisplayName = cleanRequired(ownerFirstName(request), "ownerFirstName") + " "
                + cleanRequired(ownerLastName(request), "ownerLastName");
        validateOnboardingUniqueness(tenantCode, ownerEmail);
        Map<String, Object> pricingPlan = jdbcTemplate.queryForList("""
                select id,code,name,status
                from platform_pricing_plans where id=?
                """, request.pricingPlanId()).stream().findFirst()
                .orElseThrow(() -> new BadRequestException("PRICING_PLAN_NOT_FOUND"));
        if (!"ACTIVE".equals(pricingPlan.get("status"))) throw new BadRequestException("PRICING_PLAN_NOT_ACTIVE");
        Map<String, Object> pricingVersion = jdbcTemplate.queryForList("""
                select id,billing_interval,base_price,currency_code,trial_days,included_stores,included_users,
                       additional_store_price,one_time_onboarding_fee,included_registers_per_store,
                       additional_register_price,additional_user_price,effective_from
                from platform_pricing_plan_versions
                where pricing_plan_id=? and status='ACTIVE' and effective_from<=now()
                  and (effective_to is null or effective_to>=now())
                order by effective_from desc,version_number desc limit 1
                """, request.pricingPlanId()).stream().findFirst()
                .orElseThrow(() -> new BadRequestException("PRICING_PLAN_NO_EFFECTIVE_VERSION: This pricing plan does not have an effective pricing version"));
        if (!geography.currency().getCode().equals(pricingVersion.get("currency_code"))) {
            throw new BadRequestException("Selected pricing plan currency must match merchant currency");
        }
        UUID pricingVersionId = (UUID) pricingVersion.get("id");
        validateSelectedStoreCapabilities(pricingVersionId, request.storeCapabilities());
        log.info("billing_event event=MERCHANT_PRICING_PLAN_SELECTED tenant_id={} subscription_public_id={} plan_code={}",
                tenantId, subscriptionId, pricingPlan.get("code"));
        int trialDays = ((Number) pricingVersion.get("trial_days")).intValue();
        String subscriptionStatus = trialDays > 0 ? "TRIAL" : "ACTIVE";
        LocalDate subscriptionStart = LocalDate.now();
        LocalDate billingStart = trialDays > 0 ? subscriptionStart.plusDays(trialDays) : subscriptionStart;
        LocalDate billingEnd = "YEARLY".equals(pricingVersion.get("billing_interval")) ? billingStart.plusYears(1).minusDays(1) : billingStart.plusMonths(1).minusDays(1);

        try {
            jdbcTemplate.update("""
                    insert into tenants (id, tenant_code, merchant_slug, legal_name, display_name, status, country_code,
                                         administrative_division_code, default_currency_code, primary_timezone,
                                         default_tax_region_code, country_id, administrative_division_id,
                                         default_currency_id, primary_timezone_id, default_tax_region_id,
                                         created_by_platform_user_id)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    tenantId,
                    tenantCode,
                    merchantSlug,
                    cleanRequired(request.legalBusinessName(), "legalBusinessName"),
                    cleanRequired(request.operatingName(), "operatingName"),
                    TenantStatus.PENDING_OWNER_ACTIVATION.name(),
                    geography.country().getCode(),
                    geography.administrativeDivision().getCode(),
                    geography.currency().getCode(),
                    geography.timezone().getIanaName(),
                    geography.taxRegion().getCode(),
                    geography.country().getId(),
                    geography.administrativeDivision().getId(),
                    geography.currency().getId(),
                    geography.timezone().getId(),
                    geography.taxRegion().getId(),
                    actor.id());

            jdbcTemplate.update("""
                    insert into merchant_profiles (id, tenant_id, legal_business_name, operating_name, business_number,
                                                   contact_name, contact_email, contact_phone, country_code,
                                                   administrative_division_code, default_currency_code, primary_timezone,
                                                   default_tax_region_code, country_id, administrative_division_id,
                                                   default_currency_id, primary_timezone_id, default_tax_region_id,
                                                   industry_type, estimated_store_count, notes)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    profileId,
                    tenantId,
                    cleanRequired(request.legalBusinessName(), "legalBusinessName"),
                    cleanRequired(request.operatingName(), "operatingName"),
                    cleanOptional(request.businessNumber()),
                    ownerDisplayName,
                    ownerEmail,
                    cleanOptional(ownerPhone(request)),
                    geography.country().getCode(),
                    geography.administrativeDivision().getCode(),
                    geography.currency().getCode(),
                    geography.timezone().getIanaName(),
                    geography.taxRegion().getCode(),
                    geography.country().getId(),
                    geography.administrativeDivision().getId(),
                    geography.currency().getId(),
                    geography.timezone().getId(),
                    geography.taxRegion().getId(),
                    cleanOptional(request.industryType()),
                    request.estimatedStoreCount(),
                    cleanOptional(request.notes()));

            jdbcTemplate.update("""
                    insert into tenant_subscriptions (id,tenant_id,plan_code,status,starts_at,trial_ends_at,renews_at,
                      maximum_stores,maximum_users,features,pricing_plan_id,billing_interval,current_period_start,current_period_end,
                      next_billing_date,base_price_snapshot,currency_code,plan_name_snapshot,plan_code_snapshot,included_stores_snapshot,
                      additional_store_price_snapshot,onboarding_fee_snapshot,pricing_effective_from,pricing_plan_version_id,
                      included_registers_per_store_snapshot,additional_register_price_snapshot,included_users_snapshot,additional_user_price_snapshot)
                    values (?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    subscriptionId,
                    tenantId,
                    pricingPlan.get("code"), subscriptionStatus, timestamp(now), trialDays > 0 ? timestamp(billingStart.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()) : null,
                    timestamp(billingStart.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()), pricingVersion.get("included_stores"),
                    pricingVersion.get("included_users"),
                    "{}", pricingPlan.get("id"), pricingVersion.get("billing_interval"),
                    java.sql.Date.valueOf(billingStart), java.sql.Date.valueOf(billingEnd), java.sql.Date.valueOf(billingStart),
                    pricingVersion.get("base_price"), pricingVersion.get("currency_code"), pricingPlan.get("name"), pricingPlan.get("code"),
                    pricingVersion.get("included_stores"), pricingVersion.get("additional_store_price"), pricingVersion.get("one_time_onboarding_fee"),
                    java.sql.Date.valueOf(subscriptionStart), pricingVersionId, pricingVersion.get("included_registers_per_store"),
                    pricingVersion.get("additional_register_price"), pricingVersion.get("included_users"), pricingVersion.get("additional_user_price"));
            jdbcTemplate.update("""
                    insert into tenant_subscription_capabilities(id,subscription_id,capability,status,inclusion_type_snapshot,billing_unit_snapshot,unit_price_snapshot,effective_from)
                    select gen_random_uuid(),?,capability,case when inclusion_type='INCLUDED' then 'ACTIVE' else 'INACTIVE' end,inclusion_type,billing_unit,unit_price,?
                    from platform_pricing_plan_version_capabilities where pricing_plan_version_id=? and inclusion_type<>'NOT_AVAILABLE'
                    on conflict(subscription_id,capability) do nothing
                    """,subscriptionId,java.sql.Date.valueOf(subscriptionStart),pricingVersionId);
            log.info("billing_event event=MERCHANT_SUBSCRIPTION_CREATED tenant_id={} subscription_public_id={} plan_code={}",
                    tenantId, subscriptionId, pricingPlan.get("code"));

            jdbcTemplate.update("""
                    insert into tenant_onboardings (id, tenant_id, current_stage)
                    values (?, ?, ?)
                    """, onboardingId, tenantId, OnboardingStage.OWNER_INVITATION.name());
            Set<com.merchtyl.store.StoreCapability> storeCapabilities = request.storeCapabilities() == null
                    ? Set.of(com.merchtyl.store.StoreCapability.RETAIL) : request.storeCapabilities();
            if (storeCapabilities.isEmpty()) throw new BadRequestException("At least one store capability is required");
            for (var capability : storeCapabilities) {
                jdbcTemplate.update("""
                        insert into tenant_store_operation_defaults (tenant_id, capability, kitchen_display_name)
                        values (?, ?, ?)
                        """, tenantId, capability.name(), capability == com.merchtyl.store.StoreCapability.FOOD_SERVICE
                                ? defaultKitchenName(request.operatingName(), request.kitchenDisplayName()) : null);
            }
            completeStage(onboardingId, OnboardingStage.MERCHANT_DETAILS, now);
            completeStage(onboardingId, OnboardingStage.OWNER_ACCOUNT, now);

            String temporaryPassword = temporaryPasswordGenerator.generate();
            Instant temporaryPasswordExpiresAt = now.plus(securityProperties.temporaryPassword().expiry());
            String temporaryPasswordHash = passwordEncoder.encode(temporaryPassword);
            User owner = new User(ownerEmail, ownerDisplayName, temporaryPasswordHash);
            owner.assignTenant(tenantId);
            owner.issueTemporaryPassword(temporaryPasswordHash, now, temporaryPasswordExpiresAt);
            User savedOwner = userRepository.saveAndFlush(owner);
            assignRole(savedOwner.getId(), RoleName.TENANT_OWNER);

            publishOwnerTemporaryCredentialsEmail(tenantId, tenantCode, request.operatingName(), ownerDisplayName,
                    savedOwner.getId(), ownerEmail, temporaryPassword, temporaryPasswordExpiresAt,
                    EmailTemplateCode.MERCHANT_OWNER_TEMPORARY_CREDENTIALS, actor.id(),
                    "Initial merchant owner temporary credentials email", null);
            CreatedOwnerInvitation invite = createInvitation(tenantId, savedOwner.getId(), ownerEmail, actor.id());
            publishOwnerInvitationEmail(tenantId, request.tenantCode(), request.operatingName(), ownerDisplayName, invite,
                    EmailTemplateCode.MERCHANT_OWNER_ACTIVATION, actor.id(), "Initial merchant owner activation email", null);
            completeStage(onboardingId, OnboardingStage.OWNER_INVITATION, now);
            recordStatus(tenantId, null, TenantStatus.PENDING_OWNER_ACTIVATION, actor.id(), "merchant onboarding created");

            audit(actor.id(), AuditAction.MERCHANT_TENANT_CREATED, "TENANT", tenantId, null, tenant(tenantId), null);
            audit(actor.id(), AuditAction.PRICING_PLAN_ASSIGNED_TO_MERCHANT, "MERCHANT_SUBSCRIPTION", subscriptionId, null,
                    Map.of("tenantId", tenantId, "pricingPlanId", request.pricingPlanId(), "planCode", pricingPlan.get("code")), null);
            audit(actor.id(), AuditAction.MERCHANT_GEOGRAPHY_SELECTED, "TENANT", tenantId, null,
                    Map.of("countryCode", geography.country().getCode(),
                            "administrativeDivisionCode", geography.administrativeDivision().getCode(),
                            "defaultCurrencyCode", geography.currency().getCode(),
                            "primaryTimezone", geography.timezone().getIanaName(),
                            "defaultTaxRegionCode", geography.taxRegion().getCode()), null);
            if (currencyOverrideUsed(geography)) {
                audit(actor.id(), AuditAction.CURRENCY_OVERRIDE_USED, "TENANT", tenantId, null,
                        Map.of("countryCode", geography.country().getCode(), "defaultCurrencyCode", geography.currency().getCode()),
                        request.currencyOverrideReason());
            }
            audit(actor.id(), AuditAction.INITIAL_OWNER_CREATED, "USER", savedOwner.getId(), null,
                    Map.of("tenantId", tenantId, "email", ownerEmail), null);
            audit(actor.id(), AuditAction.TEMPORARY_OWNER_CREDENTIALS_GENERATED, "USER", savedOwner.getId(), null,
                    Map.of("tenantId", tenantId, "email", ownerEmail, "expiresAt", temporaryPasswordExpiresAt), null);
            audit(actor.id(), AuditAction.OWNER_INVITATION_GENERATED, "TENANT_OWNER_INVITATION", invite.response().invitationId(), null,
                    Map.of("tenantId", tenantId, "ownerUserId", savedOwner.getId(), "email", ownerEmail, "expiresAt", invite.response().expiresAt()), null);
            return getTenant(tenantId);
        } catch (DuplicateKeyException exception) {
            throw exception;
        }
    }

    private static String defaultKitchenName(String operatingName, String requestedName) {
        String cleaned = cleanOptional(requestedName);
        return cleaned == null ? cleanRequired(operatingName, "operatingName") + " Kitchen" : cleaned;
    }

    @Transactional(readOnly = true)
    public MerchantGeographyValidationResponse validateMerchantGeography(
            MerchantGeographyValidationRequest request,
            Authentication authentication) {
        StoreGeographySelection geography = validateMerchantGeography(
                request.countryCode(),
                request.administrativeDivisionCode(),
                request.currencyCode(),
                request.timezone(),
                request.taxRegionCode(),
                request.currencyOverrideReason(),
                authentication);
        return new MerchantGeographyValidationResponse(
                true,
                new NamedCode(geography.country().getCode(), geography.country().getName()),
                new NamedCode(geography.administrativeDivision().getCode(), geography.administrativeDivision().getName()),
                new NamedCode(geography.currency().getCode(), geography.currency().getName()),
                geography.timezone().getIanaName(),
                new NamedCode(geography.taxRegion().getCode(), geography.taxRegion().getName()),
                List.of());
    }

    @Transactional(readOnly = true)
    public PageResponse<TenantSummaryResponse> listTenants(PlatformDtos.TenantListRequest request) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(100, request.size()));
        String search = cleanOptional(request.search());
        if (search != null && search.length() > 100) throw new BadRequestException("Merchant search must not exceed 100 characters");
        List<Object> parameters = new ArrayList<>();
        StringBuilder where = new StringBuilder(" where 1=1");
        if (search != null) {
            where.append("""
                     and (summary.display_name ilike ? or summary.legal_name ilike ? or summary.tenant_code ilike ?
                       or summary.primary_owner_email ilike ?
                       or exists (select 1 from security_users owner_search
                                  join security_user_roles owner_role_link on owner_role_link.user_id=owner_search.id
                                  join security_roles owner_role on owner_role.id=owner_role_link.role_id
                                  where owner_search.tenant_id=summary.id and owner_role.name in ('TENANT_OWNER','OWNER')
                                    and (owner_search.display_name ilike ? or owner_search.email ilike ?))
                       or exists (select 1 from merchant_profiles profile_search where profile_search.tenant_id=summary.id
                                  and profile_search.contact_phone ilike ?))
                    """);
            String pattern = "%" + search + "%";
            for (int index = 0; index < 7; index++) parameters.add(pattern);
        }
        if (request.status() != null) addFilter(where, parameters, "summary.status", request.status().name());
        addFilter(where, parameters, "summary.country_code", normalizedCode(request.country()));
        addFilter(where, parameters, "summary.administrative_division_code", normalizedCode(request.province()));
        if (request.createdFrom() != null) {
            where.append(" and summary.created_at >= ?");
            parameters.add(java.sql.Date.valueOf(request.createdFrom()));
        }
        if (request.createdTo() != null) {
            where.append(" and summary.created_at < ?");
            parameters.add(java.sql.Date.valueOf(request.createdTo().plusDays(1)));
        }
        if (request.createdFrom() != null && request.createdTo() != null && request.createdTo().isBefore(request.createdFrom())) {
            throw new BadRequestException("createdTo must be on or after createdFrom");
        }
        addFilter(where, parameters, "upper(summary.subscription_plan)", normalizedCode(request.pricingPlan()));
        if (cleanOptional(request.subscriptionStatus()) != null) {
            where.append(" and exists (select 1 from tenant_subscriptions subscription_filter where subscription_filter.tenant_id=summary.id and upper(subscription_filter.status)=?)");
            parameters.add(normalizedCode(request.subscriptionStatus()));
        }
        String from = " from platform_tenant_summary summary" + where;
        Long totalValue = jdbcTemplate.queryForObject("select count(*)" + from, Long.class, parameters.toArray());
        long total = totalValue == null ? 0 : totalValue;
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(pageSize);
        pageParameters.add((long) pageNumber * pageSize);
        List<TenantSummaryResponse> content = jdbcTemplate.query("select summary.*" + from + orderBy(request.sort()) + " limit ? offset ?",
                tenantSummaryMapper(), pageParameters.toArray());
        return new PageResponse<>(content, pageNumber, pageSize, total, (int) Math.ceil((double) total / pageSize),
                pageNumber == 0, (pageNumber + 1L) * pageSize >= total);
    }

    private static void addFilter(StringBuilder where, List<Object> parameters, String column, String value) {
        if (value == null) return;
        where.append(" and ").append(column).append(" = ?");
        parameters.add(value);
    }

    private static String normalizedCode(String value) {
        String cleaned = cleanOptional(value);
        return cleaned == null ? null : cleaned.toUpperCase(Locale.ROOT);
    }

    private static String orderBy(String requestedSort) {
        String sort = cleanOptional(requestedSort);
        if (sort == null || sort.equalsIgnoreCase("createdAt,desc")) return " order by summary.created_at desc, summary.id desc";
        if (sort.equalsIgnoreCase("createdAt,asc")) return " order by summary.created_at asc, summary.id asc";
        if (sort.equalsIgnoreCase("merchantName,asc")) return " order by lower(summary.display_name) asc, summary.id asc";
        if (sort.equalsIgnoreCase("merchantName,desc")) return " order by lower(summary.display_name) desc, summary.id desc";
        if (sort.equalsIgnoreCase("status,asc")) return " order by summary.status asc, summary.created_at desc, summary.id desc";
        if (sort.equalsIgnoreCase("status,desc")) return " order by summary.status desc, summary.created_at desc, summary.id desc";
        throw new BadRequestException("Unsupported merchant sort");
    }

    @Transactional(readOnly = true)
    public TenantDetailResponse getTenant(UUID tenantId) {
        TenantSummaryResponse summary = jdbcTemplate.queryForObject("""
                select * from platform_tenant_summary where id = ?
                """, tenantSummaryMapper(), tenantId);
        Map<String, Object> portal = tenant(tenantId);
        String merchantSlug = (String) portal.get("merchant_slug");
        return new TenantDetailResponse(
                summary,
                merchantProfile(tenantId),
                subscription(tenantId),
                onboarding(tenantId),
                merchantSlug,
                merchantPortalService.portalUrl(merchantSlug));
    }

    @Transactional
    public TenantDetailResponse updateTenant(UUID tenantId, TenantUpdateRequest request, Authentication authentication) {
        PlatformUserAccount actor = platformActor(authentication);
        TenantSummaryResponse before = summary(tenantId);
        if (before.version() != request.version()) {
            throw new ConflictException("Tenant was modified by another transaction");
        }
        StoreGeographySelection geography = validateMerchantGeography(
                request.countryCode(),
                request.administrativeDivisionCode(),
                request.defaultCurrencyCode(),
                request.primaryTimezone(),
                request.defaultTaxRegionCode(),
                request.reason(),
                authentication);
        jdbcTemplate.update("""
                update tenants
                set legal_name = ?, display_name = ?, country_code = ?, administrative_division_code = ?,
                    default_currency_code = ?, primary_timezone = ?, default_tax_region_code = ?,
                    country_id = ?, administrative_division_id = ?, default_currency_id = ?,
                    primary_timezone_id = ?, default_tax_region_id = ?,
                    updated_at = now(), version = version + 1
                where id = ? and version = ?
                """,
                cleanRequired(request.legalName(), "legalName"),
                cleanRequired(request.displayName(), "displayName"),
                geography.country().getCode(),
                geography.administrativeDivision().getCode(),
                geography.currency().getCode(),
                geography.timezone().getIanaName(),
                geography.taxRegion().getCode(),
                geography.country().getId(),
                geography.administrativeDivision().getId(),
                geography.currency().getId(),
                geography.timezone().getId(),
                geography.taxRegion().getId(),
                tenantId,
                request.version());
        jdbcTemplate.update("""
                update merchant_profiles
                set country_code = ?, administrative_division_code = ?, default_currency_code = ?,
                    primary_timezone = ?, default_tax_region_code = ?, country_id = ?, administrative_division_id = ?,
                    default_currency_id = ?, primary_timezone_id = ?, default_tax_region_id = ?,
                    updated_at = now(), version = version + 1
                where tenant_id = ?
                """,
                geography.country().getCode(),
                geography.administrativeDivision().getCode(),
                geography.currency().getCode(),
                geography.timezone().getIanaName(),
                geography.taxRegion().getCode(),
                geography.country().getId(),
                geography.administrativeDivision().getId(),
                geography.currency().getId(),
                geography.timezone().getId(),
                geography.taxRegion().getId(),
                tenantId);
        TenantDetailResponse after = getTenant(tenantId);
        audit(actor.id(), AuditAction.MERCHANT_TENANT_UPDATED, "TENANT", tenantId, before, after.tenant(), null);
        auditMerchantGeographyChanges(actor.id(), tenantId, before, after.tenant(), request.reason());
        return after;
    }

    @Transactional
    public TenantDetailResponse activate(UUID tenantId, VersionRequest request, Authentication authentication) {
        return changeStatus(tenantId, TenantStatus.ACTIVE, request.version(), "manual platform activation", authentication);
    }

    @Transactional
    public TenantDetailResponse suspend(UUID tenantId, PlatformDtos.LifecycleRequest request, Authentication authentication) {
        PlatformUserAccount actor = platformActor(authentication);
        TenantSummaryResponse before = summary(tenantId);
        requireVersionIfProvided(before, request.version());
        requireTransition(before, TenantStatus.SUSPENDED, actor.id(), request.reason());
        jdbcTemplate.update("""
                update tenants
                set status = 'SUSPENDED', suspended_at = now(), suspended_by_platform_user_id = ?,
                    suspension_reason = ?, updated_at = now(), version = version + 1
                where id = ?
                """, actor.id(), cleanRequired(request.reason(), "reason"), tenantId);
        recordStatus(tenantId, before.status(), TenantStatus.SUSPENDED, actor.id(), request.reason(), request.notes());
        revokeTenantRefreshTokens(tenantId);
        TenantDetailResponse after = getTenant(tenantId);
        audit(actor.id(), AuditAction.MERCHANT_SUSPENDED, "TENANT", tenantId, before, after.tenant(), request.reason());
        return after;
    }

    @Transactional
    public TenantDetailResponse reactivate(UUID tenantId, PlatformDtos.LifecycleRequest request, Authentication authentication) {
        PlatformUserAccount actor = platformActor(authentication);
        TenantSummaryResponse before = summary(tenantId);
        requireVersionIfProvided(before, request.version());
        requireTransition(before, TenantStatus.ACTIVE, actor.id(), request.reason());
        jdbcTemplate.update("""
                update tenants
                set status = 'ACTIVE',
                    activated_at = coalesce(activated_at, now()),
                    reactivated_at = now(),
                    reactivated_by_platform_user_id = ?,
                    updated_at = now(),
                    version = version + 1
                where id = ?
                """, actor.id(), tenantId);
        recordStatus(tenantId, before.status(), TenantStatus.ACTIVE, actor.id(), lifecycleReason(request, "tenant reactivated"), request.notes());
        TenantDetailResponse after = getTenant(tenantId);
        audit(actor.id(), AuditAction.MERCHANT_REACTIVATED, "TENANT", tenantId, before, after.tenant(), lifecycleReason(request, "tenant reactivated"));
        return after;
    }

    @Transactional
    public TenantDetailResponse close(UUID tenantId, PlatformDtos.LifecycleRequest request, Authentication authentication) {
        PlatformUserAccount actor = platformActor(authentication);
        TenantSummaryResponse before = summary(tenantId);
        requireVersionIfProvided(before, request.version());
        requireTransition(before, TenantStatus.CLOSED, actor.id(), request.reason());
        requireMerchantConfirmation(before, request.confirmation());
        jdbcTemplate.update("""
                update tenants
                set status = 'CLOSED',
                    closed_at = now(),
                    closed_by_platform_user_id = ?,
                    closure_reason = ?,
                    updated_at = now(),
                    version = version + 1
                where id = ?
                """, actor.id(), cleanRequired(request.reason(), "reason"), tenantId);
        jdbcTemplate.update("""
                update tenant_subscriptions
                set status = case when status <> 'CANCELLED' then 'CANCELLED' else status end,
                    cancelled_at = coalesce(cancelled_at, now()),
                    updated_at = now(),
                    version = version + 1
                where tenant_id = ?
                """, tenantId);
        recordStatus(tenantId, before.status(), TenantStatus.CLOSED, actor.id(), request.reason(), request.notes());
        revokeTenantRefreshTokens(tenantId);
        TenantDetailResponse after = getTenant(tenantId);
        audit(actor.id(), AuditAction.MERCHANT_CLOSED, "TENANT", tenantId, before, after.tenant(), request.reason());
        return after;
    }

    @Transactional
    public TenantDetailResponse reopen(UUID tenantId, PlatformDtos.LifecycleRequest request, Authentication authentication) {
        PlatformUserAccount actor = platformActor(authentication);
        TenantSummaryResponse before = summary(tenantId);
        requireVersionIfProvided(before, request.version());
        if (before.status() != TenantStatus.CLOSED) {
            audit(actor.id(), AuditAction.INVALID_STATUS_TRANSITION_ATTEMPTED, "TENANT", before.id(),
                    Map.of("status", before.status()), Map.of("requestedStatus", TenantStatus.ACTIVE, "operation", "reopen"), request.reason());
            throw new BadRequestException("Only closed merchants can be reopened");
        }
        requireMerchantConfirmation(before, request.confirmation());
        jdbcTemplate.update("""
                update tenants
                set status = 'ACTIVE',
                    reactivated_at = now(),
                    reactivated_by_platform_user_id = ?,
                    updated_at = now(),
                    version = version + 1
                where id = ?
                """, actor.id(), tenantId);
        recordStatus(tenantId, before.status(), TenantStatus.ACTIVE, actor.id(), lifecycleReason(request, "merchant reopened"), request.notes());
        TenantDetailResponse after = getTenant(tenantId);
        audit(actor.id(), AuditAction.MERCHANT_REOPENED, "TENANT", tenantId, before, after.tenant(), lifecycleReason(request, "merchant reopened"));
        return after;
    }

    @Transactional(readOnly = true)
    public TenantDeletionEligibilityResponse deletionEligibility(UUID tenantId, Authentication authentication) {
        PlatformUserAccount actor = platformActor(authentication);
        TenantDeletionEligibilityResponse response = deletionEligibility(tenantId);
        audit(actor.id(), AuditAction.DELETION_ELIGIBILITY_CHECKED, "TENANT", tenantId, null, response, null);
        return response;
    }

    @Transactional
    public void deleteEmptyTenant(UUID tenantId, TenantDeleteRequest request, Authentication authentication) {
        PlatformUserAccount actor = platformActor(authentication);
        Map<String, Object> lockedTenant = jdbcTemplate.queryForMap("""
                select id, tenant_code, status, version
                from tenants
                where id = ?
                for update
                """, tenantId);
        TenantSummaryResponse before = summary(tenantId);
        requireVersionIfProvided(before, request.version());
        String expected = "DELETE " + lockedTenant.get("tenant_code");
        if (!expected.equals(cleanRequired(request.confirmation(), "confirmation"))) {
            throw new BadRequestException("confirmation must exactly match " + expected);
        }
        audit(actor.id(), AuditAction.MERCHANT_DELETION_REQUESTED, "TENANT", tenantId, before, null, cleanOptional(request.reason()));
        TenantDeletionEligibilityResponse eligibility = deletionEligibility(tenantId);
        if (!eligibility.eligible()) {
            audit(actor.id(), AuditAction.MERCHANT_DELETION_BLOCKED, "TENANT", tenantId, before, eligibility, "merchant has deletion blockers");
            throw new ConflictException("Merchant cannot be deleted; use suspend or close instead");
        }

        String tenantCode = (String) lockedTenant.get("tenant_code");
        jdbcTemplate.update("update tenant_status_history set tenant_code_snapshot = coalesce(tenant_code_snapshot, ?) where tenant_id = ?", tenantCode, tenantId);
        jdbcTemplate.update("delete from tenant_owner_invitations where tenant_id = ?", tenantId);
        jdbcTemplate.update("""
                delete from tenant_onboarding_stages
                where tenant_onboarding_id in (select id from tenant_onboardings where tenant_id = ?)
                """, tenantId);
        jdbcTemplate.update("delete from tenant_onboardings where tenant_id = ?", tenantId);
        jdbcTemplate.update("delete from tenant_subscriptions where tenant_id = ?", tenantId);
        jdbcTemplate.update("delete from tenant_features where tenant_id = ?", tenantId);
        jdbcTemplate.update("""
                delete from security_refresh_tokens
                where user_id in (select id from security_users where tenant_id = ?)
                """, tenantId);
        jdbcTemplate.update("""
                delete from security_user_roles
                where user_id in (select id from security_users where tenant_id = ?)
                """, tenantId);
        jdbcTemplate.update("delete from security_users where tenant_id = ?", tenantId);
        jdbcTemplate.update("delete from merchant_profiles where tenant_id = ?", tenantId);
        jdbcTemplate.update("delete from tenants where id = ?", tenantId);

        audit(actor.id(), AuditAction.MERCHANT_DELETED, "TENANT", tenantId, before,
                Map.of("tenantCode", tenantCode, "deleted", true), cleanOptional(request.reason()));
    }

    @Transactional
    public SubscriptionResponse updateSubscription(UUID tenantId, SubscriptionUpdateRequest request, Authentication authentication) {
        PlatformUserAccount actor = platformActor(authentication);
        SubscriptionResponse before = subscription(tenantId);
        if (before.version() != request.version()) {
            throw new ConflictException("Subscription was modified by another transaction");
        }
        jdbcTemplate.update("""
                update tenant_subscriptions
                set plan_code = ?, status = ?, starts_at = ?, trial_ends_at = ?, renews_at = ?, cancelled_at = ?,
                    maximum_stores = ?, maximum_users = ?, features = ?::jsonb, updated_at = now(), version = version + 1
                where tenant_id = ? and version = ?
                """,
                normalizeCode(request.planCode(), "planCode"),
                normalizeSubscriptionStatus(request.status()),
                timestamp(request.startsAt()),
                timestamp(request.trialEndsAt()),
                timestamp(request.renewsAt()),
                timestamp(request.cancelledAt()),
                request.maximumStores(),
                request.maximumUsers(),
                featuresJson(request.features()),
                tenantId,
                request.version());
        SubscriptionResponse after = subscription(tenantId);
        audit(actor.id(), AuditAction.SUBSCRIPTION_CHANGED, "TENANT_SUBSCRIPTION", after.id(), before, after, null);
        return after;
    }

    @Transactional(readOnly = true)
    public OnboardingResponse onboarding(UUID tenantId) {
        UUID onboardingId = jdbcTemplate.queryForObject("""
                select id from tenant_onboardings where tenant_id = ?
                """, UUID.class, tenantId);
        var header = jdbcTemplate.queryForMap("""
                select current_stage, completed_at from tenant_onboardings where tenant_id = ?
                """, tenantId);
        List<OnboardingStageResponse> stages = jdbcTemplate.query("""
                select stage, completed_at from tenant_onboarding_stages
                where tenant_onboarding_id = ?
                order by created_at, stage
                """,
                (rs, rowNum) -> new OnboardingStageResponse(
                        OnboardingStage.valueOf(rs.getString("stage")),
                        instant(rs.getObject("completed_at"))),
                onboardingId);
        return new OnboardingResponse(
                tenantId,
                OnboardingStage.valueOf((String) header.get("current_stage")),
                instant(header.get("completed_at")),
                stages);
    }

    @Transactional(readOnly = true)
    public List<TenantStatusHistoryResponse> statusHistory(UUID tenantId) {
        summary(tenantId);
        return jdbcTemplate.query("""
                select id, tenant_id, tenant_code_snapshot, old_status, new_status, reason, notes,
                       changed_by_platform_user_id, created_at, correlation_id
                from tenant_status_history
                where tenant_id = ?
                order by created_at desc, id desc
                """, statusHistoryMapper(), tenantId);
    }

    @Transactional(readOnly = true)
    public OwnerActivationStatusResponse ownerInvitation(UUID tenantId) {
        summary(tenantId);
        return ownerActivationStatus(tenantId);
    }

    @Transactional
    public OwnerInviteResponse resendInvitation(UUID tenantId, Authentication authentication) {
        CreatedOwnerInvitation invitation = resendInvitationInternal(
                tenantId,
                new OwnerInvitationResendRequest("Platform owner invitation requested", null),
                authentication);
        return invitation.response();
    }

    @Transactional
    public OwnerInvitationResendResponse resendActivationEmail(
            UUID tenantId,
            OwnerInvitationResendRequest request,
            Authentication authentication) {
        resendInvitationInternal(tenantId, request, authentication);
        return resendResponse(tenantId);
    }

    @Transactional
    public OwnerActivationStatusResponse resendTemporaryCredentials(
            UUID tenantId,
            UUID ownerId,
            OwnerInvitationResendRequest request,
            Authentication authentication) {
        PlatformUserAccount actor = platformActor(authentication);
        String reason = cleanRequired(request.reason(), "reason");
        String notes = cleanOptional(request.notes());
        TenantSummaryResponse tenant = summary(tenantId);
        if (tenant.status() == TenantStatus.CLOSED || tenant.status() == TenantStatus.SUSPENDED || tenant.status() == TenantStatus.REJECTED) {
            throw new BadRequestException("Temporary credentials cannot be reissued while tenant status is " + tenant.status().name());
        }
        User owner = userRepository.findByIdAndTenantId(ownerId, tenantId)
                .orElseThrow(() -> new NotFoundException("Tenant owner not found"));
        if (!owner.isPasswordChangeRequired()) {
            throw new ConflictException("Merchant owner has already completed first-login password change.");
        }
        String temporaryPassword = temporaryPasswordGenerator.generate();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(securityProperties.temporaryPassword().expiry());
        owner.issueTemporaryPassword(passwordEncoder.encode(temporaryPassword), now, expiresAt);
        userRepository.saveAndFlush(owner);
        refreshTokenService.revokeActiveTokensForUser(owner, now);
        revokeFirstLoginPasswordChangeTokens(owner.getId(), now);
        publishOwnerTemporaryCredentialsEmail(
                tenantId,
                tenant.tenantCode(),
                tenant.displayName(),
                owner.getDisplayName(),
                owner.getId(),
                owner.getEmail(),
                temporaryPassword,
                expiresAt,
                EmailTemplateCode.MERCHANT_OWNER_TEMPORARY_CREDENTIALS_RESEND,
                actor.id(),
                reason,
                notes);
        audit(actor.id(), AuditAction.TEMPORARY_CREDENTIALS_REISSUED, "USER", owner.getId(), null,
                Map.of("tenantId", tenantId, "ownerEmail", owner.getEmail(), "expiresAt", expiresAt), reason);
        return ownerActivationStatus(tenantId);
    }

    private CreatedOwnerInvitation resendInvitationInternal(UUID tenantId, OwnerInvitationResendRequest request, Authentication authentication) {
        PlatformUserAccount actor = platformActor(authentication);
        String reason = cleanRequired(request.reason(), "reason");
        String notes = cleanOptional(request.notes());
        TenantSummaryResponse tenant = summary(tenantId);
        if (tenant.status() == TenantStatus.CLOSED || tenant.status() == TenantStatus.SUSPENDED || tenant.status() == TenantStatus.REJECTED) {
            throw new BadRequestException("Merchant owner activation email cannot be resent while tenant status is " + tenant.status().name());
        }
        Map<String, Object> owner = jdbcTemplate.queryForMap("""
                select id, email, display_name, enabled from security_users
                where tenant_id = ?
                order by created_at asc
                limit 1
                """, tenantId);
        if (Boolean.TRUE.equals(owner.get("enabled"))) {
            audit(actor.id(), AuditAction.OWNER_ACTIVATION_RESEND_BLOCKED_OWNER_ACTIVE, "USER", (UUID) owner.get("id"), null,
                    Map.of("tenantId", tenantId, "ownerEmail", owner.get("email")), reason);
            throw new ConflictException("Merchant owner account is already activated.");
        }
        String ownerEmail = normalizeEmail((String) owner.get("email"));
        if (ownerEmail.isBlank() || !ownerEmail.contains("@")) {
            throw new BadRequestException("Merchant owner email is missing or invalid");
        }
        enforceResendRateLimit(tenantId, (UUID) owner.get("id"), actor.id(), ownerEmail, reason);
        List<UUID> activeInvitationIds = jdbcTemplate.query("""
                select id from tenant_owner_invitations
                where tenant_id = ? and owner_user_id = ? and status in ('PENDING', 'SENT')
                order by created_at desc
                """, (rs, rowNum) -> rs.getObject("id", UUID.class), tenantId, owner.get("id"));
        int invalidated = jdbcTemplate.update("""
                update tenant_owner_invitations
                set status = 'INVALIDATED', invalidated_at = now(), invalidation_reason = ?,
                    updated_at = now(), version = version + 1
                where tenant_id = ? and owner_user_id = ? and status in ('PENDING', 'SENT')
                """, reason, tenantId, owner.get("id"));
        if (invalidated > 0) {
            activeInvitationIds.forEach(DEVELOPMENT_INVITATION_TOKENS::remove);
            audit(actor.id(), AuditAction.PREVIOUS_INVITATION_INVALIDATED, "TENANT", tenantId, null,
                    Map.of("tenantId", tenantId, "ownerId", owner.get("id"), "ownerEmail", ownerEmail,
                            "oldInvitationIds", activeInvitationIds, "invalidatedInvitations", invalidated), reason);
        }
        audit(actor.id(), AuditAction.OWNER_ACTIVATION_RESEND_REQUESTED, "TENANT", tenantId, null,
                Map.of("tenantId", tenantId, "ownerId", owner.get("id"), "ownerEmail", ownerEmail), reason);
        CreatedOwnerInvitation invitation = createInvitation(tenantId, (UUID) owner.get("id"), ownerEmail, actor.id());
        publishOwnerInvitationEmail(tenantId, tenant.tenantCode(), tenant.displayName(), (String) owner.get("display_name"), invitation,
                EmailTemplateCode.MERCHANT_OWNER_INVITATION_RESEND, actor.id(), reason, notes);
        OwnerInviteResponse response = invitation.response();
        audit(actor.id(), AuditAction.OWNER_INVITATION_RESENT, "TENANT_OWNER_INVITATION", response.invitationId(), null,
                Map.of("tenantId", tenantId, "ownerId", owner.get("id"), "email", response.email(), "expiresAt", response.expiresAt(), "deliveryStatus", response.deliveryStatus()), reason);
        return invitation;
    }

    private void enforceResendRateLimit(UUID tenantId, UUID ownerId, UUID actorId, String ownerEmail, String reason) {
        Instant now = Instant.now();
        Instant hourStart = now.minus(Duration.ofHours(1));
        Instant minStart = now.minus(properties.ownerInvitationResendMinInterval());
        long recent = count("""
                select count(*) from tenant_owner_invitations
                where tenant_id = ? and owner_user_id = ? and created_at >= ?
                """, tenantId, ownerId, timestamp(hourStart));
        long tooSoon = count("""
                select count(*) from tenant_owner_invitations
                where tenant_id = ? and owner_user_id = ? and created_at >= ?
                """, tenantId, ownerId, timestamp(minStart));
        long total = count("""
                select count(*) from tenant_owner_invitations
                where tenant_id = ? and owner_user_id = ?
                """, tenantId, ownerId);
        if (recent >= properties.ownerInvitationResendMaxPerHour()
                || tooSoon > 0
                || total >= properties.ownerInvitationResendMaxTotal()) {
            audit(actorId, AuditAction.OWNER_ACTIVATION_RESEND_RATE_LIMITED, "TENANT", tenantId, null,
                    Map.of("tenantId", tenantId, "ownerId", ownerId, "ownerEmail", ownerEmail), reason);
            throw new TooManyRequestsException("Owner activation email resend rate limit exceeded",
                    properties.ownerInvitationResendMinInterval());
        }
    }

    private OwnerInvitationResendResponse resendResponse(UUID tenantId) {
        OwnerActivationStatusResponse status = ownerActivationStatus(tenantId);
        return new OwnerInvitationResendResponse(
                status.tenantId(),
                status.ownerId(),
                status.ownerEmail(),
                status.invitationStatus(),
                status.invitationExpiresAt(),
                latestDeliverySummary(status.invitationId()));
    }

    private OwnerInvitationDeliverySummary latestDeliverySummary(UUID invitationId) {
        if (invitationId == null) {
            return null;
        }
        return jdbcTemplate.query("""
                select id, provider, provider_message_id, status, attempt_count, last_attempt_at
                from email_deliveries
                where invitation_id = ?
                order by created_at desc, id desc
                limit 1
                """, (rs, rowNum) -> new OwnerInvitationDeliverySummary(
                rs.getObject("id", UUID.class),
                rs.getString("provider"),
                rs.getString("provider_message_id"),
                rs.getString("status"),
                rs.getInt("attempt_count"),
                instant(rs.getObject("last_attempt_at"))), invitationId).stream().findFirst().orElse(null);
    }

    private OwnerActivationStatusResponse ownerActivationStatus(UUID tenantId) {
        TenantSummaryResponse tenant = summary(tenantId);
        Map<String, Object> owner = jdbcTemplate.queryForList("""
                select id, email, display_name, enabled, locked, failed_login_attempts, last_failed_login_at,
                       locked_at, lock_reason, password_change_required,
                       temporary_password_issued_at, temporary_password_expires_at, first_login_at,
                       password_changed_at, credentials_issued_at, credentials_delivery_status
                from security_users
                where tenant_id = ?
                order by created_at asc
                limit 1
                """, tenantId).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("Merchant owner not found"));
        Map<String, Object> invitation = jdbcTemplate.queryForList("""
                select id, status, expires_at, accepted_at, created_at
                from tenant_owner_invitations
                where tenant_id = ? and owner_user_id = ?
                order by created_at desc, id desc
                limit 1
                """, tenantId, owner.get("id")).stream().findFirst().orElse(null);
        Map<String, Object> latestDelivery = jdbcTemplate.queryForList("""
                select id, invitation_id, provider, status, attempt_count, last_attempt_at, sent_at, failure_message_sanitized
                from email_deliveries
                where tenant_id = ? and template_code in (
                    'MERCHANT_OWNER_ACTIVATION',
                    'MERCHANT_OWNER_INVITATION_RESEND',
                    'MERCHANT_OWNER_TEMPORARY_CREDENTIALS',
                    'MERCHANT_OWNER_TEMPORARY_CREDENTIALS_RESEND'
                )
                order by created_at desc, id desc
                limit 1
                """, tenantId).stream().findFirst().orElse(null);
        UUID invitationId = invitation == null ? null : (UUID) invitation.get("id");
        String invitationStatus = invitationStatus(invitation, Boolean.TRUE.equals(owner.get("enabled")));
        Instant activationCompletedAt = activationCompletedAt(tenantId, (UUID) owner.get("id"));
        boolean hasValidInvitation = invitation != null
                && List.of("PENDING", "SENT").contains((String) invitation.get("status"))
                && instant(invitation.get("expires_at")).isAfter(Instant.now());
        boolean deliveryFailed = latestDelivery != null
                && List.of("FAILED", "RETRY_SCHEDULED").contains((String) latestDelivery.get("status"));
        boolean successfulCurrentDelivery = invitationId != null && count("""
                select count(*) from email_deliveries where invitation_id = ? and status = 'SENT'
                """, invitationId) > 0;
        boolean canResend = !Boolean.TRUE.equals(owner.get("enabled"))
                && tenant.status() != TenantStatus.CLOSED
                && tenant.status() != TenantStatus.SUSPENDED
                && tenant.status() != TenantStatus.REJECTED;
        boolean canRetry = hasValidInvitation && deliveryFailed && !successfulCurrentDelivery;
        String developmentActivationUrl = developmentActivationUrl(invitationId);
        return new OwnerActivationStatusResponse(
                tenantId,
                (UUID) owner.get("id"),
                (String) owner.get("display_name"),
                (String) owner.get("email"),
                ownerAccountStatus(owner),
                invitationStatus,
                invitationId,
                invitation == null ? null : instant(invitation.get("created_at")),
                invitation == null ? null : instant(invitation.get("expires_at")),
                latestDelivery == null ? emailProperties.resolvedProvider().name() : (String) latestDelivery.get("provider"),
                latestDelivery == null ? null : (String) latestDelivery.get("status"),
                latestDelivery == null ? null : instant(latestDelivery.get("last_attempt_at")),
                latestDelivery == null ? null : instant(latestDelivery.get("sent_at")),
                latestDelivery == null ? 0 : (Integer) latestDelivery.get("attempt_count"),
                latestDelivery == null ? null : (String) latestDelivery.get("failure_message_sanitized"),
                activationCompletedAt,
                instant(owner.get("temporary_password_issued_at")),
                instant(owner.get("temporary_password_expires_at")),
                (String) owner.get("credentials_delivery_status"),
                instant(owner.get("first_login_at")),
                instant(owner.get("password_changed_at")),
                (Integer) owner.get("failed_login_attempts"),
                instant(owner.get("last_failed_login_at")),
                instant(owner.get("locked_at")),
                (String) owner.get("lock_reason"),
                temporaryCredentialsExpired(owner),
                developmentActivationUrl,
                canResend,
                canRetry,
                canRetry && latestDelivery != null ? (UUID) latestDelivery.get("id") : null,
                developmentActivationUrl != null,
                Boolean.TRUE.equals(owner.get("password_change_required")) && tenant.status() != TenantStatus.CLOSED
                        && tenant.status() != TenantStatus.SUSPENDED && tenant.status() != TenantStatus.REJECTED);
    }

    @Transactional
    public void disableOwner(UUID tenantId, UUID ownerId, Authentication authentication) {
        PlatformUserAccount actor = platformActor(authentication);
        User owner = userRepository.findById(ownerId)
                .filter(user -> tenantId.equals(user.getTenantId()))
                .orElseThrow(() -> new NotFoundException("Tenant owner not found"));
        owner.disable();
        revokeTenantRefreshTokens(tenantId);
        audit(actor.id(), AuditAction.TENANT_OWNER_DISABLED, "USER", ownerId, null,
                Map.of("tenantId", tenantId, "email", owner.getEmail(), "enabled", false), null);
    }

    @Transactional
    public void activateOwner(OwnerActivationRequest request) {
        String tokenHash = hashToken(request.token());
        Map<String, Object> invitation = jdbcTemplate.queryForMap("""
                select id, tenant_id, owner_user_id, expires_at, status
                from tenant_owner_invitations
                where token_hash = ?
                """, tokenHash);
        if (!List.of("PENDING", "SENT").contains((String) invitation.get("status")) || !instant(invitation.get("expires_at")).isAfter(Instant.now())) {
            throw new BadRequestException("Invitation token is expired or invalid");
        }
        UUID tenantId = (UUID) invitation.get("tenant_id");
        TenantStatus tenantStatus = tenantStatus(tenantId)
                .orElseThrow(() -> new NotFoundException("Merchant tenant not found"));
        if (List.of(TenantStatus.SUSPENDED, TenantStatus.CLOSED, TenantStatus.REJECTED).contains(tenantStatus)) {
            throw new BadRequestException("Merchant account cannot be activated while tenant status is " + tenantStatus.name());
        }
        User owner = userRepository.findById((UUID) invitation.get("owner_user_id"))
                .filter(user -> tenantId.equals(user.getTenantId()))
                .orElseThrow(() -> new NotFoundException("Invitation owner not found"));
        passwordPolicy().validate(request.password());
        owner.changePasswordHash(passwordEncoder.encode(request.password()));
        owner.enable();
        jdbcTemplate.update("""
                update tenant_owner_invitations
                set status = 'USED', accepted_at = now(), updated_at = now(), version = version + 1
                where id = ?
                """, invitation.get("id"));
        DEVELOPMENT_INVITATION_TOKENS.remove((UUID) invitation.get("id"));
        advanceOnboarding(tenantId, OnboardingStage.ORGANIZATION_SETUP, OnboardingStage.OWNER_ACTIVATION);
        audit(owner.getId(), AuditAction.OWNER_ACTIVATED, "USER", owner.getId(), null,
                Map.of("tenantId", tenantId, "email", owner.getEmail()), null);
    }

    @Transactional
    public void markOwnerPasswordChanged(UUID tenantId, UUID ownerId) {
        UUID onboardingId = jdbcTemplate.queryForObject("select id from tenant_onboardings where tenant_id = ?", UUID.class, tenantId);
        completeStage(onboardingId, OnboardingStage.OWNER_ACTIVATION, Instant.now());
        jdbcTemplate.update("""
                update tenant_onboardings
                set current_stage = case when current_stage = 'OWNER_INVITATION' then 'ORGANIZATION_SETUP' else current_stage end,
                    updated_at = now(), version = version + 1
                where id = ?
                """, onboardingId);
        jdbcTemplate.update("""
                update tenant_owner_invitations
                set status = case when status in ('PENDING', 'SENT') then 'USED' else status end,
                    accepted_at = coalesce(accepted_at, now()), updated_at = now(), version = version + 1
                where tenant_id = ? and owner_user_id = ? and status in ('PENDING', 'SENT')
                """, tenantId, ownerId);
    }

    @Transactional
    public void markFirstStoreCreated(UUID tenantId) {
        if (tenantId == null) {
            return;
        }
        TenantSummaryResponse tenant = summary(tenantId);
        if (tenant.status() == TenantStatus.SUSPENDED || tenant.status() == TenantStatus.CLOSED || tenant.status() == TenantStatus.REJECTED) {
            return;
        }
        advanceOnboarding(tenantId, OnboardingStage.COMPLETED, OnboardingStage.FIRST_STORE_SETUP);
        jdbcTemplate.update("""
                update tenants
                set status = 'ACTIVE', activated_at = coalesce(activated_at, now()), updated_at = now(), version = version + 1
                where id = ? and status <> 'ACTIVE'
                """, tenantId);
        recordStatus(tenantId, tenant.status(), TenantStatus.ACTIVE, null, "first store created");
        audit(null, AuditAction.MERCHANT_ACTIVATED, "TENANT", tenantId, tenant, summary(tenantId), "first store created");
    }

    @Transactional(readOnly = true)
    public PlatformDashboardResponse dashboard() {
        return new PlatformDashboardResponse(
                count("select count(*) from tenants where status = 'ACTIVE'"),
                count("select count(*) from tenants where status in ('PENDING_ONBOARDING', 'PENDING_OWNER_ACTIVATION')"),
                count("select count(*) from tenants where status = 'SUSPENDED'"),
                count("select count(*) from tenants where status = 'CLOSED'"),
                count("select count(*) from tenants where status in ('SUSPENDED', 'REJECTED') or status in ('PENDING_ONBOARDING', 'PENDING_OWNER_ACTIVATION')"),
                count("select count(*) from stores where active = true"),
                count("select count(*) from security_users where enabled = true"),
                count("select count(*) from tenant_subscriptions where status = 'TRIAL'"),
                jdbcTemplate.query("""
                        select * from platform_tenant_summary
                        order by created_at desc, id desc
                        limit 8
                        """, tenantSummaryMapper()),
                jdbcTemplate.query("""
                        select id, tenant_id, tenant_code_snapshot, old_status, new_status, reason, notes,
                               changed_by_platform_user_id, created_at, correlation_id
                        from tenant_status_history
                        order by created_at desc, id desc
                        limit 8
                        """, statusHistoryMapper()),
                count("select count(*) from tenant_owner_invitations where status in ('EXPIRED', 'INVALIDATED')"),
                properties.supportAccess().enabled(),
                properties.supportAccessDefaultDuration().toMinutes());
    }

    @Transactional(readOnly = true)
    public PlatformSettingsResponse settings() {
        return new PlatformSettingsResponse(
                properties.bootstrap().enabled(),
                properties.ownerInvitationExpiry().toHours(),
                properties.supportAccess().enabled(),
                properties.supportAccessDefaultDuration().toMinutes(),
                List.of(TenantStatus.values()).stream().map(Enum::name).toList(),
                List.of(OnboardingStage.values()).stream().map(Enum::name).toList(),
                List.of("TRIAL", "ACTIVE", "PAST_DUE", "SUSPENDED", "CANCELLED"),
                LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<PlatformUserResponse> listPlatformUsers() {
        return platformUserRepository.findAll().stream()
                .map(PlatformUserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlatformUserResponse getPlatformUser(UUID id) {
        return platformUserRepository.findById(id).map(PlatformUserResponse::from)
                .orElseThrow(() -> new NotFoundException("Platform user not found"));
    }

    @Transactional
    public PlatformUserResponse createPlatformUser(PlatformUserCreateRequest request, Authentication authentication) {
        PlatformUserAccount actor = platformActor(authentication);
        requirePlatformRole(request.role());
        passwordPolicy().validate(request.password());
        PlatformUserAccount created = platformUserRepository.create(
                normalizeEmail(request.email()),
                cleanRequired(request.displayName(), "displayName"),
                passwordEncoder.encode(request.password()),
                request.role(),
                !Boolean.FALSE.equals(request.enabled()),
                true);
        audit(actor.id(), AuditAction.PLATFORM_USER_CREATED, "PLATFORM_USER", created.id(), null, PlatformUserResponse.from(created), null);
        if (created.role() == RoleName.PLATFORM_SUPER_ADMIN) {
            audit(actor.id(), AuditAction.PLATFORM_ROLE_CHANGED, "PLATFORM_USER", created.id(), null,
                    Map.of("role", created.role()), "platform super admin created");
        }
        return PlatformUserResponse.from(created);
    }

    @Transactional
    public PlatformUserResponse updatePlatformUser(UUID id, PlatformUserUpdateRequest request, Authentication authentication) {
        PlatformUserAccount actor = platformActor(authentication);
        PlatformUserAccount before = platformUserRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Platform user not found"));
        requirePlatformRole(request.role());
        PlatformUserAccount after = platformUserRepository.update(
                id,
                normalizeEmail(request.email()),
                cleanRequired(request.displayName(), "displayName"),
                request.role(),
                request.locked(),
                request.version());
        audit(actor.id(), AuditAction.PLATFORM_USER_UPDATED, "PLATFORM_USER", id,
                PlatformUserResponse.from(before), PlatformUserResponse.from(after), null);
        if (before.role() != after.role()) {
            audit(actor.id(), AuditAction.PLATFORM_ROLE_CHANGED, "PLATFORM_USER", id,
                    Map.of("role", before.role()), Map.of("role", after.role()), null);
        }
        return PlatformUserResponse.from(after);
    }

    @Transactional
    public PlatformUserResponse updatePlatformUserStatus(UUID id, PlatformUserStatusRequest request, Authentication authentication) {
        PlatformUserAccount actor = platformActor(authentication);
        PlatformUserAccount before = platformUserRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Platform user not found"));
        if (before.role() == RoleName.PLATFORM_SUPER_ADMIN && before.enabled() && !request.enabled()
                && platformUserRepository.activeSuperAdminCount() <= 1) {
            throw new ForbiddenOperationException("Cannot disable the final active Platform Super Admin");
        }
        PlatformUserAccount after = platformUserRepository.updateStatus(id, request.enabled(), request.version());
        audit(actor.id(), AuditAction.PLATFORM_USER_DISABLED, "PLATFORM_USER", id,
                PlatformUserResponse.from(before), PlatformUserResponse.from(after), null);
        return PlatformUserResponse.from(after);
    }

    @Transactional(readOnly = true)
    public Optional<TenantStatus> tenantStatus(UUID tenantId) {
        if (tenantId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                select status from tenants where id = ?
                """, (rs, rowNum) -> TenantStatus.valueOf(rs.getString("status")), tenantId).stream().findFirst();
    }

    private TenantDetailResponse changeStatus(UUID tenantId, TenantStatus status, long version, String reason, Authentication authentication) {
        PlatformUserAccount actor = platformActor(authentication);
        TenantSummaryResponse before = summary(tenantId);
        if (before.version() != version) {
            throw new ConflictException("Tenant was modified by another transaction");
        }
        jdbcTemplate.update("""
                update tenants
                set status = ?, activated_at = case when ? = 'ACTIVE' then coalesce(activated_at, now()) else activated_at end,
                    updated_at = now(), version = version + 1
                where id = ? and version = ?
                """, status.name(), status.name(), tenantId, version);
        if (status == TenantStatus.SUSPENDED || status == TenantStatus.CLOSED || status == TenantStatus.REJECTED) {
            revokeTenantRefreshTokens(tenantId);
        }
        recordStatus(tenantId, before.status(), status, actor.id(), reason);
        TenantDetailResponse after = getTenant(tenantId);
        AuditAction action = switch (status) {
            case ACTIVE -> AuditAction.MERCHANT_REACTIVATED;
            case CLOSED -> AuditAction.MERCHANT_CLOSED;
            default -> AuditAction.MERCHANT_TENANT_UPDATED;
        };
        audit(actor.id(), action, "TENANT", tenantId, before, after.tenant(), reason);
        publishMerchantLifecycleNotification(after.tenant(), status, reason, actor.id());
        return after;
    }

    private void publishMerchantLifecycleNotification(TenantSummaryResponse tenant, TenantStatus status, String reason, UUID platformActorId) {
        if (tenant.primaryOwnerEmail() == null || tenant.primaryOwnerEmail().isBlank()) {
            return;
        }
        EmailTemplateCode templateCode;
        if (status == TenantStatus.SUSPENDED) {
            templateCode = EmailTemplateCode.MERCHANT_SUSPENDED;
        } else if (status == TenantStatus.ACTIVE) {
            templateCode = EmailTemplateCode.MERCHANT_REACTIVATED;
        } else {
            return;
        }
        eventPublisher.publishEvent(new MerchantNotificationEmailEvent(
                tenant.id(),
                tenant.tenantCode(),
                tenant.displayName(),
                tenant.primaryOwnerEmail(),
                templateCode,
                reason,
                platformActorId));
    }

    private void requireVersionIfProvided(TenantSummaryResponse tenant, Long version) {
        if (version != null && tenant.version() != version) {
            throw new ConflictException("Tenant was modified by another transaction");
        }
    }

    private void requireTransition(TenantSummaryResponse before, TenantStatus targetStatus, UUID actorId, String reason) {
        boolean allowed = switch (targetStatus) {
            case SUSPENDED -> before.status() == TenantStatus.PENDING_ONBOARDING
                    || before.status() == TenantStatus.PENDING_OWNER_ACTIVATION
                    || before.status() == TenantStatus.ACTIVE;
            case ACTIVE -> before.status() == TenantStatus.SUSPENDED;
            case CLOSED -> before.status() == TenantStatus.ACTIVE
                    || before.status() == TenantStatus.SUSPENDED;
            default -> false;
        };
        if (!allowed) {
            audit(actorId, AuditAction.INVALID_STATUS_TRANSITION_ATTEMPTED, "TENANT", before.id(),
                    Map.of("status", before.status()), Map.of("requestedStatus", targetStatus), reason);
            throw new BadRequestException("Invalid tenant status transition from " + before.status() + " to " + targetStatus);
        }
    }

    private static String lifecycleReason(PlatformDtos.LifecycleRequest request, String fallback) {
        String cleaned = cleanOptional(request.reason());
        return cleaned == null ? fallback : cleaned;
    }

    private static void requireMerchantConfirmation(TenantSummaryResponse tenant, String confirmation) {
        String supplied = cleanRequired(confirmation, "confirmation");
        if (!supplied.equals(tenant.tenantCode()) && !supplied.equals(tenant.displayName())) {
            throw new BadRequestException("confirmation must match merchant tenant code or display name");
        }
    }

    private TenantDeletionEligibilityResponse deletionEligibility(UUID tenantId) {
        TenantSummaryResponse tenant = summary(tenantId);
        List<TenantDeletionBlockerResponse> blockers = new ArrayList<>();
        if (!List.of(TenantStatus.PENDING_ONBOARDING, TenantStatus.PENDING_OWNER_ACTIVATION, TenantStatus.REJECTED).contains(tenant.status())) {
            blockers.add(new TenantDeletionBlockerResponse("STATUS_NOT_DELETABLE", 1,
                    "Only pending or rejected unused merchants can be deleted."));
        }
        addBlocker(blockers, "STORE_EXISTS", count("""
                select count(*) from stores where tenant_id = ?
                """, tenantId), "Merchant has existing stores.");
        addBlocker(blockers, "ACTIVE_USER_EXISTS", count("""
                select count(*) from security_users where tenant_id = ? and enabled = true
                """, tenantId), "Merchant has active users.");
        addBlocker(blockers, "NON_OWNER_USER_EXISTS", count("""
                select count(*)
                from security_users users
                where users.tenant_id = ?
                  and not exists (
                    select 1
                    from security_user_roles user_role
                    join security_roles role on role.id = user_role.role_id
                    where user_role.user_id = users.id
                      and role.name in ('TENANT_OWNER', 'OWNER')
                  )
                """, tenantId), "Merchant has employee or system tenant users.");
        addStoreBlocker(blockers, "REGISTER_EXISTS", "registers", "Merchant has registers.", tenantId);
        addStoreBlocker(blockers, "DEVICE_EXISTS", "devices", "Merchant has registered devices.", tenantId);
        addStoreBlocker(blockers, "SALE_EXISTS", "sales", "Merchant has sales that must be retained.", tenantId);
        addSqlBlocker(blockers, "PAYMENT_EXISTS", """
                select count(*)
                from payments payment
                join sales sale on sale.id = payment.sale_id
                join stores store on store.id = sale.store_id
                where store.tenant_id = ?
                """, "Merchant has payments that must be retained.", tenantId);
        addStoreBlocker(blockers, "REFUND_EXISTS", "refunds", "Merchant has refunds that must be retained.", tenantId);
        addStoreBlocker(blockers, "INVENTORY_TRANSACTION_EXISTS", "inventory_transactions", "Merchant has inventory transactions.", tenantId);
        addStoreBlocker(blockers, "REGISTER_SESSION_EXISTS", "register_sessions", "Merchant has register sessions.", tenantId);
        addStoreBlocker(blockers, "CASH_LEDGER_EXISTS", "cash_ledger_entries", "Merchant has cash-ledger records.", tenantId);
        addStoreBlocker(blockers, "CASH_MOVEMENT_EXISTS", "cash_movements", "Merchant has cash movements.", tenantId);
        addStoreBlocker(blockers, "LOTTERY_SALE_EXISTS", "lottery_sales", "Merchant has lottery sales.", tenantId);
        addStoreBlocker(blockers, "LOTTERY_PAYOUT_EXISTS", "lottery_payouts", "Merchant has lottery payouts.", tenantId);
        addStoreBlocker(blockers, "BUSINESS_DAY_EXISTS", "business_days", "Merchant has business-day records.", tenantId);
        addStoreBlocker(blockers, "END_OF_DAY_REPORT_EXISTS", "end_of_day_reports", "Merchant has end-of-day reports.", tenantId);
        addBlocker(blockers, "SUPPORT_SESSION_EXISTS", count("""
                select count(*) from support_access_sessions where tenant_id = ?
                """, tenantId), "Merchant has support access sessions.");
        String recommendedAction = blockers.isEmpty() ? "DELETE" : (tenant.status() == TenantStatus.SUSPENDED ? "CLOSE" : "SUSPEND_OR_CLOSE");
        return new TenantDeletionEligibilityResponse(blockers.isEmpty(), tenant.status(), List.copyOf(blockers), recommendedAction);
    }

    private void addStoreBlocker(List<TenantDeletionBlockerResponse> blockers, String type, String tableName, String message, UUID tenantId) {
        addSqlBlocker(blockers, type, "select count(*) from " + tableName + " record join stores store on store.id = record.store_id where store.tenant_id = ?",
                message, tenantId);
    }

    private void addSqlBlocker(List<TenantDeletionBlockerResponse> blockers, String type, String sql, String message, UUID tenantId) {
        addBlocker(blockers, type, count(sql, tenantId), message);
    }

    private static void addBlocker(List<TenantDeletionBlockerResponse> blockers, String type, long count, String message) {
        if (count > 0) {
            blockers.add(new TenantDeletionBlockerResponse(type, count, message));
        }
    }

    private CreatedOwnerInvitation createInvitation(UUID tenantId, UUID ownerUserId, String email, UUID actorId) {
        String rawToken = generateRawToken();
        Instant expiresAt = Instant.now().plus(properties.ownerInvitationExpiry());
        UUID invitationId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into tenant_owner_invitations (id, tenant_id, owner_user_id, email, token_hash, status, expires_at,
                                                      created_by_platform_user_id)
                values (?, ?, ?, ?, ?, 'PENDING', ?, ?)
                """, invitationId, tenantId, ownerUserId, normalizeEmail(email), hashToken(rawToken), timestamp(expiresAt), actorId);
        if (canExposeActivationLinks()) {
            DEVELOPMENT_INVITATION_TOKENS.put(invitationId, rawToken);
        }
        OwnerInviteResponse response = new OwnerInviteResponse(
                invitationId,
                tenantId,
                ownerUserId,
                normalizeEmail(email),
                "PENDING",
                expiresAt,
                null,
                canExposeActivationLinks() ? emailProperties.activationUrl(rawToken) : null,
                "PENDING",
                emailProperties.resolvedProvider().name());
        return new CreatedOwnerInvitation(response, rawToken);
    }

    private String developmentActivationUrl(UUID invitationId) {
        if (!canExposeActivationLinks() || invitationId == null) {
            return null;
        }
        String rawToken = DEVELOPMENT_INVITATION_TOKENS.get(invitationId);
        return rawToken == null || rawToken.isBlank() ? null : emailProperties.activationUrl(rawToken);
    }

    private boolean canExposeActivationLinks() {
        boolean developmentProfile = false;
        for (String profile : environment.getActiveProfiles()) {
            String normalized = profile.toLowerCase(Locale.ROOT);
            if (normalized.equals("prod") || normalized.equals("production")) {
                return false;
            }
            if (normalized.equals("dev") || normalized.equals("development") || normalized.equals("local")) {
                developmentProfile = true;
            }
        }
        return developmentProfile;
    }

    private void publishOwnerInvitationEmail(
            UUID tenantId,
            String tenantCode,
            String merchantOperatingName,
            String ownerName,
            CreatedOwnerInvitation invitation,
            EmailTemplateCode templateCode,
            UUID platformActorId,
            String reason,
            String notes) {
        eventPublisher.publishEvent(new OwnerInvitationEmailEvent(
                tenantId,
                tenantCode,
                blankToDefault(merchantOperatingName, tenantCode),
                invitation.response().invitationId(),
                invitation.response().email(),
                blankToDefault(ownerName, invitation.response().email()),
                invitation.rawToken(),
                invitation.response().expiresAt(),
                templateCode,
                platformActorId,
                reason,
                notes));
    }

    private void publishOwnerTemporaryCredentialsEmail(
            UUID tenantId,
            String tenantCode,
            String merchantOperatingName,
            String ownerName,
            UUID ownerUserId,
            String ownerEmail,
            String temporaryPassword,
            Instant expiresAt,
            EmailTemplateCode templateCode,
            UUID platformActorId,
            String reason,
            String notes) {
        eventPublisher.publishEvent(new OwnerTemporaryCredentialsEmailEvent(
                tenantId,
                tenantCode,
                blankToDefault(merchantOperatingName, tenantCode),
                ownerUserId,
                ownerEmail,
                blankToDefault(ownerName, ownerEmail),
                temporaryPassword,
                expiresAt,
                templateCode,
                platformActorId,
                reason,
                notes));
    }

    private void revokeFirstLoginPasswordChangeTokens(UUID ownerId, Instant revokedAt) {
        jdbcTemplate.update("""
                update first_login_password_change_tokens
                set revoked_at = ?, updated_at = now(), version = version + 1
                where user_id = ? and used_at is null and revoked_at is null
                """, timestamp(revokedAt == null ? Instant.now() : revokedAt), ownerId);
    }

    private static String ownerAccountStatus(Map<String, Object> owner) {
        if (!Boolean.TRUE.equals(owner.get("enabled"))) {
            return "DISABLED";
        }
        if (Boolean.TRUE.equals(owner.get("locked"))) {
            return "LOCKED";
        }
        if (Boolean.TRUE.equals(owner.get("password_change_required"))) {
            return "PENDING_FIRST_LOGIN";
        }
        return "ACTIVATED";
    }

    private static boolean temporaryCredentialsExpired(Map<String, Object> owner) {
        Instant expiresAt = instant(owner.get("temporary_password_expires_at"));
        return Boolean.TRUE.equals(owner.get("password_change_required"))
                && expiresAt != null
                && !expiresAt.isAfter(Instant.now());
    }

    private void advanceOnboarding(UUID tenantId, OnboardingStage nextStage, OnboardingStage completedStage) {
        UUID onboardingId = jdbcTemplate.queryForObject("select id from tenant_onboardings where tenant_id = ?", UUID.class, tenantId);
        completeStage(onboardingId, completedStage, Instant.now());
        jdbcTemplate.update("""
                update tenant_onboardings
                set current_stage = ?, completed_at = case when ? = 'COMPLETED' then now() else completed_at end,
                    updated_at = now(), version = version + 1
                where id = ?
                """, nextStage.name(), nextStage.name(), onboardingId);
    }

    private void completeStage(UUID onboardingId, OnboardingStage stage, Instant completedAt) {
        jdbcTemplate.update("""
                insert into tenant_onboarding_stages (id, tenant_onboarding_id, stage, completed_at)
                values (?, ?, ?, ?)
                on conflict (tenant_onboarding_id, stage)
                do update set completed_at = excluded.completed_at, updated_at = now(), version = tenant_onboarding_stages.version + 1
                """, UUID.randomUUID(), onboardingId, stage.name(), timestamp(completedAt));
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new IllegalStateException("Unsupported timestamp value: " + value);
    }

    private void recordStatus(UUID tenantId, TenantStatus oldStatus, TenantStatus newStatus, UUID actorId, String reason) {
        recordStatus(tenantId, oldStatus, newStatus, actorId, reason, null);
    }

    private void recordStatus(UUID tenantId, TenantStatus oldStatus, TenantStatus newStatus, UUID actorId, String reason, String notes) {
        jdbcTemplate.update("""
                insert into tenant_status_history (id, tenant_id, tenant_code_snapshot, old_status, new_status,
                                                   changed_by_platform_user_id, reason, notes)
                values (?, ?, (select tenant_code from tenants where id = ?), ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), tenantId, tenantId, oldStatus == null ? null : oldStatus.name(), newStatus.name(), actorId,
                cleanOptional(reason), cleanOptional(notes));
    }

    private void revokeTenantRefreshTokens(UUID tenantId) {
        jdbcTemplate.update("""
                update security_refresh_tokens refresh
                set revoked_at = now(), updated_at = now(), version = refresh.version + 1
                from security_users users
                where refresh.user_id = users.id
                  and users.tenant_id = ?
                  and refresh.revoked_at is null
                  and refresh.expires_at > now()
                """, tenantId);
    }

    private void assignRole(UUID userId, RoleName role) {
        UUID roleId = jdbcTemplate.queryForObject("select id from security_roles where name = ?", UUID.class, role.name());
        jdbcTemplate.update("""
                insert into security_user_roles (id, user_id, role_id)
                values (?, ?, ?)
                on conflict (user_id, role_id) do nothing
                """, UUID.randomUUID(), userId, roleId);
    }

    private PlatformUserAccount platformActor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ForbiddenOperationException("Platform authentication is required");
        }
        return platformUserRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ForbiddenOperationException("Platform authentication is required"));
    }

    private TenantSummaryResponse summary(UUID tenantId) {
        return jdbcTemplate.queryForObject("select * from platform_tenant_summary where id = ?", tenantSummaryMapper(), tenantId);
    }

    private Map<String, Object> tenant(UUID tenantId) {
        return jdbcTemplate.queryForMap("select * from tenants where id = ?", tenantId);
    }

    private PlatformDtos.MerchantProfileResponse merchantProfile(UUID tenantId) {
        return jdbcTemplate.queryForObject("""
                select id, tenant_id, legal_business_name, operating_name, business_number, contact_name, contact_email,
                       contact_phone, billing_address, country_code, administrative_division_code, postal_code,
                       default_currency_code, primary_timezone, default_tax_region_code,
                       industry_type, estimated_store_count, notes, version
                from merchant_profiles
                where tenant_id = ?
                """, (rs, rowNum) -> new PlatformDtos.MerchantProfileResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("legal_business_name"),
                rs.getString("operating_name"),
                rs.getString("business_number"),
                rs.getString("contact_name"),
                rs.getString("contact_email"),
                rs.getString("contact_phone"),
                rs.getString("billing_address"),
                rs.getString("country_code"),
                rs.getString("administrative_division_code"),
                rs.getString("default_currency_code"),
                rs.getString("primary_timezone"),
                rs.getString("default_tax_region_code"),
                rs.getString("postal_code"),
                rs.getString("industry_type"),
                (Integer) rs.getObject("estimated_store_count"),
                rs.getString("notes"),
                rs.getLong("version")), tenantId);
    }

    private SubscriptionResponse subscription(UUID tenantId) {
        return jdbcTemplate.queryForObject("""
                select id, tenant_id, plan_code, status, starts_at, trial_ends_at, renews_at, cancelled_at,
                       maximum_stores, maximum_users, features, version
                from tenant_subscriptions
                where tenant_id = ?
                """, (rs, rowNum) -> new SubscriptionResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("plan_code"),
                rs.getString("status"),
                instant(rs.getObject("starts_at")),
                instant(rs.getObject("trial_ends_at")),
                instant(rs.getObject("renews_at")),
                instant(rs.getObject("cancelled_at")),
                (Integer) rs.getObject("maximum_stores"),
                (Integer) rs.getObject("maximum_users"),
                features(rs.getString("features")),
                rs.getLong("version")), tenantId);
    }

    private RowMapper<TenantSummaryResponse> tenantSummaryMapper() {
        return (rs, rowNum) -> new TenantSummaryResponse(
                rs.getObject("id", UUID.class),
                rs.getString("tenant_code"),
                rs.getString("legal_name"),
                rs.getString("display_name"),
                TenantStatus.valueOf(rs.getString("status")),
                rs.getString("country_code"),
                rs.getString("administrative_division_code"),
                rs.getString("default_currency_code"),
                rs.getString("primary_timezone"),
                rs.getString("default_tax_region_code"),
                rs.getString("primary_owner_email"),
                rs.getString("subscription_plan"),
                OnboardingStage.valueOf(rs.getString("onboarding_stage")),
                rs.getLong("store_count"),
                rs.getLong("user_count"),
                instant(rs.getObject("created_at")),
                instant(rs.getObject("activated_at")),
                instant(rs.getObject("suspended_at")),
                rs.getObject("suspended_by_platform_user_id", UUID.class),
                rs.getString("suspension_reason"),
                instant(rs.getObject("closed_at")),
                rs.getObject("closed_by_platform_user_id", UUID.class),
                rs.getString("closure_reason"),
                instant(rs.getObject("reactivated_at")),
                rs.getObject("reactivated_by_platform_user_id", UUID.class),
                rs.getLong("version"));
    }

    private long count(String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0 : count;
    }

    private long count(String sql, Object... args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
        return count == null ? 0 : count;
    }

    private String invitationStatus(Map<String, Object> invitation, boolean ownerEnabled) {
        if (ownerEnabled) {
            return "USED";
        }
        if (invitation == null) {
            return "PENDING";
        }
        String status = (String) invitation.get("status");
        if (List.of("PENDING", "SENT").contains(status) && !instant(invitation.get("expires_at")).isAfter(Instant.now())) {
            return "EXPIRED";
        }
        return status;
    }

    private Instant activationCompletedAt(UUID tenantId, UUID ownerId) {
        return jdbcTemplate.query("""
                select accepted_at from tenant_owner_invitations
                where tenant_id = ? and owner_user_id = ? and status = 'USED'
                order by accepted_at desc nulls last, updated_at desc
                limit 1
                """, (rs, rowNum) -> instant(rs.getObject("accepted_at")), tenantId, ownerId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private RowMapper<TenantStatusHistoryResponse> statusHistoryMapper() {
        return (rs, rowNum) -> new TenantStatusHistoryResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("tenant_code_snapshot"),
                rs.getString("old_status") == null ? null : TenantStatus.valueOf(rs.getString("old_status")),
                TenantStatus.valueOf(rs.getString("new_status")),
                rs.getString("reason"),
                rs.getString("notes"),
                rs.getObject("changed_by_platform_user_id", UUID.class),
                instant(rs.getObject("created_at")),
                rs.getString("correlation_id"));
    }

    private String nextTenantCode(String displayName) {
        String prefix = cleanRequired(displayName, "operatingName")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (prefix.isBlank()) {
            prefix = "MERCHANT";
        }
        prefix = prefix.substring(0, Math.min(prefix.length(), 24));
        String candidate = prefix;
        int suffix = 1;
        while (count("select count(*) from tenants where tenant_code = '" + candidate.replace("'", "''") + "'") > 0) {
            candidate = prefix + "-" + suffix++;
        }
        return candidate;
    }

    private void validateSelectedStoreCapabilities(UUID pricingVersionId, Set<com.merchtyl.store.StoreCapability> requested) {
        Set<com.merchtyl.store.StoreCapability> capabilities = requested == null
                ? Set.of(com.merchtyl.store.StoreCapability.RETAIL)
                : requested;
        if (capabilities.isEmpty()) throw new BadRequestException("At least one store capability is required");
        Map<String, String> configured = jdbcTemplate.query("""
                select capability,inclusion_type from platform_pricing_plan_version_capabilities
                where pricing_plan_version_id=?
                """, rs -> {
            Map<String, String> values = new HashMap<>();
            while (rs.next()) values.put(rs.getString(1), rs.getString(2));
            return values;
        }, pricingVersionId);
        for (com.merchtyl.store.StoreCapability capability : capabilities) {
            String commercialCapability = capability == com.merchtyl.store.StoreCapability.RETAIL
                    ? "RETAIL_POS" : capability.name();
            if (!Set.of("INCLUDED", "PAID_ADD_ON").contains(configured.get(commercialCapability))) {
                throw new BadRequestException("PLAN_CAPABILITY_NOT_AVAILABLE: " + commercialCapability + " is not available on the selected pricing plan");
            }
        }
    }

    private void validateOnboardingUniqueness(String tenantCode, String ownerEmail) {
        if (Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select exists(select 1 from tenants where tenant_code=?)", Boolean.class, tenantCode))) {
            throw new ConflictException("TENANT_CODE_ALREADY_EXISTS: A merchant with this tenant code already exists");
        }
        if (Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select exists(select 1 from security_users where lower(email)=lower(?))", Boolean.class, ownerEmail))) {
            throw new ConflictException("OWNER_EMAIL_ALREADY_EXISTS: A user with this email already exists");
        }
    }

    private String requestedTenantCode(MerchantOnboardingRequest request) {
        String supplied = cleanOptional(request.tenantCode());
        if (supplied == null) {
            return nextTenantCode(request.operatingName());
        }
        String code = supplied.toUpperCase(Locale.ROOT);
        if (!code.matches("^[A-Z0-9][A-Z0-9_-]{1,63}$")) {
            throw new BadRequestException("tenantCode may contain only letters, numbers, underscores, and hyphens");
        }
        return code;
    }

    private StoreGeographySelection validateMerchantGeography(
            String countryCode,
            String administrativeDivisionCode,
            String currencyCode,
            String timezone,
            String taxRegionCode,
            String overrideReason,
            Authentication authentication) {
        try {
            StoreGeographySelection geography = referenceDataService.validateStoreGeography(
                    countryCode,
                    administrativeDivisionCode,
                    currencyCode,
                    timezone,
                    taxRegionCode,
                    hasPlatformPermission(authentication, PermissionCode.TENANT_CURRENCY_OVERRIDE),
                    PermissionCode.TENANT_CURRENCY_OVERRIDE.name());
            if (currencyOverrideUsed(geography) && cleanOptional(overrideReason) == null) {
                throw new BadRequestException("currencyOverrideReason is required when overriding the default country currency");
            }
            return geography;
        } catch (BadRequestException exception) {
            if (authentication != null && authentication.getName() != null) {
                platformUserRepository.findByEmail(authentication.getName())
                        .ifPresent(actor -> audit(actor.id(), AuditAction.INVALID_MERCHANT_GEOGRAPHY_ATTEMPTED, "TENANT", null, null, null, exception.getMessage()));
            }
            throw exception;
        }
    }

    private boolean hasPlatformPermission(Authentication authentication, PermissionCode permission) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(authority -> "ACCOUNT_SCOPE_PLATFORM".equals(authority.getAuthority()))
                && authentication.getAuthorities().stream().anyMatch(authority -> permission.name().equals(authority.getAuthority()));
    }

    private static boolean currencyOverrideUsed(StoreGeographySelection geography) {
        return geography.country().getDefaultCurrency() != null
                && !geography.country().getDefaultCurrency().getCode().equalsIgnoreCase(geography.currency().getCode());
    }

    private static String ownerFirstName(MerchantOnboardingRequest request) {
        return request.owner() == null ? request.ownerFirstName() : request.owner().firstName();
    }

    private static String ownerLastName(MerchantOnboardingRequest request) {
        return request.owner() == null ? request.ownerLastName() : request.owner().lastName();
    }

    private static String ownerEmail(MerchantOnboardingRequest request) {
        return request.owner() == null ? request.ownerEmail() : request.owner().email();
    }

    private static String ownerPhone(MerchantOnboardingRequest request) {
        return request.owner() == null ? request.ownerPhone() : request.owner().phone();
    }

    private void auditMerchantGeographyChanges(UUID actorId, UUID tenantId, TenantSummaryResponse before, TenantSummaryResponse after, String reason) {
        if (!java.util.Objects.equals(before.countryCode(), after.countryCode())) {
            audit(actorId, AuditAction.MERCHANT_COUNTRY_CHANGED, "TENANT", tenantId, before.countryCode(), after.countryCode(), reason);
        }
        if (!java.util.Objects.equals(before.administrativeDivisionCode(), after.administrativeDivisionCode())) {
            audit(actorId, AuditAction.MERCHANT_PROVINCE_STATE_CHANGED, "TENANT", tenantId, before.administrativeDivisionCode(), after.administrativeDivisionCode(), reason);
        }
        if (!java.util.Objects.equals(before.defaultCurrencyCode(), after.defaultCurrencyCode())) {
            audit(actorId, AuditAction.MERCHANT_DEFAULT_CURRENCY_CHANGED, "TENANT", tenantId, before.defaultCurrencyCode(), after.defaultCurrencyCode(), reason);
            audit(actorId, AuditAction.CURRENCY_OVERRIDE_USED, "TENANT", tenantId, before.defaultCurrencyCode(), after.defaultCurrencyCode(), reason);
        }
        if (!java.util.Objects.equals(before.primaryTimezone(), after.primaryTimezone())) {
            audit(actorId, AuditAction.MERCHANT_TIMEZONE_CHANGED, "TENANT", tenantId, before.primaryTimezone(), after.primaryTimezone(), reason);
        }
        if (!java.util.Objects.equals(before.defaultTaxRegionCode(), after.defaultTaxRegionCode())) {
            audit(actorId, AuditAction.MERCHANT_DEFAULT_TAX_REGION_CHANGED, "TENANT", tenantId, before.defaultTaxRegionCode(), after.defaultTaxRegionCode(), reason);
        }
    }

    private void audit(UUID actorId, AuditAction action, String entityType, UUID entityId, Object before, Object after, String reason) {
        auditService.record(new CreateAuditRecordCommand(actorId, action, entityType, entityId, null, null, before, after, reason));
    }

    private Map<String, Boolean> features(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, FEATURE_MAP);
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private String featuresJson(Map<String, Boolean> features) {
        try {
            return objectMapper.writeValueAsString(features == null ? Map.of() : features);
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("features must be valid JSON");
        }
    }

    private static void requirePlatformRole(RoleName role) {
        if (role != RoleName.PLATFORM_SUPER_ADMIN && role != RoleName.PLATFORM_SUPPORT_ADMIN) {
            throw new BadRequestException("role must be a platform role");
        }
    }

    private PasswordPolicyService passwordPolicy() {
        return passwordPolicyService == null ? new PasswordPolicyService() : passwordPolicyService;
    }

    private static String normalizeSubscriptionStatus(String status) {
        String normalized = normalizeCode(status, "status");
        if (!List.of("TRIAL", "ACTIVE", "PAST_DUE", "SUSPENDED", "CANCELLED").contains(normalized)) {
            throw new BadRequestException("status must be a valid subscription status");
        }
        return normalized;
    }

    private static String normalizeCode(String value, String field) {
        return cleanRequired(value, field).toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static String normalizeEmail(String email) {
        return cleanRequired(email, "email").toLowerCase(Locale.ROOT);
    }

    private static String normalizeCountry(String value) {
        String country = cleanRequired(value, "countryCode").toUpperCase(Locale.ROOT);
        if (!country.matches("^[A-Z]{2}$")) {
            throw new BadRequestException("countryCode must be an ISO 3166-1 alpha-2 code");
        }
        return country;
    }

    private static String normalizeCurrency(String value) {
        String currency = cleanRequired(value, "defaultCurrencyCode").toUpperCase(Locale.ROOT);
        if (!currency.matches("^[A-Z]{3}$")) {
            throw new BadRequestException("defaultCurrencyCode must be an ISO 4217 code");
        }
        return currency;
    }

    private static String cleanOptionalUpper(String value) {
        String cleaned = cleanOptional(value);
        return cleaned == null ? null : cleaned.toUpperCase(Locale.ROOT);
    }

    private static String cleanOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String cleanRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(field + " is required");
        }
        return value.trim();
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }

    private static BadCredentialsException badCredentials() {
        return new BadCredentialsException("Invalid email or password");
    }

    private record CreatedOwnerInvitation(OwnerInviteResponse response, String rawToken) {
    }
}
