# Platform Super Admin bootstrap

Bootstrap is disabled by default.

To create the first platform administrator, set these values through a secure runtime secret manager or local shell environment:

```env
MERCHTYL_BOOTSTRAP_ADMIN_ENABLED=true
MERCHTYL_BOOTSTRAP_ADMIN_EMAIL=
MERCHTYL_BOOTSTRAP_ADMIN_NAME=
MERCHTYL_BOOTSTRAP_ADMIN_PASSWORD=
```

The bootstrap runner creates an active `PLATFORM_SUPER_ADMIN` only when no active Platform Super Admin exists. The password is hashed with the application password encoder, is never logged, and the account is marked for password change.

After the account is created:

1. Stop the application.
2. Set `MERCHTYL_BOOTSTRAP_ADMIN_ENABLED=false`.
3. Remove the bootstrap password from the runtime environment or secret manager.
4. Restart the application and sign in at `/platform/login`.

Do not commit bootstrap email or password values to source control.

Owner invitation and support-access defaults:

```env
MERCHTYL_OWNER_INVITATION_EXPIRY_HOURS=48
MERCHTYL_SUPPORT_ACCESS_ENABLED=false
MERCHTYL_SUPPORT_ACCESS_DEFAULT_MINUTES=30
```

Support access is intentionally disabled by default. The database foundation exists for time-limited, reason-required support sessions, but no unrestricted merchant impersonation endpoint is exposed.
