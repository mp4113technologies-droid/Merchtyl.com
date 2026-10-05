package com.merchtyl.publiccontact;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/contact")
public class PublicContactController {
    private final PublicContactService service;

    public PublicContactController(PublicContactService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    PublicContactResponse submit(@Valid @RequestBody PublicContactRequest request) {
        return service.submit(request);
    }
}
