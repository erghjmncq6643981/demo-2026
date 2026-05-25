# Motivation Backend Project Rules

## Scope

- Backend project: `/Users/chandler/Documents/repository/github/demo-2026/chandler26-jdk17-motivation`
- Frontend project: `/Users/chandler/Documents/repository/github/demo-2026/chandler26-jdk17-motivation-web`
- Treat both projects as one front-back separated product.

## Product

- This is a family child-incentive system.
- Parents create goals, tasks, score rules, and rewards.
- Children complete daily, weekly, and monthly tasks to earn stars, flowers, or crowns.
- Calendar, point ledger, and reward exchange history are first-class product surfaces.

## Backend

- Use Spring Boot, MyBatis-Plus, MySQL, Spring Security, and JWT.
- Keep controllers thin and put business behavior in services.
- Use DTOs for API requests and responses.
- Throw project-specific runtime exceptions instead of generic runtime errors.
- Business logs should be info-level and readable by business users.
- Runtime logs should be debug-level.
- Important operations should write system log records where appropriate.
- Never modify score balances without writing point ledger records.

## Database

- Init SQL lives under `src/main/resources/db/init`.
- Keep SQL grouped by domain and easy to execute on a clean database.
- If a schema file may already have been executed, create a new patch SQL instead of silently changing executed migration intent.
- Table and column comments must be clear.
- Child-scoped, user-scoped, calendar, task-record, ledger, and reward-exchange queries need indexes.

## AI

- AI suggestions for tasks, rewards, or family routines must be reviewable before becoming persisted business rules.
- Do not let AI-generated content automatically mutate active tasks, point rules, or rewards.
- Never hardcode real API keys.

## Verification

- Run `mvn -q -DskipTests compile` after Java changes.
- Compare entity, mapper, DTO, and SQL fields after persistence changes.
