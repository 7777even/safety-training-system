# QA：认证、组织与权限前端基础能力

## 范围

OpenSpec Change：`2026-07-17-foundation-auth-organization-permission` 的 Task 1 至 Task 7。

## 质量验证

- `pnpm typecheck`：通过，shared、PC Web、移动端 TypeScript 检查成功。
- `pnpm lint`：通过，全部 workspace 零错误、零警告。
- `pnpm build:pc`：通过，PC Web 生产构建成功。
- `pnpm build:mobile`：通过，移动端 H5 构建成功。
- `git diff --check`：通过，变更无空白错误。

## Contract 与代码复核

- 登录后的 profile 请求前，Store 已先写入 token，PC 请求拦截器会设置 `Authorization: Bearer {token}`；资料请求失败会清理临时会话，避免遗留无效 token。
- 用户、角色、菜单、组织、字典管理均只调用 `docs/api/system-admin.openapi.json` 已定义接口；新增和编辑请求使用对应 Contract 字段，编辑操作携带 `version`。
- 用户、角色、组织、字典和菜单的删除均在请求前二次确认；菜单可见性单项和批量接口分别调用，没有复用错误接口。
- 后台入口由 profile 返回的 `perms`、`menus` 控制展示，后台路由要求认证和管理权限；前端守卫仅改善体验，真实鉴权仍由后端负责。
- PC 静态门户和后台概览未调用课程、考试、培训、证书、统计、仿真或人脸识别 API。

## 未执行项

- 自动化测试框架按用户要求取消，未保留或执行单元、组件测试。
- 未提供可访问后端环境与测试账号，登录、CRUD 与权限展示的真实接口联调待开发环境补充。
- 独立 Reviewer 未执行：当前协作约束禁止派生子代理，本次为代码自审。
