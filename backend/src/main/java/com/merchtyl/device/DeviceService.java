package com.merchtyl.device;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.common.PageResponse;
import com.merchtyl.register.Register;
import com.merchtyl.register.RegisterRepository;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import com.merchtyl.store.Store;
import com.merchtyl.store.StoreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class DeviceService {
    private static final int MAX_PAGE_SIZE = 100;

    private final DeviceRepository deviceRepository;
    private final StoreRepository storeRepository;
    private final RegisterRepository registerRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final Clock clock;

    @Autowired
    public DeviceService(
            DeviceRepository deviceRepository,
            StoreRepository storeRepository,
            RegisterRepository registerRepository,
            UserRepository userRepository,
            AuditService auditService) {
        this(
                deviceRepository,
                storeRepository,
                registerRepository,
                userRepository,
                auditService,
                Clock.systemUTC());
    }

    DeviceService(
            DeviceRepository deviceRepository,
            StoreRepository storeRepository,
            RegisterRepository registerRepository,
            UserRepository userRepository,
            AuditService auditService,
            Clock clock) {
        this.deviceRepository = deviceRepository;
        this.storeRepository = storeRepository;
        this.registerRepository = registerRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public DeviceResponse register(DeviceRegisterRequest request, Authentication authentication) {
        DeviceValues values = values(request);
        if (deviceRepository.existsByDeviceIdentifierIgnoreCase(values.deviceIdentifier())) {
            throw duplicateIdentifier();
        }

        Instant now = Instant.now(clock);
        Device device = new Device(
                values.store(),
                values.register(),
                values.deviceIdentifier(),
                values.displayName(),
                values.deviceType(),
                true,
                now);
        DeviceResponse response = DeviceResponse.from(save(device));
        audit(authentication, AuditAction.DEVICE_REGISTERED, response.storeId(), response.registerId(), response.id(), null, response, null);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<DeviceResponse> search(DeviceSearchRequest request) {
        int pageNumber = Math.max(0, request.page());
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, request.size()));
        var pageable = PageRequest.of(pageNumber, pageSize,
                Sort.by(Sort.Direction.DESC, "lastSeenAt").and(Sort.by(Sort.Direction.DESC, "id")));
        var page = deviceRepository.findAll(specification(request), pageable);
        return new PageResponse<>(
                page.getContent().stream().map(DeviceResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    @Transactional(readOnly = true)
    public DeviceResponse get(UUID id) {
        return DeviceResponse.from(find(id));
    }

    @Transactional
    public DeviceResponse update(UUID id, DeviceUpdateRequest request, Authentication authentication) {
        Device device = find(id);
        requireCurrentVersion(device, request.version());
        DeviceValues values = values(request);
        if (deviceRepository.existsByDeviceIdentifierIgnoreCaseAndIdNot(values.deviceIdentifier(), id)) {
            throw duplicateIdentifier();
        }

        DeviceResponse before = DeviceResponse.from(device);
        device.update(values);
        DeviceResponse after = DeviceResponse.from(save(device));
        audit(authentication, AuditAction.DEVICE_UPDATED, after.storeId(), after.registerId(), id, before, after, null);
        return after;
    }

    @Transactional
    public DeviceResponse updateStatus(UUID id, DeviceStatusRequest request, Authentication authentication) {
        Device device = find(id);
        requireCurrentVersion(device, request.version());

        DeviceResponse before = DeviceResponse.from(device);
        device.setActive(request.active());
        DeviceResponse after = DeviceResponse.from(save(device));
        audit(authentication, AuditAction.DEVICE_STATUS_CHANGED, after.storeId(), after.registerId(), id, before, after, null);
        return after;
    }

    @Transactional
    public DeviceResponse heartbeat(UUID id) {
        Device device = find(id);
        device.touch(Instant.now(clock));
        return DeviceResponse.from(save(device));
    }

    private Device save(Device device) {
        try {
            return deviceRepository.saveAndFlush(device);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateIdentifier();
        }
    }

    private Device find(UUID id) {
        return deviceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Device not found"));
    }

    private DeviceValues values(DeviceRegisterRequest request) {
        Store store = findStore(request.storeId());
        Register register = findRegister(request.registerId());
        validateRegisterStore(store, register);
        return new DeviceValues(
                store,
                register,
                normalizeDeviceIdentifier(request.deviceIdentifier()),
                cleanRequired(request.displayName(), "displayName"),
                normalizeDeviceType(request.deviceType()),
                true);
    }

    private DeviceValues values(DeviceUpdateRequest request) {
        Store store = findStore(request.storeId());
        Register register = findRegister(request.registerId());
        validateRegisterStore(store, register);
        return new DeviceValues(
                store,
                register,
                normalizeDeviceIdentifier(request.deviceIdentifier()),
                cleanRequired(request.displayName(), "displayName"),
                normalizeDeviceType(request.deviceType()),
                request.active());
    }

    private Store findStore(UUID storeId) {
        if (storeId == null) {
            throw new BadRequestException("storeId is required");
        }
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new NotFoundException("Store not found"));
    }

    private Register findRegister(UUID registerId) {
        if (registerId == null) {
            throw new BadRequestException("registerId is required");
        }
        return registerRepository.findById(registerId)
                .orElseThrow(() -> new NotFoundException("Register not found"));
    }

    private void validateRegisterStore(Store store, Register register) {
        if (!register.getStore().getId().equals(store.getId())) {
            throw new BadRequestException("registerId must belong to storeId");
        }
    }

    private Specification<Device> specification(DeviceSearchRequest request) {
        return Specification
                .where(equalAssociation("store", request.storeId()))
                .and(equalAssociation("register", request.registerId()))
                .and(equalString("deviceIdentifier", normalizeDeviceIdentifierFilter(request.deviceIdentifier())))
                .and(containsString("displayName", request.displayName()))
                .and(equalString("deviceType", normalizeDeviceTypeFilter(request.deviceType())))
                .and(equalBoolean("active", request.active()));
    }

    private void requireCurrentVersion(Device device, Long requestedVersion) {
        if (requestedVersion == null || requestedVersion != device.getVersion()) {
            throw new ConflictException("Device was modified by another transaction");
        }
    }

    private void audit(
            Authentication authentication,
            AuditAction action,
            UUID storeId,
            UUID registerId,
            UUID deviceId,
            DeviceResponse beforeSnapshot,
            DeviceResponse afterSnapshot,
            String reason) {
        auditService.record(new CreateAuditRecordCommand(
                actorUserId(authentication),
                action,
                "DEVICE",
                deviceId,
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

    private static Specification<Device> equalAssociation(String field, UUID id) {
        if (id == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field).get("id"), id);
    }

    private static Specification<Device> equalString(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private static Specification<Device> containsString(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, criteriaBuilder) -> criteriaBuilder.like(
                criteriaBuilder.lower(root.get(field)),
                pattern);
    }

    private static Specification<Device> equalBoolean(String field, Boolean value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(field), value);
    }

    private static String normalizeDeviceIdentifier(String value) {
        String identifier = cleanRequired(value, "deviceIdentifier");
        if (!identifier.matches("^[A-Za-z0-9][A-Za-z0-9_.:-]*$")) {
            throw new BadRequestException("deviceIdentifier may contain only letters, numbers, underscores, hyphens, periods, and colons");
        }
        return identifier;
    }

    private static String normalizeDeviceIdentifierFilter(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeDeviceType(String value) {
        String deviceType = cleanRequired(value, "deviceType").toUpperCase(Locale.ROOT);
        if (!deviceType.matches("^[A-Z0-9_-]+$")) {
            throw new BadRequestException("deviceType may contain only letters, numbers, underscores, and hyphens");
        }
        return deviceType;
    }

    private static String normalizeDeviceTypeFilter(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String cleanRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value.trim();
    }

    private static ConflictException duplicateIdentifier() {
        return new ConflictException("Device identifier already exists");
    }
}
