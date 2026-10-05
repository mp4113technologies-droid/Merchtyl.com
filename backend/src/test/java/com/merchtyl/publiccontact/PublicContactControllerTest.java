package com.merchtyl.publiccontact;

import com.merchtyl.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicContactControllerTest {
    private final PublicContactService service = mock(PublicContactService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PublicContactController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void acceptsValidUnauthenticatedRequest() throws Exception {
        when(service.submit(any())).thenReturn(PublicContactResponse.accepted());
        mockMvc.perform(post("/api/v1/public/contact").contentType(MediaType.APPLICATION_JSON).content(validJson()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void rejectsInvalidEmailAndMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/api/v1/public/contact").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\",\"business\":\"\",\"email\":\"not-email\",\"businessType\":\"retail\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsOversizedMessage() throws Exception {
        String oversized = "x".repeat(2001);
        mockMvc.perform(post("/api/v1/public/contact").contentType(MediaType.APPLICATION_JSON)
                        .content(validJson().replace("Hello", oversized)))
                .andExpect(status().isBadRequest());
    }

    private static String validJson() {
        return """
                {"name":"Ada Lovelace","business":"Analytical Engines","email":"visitor@example.test",
                 "businessType":"retail","countryCode":"CA","regionCode":"NB","stores":"1","registers":"2",
                 "needs":["Retail POS"],"message":"Hello","demo":true,"website":""}
                """;
    }
}
