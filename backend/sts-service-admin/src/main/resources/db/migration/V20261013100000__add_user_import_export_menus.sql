-- 新增用户导入/导出权限（sys:user:import / sys:user:export）。
-- 与用户管理（pid=201，module=200）同级，补充 11 号功能项"按单/多组织 Excel(CSV) 批量导入导出用户账号"。
-- 仅补授给已持有兄弟权限 sys:user:add(1002) 的角色，与既有用户管理授权口径一致；不额外扩大范围。
-- 全部语句幂等：菜单走 UPSERT，绑定走 INSERT IGNORE（uk_role_menu）。

INSERT INTO `us_sys_menu`
(`menu_id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `icon`, `sort_order`, `is_visible`, `menu_type`,
 `module_id`, `deleted`, `version`)
VALUES (1006, 201, '用户导入', '', '', 'sys:user:import', '', 6, 1, 'F', 200, 0, 0),
       (1007, 201, '用户导出', '', '', 'sys:user:export', '', 7, 1, 'F', 200, 0, 0)
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

-- 与 sys:user:add(1002) 的持有角色对齐；重复执行由 uk_role_menu 兜底为无操作。
INSERT IGNORE INTO `us_sys_role_menu` (`role_id`, `menu_id`, `create_by`, `create_time`)
SELECT DISTINCT rm.`role_id`, m.`menu_id`, 2, NOW()
FROM `us_sys_role_menu` rm
         JOIN `us_sys_menu` m ON m.`menu_id` IN (1006, 1007)
WHERE rm.`menu_id` = 1002;
