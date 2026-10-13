-- =================================================================================
-- 标签 / 岗位及其用户关联（BASE-02）最新完整 DDL
--
-- 数据库变化必须同步更新本文件、backend/sts-service-admin/src/main/resources/db/migration/ 中的 Admin 增量和 init schema version。
-- 本文件供 admin-init 首次建库，并由构建拷入 sts-service-admin 集成测试 classpath。
-- 含 sts_org_organization 租户主表与租户种子行（多租户改造后由本文件承载，
-- 它是租户体系的主数据，归属机构域而非课程域）。
--
-- 监管关系表 sts_org_regulatory_relation 已删除：多租户改造后监管平台无条件覆盖全部企业租户，
-- 不再需要显式监管关系配置。详见 design.md §4.1。
-- 机构成员表 sts_org_member 已删除：用户归属由 us_sys_user.organization_id 表达，
-- 档案字段并入 us_sys_user，标签/岗位直接关联 user_id。详见 design.md §5.3。
-- =================================================================================

SET NAMES utf8mb4;
SET
FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 1. 租户主数据表 (sts_org_organization)
--    每个企业或培训机构是一个业务租户，另有至多一个 REGULATOR 监管平台租户；租户之间无层级关系。
-- ----------------------------
CREATE TABLE `sts_org_organization`
(
  `organization_id` bigint       NOT NULL AUTO_INCREMENT COMMENT '租户ID（主键），即全系统 tenant_id',
  `org_code`        varchar(64)  NOT NULL COMMENT '企业编码，登录入口标识，创建后不可修改',
  `org_name`        varchar(100) NOT NULL COMMENT '租户名称',
  `org_type` varchar(32) NOT NULL COMMENT '租户类型（ENTERPRISE企业/TRAINING_INSTITUTION培训机构/REGULATOR监管平台）；企业与培训机构可由管理员明确改写，不得按名称推断',
  `contact_user_id` bigint                DEFAULT NULL COMMENT '联系人用户ID',
  `status`          tinyint      NOT NULL COMMENT '启用状态（0禁用 1正常）',
  `description`     varchar(500) NOT NULL COMMENT '描述',
  `create_by`       bigint                DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`     datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       bigint                DEFAULT NULL COMMENT '更新人用户ID',
  `update_time`     datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         bigint       NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删除，非0为主键ID）',
  `version`         int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`organization_id`),
  UNIQUE KEY `uk_org_code_deleted` (`org_code`, `deleted`),
  KEY               `idx_type_status` (`org_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='租户主数据表';

-- 种子租户：监管平台租户全系统唯一，由此处建立，不通过业务 API 创建；
-- DEMO 为开发与集成测试使用的示例企业租户。
INSERT INTO `sts_org_organization`
(`org_code`, `org_name`, `org_type`, `contact_user_id`, `status`, `description`)
VALUES ('REGULATOR', '监管平台', 'REGULATOR', NULL, 1, '监管平台租户，可查看全部企业租户数据，不承载课程考试等业务'),
       ('DEMO', '示例企业', 'ENTERPRISE', NULL, 1, '开发与集成测试使用的示例企业租户');

-- ----------------------------
-- 标签表 (sts_org_tag)
-- ----------------------------
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

CREATE TABLE `sts_org_user_tag`
(
  `user_tag_id`     bigint NOT NULL AUTO_INCREMENT COMMENT '用户标签ID（主键）',
  `organization_id` bigint NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `user_id`         bigint NOT NULL COMMENT '用户ID（对应us_sys_user）',
  `tag_id`          bigint NOT NULL COMMENT '标签ID',
  `create_by`       bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`     datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`user_tag_id`),
  KEY               `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_user_tag` (`user_id`, `tag_id`),
  KEY               `idx_tag_user` (`tag_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户标签关联表';

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

CREATE TABLE `sts_org_user_position`
(
  `user_position_id` bigint  NOT NULL AUTO_INCREMENT COMMENT '用户岗位ID（主键）',
  `organization_id`  bigint  NOT NULL COMMENT '所属租户ID（多租户隔离列，由租户插件自动填充与过滤）',
  `user_id`          bigint  NOT NULL COMMENT '用户ID（对应us_sys_user）',
  `position_id`      bigint  NOT NULL COMMENT '岗位ID',
  `is_primary`       tinyint NOT NULL COMMENT '是否主要记录（0否 1是）',
  `create_by`        bigint   DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`      datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`user_position_id`),
  KEY                `idx_organization_id` (`organization_id`),
  UNIQUE KEY `uk_user_position` (`user_id`, `position_id`),
  KEY                `idx_position_user` (`position_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户岗位关联表';


SET
FOREIGN_KEY_CHECKS = 1;

-- 第七批 T1：人员手工特征与条件用户组，不保存自动画像或任务名单。
CREATE TABLE sts_person_profile (
  profile_id bigint NOT NULL AUTO_INCREMENT COMMENT '特征ID',
  organization_id bigint NOT NULL COMMENT '租户ID',
  user_id bigint NOT NULL COMMENT '人员ID',
  birth_date date DEFAULT NULL COMMENT '出生日期',
  gender varchar(16) DEFAULT NULL COMMENT '性别',
  occupation varchar(1000) DEFAULT NULL COMMENT '职业',
  education varchar(1000) DEFAULT NULL COMMENT '学历',
  major varchar(1000) DEFAULT NULL COMMENT '专业',
  work_experience varchar(1000) DEFAULT NULL COMMENT '从业经历',
  specialty varchar(1000) DEFAULT NULL COMMENT '专长',
  qualification varchar(1000) DEFAULT NULL COMMENT '职业资格',
  reward_penalty_note varchar(1000) DEFAULT NULL COMMENT '人工人员奖惩说明',
  work_content varchar(1000) DEFAULT NULL COMMENT '工作内容',
  work_skills varchar(1000) DEFAULT NULL COMMENT '工作技能',
  create_by bigint DEFAULT NULL COMMENT '创建人',
  create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by bigint DEFAULT NULL COMMENT '更新人',
  update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted bigint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (profile_id),
  UNIQUE KEY uk_person_profile (organization_id,user_id,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人员手工特征';

CREATE TABLE sts_person_group (
  group_id bigint NOT NULL AUTO_INCREMENT COMMENT '用户组ID',
  organization_id bigint NOT NULL COMMENT '租户ID',
  group_name varchar(100) NOT NULL COMMENT '名称',
  status tinyint NOT NULL COMMENT '0停用1启用',
  conditions_json json NOT NULL COMMENT '固定分类条件',
  create_by bigint DEFAULT NULL COMMENT '创建人',
  create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by bigint DEFAULT NULL COMMENT '更新人',
  update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted bigint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version int NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (group_id),
  UNIQUE KEY uk_person_group (organization_id,group_name,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='条件用户组';
