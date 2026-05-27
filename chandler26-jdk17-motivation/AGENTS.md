# 宝贝激励助手后端项目规则

## Scope

- Backend project: `/Users/chandler/Documents/repository/github/demo-2026/chandler26-jdk17-motivation`
- Frontend project: `/Users/chandler/Documents/repository/github/demo-2026/chandler26-jdk17-motivation-web`
- Treat both projects as one front-back separated product.

## Product

- This is a family motivation system for children. UI copy should prefer `宝贝` over `孩子`.
- Parents manage goals, tasks, task check-in approvals, reward catalog, reward fulfillment, currencies, calendar style, and child profiles.
- Children can see profile home, task calendar, reward calendar, and reward store; child accounts are view/submit/confirm oriented, not rule-management oriented.
- Task calendar, reward calendar, point ledger, reward exchange tickets, child activity logs, and user preferences are first-class persisted surfaces.
- Stars, flowers, and crowns are configurable currency types. They are not just display text.

## Backend

- Use Spring Boot, MyBatis-Plus, MySQL, Spring Security, and JWT.
- Keep controllers thin and put business behavior in services.
- Use DTOs for API requests and responses.
- Throw project-specific runtime exceptions instead of generic runtime errors.
- Business logs should be info-level and readable by business users.
- Runtime logs should be debug-level.
- Important operations should write system log records where appropriate.
- Never modify point/currency balances without writing point ledger records.
- Store durable business state in MySQL, not browser storage: selected child, calendar view habits, reward tickets, point ledger, task records, reward fulfillment flow, and child activity logs must survive refresh and login from another browser.
- Validate role access in services. Parents/guardians can manage; child accounts can only view allowed data and submit/confirm their own flows.
- Reward exchange should check available balance at request time, including pending approvals/occupied amounts, not only at parent approval time.

## Database

- Init SQL lives under `src/main/resources/db/init`.
- Keep numbered domain SQL files and maintain `000_motivation_all_in_one_mysql.sql` for clean database setup.
- If a schema file may already have been executed, create a new patch SQL instead of silently changing executed migration intent.
- Table and column comments must be clear.
- Child-scoped, user-scoped, calendar, task-record, ledger, reward-exchange, and preference queries need indexes.
- After persistence changes, compare SQL, entity, mapper, DTO, service mapping, and frontend API fields.

## AI

- AI suggestions for tasks, rewards, or family routines must be reviewable before becoming persisted business rules.
- Do not let AI-generated content automatically mutate active tasks, point rules, or rewards.
- Never hardcode real API keys.

## Verification

- Run `mvn -q -DskipTests compile` after Java changes.
- Compare entity, mapper, DTO, and SQL fields after persistence changes.
- When adding APIs used by the frontend, update the frontend API wrapper and verify the page flow after backend restart.
