-- M5 超范围表/列按中山50最小闭环标注；写入内部固定学时不足规则。不删表、列、索引和历史数据。

ALTER TABLE `sts_regulation_warning`
  COMMENT = '监管预警表；当前仅用于培训计划学时不足待查看/已查看';

ALTER TABLE `sts_regulation_warning`
  MODIFY COLUMN `warning_status` varchar (16) NOT NULL COMMENT '预警状态（UNREAD待查看/READ已查看/ENDED内部结束；历史 OPEN/VIEWED/PROCESSING/CLOSED/IGNORED 保留不改写）',
  MODIFY COLUMN `current_assignee_id` bigint DEFAULT NULL COMMENT '【暂时隐藏】原用于：当前负责人用户ID；当前中山50最小闭环不读取、不写入、不展示；后续启用须新建并确认 OpenSpec Change',
  MODIFY COLUMN `warning_level` varchar (16) NOT NULL COMMENT '【内部保留】用于：固定内部级别 WARN；不提供配置页面或对外操作',
  MODIFY COLUMN `closed_time` datetime DEFAULT NULL COMMENT '【内部保留】用于：计划修正或取消后结束当前预警；不提供人工关闭';

ALTER TABLE `sts_regulation_warning_evidence`
  COMMENT = '【内部保留】用于：保存学时对比证据；不提供独立页面或对外操作';

ALTER TABLE `sts_regulation_warning_rule`
  COMMENT = '【内部保留】用于：固定系统学时不足规则；规则管理字段不提供独立页面或对外操作';

ALTER TABLE `sts_regulation_warning_condition`
  COMMENT = '【暂时隐藏】原用于：可配置预警条件；当前中山50最小闭环不启用，不得作为当前页面、API或业务规则依据；后续启用须新建并确认 OpenSpec Change';

ALTER TABLE `sts_regulation_warning_action`
  COMMENT = '【暂时隐藏】原用于：预警分派/评论/关闭等流水；当前中山50最小闭环不启用，不得作为当前页面、API或业务规则依据；后续启用须新建并确认 OpenSpec Change';

ALTER TABLE `sts_ops_export_job`
  COMMENT = '【暂时隐藏】原用于：运营异步导出任务中心；当前中山50最小闭环不启用，不得作为当前页面、API或业务规则依据；后续启用须新建并确认 OpenSpec Change';

INSERT INTO `sts_regulation_warning_rule`
(`organization_id`, `rule_code`, `rule_name`, `warning_type`, `warning_level`, `status`, `description`)
SELECT o.`organization_id`,
       'PLAN_HOURS_INSUFFICIENT',
       '培训计划学时不足',
       'PLAN_TOTAL_HOURS',
       'WARN',
       1,
       '【内部保留】用于：固定学时不足规则；不提供独立页面或对外操作'
FROM `sts_org_organization` o
WHERE o.`org_code` = 'REGULATOR'
  AND o.`deleted` = 0
  AND NOT EXISTS (SELECT 1
                  FROM `sts_regulation_warning_rule` r
                  WHERE r.`rule_code` = 'PLAN_HOURS_INSUFFICIENT'
                    AND r.`deleted` = 0);

-- 超范围菜单/权限对已部署库软删；menus-back.json 只插入缺失 ID。
UPDATE `us_sys_menu`
SET `deleted`    = `menu_id`,
    `is_visible` = 0
WHERE `deleted` = 0
  AND (`menu_id` IN (902, 903, 9102, 9103, 9201, 9202, 9301, 9302, 952, 9511, 9512, 9513, 9514, 9521)
  OR `perms` IN (
                 'ops:stats:view',
                 'ops:stats:drilldown',
                 'ops:export:list',
                 'ops:export:create',
                 'ops:workbench:learner',
                 'ops:workbench:teacher',
                 'regulation:rule:list',
                 'regulation:rule:add',
                 'regulation:rule:edit',
                 'regulation:rule:delete',
                 'regulation:warning:detect'
    ));

DELETE
FROM `us_sys_role_menu`
WHERE `menu_id` IN (902, 903, 9102, 9103, 9201, 9202, 9301, 9302, 952, 9511, 9512, 9513, 9514, 9521);

UPDATE `us_sys_menu`
SET `menu_name` = '企业工作台'
WHERE `menu_id` = 900
  AND `deleted` = 0;

UPDATE `us_sys_menu`
SET `menu_name` = '监管工作台',
    `path`      = 'workbench',
    `component` = 'regulation/workbench/index'
WHERE `menu_id` = 951
  AND `deleted` = 0;

UPDATE `us_sys_menu`
SET `menu_name` = '学时预警'
WHERE `menu_id` = 953
  AND `deleted` = 0;

UPDATE `us_sys_menu`
SET `menu_name` = '标记已查看',
    `sort_order`= 2
WHERE `menu_id` = 9523
  AND `deleted` = 0;

UPDATE `us_sys_menu`
SET `sort_order` = 1
WHERE `menu_id` = 9522
  AND `deleted` = 0;

INSERT INTO `us_sys_menu`
(`menu_id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `icon`, `sort_order`, `is_visible`, `menu_type`,
 `module_id`, `deleted`)
VALUES (9502, 951, '机构统计导出', '', '', 'regulation:learning:export', '', 2, 1, 'F', 950, 0),
       (954, 950, '数据中心', 'learning-stats', 'regulation/learning/index', 'regulation:learning:view', '', 2, 1, 'C',
        950, 0),
       (9541, 954, '学习统计查看', '', '', 'regulation:learning:view', '', 1, 1, 'F', 950, 0),
       (9542, 954, '学习统计导出', '', '', 'regulation:learning:export', '', 2, 1, 'F', 950, 0),
       (9524, 953, '预警导出', '', '', 'regulation:warning:export', '', 3, 1, 'F', 950, 0) ON DUPLICATE KEY
UPDATE `parent_id` =
VALUES (`parent_id`), `menu_name` =
VALUES (`menu_name`), `path` =
VALUES (`path`), `component` =
VALUES (`component`), `perms` =
VALUES (`perms`), `sort_order` =
VALUES (`sort_order`), `is_visible` = 1, `menu_type` =
VALUES (`menu_type`), `module_id` =
VALUES (`module_id`), `deleted` = 0;
