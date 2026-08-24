package com.merchtyl.email;

import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;

@FunctionalInterface
interface ResendEmailClient {
    CreateEmailResponse send(CreateEmailOptions options) throws ResendException;
}
