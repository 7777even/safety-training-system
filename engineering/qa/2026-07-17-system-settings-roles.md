# QA：系统设置—角色管理

## 范围

OpenSpec Change：`2026-07-17-foundation-auth-organization-permission` 的 Task 4。

## 验证结果

- 角色分页筛选使用 Contract 定义的 `pageNum`、`pageSize`、`query.roleName`、`query.roleCode` 与 `query.status`。
- 详情、编辑和删除使用 `/api/system/roles/{roleId}`；编辑请求包含 `version`。
- 菜单授权树来源为 `/api/system/menus/tree`，新增和编辑保存体仅传 `menuIds`，未添加数据范围字段。
- 删除角色在执行 API 前经过“二次确认”对话框；异常以可读消息呈现。
- `pnpm typecheck`、目标文件 eslint 检查与 `pnpm build:pc` 均通过。

## 已知限制

- 自动化测试框架按用户要求取消；未新增单元或组件测试。
- 未提供可访问后端环境与测试账号，实际角色管理联调待开发环境补充。
