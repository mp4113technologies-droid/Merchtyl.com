package com.merchtyl.publiccontact;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PublicContactRequest(
        @NotBlank @Size(min = 2, max = 120) @Pattern(regexp = "^[^\\r\\n]+$", message = "must not contain line breaks") String name,
        @NotBlank @Size(min = 2, max = 160) @Pattern(regexp = "^[^\\r\\n]+$", message = "must not contain line breaks") String business,
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 40) @Pattern(regexp = "^[0-9+(). xX-]*$", message = "must be a valid phone number") String phone,
        @NotBlank @Pattern(regexp = "convenience|retail|takeout|retail_restaurant|other", message = "must be a supported business type") String businessType,
        @NotBlank @Pattern(regexp = "(?i)CA|US", message = "must be a supported country code") String countryCode,
        @NotBlank @Pattern(regexp = "(?i)[A-Z]{2}", message = "must be a valid region code") String regionCode,
        @Pattern(regexp = "1|2–3|4–10|More than 10|", message = "must be a supported store count") String stores,
        @Pattern(regexp = "1|2|3–5|6 or more|", message = "must be a supported register count") String registers,
        @Size(max = 4) List<@Pattern(regexp = "Retail POS|Restaurant POS|Lottery|Inventory and reporting") String> needs,
        @Size(max = 2000) String message,
        boolean demo,
        @Size(max = 200) String website
) {
}
