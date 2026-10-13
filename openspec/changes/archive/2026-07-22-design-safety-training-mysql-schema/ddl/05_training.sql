-- OpenSpec MySQL schema draft; not a migration and must not be executed directly.
-- Source: ../design.md

SET NAMES utf8mb4;
SET
FOREIGN_KEY_CHECKS = 0;


-- 仿真协议本期不实施；不创建仿真资源、分配、参与及结果表，待独立 OpenSpec 确认后再设计。
-- 培训直投直播本期不实施（2026-07-21 需求方确认）；计划/模板内容不提供 LIVE 类型，
-- 直播仅通过课程内容项执行，后续启用需独立 OpenSpec Change 补执行通道。

-- Audit class A
CREATE TABLE `sts_training_type`
(
  `training_type_id` bigint        NOT NULL AUTO_INCREMENT COMMENT '培训类型ID（主键）',
  `organization_id`  bigint        NOT NULL COMMENT '所属机构ID',
  `type_code`        varchar(64)   NOT NULL COMMENT '类型编码',
  `type_name`        varchar(100)  NOT NULL COMMENT '类型名称',
  `work_type`        varchar(100)           DEFAULT NULL COMMENT '工种类型（使用培训工种字典值）',
  `tag`              varchar(64)            DEFAULT NULL COMMENT '标签',
  `image_file_id`    bigint                 DEFAULT NULL COMMENT '图片文件ID',
  `initial_hours`    decimal(8, 2)          DEFAULT NULL COMMENT '初次学时',
  `review_hours`     decimal(8, 2)          DEFAULT NULL COMMENT '复审学时',
  `online_max_ratio` decimal(7, 4)          DEFAULT NULL COMMENT '线上最大比例',
  `status`           tinyint       NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `description`      varchar(1000) NOT NULL COMMENT '描述',
  `create_by`        bigint                 DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`      datetime               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        bigint                 DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`      datetime               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`          bigint        NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`          int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`training_type_id`),
  UNIQUE KEY `uk_org_code_deleted` (`organization_id`, `type_code`, `deleted`),
  KEY                `idx_org_work_status` (`organization_id`, `work_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训类型表';

-- Audit class A
CREATE TABLE `sts_training_standard`
(
  `training_standard_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '培训标准ID（主键）',
  `organization_id`      bigint       NOT NULL COMMENT '所属机构ID',
  `standard_code`        varchar(64)  NOT NULL COMMENT '标准编码',
  `standard_name`        varchar(100) NOT NULL COMMENT '标准名称',
  `training_type_id`     bigint       NOT NULL COMMENT '培训类型ID',
  `standard_version`     varchar(32)  NOT NULL COMMENT '标准版本',
  `valid_from`           date                  DEFAULT NULL COMMENT '有效开始',
  `valid_to`             date                  DEFAULT NULL COMMENT '有效结束',
  `publish_status`       varchar(16)  NOT NULL COMMENT '发布状态（DRAFT/PUBLISHED/ARCHIVED）',
  `status`               tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`            bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`          datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`            bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`          datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`              bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`              int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`training_standard_id`),
  UNIQUE KEY `uk_org_code_version_deleted` (`organization_id`, `standard_code`, `standard_version`, `deleted`),
  KEY                    `idx_type_valid` (`training_type_id`, `valid_from`, `valid_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训标准表';

-- Audit class A
CREATE TABLE `sts_training_standard_rule`
(
  `standard_rule_id`     bigint        NOT NULL AUTO_INCREMENT COMMENT '标准规则ID（主键）',
  `training_standard_id` bigint        NOT NULL COMMENT '培训标准ID',
  `rule_type`            varchar(32)   NOT NULL COMMENT '规则类型（TOTAL_HOURS/ONLINE_RATIO/COURSE/EXAM/OFFLINE）',
  `required_value`       decimal(12, 4)         DEFAULT NULL COMMENT '要求值',
  `unit`                 varchar(16)            DEFAULT NULL COMMENT '单位',
  `resource_type`        varchar(32)            DEFAULT NULL COMMENT '资源类型（COURSE/EXAM/OFFLINE）',
  `resource_id`          bigint                 DEFAULT NULL COMMENT '资源ID',
  `sort_order`           int           NOT NULL COMMENT '显示顺序',
  `status`               tinyint       NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `description`          varchar(1000) NOT NULL COMMENT '描述',
  `create_by`            bigint                 DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`          datetime               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`            bigint                 DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`          datetime               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`              bigint        NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`              int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`standard_rule_id`),
  KEY                    `idx_standard_type` (`training_standard_id`, `rule_type`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训标准规则表';

-- Audit class A
CREATE TABLE `sts_training_template_category`
(
  `template_category_id` bigint        NOT NULL AUTO_INCREMENT COMMENT '模板分类ID（主键）',
  `organization_id`      bigint        NOT NULL COMMENT '所属机构ID',
  `parent_id`            bigint        NOT NULL DEFAULT 0 COMMENT '父级ID',
  `ancestors`            varchar(1000) NOT NULL COMMENT '祖级路径',
  `category_name`        varchar(100)  NOT NULL COMMENT '分类名称',
  `sort_order`           int           NOT NULL COMMENT '显示顺序',
  `status`               tinyint       NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`            bigint                 DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`          datetime               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`            bigint                 DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`          datetime               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`              bigint        NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`              int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`template_category_id`),
  UNIQUE KEY `uk_org_parent_name_deleted` (`organization_id`, `parent_id`, `category_name`, `deleted`),
  KEY                    `idx_parent_sort` (`parent_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训模板分类表';

-- Audit class A
CREATE TABLE `sts_training_template`
(
  `training_template_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '培训模板ID（主键）',
  `organization_id`      bigint       NOT NULL COMMENT '所属机构ID',
  `template_code`        varchar(64)  NOT NULL COMMENT '模板编码',
  `template_name`        varchar(100) NOT NULL COMMENT '模板名称',
  `template_category_id` bigint                DEFAULT NULL COMMENT '模板分类ID',
  `training_type_id`     bigint       NOT NULL COMMENT '培训类型ID',
  `training_standard_id` bigint                DEFAULT NULL COMMENT '培训标准ID',
  `description`          longtext              DEFAULT NULL COMMENT '描述',
  `publish_status`       varchar(16)  NOT NULL COMMENT '发布状态（DRAFT/PUBLISHED/ARCHIVED）',
  `status`               tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`            bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`          datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`            bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`          datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`              bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`              int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`training_template_id`),
  UNIQUE KEY `uk_org_code_deleted` (`organization_id`, `template_code`, `deleted`),
  KEY                    `idx_type_status` (`training_type_id`, `publish_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训模板表';

-- Audit class A
CREATE TABLE `sts_training_template_stage`
(
  `template_stage_id`    bigint       NOT NULL AUTO_INCREMENT COMMENT '模板阶段ID（主键）',
  `training_template_id` bigint       NOT NULL COMMENT '培训模板ID',
  `stage_name`           varchar(100) NOT NULL COMMENT '阶段名称',
  `stage_type`           varchar(16)  NOT NULL COMMENT '阶段类型（ONLINE/OFFLINE/MIXED）',
  `required_hours`       decimal(8, 2)         DEFAULT NULL COMMENT '要求学时',
  `sort_order`           int          NOT NULL COMMENT '显示顺序',
  `status`               tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`            bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`          datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`            bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`          datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`              bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`              int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`template_stage_id`),
  UNIQUE KEY `uk_template_stage_name_deleted` (`training_template_id`, `stage_name`, `deleted`),
  KEY                    `idx_template_sort` (`training_template_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训模板阶段表';

-- Audit class A
CREATE TABLE `sts_training_template_content`
(
  `template_content_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '模板内容ID（主键）',
  `template_stage_id`   bigint       NOT NULL COMMENT '模板阶段ID',
  `content_type`        varchar(24)  NOT NULL COMMENT '内容类型（COURSE/EXAM/QUESTION_BANK/OFFLINE；培训直投直播本期不实施）',
  `resource_id`         bigint                DEFAULT NULL COMMENT '资源ID',
  `content_name`        varchar(150) NOT NULL COMMENT '内容名称',
  `is_required`         tinyint      NOT NULL COMMENT '是否必修或必填（0否 1是）',
  `required_hours`      decimal(8, 2)         DEFAULT NULL COMMENT '要求学时',
  `sort_order`          int          NOT NULL COMMENT '显示顺序',
  `status`              tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`           bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`         datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`           bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`         datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`             bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`             int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`template_content_id`),
  KEY                   `idx_stage_sort` (`template_stage_id`, `sort_order`),
  KEY                   `idx_resource` (`content_type`, `resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训模板内容表';

-- Audit class A
CREATE TABLE `sts_training_plan`
(
  `training_plan_id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '培训计划ID（主键）',
  `organization_id`                 bigint       NOT NULL COMMENT '所属机构ID',
  `plan_code`                       varchar(64)  NOT NULL COMMENT '计划编码',
  `plan_name`                       varchar(150) NOT NULL COMMENT '计划名称',
  `training_type_id`                bigint       NOT NULL COMMENT '培训类型ID（允许经监管关系及资源授权跨机构引用）',
  `training_standard_id`            bigint                DEFAULT NULL COMMENT '培训标准ID（允许经监管关系及资源授权跨机构引用）',
  `source_template_id`              bigint                DEFAULT NULL COMMENT '来源培训模板ID（允许经监管关系及资源授权跨机构引用）',
  `source_owner_organization_id`    bigint                DEFAULT NULL COMMENT '跨机构来源资源所属机构ID（本机构来源允许NULL）',
  `training_type_snapshot_json`     json                  DEFAULT NULL COMMENT '计划发布时冻结的培训类型快照JSON（发布后必填）',
  `training_standard_snapshot_json` json                  DEFAULT NULL COMMENT '计划发布时冻结的培训标准及规则快照JSON（无标准时允许NULL）',
  `template_snapshot_json`          json                  DEFAULT NULL COMMENT '计划发布时冻结的来源模板快照JSON（非模板创建时允许NULL）',
  `source_snapshot_hash`            varchar(128)          DEFAULT NULL COMMENT '培训类型、标准和模板来源快照校验哈希（发布后必填）',
  `manager_user_id`                 bigint                DEFAULT NULL COMMENT '培训负责人用户ID',
  `start_time`                      datetime     NOT NULL COMMENT '开始时间',
  `end_time`                        datetime     NOT NULL COMMENT '结束时间',
  `audience_id`                     bigint                DEFAULT NULL COMMENT '参与范围ID',
  `location`                        varchar(255)          DEFAULT NULL COMMENT '地点',
  `description`                     longtext              DEFAULT NULL COMMENT '描述',
  `publish_status`                  varchar(16)  NOT NULL COMMENT '发布状态（DRAFT/PUBLISHED/RUNNING/ENDED/CANCELLED/ARCHIVED）',
  `published_time`                  datetime              DEFAULT NULL COMMENT '发布时间',
  `ended_time`                      datetime              DEFAULT NULL COMMENT '结束时间',
  `status`                          tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`                       bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`                     datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`                       bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`                     datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                         bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                         int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`training_plan_id`),
  UNIQUE KEY `uk_org_plan_code_deleted` (`organization_id`, `plan_code`, `deleted`),
  KEY                               `idx_org_status_time` (`organization_id`, `publish_status`, `start_time`, `end_time`),
  KEY                               `idx_training_type` (`training_type_id`),
  KEY                               `idx_standard` (`training_standard_id`),
  KEY                               `idx_source_template` (`source_template_id`),
  KEY                               `idx_source_owner` (`source_owner_organization_id`),
  KEY                               `idx_manager` (`manager_user_id`),
  KEY                               `idx_audience` (`audience_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训计划表';

-- Audit class B
CREATE TABLE `sts_training_plan_organization`
(
  `plan_organization_id`        bigint      NOT NULL AUTO_INCREMENT COMMENT '计划机构ID（主键）',
  `training_plan_id`            bigint      NOT NULL COMMENT '培训计划ID',
  `participant_organization_id` bigint      NOT NULL COMMENT '参与机构ID',
  `organization_role`           varchar(16) NOT NULL COMMENT '机构参与角色（ORGANIZER/PARTICIPANT）',
  `create_by`                   bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`                 datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`plan_organization_id`),
  UNIQUE KEY `uk_plan_org` (`training_plan_id`, `participant_organization_id`, `organization_role`),
  KEY                           `idx_org_plan` (`participant_organization_id`, `training_plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训计划机构关联表';

-- Audit class A
CREATE TABLE `sts_training_plan_stage`
(
  `plan_stage_id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '计划阶段ID（主键）',
  `training_plan_id`         bigint       NOT NULL COMMENT '培训计划ID',
  `source_template_stage_id` bigint                DEFAULT NULL COMMENT '来源模板阶段ID',
  `stage_name`               varchar(100) NOT NULL COMMENT '阶段名称',
  `stage_type`               varchar(16)  NOT NULL COMMENT '阶段类型（ONLINE/OFFLINE/MIXED）',
  `start_time`               datetime              DEFAULT NULL COMMENT '开始时间',
  `end_time`                 datetime              DEFAULT NULL COMMENT '结束时间',
  `location`                 varchar(255)          DEFAULT NULL COMMENT '地点',
  `required_hours`           decimal(8, 2)         DEFAULT NULL COMMENT '要求学时',
  `sort_order`               int          NOT NULL COMMENT '显示顺序',
  `stage_status`             varchar(16)  NOT NULL COMMENT '阶段状态（PENDING/RUNNING/COMPLETED/CANCELLED）',
  `create_by`                bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`              datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`                bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`              datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                  bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                  int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`plan_stage_id`),
  KEY                        `idx_plan_sort` (`training_plan_id`, `sort_order`),
  KEY                        `idx_stage_time` (`start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训计划阶段表';

-- Audit class A
CREATE TABLE `sts_training_plan_content`
(
  `plan_content_id`        bigint       NOT NULL AUTO_INCREMENT COMMENT '计划内容ID（主键）',
  `plan_stage_id`          bigint       NOT NULL COMMENT '计划阶段ID',
  `content_type`           varchar(24)  NOT NULL COMMENT '内容类型（COURSE/EXAM/QUESTION_BANK/OFFLINE；培训直投直播本期不实施）',
  `resource_id`            bigint                DEFAULT NULL COMMENT '资源ID',
  `content_name`           varchar(150) NOT NULL COMMENT '内容名称',
  `is_required`            tinyint      NOT NULL COMMENT '是否必修或必填（0否 1是）',
  `is_scored`              tinyint      NOT NULL DEFAULT 0 COMMENT '是否计入综合成绩（0否 1是，仅COURSE/EXAM）',
  `score_weight`           decimal(7, 4)         DEFAULT NULL COMMENT '同类型内容聚合权重（0到1；AVERAGE时由系统按等权处理）',
  `score_aggregation_mode` varchar(24)           DEFAULT NULL COMMENT '同类型成绩聚合方式（AVERAGE/WEIGHTED_AVERAGE）',
  `required_hours`         decimal(8, 2)         DEFAULT NULL COMMENT '要求学时',
  `sort_order`             int          NOT NULL COMMENT '显示顺序',
  `content_status`         varchar(16)  NOT NULL COMMENT '内容状态（PENDING/RUNNING/COMPLETED/FAILED/SKIPPED）',
  `create_by`              bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`            datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`              bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`            datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`plan_content_id`),
  KEY                      `idx_stage_sort` (`plan_stage_id`, `sort_order`),
  KEY                      `idx_resource` (`content_type`, `resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训计划内容表';

-- Audit class A
CREATE TABLE `sts_training_plan_evaluation_form`
(
  `plan_evaluation_form_id` bigint      NOT NULL AUTO_INCREMENT COMMENT '培训计划评价表单关联ID（主键）',
  `training_plan_id`        bigint      NOT NULL COMMENT '培训计划ID',
  `form_version_id`         bigint      NOT NULL COMMENT '评价表单版本ID',
  `usage_type`              varchar(24) NOT NULL COMMENT '评价用途（COMPOSITE/EXPERT_INSPECTION/PARTICIPANT/COURSE_QUALITY）',
  `is_required`             tinyint     NOT NULL COMMENT '是否必填（0否 1是）',
  `status`                  tinyint     NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`               bigint               DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`             datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`               bigint               DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`             datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                 bigint      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                 int         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`plan_evaluation_form_id`),
  UNIQUE KEY `uk_plan_usage_deleted` (`training_plan_id`, `usage_type`, `deleted`),
  KEY                       `idx_form_plan` (`form_version_id`, `training_plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训计划评价表单关联表';

-- Audit class A
CREATE TABLE `sts_training_participant`
(
  `training_participant_id`     bigint        NOT NULL AUTO_INCREMENT COMMENT '培训参与人员ID（主键）',
  `organization_id`             bigint        NOT NULL COMMENT '所属机构ID',
  `training_plan_id`            bigint        NOT NULL COMMENT '培训计划ID',
  `user_id`                     bigint        NOT NULL COMMENT '用户ID',
  `participant_org_id`          bigint        NOT NULL COMMENT '参训人员所属机构ID',
  `audience_id`                 bigint                 DEFAULT NULL COMMENT '参与范围ID',
  `join_source`                 varchar(16)   NOT NULL COMMENT '加入来源（ASSIGN/IMPORT/QR）',
  `joined_time`                 datetime      NOT NULL COMMENT '加入时间',
  `participant_status`          varchar(24)   NOT NULL COMMENT '参与人员状态（PENDING/LEARNING/COMPLETED/FAILED/EXPIRED/WITHDRAWN）',
  `online_hours`                decimal(8, 2) NOT NULL COMMENT '线上学时',
  `offline_hours`               decimal(8, 2) NOT NULL COMMENT '线下学时',
  `course_score`                decimal(10, 2)         DEFAULT NULL COMMENT '课程分数',
  `exam_score`                  decimal(10, 2)         DEFAULT NULL COMMENT '考试分数',
  `offline_score`               decimal(10, 2)         DEFAULT NULL COMMENT '线下培训成绩',
  `composite_score`             decimal(10, 2)         DEFAULT NULL COMMENT '综合分数',
  `official_calculation_run_id` bigint                 DEFAULT NULL COMMENT '当前正式综合成绩计算批次ID',
  `result_status`               varchar(16)            DEFAULT NULL COMMENT '培训结果状态（PASSED/FAILED/INCOMPLETE）',
  `completed_time`              datetime               DEFAULT NULL COMMENT '完成时间',
  `create_by`                   bigint                 DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`                 datetime               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`                   bigint                 DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`                 datetime               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                     bigint        NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                     int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`training_participant_id`),
  UNIQUE KEY `uk_plan_user_deleted` (`training_plan_id`, `user_id`, `deleted`),
  KEY                           `idx_user_status` (`user_id`, `participant_status`),
  KEY                           `idx_plan_org_status` (`training_plan_id`, `participant_org_id`, `participant_status`),
  KEY                           `idx_official_calculation_run` (`official_calculation_run_id`),
  KEY                           `idx_org_status_time` (`organization_id`, `participant_status`, `joined_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训参与人员表';

-- Audit class A
CREATE TABLE `sts_training_attachment`
(
  `training_attachment_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '培训附件ID（主键）',
  `training_plan_id`       bigint       NOT NULL COMMENT '培训计划ID',
  `plan_stage_id`          bigint                DEFAULT NULL COMMENT '培训计划阶段ID',
  `attachment_type`        varchar(24)  NOT NULL COMMENT '附件类型（GENERAL/IMAGE/VIDEO/REWARD_PENALTY/EVALUATION）',
  `file_info_id`           bigint       NOT NULL COMMENT '文件信息ID',
  `attachment_name`        varchar(255) NOT NULL COMMENT '附件名称',
  `description`            varchar(1000)         DEFAULT NULL COMMENT '描述',
  `sort_order`             int          NOT NULL COMMENT '显示顺序',
  `status`                 tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`              bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`            datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`              bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`            datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`training_attachment_id`),
  UNIQUE KEY `uk_plan_file_deleted` (`training_plan_id`, `file_info_id`, `deleted`),
  KEY                      `idx_plan_type` (`training_plan_id`, `attachment_type`),
  KEY                      `idx_stage` (`plan_stage_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训附件表';

-- Audit class A
CREATE TABLE `sts_training_offline_session`
(
  `offline_session_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '线下培训场次ID（主键）',
  `training_plan_id`   bigint       NOT NULL COMMENT '培训计划ID',
  `plan_stage_id`      bigint       NOT NULL COMMENT '计划阶段ID',
  `plan_content_id`    bigint                DEFAULT NULL COMMENT '培训计划内容ID',
  `session_name`       varchar(150) NOT NULL COMMENT '线下培训场次名称',
  `teacher_user_id`    bigint                DEFAULT NULL COMMENT '讲师用户ID',
  `location`           varchar(255) NOT NULL COMMENT '地点',
  `start_time`         datetime     NOT NULL COMMENT '开始时间',
  `end_time`           datetime     NOT NULL COMMENT '结束时间',
  `sign_in_start_time` datetime              DEFAULT NULL COMMENT '签到开放开始时间',
  `sign_in_end_time`   datetime              DEFAULT NULL COMMENT '签到开放结束时间',
  `attendance_rule`    varchar(32)  NOT NULL COMMENT '签到规则',
  `session_status`     varchar(16)  NOT NULL COMMENT '场次状态（DRAFT/SCHEDULED/RUNNING/ENDED/CANCELLED）',
  `status`             tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`          bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`        datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`        datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`            int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`offline_session_id`),
  KEY                  `idx_stage_time` (`plan_stage_id`, `start_time`),
  KEY                  `idx_teacher_time` (`teacher_user_id`, `start_time`),
  KEY                  `idx_plan_content` (`plan_content_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='线下培训场次表';

-- Audit class A
CREATE TABLE `sts_training_attendance`
(
  `attendance_id`           bigint      NOT NULL AUTO_INCREMENT COMMENT '签到ID（主键）',
  `organization_id`         bigint      NOT NULL COMMENT '所属机构ID',
  `offline_session_id`      bigint      NOT NULL COMMENT '线下培训场次ID',
  `training_participant_id` bigint      NOT NULL COMMENT '培训参与人员ID',
  `user_id`                 bigint      NOT NULL COMMENT '用户ID',
  `attendance_status`       varchar(16) NOT NULL COMMENT '签到状态（PRESENT/LATE/LEAVE/ABSENT）',
  `sign_in_time`            datetime             DEFAULT NULL COMMENT '签到时间',
  `sign_out_time`           datetime             DEFAULT NULL COMMENT '签退时间',
  `sign_method`             varchar(16) NOT NULL COMMENT '签到方式（允许值待产品确认）',
  `evidence_json`           json                 DEFAULT NULL COMMENT '业务证据JSON',
  `confirmed_by`            bigint               DEFAULT NULL COMMENT '确认人用户ID',
  `confirmed_time`          datetime             DEFAULT NULL COMMENT '确认时间',
  `create_by`               bigint               DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`             datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`               bigint               DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`             datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                 bigint      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                 int         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`attendance_id`),
  UNIQUE KEY `uk_session_participant_deleted` (`offline_session_id`, `training_participant_id`, `deleted`),
  KEY                       `idx_user_time` (`user_id`, `sign_in_time`),
  KEY                       `idx_session_status` (`offline_session_id`, `attendance_status`),
  KEY                       `idx_org_status_time` (`organization_id`, `attendance_status`, `sign_in_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训签到表';

-- 线下成绩批量导入统一使用 sts_common_import_batch / sts_common_import_error（business_type=OFFLINE_SCORE，business_id=线下场次ID，见 01_org_common.sql）。

-- Audit class A
CREATE TABLE `sts_training_offline_score`
(
  `offline_score_id`        bigint         NOT NULL AUTO_INCREMENT COMMENT '线下培训成绩ID（主键）',
  `organization_id`         bigint         NOT NULL COMMENT '所属机构ID',
  `training_participant_id` bigint         NOT NULL COMMENT '培训参与人员ID',
  `offline_session_id`      bigint         NOT NULL COMMENT '线下培训场次ID',
  `score_name`              varchar(100)   NOT NULL COMMENT '成绩项名称',
  `score`                   decimal(10, 2) NOT NULL COMMENT '分数',
  `max_score`               decimal(10, 2) NOT NULL COMMENT '满分',
  `score_source`            varchar(16)    NOT NULL COMMENT '成绩来源（MANUAL/IMPORT）',
  `import_batch_id`         bigint                  DEFAULT NULL COMMENT '导入批次ID（对应sts_common_import_batch）',
  `scored_by`               bigint                  DEFAULT NULL COMMENT '评分人用户ID',
  `scored_time`             datetime       NOT NULL COMMENT '评分时间',
  `create_by`               bigint                  DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`             datetime                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`               bigint                  DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`             datetime                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                 bigint         NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                 int            NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`offline_score_id`),
  UNIQUE KEY `uk_participant_session_name_deleted` (`training_participant_id`, `offline_session_id`, `score_name`, `deleted`),
  KEY                       `idx_session_score` (`offline_session_id`, `score`),
  KEY                       `idx_import_batch` (`import_batch_id`),
  KEY                       `idx_org_scored_time` (`organization_id`, `scored_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='线下培训成绩表';

-- Audit class A
CREATE TABLE `sts_training_reward_penalty`
(
  `reward_penalty_id`       bigint       NOT NULL AUTO_INCREMENT COMMENT '培训奖惩记录ID（主键）',
  `organization_id`         bigint       NOT NULL COMMENT '所属机构ID',
  `training_plan_id`        bigint       NOT NULL COMMENT '培训计划ID',
  `training_participant_id` bigint       NOT NULL COMMENT '培训参与人员ID',
  `record_type`             varchar(16)  NOT NULL COMMENT '记录类型（REWARD/PENALTY）',
  `title`                   varchar(200) NOT NULL COMMENT '标题',
  `description`             longtext     NOT NULL COMMENT '描述',
  `score_change`            decimal(10, 2)        DEFAULT NULL COMMENT '分数变动',
  `occurred_date`           date         NOT NULL COMMENT '发生日期',
  `evidence_file_id`        bigint                DEFAULT NULL COMMENT '证据文件ID',
  `status`                  tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`               bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`             datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`               bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`             datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                 bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                 int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`reward_penalty_id`),
  KEY                       `idx_participant_date` (`training_participant_id`, `occurred_date`),
  KEY                       `idx_plan_type` (`training_plan_id`, `record_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训奖惩记录表';

-- Audit class A
CREATE TABLE `sts_training_participant_evaluation`
(
  `participant_evaluation_id` bigint      NOT NULL AUTO_INCREMENT COMMENT '参训人员评价记录ID（主键）',
  `organization_id`           bigint      NOT NULL COMMENT '所属机构ID',
  `training_plan_id`          bigint      NOT NULL COMMENT '培训计划ID',
  `training_participant_id`   bigint      NOT NULL COMMENT '培训参与人员ID',
  `evaluator_user_id`         bigint      NOT NULL COMMENT '评价人用户ID',
  `evaluation_record_id`      bigint               DEFAULT NULL COMMENT '结构化评价记录ID',
  `evaluation_type`           varchar(24) NOT NULL COMMENT '评价类型（TEACHER/ORGANIZATION/SELF/OTHER）',
  `score`                     decimal(10, 2)       DEFAULT NULL COMMENT '分数',
  `content`                   longtext             DEFAULT NULL COMMENT '内容',
  `evaluated_time`            datetime    NOT NULL COMMENT '评价时间',
  `status`                    tinyint     NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`                 bigint               DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`               datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`                 bigint               DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`               datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                   bigint      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                   int         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`participant_evaluation_id`),
  KEY                         `idx_participant_type` (`training_participant_id`, `evaluation_type`),
  KEY                         `idx_plan_time` (`training_plan_id`, `evaluated_time`),
  KEY                         `idx_evaluation_record` (`evaluation_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='参训人员评价记录表';

SET
FOREIGN_KEY_CHECKS = 1;
