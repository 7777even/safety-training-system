-- Integration-test schema: archive
SET NAMES utf8mb4;

CREATE TABLE `sts_archive_asset`
(
  `archive_asset_id`     bigint       NOT NULL AUTO_INCREMENT COMMENT '档案素材ID（主键）',
  `organization_id`      bigint       NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
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
  KEY                    `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_version_file` (`archive_version_id`, `file_info_id`),
  KEY                    `idx_version_type` (`archive_version_id`, `asset_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训档案附件记录表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='【暂时隐藏】原用于：档案异步导出任务中心；当前中山50最小闭环不启用，不得作为当前页面、API或业务规则依据；后续启用须新建并确认 OpenSpec Change';

CREATE TABLE `sts_archive_participant`
(
  `archive_participant_id`       bigint      NOT NULL AUTO_INCREMENT COMMENT '档案参与人员ID（主键）',
  `organization_id`              bigint      NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `archive_version_id`           bigint      NOT NULL COMMENT '档案版本ID',
  `training_participant_id`      bigint      NOT NULL COMMENT '培训参与人员ID',
  `user_id`                      bigint      NOT NULL COMMENT '用户ID',
  `participant_org_id`           bigint      NOT NULL COMMENT '参训人员所属机构ID',
  `member_snapshot_json`         json        NOT NULL COMMENT '成员信息快照JSON',
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
  KEY                            `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_version_participant` (`archive_version_id`, `training_participant_id`),
  KEY                            `idx_user_version` (`user_id`, `archive_version_id`),
  KEY                            `idx_org_version` (`participant_org_id`, `archive_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='培训档案人员记录表';

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

CREATE TABLE `sts_archive_version`
(
  `archive_version_id`         bigint       NOT NULL AUTO_INCREMENT COMMENT '档案版本ID（主键）',
  `organization_id`            bigint       NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `training_archive_id`        bigint       NOT NULL COMMENT '培训档案ID',
  `version_no`                 int          NOT NULL COMMENT '版本号',
  `plan_snapshot_json`         json         NOT NULL COMMENT '培训计划快照JSON',
  `organization_snapshot_json` json         NOT NULL COMMENT '机构快照JSON',
  `summary_snapshot_json`      json         NOT NULL COMMENT '汇总结果快照JSON',
  `file_info_id`               bigint       DEFAULT NULL COMMENT '当前归档版本生成文件ID',
  `snapshot_hash` varchar(128) NOT NULL COMMENT '【内部保留】用于：档案快照一致性校验；不提供独立页面或对外操作',
  `generated_time`             datetime     NOT NULL COMMENT '生成时间',
  `generated_by`               bigint       NOT NULL COMMENT '生成人用户ID',
  `version_reason`             varchar(500) NOT NULL COMMENT '版本原因',
  `create_by`                  bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`                datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`archive_version_id`),
  KEY                          `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_archive_version` (`training_archive_id`, `version_no`),
  KEY                          `idx_generated_time` (`generated_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='【内部保留】用于：更新档案时生成当前快照；不提供独立页面或对外操作';
-- 档案展示更正只追加，不回写业务事实，不自动绑定任何现有角色。
CREATE TABLE sts_archive_history_correction (
  correction_id bigint NOT NULL AUTO_INCREMENT COMMENT '更正主键',
  organization_id bigint NOT NULL COMMENT '原记录机构',
  user_id bigint NOT NULL COMMENT '学员主键',
  record_type varchar(32) NOT NULL COMMENT '来源类型',
  record_id bigint NOT NULL COMMENT '来源记录主键',
  version int NOT NULL COMMENT '递增档案展示版本',
  before_json json NOT NULL COMMENT '本次字段在当前展示版本的前值',
  after_json json NOT NULL COMMENT '本次字段更正后值',
  reason varchar(500) NOT NULL COMMENT '更正理由',
  operator_id bigint NOT NULL COMMENT '授权操作人',
  operator_name varchar(100) DEFAULT NULL COMMENT '操作人姓名快照',
  operated_time datetime NOT NULL COMMENT '追加时间',
  PRIMARY KEY (correction_id),
  UNIQUE KEY uk_history_version (organization_id,user_id,record_type,record_id,version),
  KEY idx_history_user (organization_id,user_id,operated_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='只追加的档案展示更正账本';
