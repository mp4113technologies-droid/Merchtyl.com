package com.merchtyl.registersession;

import jakarta.validation.constraints.NotNull;

public record RegisterSessionTransitionRequest(@NotNull Long version) {
}
