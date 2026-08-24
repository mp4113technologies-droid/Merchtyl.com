# Transactional Email with Resend

Merchtyl sends merchant owner activation and invitation-resend messages through the configured `EmailSender`.

## Providers

- `resend`: default for development and required for production.
- `console`: available for local testing with `MERCHTYL_EMAIL_PROVIDER=console`.

The application never reads a Resend API key from source code. Configure it with environment variables or a secret manager.

## Required Production Settings

```env
MERCHTYL_EMAIL_PROVIDER=resend
MERCHTYL_EMAIL_FROM_ADDRESS=onboarding@resend.dev
MERCHTYL_EMAIL_FROM_NAME=Merchtyl
MERCHTYL_EMAIL_REPLY_TO=
MERCHTYL_FRONTEND_BASE_URL=
RESEND_ENABLED=true
RESEND_API_KEY=
```

When the provider is `resend`, startup fails if Resend is not enabled, the API key is blank, the sender address is blank, or the frontend base URL is blank. Startup errors are sanitized and do not include the API key.

## Resend Setup

1. Create a Resend account.
2. Add a sending domain or subdomain, such as `notifications.merchtyl.com`.
3. Add the SPF and DKIM DNS records from Resend.
4. Wait for domain verification.
5. Create a sending-only API key.
6. Configure `RESEND_API_KEY`.
7. Configure `MERCHTYL_EMAIL_FROM_ADDRESS`, for example `onboarding@resend.dev` for sandbox testing or a sender at your verified domain for production.
8. Set `MERCHTYL_EMAIL_PROVIDER=resend`.
9. Set `RESEND_ENABLED=true`.
10. Restart Merchtyl.
11. Send a test email from `POST /api/v1/platform/email/test`.
12. Create a test merchant and confirm the owner activation email is accepted.

The sender address must be authorized by the configured Resend account.

## Delivery Tracking

Delivery attempts are stored in `email_deliveries`. Records include recipient, template, provider, provider message ID, status, attempt counts, sanitized failure details, and correlation ID.

`SENT` means Resend accepted the message. It does not guarantee inbox delivery. Webhook event tracking for delivered, bounced, complained, opened, or clicked can be added later.

Raw activation tokens, passwords, refresh tokens, and API keys are never persisted in email delivery records.
