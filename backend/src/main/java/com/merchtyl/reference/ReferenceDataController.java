package com.merchtyl.reference;

import com.merchtyl.tax.AdministrativeAreaType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reference")
@Tag(name = "Reference Data", description = "Country-aware reference data for cascading store geography selection.")
public class ReferenceDataController {
    private final ReferenceDataService service;

    public ReferenceDataController(ReferenceDataService service) {
        this.service = service;
    }

    @GetMapping("/countries")
    @Operation(summary = "List countries", description = "Lists active or inactive countries. Store forms should use this as the first cascading selector.")
    @PreAuthorize("@authorizationService.hasAnyPermission(authentication, T(com.merchtyl.security.PermissionCode).REFERENCE_DATA_VIEW, T(com.merchtyl.security.PermissionCode).COUNTRY_REFERENCE_VIEW, T(com.merchtyl.security.PermissionCode).STORE_VIEW, T(com.merchtyl.security.PermissionCode).STORE_MANAGE)")
    List<CountryReferenceResponse> countries(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {
        return service.countries(active, search);
    }

    @GetMapping("/countries/{countryCode}")
    @Operation(summary = "Get country by ISO alpha-2 code")
    @PreAuthorize("@authorizationService.hasAnyPermission(authentication, T(com.merchtyl.security.PermissionCode).REFERENCE_DATA_VIEW, T(com.merchtyl.security.PermissionCode).COUNTRY_REFERENCE_VIEW, T(com.merchtyl.security.PermissionCode).STORE_VIEW, T(com.merchtyl.security.PermissionCode).STORE_MANAGE)")
    CountryReferenceResponse country(@PathVariable String countryCode) {
        return service.country(countryCode);
    }

    @GetMapping("/countries/{countryCode}/administrative-divisions")
    @Operation(summary = "List provinces, territories, or states for a country", description = "Use after country selection. Results include each division default timezone and tax-region code.")
    @PreAuthorize("@authorizationService.hasAnyPermission(authentication, T(com.merchtyl.security.PermissionCode).REFERENCE_DATA_VIEW, T(com.merchtyl.security.PermissionCode).COUNTRY_REFERENCE_VIEW, T(com.merchtyl.security.PermissionCode).STORE_VIEW, T(com.merchtyl.security.PermissionCode).STORE_MANAGE)")
    List<AdministrativeDivisionResponse> administrativeDivisions(
            @PathVariable String countryCode,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) AdministrativeAreaType divisionType,
            @RequestParam(required = false) String search) {
        return service.administrativeDivisions(countryCode, active, divisionType, search);
    }

    @GetMapping("/countries/{countryCode}/currencies")
    @Operation(summary = "List currencies allowed for a country", description = "The first/default currency should be suggested automatically by clients.")
    @PreAuthorize("@authorizationService.hasAnyPermission(authentication, T(com.merchtyl.security.PermissionCode).REFERENCE_DATA_VIEW, T(com.merchtyl.security.PermissionCode).CURRENCY_REFERENCE_VIEW, T(com.merchtyl.security.PermissionCode).STORE_VIEW, T(com.merchtyl.security.PermissionCode).STORE_MANAGE)")
    List<CurrencyResponse> countryCurrencies(@PathVariable String countryCode) {
        return service.currencies(true, countryCode, null);
    }

    @GetMapping("/administrative-divisions/{divisionId}/timezones")
    @Tag(name = "Timezones")
    @Operation(summary = "List allowed timezones for a province, territory, or state")
    @PreAuthorize("@authorizationService.hasAnyPermission(authentication, T(com.merchtyl.security.PermissionCode).REFERENCE_DATA_VIEW, T(com.merchtyl.security.PermissionCode).TIMEZONE_REFERENCE_VIEW, T(com.merchtyl.security.PermissionCode).STORE_VIEW, T(com.merchtyl.security.PermissionCode).STORE_MANAGE)")
    List<TimezoneReferenceResponse> divisionTimezones(@PathVariable UUID divisionId) {
        return service.timezonesForDivision(divisionId);
    }

    @GetMapping("/administrative-divisions/{divisionId}/tax-regions")
    @Tag(name = "Tax Regions")
    @Operation(summary = "List tax regions for a province, territory, or state", description = "Tax regions identify the geography used by the configurable tax engine and do not expose tax percentages.")
    @PreAuthorize("@authorizationService.hasAnyPermission(authentication, T(com.merchtyl.security.PermissionCode).REFERENCE_DATA_VIEW, T(com.merchtyl.security.PermissionCode).TAX_REGION_REFERENCE_VIEW, T(com.merchtyl.security.PermissionCode).STORE_VIEW, T(com.merchtyl.security.PermissionCode).STORE_MANAGE)")
    List<TaxRegionResponse> divisionTaxRegions(@PathVariable UUID divisionId) {
        return service.taxRegionsForDivision(divisionId);
    }

    @GetMapping("/currencies")
    @Tag(name = "Currencies")
    @Operation(summary = "List currencies")
    @PreAuthorize("@authorizationService.hasAnyPermission(authentication, T(com.merchtyl.security.PermissionCode).REFERENCE_DATA_VIEW, T(com.merchtyl.security.PermissionCode).CURRENCY_REFERENCE_VIEW, T(com.merchtyl.security.PermissionCode).STORE_VIEW, T(com.merchtyl.security.PermissionCode).STORE_MANAGE)")
    List<CurrencyResponse> currencies(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String search) {
        return service.currencies(active, countryCode, search);
    }

    @GetMapping("/timezones")
    @Tag(name = "Timezones")
    @Operation(summary = "List timezones")
    @PreAuthorize("@authorizationService.hasAnyPermission(authentication, T(com.merchtyl.security.PermissionCode).REFERENCE_DATA_VIEW, T(com.merchtyl.security.PermissionCode).TIMEZONE_REFERENCE_VIEW, T(com.merchtyl.security.PermissionCode).STORE_VIEW, T(com.merchtyl.security.PermissionCode).STORE_MANAGE)")
    List<TimezoneReferenceResponse> timezones(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String search) {
        return service.timezones(active, countryCode, search);
    }

    @GetMapping("/tax-regions")
    @Tag(name = "Tax Regions")
    @Operation(summary = "List tax regions")
    @PreAuthorize("@authorizationService.hasAnyPermission(authentication, T(com.merchtyl.security.PermissionCode).REFERENCE_DATA_VIEW, T(com.merchtyl.security.PermissionCode).TAX_REGION_REFERENCE_VIEW, T(com.merchtyl.security.PermissionCode).STORE_VIEW, T(com.merchtyl.security.PermissionCode).STORE_MANAGE)")
    List<TaxRegionResponse> taxRegions(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String search) {
        return service.taxRegions(active, countryCode, search);
    }
}
