---
name: Always annotate new JAX-RS endpoints with MicroProfile OpenAPI annotations
description: Every new JAX-RS resource method must get @Operation, @APIResponse, and @Tag — user explicitly requested this as a standing rule
type: feedback
---

Every new JAX-RS resource class and method must include MicroProfile OpenAPI annotations.

**Why:** The project uses code-first OpenAPI via quarkus-smallrye-openapi. The spec at `GET /q/openapi` is the contract consumed by clients and OpenAPI tooling. Unannotated endpoints produce an incomplete, unusable spec.

**How to apply:**
- Class level: `@Tag(name = "...", description = "...")`
- Method level: `@Operation(summary = "...", description = "...")` + one `@APIResponse` per meaningful HTTP status code
- Path/query params: `@Parameter(description = "...")` when the name alone isn't self-explanatory
- Import only the annotations you actually use — the IDE will warn on unused imports
- `HealthResource` is Vert.x router-based (not JAX-RS) — OpenAPI annotations do not apply there
