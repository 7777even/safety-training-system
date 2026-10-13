-- Integration-test schema: evaluation form
SET NAMES utf8mb4;

CREATE TABLE `sts_evaluation_form`
(
  `evaluation_form_id` bigint        NOT NULL AUTO_INCREMENT COMMENT '评价表单ID（主键）',
  `organization_id`    bigint        NOT NULL COMMENT '所属机构ID',
  `form_code`          varchar(64)   NOT NULL COMMENT '表单编码',
  `form_name`          varchar(100)  NOT NULL COMMENT '表单名称',
  `form_type`          varchar(24)   NOT NULL COMMENT '表单类型（COMPOSITE/EXPERT_INSPECTION）',
  `current_version_no` int           NOT NULL COMMENT '当前版本号',
  `status`             tinyint       NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `description`        varchar(1000) NOT NULL COMMENT '描述',
  `create_by`          bigint                 DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`        datetime               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          bigint                 DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`        datetime               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            bigint        NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`            int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`evaluation_form_id`),
  UNIQUE KEY `uk_org_code_deleted` (`organization_id`, `form_code`, `deleted`),
  KEY                  `idx_org_type` (`organization_id`, `form_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='评价表单表';

CREATE TABLE `sts_evaluation_form_version`
(
  `form_version_id`    bigint         NOT NULL AUTO_INCREMENT COMMENT '表单版本ID（主键）',
  `organization_id`    bigint         NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `evaluation_form_id` bigint         NOT NULL COMMENT '评价表单ID',
  `version_no`         int            NOT NULL COMMENT '版本号',
  `total_score`        decimal(10, 2) NOT NULL COMMENT '总分',
  `published_status`   varchar(16)    NOT NULL COMMENT '发布状态（DRAFT/PUBLISHED/ARCHIVED）',
  `published_by`       bigint       DEFAULT NULL COMMENT '发布人用户ID（发布后必填）',
  `published_time`     datetime     DEFAULT NULL COMMENT '发布时间（发布后必填）',
  `version_note`       varchar(500) DEFAULT NULL COMMENT '版本说明',
  `create_by`          bigint       DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`        datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`form_version_id`),
  KEY                  `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_form_version` (`evaluation_form_id`, `version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='【内部保留】用于：评分表和查课表启用时冻结表单内容；不提供独立页面或对外操作';

CREATE TABLE `sts_evaluation_item`
(
  `evaluation_item_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '评价项目ID（主键）',
  `organization_id`    bigint       NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `form_version_id`    bigint       NOT NULL COMMENT '表单版本ID',
  `item_code`          varchar(64)  NOT NULL COMMENT '评价项目编码',
  `item_name`          varchar(200) NOT NULL COMMENT '评价项目名称',
  `item_type`          varchar(16)  NOT NULL COMMENT '评价项目类型（SCORE/TEXT/CHOICE）',
  `max_score`          decimal(10, 2) DEFAULT NULL COMMENT '满分',
  `weight`             decimal(7, 4)  DEFAULT NULL COMMENT '权重',
  `sort_order`         int          NOT NULL COMMENT '显示顺序',
  `is_required`        tinyint      NOT NULL COMMENT '是否必修或必填（0否 1是）',
  `create_by`          bigint         DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`        datetime       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`evaluation_item_id`),
  KEY                  `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_version_code` (`form_version_id`, `item_code`),
  KEY                  `idx_version_sort` (`form_version_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='评价表单项目记录表';

CREATE TABLE `sts_evaluation_score_rule`
(
  `score_rule_id`      bigint        NOT NULL AUTO_INCREMENT COMMENT '分数规则ID（主键）',
  `organization_id`    bigint        NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `form_version_id`    bigint        NOT NULL COMMENT '表单版本ID',
  `evaluation_item_id` bigint         DEFAULT NULL COMMENT '评价项目ID',
  `calculation_type`   varchar(16)   NOT NULL COMMENT '基础计算类型（WEIGHT/ADJUSTMENT）',
  `source_type`        varchar(24)   NOT NULL COMMENT '成绩来源类型（COURSE/EXAM/OFFLINE/REWARD_PENALTY/MANUAL）',
  `score_change`       decimal(10, 2) DEFAULT NULL COMMENT '基础加减分值（正数加分，负数减分）',
  `weight`             decimal(7, 4)  DEFAULT NULL COMMENT '成绩权重',
  `sort_order`         int           NOT NULL COMMENT '显示顺序',
  `rule_description`   varchar(1000) NOT NULL COMMENT '基础评分规则说明',
  `create_by`          bigint         DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`        datetime       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`score_rule_id`),
  KEY                  `idx_organization_id` (`organization_id`),
  KEY                  `idx_version_source` (`form_version_id`, `calculation_type`, `source_type`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='综合评分基础规则记录表';


-- M4 evaluation record & calculation
CREATE TABLE `sts_evaluation_calculation_run`
(
  `calculation_run_id`      bigint         NOT NULL AUTO_INCREMENT COMMENT '综合成绩计算批次ID（主键）',
  `organization_id`         bigint         NOT NULL COMMENT '所属机构ID',
  `training_plan_id`        bigint         NOT NULL COMMENT '培训计划ID',
  `training_participant_id` bigint         NOT NULL COMMENT '培训参与人员ID',
  `form_version_id`         bigint         NOT NULL COMMENT '综合评价表单版本ID',
  `run_no`                  int            NOT NULL COMMENT '计算批次序号（从1递增）',
  `supersedes_run_id`       bigint                  DEFAULT NULL COMMENT '被本批次替代的计算批次ID',
  `calculation_status`      varchar(16)    NOT NULL COMMENT '计算状态（CALCULATED/CONFIRMED/SUPERSEDED）',
  `official_marker`         tinyint                 DEFAULT NULL COMMENT '当前正式结果唯一标记（正式结果固定为1，非正式或已替代为NULL）',
  `course_score`            decimal(10, 2)          DEFAULT NULL COMMENT '课程来源聚合分',
  `exam_score`              decimal(10, 2)          DEFAULT NULL COMMENT '考试来源聚合分',
  `offline_score`           decimal(10, 2)          DEFAULT NULL COMMENT '线下来源聚合分',
  `adjustment_score`        decimal(10, 2) NOT NULL COMMENT '奖惩及人工调整合计分',
  `composite_score`         decimal(10, 2) NOT NULL COMMENT '综合最终分数',
  `rule_snapshot_json`      json           NOT NULL COMMENT '本次计算使用的规则版本和规则完整快照JSON',
  `calculation_hash`        varchar(128)   NOT NULL COMMENT '计算输入、规则和结果校验哈希',
  `calculated_by`           bigint         NOT NULL COMMENT '计算执行人用户ID',
  `calculated_time`         datetime       NOT NULL COMMENT '计算时间',
  `confirmed_by`            bigint                  DEFAULT NULL COMMENT '正式结果确认人用户ID',
  `confirmed_time`          datetime                DEFAULT NULL COMMENT '正式结果确认时间',
  `create_by`               bigint                  DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`             datetime                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`               bigint                  DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`             datetime                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                 bigint         NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                 int            NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`calculation_run_id`),
  UNIQUE KEY `uk_participant_form_run_deleted` (`training_participant_id`, `form_version_id`, `run_no`, `deleted`),
  UNIQUE KEY `uk_participant_official_deleted` (`training_participant_id`, `official_marker`, `deleted`),
  KEY                       `idx_plan_status_time` (`training_plan_id`, `calculation_status`, `calculated_time`),
  KEY                       `idx_org_status_time` (`organization_id`, `calculation_status`, `calculated_time`),
  KEY                       `idx_supersedes_run` (`supersedes_run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='【暂时隐藏】原用于：综合成绩计算批次；当前中山50最小闭环不启用，不得作为当前页面、API或业务规则依据；后续启用须新建并确认 OpenSpec Change';

CREATE TABLE `sts_evaluation_calculation_source`
(
  `calculation_source_id` bigint         NOT NULL AUTO_INCREMENT COMMENT '综合成绩计算来源ID（主键）',
  `organization_id`       bigint         NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `calculation_run_id`    bigint         NOT NULL COMMENT '综合成绩计算批次ID',
  `source_type`           varchar(24)    NOT NULL COMMENT '计算来源类型（COURSE/EXAM/OFFLINE/REWARD_PENALTY/MANUAL）',
  `plan_content_id`       bigint         NOT NULL DEFAULT 0 COMMENT '培训计划内容ID（奖惩或人工调整无对应内容时为0）',
  `source_record_type`    varchar(32)    NOT NULL COMMENT '来源记录类型（COURSE_ENROLLMENT/EXAM_CANDIDATE/OFFLINE_SCORE/REWARD_PENALTY/MANUAL）',
  `source_record_id`      bigint         NOT NULL COMMENT '来源业务记录ID',
  `source_score`          decimal(10, 2) NOT NULL COMMENT '来源原始分数或加减分值',
  `normalized_score`      decimal(10, 2)          DEFAULT NULL COMMENT '换算后的百分制分数',
  `weight`                decimal(7, 4)           DEFAULT NULL COMMENT '本来源使用的权重',
  `aggregation_mode`      varchar(24)             DEFAULT NULL COMMENT '来源聚合方式（AVERAGE/WEIGHTED_AVERAGE/ADJUSTMENT）',
  `intermediate_score`    decimal(10, 2) NOT NULL COMMENT '本来源参与计算后的中间结果',
  `source_snapshot_json`  json           NOT NULL COMMENT '来源记录、上下文和计算参数快照JSON',
  `create_by`             bigint                  DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`           datetime                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`calculation_source_id`),
  KEY                     `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_run_source` (`calculation_run_id`, `source_record_type`, `source_record_id`, `plan_content_id`),
  KEY                     `idx_source_record` (`source_record_type`, `source_record_id`),
  KEY                     `idx_run_type` (`calculation_run_id`, `source_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='【暂时隐藏】原用于：综合成绩计算来源追溯；当前中山50最小闭环不启用，不得作为当前页面、API或业务规则依据；后续启用须新建并确认 OpenSpec Change';

CREATE TABLE `sts_evaluation_record`
(
  `evaluation_record_id` bigint      NOT NULL AUTO_INCREMENT COMMENT '评价记录ID（主键）',
  `organization_id`      bigint      NOT NULL COMMENT '所属机构ID',
  `form_version_id`      bigint      NOT NULL COMMENT '表单版本ID',
  `target_type` varchar(24) NOT NULL COMMENT '目标对象类型（当前专家查课使用 TRAINING_PLAN_CONTENT；PARTICIPANT/COURSE/TRAINING_PLAN/ORGANIZATION 为【暂时隐藏】原通用评价目标）',
  `target_id`            bigint      NOT NULL COMMENT '目标对象ID',
  `evaluator_user_id`    bigint               DEFAULT NULL COMMENT '评价人用户ID',
  `total_score`          decimal(10, 2)       DEFAULT NULL COMMENT '总分',
  `record_status`        varchar(16) NOT NULL COMMENT '记录状态（DRAFT/SUBMITTED/CALCULATED）',
  `submitted_time`       datetime             DEFAULT NULL COMMENT '提交时间',
  `summary`              longtext             DEFAULT NULL COMMENT '摘要',
  `create_by`            bigint               DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`          datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`            bigint               DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`          datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`              bigint      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`              int         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`evaluation_record_id`),
  KEY                    `idx_target` (`target_type`, `target_id`, `record_status`),
  UNIQUE KEY `uk_form_target_deleted` (`form_version_id`, `target_type`, `target_id`, `deleted`),
  KEY                    `idx_evaluator_time` (`evaluator_user_id`, `submitted_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='评价记录表';

CREATE TABLE `sts_evaluation_record_item`
(
  `record_item_id`          bigint NOT NULL AUTO_INCREMENT COMMENT '记录项目ID（主键）',
  `organization_id`         bigint NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `evaluation_record_id`    bigint NOT NULL COMMENT '评价记录ID',
  `evaluation_item_id`      bigint NOT NULL COMMENT '评价项目ID',
  `score`                   decimal(10, 2)  DEFAULT NULL COMMENT '分数',
  `text_value`              longtext        DEFAULT NULL COMMENT '文本值',
  `option_value`            varchar(200)    DEFAULT NULL COMMENT '选项值',
  `calculation_source_json` json DEFAULT NULL COMMENT '【暂时隐藏】原用于：评价项计算来源快照；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `create_by`               bigint          DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`             datetime        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`               bigint          DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`             datetime        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                 bigint NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                 int    NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`record_item_id`),
  KEY                       `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_record_item_deleted` (`evaluation_record_id`, `evaluation_item_id`, `deleted`),
  KEY                       `idx_item_score` (`evaluation_item_id`, `score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='评价记录明细表';
