-- Integration-test schema: question + paper + import_batch
SET NAMES utf8mb4;

CREATE TABLE `sts_common_import_batch`
(
  `import_batch_id` bigint      NOT NULL AUTO_INCREMENT COMMENT '导入批次ID（主键）',
  `organization_id` bigint      NOT NULL COMMENT '所属机构ID',
  `business_type`   varchar(32) NOT NULL COMMENT '导入业务类型（QUESTION题库题目/USER用户账号/TRAINING_PARTICIPANT参训人员/OFFLINE_SCORE线下成绩）',
  `business_id`     bigint      NOT NULL DEFAULT 0 COMMENT '导入目标业务对象ID（QUESTION为题库ID，TRAINING_PARTICIPANT为培训计划ID，OFFLINE_SCORE为线下培训场次ID，USER为机构级导入固定为0）',
  `source_file_id`  bigint      NOT NULL COMMENT '源文件ID',
  `template_type`   varchar(16) NOT NULL COMMENT '模板类型（WORD/EXCEL；仅题库题目导入支持WORD）',
  `import_status`   varchar(16) NOT NULL COMMENT '导入状态（PENDING/PROCESSING/SUCCEEDED/PARTIAL_FAILED/FAILED）',
  `total_rows`      int         NOT NULL COMMENT '总行数',
  `success_rows`    int         NOT NULL COMMENT '成功行数',
  `failure_rows`    int         NOT NULL COMMENT '失败行数',
  `result_file_id`  bigint               DEFAULT NULL COMMENT '结果回执文件ID',
  `started_time`    datetime             DEFAULT NULL COMMENT '开始时间',
  `finished_time`   datetime             DEFAULT NULL COMMENT '完成时间',
  `create_by`       bigint               DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`     datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       bigint               DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`     datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         bigint      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`         int         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`import_batch_id`),
  KEY               `idx_org_type_time` (`organization_id`, `business_type`, `create_time`),
  KEY               `idx_business` (`business_type`, `business_id`, `create_time`),
  KEY               `idx_status` (`import_status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公共导入批次表';

-- Audit class C
CREATE TABLE `sts_common_import_error`
(
  `import_error_id` bigint        NOT NULL AUTO_INCREMENT COMMENT '导入错误ID（主键）',
  `organization_id` bigint        NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `import_batch_id` bigint        NOT NULL COMMENT '导入批次ID',
  `row_no`          int           NOT NULL COMMENT '数据行号',
  `field_name`      varchar(64) DEFAULT NULL COMMENT '字段名称',
  `error_code`      varchar(64)   NOT NULL COMMENT '错误编码',
  `error_message`   varchar(1000) NOT NULL COMMENT '错误消息',
  `raw_data_json`   json        DEFAULT NULL COMMENT '原始导入数据JSON',
  `create_by`       bigint      DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`     datetime    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`import_error_id`),
  KEY               `idx_organization_id` (`organization_id`),
  KEY               `idx_batch_row` (`import_batch_id`, `row_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公共导入错误记录表';

-- Audit class A
CREATE TABLE `sts_question_knowledge_point`
(
  `knowledge_point_id` bigint        NOT NULL AUTO_INCREMENT COMMENT '知识点ID（主键）',
  `organization_id`    bigint        NOT NULL COMMENT '所属机构ID',
  `parent_id`          bigint        NOT NULL DEFAULT 0 COMMENT '父级ID',
  `knowledge_code`     varchar(64)   NOT NULL COMMENT '知识点编码',
  `knowledge_name`     varchar(100)  NOT NULL COMMENT '知识点名称',
  `ancestors`          varchar(1000) NOT NULL COMMENT '祖级路径',
  `sort_order`         int           NOT NULL COMMENT '显示顺序',
  `status`             tinyint       NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`          bigint                 DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`        datetime               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          bigint                 DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`        datetime               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            bigint        NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`            int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`knowledge_point_id`),
  UNIQUE KEY `uk_org_code_deleted` (`organization_id`, `knowledge_code`, `deleted`),
  KEY                  `idx_parent_sort` (`parent_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='知识点表';

-- Audit class A
CREATE TABLE `sts_question_bank_category`
(
  `bank_category_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '题库分类ID（主键）',
  `organization_id`  bigint       NOT NULL COMMENT '所属机构ID',
  `category_name`    varchar(100) NOT NULL COMMENT '分类名称',
  `description`      varchar(500) NOT NULL COMMENT '描述',
  `sort_order`       int          NOT NULL COMMENT '显示顺序',
  `status`           tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`        bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`      datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`      datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`          bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`          int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`bank_category_id`),
  UNIQUE KEY `uk_org_name_deleted` (`organization_id`, `category_name`, `deleted`),
  KEY                `idx_org_sort` (`organization_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='题库分类表';

-- Audit class A
CREATE TABLE `sts_question_bank`
(
  `question_bank_id`    bigint       NOT NULL AUTO_INCREMENT COMMENT '题库ID（主键）',
  `organization_id`     bigint       NOT NULL COMMENT '所属机构ID',
  `bank_code`           varchar(64)  NOT NULL COMMENT '题库编码',
  `bank_name`           varchar(100) NOT NULL COMMENT '题库名称',
  `bank_category_id`    bigint                DEFAULT NULL COMMENT '题库分类ID',
  `difficulty`          varchar(16)  NOT NULL COMMENT '难度（EASY/NORMAL/HARD/VERY_HARD）',
  `question_order_mode` varchar(16)  NOT NULL COMMENT '题目排序模式（TYPE/IMPORT）',
  `archive_status`      varchar(16)  NOT NULL COMMENT '归档状态（ACTIVE/ARCHIVED）',
  `status`              tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`           bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`         datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`           bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`         datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`             bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`             int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`question_bank_id`),
  UNIQUE KEY `uk_org_bank_code_deleted` (`organization_id`, `bank_code`, `deleted`),
  KEY                   `idx_org_category_status` (`organization_id`, `bank_category_id`, `archive_status`),
  KEY                   `idx_org_status_time` (`organization_id`, `status`, `create_time`),
  KEY                   `idx_name` (`bank_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='题库表';

-- Audit class B
CREATE TABLE `sts_question_bank_knowledge`
(
  `bank_knowledge_id`  bigint NOT NULL AUTO_INCREMENT COMMENT '题库知识点关联ID（主键）',
  `organization_id`    bigint NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `question_bank_id`   bigint NOT NULL COMMENT '题库ID',
  `knowledge_point_id` bigint NOT NULL COMMENT '知识点ID',
  `create_by`          bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`        datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`bank_knowledge_id`),
  KEY                  `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_bank_knowledge` (`question_bank_id`, `knowledge_point_id`),
  KEY                  `idx_knowledge_bank` (`knowledge_point_id`, `question_bank_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='题库与知识点关联表';

-- Audit class A
CREATE TABLE `sts_question_question`
(
  `question_id`        bigint      NOT NULL AUTO_INCREMENT COMMENT '题目ID（主键）',
  `organization_id`    bigint      NOT NULL COMMENT '所属机构ID',
  `question_bank_id`   bigint      NOT NULL COMMENT '题库ID',
  `question_code`      varchar(64) NOT NULL COMMENT '题目编码',
  `current_version_no` int         NOT NULL COMMENT '当前版本号',
  `archive_status`     varchar(16) NOT NULL COMMENT '归档状态（ACTIVE/ARCHIVED）',
  `status`             tinyint     NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`          bigint               DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`        datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          bigint               DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`        datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            bigint      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`            int         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`question_id`),
  UNIQUE KEY `uk_org_question_code_deleted` (`organization_id`, `question_code`, `deleted`),
  KEY                  `idx_bank_status` (`question_bank_id`, `status`, `archive_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='题目主表';

-- Audit class B
CREATE TABLE `sts_question_question_knowledge`
(
  `question_knowledge_id` bigint NOT NULL AUTO_INCREMENT COMMENT '题目知识点关联ID（主键）',
  `organization_id`       bigint NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `question_id`           bigint NOT NULL COMMENT '题目ID',
  `knowledge_point_id`    bigint NOT NULL COMMENT '知识点ID',
  `create_by`             bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`           datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`question_knowledge_id`),
  KEY                     `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_question_knowledge` (`question_id`, `knowledge_point_id`),
  KEY                     `idx_knowledge_question` (`knowledge_point_id`, `question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='题目与知识点关联表';

-- Audit class C
CREATE TABLE `sts_question_version`
(
  `question_version_id`         bigint      NOT NULL AUTO_INCREMENT COMMENT '题目版本ID（主键）',
  `organization_id`             bigint      NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `question_id`                 bigint      NOT NULL COMMENT '题目ID',
  `version_no`                  int         NOT NULL COMMENT '版本号',
  `question_type`               varchar(16) NOT NULL COMMENT '题目类型（SINGLE/MULTIPLE/JUDGE/FILL/ESSAY）',
  `stem`                        longtext    NOT NULL COMMENT '题干',
  `analysis`                    longtext     DEFAULT NULL COMMENT '解析',
  `standard_answer`             longtext     DEFAULT NULL COMMENT '问答题标准答案',
  `difficulty`                  varchar(16) NOT NULL COMMENT '难度（EASY简单/NORMAL普通/HARD困难/VERY_HARD很难）',
  `grading_mode`                varchar(16) NOT NULL COMMENT '评分模式（AUTO/MANUAL/KEYWORD）',
  `is_unordered_answer_allowed` tinyint     NOT NULL COMMENT '是否允许填空答案顺序不一致（0否 1是）',
  `is_case_ignored`             tinyint     NOT NULL COMMENT '是否忽略英文大小写（0否 1是）',
  `is_full_half_width_ignored`  tinyint     NOT NULL COMMENT '是否忽略全角半角符号差异（0否 1是）',
  `published_status`            varchar(16) NOT NULL COMMENT '发布状态（DRAFT/PUBLISHED/ARCHIVED）',
  `published_by`                bigint       DEFAULT NULL COMMENT '发布人用户ID（发布后必填）',
  `published_time`              datetime     DEFAULT NULL COMMENT '发布时间（发布后必填）',
  `version_note`                varchar(500) DEFAULT NULL COMMENT '版本说明',
  `create_by`                   bigint       DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`                 datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`question_version_id`),
  KEY                           `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_question_version` (`question_id`, `version_no`),
  KEY                           `idx_type_status` (`question_type`, `published_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='题目版本记录表';

-- Audit class C
CREATE TABLE `sts_question_option`
(
  `question_option_id`  bigint      NOT NULL AUTO_INCREMENT COMMENT '题目选项ID（主键）',
  `organization_id`     bigint      NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `question_version_id` bigint      NOT NULL COMMENT '题目版本ID',
  `option_key`          varchar(16) NOT NULL COMMENT '选项键',
  `option_content`      longtext    NOT NULL COMMENT '选项内容',
  `is_correct`          tinyint     NOT NULL COMMENT '是否正确（0否 1是）',
  `sort_order`          int         NOT NULL COMMENT '显示顺序',
  `create_by`           bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`         datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`question_option_id`),
  KEY                   `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_version_key` (`question_version_id`, `option_key`),
  KEY                   `idx_version_sort` (`question_version_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='题目选项记录表';

-- Audit class C
CREATE TABLE `sts_question_blank_answer`
(
  `blank_answer_id`     bigint        NOT NULL AUTO_INCREMENT COMMENT '填空答案ID（主键）',
  `organization_id`     bigint        NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `question_version_id` bigint        NOT NULL COMMENT '题目版本ID',
  `blank_no`            int           NOT NULL COMMENT '填空序号',
  `answer_group_no`     int           NOT NULL COMMENT '答案组序号',
  `answer_text`         varchar(1000) NOT NULL COMMENT '答案文本',
  `sort_order`          int           NOT NULL COMMENT '显示顺序',
  `create_by`           bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`         datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`blank_answer_id`),
  KEY                   `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_version_blank_answer` (`question_version_id`, `blank_no`, `answer_group_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='题目填空答案记录表';

-- Audit class C
CREATE TABLE `sts_question_keyword`
(
  `question_keyword_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '题目关键词ID（主键）',
  `organization_id`     bigint       NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `question_version_id` bigint       NOT NULL COMMENT '题目版本ID',
  `keyword`             varchar(200) NOT NULL COMMENT '关键词',
  `weight`              decimal(7, 4) DEFAULT NULL COMMENT '权重',
  `sort_order`          int          NOT NULL COMMENT '显示顺序',
  `create_by`           bigint        DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`         datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`question_keyword_id`),
  KEY                   `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_version_keyword` (`question_version_id`, `keyword`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='题目答案关键词记录表';

-- Audit class C
CREATE TABLE `sts_question_asset`
(
  `question_asset_id`   bigint      NOT NULL AUTO_INCREMENT COMMENT '题目素材ID（主键）',
  `organization_id`     bigint      NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `question_version_id` bigint      NOT NULL COMMENT '题目版本ID',
  `asset_id`            bigint      NOT NULL COMMENT '素材ID',
  `asset_role`          varchar(16) NOT NULL COMMENT '素材角色（STEM/OPTION/ANALYSIS）',
  `option_key`          varchar(16) NOT NULL DEFAULT '' COMMENT '选项键（OPTION素材必须填写；STEM/ANALYSIS素材固定为空字符串）',
  `sort_order`          int         NOT NULL COMMENT '显示顺序',
  `create_by`           bigint               DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`         datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`question_asset_id`),
  KEY                   `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_version_asset_role` (`question_version_id`, `asset_id`, `asset_role`, `option_key`),
  KEY                   `idx_asset` (`asset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='题目素材记录表';

-- 题库题目批量导入统一使用 sts_common_import_batch / sts_common_import_error（business_type=QUESTION，见 01_org_common.sql）。

-- Audit class A
CREATE TABLE `sts_question_user_state`
(
  `user_question_state_id` bigint  NOT NULL AUTO_INCREMENT COMMENT '用户题目状态ID（主键）',
  `organization_id`        bigint  NOT NULL COMMENT '所属机构ID',
  `user_id`                bigint  NOT NULL COMMENT '用户ID',
  `question_id`            bigint  NOT NULL COMMENT '题目ID',
  `is_favorite`            tinyint NOT NULL COMMENT '是否收藏（0否 1是）',
  `wrong_count`            int     NOT NULL COMMENT '答错题数',
  `correct_count`          int     NOT NULL COMMENT '答对题数',
  `last_answer_time`       datetime         DEFAULT NULL COMMENT '最近答题时间',
  `last_wrong_time`        datetime         DEFAULT NULL COMMENT '最近错误时间',
  `handled_wrong_key` varchar(100) DEFAULT NULL COMMENT '已处理错题事实标识',
  `wrong_disposition` varchar(20) DEFAULT NULL COMMENT 'DELETED或MASTERED',
  `last_exam_attempt_id`   bigint           DEFAULT NULL COMMENT '最近关联考试作答尝试ID',
  `create_by`              bigint           DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`            datetime         DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`              bigint           DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`            datetime         DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                bigint  NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                int     NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`user_question_state_id`),
  UNIQUE KEY `uk_org_user_question_deleted` (`organization_id`, `user_id`, `question_id`, `deleted`),
  KEY                      `idx_user_favorite` (`user_id`, `is_favorite`),
  KEY                      `idx_user_wrong` (`user_id`, `wrong_count`),
  KEY                      `idx_last_attempt` (`last_exam_attempt_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户题目状态表';

-- Audit class A
CREATE TABLE `sts_paper_category`
(
  `paper_category_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '试卷分类ID（主键）',
  `organization_id`   bigint       NOT NULL COMMENT '所属机构ID',
  `category_name`     varchar(100) NOT NULL COMMENT '分类名称',
  `description`       varchar(500) NOT NULL COMMENT '描述',
  `sort_order`        int          NOT NULL COMMENT '显示顺序',
  `status`            tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`         bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`       datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`         bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`       datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`           bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`           int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`paper_category_id`),
  UNIQUE KEY `uk_org_name_deleted` (`organization_id`, `category_name`, `deleted`),
  KEY                 `idx_org_sort` (`organization_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='试卷分类表';

-- Audit class A
CREATE TABLE `sts_paper_paper`
(
  `paper_id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '试卷ID（主键）',
  `organization_id`    bigint       NOT NULL COMMENT '所属机构ID',
  `paper_code`         varchar(64)  NOT NULL COMMENT '试卷编码',
  `paper_name`         varchar(100) NOT NULL COMMENT '试卷名称',
  `paper_type`         varchar(16)  NOT NULL COMMENT '试卷类型（FIXED/RANDOM/CUSTOM）',
  `paper_category_id`  bigint                DEFAULT NULL COMMENT '试卷分类ID',
  `cover_file_id`      bigint                DEFAULT NULL COMMENT '封面文件ID',
  `current_version_no` int          NOT NULL COMMENT '当前版本号',
  `archive_status`     varchar(16)  NOT NULL COMMENT '归档状态（ACTIVE/ARCHIVED）',
  `status`             tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`          bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`        datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`        datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`            int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`paper_id`),
  UNIQUE KEY `uk_org_paper_code_deleted` (`organization_id`, `paper_code`, `deleted`),
  KEY                  `idx_org_type_category` (`organization_id`, `paper_type`, `paper_category_id`, `archive_status`),
  KEY                  `idx_org_category_status` (`organization_id`, `paper_category_id`, `archive_status`, `create_time`),
  KEY                  `idx_org_status_time` (`organization_id`, `status`, `create_time`),
  KEY                  `idx_name` (`paper_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='试卷表';

-- Audit class C
CREATE TABLE `sts_paper_version`
(
  `paper_version_id`     bigint         NOT NULL AUTO_INCREMENT COMMENT '试卷版本ID（主键）',
  `organization_id`      bigint         NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `paper_id`             bigint         NOT NULL COMMENT '试卷ID',
  `version_no`           int            NOT NULL COMMENT '版本号',
  `duration_seconds`     int            NOT NULL COMMENT '时长秒数',
  `total_score`          decimal(10, 2) NOT NULL COMMENT '总分',
  `pass_ratio`           decimal(7, 4)  NOT NULL COMMENT '合格比例',
  `pass_score`           decimal(10, 2) NOT NULL COMMENT '合格分数',
  `question_count`       int            NOT NULL COMMENT '题目数量',
  `question_order_mode`  varchar(16)    NOT NULL COMMENT '题目排序模式（TYPE/IMPORT/RANDOM）',
  `multi_select_scoring` varchar(16)    NOT NULL COMMENT '多选题计分模式（ALL_OR_NOTHING/PARTIAL）',
  `draw_setting_mode`    varchar(16)    NOT NULL COMMENT '抽题设置模式（UNIFIED/PER_BANK）',
  `random_seed`          bigint       DEFAULT NULL COMMENT '随机卷抽题种子（仅RANDOM试卷，用于复现抽题结果）',
  `published_status`     varchar(16)    NOT NULL COMMENT '发布状态（DRAFT/PUBLISHED/ARCHIVED）',
  `published_by`         bigint       DEFAULT NULL COMMENT '发布人用户ID（发布后必填）',
  `published_time`       datetime     DEFAULT NULL COMMENT '发布时间（发布后必填）',
  `version_note`         varchar(500) DEFAULT NULL COMMENT '版本说明',
  `create_by`            bigint       DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`          datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`paper_version_id`),
  KEY                    `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_paper_version` (`paper_id`, `version_no`),
  KEY                    `idx_status` (`published_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='试卷版本记录表';

-- Audit class C
CREATE TABLE `sts_paper_draw_rule`
(
  `draw_rule_id`       bigint         NOT NULL AUTO_INCREMENT COMMENT '抽题规则ID（主键）',
  `organization_id`    bigint         NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `paper_version_id`   bigint         NOT NULL COMMENT '试卷版本ID',
  `question_bank_id`   bigint         NOT NULL COMMENT '题库ID',
  `question_type`      varchar(16)    NOT NULL COMMENT '题目类型（SINGLE/MULTIPLE/JUDGE/FILL/ESSAY）',
  `difficulty`         varchar(16)    NOT NULL DEFAULT 'ALL' COMMENT '难度（ALL不限/EASY简单/NORMAL普通/HARD困难/VERY_HARD很难）',
  `knowledge_point_id` bigint         NOT NULL DEFAULT 0 COMMENT '知识点ID（0表示不限知识点）',
  `draw_count`         int            NOT NULL COMMENT '抽题数量',
  `score_per_question` decimal(10, 2) NOT NULL COMMENT '单题分值',
  `bank_weight`        decimal(7, 4)           DEFAULT NULL COMMENT '题库权重',
  `sort_order`         int            NOT NULL COMMENT '显示顺序',
  `create_by`          bigint                  DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`        datetime                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`draw_rule_id`),
  KEY                  `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_version_bank_type_rule` (`paper_version_id`, `question_bank_id`, `question_type`, `difficulty`, `knowledge_point_id`),
  KEY                  `idx_version_sort` (`paper_version_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='试卷抽题规则记录表';

-- Audit class C
CREATE TABLE `sts_paper_version_question`
(
  `paper_question_id`   bigint         NOT NULL AUTO_INCREMENT COMMENT '试卷题目ID（主键）',
  `organization_id`     bigint         NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `paper_version_id`    bigint         NOT NULL COMMENT '试卷版本ID',
  `question_id`         bigint         NOT NULL COMMENT '题目ID（冗余自题目版本；同一试卷版本内按题目去重，禁止同一题目以不同版本重复入卷）',
  `question_version_id` bigint         NOT NULL COMMENT '题目版本ID',
  `question_type`       varchar(16)    NOT NULL COMMENT '题目类型（SINGLE/MULTIPLE/JUDGE/FILL/ESSAY）',
  `score`               decimal(10, 2) NOT NULL COMMENT '分数',
  `sort_order`          int            NOT NULL COMMENT '显示顺序',
  `source_bank_id`      bigint         NOT NULL COMMENT '来源题库ID',
  `create_by`           bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`         datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`paper_question_id`),
  KEY                   `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_version_question` (`paper_version_id`, `question_id`),
  KEY                   `idx_question_version` (`question_version_id`),
  KEY                   `idx_version_type_sort` (`paper_version_id`, `question_type`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='试卷版本题目记录表';

CREATE TABLE `sts_learner_practice_session` (
  `session_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `organization_id` bigint NOT NULL COMMENT '所属机构',
  `user_id` bigint NOT NULL COMMENT '学员',
  `candidate_id` bigint DEFAULT NULL COMMENT '分配练习',
  `source_question_id` bigint DEFAULT NULL COMMENT '错题来源',
  `mode` varchar(20) NOT NULL COMMENT '练习模式',
  `title` varchar(255) DEFAULT NULL COMMENT '题源名称',
  `status` varchar(20) NOT NULL COMMENT '会话状态',
  `client_token` varchar(100) NOT NULL COMMENT '启动令牌',
  `started_time` datetime NOT NULL COMMENT '开始时间',
  `submitted_time` datetime DEFAULT NULL COMMENT '交卷时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁',
  PRIMARY KEY (`session_id`),
  UNIQUE KEY uk_practice_token (organization_id,user_id,client_token,deleted),
  KEY idx_practice_user (organization_id,user_id,session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人练习记录';

CREATE TABLE `sts_learner_practice_item` (
  `item_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `organization_id` bigint NOT NULL COMMENT '所属机构',
  `session_id` bigint NOT NULL COMMENT '练习会话',
  `question_id` bigint NOT NULL COMMENT '原题目',
  `question_version_id` bigint NOT NULL COMMENT '冻结版本',
  `snapshot_json` longtext NOT NULL COMMENT '含答案的服务端快照',
  `answer_json` longtext DEFAULT NULL COMMENT '本人作答',
  `answered` tinyint NOT NULL DEFAULT 0 COMMENT '是否作答',
  `evaluation_result` varchar(30) DEFAULT NULL COMMENT '判定结果',
  `sort_order` int NOT NULL COMMENT '题序',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁',
  PRIMARY KEY (`item_id`),
  UNIQUE KEY uk_practice_item (organization_id,session_id,sort_order,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人练习记录';
