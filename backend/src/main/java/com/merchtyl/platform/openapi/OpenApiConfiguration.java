package com.merchtyl.platform.openapi;

import com.merchtyl.auth.AuthController;
import com.merchtyl.health.HealthController;
import com.merchtyl.platform.admin.PlatformAdministrationController;
import com.merchtyl.platform.testing.TestUserProvisioningController;
import com.merchtyl.platform.web.CorrelationIdFilter;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.RequestHeader;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfiguration {
    public static final String SECURITY_SCHEME_NAME = "bearerAuth";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    @Bean
    OpenAPI merchtylOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Merchtyl API")
                        .description("Retail Commerce and Point of Sale API")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste only the JWT returned by /api/v1/auth/login. Do not prefix it with Bearer."))
                        .addSchemas("ApiError", apiErrorSchema())
                        .addSchemas("ApiFieldViolation", fieldViolationSchema())
                        .addSchemas("PageResponse", pageResponseSchema())
                        .addParameters("Page", integerQueryParameter("page", "Zero-based page number.", 0))
                        .addParameters("Size", integerQueryParameter("size", "Maximum number of records to return.", 20))
                        .addParameters("Sort", stringQueryParameter("sort",
                                "Sort expression when supported by an endpoint, for example generatedAt,desc."))
                        .addParameters("CorrelationId", correlationIdParameter())
                        .addParameters("IdempotencyKey", idempotencyParameter())
                        .addHeaders("Idempotency-Replayed", new Header()
                                .description("True when the response came from a previous request with the same idempotency key.")
                                .schema(new Schema<Boolean>().type("boolean").example(false))))
                .tags(List.of(
                        tag("Authentication", "Login, refresh, logout, and current user endpoints."),
                        tag("Stores", "Store setup and store-aware configuration."),
                        tag("Registers", "Registers, devices, register sessions, and cash movement."),
                        tag("Users and Roles", "User administration, roles, and permissions."),
                        tag("Catalogue", "Products, brands, categories, units, and suppliers."),
                        tag("Taxes", "Tax jurisdictions, components, rates, groups, rules, and calculations."),
                        tag("Inventory", "Inventory balances, transactions, adjustments, and stock counts."),
                        tag("Sales", "Point-of-sale drafts, line items, payments, and completion."),
                        tag("Payments", "Tender and refund payment activity documented through sales and refund APIs."),
                        tag("Receipts", "Receipt rendering and print payloads."),
                        tag("Returns and Refunds", "Returns, refund approvals, and idempotent refund posting."),
                        tag("Lottery", "Lottery sales, payouts, reversals, operators, commissions, and settlements."),
                        tag("Reports", "Sales, register, inventory, lottery, and end-of-day reporting."),
                        tag("Business Day", "Business-day opening, closing, force-close, reopen, and closing reminders."),
                        tag("Audit", "Audit record search."),
                        tag("Hardware Settings", "Register device and hardware-facing settings."),
                        tag("Platform Administration", "Platform-scoped dashboard and tenant lifecycle APIs."),
                        tag("Merchant Onboarding", "Merchant tenant creation and initial owner activation."),
                        tag("Platform Users", "Platform administrator account management."),
                        tag("Merchant Subscriptions", "Platform-managed tenant subscription limits and status."),
                        tag("Platform Audit", "Platform-scoped audit event search."),
                        tag("Merchant Users", "Tenant-scoped manager and cashier visibility, filtering, creation, status, and update APIs."),
                        tag("Store Assignments", "Tenant-validated multi-store manager and cashier assignment APIs."),
                        tag("Store Access", "Assigned-store discovery and store-switch validation for tenant users."),
                        tag("Testing Helpers", "Development and test-only helper APIs. Hidden outside allowed non-production profiles.")));
    }

    @Bean
    OperationCustomizer merchtylOperationCustomizer() {
        return (operation, handlerMethod) -> {
            applyDefaultTag(operation, handlerMethod.getBeanType());
            applySecurity(operation, handlerMethod.getBeanType(), handlerMethod.getMethod());
            applyStandardResponses(operation);
            applyCorrelationIdHeader(operation);
            applyIdempotencyHeader(operation, handlerMethod);
            applyOptimisticLockingNote(operation);
            applyPaginationNotes(operation);
            return operation;
        };
    }

    @Bean
    GroupedOpenApi authenticationApi() {
        return group("Authentication", "/api/v1/auth/**");
    }

    @Bean
    GroupedOpenApi platformAdministrationApi() {
        return group("Platform Administration", "/api/v1/platform/dashboard/**", "/api/v1/platform/settings/**",
                "/api/v1/platform/tenants/**");
    }

    @Bean
    GroupedOpenApi merchantOnboardingApi() {
        return group("Merchant Onboarding", "/api/v1/platform/tenants", "/api/v1/platform/tenants/*/onboarding/**",
                "/api/v1/platform/owner-invitations/**");
    }

    @Bean
    GroupedOpenApi platformUsersApi() {
        return group("Platform Users", "/api/v1/platform/users/**", "/api/v1/platform/auth/**");
    }

    @Bean
    GroupedOpenApi merchantSubscriptionsApi() {
        return group("Merchant Subscriptions", "/api/v1/platform/tenants/*/subscription/**");
    }

    @Bean
    GroupedOpenApi platformAuditApi() {
        return group("Platform Audit", "/api/v1/platform/audit-events/**");
    }

    @Bean
    GroupedOpenApi storesApi() {
        return group("Stores", "/api/v1/stores/**", "/api/v1/features/**");
    }

    @Bean
    GroupedOpenApi registersApi() {
        return group("Registers", "/api/v1/registers/**", "/api/v1/register-sessions/**", "/api/v1/cash-movements/**");
    }

    @Bean
    GroupedOpenApi usersAndRolesApi() {
        return group("Users and Roles", "/api/v1/users/**", "/api/v1/roles/**");
    }

    @Bean
    GroupedOpenApi merchantUsersApi() {
        return group("Merchant Users", "/api/v1/users/**");
    }

    @Bean
    GroupedOpenApi storeAssignmentsApi() {
        return group("Store Assignments", "/api/v1/users/*/store-assignments/**");
    }

    @Bean
    GroupedOpenApi storeAccessApi() {
        return group("Store Access", "/api/v1/store-access/**");
    }

    @Bean
    GroupedOpenApi catalogueApi() {
        return group("Catalogue", "/api/v1/products/**", "/api/v1/brands/**", "/api/v1/categories/**",
                "/api/v1/units/**", "/api/v1/suppliers/**", "/api/v1/product-suppliers/**");
    }

    @Bean
    GroupedOpenApi taxesApi() {
        return group("Taxes", "/api/v1/tax/**");
    }

    @Bean
    GroupedOpenApi inventoryApi() {
        return group("Inventory", "/api/v1/inventory/**");
    }

    @Bean
    GroupedOpenApi salesApi() {
        return group("Sales", "/api/v1/sales/**");
    }

    @Bean
    GroupedOpenApi paymentsApi() {
        return group("Payments", "/api/v1/sales/**", "/api/v1/refunds/**", "/api/v1/lottery/payouts/**");
    }

    @Bean
    GroupedOpenApi receiptsApi() {
        return group("Receipts", "/api/v1/sales/*/receipt/**");
    }

    @Bean
    GroupedOpenApi returnsAndRefundsApi() {
        return group("Returns and Refunds", "/api/v1/returns/**", "/api/v1/refunds/**", "/api/v1/sales/*/returns/**");
    }

    @Bean
    GroupedOpenApi lotteryApi() {
        return group("Lottery", "/api/v1/lottery/**");
    }

    @Bean
    GroupedOpenApi reportsApi() {
        return group("Reports", "/api/v1/reports/**", "/api/v1/end-of-day-reports/**");
    }

    @Bean
    GroupedOpenApi businessDayApi() {
        return group("Business Day", "/api/v1/business-days/**");
    }

    @Bean
    GroupedOpenApi auditApi() {
        return group("Audit", "/api/v1/audit/**");
    }

    @Bean
    GroupedOpenApi hardwareSettingsApi() {
        return group("Hardware Settings", "/api/v1/devices/**");
    }

    @Bean
    @Profile({"dev", "local", "test"})
    @ConditionalOnProperty(prefix = "merchtyl.testing.user-provisioning", name = "enabled", havingValue = "true")
    GroupedOpenApi testingHelpersApi() {
        return group("Testing Helpers", "/api/v1/testing/**");
    }

    private static GroupedOpenApi group(String group, String... paths) {
        return GroupedOpenApi.builder()
                .group(group)
                .pathsToMatch(paths)
                .build();
    }

    private static Tag tag(String name, String description) {
        return new Tag().name(name).description(description);
    }

    private static void applySecurity(Operation operation, Class<?> beanType, Method method) {
        if (isPublicEndpoint(beanType, method)) {
            operation.setSecurity(List.of());
            return;
        }
        if (operation.getSecurity() == null || operation.getSecurity().isEmpty()) {
            operation.addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
        }
    }

    private static boolean isPublicEndpoint(Class<?> beanType, Method method) {
        return beanType == AuthController.class
                && List.of("login", "refresh", "register", "logout").contains(method.getName())
                || beanType == PlatformAdministrationController.class
                && List.of("platformLogin", "activateOwner").contains(method.getName())
                || beanType == TestUserProvisioningController.class
                || beanType == HealthController.class;
    }

    private static void applyDefaultTag(Operation operation, Class<?> beanType) {
        if (operation.getTags() != null && !operation.getTags().isEmpty()) {
            return;
        }
        String packageName = beanType.getPackageName();
        String tag = Map.ofEntries(
                        Map.entry("com.merchtyl.auth", "Authentication"),
                        Map.entry("com.merchtyl.store", "Stores"),
                        Map.entry("com.merchtyl.features", "Stores"),
                        Map.entry("com.merchtyl.register", "Registers"),
                        Map.entry("com.merchtyl.registersession", "Registers"),
                        Map.entry("com.merchtyl.cash", "Registers"),
                        Map.entry("com.merchtyl.device", "Hardware Settings"),
                        Map.entry("com.merchtyl.security", "Merchant Users"),
                        Map.entry("com.merchtyl.platform.admin", "Platform Administration"),
                        Map.entry("com.merchtyl.platform.testing", "Testing Helpers"),
                        Map.entry("com.merchtyl.product", "Catalogue"),
                        Map.entry("com.merchtyl.catalogue", "Catalogue"),
                        Map.entry("com.merchtyl.supplier", "Catalogue"),
                        Map.entry("com.merchtyl.tax", "Taxes"),
                        Map.entry("com.merchtyl.inventory", "Inventory"),
                        Map.entry("com.merchtyl.sales", "Sales"),
                        Map.entry("com.merchtyl.receipts", "Receipts"),
                        Map.entry("com.merchtyl.returns", "Returns and Refunds"),
                        Map.entry("com.merchtyl.refunds", "Returns and Refunds"),
                        Map.entry("com.merchtyl.lottery", "Lottery"),
                        Map.entry("com.merchtyl.reports", "Reports"),
                        Map.entry("com.merchtyl.eod", "Business Day"),
                        Map.entry("com.merchtyl.audit", "Audit"))
                .getOrDefault(packageName, "Merchtyl");
        operation.setTags(List.of(tag));
    }

    private static void applyStandardResponses(Operation operation) {
        addResponse(operation, "400", "Bad request or request validation failed.");
        addResponse(operation, "401", "Authentication is missing, invalid, or expired.");
        addResponse(operation, "403", "Authenticated user does not have the required permission.");
        addResponse(operation, "404", "The requested resource was not found.");
        addResponse(operation, "409", "Conflict, including stale optimistic-lock version values.");
        addResponse(operation, "500", "Unexpected server error.");
    }

    private static void addResponse(Operation operation, String status, String description) {
        operation.getResponses().addApiResponse(status, new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiError")))));
    }

    private static void applyIdempotencyHeader(Operation operation, org.springframework.web.method.HandlerMethod handlerMethod) {
        boolean requiresIdempotency = false;
        for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
            RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
            if (header != null && (IDEMPOTENCY_KEY_HEADER.equals(header.value())
                    || IDEMPOTENCY_KEY_HEADER.equals(header.name()))) {
                requiresIdempotency = true;
                break;
            }
        }
        if (!requiresIdempotency || hasParameter(operation, IDEMPOTENCY_KEY_HEADER)) {
            return;
        }
        operation.addParametersItem(idempotencyParameter());
    }

    private static void applyCorrelationIdHeader(Operation operation) {
        if (!hasParameter(operation, CorrelationIdFilter.HEADER_NAME)) {
            operation.addParametersItem(correlationIdParameter());
        }
    }

    private static boolean hasParameter(Operation operation, String name) {
        return operation.getParameters() != null
                && operation.getParameters().stream().anyMatch(parameter -> name.equals(parameter.getName()));
    }

    private static void applyOptimisticLockingNote(Operation operation) {
        String text = operation.getDescription();
        if (text != null && text.contains("version")) {
            return;
        }
        if (operation.getRequestBody() != null) {
            operation.setDescription(appendSentence(text,
                    "Update and lifecycle request bodies that include a version field use optimistic locking; stale versions return 409 Conflict."));
        }
    }

    private static void applyPaginationNotes(Operation operation) {
        if (operation.getParameters() == null) {
            return;
        }
        boolean hasPage = operation.getParameters().stream().anyMatch(parameter -> "page".equals(parameter.getName()));
        boolean hasSize = operation.getParameters().stream().anyMatch(parameter -> "size".equals(parameter.getName()));
        if (hasPage && hasSize) {
            operation.setDescription(appendSentence(operation.getDescription(),
                    "Paginated responses use content, page, size, totalElements, totalPages, first, and last fields."));
        }
    }

    private static String appendSentence(String existing, String sentence) {
        return existing == null || existing.isBlank() ? sentence : existing + " " + sentence;
    }

    private static Parameter idempotencyParameter() {
        return new Parameter()
                .name(IDEMPOTENCY_KEY_HEADER)
                .in("header")
                .required(true)
                .description("Required for financial and business-day lifecycle operations. Reuse the same key only when retrying the same request body.")
                .schema(new StringSchema().example("close-20260729-register-1"));
    }

    private static Parameter correlationIdParameter() {
        return new Parameter()
                .name(CorrelationIdFilter.HEADER_NAME)
                .in("header")
                .required(false)
                .description("Optional request correlation identifier. When omitted, the API generates one and returns it in the response header.")
                .schema(new StringSchema().example("b8f9428b-07d7-4d03-9c26-8a2ee89f44d3"));
    }

    private static Parameter integerQueryParameter(String name, String description, int example) {
        return new Parameter()
                .name(name)
                .in("query")
                .required(false)
                .description(description)
                .schema(new IntegerSchema().example(example));
    }

    private static Parameter stringQueryParameter(String name, String description) {
        return new Parameter()
                .name(name)
                .in("query")
                .required(false)
                .description(description)
                .schema(new StringSchema());
    }

    private static Schema<?> apiErrorSchema() {
        return new ObjectSchema()
                .description("Standard error response returned by the global exception handler.")
                .addProperty("code", new StringSchema().example("validation_failed"))
                .addProperty("message", new StringSchema().example("Request validation failed"))
                .addProperty("status", new IntegerSchema().example(400))
                .addProperty("path", new StringSchema().example("/api/v1/sales"))
                .addProperty("method", new StringSchema().example("POST"))
                .addProperty("correlationId", new StringSchema().example("b8f9428b-07d7-4d03-9c26-8a2ee89f44d3"))
                .addProperty("violations", new ArraySchema().items(new Schema<>().$ref("#/components/schemas/ApiFieldViolation")))
                .addProperty("timestamp", new StringSchema().format("date-time").example(Instant.parse("2026-07-29T12:00:00Z").toString()));
    }

    private static Schema<?> fieldViolationSchema() {
        return new ObjectSchema()
                .addProperty("field", new StringSchema().example("version"))
                .addProperty("message", new StringSchema().example("must not be null"));
    }

    private static Schema<?> pageResponseSchema() {
        return new ObjectSchema()
                .description("Paginated response wrapper. Sorting is endpoint-specific; when supported, pass a sort query parameter.")
                .addProperty("content", new ArraySchema().description("Current page records."))
                .addProperty("page", new IntegerSchema().example(0))
                .addProperty("size", new IntegerSchema().example(20))
                .addProperty("totalElements", new IntegerSchema().format("int64").example(125))
                .addProperty("totalPages", new IntegerSchema().example(7))
                .addProperty("first", new Schema<Boolean>().type("boolean").example(true))
                .addProperty("last", new Schema<Boolean>().type("boolean").example(false));
    }
}
