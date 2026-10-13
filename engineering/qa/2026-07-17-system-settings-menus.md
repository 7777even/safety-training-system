# QA：系统设置—菜单管理

## 范围

OpenSpec Change：`2026-07-17-foundation-auth-organization-permission` 的 Task 5。

## 验证结果

- 菜单树筛选使用 Contract 定义的 `menuName`、`perms` 与 `isVisible`。
- 新增、编辑和删除分别调用 `/api/system/menus` 与 `/api/system/menus/{menuId}`；编辑请求携带 `version`。
- 单项可见性仅调用 `/api/system/menus/{menuId}/visibility` 并携带 `isVisible`、`version`；批量可见性仅调用 `/api/system/menus/batch-visibility` 并提交完整 `visibleIds` 与菜单版本映射。
- 表单父菜单数据独立从完整菜单树加载，不受当前列表筛选影响；批量树使用严格勾选以保留每个菜单的当前可见状态。
- 删除、单项可见性和批量可见性均在调用 API 前经过“二次确认”对话框，后端返回的冲突或业务错误会以可读消息呈现。
- `pnpm typecheck`、`pnpm lint`、`pnpm build:pc` 与 `git diff --check` 均通过。

## 已知限制

- 自动化测试框架按用户要求取消；未新增单元或组件测试。
- 未提供可访问后端环境与测试账号，实际菜单 CRUD 与可见性联调待开发环境补充。
