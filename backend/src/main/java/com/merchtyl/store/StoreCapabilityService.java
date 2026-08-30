package com.merchtyl.store;

import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.security.StoreAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class StoreCapabilityService {
    private final StoreRepository storeRepository;
    private final StoreAccessService storeAccessService;

    public StoreCapabilityService(StoreRepository storeRepository, StoreAccessService storeAccessService) {
        this.storeRepository = storeRepository;
        this.storeAccessService = storeAccessService;
    }

    @Transactional(readOnly = true)
    public Store requireCapability(UUID storeId, StoreCapability capability, Authentication authentication) {
        storeAccessService.requireStoreAccess(authentication, storeId);
        Store store = storeRepository.findById(storeId).orElseThrow(() -> new NotFoundException("Store not found"));
        if (!store.getCapabilities().contains(capability)) {
            throw new ForbiddenOperationException("Store does not support " + capability.name());
        }
        return store;
    }
}
