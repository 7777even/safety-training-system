-- 清理 2026-09-21 经菜单管理界面手工创建、未登记进 menus.json / 迁移 / 工程记录的 M4 菜单子树（9566～9605）。
-- 清理理由：该段与正式节点（9700 段「培训成果」、9751 门户「我的证书」）语义重复，
-- 且经前端 resolveFullPath 归一后落在同一路由路径（遗留用绝对 /admin/result，正式用相对 path），
-- 一旦被勾选角色就会出现重复动态路由与重复侧边栏项。
-- 该段从未绑定任何角色（us_sys_role_menu 零命中），软删不影响任何用户的可见性。
-- 写法与库内既有失效行保持一致：deleted 置为行自身 menu_id（而非 1），统一逻辑删除语义。
-- 条件 deleted = 0 使本迁移可重复执行。
UPDATE `us_sys_menu`
SET `deleted` = `menu_id`
WHERE `menu_id` BETWEEN 9566 AND 9605
  AND `deleted` = 0;
