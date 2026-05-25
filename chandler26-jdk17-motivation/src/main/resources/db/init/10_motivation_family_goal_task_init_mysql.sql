-- 激励系统孩子、家庭成员、目标、任务、任务规则、任务记录与积分余额初始化脚本。
-- 适用于全新数据库初始化。

CREATE TABLE IF NOT EXISTS motivation_child (
    id BIGINT NOT NULL COMMENT '主键',
    nickname VARCHAR(64) NOT NULL COMMENT '孩子昵称',
    avatar_url VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
    birthday DATE DEFAULT NULL COMMENT '生日',
    gender VARCHAR(20) DEFAULT NULL COMMENT '性别：BOY、GIRL、UNKNOWN',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE、INACTIVE',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除',
    created_by_user_id BIGINT DEFAULT NULL COMMENT '创建人用户 ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_motivation_child_status (status, deleted, update_time),
    KEY idx_motivation_child_created_by (created_by_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='激励系统孩子档案';

CREATE TABLE IF NOT EXISTS motivation_family_member (
    id BIGINT NOT NULL COMMENT '主键',
    child_id BIGINT NOT NULL COMMENT '孩子 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    relation_role VARCHAR(20) NOT NULL DEFAULT 'PARENT' COMMENT '关系角色：PARENT、GUARDIAN',
    is_primary TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否主负责人',
    can_manage TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否可管理',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE、INACTIVE',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_motivation_family_member_child_user (child_id, user_id),
    KEY idx_motivation_family_member_user (user_id, status),
    KEY idx_motivation_family_member_child (child_id, status, is_primary)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='家庭成员与孩子关系';

CREATE TABLE IF NOT EXISTS motivation_goal (
    id BIGINT NOT NULL COMMENT '主键',
    child_id BIGINT NOT NULL COMMENT '孩子 ID',
    name VARCHAR(64) NOT NULL COMMENT '目标名称',
    description VARCHAR(255) DEFAULT NULL COMMENT '目标描述',
    goal_color VARCHAR(32) DEFAULT NULL COMMENT '目标颜色',
    icon VARCHAR(32) DEFAULT NULL COMMENT '目标图标',
    start_date DATE DEFAULT NULL COMMENT '开始日期',
    end_date DATE DEFAULT NULL COMMENT '结束日期',
    target_points INT NOT NULL DEFAULT 0 COMMENT '目标积分',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE、PAUSED、FINISHED',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    created_by_user_id BIGINT DEFAULT NULL COMMENT '创建人用户 ID',
    updated_by_user_id BIGINT DEFAULT NULL COMMENT '更新人用户 ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_motivation_goal_child (child_id, status, deleted, update_time),
    KEY idx_motivation_goal_created_by (created_by_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='激励系统成长目标';

CREATE TABLE IF NOT EXISTS motivation_task (
    id BIGINT NOT NULL COMMENT '主键',
    goal_id BIGINT NOT NULL COMMENT '目标 ID',
    child_id BIGINT NOT NULL COMMENT '孩子 ID',
    name VARCHAR(64) NOT NULL COMMENT '任务名称',
    description VARCHAR(255) DEFAULT NULL COMMENT '任务说明',
    period_type VARCHAR(20) NOT NULL COMMENT '周期类型：DAILY、WEEKLY、MONTHLY',
    schedule_json JSON NOT NULL COMMENT '排期规则 JSON',
    task_color VARCHAR(32) DEFAULT NULL COMMENT '任务颜色',
    point_type VARCHAR(20) NOT NULL COMMENT '积分类型：STAR、FLOWER、CROWN',
    point_color VARCHAR(32) DEFAULT NULL COMMENT '积分颜色',
    base_points INT NOT NULL DEFAULT 0 COMMENT '基础积分',
    require_approval TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否需要家长审核',
    allow_penalty TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否允许扣分',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE、PAUSED、ARCHIVED',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    created_by_user_id BIGINT DEFAULT NULL COMMENT '创建人用户 ID',
    updated_by_user_id BIGINT DEFAULT NULL COMMENT '更新人用户 ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_motivation_task_child (child_id, status, deleted, update_time),
    KEY idx_motivation_task_goal (goal_id, status, deleted, sort_no),
    KEY idx_motivation_task_period (child_id, period_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='激励系统任务';

CREATE TABLE IF NOT EXISTS motivation_task_rule (
    id BIGINT NOT NULL COMMENT '主键',
    task_id BIGINT NOT NULL COMMENT '任务 ID',
    rule_code VARCHAR(64) NOT NULL COMMENT '规则编码',
    rule_name VARCHAR(64) NOT NULL COMMENT '规则名称',
    trigger_type VARCHAR(32) NOT NULL COMMENT '触发类型：ONTIME、EARLY、LATE、STREAK、MANUAL_BONUS、MANUAL_PENALTY',
    condition_json JSON DEFAULT NULL COMMENT '条件 JSON',
    point_type VARCHAR(20) NOT NULL COMMENT '积分类型',
    point_change INT NOT NULL COMMENT '积分变动，可正可负',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_motivation_task_rule_code (task_id, rule_code),
    KEY idx_motivation_task_rule_task (task_id, trigger_type, enabled, deleted, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='激励系统任务规则';

CREATE TABLE IF NOT EXISTS motivation_task_record (
    id BIGINT NOT NULL COMMENT '主键',
    task_id BIGINT NOT NULL COMMENT '任务 ID',
    goal_id BIGINT NOT NULL COMMENT '目标 ID',
    child_id BIGINT NOT NULL COMMENT '孩子 ID',
    task_date DATE NOT NULL COMMENT '任务所属日期',
    task_name_snapshot VARCHAR(64) NOT NULL COMMENT '任务名称快照',
    task_color_snapshot VARCHAR(32) DEFAULT NULL COMMENT '任务颜色快照',
    point_type_snapshot VARCHAR(20) NOT NULL COMMENT '积分类型快照',
    point_color_snapshot VARCHAR(32) DEFAULT NULL COMMENT '积分颜色快照',
    base_points_snapshot INT NOT NULL DEFAULT 0 COMMENT '基础积分快照',
    schedule_snapshot_json JSON DEFAULT NULL COMMENT '排期快照 JSON',
    rule_snapshot_json JSON DEFAULT NULL COMMENT '规则快照 JSON',
    completion_progress INT NOT NULL DEFAULT 0 COMMENT '完成进度 0-100',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING、SUBMITTED、APPROVED、REJECTED、SKIPPED',
    source_type VARCHAR(20) NOT NULL DEFAULT 'CHILD' COMMENT '来源类型：CHILD、PARENT、SYSTEM',
    submitted_by_user_id BIGINT DEFAULT NULL COMMENT '提交人用户 ID',
    submitted_at DATETIME DEFAULT NULL COMMENT '提交时间',
    reviewed_by_user_id BIGINT DEFAULT NULL COMMENT '审核人用户 ID',
    reviewed_at DATETIME DEFAULT NULL COMMENT '审核时间',
    review_remark VARCHAR(255) DEFAULT NULL COMMENT '审核备注',
    score_awarded INT NOT NULL DEFAULT 0 COMMENT '本次实际入账积分',
    attachment_json JSON DEFAULT NULL COMMENT '打卡附件或补充信息 JSON',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_motivation_task_record_task_date (task_id, task_date),
    KEY idx_motivation_task_record_child_date (child_id, task_date, status),
    KEY idx_motivation_task_record_goal_date (goal_id, task_date, status),
    KEY idx_motivation_task_record_review (status, reviewed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='激励系统任务记录';

CREATE TABLE IF NOT EXISTS motivation_child_point_balance (
    id BIGINT NOT NULL COMMENT '主键',
    child_id BIGINT NOT NULL COMMENT '孩子 ID',
    point_type VARCHAR(20) NOT NULL COMMENT '积分类型：STAR、FLOWER、CROWN',
    balance INT NOT NULL DEFAULT 0 COMMENT '当前余额',
    earned_total INT NOT NULL DEFAULT 0 COMMENT '累计获得',
    spent_total INT NOT NULL DEFAULT 0 COMMENT '累计消耗',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_motivation_child_point_balance (child_id, point_type),
    KEY idx_motivation_child_point_balance_child (child_id, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='激励系统孩子积分余额';
