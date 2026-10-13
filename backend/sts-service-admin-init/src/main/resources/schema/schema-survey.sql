-- Integration-test schema: survey
SET NAMES utf8mb4;

CREATE TABLE `sts_survey_answer`
(
  `survey_answer_id`   bigint NOT NULL AUTO_INCREMENT COMMENT '问卷答案ID（主键）',
  `organization_id`    bigint NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `survey_response_id` bigint NOT NULL COMMENT '问卷答卷ID',
  `survey_question_id` bigint NOT NULL COMMENT '问卷题目ID',
  `survey_sub_item_id` bigint         DEFAULT NULL COMMENT '【暂时隐藏】原用于：矩阵/子项答案；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `sub_item_no`        int    NOT NULL DEFAULT 0 COMMENT '子项序号',
  `answer_text`        longtext        DEFAULT NULL COMMENT '答案文本',
  `numeric_value`      decimal(10, 2) DEFAULT NULL COMMENT '【暂时隐藏】原用于：数值答案；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `date_value`         date           DEFAULT NULL COMMENT '【暂时隐藏】原用于：日期答案；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `time_value`         time           DEFAULT NULL COMMENT '【暂时隐藏】原用于：时间答案；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `other_text`         varchar(1000)  DEFAULT NULL COMMENT '【暂时隐藏】原用于：其他选项文本；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `description_text`   varchar(2000)  DEFAULT NULL COMMENT '【暂时隐藏】原用于：补充描述；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `sort_value`         int            DEFAULT NULL COMMENT '【暂时隐藏】原用于：排序题答案；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `create_by`          bigint          DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`        datetime        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          bigint          DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`        datetime        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            bigint NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`            int    NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`survey_answer_id`),
  KEY                  `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_response_question_slot_deleted` (`survey_response_id`, `survey_question_id`, `sub_item_no`, `deleted`),
  KEY                  `idx_question` (`survey_question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='问卷答案表';

CREATE TABLE `sts_survey_answer_asset`
(
  `answer_asset_id`  bigint NOT NULL AUTO_INCREMENT COMMENT '答案素材ID（主键）',
  `organization_id`  bigint NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `survey_answer_id` bigint NOT NULL COMMENT '问卷答案ID',
  `file_info_id`     bigint NOT NULL COMMENT '文件信息ID',
  `sort_order`       int    NOT NULL COMMENT '显示顺序',
  `create_by`        bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`      datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`answer_asset_id`),
  KEY                `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_answer_file` (`survey_answer_id`, `file_info_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='【暂时隐藏】原用于：问卷附件题答案素材；当前中山50最小闭环不启用，不得作为当前页面、API或业务规则依据；后续启用须新建并确认 OpenSpec Change';

CREATE TABLE `sts_survey_answer_option`
(
  `answer_option_id` bigint NOT NULL AUTO_INCREMENT COMMENT '答案选项ID（主键）',
  `organization_id`  bigint NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `survey_answer_id` bigint NOT NULL COMMENT '问卷答案ID',
  `survey_option_id` bigint NOT NULL COMMENT '问卷选项ID',
  `sort_order`       int      DEFAULT NULL COMMENT '显示顺序',
  `create_by`        bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`      datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`answer_option_id`),
  KEY                `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_answer_option` (`survey_answer_id`, `survey_option_id`),
  KEY                `idx_option_answer` (`survey_option_id`, `survey_answer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='问卷答案选项关联表';

CREATE TABLE `sts_survey_instance`
(
  `survey_instance_id`    bigint       NOT NULL AUTO_INCREMENT COMMENT '问卷发放实例ID（主键）',
  `organization_id`       bigint       NOT NULL COMMENT '所属机构ID',
  `instance_code`         varchar(64)  NOT NULL COMMENT '问卷发放实例编码',
  `instance_name`         varchar(150) NOT NULL COMMENT '问卷发放实例名称',
  `survey_id`             bigint       NOT NULL COMMENT '问卷ID',
  `survey_version_id`     bigint      NOT NULL COMMENT '【内部保留】用于：本次投放固定问卷快照；不提供独立页面或对外操作',
  `survey_binding_id`     bigint               DEFAULT NULL COMMENT '【暂时隐藏】原用于：问卷业务对象绑定ID；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `purpose`               varchar(32) NOT NULL DEFAULT 'TRAINING_SURVEY' COMMENT '投放用途（TRAINING_SURVEY 线下培训调查 / ORG_EVALUATION 培训机构评价）',
  `result_visibility`     varchar(32) NOT NULL DEFAULT 'ADMIN_ONLY' COMMENT '结果可见方式（ADMIN_ONLY 仅管理员 / SUBMITTER_AFTER_SUBMIT 提交后填写人可见）',
  `source_type`           varchar(24)  NOT NULL COMMENT '发放业务类型（STANDALONE/COURSE/EXAM/TRAINING/ORGANIZATION）',
  `source_id`             bigint       NOT NULL DEFAULT 0 COMMENT '发放业务对象ID（STANDALONE固定为0）',
  `publish_mode`          varchar(16)  NOT NULL COMMENT '发放模式（IMMEDIATE/SCHEDULED）',
  `planned_publish_time`  datetime              DEFAULT NULL COMMENT '计划发布时间',
  `published_time`        datetime              DEFAULT NULL COMMENT '实际发布时间',
  `start_time`            datetime              DEFAULT NULL COMMENT '填写开始时间',
  `end_time`              datetime              DEFAULT NULL COMMENT '填写截止时间',
  `is_anonymous_snapshot` tinyint     NOT NULL COMMENT '【暂时隐藏】原用于：投放匿名快照；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `instance_status`       varchar(16)  NOT NULL COMMENT '发放状态（DRAFT/SCHEDULED/PUBLISHED/CLOSED/WITHDRAWN）',
  `status`                tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`             bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`           datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`             bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`           datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`               bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`               int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`survey_instance_id`),
  UNIQUE KEY `uk_org_instance_code_deleted` (`organization_id`, `instance_code`, `deleted`),
  KEY                     `idx_source_status` (`source_type`, `source_id`, `instance_status`),
  KEY                     `idx_version_status` (`survey_version_id`, `instance_status`),
  KEY                     `idx_org_status_time` (`organization_id`, `instance_status`, `published_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='问卷发放实例表';

CREATE TABLE `sts_survey_option`
(
  `survey_option_id`   bigint        NOT NULL AUTO_INCREMENT COMMENT '问卷选项ID（主键）',
  `organization_id`    bigint        NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
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
  KEY                  `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_question_key_deleted` (`survey_question_id`, `option_key`, `deleted`),
  KEY                  `idx_question_sort` (`survey_question_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='问卷选项表';

CREATE TABLE `sts_survey_participant`
(
  `survey_participant_id` bigint      NOT NULL AUTO_INCREMENT COMMENT '问卷参与人员ID（主键）',
  `organization_id`       bigint      NOT NULL COMMENT '所属机构ID',
  `survey_instance_id`    bigint      NOT NULL COMMENT '问卷发放实例ID',
  `survey_id`             bigint      NOT NULL COMMENT '问卷ID',
  `survey_version_id`     bigint      NOT NULL COMMENT '问卷发布版本ID',
  `user_id`               bigint      NOT NULL COMMENT '用户ID',
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

CREATE TABLE `sts_survey_question`
(
  `survey_question_id`     bigint       NOT NULL AUTO_INCREMENT COMMENT '问卷题目ID（主键）',
  `organization_id`        bigint       NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `survey_id`              bigint       NOT NULL COMMENT '问卷ID',
  `question_type`          varchar(24)  NOT NULL COMMENT '题目类型（SINGLE/MULTIPLE/SINGLE_FILL/MULTI_FILL/SORT/RATING/SCORE/UPLOAD/TEXT）',
  `question_text`          varchar(300) NOT NULL COMMENT '题目文本',
  `is_required`            tinyint      NOT NULL COMMENT '是否必修或必填（0否 1是）',
  `is_other_allowed`       tinyint NOT NULL COMMENT '【暂时隐藏】原用于：允许其他选项；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `is_description_allowed` tinyint NOT NULL COMMENT '【暂时隐藏】原用于：允许补充描述；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `data_type`              varchar(16) DEFAULT NULL COMMENT '【暂时隐藏】原用于：填空数据类型；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `max_star`               int         DEFAULT NULL COMMENT '【暂时隐藏】原用于：评分题最高星级；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `max_upload_count`       int         DEFAULT NULL COMMENT '【暂时隐藏】原用于：附件题最大上传数量；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `sort_order`             int          NOT NULL COMMENT '显示顺序',
  `status`                 tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`              bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`            datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`              bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`            datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`survey_question_id`),
  KEY                      `idx_organization_id` (`organization_id`),
  KEY                      `idx_survey_sort` (`survey_id`, `sort_order`),
  KEY                      `idx_survey_type` (`survey_id`, `question_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='问卷题目表';

CREATE TABLE `sts_survey_response`
(
  `survey_response_id`    bigint      NOT NULL AUTO_INCREMENT COMMENT '问卷答卷ID（主键）',
  `organization_id`       bigint      NOT NULL COMMENT '所属机构ID',
  `survey_instance_id`    bigint      NOT NULL COMMENT '问卷发放实例ID',
  `survey_id`             bigint      NOT NULL COMMENT '问卷ID',
  `survey_version_id`     bigint      NOT NULL COMMENT '问卷发布版本ID',
  `survey_participant_id` bigint      NOT NULL COMMENT '问卷参与人员ID',
  `user_id`               bigint      NOT NULL COMMENT '用户ID',
  `is_anonymous` tinyint NOT NULL COMMENT '【暂时隐藏】原用于：答卷匿名展示；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
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

CREATE TABLE `sts_survey_sub_item`
(
  `survey_sub_item_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '问卷题目子项ID（主键）',
  `organization_id`    bigint       NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
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
  KEY                  `idx_organization_id` (`organization_id`),
  KEY                  `idx_question_sort` (`survey_question_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='【暂时隐藏】原用于：问卷矩阵/子项题；当前中山50最小闭环不启用，不得作为当前页面、API或业务规则依据；后续启用须新建并确认 OpenSpec Change';

CREATE TABLE `sts_survey_survey`
(
  `survey_id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '问卷ID（主键）',
  `organization_id`    bigint       NOT NULL COMMENT '所属机构ID',
  `survey_code`        varchar(64)  NOT NULL COMMENT '问卷编码',
  `survey_name`        varchar(100) NOT NULL COMMENT '问卷名称',
  `current_version_id` bigint DEFAULT NULL COMMENT '【内部保留】用于：当前投放快照版本ID；不提供独立页面或对外操作',
  `survey_type`        varchar(32)  NOT NULL COMMENT '问卷类型（GENERAL/TRAINING/ORGANIZATION_EVALUATION）',
  `cover_file_id`      bigint                DEFAULT NULL COMMENT '封面文件ID',
  `description`        longtext              DEFAULT NULL COMMENT '描述',
  `closing_text`       longtext              DEFAULT NULL COMMENT '结束语文本',
  `is_anonymous`       tinyint NOT NULL COMMENT '【暂时隐藏】原用于：问卷匿名展示；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
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

CREATE TABLE `sts_survey_target_binding`
(
  `survey_binding_id`             bigint      NOT NULL AUTO_INCREMENT COMMENT '问卷绑定ID（主键）',
  `organization_id`               bigint      NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
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
  KEY                             `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_survey_source_deleted` (`survey_id`, `source_type`, `source_id`, `deleted`),
  KEY                             `idx_source` (`source_type`, `source_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='【暂时隐藏】原用于：问卷高级业务对象绑定；当前中山50最小闭环不启用，不得作为当前页面、API或业务规则依据；后续启用须新建并确认 OpenSpec Change';

CREATE TABLE `sts_survey_version`
(
  `survey_version_id`     bigint       NOT NULL AUTO_INCREMENT COMMENT '问卷发布版本ID（主键）',
  `organization_id`       bigint       NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `survey_id`             bigint       NOT NULL COMMENT '问卷ID',
  `version_no`            int          NOT NULL COMMENT '版本号（从1递增）',
  `content_snapshot_json` json         NOT NULL COMMENT '【内部保留】用于：投放时冻结题目和选项；不提供独立页面或对外操作',
  `snapshot_hash`         varchar(128) NOT NULL COMMENT '【内部保留】用于：问卷投放快照一致性校验；不提供独立页面或对外操作',
  `version_note`          varchar(500) DEFAULT NULL COMMENT '版本说明',
  `published_by`          bigint       NOT NULL COMMENT '发布人用户ID',
  `published_time`        datetime     NOT NULL COMMENT '发布时间',
  `create_by`             bigint       DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`           datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`survey_version_id`),
  KEY                     `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_survey_version` (`survey_id`, `version_no`),
  KEY                     `idx_survey_publish_time` (`survey_id`, `published_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='【内部保留】用于：投放时冻结问卷内容快照；不提供独立页面或对外操作';
