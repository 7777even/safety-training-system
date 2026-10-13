-- 同步 M4「培训成果」菜单（问卷管理、培训档案、证书管理三叶子页面及按钮权限）。
-- 只插入已实现的 M4 能力：不迁移附件里的无参数成果页、评价记录、综合计算、
-- 证书签发/撤销/重发等未实现或已删除节点，也不复用与既有 9530 模块冲突的 ID。
-- menus.json 已包含同一份树，本迁移让已部署库幂等收敛到该结构。
INSERT INTO `us_sys_menu`
(`menu_id`, `parent_id`, `menu_name`, `path`, `component`, `perms`, `icon`, `sort_order`,
 `is_visible`, `menu_type`, `module_id`, `deleted`)
VALUES (9700, 9530, '培训成果', '/result', 'Layout', '', 'Document', 5, 1, 'M', 9530, 0),
       (9701, 9700, '问卷管理', 'survey', 'survey/index', 'survey:survey:list', '', 1, 1, 'C', 9530, 0),
       (9702, 9700, '培训档案', 'archive', 'archive/index', 'archive:training:list', '', 2, 1, 'C', 9530, 0),
       (9703, 9700, '证书管理', 'certificate', 'certificate/index', 'certificate:template:list', '', 3, 1, 'C', 9530, 0),
       (9704, 9703, '证书模板', 'templates', 'certificate/templates/index', 'certificate:template:list', '', 1, 1, 'C', 9530, 0),
       (9705, 9703, '签发规则', 'rules', 'certificate/rules/index', 'certificate:rule:list', '', 2, 1, 'C', 9530, 0),
       (9706, 9703, '已签发证书', 'certificates', 'certificate/certificates/index', 'certificate:cert:list', '', 3, 1, 'C', 9530, 0),
       (9710, 9701, '问卷查询', '', '', 'survey:survey:list', '', 1, 1, 'F', 9530, 0),
       (9711, 9701, '问卷新增', '', '', 'survey:survey:add', '', 2, 1, 'F', 9530, 0),
       (9712, 9701, '问卷编辑', '', '', 'survey:survey:edit', '', 3, 1, 'F', 9530, 0),
       (9713, 9701, '问卷删除', '', '', 'survey:survey:delete', '', 4, 1, 'F', 9530, 0),
       (9714, 9701, '投放查询', '', '', 'survey:instance:list', '', 5, 1, 'F', 9530, 0),
       (9715, 9701, '投放新增', '', '', 'survey:instance:add', '', 6, 1, 'F', 9530, 0),
       (9720, 9702, '档案查询', '', '', 'archive:training:list', '', 1, 1, 'F', 9530, 0),
       (9721, 9702, '档案更新', '', '', 'archive:training:create', '', 2, 1, 'F', 9530, 0),
       (9730, 9704, '模板查询', '', '', 'certificate:template:list', '', 1, 1, 'F', 9530, 0),
       (9731, 9704, '模板新增', '', '', 'certificate:template:add', '', 2, 1, 'F', 9530, 0),
       (9732, 9704, '模板编辑', '', '', 'certificate:template:edit', '', 3, 1, 'F', 9530, 0),
       (9733, 9704, '模板删除', '', '', 'certificate:template:delete', '', 4, 1, 'F', 9530, 0),
       (9734, 9704, '模板发布', '', '', 'certificate:template:publish', '', 5, 1, 'F', 9530, 0),
       (9740, 9705, '规则查询', '', '', 'certificate:rule:list', '', 1, 1, 'F', 9530, 0),
       (9741, 9705, '规则新增', '', '', 'certificate:rule:add', '', 2, 1, 'F', 9530, 0),
       (9742, 9705, '规则编辑', '', '', 'certificate:rule:edit', '', 3, 1, 'F', 9530, 0),
       (9743, 9705, '规则删除', '', '', 'certificate:rule:delete', '', 4, 1, 'F', 9530, 0),
       (9750, 9706, '证书查询', '', '', 'certificate:cert:list', '', 1, 1, 'F', 9530, 0)
ON DUPLICATE KEY UPDATE `parent_id`    = VALUES(`parent_id`),
                        `menu_name`    = VALUES(`menu_name`),
                        `path`         = VALUES(`path`),
                        `component`    = VALUES(`component`),
                        `perms`        = VALUES(`perms`),
                        `icon`         = VALUES(`icon`),
                        `sort_order`   = VALUES(`sort_order`),
                        `is_visible`   = VALUES(`is_visible`),
                        `menu_type`    = VALUES(`menu_type`),
                        `module_id`    = VALUES(`module_id`),
                        `deleted`      = 0;

-- 已部署库的 admin 角色在 init 时已绑定当时全部菜单；新增节点需同步授予，
-- 否则升级后管理员仍进不去 M4 页面。自定义角色的授权仍由角色管理界面人工决定。
INSERT IGNORE INTO `us_sys_role_menu` (`role_id`, `menu_id`)
SELECT `role`.`role_id`, `menu`.`menu_id`
FROM `us_sys_role` `role`
       JOIN `us_sys_menu` `menu` ON `menu`.`menu_id` IN
                                                     (9700, 9701, 9702, 9703, 9704, 9705, 9706,
                                                      9710, 9711, 9712, 9713, 9714, 9715,
                                                      9720, 9721,
                                                      9730, 9731, 9732, 9733, 9734,
                                                      9740, 9741, 9742, 9743,
                                                      9750)
WHERE `role`.`role_code` = 'admin'
  AND `role`.`deleted` = 0
  AND `menu`.`deleted` = 0;
