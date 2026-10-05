package com.merchtyl.publiccontact;

public record PublicContactResponse(boolean success, String message) {
    public static PublicContactResponse accepted() {
        return new PublicContactResponse(true, "Your message has been received.");
    }
}
