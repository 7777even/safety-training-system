-- Integration-test schema: exam
SET NAMES utf8mb4;

-- Audit class A
CREATE TABLE `sts_exam_category`
(
  `exam_category_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '考试分类ID（主键）',
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
  PRIMARY KEY (`exam_category_id`),
  UNIQUE KEY `uk_org_name_deleted` (`organization_id`, `category_name`, `deleted`),
  KEY                `idx_org_sort` (`organization_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='考试分类表';

-- Audit class A
CREATE TABLE `sts_exam_exam`
(
  `exam_id`              bigint         NOT NULL AUTO_INCREMENT COMMENT '考试ID（主键）',
  `organization_id`      bigint         NOT NULL COMMENT '所属机构ID',
  `exam_code`            varchar(64)    NOT NULL COMMENT '考试编码',
  `exam_name`            varchar(100)   NOT NULL COMMENT '考试名称',
  `exam_type`            varchar(24)    NOT NULL COMMENT '考试类型（ONLINE/OFFLINE/SPECIAL_PRACTICE/BANK_PRACTICE）',
  `exam_category_id`     bigint                  DEFAULT NULL COMMENT '考试分类ID',
  `cover_file_id`        bigint                  DEFAULT NULL COMMENT '封面文件ID',
  `paper_version_id`     bigint                  DEFAULT NULL COMMENT '试卷版本ID（线上考试、专项练习使用）',
  `question_bank_id`     bigint                  DEFAULT NULL COMMENT '题库ID（题库练习使用）',
  `plan_content_id`      bigint                  DEFAULT NULL COMMENT '来源培训计划内容ID',
  `responsible_user_id`  bigint                  DEFAULT NULL COMMENT '考试负责人用户ID',
  `open_start_time`      datetime                DEFAULT NULL COMMENT '开放开始时间',
  `open_end_time`        datetime                DEFAULT NULL COMMENT '开放结束时间',
  `duration_seconds`     int            NOT NULL COMMENT '时长秒数',
  `pass_ratio`           decimal(7, 4)  NOT NULL COMMENT '合格比例',
  `pass_score`           decimal(10, 2) NOT NULL COMMENT '合格分数',
  `credit`               decimal(10, 2) NOT NULL COMMENT '学分',
  `question_order_mode`  varchar(16)    NOT NULL COMMENT '题目排序模式（TYPE按题型/IMPORT按导入/RANDOM随机）',
  `multi_select_scoring` varchar(16)    NOT NULL COMMENT '多选题计分模式',
  `is_notice_enabled`    tinyint        NOT NULL COMMENT '是否启用须知（0否 1是）',
  `notice_content`       longtext                DEFAULT NULL COMMENT '须知内容',
  `publish_status`       varchar(16)    NOT NULL COMMENT '发布状态（DRAFT/PUBLISHED/RUNNING/ENDED/CANCELLED）',
  `published_time`       datetime                DEFAULT NULL COMMENT '发布时间',
  `locked_time`          datetime                DEFAULT NULL COMMENT '锁定时间',
  `status`               tinyint        NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `draw_rule_json`       json                    DEFAULT NULL COMMENT '题库练习抽题规则（仅BANK_PRACTICE，结构与培训侧drawRules一致）',
  `create_by`            bigint                  DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`          datetime                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`            bigint                  DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`          datetime                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`              bigint         NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`              int            NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`exam_id`),
  UNIQUE KEY `uk_org_exam_code_deleted` (`organization_id`, `exam_code`, `deleted`),
  KEY                    `idx_org_type_status` (`organization_id`, `exam_type`, `publish_status`),
  KEY                    `idx_org_category_status` (`organization_id`, `exam_category_id`, `publish_status`, `create_time`),
  KEY                    `idx_org_status_time` (`organization_id`, `publish_status`, `create_time`),
  KEY                    `idx_open_time` (`open_start_time`, `open_end_time`),
  KEY                    `idx_paper_version` (`paper_version_id`),
  KEY                    `idx_question_bank` (`question_bank_id`),
  UNIQUE KEY `uk_plan_content_deleted` (`plan_content_id`, `deleted`),
  KEY                    `idx_responsible_user` (`responsible_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='考试表';

-- Audit class A
CREATE TABLE `sts_exam_policy`
(
  `exam_policy_id`                   bigint         NOT NULL AUTO_INCREMENT COMMENT '考试策略ID（主键）',
  `organization_id`                  bigint         NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `exam_id`                          bigint         NOT NULL COMMENT '考试ID',
  `is_anticheat_enabled`             tinyint        NOT NULL DEFAULT 0 COMMENT '是否启用考试防作弊事件审计（0否 1是）',
  `is_watermark_enabled`             tinyint        NOT NULL COMMENT '是否启用页面水印（0否 1是）',
  `is_pc_allowed`                    tinyint        NOT NULL COMMENT '是否允许PC端答题（0否 1是）',
  `is_app_allowed`                   tinyint        NOT NULL COMMENT '是否允许APP端答题（0否 1是）',
  `is_h5_allowed`                    tinyint        NOT NULL COMMENT '是否允许H5端答题（0否 1是）',
  `switch_limit`                     int DEFAULT NULL COMMENT '【暂时隐藏】原用于：切屏次数阈值；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `is_split_screen_force_submit`     tinyint        NOT NULL COMMENT '【暂时隐藏】原用于：检测到分屏时强制交卷；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `is_copy_refresh_devtool_disabled` tinyint        NOT NULL COMMENT '【暂时隐藏】原用于：禁止选择复制、刷新及开发者工具；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `inactivity_seconds`               int DEFAULT NULL COMMENT '【暂时隐藏】原用于：无操作判定秒数；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `inactivity_countdown_seconds`     int DEFAULT NULL COMMENT '【暂时隐藏】原用于：无操作强制交卷倒计时秒数；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `minimum_answer_seconds`           int DEFAULT NULL COMMENT '【暂时隐藏】原用于：最短作答秒数；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `is_retake_enabled`                tinyint        NOT NULL COMMENT '是否启用补考（0否 1是）',
  `retake_scope`                     varchar(24)             DEFAULT NULL COMMENT '补考范围（FAILED/FAILED_OR_ABSENT/ABSENT）',
  `retake_start_time`                datetime                DEFAULT NULL COMMENT '补考开始时间',
  `retake_end_time`                  datetime                DEFAULT NULL COMMENT '补考结束时间',
  `retake_max_count`                 int            NOT NULL COMMENT '补考最大数量',
  `retake_score_mode`                varchar(16)    NOT NULL COMMENT '补考成绩取值模式（HIGHEST/LATEST）',
  `is_retake_record_visible`         tinyint        NOT NULL COMMENT '是否允许查看补考记录（0否 1是）',
  `pass_point`                       decimal(10, 2) NOT NULL COMMENT '【暂时隐藏】原用于：合格积分；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `fail_point`                       decimal(10, 2) NOT NULL COMMENT '【暂时隐藏】原用于：不合格积分；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `absent_point`                     decimal(10, 2) NOT NULL COMMENT '【暂时隐藏】原用于：缺考积分；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `late_forbid_seconds`              int            NOT NULL COMMENT '【暂时隐藏】原用于：迟到禁考秒数；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `answer_detail_mode`               varchar(24)    NOT NULL COMMENT '答题详情查看模式（SUBMITTED交卷后/PUBLISHED成绩发布后/NEVER不可查看）',
  `is_analysis_visible`              tinyint        NOT NULL COMMENT '是否显示答案解析（0否 1是）',
  `is_score_visible`                 tinyint        NOT NULL COMMENT '是否显示成绩（0否 1是）',
  `is_ranking_enabled`               tinyint        NOT NULL COMMENT '【暂时隐藏】原用于：考试排行榜展示；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `create_by`                        bigint                  DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`                      datetime                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`                        bigint                  DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`                      datetime                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                          bigint         NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                          int            NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`exam_policy_id`),
  KEY                                `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_exam_deleted` (`exam_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='考试策略表';

-- Audit class A
CREATE TABLE `sts_assessment_execution`
(
  `assessment_execution_id`       bigint         NOT NULL AUTO_INCREMENT COMMENT '考核执行ID（主键）',
  `organization_id`               bigint         NOT NULL COMMENT '所属机构ID',
  `source_type`                   varchar(32)    NOT NULL COMMENT '来源类型（STANDALONE_EXAM/STANDALONE_PRACTICE/COURSE_QUIZ/COURSE_BANK_PRACTICE）',
  `source_business_id`            bigint         NOT NULL COMMENT '稳定来源业务ID',
  `exam_id`                       bigint                  DEFAULT NULL COMMENT '独立考试任务ID（课程来源为空）',
  `course_offering_id`            bigint                  DEFAULT NULL COMMENT '课程开课ID（独立来源为空）',
  `course_version_id`             bigint                  DEFAULT NULL COMMENT '冻结课程版本ID（独立来源为空）',
  `course_item_id`                bigint                  DEFAULT NULL COMMENT '课程内容项ID（独立来源为空）',
  `assessment_kind`               varchar(24)    NOT NULL COMMENT '考核类型（FORMAL_EXAM/QUIZ/BANK_PRACTICE/SPECIAL_PRACTICE）',
  `resource_type`                 varchar(24)    NOT NULL COMMENT '资源类型（PAPER_VERSION/QUESTION_BANK/OFFLINE_RESULT）',
  `resource_id`                   bigint         NOT NULL COMMENT '冻结资源ID',
  `snapshot_schema_version` int      NOT NULL COMMENT '完整执行快照结构版本',
  `snapshot_json`           json     NOT NULL COMMENT '来源、资源和执行规则的完整不可变快照',
  `snapshot_hash`           char(64) NOT NULL COMMENT '规范化完整执行快照SHA-256摘要',
  `execution_status`              varchar(16)    NOT NULL COMMENT '执行状态（READY/CLOSED/VOID）',
  `create_by`                     bigint                  DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`                   datetime                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`                     bigint                  DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`                   datetime                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                       bigint         NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                       int            NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`assessment_execution_id`),
  UNIQUE KEY `uk_execution_exam_deleted` (`exam_id`, `deleted`),
  UNIQUE KEY `uk_execution_course_item_deleted` (`course_offering_id`, `course_version_id`, `course_item_id`, `deleted`),
  KEY                             `idx_source_business` (`source_type`, `source_business_id`),
  KEY                             `idx_org_status` (`organization_id`, `execution_status`),
  KEY                             `idx_resource` (`resource_type`, `resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='统一考核执行表';

-- Audit class C
CREATE TABLE `sts_assessment_execution_question_pool`
(
  `execution_question_pool_id` bigint      NOT NULL AUTO_INCREMENT COMMENT '执行冻结题池行ID',
  `organization_id`            bigint      NOT NULL COMMENT '所属机构ID',
  `assessment_execution_id`    bigint      NOT NULL COMMENT '考核执行ID',
  `source_plan_content_id`     bigint      NOT NULL COMMENT '来源计划内容ID',
  `question_bank_id`           bigint      NOT NULL COMMENT '题库ID',
  `question_id`                bigint      NOT NULL COMMENT '题目ID',
  `question_version_id`        bigint      NOT NULL COMMENT '冻结题目版本ID',
  `question_type`              varchar(16) NOT NULL COMMENT '题目类型',
  `difficulty`                 varchar(16) DEFAULT NULL COMMENT '难度',
  `knowledge_point_ids_json`   json        NOT NULL COMMENT '知识点ID快照',
  `evaluation_mode`            varchar(16) NOT NULL COMMENT '判定模式（AUTO/RECORD_ONLY）',
  `candidate_order`            int         NOT NULL COMMENT '候选稳定顺序',
  `pool_hash`                  char(64)    NOT NULL COMMENT '候选池SHA-256指纹',
  `create_by`                  bigint      DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`                datetime    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`execution_question_pool_id`),
  UNIQUE KEY `uk_execution_question` (`assessment_execution_id`, `question_id`),
  KEY                          `idx_execution_order` (`assessment_execution_id`, `candidate_order`),
  KEY                          `idx_question_version` (`question_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='考核执行冻结题池';

-- Audit class A
CREATE TABLE `sts_exam_candidate`
(
  `exam_candidate_id`               bigint      NOT NULL AUTO_INCREMENT COMMENT '考试人员ID（主键）',
  `organization_id`                 bigint      NOT NULL COMMENT '所属机构ID',
  `assessment_execution_id` bigint NOT NULL COMMENT '考核执行ID',
  `user_id`                         bigint      NOT NULL COMMENT '用户ID',
  `course_offering_id`              bigint      NOT NULL DEFAULT 0 COMMENT '来源课程开课ID（仅COURSE来源，非课程为0）',
  `course_item_id`                  bigint      NOT NULL DEFAULT 0 COMMENT '来源课程内容项ID（仅COURSE来源，非课程为0）',
  `course_enrollment_id`            bigint      NOT NULL DEFAULT 0 COMMENT '来源课程报名ID（仅COURSE来源，非课程为0）',
  `training_participant_id`         bigint               DEFAULT NULL COMMENT '来源培训参训人员ID',
  `assignment_source`               varchar(16) NOT NULL COMMENT '分配来源（ASSIGN/IMPORT/COURSE/TRAINING）',
  `source_business_id`              bigint      NOT NULL DEFAULT 0 COMMENT '分配来源业务ID（仅用于追溯来源，不参与考试人员身份）',
  `candidate_profile_snapshot_json` json        NOT NULL COMMENT '考试分配时账号、姓名、机构、部门路径、工号、岗位和职务快照JSON',
  `profile_snapshot_time`           datetime    NOT NULL COMMENT '考试人员档案快照时间',
  `notice_confirmed_time`           datetime             DEFAULT NULL COMMENT '须知确认时间',
  `candidate_status` varchar(24) NOT NULL COMMENT '人员状态（NOT_STARTED/IN_PROGRESS/SUBMITTED/PENDING_REVIEW/PENDING_PUBLISH/PASSED/FAILED/COMPLETED/ABSENT）',
  `attempt_count`                   int         NOT NULL COMMENT '作答次数',
  `best_score`                      decimal(10, 2)       DEFAULT NULL COMMENT '最佳分数',
  `final_score`                     decimal(10, 2)       DEFAULT NULL COMMENT '最终分数',
  `final_attempt_id`                bigint               DEFAULT NULL COMMENT '最终作答尝试ID',
  `result_published_time`           datetime             DEFAULT NULL COMMENT '结果发布时间',
  `result_effective_time`   datetime DEFAULT NULL COMMENT '课程考核结果自动生效时间',
  `create_by`                       bigint               DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`                     datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`                       bigint               DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`                     datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                         bigint      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                         int         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`exam_candidate_id`),
  UNIQUE KEY `uk_execution_user_enrollment_item_deleted` (`assessment_execution_id`, `user_id`, `course_enrollment_id`, `course_item_id`, `deleted`),
  KEY                               `idx_user_status` (`user_id`, `candidate_status`),
  KEY                       `idx_execution_status` (`assessment_execution_id`, `candidate_status`, `final_score`),
  KEY                               `idx_org_status_time` (`organization_id`, `candidate_status`, `result_published_time`),
  KEY                               `idx_course_offering_item` (`course_offering_id`, `course_item_id`),
  KEY                               `idx_training_participant` (`training_participant_id`),
  KEY                       `idx_assignment_source` (`assignment_source`, `source_business_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='考试人员表';

-- Audit class B
CREATE TABLE `sts_exam_reviewer`
(
  `exam_reviewer_id`        bigint      NOT NULL AUTO_INCREMENT COMMENT '考核批阅人ID（主键）',
  `organization_id`         bigint      NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `assessment_execution_id` bigint      NOT NULL COMMENT '考核执行ID',
  `reviewer_user_id`        bigint      NOT NULL COMMENT '批阅人用户ID',
  `assignment_source`       varchar(16) NOT NULL COMMENT '批阅人分配来源（ROLE业务角色/TAG标签/POSITION岗位/USER用户）',
  `create_by`               bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`             datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`exam_reviewer_id`),
  KEY                 `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_execution_reviewer` (`assessment_execution_id`, `reviewer_user_id`),
  KEY                       `idx_reviewer_execution` (`reviewer_user_id`, `assessment_execution_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='考核执行批阅人关联表';

-- Audit class A
CREATE TABLE `sts_exam_attempt`
(
  `exam_attempt_id`               bigint         NOT NULL AUTO_INCREMENT COMMENT '考试作答尝试ID（主键）',
  `organization_id`               bigint         NOT NULL COMMENT '所属机构ID',
  `assessment_execution_id` bigint      NOT NULL COMMENT '考核执行ID',
  `source_type`             varchar(32) NOT NULL COMMENT '来源类型快照',
  `assessment_kind`         varchar(24) NOT NULL COMMENT '考核类型快照',
  `exam_candidate_id`             bigint         NOT NULL COMMENT '考试人员ID',
  `user_id`                       bigint         NOT NULL COMMENT '用户ID',
  `paper_version_id`              bigint                  DEFAULT NULL COMMENT '正式试卷版本ID（题库练习和线下结果型尝试允许NULL）',
  `paper_source_type`             varchar(24)    NOT NULL COMMENT '执行试卷来源（PAPER_VERSION/QUESTION_BANK/OFFLINE_RESULT）',
  `paper_source_id`               bigint         NOT NULL DEFAULT 0 COMMENT '执行试卷来源ID（正式试卷版本或题库ID；线下结果型尝试固定为0）',
  `total_score_snapshot` decimal(10, 2) DEFAULT NULL COMMENT '本次尝试总分快照；不评分练习为空',
  `pass_score_snapshot`  decimal(10, 2) DEFAULT NULL COMMENT '本次尝试合格分快照；不评分练习为空',
  `duration_seconds_snapshot`     int            NOT NULL COMMENT '本次尝试答题时长秒数快照',
  `question_order_mode_snapshot`  varchar(16)    NOT NULL COMMENT '本次尝试题目排序模式快照（TYPE/IMPORT/RANDOM/NOT_APPLICABLE）',
  `multi_select_scoring_snapshot` varchar(16)    NOT NULL COMMENT '本次尝试多选题计分模式快照（ALL_OR_NOTHING/PARTIAL/NOT_APPLICABLE）',
  `attempt_no`                    int            NOT NULL COMMENT '作答次数序号',
  `attempt_type`                  varchar(16)    NOT NULL COMMENT '作答尝试类型（FIRST/RETAKE/PRACTICE/MOCK）',
  `attempt_status`       varchar(24) NOT NULL COMMENT '作答尝试状态（CREATED/ANSWERING/SUBMITTED/PENDING_REVIEW/PENDING_PUBLISH/PUBLISHED/RESULT_EFFECTIVE/VOID）',
  `started_time`                  datetime                DEFAULT NULL COMMENT '开始时间',
  `deadline_time`                 datetime                DEFAULT NULL COMMENT '截止时间',
  `submitted_time`                datetime                DEFAULT NULL COMMENT '提交时间',
  `used_seconds`                  int            NOT NULL COMMENT '实际答题秒数',
  `objective_score`      decimal(10, 2) DEFAULT NULL COMMENT '客观题分数；不评分练习为空',
  `subjective_score`     decimal(10, 2) DEFAULT NULL COMMENT '主观题分数；不评分练习为空',
  `final_score`          decimal(10, 2) DEFAULT NULL COMMENT '最终分数；不评分练习为空',
  `is_passed`                     tinyint                 DEFAULT NULL COMMENT '是否合格（0否 1是，NULL未判定）',
  `correct_count`                 int            NOT NULL COMMENT '答对题数',
  `wrong_count`                   int            NOT NULL COMMENT '答错题数',
  `evaluated_count`      int            DEFAULT NULL COMMENT '可判断题数；历史考试为空',
  `unassessed_count`     int            DEFAULT NULL COMMENT '不判断题数；历史考试为空',
  `accuracy_rate`        decimal(7, 2)  DEFAULT NULL COMMENT '可判断题正确率百分比',
  `submit_reason`                 varchar(32)             DEFAULT NULL COMMENT '提交原因',
  `platform`                      varchar(32)             DEFAULT NULL COMMENT '平台',
  `operating_system`              varchar(64)             DEFAULT NULL COMMENT '操作系统',
  `browser`                       varchar(64)             DEFAULT NULL COMMENT '浏览器',
  `client_ip`                     varchar(128)            DEFAULT NULL COMMENT '客户端IP地址',
  `review_completed_time`         datetime                DEFAULT NULL COMMENT '批阅完成时间',
  `score_published_time`          datetime                DEFAULT NULL COMMENT '分数发布时间',
  `result_effective_time`   datetime DEFAULT NULL COMMENT '课程考核结果自动生效时间',
  `client_attempt_token`          varchar(128)   NOT NULL COMMENT '客户端作答幂等令牌（同一考试人员内幂等唯一）',
  `draw_seed`            bigint         DEFAULT NULL COMMENT '题库随机抽取种子',
  `create_by`                     bigint                  DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`                   datetime                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`                     bigint                  DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`                   datetime                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                       bigint         NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                       int            NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`exam_attempt_id`),
  UNIQUE KEY `uk_candidate_attempt_deleted` (`exam_candidate_id`, `attempt_no`, `deleted`),
  UNIQUE KEY `uk_candidate_token_deleted` (`exam_candidate_id`, `client_attempt_token`, `deleted`),
  KEY                       `idx_execution_status` (`assessment_execution_id`, `attempt_status`, `submitted_time`),
  KEY                             `idx_user_time` (`user_id`, `started_time`),
  KEY                             `idx_org_status_time` (`organization_id`, `attempt_status`, `submitted_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='考试作答尝试表';

-- Audit class C
CREATE TABLE `sts_exam_attempt_question`
(
  `attempt_question_id`          bigint         NOT NULL AUTO_INCREMENT COMMENT '作答尝试题目ID（主键）',
  `organization_id`              bigint         NOT NULL COMMENT '所属机构ID',
  `exam_attempt_id`              bigint         NOT NULL COMMENT '考试作答尝试ID',
  `question_id`                  bigint         NOT NULL COMMENT '题目ID',
  `question_version_id`          bigint         NOT NULL COMMENT '题目版本ID',
  `question_type`                varchar(16)    NOT NULL COMMENT '题目类型（SINGLE/MULTIPLE/JUDGE/FILL/ESSAY）',
  `stem_snapshot`                longtext       NOT NULL COMMENT '题干快照',
  `analysis_snapshot`            longtext DEFAULT NULL COMMENT '解析快照',
  `grading_mode`                 varchar(16)    NOT NULL COMMENT '评分模式（AUTO自动/MANUAL人工/KEYWORD关键词辅助）',
  `score`           decimal(10, 2) DEFAULT NULL COMMENT '分数；不评分练习为空',
  `correct_answer_snapshot_json` json     DEFAULT NULL COMMENT '正确答案快照JSON',
  `sort_order`                   int            NOT NULL COMMENT '显示顺序',
  `source_bank_id`               bigint         NOT NULL COMMENT '来源题库ID',
  `draw_rule_index` int            DEFAULT NULL COMMENT '抽题规则稳定序号',
  `evaluation_mode` varchar(16)    DEFAULT NULL COMMENT '判定模式（AUTO/RECORD_ONLY；历史为空）',
  `knowledge_point_ids_snapshot_json` json DEFAULT NULL COMMENT '作答冻结知识点ID数组，历史NULL未记录',
  `create_by`                    bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`                  datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`attempt_question_id`),
  UNIQUE KEY `uk_attempt_question` (`exam_attempt_id`, `question_id`),
  KEY                            `idx_attempt_type_sort` (`exam_attempt_id`, `question_type`, `sort_order`),
  KEY                            `idx_question_version` (`question_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='考试作答尝试题目记录表';

-- Audit class C
CREATE TABLE `sts_exam_attempt_option`
(
  `attempt_option_id`       bigint      NOT NULL AUTO_INCREMENT COMMENT '作答尝试选项ID（主键）',
  `organization_id`         bigint      NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `attempt_question_id`     bigint      NOT NULL COMMENT '作答尝试题目ID',
  `option_key`              varchar(16) NOT NULL COMMENT '选项键',
  `option_content_snapshot` longtext    NOT NULL COMMENT '选项内容快照',
  `is_correct_snapshot`     tinyint     NOT NULL COMMENT '快照答案是否正确（0否 1是）',
  `sort_order`              int         NOT NULL COMMENT '显示顺序',
  `create_by`               bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`             datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`attempt_option_id`),
  KEY                       `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_attempt_question_key` (`attempt_question_id`, `option_key`),
  KEY                       `idx_question_sort` (`attempt_question_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='考试作答尝试选项记录表';

-- Audit class A
CREATE TABLE `sts_exam_answer`
(
  `exam_answer_id`      bigint         NOT NULL AUTO_INCREMENT COMMENT '考试答案ID（主键）',
  `organization_id`     bigint         NOT NULL COMMENT '所属机构ID',
  `exam_attempt_id`     bigint         NOT NULL COMMENT '考试作答尝试ID',
  `attempt_question_id` bigint         NOT NULL COMMENT '作答尝试题目ID',
  `answer_text`         longtext                DEFAULT NULL COMMENT '答案文本',
  `is_answered`         tinyint        NOT NULL COMMENT '是否已作答（0否 1是）',
  `auto_score`          decimal(10, 2)          DEFAULT NULL COMMENT '自动分数',
  `manual_score`        decimal(10, 2)          DEFAULT NULL COMMENT '手工分数',
  `final_score`       decimal(10, 2) DEFAULT NULL COMMENT '最终分数；不评分练习为空',
  `is_correct`          tinyint                 DEFAULT NULL COMMENT '是否正确（0否 1是，NULL未判定）',
  `grading_status`      varchar(16)    NOT NULL COMMENT '评分状态（UNANSWERED/AUTO_GRADED/PENDING_MANUAL/MANUAL_GRADED）',
  `evaluation_result` varchar(20)    DEFAULT NULL COMMENT '练习判定（AUTO_CORRECT/AUTO_WRONG/NOT_EVALUATED）',
  `answered_time`       datetime                DEFAULT NULL COMMENT '作答时间',
  `last_saved_time`     datetime                DEFAULT NULL COMMENT '最近保存时间',
  `create_by`           bigint                  DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`         datetime                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`           bigint                  DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`         datetime                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`             bigint         NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`             int            NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`exam_answer_id`),
  UNIQUE KEY `uk_attempt_question_deleted` (`exam_attempt_id`, `attempt_question_id`, `deleted`),
  KEY                   `idx_attempt_grading` (`exam_attempt_id`, `grading_status`),
  KEY                   `idx_org_grading_time` (`organization_id`, `grading_status`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='考试答案表';

-- Audit class A
CREATE TABLE `sts_exam_answer_item`
(
  `answer_item_id`  bigint      NOT NULL AUTO_INCREMENT COMMENT '答案明细ID（主键）',
  `organization_id` bigint      NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `exam_answer_id`  bigint      NOT NULL COMMENT '考试答案ID',
  `item_type`       varchar(16) NOT NULL COMMENT '答案项类型（OPTION/BLANK）',
  `item_key`        varchar(32) NOT NULL COMMENT '答案项键',
  `item_value`      longtext    NOT NULL COMMENT '答案项内容',
  `sort_order`      int         NOT NULL COMMENT '显示顺序',
  `create_by`       bigint               DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`     datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       bigint               DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`     datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         bigint      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`         int         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`answer_item_id`),
  KEY               `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_answer_type_key_deleted` (`exam_answer_id`, `item_type`, `item_key`, `deleted`),
  KEY               `idx_answer_sort` (`exam_answer_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='考试答案明细表';

-- Audit class B
CREATE TABLE `sts_exam_answer_asset`
(
  `answer_asset_id` bigint NOT NULL AUTO_INCREMENT COMMENT '答案素材ID（主键）',
  `organization_id` bigint NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `exam_answer_id`  bigint NOT NULL COMMENT '考试答案ID',
  `file_info_id`    bigint NOT NULL COMMENT '文件信息ID',
  `sort_order`      int    NOT NULL COMMENT '显示顺序',
  `create_by`       bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`     datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`answer_asset_id`),
  KEY               `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_answer_file` (`exam_answer_id`, `file_info_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='考试答案素材关联表';

-- Audit class C
CREATE TABLE `sts_exam_review_record`
(
  `review_record_id` bigint         NOT NULL AUTO_INCREMENT COMMENT '批阅记录ID（主键）',
  `organization_id`  bigint         NOT NULL COMMENT '所属机构ID',
  `exam_attempt_id`  bigint         NOT NULL COMMENT '考试作答尝试ID',
  `exam_answer_id`   bigint         NOT NULL COMMENT '考试答案ID',
  `reviewer_user_id` bigint         NOT NULL COMMENT '批阅人用户ID',
  `before_score`     decimal(10, 2) DEFAULT NULL COMMENT '变更前分数',
  `after_score`      decimal(10, 2) NOT NULL COMMENT '变更后分数',
  `review_comment`   varchar(2000)  DEFAULT NULL COMMENT '批阅评语',
  `review_action`    varchar(16)    NOT NULL COMMENT '批阅操作（SCORE/RESCORE）',
  `reviewed_time`    datetime       NOT NULL COMMENT '批阅时间',
  `create_by`        bigint         DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`      datetime       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`review_record_id`),
  KEY                `idx_attempt_time` (`exam_attempt_id`, `reviewed_time`),
  KEY                `idx_reviewer_time` (`reviewer_user_id`, `reviewed_time`),
  KEY                `idx_org_reviewed_time` (`organization_id`, `reviewed_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='考试批阅记录表';

-- Audit class C
CREATE TABLE `sts_exam_score_publish_log`
(
  `score_publish_log_id` bigint         NOT NULL AUTO_INCREMENT COMMENT '分数发布日志ID（主键）',
  `organization_id`      bigint         NOT NULL COMMENT '所属机构ID',
  `exam_id`              bigint         NOT NULL COMMENT '考试ID',
  `exam_attempt_id`      bigint         NOT NULL COMMENT '考试作答尝试ID',
  `published_by`         bigint         NOT NULL COMMENT '发布人用户ID',
  `published_time`       datetime       NOT NULL COMMENT '发布时间',
  `publish_type`         varchar(16)    NOT NULL COMMENT '发布类型（SINGLE/BATCH）',
  `score_snapshot`       decimal(10, 2) NOT NULL COMMENT '分数快照',
  `result_snapshot`      varchar(16)    NOT NULL COMMENT '发布结果快照',
  `create_by`            bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`          datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`score_publish_log_id`),
  KEY                    `idx_exam_time` (`exam_id`, `published_time`),
  KEY                    `idx_attempt` (`exam_attempt_id`),
  KEY                    `idx_org_publish_time` (`organization_id`, `published_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='考试成绩发布日志表';

-- Audit class C
CREATE TABLE `sts_exam_anticheat_event`
(
  `anticheat_event_id`  bigint      NOT NULL AUTO_INCREMENT COMMENT '防作弊事件ID（主键）',
  `organization_id`     bigint      NOT NULL COMMENT '所属机构ID',
  `exam_attempt_id`     bigint      NOT NULL COMMENT '考试作答尝试ID',
  `event_type`          varchar(32) NOT NULL COMMENT '事件类型（PAGE_SWITCH/SPLIT_SCREEN/INACTIVITY/COPY/REFRESH/DEVTOOL/TIMEOUT）',
  `event_time`          datetime    NOT NULL COMMENT '事件时间',
  `event_count`         int         NOT NULL COMMENT '事件累计次数',
  `action_taken`        varchar(32) NOT NULL COMMENT '已采取措施（RECORD/WARN/FORCE_SUBMIT）',
  `evidence_file_id`    bigint   DEFAULT NULL COMMENT '防作弊证据文件ID',
  `client_payload_json` json     DEFAULT NULL COMMENT '客户端事件原始数据JSON',
  `create_by`           bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`         datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`anticheat_event_id`),
  KEY                   `idx_attempt_time` (`exam_attempt_id`, `event_time`),
  KEY                   `idx_event_type` (`event_type`, `event_time`),
  KEY                   `idx_org_event_time` (`organization_id`, `event_type`, `event_time`),
  KEY                   `idx_evidence_file` (`evidence_file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='考试防作弊事件记录表';

-- Audit class C
CREATE TABLE `sts_exam_practice_reset`
(
  `practice_reset_id` bigint      NOT NULL AUTO_INCREMENT COMMENT '练习重置ID（主键）',
  `organization_id`   bigint      NOT NULL COMMENT '所属机构ID',
  `exam_id`           bigint      NOT NULL COMMENT '考试ID',
  `exam_candidate_id` bigint      NOT NULL COMMENT '考试人员ID（练习重置仅作用于该执行上下文）',
  `user_id`           bigint      NOT NULL COMMENT '用户ID',
  `reset_scope`       varchar(16) NOT NULL COMMENT '重置范围（ALL/WRONG/UNFINISHED）',
  `reset_time`        datetime    NOT NULL COMMENT '重置时间',
  `reason`            varchar(500) DEFAULT NULL COMMENT '原因',
  `create_by`         bigint       DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`       datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`practice_reset_id`),
  KEY                 `idx_candidate_time` (`exam_candidate_id`, `reset_time`),
  KEY                 `idx_exam_user_time` (`exam_id`, `user_id`, `reset_time`),
  KEY                 `idx_org_reset_time` (`organization_id`, `reset_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='考试练习重置记录表';
