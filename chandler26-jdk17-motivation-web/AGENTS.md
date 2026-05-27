# 宝贝激励助手前端项目规则

## Scope

- Frontend project: `/Users/chandler/Documents/repository/github/demo-2026/chandler26-jdk17-motivation-web`
- Backend project: `/Users/chandler/Documents/repository/github/demo-2026/chandler26-jdk17-motivation`
- Treat both projects as one front-back separated product.

## Structure

- Keep feature code under `public/src/features/**`.
- Keep shared utilities under `public/src/shared/**`.
- Keep `public/app.js` as a small entry and wiring layer.
- Avoid growing a large single-file prototype once 1.0 development starts.
- API calls belong in `public/src/shared/api.js`; display text/status mapping belongs in shared text helpers.

## UI

- The product should feel bright, focused, and suitable for children.
- Parent views should stay clear, reviewable, and efficient.
- Calendar is a core view, inspired by Notion's clear month layout.
- Tasks, calendar events, stars, flowers, crowns, and rewards should support rich color configuration.
- Avoid text overflow, hidden controls, and fixed-width mobile breakage.
- Prefer icon-first, child-readable interactions for balances, task rewards, reward tickets, and confirmations.
- Left navigation must support collapse/expand and mobile use. Child accounts only show profile home, task calendar, reward calendar, and reward store.
- Management lists should use concise filters by default, with secondary filters behind a more/advanced control.

## Interaction

- Destructive actions, point penalties, and context-losing navigation need confirmation.
- Manual bonus and penalty flows must require a reason.
- Reward exchange and parent approval states should be visible.
- Add/edit flows should use modals, and delete actions must use a second confirmation.
- User habits that should survive refresh, such as selected child and task/reward calendar view mode, must be persisted through backend preferences when online.
- Calendar date cells need minimum usable width; task names in cells should stay compact and not hide the date.
- Task check-in reward selection should use the configured currency icon/color and support adjusting the awarded count.
- Reward store purchase modals must show cost, balance, insufficient-balance hints, higher-currency payment, and change clearly.
- Verify desktop and mobile behavior for layout or interaction changes.

## Verification

- Run `node --check` on changed JS modules.
- Use the browser to verify visual and interaction changes.
- For role-sensitive changes, verify both parent and child account views.
