package com.merchtyl.auth;

record PasswordResetPortalContext(String realm, String merchantSlug) {
    static final String REALM_HEADER = "X-Portal-Realm";

    static PasswordResetPortalContext of(String realm, String merchantSlug) {
        String normalizedRealm = realm == null ? "PUBLIC" : realm.trim().toUpperCase();
        String normalizedSlug = merchantSlug == null || merchantSlug.isBlank()
                ? null : merchantSlug.trim().toLowerCase();
        return new PasswordResetPortalContext(normalizedRealm, normalizedSlug);
    }

    boolean merchant() {
        return "MERCHANT".equals(realm) || ("DEVELOPMENT".equals(realm) && merchantSlug != null);
    }

    boolean platform() {
        return "PLATFORM".equals(realm);
    }

    boolean developmentWithoutSlug() {
        return "DEVELOPMENT".equals(realm) && merchantSlug == null;
    }
}
