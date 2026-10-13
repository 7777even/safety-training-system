# Tasks：学员课程简介与受控附件下载

> 状态：已完成（2026-08-13）。
>
> Owner：lims

## Task 1：完成学员课程详情聚合与受控附件下载能力

- 状态：已完成（2026-08-13）
- Owner：lims
- 输入：确认后的 proposal/design；既有报名、开课、冻结课程版本、素材和源文件事实；现有私有存储访问能力
- 输出：扩展后的本人课程学习详情、课程学习域受控附件下载接口、后端权限与状态实现、`docs/api/course.openapi.json`
  、自动化测试、QA/Retro 和进度记录
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：修改 `GET /course/learning/enrollments/{enrollmentId}`；新增
  `GET /course/learning/enrollments/{enrollmentId}/download-assets/{assetId}/file`
- 数据影响：只读既有报名、开课、课程版本快照、素材和文件数据；不改数据库，不创建 migration
- 验收标准：
  - [x] 详情接口保持仅报名本人可访问，并从开课冻结版本返回 `course` 课程简介
  - [x] 详情接口按冻结附件顺序返回 `downloadAssets`，素材与文件字段来自当前机构内的有效事实
  - [x] `downloadable`、`downloadUrl` 和 `unavailableReason` 与下载模式、冻结附件、素材处理状态及文件记录一致
  - [x] 学员附件标识使用冻结快照已有的 `assetId`，不依赖可变的当前课程附件配置 ID
  - [x] 下载接口仅允许报名本人下载冻结课程中允许下载的附件，不授予后台课程或素材权限
  - [x] 下载响应不暴露对象存储地址，提供安全文件名、内容类型并正确处理完整请求、单 Range、404 和 416
  - [x] 他人报名、无效学习状态、课程禁止下载、附件不属于冻结课程、素材失效和文件不存在均被后端拒绝，且不会读取存储对象
  - [x] `docs/api/course.openapi.json` 按真实 Controller 分类，字段均有中文说明，参数/成功响应有 example，包含
    `x-permission`、HTTP 状态和业务错误码
  - [x] Controller Web Slice、本人报名校验和各附件不可用分支测试按 Red → Green → Refactor 完成并通过
  - [x] 规格符合性与代码质量双轴 Review 结论均为“通过”，QA、Retro、OpenSpec Task 和进度表证据一致
- 测试方式：
  - 按 TDD 先运行目标测试并观察因新 VO/服务/接口尚不存在或行为缺失而失败，再最小实现
  - 在 `backend/` 运行受影响的零 DB 定向测试，并运行 `mvn -pl sts-service-admin -am test`
  - 本 Change 默认不运行无相关持久化风险的 IT；若实现新增 Mapper SQL 或持久化路径，则运行
    `mvn -pl sts-service-admin -am -Pintegration-test test`
  - `openspec validate 2026-08-13-add-learner-course-detail-download`
  - `git diff --check`
- 验证结果（2026-08-13）：
  - Red：新增测试首次因课程简介/附件 VO、资源组装器、下载 Service/Controller 和 `sourceFile` 不存在而在 `testCompile`
    失败，已观察预期失败
  - Green：最终目标测试 40 个通过，覆盖冻结聚合、本人边界、下载状态、文件流响应与 Web Slice 错误外壳
  - 全量零 DB：585 个通过，0 失败、0 错误、0 跳过
  - IT：未运行；本 Change 未修改 Entity、Mapper、自定义 SQL、schema、索引或持久化事务
  - OpenAPI JSON、OpenSpec 校验与 `git diff --check` 均通过
  - 规格符合性 Review：通过；代码质量 Review：通过
