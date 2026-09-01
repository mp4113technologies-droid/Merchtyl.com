# Database error mapping

Merchtyl validates predictable business conflicts in the service layer and retains database constraints as the concurrency-safe fallback.

Persistence failures flow through `GlobalExceptionHandler` into `DatabaseConstraintErrorMapper`. The mapper reads Hibernate/PostgreSQL structured constraint metadata, maps known constraint names to stable API codes and optional field violations, and classifies unknown constraints by SQLState. The API response contains only the safe code/message, while the `DATABASE_WRITE_FAILED` server log retains SQLState, constraint name, exception type, request path, and correlation ID.

To add a mapping:

1. Give the database constraint a stable explicit name in the next Flyway migration when it does not already have one.
2. Add the constraint to `DatabaseConstraintErrorMapper` with its safe code, message, status, and form field.
3. Add equivalent service-layer validation where the conflict is inexpensive to detect.
4. Add handler tests for the safe response and service/integration tests for both preflight and database-race paths.

Never include SQL, table names, constraint names, PostgreSQL details, credentials, tokens, or payment data in API messages.
