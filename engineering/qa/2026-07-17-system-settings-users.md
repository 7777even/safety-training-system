# QA：系统设置—用户管理

## 范围

OpenSpec Change：`2026-07-17-foundation-auth-organization-permission` 的 Task 3。

## 验证结果

- 用户列表按 Contract 使用 `pageNum`、`pageSize` 和 `query.userCode`、`query.userName`、`query.phone`、`query.status`、`query.deptId` 查询。
- 新增、修改、删除和重置密码分别使用 `/api/system/users`、`/api/system/users/{userId}`、`/api/system/users/{userId}/reset-password`；修改请求携带 `version`。
- 删除与重置密码均在执行 API 前经过“二次确认”对话框；请求异常显示可读错误消息。
- 部门树和角色选择分别使用 `/api/system/depts/tree` 与分页角色接口，未硬编码角色语义。
- `pnpm typecheck`、目标文件 eslint 检查与 `pnpm build:pc` 均通过。

## 已知限制

- 自动化测试框架按用户要求取消；未新增单元或组件测试。
- 未提供可访问后端环境与测试账号，实际 CRUD 联调待开发环境补充。
