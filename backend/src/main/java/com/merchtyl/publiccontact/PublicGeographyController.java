package com.merchtyl.publiccontact;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/public/geography")
public class PublicGeographyController {
    private final PublicGeographyService service;

    public PublicGeographyController(PublicGeographyService service) {
        this.service = service;
    }

    @GetMapping("/countries")
    List<PublicCountryResponse> countries() {
        return service.countries();
    }

    @GetMapping("/countries/{countryCode}/regions")
    List<PublicRegionResponse> regions(@PathVariable String countryCode) {
        return service.regions(countryCode);
    }
}
