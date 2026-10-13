-- M5 PC 菜单使用独立 ID；旧 M5 菜单退出导航，避免与后续课程菜单 ID 冲突。
DELETE rm FROM us_sys_role_menu rm JOIN us_sys_menu m ON m.menu_id = rm.menu_id WHERE m.module_id IN (900, 950);
UPDATE us_sys_menu SET deleted = menu_id, is_visible = 0 WHERE module_id IN (900, 950) AND deleted = 0;

INSERT INTO us_sys_menu (menu_id, parent_id, menu_name, path, component, perms, icon, sort_order, is_visible, menu_type, module_id, deleted) VALUES
(9800, 0, '企业工作台', '/admin/ops', 'Layout', '', 'DataAnalysis', 4, 1, 'D', 9800, 0),
(9801, 9800, '企业工作台', 'workbench', 'ops/workbench/index', 'ops:workbench:enterprise', '', 1, 1, 'C', 9800, 0),
(9810, 0, '园区监管', '/admin/regulation', 'Layout', '', 'DataAnalysis', 5, 1, 'D', 9810, 0),
(9811, 9810, '监管工作台', 'workbench', 'regulation/workbench/index', 'regulation:overview:view', '', 1, 1, 'C', 9810, 0),
(9812, 9810, '数据中心', 'learning-stats', 'regulation/learning/index', 'regulation:learning:view', '', 2, 1, 'C', 9810, 0),
(9813, 9810, '学时预警', 'warnings', 'regulation/warnings/index', 'regulation:warning:list', '', 3, 1, 'C', 9810, 0),
(9814, 9810, '机构管理', 'institutions', 'system/org/organization/index', 'system:tenant:list', '', 4, 1, 'C', 9810, 0),
(9821, 9811, '机构统计导出', '', '', 'regulation:learning:export', '', 1, 1, 'F', 9810, 0),
(9822, 9812, '学习统计导出', '', '', 'regulation:learning:export', '', 1, 1, 'F', 9810, 0),
(9823, 9813, '标记已查看', '', '', 'regulation:warning:action', '', 1, 1, 'F', 9810, 0),
(9824, 9813, '预警导出', '', '', 'regulation:warning:export', '', 2, 1, 'F', 9810, 0),
(9825, 9814, '机构新增', '', '', 'system:tenant:add', '', 1, 1, 'F', 9810, 0),
(9826, 9814, '机构编辑', '', '', 'system:tenant:edit', '', 2, 1, 'F', 9810, 0)
ON DUPLICATE KEY UPDATE parent_id = VALUES(parent_id), menu_name = VALUES(menu_name), path = VALUES(path), component = VALUES(component), perms = VALUES(perms), icon = VALUES(icon), sort_order = VALUES(sort_order), is_visible = VALUES(is_visible), menu_type = VALUES(menu_type), module_id = VALUES(module_id), deleted = 0;

-- 现有租户管理员获得本机构工作台；监管管理员获得监管目录及当前固定权限。
INSERT IGNORE INTO us_sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id FROM us_sys_role r
JOIN sts_org_organization o ON o.organization_id = r.organization_id AND o.deleted = 0
JOIN us_sys_menu m ON m.menu_id IN (9800, 9801) AND m.deleted = 0
WHERE r.role_code = 'admin' AND r.deleted = 0 AND o.org_type <> 'REGULATOR';

INSERT IGNORE INTO us_sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id FROM us_sys_role r
JOIN sts_org_organization o ON o.organization_id = r.organization_id AND o.deleted = 0
JOIN us_sys_menu m ON m.module_id = 9810 AND m.deleted = 0
WHERE r.role_code = 'admin' AND r.deleted = 0 AND o.org_type = 'REGULATOR';

-- 只读快捷入口还需要其真实 PC 菜单祖先，否则拥有权限码但无法注册路由。
INSERT IGNORE INTO us_sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id FROM us_sys_role r
JOIN sts_org_organization o ON o.organization_id = r.organization_id AND o.deleted = 0
JOIN us_sys_menu m ON m.menu_id IN (9530, 300, 9531, 9700, 9703, 9706) AND m.deleted = 0
WHERE r.role_code = 'admin' AND r.deleted = 0 AND o.org_type = 'REGULATOR';
