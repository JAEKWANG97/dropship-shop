# GitHub And Linear Setup

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
- Project: MVP

## Current Status

The new Linear organization was created through the Linear GraphQL onboarding mutation.

The currently available `LINEAR_API_KEY` still points to the old `mungnyang` organization. To create the `Core` team, `MVP` project, and issues inside the new `Dropship Shop` organization, generate a new API key from the new Linear organization and use that key for subsequent GraphQL calls.

## Next Setup Steps

1. Open Linear and switch to the `Dropship Shop` organization.
2. Create or verify team:
   - Name: Core
   - Key: DS
3. Create project:
   - Name: MVP
4. Create issues from [Linear Backlog](linear-backlog.md).
5. Link GitHub repository:
   - `JAEKWANG97/dropship-shop`

