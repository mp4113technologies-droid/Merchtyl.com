package com.merchtyl.common;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Paginated response wrapper. Supported filters and sorting are documented per endpoint.")
public record PageResponse<T>(
        @Schema(description = "Records in the current page.")
        List<T> content,
        @Schema(description = "Zero-based page number.", example = "0")
        int page,
        @Schema(description = "Requested page size.", example = "20")
        int size,
        @Schema(description = "Total records matching the query.", example = "125")
        long totalElements,
        @Schema(description = "Total pages matching the query.", example = "7")
        int totalPages,
        @Schema(description = "True when this is the first page.", example = "true")
        boolean first,
        @Schema(description = "True when this is the last page.", example = "false")
        boolean last
) {
}
