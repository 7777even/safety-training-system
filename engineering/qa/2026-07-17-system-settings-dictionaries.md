# QA：系统设置—字典管理

## 范围

OpenSpec Change：`2026-07-17-foundation-auth-organization-permission` 的 Task 7。

## 验证结果

- 字典类型与字典数据分别按 Contract 使用分页及 `query.*` 筛选参数。
- 类型和数据的编辑请求均携带 `version`；删除操作调用对应接口前经过“二次确认”。
- 字典目录仅读取 Contract 明确的 `version` 与类型编码键；按类型读取接口作为可复用 API 导出，未假设额外字段。
- `pnpm typecheck`、目标文件 eslint 检查与 `pnpm build:pc` 均通过。

## 已知限制

- 自动化测试框架按用户要求取消；未新增单元或组件测试。
- 未提供可访问后端环境与测试账号，实际字典管理联调待开发环境补充。
