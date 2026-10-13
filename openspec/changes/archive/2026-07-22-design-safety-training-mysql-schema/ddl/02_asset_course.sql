-- OpenSpec MySQL schema draft; not a migration and must not be executed directly.
-- Source: ../design.md

SET NAMES utf8mb4;
SET
FOREIGN_KEY_CHECKS = 0;

-- Audit class A
CREATE TABLE `sts_asset_asset`
(
  `asset_id`         bigint       NOT NULL AUTO_INCREMENT COMMENT '素材ID（主键）',
  `organization_id`  bigint       NOT NULL COMMENT '所属机构ID',
  `asset_code`       varchar(64)  NOT NULL COMMENT '素材编码',
  `asset_name`       varchar(255) NOT NULL COMMENT '素材名称',
  `asset_type`       varchar(32)  NOT NULL COMMENT '素材类型（DOCUMENT/IMAGE/AUDIO/VIDEO/RICH_TEXT/LINK/LIVE）',
  `source_type`      varchar(16)  NOT NULL COMMENT '来源类型（UPLOAD/CREATED/EXTERNAL）',
  `file_info_id`     bigint                DEFAULT NULL COMMENT '文件信息ID',
  `external_url`     varchar(1000)         DEFAULT NULL COMMENT '外部访问地址',
  `rich_content`     longtext              DEFAULT NULL COMMENT '富文本内容',
  `mime_type`        varchar(128)          DEFAULT NULL COMMENT 'MIME类型',
  `file_size`        bigint                DEFAULT NULL COMMENT '文件大小字节数',
  `page_count`       int                   DEFAULT NULL COMMENT '页数',
  `duration_seconds` int                   DEFAULT NULL COMMENT '时长秒数',
  `checksum`         varchar(128)          DEFAULT NULL COMMENT '文件校验值',
  `process_status`   varchar(16)  NOT NULL COMMENT '处理状态（UPLOADED/PROCESSING/SUCCEEDED/FAILED）',
  `status`           tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`        bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`      datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`      datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`          bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`          int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`asset_id`),
  UNIQUE KEY `uk_org_asset_code_deleted` (`organization_id`, `asset_code`, `deleted`),
  KEY                `idx_org_type_status` (`organization_id`, `asset_type`, `status`),
  KEY                `idx_file_info` (`file_info_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='素材表';

-- Audit class A
CREATE TABLE `sts_asset_derivative`
(
  `asset_derivative_id` bigint      NOT NULL AUTO_INCREMENT COMMENT '素材派生文件ID（主键）',
  `asset_id`            bigint      NOT NULL COMMENT '素材ID',
  `derivative_type`     varchar(32) NOT NULL COMMENT '派生文件类型（PREVIEW/TRANSCODED/THUMBNAIL/HLS）',
  `file_info_id`        bigint      NOT NULL COMMENT '文件信息ID',
  `duration_seconds`    int                  DEFAULT NULL COMMENT '时长秒数',
  `page_count`          int                  DEFAULT NULL COMMENT '页数',
  `status`              tinyint     NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`           bigint               DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`         datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`           bigint               DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`         datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`             bigint      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`             int         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`asset_derivative_id`),
  UNIQUE KEY `uk_asset_type_deleted` (`asset_id`, `derivative_type`, `deleted`),
  KEY                   `idx_file` (`file_info_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='素材派生文件表';

-- Audit class A
CREATE TABLE `sts_asset_transcode_job`
(
  `transcode_job_id` bigint      NOT NULL AUTO_INCREMENT COMMENT '转码任务ID（主键）',
  `organization_id`  bigint      NOT NULL COMMENT '所属机构ID',
  `asset_id`         bigint      NOT NULL COMMENT '素材ID',
  `course_id`        bigint               DEFAULT NULL COMMENT '课程ID',
  `chapter_id`       bigint               DEFAULT NULL COMMENT '章节ID',
  `job_type`         varchar(32) NOT NULL COMMENT '转码任务类型（DOCUMENT_PREVIEW/AUDIO_TRANSCODE/VIDEO_TRANSCODE）',
  `job_status`       varchar(16) NOT NULL COMMENT '转码任务状态（PENDING/RUNNING/SUCCEEDED/FAILED）',
  `provider_job_id`  varchar(128)         DEFAULT NULL COMMENT '服务商任务标识',
  `started_time`     datetime             DEFAULT NULL COMMENT '开始时间',
  `finished_time`    datetime             DEFAULT NULL COMMENT '完成时间',
  `retry_count`      int         NOT NULL COMMENT '重试次数',
  `failure_code`     varchar(64)          DEFAULT NULL COMMENT '失败编码',
  `failure_reason`   varchar(1000)        DEFAULT NULL COMMENT '失败原因',
  `create_by`        bigint               DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`      datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        bigint               DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`      datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`          bigint      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`          int         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`transcode_job_id`),
  KEY                `idx_status_time` (`job_status`, `create_time`),
  KEY                `idx_asset` (`asset_id`, `job_status`),
  KEY                `idx_course_chapter` (`course_id`, `chapter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='素材转码任务表';

-- Audit class A
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

-- Audit class A
CREATE TABLE `sts_course_setting`
(
  `course_setting_id`                 bigint        NOT NULL AUTO_INCREMENT COMMENT '机构课程设置ID（主键）',
  `organization_id`                   bigint        NOT NULL COMMENT '所属机构ID',
  `default_document_page_seconds`     int           NOT NULL COMMENT '文档每页默认学习秒数',
  `default_rich_text_seconds`         int           NOT NULL COMMENT '图文课件默认学习秒数',
  `default_link_live_seconds`         int           NOT NULL COMMENT '链接或直播课件默认学习秒数',
  `default_media_completion_ratio`    decimal(7, 4) NOT NULL COMMENT '音视频默认完成比例',
  `is_category_permission_enabled`    tinyint       NOT NULL COMMENT '是否启用课程分类权限（0否 1是）',
  `is_resume_prompt_enabled`          tinyint       NOT NULL COMMENT '是否启用断点续播提示（0否 1是）',
  `is_last_page_required`             tinyint       NOT NULL COMMENT '是否强制学习文档最后一页（0否 1是）',
  `is_fast_forward_globally_disabled` tinyint       NOT NULL COMMENT '是否全局禁止拖动快进（0否 1是）',
  `is_screen_leave_time_paused`       tinyint       NOT NULL COMMENT '是否在离开学习页面时暂停计时（0否 1是）',
  `is_transcode_notification_enabled` tinyint       NOT NULL COMMENT '是否向发布人发送转码完成通知（0否 1是）',
  `status`                            tinyint       NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`                         bigint                 DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`                       datetime               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`                         bigint                 DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`                       datetime               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                           bigint        NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                           int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`course_setting_id`),
  UNIQUE KEY `uk_org_deleted` (`organization_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='机构统一课程设置表';

-- Audit class A
CREATE TABLE `sts_course_course`
(
  `course_id`                    bigint         NOT NULL AUTO_INCREMENT COMMENT '课程ID（主键）',
  `organization_id`              bigint         NOT NULL COMMENT '所属机构ID',
  `course_code`                  varchar(64)    NOT NULL COMMENT '课程编码',
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
  `publish_status`               varchar(16)    NOT NULL COMMENT '发布状态（DRAFT/PUBLISHED/OFFLINE）',
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

-- Audit class C
CREATE TABLE `sts_course_version`
(
  `course_version_id`     bigint       NOT NULL AUTO_INCREMENT COMMENT '课程发布版本ID（主键）',
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
  UNIQUE KEY `uk_course_version` (`course_id`, `version_no`),
  KEY                     `idx_course_publish_time` (`course_id`, `published_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程不可变发布版本表';

-- Audit class B
CREATE TABLE `sts_course_course_category`
(
  `course_category_rel_id` bigint NOT NULL AUTO_INCREMENT COMMENT '课程分类关联ID（主键）',
  `course_id`              bigint NOT NULL COMMENT '课程ID',
  `course_category_id`     bigint NOT NULL COMMENT '课程分类ID',
  `create_by`              bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`            datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`course_category_rel_id`),
  UNIQUE KEY `uk_course_category` (`course_id`, `course_category_id`),
  KEY                      `idx_category_course` (`course_category_id`, `course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程与分类关联表';

-- Audit class A
CREATE TABLE `sts_course_chapter`
(
  `chapter_id`   bigint        NOT NULL AUTO_INCREMENT COMMENT '章节ID（主键）',
  `course_id`    bigint        NOT NULL COMMENT '课程ID',
  `parent_id`    bigint        NOT NULL DEFAULT 0 COMMENT '父级ID',
  `ancestors`    varchar(1000) NOT NULL COMMENT '祖级路径',
  `chapter_name` varchar(100)  NOT NULL COMMENT '章节名称',
  `sort_order`   int           NOT NULL COMMENT '显示顺序',
  `status`       tinyint       NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`    bigint                 DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`  datetime               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`    bigint                 DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`  datetime               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      bigint        NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`      int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`chapter_id`),
  UNIQUE KEY `uk_course_parent_name_deleted` (`course_id`, `parent_id`, `chapter_name`, `deleted`),
  KEY            `idx_course_parent_sort` (`course_id`, `parent_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程章节表';

-- Audit class A
CREATE TABLE `sts_course_item`
(
  `course_item_id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '课程内容项ID（主键）',
  `course_id`                     bigint       NOT NULL COMMENT '课程ID',
  `chapter_id`                    bigint       NOT NULL COMMENT '章节ID',
  `item_type`                     varchar(24)  NOT NULL COMMENT '课程内容项类型（ASSET/KNOWLEDGE_POINT/QUESTION_BANK/EXAM/PRACTICE/LIVE）',
  `item_title`                    varchar(255) NOT NULL COMMENT '课程内容项标题',
  `asset_id`                      bigint                DEFAULT NULL COMMENT '素材ID',
  `exam_id`                       bigint                DEFAULT NULL COMMENT '统一考试执行对象ID（QUESTION_BANK/EXAM/PRACTICE类型发布时必填）',
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
  KEY                             `idx_chapter_sort` (`chapter_id`, `sort_order`),
  KEY                             `idx_course_type` (`course_id`, `item_type`, `status`),
  KEY                             `idx_asset` (`asset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程内容项表';

-- Audit class B
CREATE TABLE `sts_course_item_knowledge`
(
  `item_knowledge_id`  bigint NOT NULL AUTO_INCREMENT COMMENT '课程内容项知识点关联ID（主键）',
  `course_item_id`     bigint NOT NULL COMMENT '课程内容项ID',
  `knowledge_point_id` bigint NOT NULL COMMENT '知识点ID',
  `sort_order`         int    NOT NULL COMMENT '显示顺序',
  `create_by`          bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`        datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`item_knowledge_id`),
  UNIQUE KEY `uk_item_knowledge` (`course_item_id`, `knowledge_point_id`),
  KEY                  `idx_knowledge_item` (`knowledge_point_id`, `course_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程内容项知识点关联表';

-- Audit class B
CREATE TABLE `sts_course_item_question_bank`
(
  `item_question_bank_id` bigint NOT NULL AUTO_INCREMENT COMMENT '课程内容项题库关联ID（主键）',
  `course_item_id`        bigint NOT NULL COMMENT '课程内容项ID',
  `question_bank_id`      bigint NOT NULL COMMENT '题库ID',
  `create_by`             bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`           datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`item_question_bank_id`),
  UNIQUE KEY `uk_item_question_bank` (`course_item_id`),
  KEY                     `idx_bank_item` (`question_bank_id`, `course_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程内容项题库关联表';

-- Audit class A
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
  `audience_id`     bigint                 DEFAULT NULL COMMENT '参与范围ID',
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
  KEY               `idx_teacher_time` (`teacher_user_id`, `start_time`),
  KEY               `idx_audience` (`audience_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='直播课程场次表';

-- Audit class A
CREATE TABLE `sts_course_item_material_rule`
(
  `material_rule_id`        bigint      NOT NULL AUTO_INCREMENT COMMENT '课程材料规则ID（主键）',
  `course_item_id`          bigint      NOT NULL COMMENT '课程内容项ID',
  `duration_mode`           varchar(16) NOT NULL COMMENT '时长模式（AUTO/MANUAL）',
  `required_seconds`        int         NOT NULL COMMENT '要求学习秒数',
  `is_fast_forward_allowed` tinyint     NOT NULL COMMENT '是否允许快进（0否 1是）',
  `is_speed_play_allowed`   tinyint     NOT NULL COMMENT '是否允许倍速播放（0否 1是）',
  `watermark_mode`          varchar(16) NOT NULL COMMENT '水印模式（SYSTEM/CUSTOM/NONE）',
  `watermark_color`         varchar(16)          DEFAULT NULL COMMENT '水印颜色',
  `watermark_opacity`       decimal(7, 4)        DEFAULT NULL COMMENT '水印不透明度（0到1）',
  `aux_audio_asset_id`      bigint               DEFAULT NULL COMMENT '辅助音频素材ID',
  `is_screen_leave_paused`  tinyint     NOT NULL COMMENT '离开学习页面时是否暂停计时（0否 1是）',
  `create_by`               bigint               DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`             datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`               bigint               DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`             datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                 bigint      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                 int         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`material_rule_id`),
  UNIQUE KEY `uk_course_item_deleted` (`course_item_id`, `deleted`),
  KEY                       `idx_aux_audio_asset` (`aux_audio_asset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程材料规则表';

-- Audit class A
CREATE TABLE `sts_course_item_video_question`
(
  `video_question_id`   bigint  NOT NULL AUTO_INCREMENT COMMENT '视频弹题配置ID（主键）',
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
  UNIQUE KEY `uk_item_question_time_deleted` (`course_item_id`, `question_version_id`, `popup_second`, `deleted`),
  KEY                   `idx_item_time` (`course_item_id`, `popup_second`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程视频弹题配置表';

-- Audit class A
CREATE TABLE `sts_course_download_asset`
(
  `course_download_asset_id` bigint  NOT NULL AUTO_INCREMENT COMMENT '课程下载素材ID（主键）',
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
  UNIQUE KEY `uk_course_asset_deleted` (`course_id`, `asset_id`, `deleted`),
  KEY                        `idx_course_sort` (`course_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程可下载素材表';

-- Audit class A
CREATE TABLE `sts_course_offering`
(
  `course_offering_id`       bigint       NOT NULL AUTO_INCREMENT COMMENT '课程开课ID（主键）',
  `organization_id`          bigint       NOT NULL COMMENT '所属机构ID',
  `offering_code`            varchar(64)  NOT NULL COMMENT '开课编码',
  `course_id`                bigint       NOT NULL COMMENT '课程ID',
  `course_version_id`        bigint                DEFAULT NULL COMMENT '开课绑定的课程发布版本ID（发布后必填且不可变）',
  `plan_content_id`          bigint                DEFAULT NULL COMMENT '来源培训计划内容ID',
  `offering_name`            varchar(150) NOT NULL COMMENT '开课名称',
  `start_time`               datetime              DEFAULT NULL COMMENT '开始时间',
  `end_time`                 datetime              DEFAULT NULL COMMENT '结束时间',
  `course_type`              varchar(16)  NOT NULL COMMENT '开课类型（REQUIRED必修/ELECTIVE选修；与报名类型正交）',
  `enrollment_type`          varchar(16)  NOT NULL COMMENT '报名类型（ASSIGNED/OPEN/LINK）',
  `completion_rule`          varchar(32)  NOT NULL COMMENT '完成规则（开课合格方式；允许值待产品确认）',
  `audience_id`              bigint                DEFAULT NULL COMMENT '参与范围ID',
  `is_self_enroll_allowed`   tinyint      NOT NULL COMMENT '是否允许自主报名（0否 1是）',
  `is_external_user_allowed` tinyint      NOT NULL COMMENT '是否允许外部人员参加（0否 1是）',
  `publish_status`           varchar(16)  NOT NULL COMMENT '发布状态（DRAFT/PUBLISHED/ENDED/CANCELLED）',
  `published_time`           datetime              DEFAULT NULL COMMENT '发布时间',
  `status`                   tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`                bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`              datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`                bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`              datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                  bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                  int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`course_offering_id`),
  UNIQUE KEY `uk_org_offering_code_deleted` (`organization_id`, `offering_code`, `deleted`),
  KEY                        `idx_course_time` (`course_id`, `start_time`, `end_time`),
  KEY                        `idx_course_version` (`course_version_id`),
  KEY                        `idx_org_publish` (`organization_id`, `publish_status`),
  KEY                        `idx_plan_content` (`plan_content_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程开课表';

-- Audit class A
CREATE TABLE `sts_course_enrollment`
(
  `course_enrollment_id`    bigint         NOT NULL AUTO_INCREMENT COMMENT '课程报名ID（主键）',
  `organization_id`         bigint         NOT NULL COMMENT '所属机构ID',
  `course_offering_id`      bigint         NOT NULL COMMENT '课程开课ID',
  `user_id`                 bigint         NOT NULL COMMENT '用户ID',
  `training_participant_id` bigint                  DEFAULT NULL COMMENT '来源培训参训人员ID',
  `enrollment_source`       varchar(16)    NOT NULL COMMENT '报名来源（ASSIGN/SELF/LINK/IMPORT/TRAINING）',
  `source_business_id`      bigint                  DEFAULT NULL COMMENT '来源业务ID',
  `enrolled_time`           datetime       NOT NULL COMMENT '报名时间',
  `learning_status`         varchar(16)    NOT NULL COMMENT '学习状态（NOT_STARTED/LEARNING/COMPLETED/EXPIRED）',
  `progress_percent`        decimal(5, 2)  NOT NULL COMMENT '进度百分比',
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

-- Audit class A
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

-- Audit class A
CREATE TABLE `sts_course_study_session`
(
  `study_session_id`       bigint       NOT NULL AUTO_INCREMENT COMMENT '学习会话ID（主键）',
  `organization_id`        bigint       NOT NULL COMMENT '所属机构ID',
  `course_enrollment_id`   bigint       NOT NULL COMMENT '课程报名ID',
  `course_item_id`         bigint       NOT NULL COMMENT '课程内容项ID',
  `user_id`                bigint       NOT NULL COMMENT '用户ID',
  `device_type`            varchar(16)  NOT NULL COMMENT '设备类型（PC/APP/H5）',
  `started_time`           datetime     NOT NULL COMMENT '开始时间',
  `ended_time`             datetime              DEFAULT NULL COMMENT '结束时间',
  `valid_study_seconds`    int          NOT NULL COMMENT '有效学习秒数',
  `start_position_seconds` int                   DEFAULT NULL COMMENT '开始播放位置秒数',
  `end_position_seconds`   int                   DEFAULT NULL COMMENT '结束播放位置秒数',
  `start_page_no`          int                   DEFAULT NULL COMMENT '开始页码',
  `end_page_no`            int                   DEFAULT NULL COMMENT '结束页码',
  `end_reason`             varchar(32)  NOT NULL COMMENT '结束原因（允许值待产品确认）',
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
  KEY                      `idx_org_started_time` (`organization_id`, `started_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程学习会话表';

-- Audit class A
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
