package com.merchtyl.store;

import com.merchtyl.common.ForbiddenOperationException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.security.StoreAccessService;
import com.merchtyl.register.RegisterCapabilityService;
import com.merchtyl.register.RegisterType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class StoreCapabilityService {
    private final StoreRepository storeRepository;
    private final StoreAccessService storeAccessService;
    private final RegisterCapabilityService registerCapabilities;

    public StoreCapabilityService(StoreRepository storeRepository, StoreAccessService storeAccessService,
                                  RegisterCapabilityService registerCapabilities) {
        this.storeRepository = storeRepository;
        this.storeAccessService = storeAccessService;
        this.registerCapabilities = registerCapabilities;
    }

    @Transactional(readOnly = true)
    public Store requireCapability(UUID storeId, StoreCapability capability, Authentication authentication) {
        storeAccessService.requireStoreAccess(authentication, storeId);
        Store store = storeRepository.findById(storeId).orElseThrow(() -> new NotFoundException("Store not found"));
        if (capability == StoreCapability.FOOD_SERVICE) registerCapabilities.requireEnabled(store, RegisterType.FOOD_SERVICE);
        else if (!store.getCapabilities().contains(capability))
            throw new ForbiddenOperationException("STORE_CAPABILITY_NOT_ENABLED: " + capability.name() + " is not enabled for this store.");
        return store;
    }
}
