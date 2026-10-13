# Tasks：拒绝用户资料中的不可见 Unicode 字符

> 状态：已完成（2026-08-13）。
>
> Owner：lims

## Task 1：完成系统用户文本输入的不可见字符校验

- 状态：已完成（2026-08-13）
- Owner：lims
- 输入：确认后的 proposal/design；现有系统用户创建、更新、重置密码 DTO 与统一参数异常处理；现有 `system-admin.openapi.json`
- 输出：可复用不可见 Unicode 校验约束、用户 DTO 校验、密码摘要格式校验、同步后的 OpenAPI、自动化测试、QA/Retro 和进度记录
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：修改既有 `POST /api/system/users`、`PUT /api/system/users/{userId}`、
  `PUT /api/system/users/{userId}/reset-password` 的请求校验语义；路径、方法、响应结构和权限不变
- 数据影响：不修改数据库结构或历史数据，不创建 migration；非法请求在落库前拒绝
- 验收标准：
  - [x] 用户创建的 `userCode`、`userName`、`phone`、`password`、`memberType`、`email`、`jobTitle` 均拒绝已确认范围内的不可见
    Unicode 字符
  - [x] 用户更新的 `userName`、`phone`、`memberType`、`email`、`jobTitle` 均执行相同校验
  - [x] 创建及重置密码只接受 64 位小写十六进制 SHA-256 摘要，混入不可见字符或其他格式时拒绝
  - [x] `U+200B/U+200C/U+200D/U+2060/U+FEFF`、双向控制符、控制字符和异常 Unicode 分隔字符均被拒绝
  - [x] 正常中文、拉丁字母、数字、标点、Emoji、组合字符和普通半角空格不被本规则误拒绝
  - [x] 非法创建、更新和重置密码均返回 HTTP 400、业务码 1001，且不得调用 Service 或产生写入副作用
  - [x] 校验按 Unicode code point 执行，并由可复用 Jakarta Validation 约束集中实现，不在各业务类散落重复逻辑
  - [x] `docs/api/system-admin.openapi.json` 同步字段说明、密码格式、请求 example、错误语义和 `x-permission`
  - [x] Controller、Web Slice 与 Validator 测试按 Red → Green → Refactor 完成，Admin 零 DB 回归通过
  - [x] 规格符合性与代码质量双轴 Review 结论均为“通过”，QA、Retro、Task 与进度表证据一致
- 测试方式：
  - 先编写/修改目标自动化测试并观察预期失败，再以最小实现使其通过
  - 在 `backend/` 运行受影响的 Validator、`SysUserControllerTest`、`SysUserControllerWebSliceTest` 定向测试
  - 在 `backend/` 运行 `mvn -pl sts-service-admin -am test`
  - 本 Change 默认不运行 IT；如实现新增 Mapper/SQL 或改变持久化路径，必须补充并运行相关 MySQL IT
  - 运行 `openspec validate 2026-08-13-reject-invisible-user-input`
  - 运行 `git diff --check`

## 验收记录

- Red：Controller/Web Slice 23 个目标测试中新增 5 个行为断言按预期失败；Validator 测试因约束尚不存在而测试编译失败。
- Green：Validator、Controller、Web Slice 目标测试 29/29 通过；Admin 零 DB 全量 609/609 通过。
- Contract：`jq empty docs/api/system-admin.openapi.json` 通过；创建、更新、重置密码均同步 400/1001、字段说明和权限码。
- Spec：`openspec validate 2026-08-13-reject-invisible-user-input` 通过；`git diff --check` 通过。
- IT：未运行；实现未触及 Entity、Mapper、SQL、schema、索引或持久化事务。
- Review：规格符合性“通过”；代码质量“通过”。
- 工程记录：`engineering/qa/2026-08-13-reject-invisible-user-input.md`、
  `engineering/retro/2026-08-13-reject-invisible-user-input.md`。
