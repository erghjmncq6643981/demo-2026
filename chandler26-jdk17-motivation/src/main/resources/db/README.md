# 激励系统数据库脚本说明

## 新库初始化顺序

1. `init/00_motivation_auth_init_mysql.sql`
2. `init/10_motivation_family_goal_task_init_mysql.sql`
3. `init/20_motivation_point_reward_init_mysql.sql`
4. `init/30_motivation_operational_init_mysql.sql`

## 说明

- `00` 负责用户登录与令牌。
- `10` 负责孩子、家庭成员、目标、任务、规则、任务记录与积分余额。
- `20` 负责积分流水、奖励与兑换记录。
- `30` 负责业务日志。
- 脚本按全新数据库初始化设计，方便你直接执行。
