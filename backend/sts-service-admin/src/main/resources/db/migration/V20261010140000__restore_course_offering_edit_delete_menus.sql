-- 修复菜单/权限缺口：course:offering:edit 与 course:offering:delete 在已部署库中完全缺失。
--
-- 根因：init 种子 menus.json 把 9541/9542 定义为「开课编辑 / 开课删除」，但更早的
-- V20260920210000 把这两个 ID 复用给了监管「学习统计查看 / 学习统计导出」并覆盖写入了种子定义；
-- 监管统计菜单随后迁到 9810 模块（9812/9821/9822）并软删，于是 9541/9542 两条开课权限就再也没出现过。
-- 后果：CourseOfferingController 的编辑/删除、CourseAssessmentManagementController 的「配置阅卷人」
-- 对任何普通管理员恒 403（Sa-Token 1003 无此权限），含主观题的课程发布也会卡在阅卷人校验。
--
-- 本迁移按种子口径恢复 9541/9542（parent 9531 课程中心 / module 9530 应用中心），
-- 并只把这两条权限补授给「已持有兄弟权限 course:offering:list(9539)」的角色 —— 横向对照显示
-- 企业与培训机构管理员都已有 list/add/publish，属授权漏配，补齐同一套即可，不额外扩大到其它租户。
-- 全部语句幂等：菜单走 UPSERT，绑定走 INSERT IGNORE（uk_role_menu）。

INSERT INTO `us_sys_menu`
(`menu_id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `icon`, `sort_order`, `is_visible`, `menu_type`,
 `module_id`, `deleted`, `version`)
VALUES (9541, 9531, '开课编辑', '', '', 'course:offering:edit', '', 99, 1, 'F', 9530, 0, 0),
       (9542, 9531, '开课删除', '', '', 'course:offering:delete', '', 99, 1, 'F', 9530, 0, 0)
ON DUPLICATE KEY UPDATE `parent_id`   = VALUES(`parent_id`),
                        `menu_name`   = VALUES(`menu_name`),
                        `path`        = VALUES(`path`),
                        `component`   = VALUES(`component`),
                        `perms`       = VALUES(`perms`),
                        `icon`        = VALUES(`icon`),
                        `sort_order`  = VALUES(`sort_order`),
                        `is_visible`  = VALUES(`is_visible`),
                        `menu_type`   = VALUES(`menu_type`),
                        `module_id`   = VALUES(`module_id`),
                        `deleted`     = VALUES(`deleted`);

-- 与 course:offering:list(9539) 的持有角色对齐；重复执行由 uk_role_menu 兜底为无操作。
INSERT IGNORE INTO `us_sys_role_menu` (`role_id`, `menu_id`, `create_by`, `create_time`)
SELECT DISTINCT rm.`role_id`, m.`menu_id`, 2, NOW()
FROM `us_sys_role_menu` rm
         JOIN `us_sys_menu` m ON m.`menu_id` IN (9541, 9542)
WHERE rm.`menu_id` = 9539;
