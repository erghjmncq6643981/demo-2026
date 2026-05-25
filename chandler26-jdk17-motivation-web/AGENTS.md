# Motivation Frontend Project Rules

## Scope

- Frontend project: `/Users/chandler/Documents/repository/github/demo-2026/chandler26-jdk17-motivation-web`
- Backend project: `/Users/chandler/Documents/repository/github/demo-2026/chandler26-jdk17-motivation`
- Treat both projects as one front-back separated product.

## Structure

- Keep feature code under `public/src/features/**`.
- Keep shared utilities under `public/src/shared/**`.
- Keep `public/app.js` as a small entry and wiring layer.
- Avoid growing a large single-file prototype once 1.0 development starts.

## UI

- The product should feel bright, focused, and suitable for children.
- Parent views should stay clear, reviewable, and efficient.
- Calendar is a core view, inspired by Notion's clear month layout.
- Tasks, calendar events, stars, flowers, crowns, and rewards should support rich color configuration.
- Avoid text overflow, hidden controls, and fixed-width mobile breakage.

## Interaction

- Destructive actions, point penalties, and context-losing navigation need confirmation.
- Manual bonus and penalty flows must require a reason.
- Reward exchange and parent approval states should be visible.
- Verify desktop and mobile behavior for layout or interaction changes.

## Verification

- Run `node --check` on changed JS modules.
- Use the browser to verify visual and interaction changes.
