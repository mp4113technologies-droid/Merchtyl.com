package com.merchtyl.publiccontact;

import com.merchtyl.common.BadRequestException;
import com.merchtyl.tax.AdministrativeArea;
import com.merchtyl.tax.AdministrativeAreaRepository;
import com.merchtyl.tax.Country;
import com.merchtyl.tax.CountryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PublicGeographyService {
    private static final Map<String, String> SUPPORTED_MARKETS = Map.of(
            "CA", "Province / Territory",
            "US", "State");

    private final CountryRepository countryRepository;
    private final AdministrativeAreaRepository administrativeAreaRepository;

    public PublicGeographyService(CountryRepository countryRepository,
            AdministrativeAreaRepository administrativeAreaRepository) {
        this.countryRepository = countryRepository;
        this.administrativeAreaRepository = administrativeAreaRepository;
    }

    @Transactional(readOnly = true)
    public List<PublicCountryResponse> countries() {
        return List.of("CA", "US").stream()
                .map(this::supportedCountry)
                .map(country -> new PublicCountryResponse(country.getCode(), country.getName(),
                        SUPPORTED_MARKETS.get(country.getCode())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PublicRegionResponse> regions(String countryCode) {
        Country country = supportedCountry(countryCode);
        return administrativeAreaRepository.findByCountryAndActiveTrueOrderByDisplayOrderAscNameAsc(country).stream()
                .map(region -> new PublicRegionResponse(region.getCode(), region.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public GeographySelection validate(String countryCode, String regionCode) {
        Country country = supportedCountry(countryCode);
        String normalizedRegion = requiredCode(regionCode, "regionCode");
        AdministrativeArea region = administrativeAreaRepository.findByCountryAndCodeIgnoreCase(country, normalizedRegion)
                .filter(AdministrativeArea::isActive)
                .orElseThrow(() -> new BadRequestException("regionCode must belong to the selected country"));
        return new GeographySelection(country.getCode(), country.getName(), region.getCode(), region.getName(),
                SUPPORTED_MARKETS.get(country.getCode()));
    }

    private Country supportedCountry(String value) {
        String code = requiredCode(value, "countryCode");
        if (!SUPPORTED_MARKETS.containsKey(code)) {
            throw new BadRequestException("countryCode must reference a supported market");
        }
        return countryRepository.findByCodeIgnoreCase(code)
                .filter(Country::isActive)
                .orElseThrow(() -> new BadRequestException("countryCode must reference an active supported market"));
    }

    private static String requiredCode(String value, String field) {
        if (value == null || value.isBlank()) throw new BadRequestException(field + " is required");
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public record GeographySelection(String countryCode, String countryName, String regionCode, String regionName,
            String regionLabel) {
    }
}
