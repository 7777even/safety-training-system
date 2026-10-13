-- 同步已部署库的门户培训列表权限和教师教学任务菜单。
-- menus.json 只会插入缺失 ID，不能修复已有 9565 的空权限，故迁移以固定菜单 ID 幂等收敛。
INSERT INTO `us_sys_menu`
  (`menu_id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `icon`, `sort_order`, `is_visible`, `menu_type`, `module_id`, `deleted`)
VALUES
  (9564, 9527, '培训列表', '', '', 'training:learner:task', '', 0, 1, 'F', 1, 0),
  (9565, 800, '教学任务', 'teacher-tasks', 'business-center/training/teacher-tasks/index', 'training:teacher:task', 'Menu', 0, 1, 'C', 9530, 0)
ON DUPLICATE KEY UPDATE
  `parent_id` = VALUES(`parent_id`),
  `menu_name` = VALUES(`menu_name`),
  `path` = VALUES(`path`),
  `component` = VALUES(`component`),
  `perms` = VALUES(`perms`),
  `icon` = VALUES(`icon`),
  `sort_order` = VALUES(`sort_order`),
  `is_visible` = VALUES(`is_visible`),
  `menu_type` = VALUES(`menu_type`),
  `module_id` = VALUES(`module_id`),
  `deleted` = 0;
