# Cypress E2E Tests

End-to-end tests that exercise the Angular UI and Spring Boot API together against a real running stack.

## Prerequisites

The full stack must be running before executing tests.

Test credentials are configured in `cypress.config.ts` (`username: test`, `password: test`).

---

## Writing and Generating Tests

### Step 1 — Write a user story

Copy the template and fill it in:

```bash
cp cypress/user-stories/TEMPLATE.md cypress/user-stories/<feature>.md
```

Edit the file to describe the feature in plain English — the role, the steps, and what you expect to see. Example:

```
cypress/user-stories/create-codesystem.md
```

### Step 2 — Ask AI to generate the test

Open Cursor / Claude Code in this repository and ask:

> "Generate a Cypress test for the story in cypress/user-stories/create-codesystem.md"

The AI will:
- Read your user story
- Inspect the relevant Angular component templates to find or add stable `data-cy` selectors
- Write the test to `cypress/e2e/<feature>.cy.ts`

### Step 3 — Run the test

**Interactive (recommended for development):**
```bash
cd angular-ui && npx cypress open
```
Select the test file from the Cypress UI.

**Headless (for CI or quick runs):**
```bash
cd angular-ui && npx cypress run --spec "cypress/e2e/<feature>.cy.ts"
```

**Run all tests:**
```bash
cd angular-ui && npx cypress run
```

---

## Directory Structure

```
cypress/
  e2e/                  # Test files (.cy.ts) — one per feature
  user-stories/         # Plain-English user stories (.md) — source of truth for tests
    TEMPLATE.md         # Copy this to start a new story
  support/
    commands.ts         # Custom commands (cy.login(), etc.)
    e2e.ts              # Loaded before every test; imports commands
  fixtures/             # Static JSON data for tests (if needed)
```

---

## Custom Commands

| Command | Description |
|---------|-------------|
| `cy.login()` | Clears cookies, visits the app, accepts the legal agreement if present, and logs in via IMS using the credentials in `cypress.config.ts`. |

Use `cy.login()` in `beforeEach` so each test starts authenticated:

```typescript
beforeEach(() => {
  cy.login()
  cy.visit('/your-route')
})
```
