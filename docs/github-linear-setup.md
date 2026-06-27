# GitHub And Linear Setup Record

This document records the initial GitHub and Linear setup for Dropship Shop.

Later issue planning is tracked in [Linear Backlog](linear-backlog.md) and Linear.

## GitHub

Repository:

- Owner: JAEKWANG97
- Name: dropship-shop
- Visibility: private
- URL: https://github.com/JAEKWANG97/dropship-shop
- Default branch: main

Initial commit:

- `docs: initialize dropship shop planning`

## Linear

Created organization:

- Name: Dropship Shop
- URL key: dropship-shop
- Organization ID: c3136ee1-43f7-4ad6-a428-4630f1813c82

Recommended structure:

- Team: Core
- Team key: DS
- Team ID: aeb40ff2-507c-447a-8964-825302520b12
- Project: MVP
- Project ID: 1e6d10e2-c4ac-4f5f-bd68-79d591838a6a
- Project URL: https://linear.app/dropship-shop/project/mvp-c80b0bb67771

## Current Status

The new Linear organization was created through the Linear GraphQL onboarding mutation.

The `Core` team, `MVP` project, and MVP backlog issues were created in the new Linear organization.

GitHub Autolink is configured:

- `DS-123` links to `https://linear.app/dropship-shop/issue/DS-123`

Created initial issues:

- Initial issues DS-1 through DS-17

## Remaining Setup Steps

1. Enable Linear GitHub integration from the Linear UI:
   - `JAEKWANG97/dropship-shop`
2. Grant Linear GitHub App access to this private repository.
3. Configure Linear automation for PR status updates.
4. Decide whether to keep or archive the default `Dropship Shop / DRO` team that Linear created during organization onboarding.
5. Keep API keys out of git and local documentation.

## PR Workflow

Use Linear issue ids in branch names, PR titles, and PR descriptions.

Examples:

- Branch: `feature/ds-4-backend-scaffold`
- PR title: `DS-4 Scaffold Spring Boot backend`
- PR body: `Fixes DS-4`

See [Development Workflow](development-workflow.md).
