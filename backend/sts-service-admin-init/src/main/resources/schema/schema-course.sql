-- =================================================================================
-- UNISENCE (统一互联) 平台课程业务模块数据库表结构 (MySQL DDL 脚本)
--
-- 最新完整 DDL；数据库变化必须同步更新本文件、backend/sts-service-admin/src/main/resources/db/migration/ 中的 Admin 增量和 init schema version。
-- 本文件供 admin-init 首次建库，并由构建拷入 sts-service-admin 集成测试 classpath，供 Testcontainers 空库建表。
-- 仅含 sts_* 课程业务表；us_sys_* 系统表见 schema-system.sql。
-- =================================================================================

SET NAMES utf8mb4;
SET
FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 1. 课程分类表 (sts_course_category)
-- ----------------------------
CREATE TABLE `sts_course_category`
(
  `course_category_id` bigint        NOT NULL AUTO_INCREMENT COMMENT '课程分类ID（主键）',
  `organization_id`    bigint        NOT NULL COMMENT '所属机构ID',
  `parent_id`          bigint        NOT NULL DEFAULT 0 COMMENT '父级ID',
  `ancestors`          varchar(1000) NOT NULL COMMENT '祖级路径',
  `category_name`      varchar(100)  NOT NULL COMMENT '分类名称',
  `description`        varchar(500)  NOT NULL COMMENT '描述',
  `sort_order`         int           NOT NULL COMMENT '显示顺序',
  `status`             tinyint       NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`          bigint                 DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`        datetime               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          bigint                 DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`        datetime               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            bigint        NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`            int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`course_category_id`),
  UNIQUE KEY `uk_org_parent_name_deleted` (`organization_id`, `parent_id`, `category_name`, `deleted`),
  KEY                  `idx_parent_sort` (`parent_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程分类表';

-- ----------------------------
-- 3. 机构统一课程设置表 (sts_course_setting，当前暂时隐藏)
-- ----------------------------
CREATE TABLE `sts_course_setting`
(
  `course_setting_id`                 bigint        NOT NULL AUTO_INCREMENT COMMENT '机构课程设置ID（主键）',
  `organization_id`                   bigint        NOT NULL COMMENT '所属机构ID',
  `default_document_page_seconds`     int           NOT NULL COMMENT '【暂时隐藏】原用于：文档每页默认学习秒数；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `default_rich_text_seconds`         int           NOT NULL COMMENT '【暂时隐藏】原用于：图文课件默认学习秒数；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `default_link_live_seconds`         int           NOT NULL COMMENT '【暂时隐藏】原用于：链接或直播课件默认学习秒数；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `default_media_completion_ratio`    decimal(7, 4) NOT NULL COMMENT '【暂时隐藏】原用于：音视频默认完成比例；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `is_category_permission_enabled`    tinyint       NOT NULL COMMENT '【暂时隐藏】原用于：课程分类权限开关；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `is_resume_prompt_enabled`          tinyint       NOT NULL COMMENT '【暂时隐藏】原用于：断点续播提示开关；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `is_last_page_required`             tinyint       NOT NULL COMMENT '【暂时隐藏】原用于：强制学习文档最后一页；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `is_fast_forward_globally_disabled` tinyint       NOT NULL COMMENT '【暂时隐藏】原用于：全局禁止拖动快进；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `is_screen_leave_time_paused`       tinyint       NOT NULL COMMENT '【暂时隐藏】原用于：离开学习页面时暂停计时；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `is_transcode_notification_enabled` tinyint       NOT NULL COMMENT '【暂时隐藏】原用于：向发布人发送转码完成通知；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  `status`                            tinyint       NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`                         bigint                 DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`                       datetime               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`                         bigint                 DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`                       datetime               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                           bigint        NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                           int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`course_setting_id`),
  UNIQUE KEY `uk_org_deleted` (`organization_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='【暂时隐藏】原用于：机构统一课程设置；当前中山50最小闭环不启用，不得作为当前页面、API或业务规则依据；后续启用须新建并确认 OpenSpec Change';

-- ----------------------------
-- 4. 课程表 (sts_course_course) 与课程章节表 (sts_course_chapter)
-- 2026-10-10 已确认：章节启用同课程多层父关系。
-- ----------------------------
CREATE TABLE `sts_course_course`
(
  `course_id`                    bigint         NOT NULL AUTO_INCREMENT COMMENT '课程ID（主键）',
  `organization_id`              bigint         NOT NULL COMMENT '所属机构ID',
  `course_code`                  varchar(64)    NOT NULL COMMENT '课程编码',
  `teacher_user_id` bigint DEFAULT NULL COMMENT '主讲教师用户ID',
  `course_name`                  varchar(100)   NOT NULL COMMENT '课程名称',
  `current_version_id`           bigint                  DEFAULT NULL COMMENT '当前发布版本ID',
  `cover_file_id`                bigint                  DEFAULT NULL COMMENT '封面文件ID',
  `introduction`                 longtext                DEFAULT NULL COMMENT '简介',
  `teaching_objective`           longtext                DEFAULT NULL COMMENT '教学目标',
  `syllabus`                     longtext                DEFAULT NULL COMMENT '教学大纲',
  `assessment_method`            longtext                DEFAULT NULL COMMENT '考核方式',
  `content_mode`                 varchar(16)    NOT NULL COMMENT '内容模式（SINGLE/MULTI_CHAPTER）',
  `credit`                       decimal(10, 2) NOT NULL COMMENT '学分',
  `point_mode`                   varchar(16)    NOT NULL COMMENT '积分奖励模式（NONE/PER_ITEM/COURSE）',
  `point_value`                  decimal(10, 2) NOT NULL COMMENT '积分值',
  `source_download_mode`         varchar(16)    NOT NULL COMMENT '源文件下载模式（NONE/ALL/PARTIAL）',
  `is_speed_play_allowed`        tinyint        NOT NULL COMMENT '是否允许倍速播放（0否 1是）',
  `is_study_anticheat_enabled`   tinyint        NOT NULL COMMENT '是否启用学习防作弊（0否 1是）',
  `is_study_description_enabled` tinyint        NOT NULL COMMENT '是否显示学时说明（0否 1是）',
  `is_watermark_enabled`         tinyint        NOT NULL COMMENT '是否启用水印（0否 1是）',
  `publish_status`               varchar(16)    NOT NULL COMMENT '发布状态（DRAFT/PUBLISHED/OFFLINE；本 Change 恒为 DRAFT）',
  `published_time`               datetime                DEFAULT NULL COMMENT '发布时间',
  `status`                       tinyint        NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`                    bigint                  DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`                  datetime                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`                    bigint                  DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`                  datetime                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                      bigint         NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                      int            NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`course_id`),
  UNIQUE KEY `uk_org_course_code_deleted` (`organization_id`, `course_code`, `deleted`),
  KEY                            `idx_org_publish` (`organization_id`, `publish_status`, `status`),
  KEY                            `idx_current_version` (`current_version_id`),
  KEY                            `idx_name` (`course_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程表';

CREATE TABLE `sts_course_chapter`
(
  `chapter_id`      bigint        NOT NULL AUTO_INCREMENT COMMENT '章节ID（主键）',
  `organization_id` bigint        NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `course_id`       bigint        NOT NULL COMMENT '课程ID',
  `parent_id`       bigint        NOT NULL DEFAULT 0 COMMENT '父章节ID（0为根级）',
  `ancestors`       longtext NOT NULL COMMENT '祖级路径',
  `chapter_name`    varchar(100)  NOT NULL COMMENT '章节名称',
  `sort_order`      int           NOT NULL COMMENT '显示顺序',
  `status`          tinyint       NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`       bigint                 DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`     datetime               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       bigint                 DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`     datetime               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         bigint        NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`         int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`chapter_id`),
  KEY               `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_course_parent_name_deleted` (`course_id`, `parent_id`, `chapter_name`, `deleted`),
  KEY               `idx_course_parent_sort` (`course_id`, `parent_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程章节表';

-- ----------------------------
-- 5. 课程与分类关联表 (sts_course_course_category)
-- ----------------------------
CREATE TABLE `sts_course_course_category`
(
  `course_category_rel_id` bigint NOT NULL AUTO_INCREMENT COMMENT '课程分类关联ID（主键）',
  `organization_id`        bigint NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `course_id`              bigint NOT NULL COMMENT '课程ID',
  `course_category_id`     bigint NOT NULL COMMENT '课程分类ID',
  `create_by`              bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`            datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`course_category_rel_id`),
  KEY                      `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_course_category` (`course_id`, `course_category_id`),
  KEY                      `idx_category_course` (`course_category_id`, `course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程与分类关联表';

-- ----------------------------
-- 6. 课程内容项及附属表（Task 3）
-- ----------------------------
CREATE TABLE `sts_course_item`
(
  `course_item_id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '课程内容项ID（主键）',
  `organization_id`               bigint       NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `course_id`                     bigint       NOT NULL COMMENT '课程ID',
  `chapter_id`                    bigint       NOT NULL COMMENT '章节ID',
  `item_type`        varchar(24) NOT NULL COMMENT '课程内容项类型（ASSET/KNOWLEDGE_POINT/QUESTION_BANK/QUIZ/LIVE）',
  `item_title`                    varchar(255) NOT NULL COMMENT '课程内容项标题',
  `is_final_assessment` tinyint NOT NULL DEFAULT 0 COMMENT '结业测评标记',
  `pass_score` decimal(10,2) DEFAULT NULL COMMENT '结业测评及格线覆盖值',
  `linked_quiz_item_id` bigint DEFAULT NULL COMMENT '课件关联同课程测验ID',
  `asset_id`                      bigint                DEFAULT NULL COMMENT '素材ID',
  `paper_version_id` bigint DEFAULT NULL COMMENT '测验直接引用的已发布试卷版本ID（仅QUIZ）',
  `is_required`                   tinyint      NOT NULL COMMENT '是否必修或必填（0否 1是）',
  `minimum_study_seconds`         int          NOT NULL COMMENT '最低有效学习秒数',
  `sort_order`                    int          NOT NULL COMMENT '显示顺序',
  `is_locked_after_participation` tinyint      NOT NULL COMMENT '参与后是否锁定配置（0否 1是）',
  `status`                        tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`                     bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`                   datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`                     bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`                   datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                       bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                       int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`course_item_id`),
  KEY                             `idx_organization_id` (`organization_id`),
  KEY                             `idx_chapter_sort` (`chapter_id`, `sort_order`),
  KEY                             `idx_course_type` (`course_id`, `item_type`, `status`),
  KEY                `idx_asset` (`asset_id`),
  KEY                `idx_paper_version` (`paper_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程内容项表';

CREATE TABLE `sts_course_item_knowledge`
(
  `item_knowledge_id`  bigint NOT NULL AUTO_INCREMENT COMMENT '课程内容项知识点关联ID（主键）',
  `organization_id`    bigint NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `course_item_id`     bigint NOT NULL COMMENT '课程内容项ID',
  `knowledge_point_id` bigint NOT NULL COMMENT '知识点ID',
  `sort_order`         int    NOT NULL COMMENT '显示顺序',
  `create_by`          bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`        datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`item_knowledge_id`),
  KEY                  `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_item_knowledge` (`course_item_id`, `knowledge_point_id`),
  KEY                  `idx_knowledge_item` (`knowledge_point_id`, `course_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程内容项知识点关联表';

CREATE TABLE `sts_course_item_question_bank`
(
  `item_question_bank_id` bigint NOT NULL AUTO_INCREMENT COMMENT '课程内容项题库关联ID（主键）',
  `organization_id`       bigint NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `course_item_id`        bigint NOT NULL COMMENT '课程内容项ID',
  `question_bank_id`      bigint NOT NULL COMMENT '题库ID',
  `create_by`             bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`           datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`item_question_bank_id`),
  KEY                     `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_item_question_bank` (`course_item_id`),
  KEY                     `idx_bank_item` (`question_bank_id`, `course_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程内容项题库关联表';

CREATE TABLE `sts_course_live_session`
(
  `live_session_id` bigint        NOT NULL AUTO_INCREMENT COMMENT '直播课程场次ID（主键）',
  `organization_id` bigint        NOT NULL COMMENT '所属机构ID',
  `course_item_id`  bigint        NOT NULL COMMENT '课程内容项ID',
  `live_name`       varchar(150)  NOT NULL COMMENT '直播名称',
  `teacher_user_id` bigint        NOT NULL COMMENT '直播讲师用户ID',
  `live_url`        varchar(1000) NOT NULL COMMENT '直播入口链接',
  `start_time`      datetime      NOT NULL COMMENT '直播开始时间',
  `end_time`        datetime      NOT NULL COMMENT '直播结束时间',
  `live_status`     varchar(16)   NOT NULL COMMENT '直播状态（DRAFT/SCHEDULED/LIVING/ENDED/CANCELLED）',
  `status`          tinyint       NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`       bigint                 DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`     datetime               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       bigint                 DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`     datetime               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         bigint        NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`         int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`live_session_id`),
  UNIQUE KEY `uk_course_item_deleted` (`course_item_id`, `deleted`),
  KEY               `idx_org_time` (`organization_id`, `start_time`, `end_time`),
  KEY               `idx_teacher_time` (`teacher_user_id`, `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='直播课程场次表';

CREATE TABLE `sts_course_item_video_question`
(
  `video_question_id`   bigint  NOT NULL AUTO_INCREMENT COMMENT '视频弹题配置ID（主键）',
  `organization_id`     bigint  NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `course_item_id`      bigint  NOT NULL COMMENT '课程内容项ID',
  `question_version_id` bigint  NOT NULL COMMENT '题目版本ID',
  `popup_second`        int     NOT NULL COMMENT '视频弹题时间点秒数',
  `wrong_replay_second` int     NOT NULL COMMENT '答错回退播放时间点秒数',
  `sort_order`          int     NOT NULL COMMENT '显示顺序',
  `status`              tinyint NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`           bigint           DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`         datetime         DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`           bigint           DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`         datetime         DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`             bigint  NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`             int     NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`video_question_id`),
  KEY                   `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_item_question_time_deleted` (`course_item_id`, `question_version_id`, `popup_second`, `deleted`),
  KEY                   `idx_item_time` (`course_item_id`, `popup_second`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程视频弹题配置表';

-- Audit class C: immutable course publish version
CREATE TABLE `sts_course_version`
(
  `course_version_id`     bigint       NOT NULL AUTO_INCREMENT COMMENT '课程发布版本ID（主键）',
  `organization_id`       bigint       NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `course_id`             bigint       NOT NULL COMMENT '课程ID',
  `version_no`            int          NOT NULL COMMENT '版本号（从1递增）',
  `content_snapshot_json` json         NOT NULL COMMENT '课程基本信息、章节、内容项和学习规则完整快照JSON',
  `snapshot_hash`         varchar(128) NOT NULL COMMENT '版本快照校验哈希',
  `version_note`          varchar(500) DEFAULT NULL COMMENT '版本说明',
  `published_by`          bigint       NOT NULL COMMENT '发布人用户ID',
  `published_time`        datetime     NOT NULL COMMENT '发布时间',
  `create_by`             bigint       DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`           datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`course_version_id`),
  KEY                     `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_course_version` (`course_id`, `version_no`),
  KEY                     `idx_course_publish_time` (`course_id`, `published_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程不可变发布版本表';

CREATE TABLE `sts_course_download_asset`
(
  `course_download_asset_id` bigint  NOT NULL AUTO_INCREMENT COMMENT '课程下载素材ID（主键）',
  `organization_id`          bigint  NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `course_id`                bigint  NOT NULL COMMENT '课程ID',
  `asset_id`                 bigint  NOT NULL COMMENT '素材ID',
  `sort_order`               int     NOT NULL COMMENT '显示顺序',
  `status`                   tinyint NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`                bigint           DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`              datetime         DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`                bigint           DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`              datetime         DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                  bigint  NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                  int     NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`course_download_asset_id`),
  KEY                        `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_course_asset_deleted` (`course_id`, `asset_id`, `deleted`),
  KEY                        `idx_course_sort` (`course_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程可下载素材表';

-- Audit class A: course offering through evaluation
CREATE TABLE `sts_course_offering`
(
  `course_offering_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '课程开课ID（主键）',
  `organization_id`    bigint       NOT NULL COMMENT '所属机构ID',
  `offering_code`      varchar(64)  NOT NULL COMMENT '开课编码',
  `course_id`          bigint       NOT NULL COMMENT '课程ID',
  `course_version_id`  bigint                DEFAULT NULL COMMENT '开课绑定的课程发布版本ID（发布后必填且不可变）',
  `plan_content_id`    bigint                DEFAULT NULL COMMENT '来源培训计划内容ID',
  `offering_name`      varchar(150) NOT NULL COMMENT '开课名称',
  `start_time`         datetime              DEFAULT NULL COMMENT '开始时间',
  `end_time`           datetime              DEFAULT NULL COMMENT '结束时间',
  `course_type`        varchar(16)  NOT NULL COMMENT '开课类型（REQUIRED必修/ELECTIVE选修；与报名类型正交）',
  `enrollment_type`    varchar(16)  NOT NULL COMMENT '报名类型（ASSIGNED/OPEN）',
  `eligibility_type`   varchar(16)  NOT NULL COMMENT '开放报名资格类型（ALL/SELECTED；ASSIGNED时忽略）',
  `completion_rule`    varchar(32)  NOT NULL COMMENT '完成规则（ALL_REQUIRED_ITEMS/ALL_ITEMS）',
  `publish_status`     varchar(16)  NOT NULL COMMENT '发布状态（DRAFT/PUBLISHED/ENDED/CANCELLED）',
  `published_time`     datetime              DEFAULT NULL COMMENT '发布时间',
  `status`             tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`          bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`        datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`        datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`            int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`course_offering_id`),
  UNIQUE KEY `uk_org_offering_code_deleted` (`organization_id`, `offering_code`, `deleted`),
  KEY                  `idx_course_time` (`course_id`, `start_time`, `end_time`),
  KEY                  `idx_course_version` (`course_version_id`),
  KEY                  `idx_org_publish` (`organization_id`, `publish_status`),
  UNIQUE KEY `uk_plan_content_deleted` (`plan_content_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程开课表';

CREATE TABLE `sts_course_offering_eligible_user`
(
  `offering_eligible_user_id` bigint NOT NULL AUTO_INCREMENT COMMENT '开放报名具体用户白名单ID',
  `organization_id`           bigint NOT NULL COMMENT '所属机构ID',
  `course_offering_id`        bigint NOT NULL COMMENT '课程开课ID',
  `user_id`                   bigint NOT NULL COMMENT '可自主报名用户ID',
  `create_by`                 bigint          DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`               datetime        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`                 bigint          DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`               datetime        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                   bigint NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                   int    NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`offering_eligible_user_id`),
  UNIQUE KEY `uk_offering_user_deleted` (`course_offering_id`, `user_id`, `deleted`),
  KEY                         `idx_org_user` (`organization_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程开放报名具体用户白名单';

CREATE TABLE `sts_course_enrollment`
(
  `course_enrollment_id`    bigint         NOT NULL AUTO_INCREMENT COMMENT '课程报名ID（主键）',
  `organization_id`         bigint         NOT NULL COMMENT '所属机构ID',
  `course_offering_id`      bigint         NOT NULL COMMENT '课程开课ID',
  `user_id`                 bigint         NOT NULL COMMENT '用户ID',
  `training_participant_id` bigint                  DEFAULT NULL COMMENT '来源培训参训人员ID',
  `enrollment_source`       varchar(16)    NOT NULL COMMENT '报名来源（ASSIGN/SELF/IMPORT/TRAINING）',
  `source_business_id`      bigint                  DEFAULT NULL COMMENT '来源业务ID',
  `enrolled_time`           datetime       NOT NULL COMMENT '报名时间',
  `learning_status`         varchar(16)    NOT NULL COMMENT '学习状态（NOT_STARTED/LEARNING/COMPLETED/EXPIRED）',
  `progress_percent`        decimal(5, 2)  NOT NULL COMMENT '进度百分比',
  `recognized_study_seconds` int DEFAULT NULL COMMENT '合格认可学习秒数，历史为空',
  `total_study_seconds`     int            NOT NULL COMMENT '累计学习秒数',
  `credit_awarded`          decimal(10, 2) NOT NULL COMMENT '学分获得',
  `final_score`             decimal(10, 2)          DEFAULT NULL COMMENT '课程正式成绩（百分制；未产生正式成绩时为NULL）',
  `completed_time`          datetime                DEFAULT NULL COMMENT '完成时间',
  `last_study_time`         datetime                DEFAULT NULL COMMENT '最近学习时间',
  `result_snapshot_json`    json                    DEFAULT NULL COMMENT '结果快照JSON',
  `create_by`               bigint                  DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`             datetime                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`               bigint                  DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`             datetime                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                 bigint         NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                 int            NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`course_enrollment_id`),
  UNIQUE KEY `uk_offering_user_deleted` (`course_offering_id`, `user_id`, `deleted`),
  KEY                       `idx_user_status` (`user_id`, `learning_status`, `last_study_time`),
  KEY                       `idx_org_status` (`organization_id`, `learning_status`),
  KEY                       `idx_training_participant` (`training_participant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程报名表';

CREATE TABLE `sts_course_item_progress`
(
  `item_progress_id`      bigint      NOT NULL AUTO_INCREMENT COMMENT '课程内容项学习进度ID（主键）',
  `organization_id`       bigint      NOT NULL COMMENT '所属机构ID',
  `course_enrollment_id`  bigint      NOT NULL COMMENT '课程报名ID',
  `course_item_id`        bigint      NOT NULL COMMENT '课程内容项ID',
  `progress_status`       varchar(16) NOT NULL COMMENT '进度状态（NOT_STARTED/LEARNING/COMPLETED/FAILED）',
  `study_seconds`         int         NOT NULL COMMENT '学习秒',
  `last_position_seconds` int                  DEFAULT NULL COMMENT '最近播放位置秒数',
  `last_page_no`          int                  DEFAULT NULL COMMENT '最近学习页码',
  `attempt_count`         int         NOT NULL COMMENT '作答次数',
  `best_score`            decimal(10, 2)       DEFAULT NULL COMMENT '最佳分数',
  `first_start_time`      datetime             DEFAULT NULL COMMENT '首次开始时间',
  `completed_time`        datetime             DEFAULT NULL COMMENT '完成时间',
  `last_study_time`       datetime             DEFAULT NULL COMMENT '最近学习时间',
  `create_by`             bigint               DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`           datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`             bigint               DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`           datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`               bigint      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`               int         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`item_progress_id`),
  UNIQUE KEY `uk_enrollment_item_deleted` (`course_enrollment_id`, `course_item_id`, `deleted`),
  KEY                     `idx_item_status` (`course_item_id`, `progress_status`),
  KEY                     `idx_org_status_time` (`organization_id`, `progress_status`, `last_study_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程内容项学习进度表';

-- Audit class C
CREATE TABLE `sts_course_assessment_result`
(
  `course_assessment_result_id` bigint         NOT NULL AUTO_INCREMENT COMMENT '课程考核生效结果ID（主键）',
  `organization_id`             bigint         NOT NULL COMMENT '所属机构ID',
  `assessment_execution_id`     bigint         NOT NULL COMMENT '考核执行ID',
  `exam_candidate_id`           bigint         NOT NULL COMMENT '考核人员ID',
  `exam_attempt_id`             bigint         NOT NULL COMMENT '作答尝试ID',
  `course_enrollment_id`        bigint         NOT NULL COMMENT '课程报名ID',
  `course_offering_id`          bigint         NOT NULL COMMENT '课程开课ID',
  `course_item_id`              bigint         NOT NULL COMMENT '课程内容项ID',
  `user_id`                     bigint         NOT NULL COMMENT '学员用户ID',
  `source_type`                 varchar(32)    NOT NULL COMMENT '来源类型（COURSE_QUIZ/COURSE_BANK_PRACTICE）',
  `attempt_no`                  int            NOT NULL COMMENT '作答轮次',
  `final_score`                 decimal(10, 2) NOT NULL COMMENT '最终分数',
  `is_passed`                   tinyint        NOT NULL COMMENT '是否合格（0否 1是）',
  `correct_count`               int            NOT NULL COMMENT '答对题数',
  `wrong_count`                 int            NOT NULL COMMENT '答错题数',
  `effective_time`              datetime       NOT NULL COMMENT '结果生效时间',
  `create_by`                   bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`                 datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`course_assessment_result_id`),
  UNIQUE KEY `uk_assessment_attempt` (`exam_attempt_id`),
  KEY                           `idx_execution_enrollment_item` (`assessment_execution_id`, `course_enrollment_id`, `course_item_id`),
  KEY                           `idx_enrollment_item_time` (`course_enrollment_id`, `course_item_id`, `effective_time`),
  KEY                           `idx_org_source_time` (`organization_id`, `source_type`, `effective_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程考核自动生效结果事实表';

CREATE TABLE `sts_course_study_session`
(
  `study_session_id`       bigint       NOT NULL AUTO_INCREMENT COMMENT '学习会话ID（主键）',
  `organization_id`        bigint       NOT NULL COMMENT '所属机构ID',
  `course_enrollment_id`   bigint       NOT NULL COMMENT '课程报名ID',
  `course_item_id`         bigint       NOT NULL COMMENT '课程内容项ID',
  `user_id`                bigint       NOT NULL COMMENT '用户ID',
  `device_type`            varchar(16)  NOT NULL COMMENT '设备类型（PC/APP/H5）',
  `device_id`              varchar(64)  NOT NULL DEFAULT '' COMMENT '终端标识（客户端生成并持久化，用于同一终端多账户互斥检测）',
  `started_time`           datetime     NOT NULL COMMENT '开始时间',
  `ended_time`             datetime              DEFAULT NULL COMMENT '结束时间',
  `valid_study_seconds`    int          NOT NULL COMMENT '有效学习秒数',
  `start_position_seconds` int                   DEFAULT NULL COMMENT '开始播放位置秒数',
  `end_position_seconds`   int                   DEFAULT NULL COMMENT '结束播放位置秒数',
  `start_page_no`          int                   DEFAULT NULL COMMENT '开始页码',
  `end_page_no`            int                   DEFAULT NULL COMMENT '结束页码',
  `end_reason`             varchar(32)  NOT NULL COMMENT '结束原因（COMPLETED/USER_EXIT/SCREEN_LEAVE/TIMEOUT/ERROR/HEARTBEAT；HEARTBEAT为定时检查点，非HEARTBEAT收尾即释放互斥）',
  `client_trace_id`        varchar(128) NOT NULL COMMENT '客户端追踪标识（同一课程报名内幂等唯一）',
  `create_by`              bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`            datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`              bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`            datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`study_session_id`),
  UNIQUE KEY `uk_enrollment_trace_deleted` (`course_enrollment_id`, `client_trace_id`, `deleted`),
  KEY                      `idx_enrollment_time` (`course_enrollment_id`, `started_time`),
  KEY                      `idx_user_time` (`user_id`, `started_time`),
  KEY                      `idx_user_active` (`user_id`, `update_time`),
  KEY                      `idx_device_active` (`device_id`, `update_time`),
  KEY                      `idx_org_started_time` (`organization_id`, `started_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程学习会话表';

CREATE TABLE `sts_course_evaluation`
(
  `course_evaluation_id` bigint        NOT NULL AUTO_INCREMENT COMMENT '课程评价ID（主键）',
  `organization_id`      bigint        NOT NULL COMMENT '所属机构ID',
  `course_offering_id`   bigint        NOT NULL COMMENT '课程开课ID',
  `course_enrollment_id` bigint        NOT NULL COMMENT '课程报名ID',
  `user_id`              bigint        NOT NULL COMMENT '用户ID',
  `rating`               decimal(3, 1) NOT NULL COMMENT '评分',
  `content`              varchar(2000)          DEFAULT NULL COMMENT '内容',
  `is_anonymous`         tinyint       NOT NULL COMMENT '是否匿名展示（0否 1是）',
  `submitted_time`       datetime      NOT NULL COMMENT '提交时间',
  `status`               tinyint       NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`            bigint                 DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`          datetime               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`            bigint                 DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`          datetime               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`              bigint        NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`              int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`course_evaluation_id`),
  UNIQUE KEY `uk_enrollment_deleted` (`course_enrollment_id`, `deleted`),
  KEY                    `idx_offering_time` (`course_offering_id`, `submitted_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程评价表';

SET
FOREIGN_KEY_CHECKS = 1;

CREATE TABLE `sts_course_favorite` (
  `course_favorite_id` bigint NOT NULL AUTO_INCREMENT COMMENT '收藏记录主键',
  `organization_id` bigint NOT NULL COMMENT '所属机构',
  `user_id` bigint NOT NULL COMMENT '学员',
  `course_id` bigint NOT NULL COMMENT '课程',
  `is_favorite` tinyint NOT NULL DEFAULT 1 COMMENT '是否收藏',
  `course_name_snapshot` varchar(255) NOT NULL COMMENT '课程名称快照',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁',
  PRIMARY KEY (`course_favorite_id`),
  UNIQUE KEY uk_course_favorite (organization_id,user_id,course_id,deleted),
  KEY idx_course_favorite_list (organization_id,user_id,is_favorite)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本人课程收藏';

CREATE TABLE `sts_teacher_follow` (
  `teacher_follow_id` bigint NOT NULL AUTO_INCREMENT COMMENT '关注记录主键',
  `organization_id` bigint NOT NULL COMMENT '所属机构',
  `user_id` bigint NOT NULL COMMENT '学员',
  `teacher_id` bigint NOT NULL COMMENT '已有教师用户',
  `is_followed` tinyint NOT NULL DEFAULT 1 COMMENT '是否关注',
  `teacher_name_snapshot` varchar(255) NOT NULL COMMENT '教师姓名快照',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bigint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁',
  PRIMARY KEY (`teacher_follow_id`),
  UNIQUE KEY uk_teacher_follow (organization_id,user_id,teacher_id,deleted),
  KEY idx_teacher_follow_list (organization_id,user_id,is_followed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本人教师关注';
