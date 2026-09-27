package com.merchtyl.tax;

import com.merchtyl.common.ConflictException;
import com.merchtyl.product.Product;
import com.merchtyl.product.ProductTaxClass;
import com.merchtyl.store.Store;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/** Resolves semantic product tax classes using persisted store jurisdiction. */
@Service
public class ProductTaxConfigurationResolver {
    public static final String MISSING_VAPE_CONFIGURATION =
            "No active Vape tax configuration is available for this Store jurisdiction.";

    private final TaxCategoryRepository categories;

    public ProductTaxConfigurationResolver(TaxCategoryRepository categories) {
        this.categories = categories;
    }

    public TaxCategory resolve(Product product, Store store, LocalDate transactionDate) {
        if (product == null) return null;
        ProductTaxClass taxClass = product.getTaxClass();
        if (taxClass == ProductTaxClass.VAPE) {
            if (store == null || !"CA".equalsIgnoreCase(store.getCountryCode())
                    || store.getAdministrativeAreaCode() == null) {
                throw new ConflictException(MISSING_VAPE_CONFIGURATION);
            }
            String code = "CA_" + store.getAdministrativeAreaCode().trim().toUpperCase() + "_VAPE";
            TaxCategory category = categories.findByCodeIgnoreCase(code)
                    .filter(TaxCategory::isActive)
                    .orElseThrow(() -> new ConflictException(MISSING_VAPE_CONFIGURATION));
            if (category.getTaxGroup() == null) throw new ConflictException(MISSING_VAPE_CONFIGURATION);
            return category;
        }
        if (taxClass == ProductTaxClass.NON_TAXABLE) {
            return categories.findByCodeIgnoreCase("EXEMPT").filter(TaxCategory::isActive)
                    .orElseThrow(() -> new ConflictException("No active non-taxable configuration is available"));
        }
        if (taxClass == ProductTaxClass.CUSTOM) {
            return product.getTaxCategoryId() == null ? null : categories.findById(product.getTaxCategoryId()).orElse(null);
        }
        return null;
    }
}
