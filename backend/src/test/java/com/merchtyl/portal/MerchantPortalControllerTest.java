package com.merchtyl.portal;

import com.merchtyl.common.NotFoundException;
import com.merchtyl.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MerchantPortalControllerTest {
    private final MerchantPortalService service = mock(MerchantPortalService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new MerchantPortalController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void returnsOnlySafePublicMetadataWithoutAuthentication() throws Exception {
        when(service.resolve("adviam")).thenReturn(new PublicMerchantPortalResponse("adviam", "Adviam Creatives", true));

        mockMvc.perform(get("/api/v1/public/merchant-portals/adviam"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantSlug").value("adviam"))
                .andExpect(jsonPath("$.displayName").value("Adviam Creatives"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.users").doesNotExist())
                .andExpect(jsonPath("$.stores").doesNotExist());
    }

    @Test
    void reportsInactiveMerchantAsUnavailable() throws Exception {
        when(service.resolve("inactive-shop")).thenReturn(new PublicMerchantPortalResponse("inactive-shop", "Inactive Shop", false));

        mockMvc.perform(get("/api/v1/public/merchant-portals/inactive-shop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void unknownMerchantUsesSafeNotFoundResponse() throws Exception {
        when(service.resolve("not-real")).thenThrow(new NotFoundException("MERCHANT_PORTAL_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/public/merchant-portals/not-real"))
                .andExpect(status().isNotFound());
    }
}
