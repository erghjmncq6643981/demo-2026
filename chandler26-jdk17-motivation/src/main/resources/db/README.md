# 激励系统数据库脚本说明

## 新库初始化顺序

1. `init/00_motivation_auth_init_mysql.sql`
2. `init/10_motivation_family_goal_task_init_mysql.sql`
3. `init/20_motivation_point_reward_init_mysql.sql`
4. `init/30_motivation_operational_init_mysql.sql`
5. `init/50_motivation_point_exchange_rule_mysql.sql`
6. `init/60_motivation_reward_fulfillment_mysql.sql`
7. `init/70_motivation_point_currency_mysql.sql`

## 说明

- `00` 负责用户登录与令牌。
- `10` 负责孩子、家庭成员、目标、任务、规则、任务记录与积分余额。
- `20` 负责积分流水、奖励与兑换记录。
- `30` 负责业务日志。
- `50` 负责星星、红花、皇冠之间的兑换比例。
- `60` 负责奖励实现方式和礼物券状态。
- `70` 负责币值名称、图标、颜色和比例配置。
- 脚本按全新数据库初始化设计，方便你直接执行。
