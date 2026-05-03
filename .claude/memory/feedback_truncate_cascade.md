---
name: Test cleanup requires TRUNCATE CASCADE
description: Always use TRUNCATE ... CASCADE in test @BeforeEach cleanup — user_library_tracks and refresh_tokens both reference users
type: feedback
---

Always use `TRUNCATE ... CASCADE` in test `@BeforeEach` cleanup methods, never plain `TRUNCATE`.

**Why:** `user_library_tracks` and `refresh_tokens` both have foreign keys on `users`. A plain `TRUNCATE refresh_tokens, users` fails with a Postgres FK constraint error if any other table references `users`. CASCADE handles the whole dependency chain safely.

**How to apply:** Any time a new FK referencing `users` or `tracks` is added, existing test cleanup statements are already safe because CASCADE propagates. When writing new tests, always use `TRUNCATE <tables> CASCADE`.
