-- OpenSpec MySQL schema draft; not a migration and must not be executed directly.
-- Source: ../design.md

SET NAMES utf8mb4;
SET
FOREIGN_KEY_CHECKS = 0;

-- Audit class A
CREATE TABLE `sts_org_organization`
(
  `organization_id`        bigint       NOT NULL AUTO_INCREMENT COMMENT '机构ID（主键）',
  `org_code`               varchar(64)  NOT NULL COMMENT '机构编码',
  `org_name`               varchar(100) NOT NULL COMMENT '机构名称',
  `org_type`               varchar(32)  NOT NULL COMMENT '机构类型（PLATFORM/PARK/ENTERPRISE/TRAINING_INSTITUTION）',
  `parent_organization_id` bigint                DEFAULT NULL COMMENT '上级机构ID',
  `contact_user_id`        bigint                DEFAULT NULL COMMENT '联系人用户ID',
  `status`                 tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `description`            varchar(500) NOT NULL COMMENT '描述',
  `create_by`              bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`            datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`              bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`            datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`organization_id`),
  UNIQUE KEY `uk_org_code_deleted` (`org_code`, `deleted`),
  KEY                      `idx_parent_type` (`parent_organization_id`, `org_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='组织机构表';

-- Audit class A
CREATE TABLE `sts_org_member`
(
  `org_member_id`   bigint      NOT NULL AUTO_INCREMENT COMMENT '机构成员ID（主键）',
  `organization_id` bigint      NOT NULL COMMENT '所属机构ID',
  `user_id`         bigint      NOT NULL COMMENT '系统登录用户ID（对应us_sys_user；部门由us_sys_user.dept_id确定）',
  `member_type`     varchar(32) NOT NULL COMMENT '成员类型（INTERNAL/EXTERNAL/REGULATOR）',
  `employee_no`     varchar(64)          DEFAULT NULL COMMENT '工号',
  `email`           varchar(128)         DEFAULT NULL COMMENT '邮箱',
  `job_title`       varchar(100)         DEFAULT NULL COMMENT '职务',
  `entry_date`      date                 DEFAULT NULL COMMENT '入职日期',
  `status`          tinyint     NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`       bigint               DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`     datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       bigint               DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`     datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         bigint      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`         int         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`org_member_id`),
  UNIQUE KEY `uk_org_user_deleted` (`organization_id`, `user_id`, `deleted`),
  UNIQUE KEY `uk_org_employee_deleted` (`organization_id`, `employee_no`, `deleted`),
  KEY               `idx_org_user_status` (`organization_id`, `user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='组织成员表';

-- Audit class A
CREATE TABLE `sts_org_tag`
(
  `tag_id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '标签ID（主键）',
  `organization_id` bigint       NOT NULL COMMENT '所属机构ID',
  `tag_name`        varchar(100) NOT NULL COMMENT '标签名称',
  `description`     varchar(500) NOT NULL COMMENT '描述',
  `status`          tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`       bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`     datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`     datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`         int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`tag_id`),
  UNIQUE KEY `uk_org_tag_deleted` (`organization_id`, `tag_name`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='组织标签表';

-- Audit class B
CREATE TABLE `sts_org_member_tag`
(
  `member_tag_id` bigint NOT NULL AUTO_INCREMENT COMMENT '成员标签ID（主键）',
  `org_member_id` bigint NOT NULL COMMENT '机构成员ID',
  `tag_id`        bigint NOT NULL COMMENT '标签ID',
  `create_by`     bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`   datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`member_tag_id`),
  UNIQUE KEY `uk_member_tag` (`org_member_id`, `tag_id`),
  KEY             `idx_tag_member` (`tag_id`, `org_member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='组织成员标签关联表';

-- Audit class A
CREATE TABLE `sts_org_position`
(
  `position_id`     bigint       NOT NULL AUTO_INCREMENT COMMENT '岗位ID（主键）',
  `organization_id` bigint       NOT NULL COMMENT '所属机构ID',
  `position_code`   varchar(64)  NOT NULL COMMENT '岗位编码',
  `position_name`   varchar(100) NOT NULL COMMENT '岗位名称',
  `description`     varchar(500) NOT NULL COMMENT '描述',
  `status`          tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`       bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`     datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`     datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`         int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`position_id`),
  UNIQUE KEY `uk_org_position_code_deleted` (`organization_id`, `position_code`, `deleted`),
  KEY               `idx_org_position_name` (`organization_id`, `position_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='组织岗位表';

-- Audit class B
CREATE TABLE `sts_org_member_position`
(
  `member_position_id` bigint  NOT NULL AUTO_INCREMENT COMMENT '成员岗位ID（主键）',
  `org_member_id`      bigint  NOT NULL COMMENT '机构成员ID',
  `position_id`        bigint  NOT NULL COMMENT '岗位ID',
  `is_primary`         tinyint NOT NULL COMMENT '是否主要记录（0否 1是）',
  `create_by`          bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`        datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`member_position_id`),
  UNIQUE KEY `uk_member_position` (`org_member_id`, `position_id`),
  KEY                  `idx_position_member` (`position_id`, `org_member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='组织成员岗位关联表';

-- Audit class A
CREATE TABLE `sts_org_regulatory_relation`
(
  `regulatory_relation_id` bigint      NOT NULL AUTO_INCREMENT COMMENT '监管关系ID（主键）',
  `regulator_org_id`       bigint      NOT NULL COMMENT '监管机构ID',
  `regulated_org_id`       bigint      NOT NULL COMMENT '被监管机构ID',
  `relation_type`          varchar(32) NOT NULL COMMENT '监管关系类型（REGULATES/MANAGES）',
  `valid_from`             datetime             DEFAULT NULL COMMENT '有效开始',
  `valid_to`               datetime             DEFAULT NULL COMMENT '有效结束',
  `status`                 tinyint     NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`              bigint               DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`            datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`              bigint               DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`            datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                bigint      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                int         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`regulatory_relation_id`),
  UNIQUE KEY `uk_regulator_org_deleted` (`regulator_org_id`, `regulated_org_id`, `deleted`),
  KEY                      `idx_regulated_status` (`regulated_org_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='组织监管关系表';

-- Audit class A
CREATE TABLE `sts_common_audience`
(
  `audience_id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '参与范围ID（主键）',
  `organization_id`          bigint       NOT NULL COMMENT '所属机构ID',
  `audience_name`            varchar(100) NOT NULL COMMENT '参与范围名称',
  `audience_kind`            varchar(16)  NOT NULL COMMENT '参与范围类别（TEMPLATE/BOUND）',
  `business_type`            varchar(32)           DEFAULT NULL COMMENT '业务对象类型（COURSE/OFFERING/LIVE/EXAM/TRAINING/SURVEY/SURVEY_INSTANCE；LIVE的业务对象为直播场次；TEMPLATE范围允许NULL，BOUND范围必填）',
  `business_id`              bigint                DEFAULT NULL COMMENT '业务对象ID（TEMPLATE范围允许NULL，BOUND范围必填）',
  `purpose`                  varchar(32)  NOT NULL COMMENT '用途（VISIBILITY/PARTICIPANT/REVIEWER/NOTIFICATION）',
  `resolve_mode`             varchar(16)  NOT NULL COMMENT '人员范围解析模式（DYNAMIC/FROZEN）',
  `is_internal_link_allowed` tinyint      NOT NULL COMMENT '是否允许内部人员通过链接参加（0否 1是）',
  `is_external_link_allowed` tinyint      NOT NULL COMMENT '是否允许外部人员通过链接参加（0否 1是）',
  `status`                   tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`                bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`              datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`                bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`              datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                  bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                  int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`audience_id`),
  UNIQUE KEY `uk_business_purpose_deleted` (`organization_id`, `business_type`, `business_id`, `purpose`, `deleted`),
  KEY                        `idx_org_kind` (`organization_id`, `audience_kind`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公共参与范围表';

-- Audit class B
CREATE TABLE `sts_common_audience_rule`
(
  `audience_rule_id` bigint      NOT NULL AUTO_INCREMENT COMMENT '参与范围规则ID（主键）',
  `audience_id`      bigint      NOT NULL COMMENT '参与范围ID',
  `decision`         varchar(16) NOT NULL COMMENT '决策（INCLUDE/EXCLUDE）',
  `subject_type`     varchar(16) NOT NULL COMMENT '范围对象类型（ALL/DEPT/TAG/POSITION/USER/AUDIENCE）',
  `subject_id`       bigint      NOT NULL DEFAULT 0 COMMENT '范围对象ID（subject_type=ALL时固定为0，其他类型必须为正数ID）',
  `create_by`        bigint               DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`      datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`audience_rule_id`),
  UNIQUE KEY `uk_audience_rule` (`audience_id`, `decision`, `subject_type`, `subject_id`),
  KEY                `idx_subject` (`subject_type`, `subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公共参与范围规则关联表';

-- Audit class B
CREATE TABLE `sts_common_audience_member`
(
  `audience_member_id` bigint      NOT NULL AUTO_INCREMENT COMMENT '参与范围成员ID（主键）',
  `audience_id`        bigint      NOT NULL COMMENT '参与范围ID',
  `user_id`            bigint      NOT NULL COMMENT '用户ID',
  `final_decision`     varchar(16) NOT NULL COMMENT '最终范围决策（INCLUDE包含/EXCLUDE排除）',
  `source_rule_id`     bigint   DEFAULT NULL COMMENT '来源规则ID',
  `resolved_time`      datetime    NOT NULL COMMENT '解析时间',
  `create_by`          bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`        datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`audience_member_id`),
  UNIQUE KEY `uk_audience_user` (`audience_id`, `user_id`),
  KEY                  `idx_user_decision` (`user_id`, `final_decision`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公共参与范围成员关联表';

-- Audit class B
CREATE TABLE `sts_common_resource_admin_permission`
(
  `admin_permission_id` bigint      NOT NULL AUTO_INCREMENT COMMENT '管理员权限ID（主键）',
  `organization_id`     bigint      NOT NULL COMMENT '所属机构ID',
  `resource_type`       varchar(32) NOT NULL COMMENT '资源类型（COURSE/CATEGORY/QUESTION_BANK/PAPER/EXAM/SURVEY）',
  `resource_id`         bigint      NOT NULL COMMENT '资源ID',
  `admin_org_member_id` bigint      NOT NULL COMMENT '资源管理员机构成员ID',
  `permission_code`     varchar(16) NOT NULL COMMENT '权限编码（EDIT/DELETE/STAT/ALL）',
  `create_by`           bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`         datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`admin_permission_id`),
  UNIQUE KEY `uk_resource_admin_permission` (`resource_type`, `resource_id`, `admin_org_member_id`, `permission_code`),
  KEY                   `idx_admin_resource` (`admin_org_member_id`, `resource_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公共资源管理员权限关联表';

-- Audit class A
CREATE TABLE `sts_common_resource_org_grant`
(
  `org_grant_id`            bigint      NOT NULL AUTO_INCREMENT COMMENT '机构授权ID（主键）',
  `owner_organization_id`   bigint      NOT NULL COMMENT '资源所属机构ID',
  `resource_type`           varchar(32) NOT NULL COMMENT '资源类型（COURSE/QUESTION_BANK/PAPER/EXAM/TRAINING_TYPE/TRAINING_STANDARD/TRAINING_TEMPLATE）',
  `resource_id`             bigint      NOT NULL COMMENT '资源ID',
  `grantee_organization_id` bigint      NOT NULL COMMENT '被授权机构ID',
  `grant_level`             varchar(16) NOT NULL COMMENT '授权级别（VIEW/USE/MANAGE）',
  `valid_from`              datetime             DEFAULT NULL COMMENT '有效开始',
  `valid_to`                datetime             DEFAULT NULL COMMENT '有效结束',
  `status`                  tinyint     NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`               bigint               DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`             datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`               bigint               DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`             datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                 bigint      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`                 int         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`org_grant_id`),
  UNIQUE KEY `uk_resource_grantee_deleted` (`resource_type`, `resource_id`, `grantee_organization_id`, `deleted`),
  KEY                       `idx_grantee_status` (`grantee_organization_id`, `status`, `valid_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公共资源机构授权表';

-- Audit class A
CREATE TABLE `sts_common_share_link`
(
  `share_link_id`   bigint       NOT NULL AUTO_INCREMENT COMMENT '分享链接ID（主键）',
  `organization_id` bigint       NOT NULL COMMENT '所属机构ID',
  `business_type`   varchar(32)  NOT NULL COMMENT '业务对象类型（COURSE/OFFERING/EXAM/TRAINING/SURVEY_INSTANCE；问卷链接一律挂投放实例）',
  `business_id`     bigint       NOT NULL COMMENT '业务对象ID',
  `link_token`      varchar(128) NOT NULL COMMENT '链接令牌',
  `link_mode`       varchar(16)  NOT NULL COMMENT '链接模式（PC/MOBILE/AUTO）',
  `poster_file_id`  bigint                DEFAULT NULL COMMENT '海报文件ID',
  `valid_from`      datetime              DEFAULT NULL COMMENT '有效开始',
  `valid_to`        datetime              DEFAULT NULL COMMENT '有效结束',
  `status`          tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`       bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`     datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`     datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`         int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`share_link_id`),
  UNIQUE KEY `uk_link_token_deleted` (`link_token`, `deleted`),
  KEY               `idx_business_status` (`business_type`, `business_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公共分享链接表';

-- Audit class A
CREATE TABLE `sts_common_reminder_rule`
(
  `reminder_rule_id`     bigint        NOT NULL AUTO_INCREMENT COMMENT '提醒规则ID（主键）',
  `organization_id`      bigint        NOT NULL COMMENT '所属机构ID',
  `business_type`        varchar(32)   NOT NULL COMMENT '业务对象类型（COURSE/OFFERING/EXAM/TRAINING/SURVEY_INSTANCE；问卷提醒一律挂投放实例）',
  `business_id`          bigint        NOT NULL COMMENT '业务对象ID',
  `reminder_type`        varchar(32)   NOT NULL COMMENT '提醒类型（NEW_TASK/NOT_STARTED/NOT_COMPLETED/STARTING/EXPIRING/RESULT）',
  `receiver_filter`      varchar(32)   NOT NULL COMMENT '接收人筛选（允许值待产品确认）',
  `receiver_audience_id` bigint                 DEFAULT NULL COMMENT '直接选择的提醒接收人范围ID',
  `template_content`     varchar(1000) NOT NULL COMMENT '模板内容',
  `start_time`           datetime               DEFAULT NULL COMMENT '开始时间',
  `end_time`             datetime               DEFAULT NULL COMMENT '结束时间',
  `interval_minutes`     int                    DEFAULT NULL COMMENT '间隔分钟',
  `max_send_count`       int                    DEFAULT NULL COMMENT '最大发送次数',
  `send_time`            time                   DEFAULT NULL COMMENT '发送时间',
  `status`               tinyint       NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`            bigint                 DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`          datetime               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`            bigint                 DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`          datetime               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`              bigint        NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`              int           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`reminder_rule_id`),
  KEY                    `idx_business_type` (`business_type`, `business_id`, `status`),
  KEY                    `idx_receiver_audience` (`receiver_audience_id`),
  KEY                    `idx_schedule` (`status`, `start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公共提醒规则表';

-- Audit class B
CREATE TABLE `sts_common_reminder_channel`
(
  `reminder_channel_id` bigint      NOT NULL AUTO_INCREMENT COMMENT '提醒渠道ID（主键）',
  `reminder_rule_id`    bigint      NOT NULL COMMENT '提醒规则ID',
  `channel_type`        varchar(16) NOT NULL COMMENT '渠道类型（APP/SMS/EMAIL）',
  `create_by`           bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`         datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`reminder_channel_id`),
  UNIQUE KEY `uk_rule_channel` (`reminder_rule_id`, `channel_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公共提醒渠道关联表';

-- Audit class A
CREATE TABLE `sts_common_message`
(
  `message_id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '消息ID（主键）',
  `organization_id`     bigint       NOT NULL COMMENT '所属机构ID',
  `message_type`        varchar(32)  NOT NULL COMMENT '消息类型（TASK/REMINDER/RESULT/SYSTEM）',
  `business_type`       varchar(32)           DEFAULT NULL COMMENT '业务对象类型（COURSE/OFFERING/EXAM/TRAINING/SURVEY_INSTANCE；问卷消息一律挂投放实例）',
  `business_id`         bigint                DEFAULT NULL COMMENT '业务对象ID',
  `title`               varchar(200) NOT NULL COMMENT '标题',
  `content`             text         NOT NULL COMMENT '内容',
  `channel_type`        varchar(16)  NOT NULL COMMENT '渠道类型（APP/SMS/EMAIL）',
  `planned_send_time`   datetime              DEFAULT NULL COMMENT '计划发送时间',
  `sent_time`           datetime              DEFAULT NULL COMMENT '发送时间',
  `send_status`         varchar(16)  NOT NULL COMMENT '发送状态（PENDING/SENDING/SENT/PARTIAL_FAILED/FAILED）',
  `provider_message_id` varchar(128)          DEFAULT NULL COMMENT '服务商消息标识',
  `create_by`           bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`         datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`           bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`         datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`             bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`             int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`message_id`),
  KEY                   `idx_org_send` (`organization_id`, `send_status`, `planned_send_time`),
  KEY                   `idx_business` (`business_type`, `business_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公共消息表';

-- Audit class A
CREATE TABLE `sts_common_message_recipient`
(
  `message_recipient_id` bigint      NOT NULL AUTO_INCREMENT COMMENT '消息接收人ID（主键）',
  `message_id`           bigint      NOT NULL COMMENT '消息ID',
  `user_id`              bigint      NOT NULL COMMENT '用户ID',
  `delivery_status`      varchar(16) NOT NULL COMMENT '投递状态（PENDING/DELIVERED/READ/FAILED）',
  `delivered_time`       datetime             DEFAULT NULL COMMENT '送达时间',
  `read_time`            datetime             DEFAULT NULL COMMENT '阅读时间',
  `failure_reason`       varchar(500)         DEFAULT NULL COMMENT '失败原因',
  `create_by`            bigint               DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`          datetime             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`            bigint               DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`          datetime             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`              bigint      NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`              int         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`message_recipient_id`),
  UNIQUE KEY `uk_message_user_deleted` (`message_id`, `user_id`, `deleted`),
  KEY                    `idx_user_read` (`user_id`, `read_time`, `delivery_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公共消息接收人表';

-- 人脸供应商能力本期不实施；不创建人脸配置、档案、策略、核验及修订表，待独立 OpenSpec 确认后再设计。

-- Audit class A
CREATE TABLE `sts_common_point_account`
(
  `point_account_id` bigint         NOT NULL AUTO_INCREMENT COMMENT '积分账户ID（主键）',
  `organization_id`  bigint         NOT NULL COMMENT '所属机构ID',
  `user_id`          bigint         NOT NULL COMMENT '用户ID',
  `balance`          decimal(12, 2) NOT NULL COMMENT '余额',
  `status`           tinyint        NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `create_by`        bigint                  DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`      datetime                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        bigint                  DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`      datetime                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`          bigint         NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`          int            NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`point_account_id`),
  UNIQUE KEY `uk_org_user_deleted` (`organization_id`, `user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公共积分账户表';

-- Audit class C
CREATE TABLE `sts_common_point_ledger`
(
  `point_ledger_id`  bigint         NOT NULL AUTO_INCREMENT COMMENT '积分流水ID（主键）',
  `organization_id`  bigint         NOT NULL COMMENT '所属机构ID',
  `point_account_id` bigint         NOT NULL COMMENT '积分账户ID',
  `business_type`    varchar(32)    NOT NULL COMMENT '业务对象类型（COURSE/OFFERING/EXAM/TRAINING）',
  `business_id`      bigint         NOT NULL COMMENT '业务对象ID',
  `change_type`      varchar(32)    NOT NULL COMMENT '积分变动类型（EARN/DEDUCT/ADJUST/REVOKE）',
  `change_amount`    decimal(12, 2) NOT NULL COMMENT '变动数额',
  `balance_after`    decimal(12, 2) NOT NULL COMMENT '余额变更后',
  `idempotency_key`  varchar(128)   NOT NULL COMMENT '幂等键',
  `occurred_time`    datetime       NOT NULL COMMENT '发生时间',
  `description`      varchar(500)   NOT NULL COMMENT '描述',
  `create_by`        bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`      datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`point_ledger_id`),
  UNIQUE KEY `uk_idempotency_key` (`idempotency_key`),
  KEY                `idx_account_time` (`point_account_id`, `occurred_time`),
  KEY                `idx_business` (`business_type`, `business_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公共积分流水记录表';

-- Audit class A. 统一承载题库题目、用户账号、参训人员和线下成绩四类批量导入（2026-07-21 需求方确认）。
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
  `import_batch_id` bigint        NOT NULL COMMENT '导入批次ID',
  `row_no`          int           NOT NULL COMMENT '数据行号',
  `field_name`      varchar(64) DEFAULT NULL COMMENT '字段名称',
  `error_code`      varchar(64)   NOT NULL COMMENT '错误编码',
  `error_message`   varchar(1000) NOT NULL COMMENT '错误消息',
  `raw_data_json`   json        DEFAULT NULL COMMENT '原始导入数据JSON',
  `create_by`       bigint      DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`     datetime    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`import_error_id`),
  KEY               `idx_batch_row` (`import_batch_id`, `row_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='公共导入错误记录表';

SET
FOREIGN_KEY_CHECKS = 1;
