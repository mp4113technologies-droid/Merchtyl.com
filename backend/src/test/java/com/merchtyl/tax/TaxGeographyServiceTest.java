package com.merchtyl.tax;

import com.merchtyl.audit.AuditAction;
import com.merchtyl.audit.AuditService;
import com.merchtyl.audit.CreateAuditRecordCommand;
import com.merchtyl.common.BadRequestException;
import com.merchtyl.common.ConflictException;
import com.merchtyl.security.User;
import com.merchtyl.security.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaxGeographyServiceTest {
    private final CountryRepository countryRepository = mock(CountryRepository.class);
    private final AdministrativeAreaRepository administrativeAreaRepository = mock(AdministrativeAreaRepository.class);
    private final TaxJurisdictionRepository taxJurisdictionRepository = mock(TaxJurisdictionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final CountryService countryService = new CountryService(countryRepository, userRepository, auditService);
    private final AdministrativeAreaService administrativeAreaService = new AdministrativeAreaService(
            administrativeAreaRepository,
            countryService,
            userRepository,
            auditService);
    private final TaxJurisdictionService taxJurisdictionService = new TaxJurisdictionService(
            taxJurisdictionRepository,
            countryService,
            administrativeAreaService,
            userRepository,
            auditService);

    @Test
    void createCountryNormalizesCodeAndAudits() {
        User actor = new User("manager@example.local", "Manager", "hash");
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("manager@example.local");
        when(userRepository.findByEmailIgnoreCase("manager@example.local")).thenReturn(Optional.of(actor));
        when(countryRepository.existsByCodeIgnoreCase("CA")).thenReturn(false);
        when(countryRepository.saveAndFlush(any(Country.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CountryResponse response = countryService.create(new CountryRequest(" ca ", " Canada ", true), authentication);

        assertThat(response.code()).isEqualTo("CA");
        assertThat(response.name()).isEqualTo("Canada");
        verify(auditService).record(any(CreateAuditRecordCommand.class));
    }

    @Test
    void createCountryRejectsDuplicateCode() {
        when(countryRepository.existsByCodeIgnoreCase("US")).thenReturn(true);

        assertThatThrownBy(() -> countryService.create(new CountryRequest("us", "United States", true), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Country code already exists");

        verify(countryRepository, never()).saveAndFlush(any());
        verify(auditService, never()).record(any());
    }

    @Test
    void createAdministrativeAreaRequiresUniqueCodeWithinCountry() {
        Country country = new Country("CA", "Canada", true);
        when(countryRepository.findById(country.getId())).thenReturn(Optional.of(country));
        when(administrativeAreaRepository.existsByCountryAndCodeIgnoreCase(country, "NB")).thenReturn(true);

        assertThatThrownBy(() -> administrativeAreaService.create(new AdministrativeAreaRequest(
                country.getId(),
                "nb",
                "New Brunswick",
                AdministrativeAreaType.PROVINCE,
                true), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Administrative area code already exists");

        verify(administrativeAreaRepository, never()).saveAndFlush(any());
    }

    @Test
    void createAdministrativeAreaAuditsCreation() {
        Country country = new Country("CA", "Canada", true);
        when(countryRepository.findById(country.getId())).thenReturn(Optional.of(country));
        when(administrativeAreaRepository.existsByCountryAndCodeIgnoreCase(country, "NB")).thenReturn(false);
        when(administrativeAreaRepository.saveAndFlush(any(AdministrativeArea.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdministrativeAreaResponse response = administrativeAreaService.create(new AdministrativeAreaRequest(
                country.getId(),
                " nb ",
                " New Brunswick ",
                AdministrativeAreaType.PROVINCE,
                true), null);

        assertThat(response.countryId()).isEqualTo(country.getId());
        assertThat(response.code()).isEqualTo("NB");
        assertThat(response.type()).isEqualTo(AdministrativeAreaType.PROVINCE);
        verify(auditService).record(any(CreateAuditRecordCommand.class));
    }

    @Test
    void createTaxJurisdictionRejectsAreaFromAnotherCountry() {
        Country canada = new Country("CA", "Canada", true);
        Country unitedStates = new Country("US", "United States", true);
        AdministrativeArea maine = new AdministrativeArea(unitedStates, "ME", "Maine", AdministrativeAreaType.STATE, true);
        when(countryRepository.findById(canada.getId())).thenReturn(Optional.of(canada));
        when(administrativeAreaRepository.findById(maine.getId())).thenReturn(Optional.of(maine));

        assertThatThrownBy(() -> taxJurisdictionService.create(new TaxJurisdictionRequest(
                canada.getId(),
                maine.getId(),
                "CA-NB-HST",
                "New Brunswick HST",
                TaxJurisdictionType.PROVINCIAL,
                true), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Administrative area must belong");

        verify(taxJurisdictionRepository, never()).saveAndFlush(any());
    }

    @Test
    void createTaxJurisdictionRejectsNationalJurisdictionWithArea() {
        Country country = new Country("CA", "Canada", true);
        AdministrativeArea area = new AdministrativeArea(country, "NB", "New Brunswick", AdministrativeAreaType.PROVINCE, true);
        when(countryRepository.findById(country.getId())).thenReturn(Optional.of(country));
        when(administrativeAreaRepository.findById(area.getId())).thenReturn(Optional.of(area));

        assertThatThrownBy(() -> taxJurisdictionService.create(new TaxJurisdictionRequest(
                country.getId(),
                area.getId(),
                "GST",
                "GST",
                TaxJurisdictionType.NATIONAL,
                true), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("National jurisdictions cannot");

        verify(taxJurisdictionRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateTaxJurisdictionRequiresCurrentVersion() {
        Country country = new Country("CA", "Canada", true);
        TaxJurisdiction jurisdiction = new TaxJurisdiction(country, null, "GST", "GST", TaxJurisdictionType.NATIONAL, true);
        when(taxJurisdictionRepository.findById(jurisdiction.getId())).thenReturn(Optional.of(jurisdiction));

        assertThatThrownBy(() -> taxJurisdictionService.update(jurisdiction.getId(), new TaxJurisdictionUpdateRequest(
                country.getId(),
                null,
                "GST",
                "GST",
                TaxJurisdictionType.NATIONAL,
                true,
                jurisdiction.getVersion() + 1), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Tax jurisdiction was modified");

        verify(taxJurisdictionRepository, never()).saveAndFlush(any());
        verify(auditService, never()).record(any());
    }

    @Test
    void createTaxJurisdictionNormalizesCodeAndAudits() {
        Country country = new Country("CA", "Canada", true);
        when(countryRepository.findById(country.getId())).thenReturn(Optional.of(country));
        when(taxJurisdictionRepository.existsByCountryAndCodeIgnoreCase(country, "GST")).thenReturn(false);
        when(taxJurisdictionRepository.saveAndFlush(any(TaxJurisdiction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaxJurisdictionResponse response = taxJurisdictionService.create(new TaxJurisdictionRequest(
                country.getId(),
                null,
                " gst ",
                " Federal GST ",
                TaxJurisdictionType.NATIONAL,
                true), null);

        assertThat(response.code()).isEqualTo("GST");
        assertThat(response.administrativeAreaId()).isNull();
        verify(auditService).record(any(CreateAuditRecordCommand.class));
    }
}
