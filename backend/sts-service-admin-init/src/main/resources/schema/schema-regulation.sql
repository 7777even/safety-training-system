-- Integration-test schema: regulation
SET NAMES utf8mb4;

CREATE TABLE `sts_regulation_warning_rule`
(
  `warning_rule_id`  bigint        NOT NULL AUTO_INCREMENT COMMENT '预警规则ID（主键）',
  `organization_id`  bigint        NOT NULL COMMENT '所属机构ID',
  `rule_code`        varchar(64)   NOT NULL COMMENT '规则编码',
  `rule_name`        varchar(100)  NOT NULL COMMENT '规则名称',
  `warning_type`     varchar(32)   NOT NULL COMMENT '预警类型',
  `training_type_id` bigint                 DEFAULT NULL COMMENT '培训类型ID',
  `warning_level`    varchar(16)   NOT NULL COMMENT '预警级别（INFO/WARN/CRITICAL）',
  `valid_from`       datetime               DEFAULT NULL COMMENT '有效开始',
  `valid_to`         datetime               DEFAULT NULL COMMENT '有效结束',
  `status`           tinyint       NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `description`      varchar(1000) NOT NULL COMMENT '描述',
  `create_by`        bigint                 DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`      datetime               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        bigint                 DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`      datetime               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`          bigint        NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`          int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`warning_rule_id`),
  UNIQUE KEY `uk_org_code_deleted` (`organization_id`, `rule_code`, `deleted`),
  KEY                `idx_type_status` (`warning_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='【内部保留】用于：固定系统学时不足规则；规则管理字段不提供独立页面或对外操作';

CREATE TABLE `sts_regulation_warning_condition`
(
  `warning_condition_id` bigint         NOT NULL AUTO_INCREMENT COMMENT '预警条件ID（主键）',
  `organization_id`      bigint         NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `warning_rule_id`      bigint         NOT NULL COMMENT '预警规则ID',
  `metric_code`          varchar(64)    NOT NULL COMMENT '指标编码',
  `operator`             varchar(16)    NOT NULL COMMENT '运算符（LT/LE/EQ/GE/GT）',
  `threshold_value`      decimal(12, 4) NOT NULL COMMENT '阈值值',
  `unit`                 varchar(16)             DEFAULT NULL COMMENT '单位',
  `sort_order`           int            NOT NULL COMMENT '显示顺序',
  `status`               tinyint        NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`            bigint                  DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`          datetime                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`            bigint                  DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`          datetime                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`              bigint         NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`              int            NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`warning_condition_id`),
  KEY                    `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_rule_metric_deleted` (`warning_rule_id`, `metric_code`, `deleted`),
  KEY                    `idx_rule_sort` (`warning_rule_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='【暂时隐藏】原用于：可配置预警条件；当前中山50最小闭环不启用，不得作为当前页面、API或业务规则依据；后续启用须新建并确认 OpenSpec Change';

CREATE TABLE `sts_regulation_warning`
(
  `warning_id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '预警ID（主键）',
  `organization_id`     bigint       NOT NULL COMMENT '所属机构ID',
  `warning_rule_id`     bigint       NOT NULL COMMENT '预警规则ID',
  `regulated_org_id`    bigint       NOT NULL COMMENT '被监管机构ID',
  `training_plan_id`    bigint                DEFAULT NULL COMMENT '培训计划ID',
  `detection_cycle`     varchar(64)  NOT NULL COMMENT '预警检测周期标识',
  `dedup_key`           varchar(128) NOT NULL COMMENT '预警幂等去重键',
  `warning_level`       varchar(16) NOT NULL COMMENT '【内部保留】用于：固定内部级别 WARN；不提供配置页面或对外操作',
  `warning_title`       varchar(200) NOT NULL COMMENT '预警标题',
  `warning_status`      varchar(16) NOT NULL COMMENT '预警状态（UNREAD待查看/READ已查看/ENDED内部结束；历史 OPEN/VIEWED/PROCESSING/CLOSED/IGNORED 保留不改写）',
  `detected_time`       datetime     NOT NULL COMMENT '检测时间',
  `first_viewed_time`   datetime              DEFAULT NULL COMMENT '首次查看时间',
  `closed_time`         datetime DEFAULT NULL COMMENT '【内部保留】用于：计划修正或取消后结束当前预警；不提供人工关闭',
  `current_assignee_id` bigint   DEFAULT NULL COMMENT '【暂时隐藏】原用于：当前负责人用户ID；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `create_by`           bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`         datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`           bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`         datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`             bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`             int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`warning_id`),
  UNIQUE KEY `uk_org_dedup_deleted` (`organization_id`, `dedup_key`, `deleted`),
  KEY                   `idx_regulated_status` (`regulated_org_id`, `warning_status`, `detected_time`),
  KEY                   `idx_rule_time` (`warning_rule_id`, `detected_time`),
  KEY                   `idx_assignee` (`current_assignee_id`, `warning_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='监管预警表；当前仅用于培训计划学时不足待查看/已查看';

CREATE TABLE `sts_regulation_warning_evidence`
(
  `warning_evidence_id`    bigint       NOT NULL AUTO_INCREMENT COMMENT '预警证据ID（主键）',
  `organization_id`        bigint       NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `warning_id`             bigint       NOT NULL COMMENT '预警ID',
  `evidence_key`           varchar(128) NOT NULL COMMENT '证据幂等键',
  `metric_code`            varchar(64)  NOT NULL COMMENT '指标编码',
  `actual_value`           decimal(12, 4) DEFAULT NULL COMMENT '实际值',
  `threshold_value`        decimal(12, 4) DEFAULT NULL COMMENT '阈值值',
  `unit`                   varchar(16)    DEFAULT NULL COMMENT '单位',
  `source_type`            varchar(24)  NOT NULL COMMENT '证据来源类型',
  `source_id`              bigint       NOT NULL COMMENT '来源对象ID',
  `evidence_snapshot_json` json           DEFAULT NULL COMMENT '预警证据快照JSON',
  `create_by`              bigint         DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`            datetime       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`warning_evidence_id`),
  KEY                      `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_warning_evidence` (`warning_id`, `evidence_key`),
  KEY                      `idx_warning_metric` (`warning_id`, `metric_code`),
  KEY                      `idx_source` (`source_type`, `source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='【内部保留】用于：保存学时对比证据；不提供独立页面或对外操作';

CREATE TABLE `sts_regulation_warning_action`
(
  `warning_action_id` bigint      NOT NULL AUTO_INCREMENT COMMENT '预警操作ID（主键）',
  `organization_id`   bigint      NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `warning_id`        bigint      NOT NULL COMMENT '预警ID',
  `action_type`       varchar(16) NOT NULL COMMENT '操作类型（VIEW/ASSIGN/COMMENT/CLOSE/IGNORE/REOPEN）',
  `action_by`         bigint      NOT NULL COMMENT '操作人用户ID',
  `action_time`       datetime    NOT NULL COMMENT '操作时间',
  `before_status`     varchar(16)   DEFAULT NULL COMMENT '变更前预警状态',
  `after_status`      varchar(16)   DEFAULT NULL COMMENT '变更后预警状态',
  `action_content`    varchar(2000) DEFAULT NULL COMMENT '操作内容',
  `create_by`         bigint        DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`       datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`warning_action_id`),
  KEY                 `idx_organization_id` (`organization_id`),
  KEY                 `idx_warning_time` (`warning_id`, `action_time`),
  KEY                 `idx_actor_time` (`action_by`, `action_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='【暂时隐藏】原用于：预警分派/评论/关闭等流水；当前中山50最小闭环不启用，不得作为当前页面、API或业务规则依据；后续启用须新建并确认 OpenSpec Change';

INSERT INTO `sts_regulation_warning_rule`
(`organization_id`, `rule_code`, `rule_name`, `warning_type`, `warning_level`, `status`, `description`)
SELECT o.`organization_id`,
       'PLAN_HOURS_INSUFFICIENT',
       '培训计划学时不足',
       'PLAN_TOTAL_HOURS',
       'WARN',
       1,
       '【内部保留】用于：固定学时不足规则；不提供独立页面或对外操作'
FROM `sts_org_organization` o
WHERE o.`org_code` = 'REGULATOR'
  AND o.`deleted` = 0;
