package com.merchtyl.publiccontact;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicGeographyControllerTest {
    private final PublicGeographyService service = mock(PublicGeographyService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PublicGeographyController(service)).build();

    @Test
    void returnsOnlySupportedPublicMarkets() throws Exception {
        when(service.countries()).thenReturn(List.of(
                new PublicCountryResponse("CA", "Canada", "Province / Territory"),
                new PublicCountryResponse("US", "United States", "State")));

        mockMvc.perform(get("/api/v1/public/geography/countries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("CA"))
                .andExpect(jsonPath("$[1].code").value("US"));
    }

    @Test
    void returnsOnlyPublicRegionProjection() throws Exception {
        when(service.regions("US")).thenReturn(List.of(new PublicRegionResponse("NY", "New York")));
        mockMvc.perform(get("/api/v1/public/geography/countries/US/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("NY"))
                .andExpect(jsonPath("$[0].name").value("New York"))
                .andExpect(jsonPath("$[0].id").doesNotExist());
    }
}
