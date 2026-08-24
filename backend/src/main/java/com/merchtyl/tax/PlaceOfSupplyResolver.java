package com.merchtyl.tax;

import com.merchtyl.common.NotFoundException;
import com.merchtyl.store.Store;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PlaceOfSupplyResolver {
    private final TaxJurisdictionRepository taxJurisdictionRepository;

    public PlaceOfSupplyResolver(TaxJurisdictionRepository taxJurisdictionRepository) {
        this.taxJurisdictionRepository = taxJurisdictionRepository;
    }

    public UUID resolveStoreJurisdiction(Store store, UUID explicitJurisdictionId) {
        if (explicitJurisdictionId != null) {
            if (!taxJurisdictionRepository.existsById(explicitJurisdictionId)) {
                throw new NotFoundException("Tax jurisdiction not found");
            }
            return explicitJurisdictionId;
        }
        return resolveFromStore(store);
    }

    public UUID resolveSupplyJurisdiction(Store store, UUID explicitSupplyJurisdictionId) {
        if (explicitSupplyJurisdictionId != null) {
            if (!taxJurisdictionRepository.existsById(explicitSupplyJurisdictionId)) {
                throw new NotFoundException("Tax jurisdiction not found");
            }
            return explicitSupplyJurisdictionId;
        }
        return resolveFromStore(store);
    }

    private UUID resolveFromStore(Store store) {
        if (store == null) {
            return null;
        }
        return taxJurisdictionRepository.findBestForStore(store.getCountryCode(), store.getAdministrativeAreaCode()).stream()
                .findFirst()
                .map(TaxJurisdiction::getId)
                .orElse(null);
    }
}
