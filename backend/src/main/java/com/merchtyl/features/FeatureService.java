package com.merchtyl.features;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.register.Register;
import com.merchtyl.register.RegisterRepository;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FeatureService {
    private final FeatureDefinitionRepository featureDefinitionRepository;
    private final TenantFeatureRepository tenantFeatureRepository;
    private final StoreFeatureRepository storeFeatureRepository;
    private final RegisterFeatureRepository registerFeatureRepository;
    private final StoreRepository storeRepository;
    private final RegisterRepository registerRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public FeatureService(
            FeatureDefinitionRepository featureDefinitionRepository,
            TenantFeatureRepository tenantFeatureRepository,
            StoreFeatureRepository storeFeatureRepository,
            RegisterFeatureRepository registerFeatureRepository,
            StoreRepository storeRepository,
            RegisterRepository registerRepository,
            UserRepository userRepository,
            AuditService auditService) {
        this.featureDefinitionRepository = featureDefinitionRepository;
        this.tenantFeatureRepository = tenantFeatureRepository;
        this.storeFeatureRepository = storeFeatureRepository;
        this.registerFeatureRepository = registerFeatureRepository;
        this.storeRepository = storeRepository;
        this.registerRepository = registerRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<FeatureDefinitionResponse> listDefinitions() {
        return definitions().stream().map(FeatureDefinitionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<FeatureResolutionResponse> resolve(UUID storeId, UUID registerId) {
        Scope scope = scope(storeId, registerId);
        List<FeatureDefinition> definitions = definitions();
        Map<FeatureCode, TenantFeature> tenantOverrides = tenantFeatureRepository
                .findByFeatureDefinitionIn(definitions)
                .stream()
                .collect(Collectors.toMap(feature -> feature.getFeatureDefinition().getCode(), Function.identity()));
        Map<FeatureCode, StoreFeature> storeOverrides = scope.store() == null
                ? Map.of()
                : storeFeatureRepository.findByStore_IdAndFeatureDefinitionIn(scope.store().getId(), definitions)
                        .stream()
                        .collect(Collectors.toMap(feature -> feature.getFeatureDefinition().getCode(), Function.identity()));
        Map<FeatureCode, RegisterFeature> registerOverrides = scope.register() == null
                ? Map.of()
                : registerFeatureRepository.findByRegister_IdAndFeatureDefinitionIn(scope.register().getId(), definitions)
                        .stream()
                        .collect(Collectors.toMap(feature -> feature.getFeatureDefinition().getCode(), Function.identity()));

        return definitions.stream()
                .map(definition -> resolution(
                        definition,
                        scope,
                        tenantOverrides.get(definition.getCode()),
                        storeOverrides.get(definition.getCode()),
                        registerOverrides.get(definition.getCode())))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(FeatureCode code, UUID storeId, UUID registerId) {
        return resolve(storeId, registerId).stream()
                .filter(resolution -> resolution.definition().code() == code)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Feature definition not found"))
                .enabled();
    }

    @Transactional(readOnly = true)
    public void requireEnabled(FeatureCode code, UUID storeId, UUID registerId) {
        if (!isEnabled(code, storeId, registerId)) {
            throw new ForbiddenOperationException("Feature is disabled: " + code.name());
        }
    }

    @Transactional
    public FeatureResolutionResponse updateDeployment(
            FeatureCode code,
            FeatureOverrideRequest request,
            Authentication authentication) {
        FeatureDefinition definition = definition(code);
        TenantFeature existing = tenantFeatureRepository.findByFeatureDefinition(definition).orElse(null);
        if (request.enabled() == null) {
            if (existing == null) {
                return resolve(null, null, definition);
            }
            requireCurrentVersion(existing.getVersion(), request.version(), "Feature override was modified by another transaction");
            FeatureOverrideResponse before = FeatureOverrideResponse.from(existing);
            tenantFeatureRepository.delete(existing);
            tenantFeatureRepository.flush();
            audit(authentication, AuditAction.FEATURE_OVERRIDE_REMOVED, "TENANT_FEATURE", existing.getId(), null, null, before, null, code.name());
            return resolve(null, null, definition);
        }
        TenantFeature feature = existing;
        FeatureOverrideResponse before = existing == null ? null : FeatureOverrideResponse.from(existing);
        if (feature == null) {
            requireNoVersion(request.version());
            feature = new TenantFeature(definition, request.enabled());
        } else {
            requireCurrentVersion(feature.getVersion(), request.version(), "Feature override was modified by another transaction");
            feature.setEnabled(request.enabled());
        }
        TenantFeature saved = tenantFeatureRepository.saveAndFlush(feature);
        FeatureOverrideResponse after = FeatureOverrideResponse.from(saved);
        audit(authentication, AuditAction.FEATURE_OVERRIDE_UPDATED, "TENANT_FEATURE", saved.getId(), null, null, before, after, code.name());
        return resolve(null, null, definition);
    }

    @Transactional
    public FeatureResolutionResponse updateStore(
            FeatureCode code,
            UUID storeId,
            FeatureOverrideRequest request,
            Authentication authentication) {
        FeatureDefinition definition = definition(code);
        Store store = store(storeId);
        StoreFeature existing = storeFeatureRepository.findByStore_IdAndFeatureDefinition(storeId, definition).orElse(null);
        if (request.enabled() == null) {
            if (existing == null) {
                return resolve(store.getId(), null, definition);
            }
            requireCurrentVersion(existing.getVersion(), request.version(), "Feature override was modified by another transaction");
            FeatureOverrideResponse before = FeatureOverrideResponse.from(existing);
            storeFeatureRepository.delete(existing);
            storeFeatureRepository.flush();
            audit(authentication, AuditAction.FEATURE_OVERRIDE_REMOVED, "STORE_FEATURE", existing.getId(), store.getId(), null, before, null, code.name());
            return resolve(store.getId(), null, definition);
        }
        StoreFeature feature = existing;
        FeatureOverrideResponse before = existing == null ? null : FeatureOverrideResponse.from(existing);
        if (feature == null) {
            requireNoVersion(request.version());
            feature = new StoreFeature(store, definition, request.enabled());
        } else {
            requireCurrentVersion(feature.getVersion(), request.version(), "Feature override was modified by another transaction");
            feature.setEnabled(request.enabled());
        }
        StoreFeature saved = storeFeatureRepository.saveAndFlush(feature);
        FeatureOverrideResponse after = FeatureOverrideResponse.from(saved);
        audit(authentication, AuditAction.FEATURE_OVERRIDE_UPDATED, "STORE_FEATURE", saved.getId(), store.getId(), null, before, after, code.name());
        return resolve(store.getId(), null, definition);
    }

    @Transactional
    public FeatureResolutionResponse updateRegister(
            FeatureCode code,
            UUID registerId,
            FeatureOverrideRequest request,
            Authentication authentication) {
        FeatureDefinition definition = definition(code);
        Register register = register(registerId);
        RegisterFeature existing = registerFeatureRepository.findByRegister_IdAndFeatureDefinition(registerId, definition).orElse(null);
        if (request.enabled() == null) {
            if (existing == null) {
                return resolve(register.getStore().getId(), register.getId(), definition);
            }
            requireCurrentVersion(existing.getVersion(), request.version(), "Feature override was modified by another transaction");
            FeatureOverrideResponse before = FeatureOverrideResponse.from(existing);
            registerFeatureRepository.delete(existing);
            registerFeatureRepository.flush();
            audit(authentication, AuditAction.FEATURE_OVERRIDE_REMOVED, "REGISTER_FEATURE", existing.getId(), register.getStore().getId(), register.getId(), before, null, code.name());
            return resolve(register.getStore().getId(), register.getId(), definition);
        }
        RegisterFeature feature = existing;
        FeatureOverrideResponse before = existing == null ? null : FeatureOverrideResponse.from(existing);
        if (feature == null) {
            requireNoVersion(request.version());
            feature = new RegisterFeature(register, definition, request.enabled());
        } else {
            requireCurrentVersion(feature.getVersion(), request.version(), "Feature override was modified by another transaction");
            feature.setEnabled(request.enabled());
        }
        RegisterFeature saved = registerFeatureRepository.saveAndFlush(feature);
        FeatureOverrideResponse after = FeatureOverrideResponse.from(saved);
        audit(authentication, AuditAction.FEATURE_OVERRIDE_UPDATED, "REGISTER_FEATURE", saved.getId(), register.getStore().getId(), register.getId(), before, after, code.name());
        return resolve(register.getStore().getId(), register.getId(), definition);
    }

    private FeatureResolutionResponse resolve(UUID storeId, UUID registerId, FeatureDefinition definition) {
        return resolve(storeId, registerId).stream()
                .filter(resolution -> resolution.definition().code() == definition.getCode())
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Feature definition not found"));
    }

    private FeatureResolutionResponse resolution(
            FeatureDefinition definition,
            Scope scope,
            TenantFeature tenantOverride,
            StoreFeature storeOverride,
            RegisterFeature registerOverride) {
        boolean enabled = definition.isDefaultEnabled();
        FeatureResolutionSource source = FeatureResolutionSource.DEFAULT;
        if (tenantOverride != null) {
            enabled = tenantOverride.isEnabled();
            source = FeatureResolutionSource.TENANT;
        }
        if (storeOverride != null) {
            enabled = storeOverride.isEnabled();
            source = FeatureResolutionSource.STORE;
        }
        if (registerOverride != null) {
            enabled = registerOverride.isEnabled();
            source = FeatureResolutionSource.REGISTER;
        }
        return new FeatureResolutionResponse(
                FeatureDefinitionResponse.from(definition),
                enabled,
                source,
                scope.store() == null ? null : scope.store().getId(),
                scope.register() == null ? null : scope.register().getId(),
                tenantOverride == null ? null : FeatureOverrideResponse.from(tenantOverride),
                storeOverride == null ? null : FeatureOverrideResponse.from(storeOverride),
                registerOverride == null ? null : FeatureOverrideResponse.from(registerOverride));
    }

    private List<FeatureDefinition> definitions() {
        return featureDefinitionRepository.findAllByOrderByCodeAsc()
                .stream()
                .sorted(Comparator.comparing(definition -> definition.getCode().name()))
                .toList();
    }

    private FeatureDefinition definition(FeatureCode code) {
        return featureDefinitionRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("Feature definition not found: " + code.name()));
    }

    private Scope scope(UUID storeId, UUID registerId) {
        Store store = storeId == null ? null : store(storeId);
        Register register = registerId == null ? null : register(registerId);
        if (register != null) {
            if (store != null && !register.getStore().getId().equals(store.getId())) {
                throw new BadRequestException("Register does not belong to store");
            }
            store = register.getStore();
        }
        return new Scope(store, register);
    }

    private Store store(UUID id) {
        return storeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Store not found"));
    }

    private Register register(UUID id) {
        return registerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Register not found"));
    }

    private void requireNoVersion(Long requestedVersion) {
        if (requestedVersion != null) {
            throw new ConflictException("Feature override does not exist");
        }
    }

    private void requireCurrentVersion(long currentVersion, Long requestedVersion, String message) {
        if (requestedVersion == null || requestedVersion != currentVersion) {
            throw new ConflictException(message);
        }
    }

    private void audit(
            Authentication authentication,
            AuditAction action,
            String entityType,
            UUID entityId,
            UUID storeId,
            UUID registerId,
            Object beforeSnapshot,
            Object afterSnapshot,
            String reason) {
        auditService.record(new CreateAuditRecordCommand(
                actorUserId(authentication),
                action,
                entityType,
                entityId,
                storeId,
                registerId,
                beforeSnapshot,
                afterSnapshot,
                reason));
    }

    private UUID actorUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .map(User::getId)
                .orElse(null);
    }

    private record Scope(Store store, Register register) {
    }
}
