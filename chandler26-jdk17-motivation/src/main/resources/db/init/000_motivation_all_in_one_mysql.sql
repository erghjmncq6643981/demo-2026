-- 宝贝激励助手全量建表与演示账号初始化脚本。
-- 新环境可以直接执行本文件；已经执行过历史初始化脚本的环境，请按编号执行后续补丁脚本。

CREATE TABLE IF NOT EXISTS motivation_user (
    id BIGINT NOT NULL COMMENT '主键',
    username VARCHAR(64) NOT NULL COMMENT '登录用户名',
    nickname VARCHAR(64) DEFAULT NULL COMMENT '显示昵称',
    password_hash VARCHAR(128) NOT NULL COMMENT '密码哈希',
    password_cipher TEXT DEFAULT NULL COMMENT '密码可查看副本密文，仅用于家长查看/重置孩子子账户密码',
    avatar_url VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
    avatar_data MEDIUMBLOB DEFAULT NULL COMMENT '压缩后的头像图片数据',
    avatar_content_type VARCHAR(64) DEFAULT NULL COMMENT '头像图片 MIME 类型',
    user_type VARCHAR(20) NOT NULL DEFAULT 'PARENT' COMMENT '用户类型：PARENT、GUARDIAN、CHILD',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    last_login_time DATETIME DEFAULT NULL COMMENT '最近登录时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_motivation_user_username (username),
    KEY idx_motivation_user_update_time (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='激励系统用户';

CREATE TABLE IF NOT EXISTS motivation_user_token (
    id BIGINT NOT NULL COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    token_hash VARCHAR(128) NOT NULL COMMENT '访问令牌 SHA-256 哈希',
    device_name VARCHAR(128) DEFAULT NULL COMMENT '设备名称',
    expired_time DATETIME NOT NULL COMMENT '过期时间',
    revoked TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已注销',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_motivation_user_token_hash (token_hash),
    KEY idx_motivation_user_token_user (user_id),
    KEY idx_motivation_user_token_expired_time (expired_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='激励系统登录令牌';

CREATE TABLE IF NOT EXISTS motivation_child (
    id BIGINT NOT NULL COMMENT '主键',
    nickname VARCHAR(64) NOT NULL COMMENT '宝贝昵称',
    avatar_url VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
    avatar_data MEDIUMBLOB DEFAULT NULL COMMENT '压缩后的头像图片数据',
    avatar_content_type VARCHAR(64) DEFAULT NULL COMMENT '头像图片 MIME 类型',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='激励系统宝贝档案';

CREATE TABLE IF NOT EXISTS motivation_family_member (
    id BIGINT NOT NULL COMMENT '主键',
    child_id BIGINT NOT NULL COMMENT '宝贝 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    relation_role VARCHAR(20) NOT NULL DEFAULT 'PARENT' COMMENT '关系角色：PARENT、GUARDIAN、CHILD',
    is_primary TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否主负责人',
    can_manage TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否可管理',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE、INACTIVE',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_motivation_family_member_child_user (child_id, user_id),
    KEY idx_motivation_family_member_user (user_id, status),
    KEY idx_motivation_family_member_child (child_id, status, is_primary)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='家庭成员与宝贝关系';

CREATE TABLE IF NOT EXISTS motivation_goal (
    id BIGINT NOT NULL COMMENT '主键',
    child_id BIGINT NOT NULL COMMENT '宝贝 ID',
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
    child_id BIGINT NOT NULL COMMENT '宝贝 ID',
    name VARCHAR(64) NOT NULL COMMENT '任务名称',
    description VARCHAR(255) DEFAULT NULL COMMENT '任务说明',
    period_type VARCHAR(20) NOT NULL COMMENT '周期类型：DAILY、WEEKLY、MONTHLY',
    schedule_json JSON NOT NULL COMMENT '排期规则 JSON',
    task_color VARCHAR(32) DEFAULT NULL COMMENT '任务颜色',
    point_type VARCHAR(20) NOT NULL COMMENT '货币类型：STAR、FLOWER、CROWN',
    point_color VARCHAR(32) DEFAULT NULL COMMENT '货币颜色',
    base_points INT NOT NULL DEFAULT 0 COMMENT '基础货币数量',
    require_approval TINYINT(1) NOT NULL DEFAULT 0 COMMENT '积分生效方式，1 表示审批后生效',
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
    point_type VARCHAR(20) NOT NULL COMMENT '货币类型',
    point_change INT NOT NULL COMMENT '货币变动，可正可负',
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
    child_id BIGINT NOT NULL COMMENT '宝贝 ID',
    task_date DATE NOT NULL COMMENT '任务所属日期',
    task_name_snapshot VARCHAR(64) NOT NULL COMMENT '任务名称快照',
    task_color_snapshot VARCHAR(32) DEFAULT NULL COMMENT '任务颜色快照',
    point_type_snapshot VARCHAR(20) NOT NULL COMMENT '货币类型快照',
    point_color_snapshot VARCHAR(32) DEFAULT NULL COMMENT '货币颜色快照',
    base_points_snapshot INT NOT NULL DEFAULT 0 COMMENT '基础货币数量快照',
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
    score_awarded INT NOT NULL DEFAULT 0 COMMENT '本次实际入账数量',
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
    child_id BIGINT NOT NULL COMMENT '宝贝 ID',
    point_type VARCHAR(20) NOT NULL COMMENT '货币类型：STAR、FLOWER、CROWN',
    balance INT NOT NULL DEFAULT 0 COMMENT '当前余额',
    earned_total INT NOT NULL DEFAULT 0 COMMENT '累计获得',
    spent_total INT NOT NULL DEFAULT 0 COMMENT '累计消耗',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_motivation_child_point_balance (child_id, point_type),
    KEY idx_motivation_child_point_balance_child (child_id, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='激励系统宝贝货币余额';

CREATE TABLE IF NOT EXISTS motivation_point_ledger (
    id BIGINT NOT NULL COMMENT '主键',
    child_id BIGINT NOT NULL COMMENT '宝贝 ID',
    point_type VARCHAR(20) NOT NULL COMMENT '货币类型：STAR、FLOWER、CROWN',
    change_amount INT NOT NULL COMMENT '货币变动，正数增加、负数扣减',
    balance_after INT NOT NULL COMMENT '变动后余额',
    source_type VARCHAR(32) NOT NULL COMMENT '来源类型：TASK_RECORD、MANUAL_ADJUST、REWARD_EXCHANGE、POINT_EXCHANGE、SYSTEM',
    source_id BIGINT NOT NULL COMMENT '来源 ID',
    source_name VARCHAR(128) DEFAULT NULL COMMENT '来源名称快照',
    reason VARCHAR(255) DEFAULT NULL COMMENT '变动原因',
    operator_user_id BIGINT DEFAULT NULL COMMENT '操作人用户 ID',
    event_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '流水发生时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_motivation_point_ledger_source (source_type, source_id, point_type, child_id),
    KEY idx_motivation_point_ledger_child_time (child_id, point_type, event_time),
    KEY idx_motivation_point_ledger_source (source_type, source_id),
    KEY idx_motivation_point_ledger_operator (operator_user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='激励系统货币流水';

CREATE TABLE IF NOT EXISTS motivation_reward (
    id BIGINT NOT NULL COMMENT '主键',
    child_id BIGINT NOT NULL COMMENT '宝贝 ID',
    name VARCHAR(64) NOT NULL COMMENT '奖励名称',
    description VARCHAR(255) DEFAULT NULL COMMENT '奖励说明',
    reward_icon VARCHAR(32) DEFAULT NULL COMMENT '奖励图标',
    reward_color VARCHAR(32) DEFAULT NULL COMMENT '奖励颜色',
    required_point_type VARCHAR(20) NOT NULL COMMENT '所需货币类型',
    required_points INT NOT NULL COMMENT '所需货币数量',
    stock_total INT NOT NULL DEFAULT 0 COMMENT '总库存',
    stock_remaining INT NOT NULL DEFAULT 0 COMMENT '剩余库存',
    exchange_limit_type VARCHAR(20) NOT NULL DEFAULT 'UNLIMITED' COMMENT '兑换限制：UNLIMITED、DAILY、WEEKLY、MONTHLY',
    exchange_limit_count INT NOT NULL DEFAULT 0 COMMENT '限制次数',
    fulfillment_type VARCHAR(32) NOT NULL DEFAULT 'INVENTORY_DEDUCT' COMMENT '奖励实现方式：INVENTORY_DEDUCT、PARENT_EXECUTE、PARENT_PURCHASE、PARENT_FULFILL',
    require_approval TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否需要家长确认',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE、PAUSED、ARCHIVED',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    created_by_user_id BIGINT DEFAULT NULL COMMENT '创建人用户 ID',
    updated_by_user_id BIGINT DEFAULT NULL COMMENT '更新人用户 ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_motivation_reward_child_status (child_id, status, deleted, update_time),
    KEY idx_motivation_reward_point_type (child_id, required_point_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='激励系统奖励';

CREATE TABLE IF NOT EXISTS motivation_reward_exchange (
    id BIGINT NOT NULL COMMENT '主键',
    reward_id BIGINT NOT NULL COMMENT '奖励 ID',
    child_id BIGINT NOT NULL COMMENT '宝贝 ID',
    reward_name_snapshot VARCHAR(64) NOT NULL COMMENT '奖励名称快照',
    reward_color_snapshot VARCHAR(32) DEFAULT NULL COMMENT '奖励颜色快照',
    reward_icon_snapshot VARCHAR(32) DEFAULT NULL COMMENT '奖励图标快照',
    required_point_type VARCHAR(20) NOT NULL COMMENT '所需货币类型快照',
    required_points_snapshot INT NOT NULL COMMENT '所需货币数量快照',
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED' COMMENT '主流程状态：REQUESTED、APPROVED、REJECTED、COMPLETED、CANCELLED',
    fulfillment_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '礼物实现状态：PENDING、SCHEDULED、IN_PROGRESS、COMPLETED、CONFIRMED',
    branch_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '奖励分支状态：PENDING、PURCHASE_ORDERED、PURCHASE_SHIPPING、PURCHASE_ARRIVED、SCHEDULED、IN_PROGRESS、COMPLETED',
    requested_by_user_id BIGINT DEFAULT NULL COMMENT '发起人用户 ID',
    reviewed_by_user_id BIGINT DEFAULT NULL COMMENT '审核人用户 ID',
    fulfillment_updated_by_user_id BIGINT DEFAULT NULL COMMENT '礼物状态更新人用户 ID',
    deducted_ledger_id BIGINT DEFAULT NULL COMMENT '扣减流水 ID',
    requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发起时间',
    reviewed_at DATETIME DEFAULT NULL COMMENT '审核时间',
    fulfillment_updated_at DATETIME DEFAULT NULL COMMENT '礼物状态更新时间',
    expected_arrival_date DATE DEFAULT NULL COMMENT '家长购买奖励的预期到达日期',
    schedule_start_date DATE DEFAULT NULL COMMENT '家长实现奖励的日程开始日期',
    schedule_end_date DATE DEFAULT NULL COMMENT '家长实现奖励的日程结束日期',
    completed_at DATETIME DEFAULT NULL COMMENT '完成时间',
    confirmed_by_user_id BIGINT DEFAULT NULL COMMENT '礼物确认人用户 ID',
    confirmed_at DATETIME DEFAULT NULL COMMENT '礼物确认时间',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_motivation_reward_exchange_child (child_id, status, create_time),
    KEY idx_motivation_reward_exchange_reward (reward_id, status, create_time),
    KEY idx_motivation_reward_exchange_request_time (requested_at),
    KEY idx_motivation_reward_exchange_deducted_ledger (deducted_ledger_id),
    KEY idx_motivation_reward_exchange_fulfillment (child_id, fulfillment_status, update_time),
    KEY idx_motivation_reward_exchange_branch_date (child_id, branch_status, expected_arrival_date, schedule_start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='激励系统奖励兑换记录';

CREATE TABLE IF NOT EXISTS motivation_system_log (
    id BIGINT NOT NULL COMMENT '主键',
    user_id BIGINT DEFAULT NULL COMMENT '操作用户 ID',
    child_id BIGINT DEFAULT NULL COMMENT '关联宝贝 ID',
    log_type VARCHAR(50) NOT NULL COMMENT '日志类型：AUTH、TASK、POINT、REWARD、CALENDAR、SYSTEM',
    title VARCHAR(128) NOT NULL COMMENT '日志标题',
    detail TEXT DEFAULT NULL COMMENT '日志详情',
    source VARCHAR(50) DEFAULT NULL COMMENT '来源模块',
    trace_id VARCHAR(64) DEFAULT NULL COMMENT '链路追踪 ID',
    request_ip VARCHAR(64) DEFAULT NULL COMMENT '请求 IP',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_motivation_system_log_user_time (user_id, create_time),
    KEY idx_motivation_system_log_child_time (child_id, create_time),
    KEY idx_motivation_system_log_type_time (log_type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='激励系统业务日志';

CREATE TABLE IF NOT EXISTS motivation_point_exchange_rule (
    id BIGINT NOT NULL COMMENT '主键',
    child_id BIGINT NOT NULL COMMENT '宝贝 ID',
    star_weight INT NOT NULL DEFAULT 1 COMMENT '星星币币值，默认 1',
    flower_weight INT NOT NULL DEFAULT 10 COMMENT '红花币币值，默认 10',
    crown_weight INT NOT NULL DEFAULT 100 COMMENT '皇冠币币值，默认 100',
    created_by_user_id BIGINT DEFAULT NULL COMMENT '创建人用户 ID',
    updated_by_user_id BIGINT DEFAULT NULL COMMENT '更新人用户 ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_motivation_point_exchange_rule_child (child_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='激励系统积分币值';

CREATE TABLE IF NOT EXISTS motivation_point_currency (
    id BIGINT NOT NULL COMMENT '主键',
    child_id BIGINT NOT NULL COMMENT '宝贝 ID',
    point_type VARCHAR(20) NOT NULL COMMENT '货币类型：STAR、FLOWER、CROWN',
    name VARCHAR(32) NOT NULL COMMENT '币值名称，例如星星、红花、皇冠',
    icon VARCHAR(16) NOT NULL COMMENT '币值图标',
    color VARCHAR(32) DEFAULT NULL COMMENT '币值展示颜色',
    exchange_weight INT NOT NULL DEFAULT 1 COMMENT '币值比例，星星币默认 1、红花币默认 10、皇冠币默认 100',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE、INACTIVE',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0 否，1 是',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    created_by_user_id BIGINT DEFAULT NULL COMMENT '创建人用户 ID',
    updated_by_user_id BIGINT DEFAULT NULL COMMENT '更新人用户 ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_motivation_point_currency_child_type (child_id, point_type, deleted),
    KEY idx_motivation_point_currency_child (child_id, status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='激励系统币值配置';

CREATE TABLE IF NOT EXISTS motivation_user_preference (
    id BIGINT NOT NULL COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    preference_key VARCHAR(64) NOT NULL COMMENT '配置项，例如 selectedChildId、taskCalendarViewMode',
    preference_value VARCHAR(512) DEFAULT NULL COMMENT '配置值',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_motivation_user_preference_user_key (user_id, preference_key),
    KEY idx_motivation_user_preference_user_time (user_id, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='激励系统用户偏好配置';

-- 演示账号：
--   家长：demo-parent / 123456
--   孩子：demo-child / 123456
INSERT INTO motivation_user (
    id,
    username,
    nickname,
    password_hash,
    user_type,
    enabled,
    deleted
) VALUES (
    202605250002,
    'demo-child',
    '小星',
    'sha256$6368696c642d64656d6f2d32303236$43003bf4171a4b22deac076934ec4828f4c8e52118e8f2953b65ea692aa7d2bf',
    'CHILD',
    1,
    0
)
ON DUPLICATE KEY UPDATE
    nickname = VALUES(nickname),
    password_hash = VALUES(password_hash),
    user_type = 'CHILD',
    enabled = 1,
    deleted = 0;

INSERT INTO motivation_user (
    id,
    username,
    nickname,
    password_hash,
    user_type,
    enabled,
    deleted
)
SELECT
    202605250001,
    'demo-parent',
    '星星家长',
    'sha256$706172656e742d64656d6f2d32303236$8bf31f00566b4d45c64f97618146721fcf378dcf25b19b81871c3c3701f56d41',
    'PARENT',
    1,
    0
WHERE NOT EXISTS (
    SELECT 1 FROM motivation_user WHERE username = 'demo-parent'
);

UPDATE motivation_user
SET nickname = '星星家长',
    password_hash = 'sha256$706172656e742d64656d6f2d32303236$8bf31f00566b4d45c64f97618146721fcf378dcf25b19b81871c3c3701f56d41',
    user_type = 'PARENT',
    enabled = 1,
    deleted = 0
WHERE username = 'demo-parent';

SET @demo_parent_user_id = (
    SELECT id FROM motivation_user WHERE username = 'demo-parent' LIMIT 1
);
SET @demo_child_user_id = (
    SELECT id FROM motivation_user WHERE username = 'demo-child' LIMIT 1
);
SET @demo_child_id = (
    SELECT fm.child_id
    FROM motivation_family_member fm
    JOIN motivation_child c ON c.id = fm.child_id
    WHERE fm.user_id = @demo_parent_user_id
      AND fm.status = 'ACTIVE'
      AND c.deleted = 0
    ORDER BY fm.is_primary DESC, c.update_time DESC
    LIMIT 1
);

INSERT INTO motivation_child (
    id,
    nickname,
    gender,
    remark,
    status,
    deleted,
    created_by_user_id
)
SELECT
    202605250101,
    '小星',
    'UNKNOWN',
    '1.0 默认宝贝档案',
    'ACTIVE',
    0,
    @demo_parent_user_id
WHERE @demo_child_id IS NULL;

SET @demo_child_id = COALESCE(@demo_child_id, 202605250101);

INSERT INTO motivation_family_member (
    id,
    child_id,
    user_id,
    relation_role,
    is_primary,
    can_manage,
    status
)
SELECT
    202605250201,
    @demo_child_id,
    @demo_parent_user_id,
    'PARENT',
    1,
    1,
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
    FROM motivation_family_member
    WHERE child_id = @demo_child_id
      AND user_id = @demo_parent_user_id
);

UPDATE motivation_family_member
SET relation_role = 'PARENT',
    is_primary = 1,
    can_manage = 1,
    status = 'ACTIVE'
WHERE child_id = @demo_child_id
  AND user_id = @demo_parent_user_id;

INSERT INTO motivation_family_member (
    id,
    child_id,
    user_id,
    relation_role,
    is_primary,
    can_manage,
    status
)
SELECT
    202605250202,
    @demo_child_id,
    @demo_child_user_id,
    'CHILD',
    0,
    0,
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
    FROM motivation_family_member
    WHERE child_id = @demo_child_id
      AND user_id = @demo_child_user_id
);

UPDATE motivation_family_member
SET relation_role = 'CHILD',
    is_primary = 0,
    can_manage = 0,
    status = 'ACTIVE'
WHERE child_id = @demo_child_id
  AND user_id = @demo_child_user_id;
