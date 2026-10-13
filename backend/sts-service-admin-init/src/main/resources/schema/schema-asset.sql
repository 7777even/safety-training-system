-- =================================================================================
-- UNISENCE (统一互联) 平台素材模块数据库表结构 (MySQL DDL 脚本)
--
-- 最新完整 DDL；数据库变化必须同步更新本文件、backend/sts-service-admin/src/main/resources/db/migration/ 中的 Admin 增量和 init schema version。
-- 本文件供 admin-init 首次建库，并由构建拷入 sts-service-admin 集成测试 classpath，供 Testcontainers 空库建表。
-- 仅含 sts_asset_* 素材业务表；课程业务见 schema-course.sql，系统表见 schema-system.sql。
-- =================================================================================

SET NAMES utf8mb4;
SET
FOREIGN_KEY_CHECKS = 0;

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

CREATE TABLE `sts_asset_derivative`
(
  `asset_derivative_id` bigint      NOT NULL AUTO_INCREMENT COMMENT '素材派生文件ID（主键）',
  `organization_id`     bigint      NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
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
  KEY                   `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_asset_type_deleted` (`asset_id`, `derivative_type`, `deleted`),
  KEY                   `idx_file` (`file_info_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='素材派生文件表';

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

SET
FOREIGN_KEY_CHECKS = 1;
