package com.merchtyl.publiccontact;

import com.merchtyl.common.BadRequestException;
import com.merchtyl.tax.AdministrativeArea;
import com.merchtyl.tax.AdministrativeAreaRepository;
import com.merchtyl.tax.AdministrativeAreaType;
import com.merchtyl.tax.Country;
import com.merchtyl.tax.CountryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicGeographyServiceTest {
    private final CountryRepository countries = mock(CountryRepository.class);
    private final AdministrativeAreaRepository regions = mock(AdministrativeAreaRepository.class);
    private final PublicGeographyService service = new PublicGeographyService(countries, regions);
    private final Country canada = new Country("CA", "Canada", true);
    private final Country unitedStates = new Country("US", "United States", true);

    @BeforeEach
    void setUp() {
        when(countries.findByCodeIgnoreCase("CA")).thenReturn(Optional.of(canada));
        when(countries.findByCodeIgnoreCase("US")).thenReturn(Optional.of(unitedStates));
    }

    @Test
    void exposesOnlySupportedMarkets() {
        assertThat(service.countries()).extracting(PublicCountryResponse::code).containsExactly("CA", "US");
        assertThat(service.countries()).extracting(PublicCountryResponse::regionLabel)
                .containsExactly("Province / Territory", "State");
    }

    @Test
    void acceptsSupportedCountryRegionPairs() {
        accept(canada, "NB", "New Brunswick");
        accept(canada, "BC", "British Columbia");
        accept(unitedStates, "NY", "New York");
        accept(unitedStates, "CA", "California");
    }

    @Test
    void rejectsCrossCountryUnknownAndUnsupportedPairs() {
        when(regions.findByCountryAndCodeIgnoreCase(unitedStates, "NB")).thenReturn(Optional.empty());
        when(regions.findByCountryAndCodeIgnoreCase(canada, "TX")).thenReturn(Optional.empty());
        when(regions.findByCountryAndCodeIgnoreCase(canada, "ZZ")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.validate("US", "NB")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.validate("CA", "TX")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.validate("CA", "ZZ")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.validate("XX", "AA")).isInstanceOf(BadRequestException.class);
    }

    private void accept(Country country, String code, String name) {
        AdministrativeArea region = new AdministrativeArea(country, code, name,
                country == canada ? AdministrativeAreaType.PROVINCE : AdministrativeAreaType.STATE, true);
        when(regions.findByCountryAndCodeIgnoreCase(country, code)).thenReturn(Optional.of(region));
        assertThat(service.validate(country.getCode(), code).regionName()).isEqualTo(name);
    }
}
