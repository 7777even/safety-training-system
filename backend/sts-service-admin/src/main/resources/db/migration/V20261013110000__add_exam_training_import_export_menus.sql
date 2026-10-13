-- 新增考试考生与培训参训人员 导入/导出 权限点（exam:exam:import/export、training:participant:import/export），
-- 并补授给对应能力持有角色：导入随写权限、导出随读权限，与既有候选/成员写入口径一致；不额外扩大范围。
-- 全部语句幂等：菜单走 UPSERT，绑定走 INSERT IGNORE（uk_role_menu）。

INSERT INTO `us_sys_menu`
(`menu_id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `icon`, `sort_order`, `is_visible`, `menu_type`,
 `module_id`, `deleted`, `version`)
VALUES (6190, 605, '考生导入', '', '', 'exam:exam:import', '', 8, 1, 'F', 9530, 0, 0),
       (6191, 605, '考生导出', '', '', 'exam:exam:export', '', 9, 1, 'F', 9530, 0, 0),
       (7590, 705, '参训导入', '', '', 'training:participant:import', '', 8, 1, 'F', 9530, 0, 0),
       (7591, 705, '参训导出', '', '', 'training:participant:export', '', 9, 1, 'F', 9530, 0, 0)
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

-- 考生导入：补授给已持有 exam:exam:edit 的角色。
INSERT IGNORE INTO `us_sys_role_menu` (`role_id`, `menu_id`, `create_by`, `create_time`)
SELECT DISTINCT rm.`role_id`, 6190, 2, NOW()
FROM `us_sys_role_menu` rm
WHERE rm.`menu_id` IN (SELECT `menu_id` FROM `us_sys_menu` WHERE `perms` = 'exam:exam:edit');

-- 考生导出：补授给已持有 exam:exam:list 的角色。
INSERT IGNORE INTO `us_sys_role_menu` (`role_id`, `menu_id`, `create_by`, `create_time`)
SELECT DISTINCT rm.`role_id`, 6191, 2, NOW()
FROM `us_sys_role_menu` rm
WHERE rm.`menu_id` IN (SELECT `menu_id` FROM `us_sys_menu` WHERE `perms` = 'exam:exam:list');

-- 参训导入：补授给已持有 training:participant:assign 的角色。
INSERT IGNORE INTO `us_sys_role_menu` (`role_id`, `menu_id`, `create_by`, `create_time`)
SELECT DISTINCT rm.`role_id`, 7590, 2, NOW()
FROM `us_sys_role_menu` rm
WHERE rm.`menu_id` IN (SELECT `menu_id` FROM `us_sys_menu` WHERE `perms` = 'training:participant:assign');

-- 参训导出：补授给已持有 training:participant:list 的角色。
INSERT IGNORE INTO `us_sys_role_menu` (`role_id`, `menu_id`, `create_by`, `create_time`)
SELECT DISTINCT rm.`role_id`, 7591, 2, NOW()
FROM `us_sys_role_menu` rm
WHERE rm.`menu_id` IN (SELECT `menu_id` FROM `us_sys_menu` WHERE `perms` = 'training:participant:list');
