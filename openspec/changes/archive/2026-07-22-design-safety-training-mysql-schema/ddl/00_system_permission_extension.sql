-- OpenSpec MySQL schema draft; not a migration and must not be executed directly.
-- Source: ../design.md
-- Prerequisite: backend/sql/schema-system.sql has been applied.

SET NAMES utf8mb4;
SET
FOREIGN_KEY_CHECKS = 0;

-- Extend the existing system department tree so it can serve training organizations.
-- A formal migration must first backfill organization_id/dept_code for existing rows,
-- and only then apply the NOT NULL and unique constraints shown by this target schema.
ALTER TABLE `us_sys_dept`
  ADD COLUMN `organization_id` bigint NOT NULL COMMENT '所属机构ID（对应sts_org_organization）' AFTER `dept_id`,
  ADD COLUMN `dept_code` varchar(64) NOT NULL COMMENT '机构内部门编码' AFTER `ancestors`,
  ADD COLUMN `dept_type` varchar(32) NOT NULL DEFAULT 'DEPARTMENT' COMMENT '部门类型（SCHOOL/COLLEGE/MAJOR/DEPARTMENT/CLASS/OTHER）' AFTER `dept_name`,
  ADD COLUMN `description` varchar(500) NOT NULL DEFAULT '' COMMENT '部门描述' AFTER `phone`,
  ADD UNIQUE KEY `uk_org_dept_code_deleted` (`organization_id`, `dept_code`, `deleted`),
  ADD KEY `idx_org_parent_status` (`organization_id`, `parent_id`, `status`, `sort_order`),
  ADD KEY `idx_org_dept_type` (`organization_id`, `dept_type`, `status`);

-- Extend the existing role model with organization ownership and data scope.
-- Functional permissions continue to use us_sys_role_menu -> us_sys_menu.perms.
ALTER TABLE `us_sys_role`
DROP INDEX `uk_role_code_deleted`,
  ADD COLUMN `organization_id` bigint NOT NULL COMMENT '所属机构ID（对应sts_org_organization）' AFTER `role_id`,
  ADD COLUMN `role_level` varchar(16) NOT NULL COMMENT '角色级别（ADMIN/TEACHER/STUDENT）' AFTER `role_code`,
  ADD COLUMN `is_builtin` tinyint NOT NULL DEFAULT 0 COMMENT '是否内置角色（0否 1是）' AFTER `role_level`,
  ADD COLUMN `data_scope` varchar(32) NOT NULL DEFAULT 'SELF' COMMENT '数据范围（ALL/ORGANIZATION/DEPT/DEPT_AND_CHILDREN/CUSTOM/SELF）' AFTER `is_builtin`,
  ADD COLUMN `description` varchar(500) NOT NULL DEFAULT '' COMMENT '角色描述' AFTER `status`,
  ADD UNIQUE KEY `uk_org_role_code_deleted` (`organization_id`, `role_code`, `deleted`),
  ADD KEY `idx_org_role_level` (`organization_id`, `role_level`, `status`),
  ADD KEY `idx_org_data_scope` (`organization_id`, `data_scope`, `status`);

-- Audit class B. Used only when us_sys_role.data_scope = CUSTOM.
CREATE TABLE `us_sys_role_dept`
(
  `role_dept_id`     bigint  NOT NULL AUTO_INCREMENT COMMENT '角色部门数据范围关联ID（主键）',
  `role_id`          bigint  NOT NULL COMMENT '角色ID（对应us_sys_role）',
  `dept_id`          bigint  NOT NULL COMMENT '部门ID（对应us_sys_dept）',
  `include_children` tinyint NOT NULL DEFAULT 1 COMMENT '是否包含下级部门（0否 1是）',
  `create_by`        bigint           DEFAULT NULL COMMENT '创建人用户ID',
  `create_time`      datetime         DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`role_dept_id`),
  UNIQUE KEY `uk_role_dept` (`role_id`, `dept_id`),
  KEY                `idx_dept_role` (`dept_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色自定义部门数据范围关联表';

SET
FOREIGN_KEY_CHECKS = 1;
