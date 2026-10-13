-- 门户「我的证书」正式节点（学员端入口），落在门户 #1 下，与学习中心 / 考试中心 / 培训中心同级。
-- 背景：/portal/certificates 既不在前端静态路由，也不在 PORTAL_INTERNAL_ROUTE_DEFINITIONS，
-- 只能由菜单节点注册；种子与历史迁移都缺该节点，学员点「我的证书」时路由未注册、入口断链。
-- 组件路径 portal/certificates/index 由前端 resolveMenuComponent 解析为 src/views/portal/certificates/index.vue，
-- 相对 path 'certificates' 由 resolveFullPath 补全为 /portal/certificates。
-- 权限沿用学员既有权限 training:learner:task（与证书「本人证书」端点的 OR 条件一致）。
INSERT INTO `us_sys_menu`
(`menu_id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `icon`, `sort_order`,
 `is_visible`, `menu_type`, `module_id`, `deleted`)
VALUES (9751, 1, '我的证书', 'certificates', 'portal/certificates/index', 'training:learner:task', '', 5, 1, 'C', 1, 0)
ON DUPLICATE KEY UPDATE `parent_id`  = VALUES(`parent_id`),
                        `menu_name`  = VALUES(`menu_name`),
                        `path`       = VALUES(`path`),
                        `component`  = VALUES(`component`),
                        `perms`      = VALUES(`perms`),
                        `icon`       = VALUES(`icon`),
                        `sort_order` = VALUES(`sort_order`),
                        `is_visible` = VALUES(`is_visible`),
                        `menu_type`  = VALUES(`menu_type`),
                        `module_id`  = VALUES(`module_id`),
                        `deleted`    = 0;

-- 学员角色需显式授予该菜单，否则节点存在但门户看不到入口。
-- 按 role_level = 'STUDENT' 授权，覆盖库内全部学员角色（与门户既有页面的授权范围保持一致）。
INSERT IGNORE INTO `us_sys_role_menu` (`role_id`, `menu_id`)
SELECT `role`.`role_id`, 9751
FROM `us_sys_role` `role`
WHERE `role`.`deleted` = 0
  AND `role`.`role_level` = 'STUDENT';
