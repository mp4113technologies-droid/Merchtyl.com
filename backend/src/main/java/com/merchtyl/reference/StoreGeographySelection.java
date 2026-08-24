package com.merchtyl.reference;

import com.merchtyl.tax.AdministrativeArea;
import com.merchtyl.tax.Country;

public record StoreGeographySelection(
        Country country,
        AdministrativeArea administrativeDivision,
        Currency currency,
        TimezoneReference timezone,
        TaxRegion taxRegion
) {
}
