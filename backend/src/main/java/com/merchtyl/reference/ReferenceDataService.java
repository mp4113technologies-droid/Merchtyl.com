package com.merchtyl.reference;

import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.NotFoundException;
import com.merchtyl.tax.AdministrativeArea;
import com.merchtyl.tax.AdministrativeAreaRepository;
import com.merchtyl.tax.AdministrativeAreaType;
import com.merchtyl.tax.Country;
import com.merchtyl.tax.CountryRepository;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ReferenceDataService {
    private final CountryRepository countryRepository;
    private final AdministrativeAreaRepository administrativeAreaRepository;
    private final CurrencyRepository currencyRepository;
    private final TimezoneReferenceRepository timezoneReferenceRepository;
    private final AdministrativeDivisionTimezoneRepository administrativeDivisionTimezoneRepository;
    private final TaxRegionRepository taxRegionRepository;

    public ReferenceDataService(
            CountryRepository countryRepository,
            AdministrativeAreaRepository administrativeAreaRepository,
            CurrencyRepository currencyRepository,
            TimezoneReferenceRepository timezoneReferenceRepository,
            AdministrativeDivisionTimezoneRepository administrativeDivisionTimezoneRepository,
            TaxRegionRepository taxRegionRepository) {
        this.countryRepository = countryRepository;
        this.administrativeAreaRepository = administrativeAreaRepository;
        this.currencyRepository = currencyRepository;
        this.timezoneReferenceRepository = timezoneReferenceRepository;
        this.administrativeDivisionTimezoneRepository = administrativeDivisionTimezoneRepository;
        this.taxRegionRepository = taxRegionRepository;
    }

    @Transactional(readOnly = true)
    public List<CountryReferenceResponse> countries(Boolean active, String search) {
        return countryRepository.findAll(
                        countrySpecification(active, search),
                        Sort.by("displayOrder").ascending().and(Sort.by("name")))
                .stream()
                .map(CountryReferenceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CountryReferenceResponse country(String countryCode) {
        return CountryReferenceResponse.from(findCountry(countryCode, false));
    }

    @Transactional(readOnly = true)
    public List<AdministrativeDivisionResponse> administrativeDivisions(
            String countryCode,
            Boolean active,
            AdministrativeAreaType divisionType,
            String search) {
        Country country = findCountry(countryCode, false);
        return administrativeAreaRepository.findAll(
                        administrativeDivisionSpecification(country, active, divisionType, search),
                        Sort.by("displayOrder").ascending().and(Sort.by("name")))
                .stream()
                .map(AdministrativeDivisionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CurrencyResponse> currencies(Boolean active, String countryCode, String search) {
        List<Currency> currencies = countryCode == null || countryCode.isBlank()
                ? currencyRepository.findAll(currencySpecification(active, search), Sort.by("code"))
                : currencyRepository.findAllowedForCountry(countryCode.trim().toUpperCase(Locale.ROOT));
        return currencies.stream()
                .filter(currency -> active == null || currency.isActive() == active)
                .filter(currency -> matchesCurrencySearch(currency, search))
                .map(CurrencyResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TimezoneReferenceResponse> timezones(Boolean active, String countryCode, String search) {
        return timezoneReferenceRepository.findAll(timezoneSpecification(active, countryCode, search),
                        Sort.by("displayOrder").ascending().and(Sort.by("ianaName")))
                .stream()
                .map(TimezoneReferenceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TimezoneReferenceResponse> timezonesForDivision(UUID divisionId) {
        AdministrativeArea division = administrativeAreaRepository.findById(divisionId)
                .orElseThrow(() -> new NotFoundException("Administrative division not found"));
        return administrativeDivisionTimezoneRepository
                .findByAdministrativeDivisionOrderByDefaultTimezoneDescTimezoneDisplayOrderAscTimezoneIanaNameAsc(division)
                .stream()
                .filter(mapping -> mapping.getTimezone().isActive())
                .map(mapping -> TimezoneReferenceResponse.from(mapping.getTimezone(), mapping.isDefaultTimezone()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaxRegionResponse> taxRegions(Boolean active, String countryCode, String search) {
        return taxRegionRepository.findAll(taxRegionSpecification(active, countryCode, search),
                        Sort.by("code"))
                .stream()
                .map(TaxRegionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaxRegionResponse> taxRegionsForDivision(UUID divisionId) {
        AdministrativeArea division = administrativeAreaRepository.findById(divisionId)
                .orElseThrow(() -> new NotFoundException("Administrative division not found"));
        return taxRegionRepository.findByAdministrativeDivisionOrderByDefaultForDivisionDescCodeAsc(division).stream()
                .filter(TaxRegion::isActive)
                .map(TaxRegionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public StoreGeographySelection validateStoreGeography(
            String countryCodeValue,
            String administrativeDivisionCodeValue,
            String currencyCodeValue,
            String timezoneValue,
            String taxRegionCodeValue,
            boolean allowCurrencyOverride) {
        return validateStoreGeography(
                countryCodeValue,
                administrativeDivisionCodeValue,
                currencyCodeValue,
                timezoneValue,
                taxRegionCodeValue,
                allowCurrencyOverride,
                "STORE_CURRENCY_OVERRIDE");
    }

    @Transactional(readOnly = true)
    public StoreGeographySelection validateStoreGeography(
            String countryCodeValue,
            String administrativeDivisionCodeValue,
            String currencyCodeValue,
            String timezoneValue,
            String taxRegionCodeValue,
            boolean allowCurrencyOverride,
            String currencyOverridePermissionName) {
        Country country = findCountry(countryCodeValue, true);
        AdministrativeArea division = findDivision(country, administrativeDivisionCodeValue);
        Currency currency = findCurrency(currencyCodeValue);
        TimezoneReference timezone = findTimezone(timezoneValue);
        TaxRegion taxRegion = findTaxRegion(taxRegionCodeValue, division);

        if (!currency.isActive()) {
            throw new BadRequestException("currencyCode must reference an active currency");
        }
        List<Currency> allowedCurrencies = currencyRepository.findAllowedForCountry(country.getCode());
        boolean currencyAllowed = allowedCurrencies.stream().anyMatch(allowed -> allowed.getId().equals(currency.getId()));
        if (!currencyAllowed && !allowCurrencyOverride) {
            throw new BadRequestException("currencyCode is not allowed for the selected country");
        }
        String defaultCurrencyCode = country.getDefaultCurrency() == null ? null : country.getDefaultCurrency().getCode();
        if (defaultCurrencyCode != null
                && (!defaultCurrencyCode.equalsIgnoreCase(currency.getCode()) || !currencyAllowed)
                && !allowCurrencyOverride) {
            throw new BadRequestException(currencyOverridePermissionName + " permission is required to use a non-default currency");
        }
        if (!timezone.isActive()) {
            throw new BadRequestException("timezone must reference an active timezone");
        }
        if (administrativeDivisionTimezoneRepository.findByAdministrativeDivisionAndTimezone(division, timezone).isEmpty()) {
            throw new BadRequestException("timezone is not allowed for the selected province, territory, or state");
        }
        if (!taxRegion.isActive()) {
            throw new BadRequestException("taxRegionCode must reference an active tax region");
        }
        if (!taxRegion.getCountry().getId().equals(country.getId())) {
            throw new BadRequestException("taxRegionCode does not belong to the selected country");
        }
        if (taxRegion.getAdministrativeDivision() != null
                && !taxRegion.getAdministrativeDivision().getId().equals(division.getId())) {
            throw new BadRequestException("taxRegionCode does not belong to the selected province, territory, or state");
        }
        return new StoreGeographySelection(country, division, currency, timezone, taxRegion);
    }

    private Country findCountry(String countryCode, boolean requireActive) {
        String normalized = required(countryCode, "countryCode").toUpperCase(Locale.ROOT);
        Country country = countryRepository.findByCodeIgnoreCase(normalized)
                .orElseThrow(() -> new BadRequestException("countryCode must reference a configured country"));
        if (requireActive && !country.isActive()) {
            throw new BadRequestException("countryCode must reference an active country");
        }
        return country;
    }

    private AdministrativeArea findDivision(Country country, String code) {
        String normalized = required(code, "administrativeDivisionCode").toUpperCase(Locale.ROOT);
        AdministrativeArea division = administrativeAreaRepository.findByCountryAndCodeIgnoreCase(country, normalized)
                .orElseThrow(() -> new BadRequestException("administrativeDivisionCode must belong to the selected country"));
        if (!division.isActive()) {
            throw new BadRequestException("administrativeDivisionCode must reference an active province, territory, or state");
        }
        return division;
    }

    private Currency findCurrency(String code) {
        String normalized = required(code, "currencyCode").toUpperCase(Locale.ROOT);
        return currencyRepository.findByCodeIgnoreCase(normalized)
                .orElseThrow(() -> new BadRequestException("currencyCode must reference a configured currency"));
    }

    private TimezoneReference findTimezone(String timezone) {
        String normalized = required(timezone, "timezone");
        return timezoneReferenceRepository.findByIanaNameIgnoreCase(normalized)
                .orElseThrow(() -> new BadRequestException("timezone must reference a configured IANA timezone"));
    }

    private TaxRegion findTaxRegion(String taxRegionCode, AdministrativeArea division) {
        if (taxRegionCode == null || taxRegionCode.isBlank()) {
            return taxRegionRepository.findByAdministrativeDivisionOrderByDefaultForDivisionDescCodeAsc(division).stream()
                    .filter(TaxRegion::isActive)
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("taxRegionCode is required"));
        }
        String normalized = required(taxRegionCode, "taxRegionCode").toUpperCase(Locale.ROOT);
        return taxRegionRepository.findByCodeIgnoreCase(normalized)
                .orElseThrow(() -> new BadRequestException("taxRegionCode must reference a configured tax region"));
    }

    private static Specification<Country> countrySpecification(Boolean active, String search) {
        return (root, query, cb) -> {
            root.fetch("defaultCurrency", JoinType.LEFT);
            var predicate = cb.conjunction();
            if (active != null) {
                predicate = cb.and(predicate, cb.equal(root.get("active"), active));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("code")), pattern),
                        cb.like(cb.lower(root.get("alpha3Code")), pattern)));
            }
            return predicate;
        };
    }

    private static Specification<AdministrativeArea> administrativeDivisionSpecification(Country country, Boolean active, AdministrativeAreaType divisionType, String search) {
        return (root, query, cb) -> {
            root.fetch("country", JoinType.INNER);
            root.fetch("defaultTimezone", JoinType.LEFT);
            root.fetch("defaultTaxRegion", JoinType.LEFT);
            var predicate = cb.equal(root.get("country"), country);
            if (active != null) {
                predicate = cb.and(predicate, cb.equal(root.get("active"), active));
            }
            if (divisionType != null) {
                predicate = cb.and(predicate, cb.equal(root.get("type"), divisionType));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("code")), pattern)));
            }
            return predicate;
        };
    }

    private static Specification<Currency> currencySpecification(Boolean active, String search) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (active != null) {
                predicate = cb.and(predicate, cb.equal(root.get("active"), active));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("code")), pattern),
                        cb.like(cb.lower(root.get("name")), pattern)));
            }
            return predicate;
        };
    }

    private static Specification<TimezoneReference> timezoneSpecification(Boolean active, String countryCode, String search) {
        return (root, query, cb) -> {
            root.fetch("country", JoinType.LEFT);
            var predicate = cb.conjunction();
            if (active != null) {
                predicate = cb.and(predicate, cb.equal(root.get("active"), active));
            }
            if (countryCode != null && !countryCode.isBlank()) {
                predicate = cb.and(predicate, cb.equal(cb.upper(root.get("country").get("code")), countryCode.trim().toUpperCase(Locale.ROOT)));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("ianaName")), pattern),
                        cb.like(cb.lower(root.get("displayName")), pattern)));
            }
            return predicate;
        };
    }

    private static Specification<TaxRegion> taxRegionSpecification(Boolean active, String countryCode, String search) {
        return (root, query, cb) -> {
            root.fetch("country", JoinType.INNER);
            root.fetch("administrativeDivision", JoinType.LEFT);
            root.fetch("taxJurisdiction", JoinType.LEFT);
            var predicate = cb.conjunction();
            if (active != null) {
                predicate = cb.and(predicate, cb.equal(root.get("active"), active));
            }
            if (countryCode != null && !countryCode.isBlank()) {
                predicate = cb.and(predicate, cb.equal(cb.upper(root.get("country").get("code")), countryCode.trim().toUpperCase(Locale.ROOT)));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("code")), pattern),
                        cb.like(cb.lower(root.get("name")), pattern)));
            }
            return predicate;
        };
    }

    private static boolean matchesCurrencySearch(Currency currency, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String value = search.trim().toLowerCase(Locale.ROOT);
        return currency.getCode().toLowerCase(Locale.ROOT).contains(value)
                || currency.getName().toLowerCase(Locale.ROOT).contains(value);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(field + " is required");
        }
        return value.trim();
    }
}
