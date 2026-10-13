-- OpenSpec MySQL schema draft; not a migration and must not be executed directly.
-- Source: ../design.md

SET NAMES utf8mb4;
SET
FOREIGN_KEY_CHECKS = 0;

-- Audit class A
CREATE TABLE `sts_certificate_template`
(
  `certificate_template_id` bigint        NOT NULL AUTO_INCREMENT COMMENT '证书模板ID（主键）',
  `organization_id`         bigint        NOT NULL COMMENT '所属机构ID',
  `template_code`           varchar(64)   NOT NULL COMMENT '模板编码',
  `template_name`           varchar(100)  NOT NULL COMMENT '模板名称',
  `current_version_no`      int           NOT NULL COMMENT '当前版本号',
  `status`                  tinyint       NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `description`             varchar(1000) NOT NULL COMMENT '描述',
  `create_by`               bigint                 DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`             datetime               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`               bigint                 DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`             datetime               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                 bigint        NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                 int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`certificate_template_id`),
  UNIQUE KEY `uk_org_code_deleted` (`organization_id`, `template_code`, `deleted`),
  KEY                       `idx_org_status` (`organization_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='证书模板表';

-- Audit class C
CREATE TABLE `sts_certificate_template_version`
(
  `template_version_id`     bigint      NOT NULL AUTO_INCREMENT COMMENT '证书模板版本ID（主键）',
  `certificate_template_id` bigint      NOT NULL COMMENT '证书模板ID',
  `version_no`              int         NOT NULL COMMENT '版本号',
  `background_file_id`      bigint      NOT NULL COMMENT '背景图片文件ID',
  `width_px`                int         NOT NULL COMMENT '模板宽度像素',
  `height_px`               int         NOT NULL COMMENT '模板高度像素',
  `published_status`        varchar(16) NOT NULL COMMENT '发布状态（DRAFT/PUBLISHED/ARCHIVED）',
  `published_by`            bigint       DEFAULT NULL COMMENT '发布人用户ID（发布后必填）',
  `published_time`          datetime     DEFAULT NULL COMMENT '发布时间（发布后必填）',
  `version_note`            varchar(500) DEFAULT NULL COMMENT '版本说明',
  `create_by`               bigint       DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`             datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`template_version_id`),
  UNIQUE KEY `uk_template_version` (`certificate_template_id`, `version_no`),
  KEY                       `idx_background_file` (`background_file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='证书模板版本记录表';

-- Audit class A
CREATE TABLE `sts_certificate_template_element`
(
  `template_element_id` bigint      NOT NULL AUTO_INCREMENT COMMENT '模板元素ID（主键）',
  `template_version_id` bigint      NOT NULL COMMENT '证书模板版本ID',
  `element_type`        varchar(16) NOT NULL COMMENT '元素类型（TEXT/IMAGE/VARIABLE）',
  `variable_code`       varchar(64)          DEFAULT NULL COMMENT '变量编码',
  `static_content`      varchar(1000)        DEFAULT NULL COMMENT '静态内容',
  `x_position`          int         NOT NULL COMMENT '横向坐标',
  `y_position`          int         NOT NULL COMMENT '纵向坐标',
  `width`               int                  DEFAULT NULL COMMENT '宽度',
  `height`              int                  DEFAULT NULL COMMENT '高度',
  `font_family`         varchar(64)          DEFAULT NULL COMMENT '字体名称',
  `font_size`           int                  DEFAULT NULL COMMENT '字体大小',
  `font_color`          varchar(16)          DEFAULT NULL COMMENT '字体颜色',
  `sort_order`          int         NOT NULL COMMENT '显示顺序',
  `status`              tinyint     NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`           bigint               DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`         datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`           bigint               DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`         datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`             bigint      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`             int         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`template_element_id`),
  KEY                   `idx_template_sort` (`template_version_id`, `sort_order`),
  KEY                   `idx_variable` (`variable_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='证书模板元素表';

-- Audit class A
CREATE TABLE `sts_certificate_issue_rule`
(
  `certificate_rule_id`     bigint       NOT NULL AUTO_INCREMENT COMMENT '证书规则ID（主键）',
  `organization_id`         bigint       NOT NULL COMMENT '所属机构ID',
  `rule_name`               varchar(100) NOT NULL COMMENT '规则名称',
  `certificate_template_id` bigint       NOT NULL COMMENT '证书模板ID',
  `source_type`             varchar(16)  NOT NULL COMMENT '来源类型（COURSE/EXAM/TRAINING）',
  `source_id`               bigint       NOT NULL COMMENT '来源对象ID（COURSE为课程ID，EXAM为考试ID，TRAINING为培训计划ID）',
  `condition_type`          varchar(24)  NOT NULL COMMENT '条件类型（COMPLETED/PASSED/SCORE_AT_LEAST）',
  `condition_value`         decimal(10, 2)        DEFAULT NULL COMMENT '条件值',
  `collection_instruction`  longtext              DEFAULT NULL COMMENT '证书领取说明',
  `is_auto_issue`           tinyint      NOT NULL COMMENT '是否自动签发证书（0否 1是）',
  `valid_from`              datetime              DEFAULT NULL COMMENT '有效开始',
  `valid_to`                datetime              DEFAULT NULL COMMENT '有效结束',
  `status`                  tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`               bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`             datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`               bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`             datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                 bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                 int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`certificate_rule_id`),
  UNIQUE KEY `uk_source_rule_deleted` (`source_type`, `source_id`, `certificate_template_id`, `deleted`),
  KEY                       `idx_source_status` (`source_type`, `source_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='证书签发规则表';

-- Audit class A
CREATE TABLE `sts_certificate_certificate`
(
  `certificate_id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '证书ID（主键）',
  `organization_id`         bigint       NOT NULL COMMENT '所属机构ID',
  `certificate_no`          varchar(128) NOT NULL COMMENT '证书序号',
  `certificate_rule_id`     bigint                DEFAULT NULL COMMENT '证书规则ID',
  `certificate_template_id` bigint       NOT NULL COMMENT '证书模板ID',
  `template_version_id`     bigint       NOT NULL COMMENT '证书模板版本ID',
  `holder_user_id`          bigint       NOT NULL COMMENT '证书持有人用户ID',
  `source_type`             varchar(16)  NOT NULL COMMENT '来源类型（COURSE/EXAM/TRAINING）',
  `source_id`               bigint       NOT NULL COMMENT '来源对象ID（COURSE为开课ID，EXAM为考试ID，TRAINING为培训计划ID；复训经新开课/新计划自然签发新证书）',
  `source_result_id`        bigint                DEFAULT NULL COMMENT '来源结果记录ID（COURSE为课程报名ID，EXAM为考试人员ID，TRAINING为参训人员ID）',
  `issued_time`             datetime     NOT NULL COMMENT '签发时间',
  `valid_from`              date                  DEFAULT NULL COMMENT '有效开始',
  `valid_to`                date                  DEFAULT NULL COMMENT '有效结束',
  `certificate_file_id`     bigint       NOT NULL COMMENT '证书文件ID',
  `content_snapshot_json`   json         NOT NULL COMMENT '证书内容快照JSON',
  `snapshot_hash`           varchar(128) NOT NULL COMMENT '快照校验哈希',
  `certificate_status`      varchar(16)  NOT NULL COMMENT '证书状态（ISSUED/REVOKED/EXPIRED）',
  `revoked_time`            datetime              DEFAULT NULL COMMENT '撤销时间',
  `create_by`               bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`             datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`               bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`             datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                 bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                 int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`certificate_id`),
  UNIQUE KEY `uk_certificate_no_deleted` (`certificate_no`, `deleted`),
  UNIQUE KEY `uk_holder_source_deleted` (`holder_user_id`, `source_type`, `source_id`, `certificate_template_id`, `deleted`),
  KEY                       `idx_holder_status` (`holder_user_id`, `certificate_status`),
  KEY                       `idx_template_version` (`template_version_id`),
  KEY                       `idx_certificate_rule` (`certificate_rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='证书表';

-- Audit class C
CREATE TABLE `sts_certificate_revocation`
(
  `certificate_revocation_id` bigint        NOT NULL AUTO_INCREMENT COMMENT '证书撤销ID（主键）',
  `organization_id`           bigint        NOT NULL COMMENT '所属机构ID',
  `certificate_id`            bigint        NOT NULL COMMENT '证书ID',
  `revoked_by`                bigint        NOT NULL COMMENT '撤销人用户ID',
  `revoked_time`              datetime      NOT NULL COMMENT '撤销时间',
  `reason`                    varchar(1000) NOT NULL COMMENT '原因',
  `before_status`             varchar(16)   NOT NULL COMMENT '变更前证书状态（ISSUED/REVOKED/EXPIRED）',
  `after_status`              varchar(16)   NOT NULL COMMENT '变更后证书状态（ISSUED/REVOKED/EXPIRED）',
  `create_by`                 bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`               datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`certificate_revocation_id`),
  KEY                         `idx_certificate_time` (`certificate_id`, `revoked_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='证书撤销记录表';

-- Audit class A
CREATE TABLE `sts_survey_survey`
(
  `survey_id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '问卷ID（主键）',
  `organization_id`    bigint       NOT NULL COMMENT '所属机构ID',
  `survey_code`        varchar(64)  NOT NULL COMMENT '问卷编码',
  `survey_name`        varchar(100) NOT NULL COMMENT '问卷名称',
  `current_version_id` bigint                DEFAULT NULL COMMENT '当前发布版本ID',
  `survey_type`        varchar(32)  NOT NULL COMMENT '问卷类型（GENERAL/TRAINING/ORGANIZATION_EVALUATION）',
  `cover_file_id`      bigint                DEFAULT NULL COMMENT '封面文件ID',
  `description`        longtext              DEFAULT NULL COMMENT '描述',
  `closing_text`       longtext              DEFAULT NULL COMMENT '结束语文本',
  `is_anonymous`       tinyint      NOT NULL COMMENT '是否匿名展示（0否 1是）',
  `content_status`     varchar(16)  NOT NULL COMMENT '问卷内容状态（DRAFT/PUBLISHED/OFFLINE）',
  `status`             tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`          bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`        datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`        datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`            int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`survey_id`),
  UNIQUE KEY `uk_org_code_deleted` (`organization_id`, `survey_code`, `deleted`),
  KEY                  `idx_current_version` (`current_version_id`),
  KEY                  `idx_org_content_status` (`organization_id`, `content_status`, `status`),
  KEY                  `idx_name` (`survey_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='问卷表';

-- Audit class C
CREATE TABLE `sts_survey_version`
(
  `survey_version_id`     bigint       NOT NULL AUTO_INCREMENT COMMENT '问卷发布版本ID（主键）',
  `survey_id`             bigint       NOT NULL COMMENT '问卷ID',
  `version_no`            int          NOT NULL COMMENT '版本号（从1递增）',
  `content_snapshot_json` json         NOT NULL COMMENT '问卷基本信息、题目、选项和子项完整快照JSON',
  `snapshot_hash`         varchar(128) NOT NULL COMMENT '版本快照校验哈希',
  `version_note`          varchar(500) DEFAULT NULL COMMENT '版本说明',
  `published_by`          bigint       NOT NULL COMMENT '发布人用户ID',
  `published_time`        datetime     NOT NULL COMMENT '发布时间',
  `create_by`             bigint       DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`           datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`survey_version_id`),
  UNIQUE KEY `uk_survey_version` (`survey_id`, `version_no`),
  KEY                     `idx_survey_publish_time` (`survey_id`, `published_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='问卷不可变发布版本表';

-- Audit class A
CREATE TABLE `sts_survey_question`
(
  `survey_question_id`     bigint       NOT NULL AUTO_INCREMENT COMMENT '问卷题目ID（主键）',
  `survey_id`              bigint       NOT NULL COMMENT '问卷ID',
  `question_type`          varchar(24)  NOT NULL COMMENT '题目类型（SINGLE/MULTIPLE/SINGLE_FILL/MULTI_FILL/SORT/RATING/SCORE/UPLOAD/TEXT）',
  `question_text`          varchar(300) NOT NULL COMMENT '题目文本',
  `is_required`            tinyint      NOT NULL COMMENT '是否必修或必填（0否 1是）',
  `is_other_allowed`       tinyint      NOT NULL COMMENT '是否允许其他选项（0否 1是）',
  `is_description_allowed` tinyint      NOT NULL COMMENT '是否允许补充描述（0否 1是）',
  `data_type`              varchar(16)           DEFAULT NULL COMMENT '数据类型（DATE/TIME/PHONE/EMAIL/TEXT/GENDER/AGE_RANGE）',
  `max_star`               int                   DEFAULT NULL COMMENT '最高星级',
  `max_upload_count`       int                   DEFAULT NULL COMMENT '最大上传数量',
  `sort_order`             int          NOT NULL COMMENT '显示顺序',
  `status`                 tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`              bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`            datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`              bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`            datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`survey_question_id`),
  KEY                      `idx_survey_sort` (`survey_id`, `sort_order`),
  KEY                      `idx_survey_type` (`survey_id`, `question_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='问卷题目表';

-- Audit class A
CREATE TABLE `sts_survey_option`
(
  `survey_option_id`   bigint        NOT NULL AUTO_INCREMENT COMMENT '问卷选项ID（主键）',
  `survey_question_id` bigint        NOT NULL COMMENT '问卷题目ID',
  `option_key`         varchar(16)   NOT NULL COMMENT '选项键',
  `option_content`     varchar(1000) NOT NULL COMMENT '选项内容',
  `sort_order`         int           NOT NULL COMMENT '显示顺序',
  `status`             tinyint       NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`          bigint                 DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`        datetime               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          bigint                 DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`        datetime               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            bigint        NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`            int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`survey_option_id`),
  UNIQUE KEY `uk_question_key_deleted` (`survey_question_id`, `option_key`, `deleted`),
  KEY                  `idx_question_sort` (`survey_question_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='问卷选项表';

-- Audit class A
CREATE TABLE `sts_survey_sub_item`
(
  `survey_sub_item_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '问卷题目子项ID（主键）',
  `survey_question_id` bigint       NOT NULL COMMENT '问卷题目ID',
  `item_name`          varchar(300) NOT NULL COMMENT '问卷题目子项名称',
  `data_type`          varchar(16)           DEFAULT NULL COMMENT '数据类型（DATE/TIME/PHONE/EMAIL/TEXT/GENDER/AGE_RANGE）',
  `max_star`           int                   DEFAULT NULL COMMENT '最高星级',
  `sort_order`         int          NOT NULL COMMENT '显示顺序',
  `status`             tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`          bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`        datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`        datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`            int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`survey_sub_item_id`),
  KEY                  `idx_question_sort` (`survey_question_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='问卷题目子项表';

-- Audit class A
CREATE TABLE `sts_survey_target_binding`
(
  `survey_binding_id`             bigint      NOT NULL AUTO_INCREMENT COMMENT '问卷绑定ID（主键）',
  `survey_id`                     bigint      NOT NULL COMMENT '问卷ID',
  `source_type`                   varchar(16) NOT NULL COMMENT '来源类型（COURSE/EXAM/TRAINING/ORGANIZATION）',
  `source_id`                     bigint      NOT NULL COMMENT '来源对象ID',
  `is_available_after_completion` tinyint     NOT NULL COMMENT '是否完成业务后才可填写（0否 1是）',
  `status`                        tinyint     NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`                     bigint               DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`                   datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`                     bigint               DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`                   datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                       bigint      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                       int         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`survey_binding_id`),
  UNIQUE KEY `uk_survey_source_deleted` (`survey_id`, `source_type`, `source_id`, `deleted`),
  KEY                             `idx_source` (`source_type`, `source_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='问卷业务对象绑定表';

-- Audit class A
CREATE TABLE `sts_survey_instance`
(
  `survey_instance_id`      bigint       NOT NULL AUTO_INCREMENT COMMENT '问卷发放实例ID（主键）',
  `organization_id`         bigint       NOT NULL COMMENT '所属机构ID',
  `instance_code`           varchar(64)  NOT NULL COMMENT '问卷发放实例编码',
  `instance_name`           varchar(150) NOT NULL COMMENT '问卷发放实例名称',
  `survey_id`               bigint       NOT NULL COMMENT '问卷ID',
  `survey_version_id`       bigint       NOT NULL COMMENT '本次发放固定问卷版本ID',
  `survey_binding_id`       bigint                DEFAULT NULL COMMENT '问卷业务对象绑定ID（独立发放允许NULL）',
  `source_type`             varchar(24)  NOT NULL COMMENT '发放业务类型（STANDALONE/COURSE/EXAM/TRAINING/ORGANIZATION）',
  `source_id`               bigint       NOT NULL DEFAULT 0 COMMENT '发放业务对象ID（STANDALONE固定为0）',
  `audience_id`             bigint                DEFAULT NULL COMMENT '本次发放冻结参与范围ID（必须为BOUND/FROZEN）',
  `result_view_audience_id` bigint                DEFAULT NULL COMMENT '结果及统计冻结可见范围ID（必须为BOUND/FROZEN，NULL表示仅业务管理员）',
  `publish_mode`            varchar(16)  NOT NULL COMMENT '发放模式（IMMEDIATE/SCHEDULED）',
  `planned_publish_time`    datetime              DEFAULT NULL COMMENT '计划发布时间',
  `published_time`          datetime              DEFAULT NULL COMMENT '实际发布时间',
  `start_time`              datetime              DEFAULT NULL COMMENT '填写开始时间',
  `end_time`                datetime              DEFAULT NULL COMMENT '填写截止时间',
  `is_anonymous_snapshot`   tinyint      NOT NULL COMMENT '本次发放是否匿名展示快照（0否 1是）',
  `instance_status`         varchar(16)  NOT NULL COMMENT '发放状态（DRAFT/SCHEDULED/PUBLISHED/CLOSED/WITHDRAWN）',
  `status`                  tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`               bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`             datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`               bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`             datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                 bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                 int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`survey_instance_id`),
  UNIQUE KEY `uk_org_instance_code_deleted` (`organization_id`, `instance_code`, `deleted`),
  KEY                       `idx_source_status` (`source_type`, `source_id`, `instance_status`),
  KEY                       `idx_version_status` (`survey_version_id`, `instance_status`),
  KEY                       `idx_org_status_time` (`organization_id`, `instance_status`, `published_time`, `end_time`),
  KEY                       `idx_audience` (`audience_id`),
  KEY                       `idx_result_view_audience` (`result_view_audience_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='问卷发放实例表';

-- Audit class A
CREATE TABLE `sts_survey_participant`
(
  `survey_participant_id` bigint      NOT NULL AUTO_INCREMENT COMMENT '问卷参与人员ID（主键）',
  `organization_id`       bigint      NOT NULL COMMENT '所属机构ID',
  `survey_instance_id`    bigint      NOT NULL COMMENT '问卷发放实例ID',
  `survey_id`             bigint      NOT NULL COMMENT '问卷ID',
  `survey_version_id`     bigint      NOT NULL COMMENT '问卷发布版本ID',
  `user_id`               bigint      NOT NULL COMMENT '用户ID',
  `audience_id`           bigint               DEFAULT NULL COMMENT '参与范围ID',
  `participant_status`    varchar(16) NOT NULL COMMENT '参与人员状态（PENDING/DRAFT/SUBMITTED/EXPIRED）',
  `first_open_time`       datetime             DEFAULT NULL COMMENT '首次打开时间',
  `submitted_time`        datetime             DEFAULT NULL COMMENT '提交时间',
  `create_by`             bigint               DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`           datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`             bigint               DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`           datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`               bigint      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`               int         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`survey_participant_id`),
  UNIQUE KEY `uk_instance_user_deleted` (`survey_instance_id`, `user_id`, `deleted`),
  KEY                     `idx_user_status` (`user_id`, `participant_status`),
  KEY                     `idx_instance_status` (`survey_instance_id`, `participant_status`),
  KEY                     `idx_survey_status` (`survey_id`, `participant_status`),
  KEY                     `idx_version_status` (`survey_version_id`, `participant_status`),
  KEY                     `idx_org_status_time` (`organization_id`, `participant_status`, `submitted_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='问卷参与人员表';

-- Audit class A
CREATE TABLE `sts_survey_response`
(
  `survey_response_id`    bigint      NOT NULL AUTO_INCREMENT COMMENT '问卷答卷ID（主键）',
  `organization_id`       bigint      NOT NULL COMMENT '所属机构ID',
  `survey_instance_id`    bigint      NOT NULL COMMENT '问卷发放实例ID',
  `survey_id`             bigint      NOT NULL COMMENT '问卷ID',
  `survey_version_id`     bigint      NOT NULL COMMENT '问卷发布版本ID',
  `survey_participant_id` bigint      NOT NULL COMMENT '问卷参与人员ID',
  `user_id`               bigint      NOT NULL COMMENT '用户ID',
  `is_anonymous`          tinyint     NOT NULL COMMENT '是否匿名展示（0否 1是）',
  `response_status`       varchar(16) NOT NULL COMMENT '答卷状态（DRAFT/SUBMITTED）',
  `started_time`          datetime    NOT NULL COMMENT '开始时间',
  `submitted_time`        datetime             DEFAULT NULL COMMENT '提交时间',
  `client_ip`             varchar(128)         DEFAULT NULL COMMENT '客户端IP地址',
  `device_type`           varchar(16)          DEFAULT NULL COMMENT '设备类型（PC/APP/H5）',
  `create_by`             bigint               DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`           datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`             bigint               DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`           datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`               bigint      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`               int         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`survey_response_id`),
  UNIQUE KEY `uk_participant_deleted` (`survey_participant_id`, `deleted`),
  KEY                     `idx_instance_submit` (`survey_instance_id`, `response_status`, `submitted_time`),
  KEY                     `idx_survey_submit` (`survey_id`, `response_status`, `submitted_time`),
  KEY                     `idx_version_submit` (`survey_version_id`, `response_status`, `submitted_time`),
  KEY                     `idx_org_status_time` (`organization_id`, `response_status`, `submitted_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='问卷答卷表';

-- Audit class A
CREATE TABLE `sts_survey_answer`
(
  `survey_answer_id`   bigint NOT NULL AUTO_INCREMENT COMMENT '问卷答案ID（主键）',
  `survey_response_id` bigint NOT NULL COMMENT '问卷答卷ID',
  `survey_question_id` bigint NOT NULL COMMENT '问卷题目ID',
  `survey_sub_item_id` bigint          DEFAULT NULL COMMENT '问卷题目子项ID',
  `sub_item_no`        int    NOT NULL DEFAULT 0 COMMENT '子项序号',
  `answer_text`        longtext        DEFAULT NULL COMMENT '答案文本',
  `numeric_value`      decimal(10, 2)  DEFAULT NULL COMMENT '数值值',
  `date_value`         date            DEFAULT NULL COMMENT '日期值',
  `time_value`         time            DEFAULT NULL COMMENT '时间值',
  `other_text`         varchar(1000)   DEFAULT NULL COMMENT '其他文本',
  `description_text`   varchar(2000)   DEFAULT NULL COMMENT '描述文本',
  `sort_value`         int             DEFAULT NULL COMMENT '排序值',
  `create_by`          bigint          DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`        datetime        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          bigint          DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`        datetime        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            bigint NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`            int    NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`survey_answer_id`),
  UNIQUE KEY `uk_response_question_slot_deleted` (`survey_response_id`, `survey_question_id`, `sub_item_no`, `deleted`),
  KEY                  `idx_question` (`survey_question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='问卷答案表';

-- Audit class B
CREATE TABLE `sts_survey_answer_option`
(
  `answer_option_id` bigint NOT NULL AUTO_INCREMENT COMMENT '答案选项ID（主键）',
  `survey_answer_id` bigint NOT NULL COMMENT '问卷答案ID',
  `survey_option_id` bigint NOT NULL COMMENT '问卷选项ID',
  `sort_order`       int      DEFAULT NULL COMMENT '显示顺序',
  `create_by`        bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`      datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`answer_option_id`),
  UNIQUE KEY `uk_answer_option` (`survey_answer_id`, `survey_option_id`),
  KEY                `idx_option_answer` (`survey_option_id`, `survey_answer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='问卷答案选项关联表';

-- Audit class B
CREATE TABLE `sts_survey_answer_asset`
(
  `answer_asset_id`  bigint NOT NULL AUTO_INCREMENT COMMENT '答案素材ID（主键）',
  `survey_answer_id` bigint NOT NULL COMMENT '问卷答案ID',
  `file_info_id`     bigint NOT NULL COMMENT '文件信息ID',
  `sort_order`       int    NOT NULL COMMENT '显示顺序',
  `create_by`        bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`      datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`answer_asset_id`),
  UNIQUE KEY `uk_answer_file` (`survey_answer_id`, `file_info_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='问卷答案素材关联表';

-- Audit class A
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

-- Audit class C
CREATE TABLE `sts_evaluation_form_version`
(
  `form_version_id`    bigint         NOT NULL AUTO_INCREMENT COMMENT '表单版本ID（主键）',
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
  UNIQUE KEY `uk_form_version` (`evaluation_form_id`, `version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='评价表单版本记录表';

-- Audit class C
CREATE TABLE `sts_evaluation_item`
(
  `evaluation_item_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '评价项目ID（主键）',
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
  UNIQUE KEY `uk_version_code` (`form_version_id`, `item_code`),
  KEY                  `idx_version_sort` (`form_version_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='评价表单项目记录表';

-- Audit class C
CREATE TABLE `sts_evaluation_score_rule`
(
  `score_rule_id`      bigint        NOT NULL AUTO_INCREMENT COMMENT '分数规则ID（主键）',
  `form_version_id`    bigint        NOT NULL COMMENT '表单版本ID',
  `evaluation_item_id` bigint         DEFAULT NULL COMMENT '评价项目ID',
  `calculation_type`   varchar(16)   NOT NULL COMMENT '基础计算类型（WEIGHT权重/ADJUSTMENT加减分）',
  `source_type`        varchar(24)   NOT NULL COMMENT '成绩来源类型（COURSE/EXAM/OFFLINE/REWARD_PENALTY/MANUAL）',
  `score_change`       decimal(10, 2) DEFAULT NULL COMMENT '基础加减分值（正数加分，负数减分）',
  `weight`             decimal(7, 4)  DEFAULT NULL COMMENT '成绩权重（例如0.3000表示30%）',
  `sort_order`         int           NOT NULL COMMENT '显示顺序',
  `rule_description`   varchar(1000) NOT NULL COMMENT '基础评分规则说明',
  `create_by`          bigint         DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`        datetime       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`score_rule_id`),
  KEY                  `idx_version_source` (`form_version_id`, `calculation_type`, `source_type`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='综合评分基础规则记录表';

-- Audit class A
CREATE TABLE `sts_evaluation_record`
(
  `evaluation_record_id` bigint      NOT NULL AUTO_INCREMENT COMMENT '评价记录ID（主键）',
  `organization_id`      bigint      NOT NULL COMMENT '所属机构ID',
  `form_version_id`      bigint      NOT NULL COMMENT '表单版本ID',
  `target_type`          varchar(24) NOT NULL COMMENT '目标对象类型（PARTICIPANT/COURSE/TRAINING_PLAN/ORGANIZATION）',
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
  KEY                    `idx_evaluator_time` (`evaluator_user_id`, `submitted_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='评价记录表';

-- Audit class A
CREATE TABLE `sts_evaluation_record_item`
(
  `record_item_id`          bigint NOT NULL AUTO_INCREMENT COMMENT '记录项目ID（主键）',
  `evaluation_record_id`    bigint NOT NULL COMMENT '评价记录ID',
  `evaluation_item_id`      bigint NOT NULL COMMENT '评价项目ID',
  `score`                   decimal(10, 2)  DEFAULT NULL COMMENT '分数',
  `text_value`              longtext        DEFAULT NULL COMMENT '文本值',
  `option_value`            varchar(200)    DEFAULT NULL COMMENT '选项值',
  `calculation_source_json` json            DEFAULT NULL COMMENT '计算来源快照JSON',
  `create_by`               bigint          DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`             datetime        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`               bigint          DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`             datetime        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                 bigint NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                 int    NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`record_item_id`),
  UNIQUE KEY `uk_record_item_deleted` (`evaluation_record_id`, `evaluation_item_id`, `deleted`),
  KEY                       `idx_item_score` (`evaluation_item_id`, `score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='评价记录明细表';

-- Audit class A
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='综合成绩计算批次表';

-- Audit class C
CREATE TABLE `sts_evaluation_calculation_source`
(
  `calculation_source_id` bigint         NOT NULL AUTO_INCREMENT COMMENT '综合成绩计算来源ID（主键）',
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
  UNIQUE KEY `uk_run_source` (`calculation_run_id`, `source_record_type`, `source_record_id`, `plan_content_id`),
  KEY                     `idx_source_record` (`source_record_type`, `source_record_id`),
  KEY                     `idx_run_type` (`calculation_run_id`, `source_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='综合成绩计算来源记录表';

-- Audit class A
CREATE TABLE `sts_archive_training`
(
  `training_archive_id` bigint      NOT NULL AUTO_INCREMENT COMMENT '培训档案ID（主键）',
  `organization_id`     bigint      NOT NULL COMMENT '所属机构ID',
  `archive_code`        varchar(64) NOT NULL COMMENT '档案编码',
  `training_plan_id`    bigint      NOT NULL COMMENT '培训计划ID',
  `current_version_no`  int         NOT NULL COMMENT '当前版本号',
  `archive_status`      varchar(16) NOT NULL COMMENT '归档状态（DRAFT/SEALED/SUPERSEDED）',
  `sealed_by`           bigint               DEFAULT NULL COMMENT '封存人用户ID',
  `sealed_time`         datetime             DEFAULT NULL COMMENT '封存时间',
  `status`              tinyint     NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`           bigint               DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`         datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`           bigint               DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`         datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`             bigint      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`             int         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`training_archive_id`),
  UNIQUE KEY `uk_org_archive_code_deleted` (`organization_id`, `archive_code`, `deleted`),
  UNIQUE KEY `uk_plan_deleted` (`training_plan_id`, `deleted`),
  KEY                   `idx_org_status` (`organization_id`, `archive_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训档案表';

-- Audit class C
CREATE TABLE `sts_archive_version`
(
  `archive_version_id`         bigint       NOT NULL AUTO_INCREMENT COMMENT '档案版本ID（主键）',
  `training_archive_id`        bigint       NOT NULL COMMENT '培训档案ID',
  `version_no`                 int          NOT NULL COMMENT '版本号',
  `plan_snapshot_json`         json         NOT NULL COMMENT '培训计划快照JSON',
  `organization_snapshot_json` json         NOT NULL COMMENT '机构快照JSON',
  `summary_snapshot_json`      json         NOT NULL COMMENT '汇总结果快照JSON',
  `snapshot_hash`              varchar(128) NOT NULL COMMENT '快照校验哈希',
  `generated_time`             datetime     NOT NULL COMMENT '生成时间',
  `generated_by`               bigint       NOT NULL COMMENT '生成人用户ID',
  `version_reason`             varchar(500) NOT NULL COMMENT '版本原因',
  `create_by`                  bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`                datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`archive_version_id`),
  UNIQUE KEY `uk_archive_version` (`training_archive_id`, `version_no`),
  KEY                          `idx_generated_time` (`generated_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训档案版本记录表';

-- Audit class C
CREATE TABLE `sts_archive_participant`
(
  `archive_participant_id`       bigint      NOT NULL AUTO_INCREMENT COMMENT '档案参与人员ID（主键）',
  `archive_version_id`           bigint      NOT NULL COMMENT '档案版本ID',
  `training_participant_id`      bigint      NOT NULL COMMENT '培训参与人员ID',
  `user_id`                      bigint      NOT NULL COMMENT '用户ID',
  `participant_org_id`           bigint      NOT NULL COMMENT '参训人员所属机构ID',
  `member_snapshot_json`         json        NOT NULL COMMENT '成员信息快照JSON',
  `attendance_snapshot_json`     json        NOT NULL COMMENT '签到快照JSON',
  `learning_snapshot_json`       json        NOT NULL COMMENT '学习结果快照JSON',
  `exam_snapshot_json`           json        NOT NULL COMMENT '考试结果快照JSON',
  `evaluation_snapshot_json`     json        NOT NULL COMMENT '评价结果快照JSON',
  `reward_penalty_snapshot_json` json        NOT NULL COMMENT '奖惩快照JSON',
  `certificate_snapshot_json`    json        NOT NULL COMMENT '证书快照JSON',
  `final_score`                  decimal(10, 2) DEFAULT NULL COMMENT '最终分数',
  `result_status`                varchar(16) NOT NULL COMMENT '归档结果状态（PASSED/FAILED/INCOMPLETE）',
  `create_by`                    bigint         DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`                  datetime       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`archive_participant_id`),
  UNIQUE KEY `uk_version_participant` (`archive_version_id`, `training_participant_id`),
  KEY                            `idx_user_version` (`user_id`, `archive_version_id`),
  KEY                            `idx_org_version` (`participant_org_id`, `archive_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训档案人员记录表';

-- Audit class C
CREATE TABLE `sts_archive_asset`
(
  `archive_asset_id`     bigint       NOT NULL AUTO_INCREMENT COMMENT '档案素材ID（主键）',
  `archive_version_id`   bigint       NOT NULL COMMENT '档案版本ID',
  `source_attachment_id` bigint       DEFAULT NULL COMMENT '来源附件ID',
  `asset_type`           varchar(24)  NOT NULL COMMENT '档案附件类型（NOTICE/ATTENDANCE/IMAGE/VIDEO/SUMMARY/OTHER）',
  `file_info_id`         bigint       NOT NULL COMMENT '文件信息ID',
  `file_name_snapshot`   varchar(255) NOT NULL COMMENT '文件名称快照',
  `checksum`             varchar(128) DEFAULT NULL COMMENT '文件校验值',
  `sort_order`           int          NOT NULL COMMENT '显示顺序',
  `create_by`            bigint       DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`          datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`archive_asset_id`),
  UNIQUE KEY `uk_version_file` (`archive_version_id`, `file_info_id`),
  KEY                    `idx_version_type` (`archive_version_id`, `asset_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训档案附件记录表';

-- Audit class A
CREATE TABLE `sts_archive_export_job`
(
  `archive_export_job_id` bigint      NOT NULL AUTO_INCREMENT COMMENT '档案导出任务ID（主键）',
  `organization_id`       bigint      NOT NULL COMMENT '所属机构ID',
  `archive_version_id`    bigint               DEFAULT NULL COMMENT '档案版本ID',
  `export_scope`          varchar(16) NOT NULL COMMENT '导出范围（PERSON/ORGANIZATION/PERIOD）',
  `scope_id`              bigint      NOT NULL COMMENT '导出范围对象ID',
  `export_format`         varchar(16) NOT NULL COMMENT '导出格式（允许值待产品确认）',
  `job_status`            varchar(16) NOT NULL COMMENT '导出任务状态（PENDING/RUNNING/SUCCEEDED/FAILED）',
  `result_file_id`        bigint               DEFAULT NULL COMMENT '结果文件ID',
  `failure_reason`        varchar(1000)        DEFAULT NULL COMMENT '失败原因',
  `requested_by`          bigint      NOT NULL COMMENT '请求人用户ID',
  `finished_time`         datetime             DEFAULT NULL COMMENT '完成时间',
  `create_by`             bigint               DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`           datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`             bigint               DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`           datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`               bigint      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`               int         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`archive_export_job_id`),
  KEY                     `idx_requester_time` (`requested_by`, `create_time`),
  KEY                     `idx_status_time` (`job_status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='档案导出任务表';

-- Audit class A
CREATE TABLE `sts_regulation_warning_rule`
(
  `warning_rule_id`  bigint        NOT NULL AUTO_INCREMENT COMMENT '预警规则ID（主键）',
  `organization_id`  bigint        NOT NULL COMMENT '所属机构ID',
  `rule_code`        varchar(64)   NOT NULL COMMENT '规则编码',
  `rule_name`        varchar(100)  NOT NULL COMMENT '规则名称',
  `warning_type`     varchar(32)   NOT NULL COMMENT '预警类型（PLAN_HOURS/ONLINE_RATIO/PARTICIPATION/COMPLETION/OTHER）',
  `training_type_id` bigint                 DEFAULT NULL COMMENT '培训类型ID',
  `warning_level`    varchar(16)   NOT NULL COMMENT '预警级别（允许值待产品确认）',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='监管预警规则表';

-- Audit class A
CREATE TABLE `sts_regulation_warning_condition`
(
  `warning_condition_id` bigint         NOT NULL AUTO_INCREMENT COMMENT '预警条件ID（主键）',
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
  UNIQUE KEY `uk_rule_metric_deleted` (`warning_rule_id`, `metric_code`, `deleted`),
  KEY                    `idx_rule_sort` (`warning_rule_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='监管预警条件表';

-- Audit class A
CREATE TABLE `sts_regulation_warning`
(
  `warning_id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '预警ID（主键）',
  `organization_id`     bigint       NOT NULL COMMENT '所属机构ID',
  `warning_rule_id`     bigint       NOT NULL COMMENT '预警规则ID',
  `regulated_org_id`    bigint       NOT NULL COMMENT '被监管机构ID',
  `training_plan_id`    bigint                DEFAULT NULL COMMENT '培训计划ID',
  `detection_cycle`     varchar(64)  NOT NULL COMMENT '预警检测周期标识',
  `dedup_key`           varchar(128) NOT NULL COMMENT '预警幂等去重键（规则、机构、业务对象和检测周期稳定摘要）',
  `warning_level`       varchar(16)  NOT NULL COMMENT '预警级别（允许值待产品确认）',
  `warning_title`       varchar(200) NOT NULL COMMENT '预警标题',
  `warning_status`      varchar(16)  NOT NULL COMMENT '预警状态（OPEN/VIEWED/PROCESSING/CLOSED/IGNORED）',
  `detected_time`       datetime     NOT NULL COMMENT '检测时间',
  `first_viewed_time`   datetime              DEFAULT NULL COMMENT '首次查看时间',
  `closed_time`         datetime              DEFAULT NULL COMMENT '关闭时间',
  `current_assignee_id` bigint                DEFAULT NULL COMMENT '当前负责人用户ID',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='监管预警表';

-- Audit class C
CREATE TABLE `sts_regulation_warning_evidence`
(
  `warning_evidence_id`    bigint       NOT NULL AUTO_INCREMENT COMMENT '预警证据ID（主键）',
  `warning_id`             bigint       NOT NULL COMMENT '预警ID',
  `evidence_key`           varchar(128) NOT NULL COMMENT '证据幂等键（指标、来源记录及来源事实版本稳定摘要）',
  `metric_code`            varchar(64)  NOT NULL COMMENT '指标编码',
  `actual_value`           decimal(12, 4) DEFAULT NULL COMMENT '实际值',
  `threshold_value`        decimal(12, 4) DEFAULT NULL COMMENT '阈值值',
  `unit`                   varchar(16)    DEFAULT NULL COMMENT '单位',
  `source_type`            varchar(24)  NOT NULL COMMENT '证据来源类型（STANDARD/TRAINING_PLAN/COURSE/EXAM/ARCHIVE）',
  `source_id`              bigint       NOT NULL COMMENT '来源对象ID',
  `evidence_snapshot_json` json           DEFAULT NULL COMMENT '预警证据快照JSON',
  `create_by`              bigint         DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`            datetime       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`warning_evidence_id`),
  UNIQUE KEY `uk_warning_evidence` (`warning_id`, `evidence_key`),
  KEY                      `idx_warning_metric` (`warning_id`, `metric_code`),
  KEY                      `idx_source` (`source_type`, `source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='监管预警证据记录表';

-- Audit class C
CREATE TABLE `sts_regulation_warning_action`
(
  `warning_action_id` bigint      NOT NULL AUTO_INCREMENT COMMENT '预警操作ID（主键）',
  `warning_id`        bigint      NOT NULL COMMENT '预警ID',
  `action_type`       varchar(16) NOT NULL COMMENT '操作类型（VIEW/ASSIGN/COMMENT/CLOSE/IGNORE/REOPEN）',
  `action_by`         bigint      NOT NULL COMMENT '操作人用户ID',
  `action_time`       datetime    NOT NULL COMMENT '操作时间',
  `before_status`     varchar(16)   DEFAULT NULL COMMENT '变更前预警状态（OPEN/VIEWED/PROCESSING/CLOSED/IGNORED）',
  `after_status`      varchar(16)   DEFAULT NULL COMMENT '变更后预警状态（OPEN/VIEWED/PROCESSING/CLOSED/IGNORED）',
  `action_content`    varchar(2000) DEFAULT NULL COMMENT '操作内容',
  `create_by`         bigint        DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`       datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`warning_action_id`),
  KEY                 `idx_warning_time` (`warning_id`, `action_time`),
  KEY                 `idx_actor_time` (`action_by`, `action_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='监管预警操作记录表';

SET
FOREIGN_KEY_CHECKS = 1;
