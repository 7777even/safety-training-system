# QA：系统设置—组织管理

## 范围

OpenSpec Change：`2026-07-17-foundation-auth-organization-permission` 的 Task 6。

## 验证结果

- 组织树筛选使用 `/api/system/depts/tree` 的 `deptName`、`status` 参数。
- 新增、编辑、删除分别调用 `/api/system/depts` 与 `/api/system/depts/{deptId}`；编辑请求携带 `version`。
- 表单提交父级、状态、排序、负责人和电话字段，均来自部门 Contract。
- 父级选择使用独立完整部门树，不受当前列表筛选影响；删除前经过“二次确认”。
- 认证初始化会在 profile 请求前设置 token，受保护请求不再发送 `Bearer undefined`。
- 目标文件 eslint、`pnpm typecheck` 与 `pnpm build:pc` 均通过。

## 已知限制

- 自动化测试框架按用户要求取消；未新增单元或组件测试。
- 未提供可访问后端环境与测试账号，实际组织管理联调待开发环境补充。
