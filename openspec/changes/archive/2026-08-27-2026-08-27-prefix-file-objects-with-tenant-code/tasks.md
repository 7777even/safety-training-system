# Tasks：文件对象统一增加租户编码根目录

> 状态：已人工确认（2026-08-27）；Task 1 已完成。
>
> Owner：lims
>
> 执行约束：确认后一次只执行一个 Task；按 Red → Green → Refactor 实施；测试和双轴 Review 通过后才能更新完成状态。

## Task 1：完成所有新文件对象的租户根目录纵向闭环

- 状态：已完成（2026-08-27）
- Owner：lims
- 输入：不可变机构编码、`TenantContext`、现有 Local/MinIO 上传、multipart、课程封面、source/derivative 生成与媒体读取链、历史
  `objectName` 记录。
- 输出：所有新对象统一使用 `{ORG_CODE}/{既有对象键}`，历史键保持可读可删，OpenAPI、自动化测试、QA/Retro 和进度记录同步。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：HTTP 路径、权限和错误码不变；更新 `docs/api/system-admin.openapi.json` 的 `objectName/fileUrl` 字段说明及示例，更新
  `docs/api/course.openapi.json` 的课程封面内部路径说明。
- 数据影响：无数据库结构或 Migration 变化；只影响新写入对象和新文件记录，历史对象及引用不迁移、不改写。
- 验收标准：
  - [x] 先增加自动化测试并观察新对象缺少机构编码根目录、历史兼容路径未覆盖的预期失败。
  - [x] 当前机构的所有新单文件、分片源文件、课程封面和派生文件对象键以该机构 `orgCode` 为首段。
  - [x] 机构编码只来自服务端租户上下文和机构主数据；缺失、无效或跨机构编码不得写入对象。
  - [x] `source`、`derivative`、`course-cover` 及其他业务目录的既有内部层级保持不变，年月仍为年/月，不新增日目录。
  - [x] 历史无租户前缀对象继续通过原数据库引用完成下载、预览、播放、删除和失败清理，不迁移、不重命名、不双写。
  - [x] 新旧 HLS manifest/segment、课程封面和素材源文件读取均有回归测试，且不得通过字符串硬拼导致重复前缀。
  - [x] `docs/api/system-admin.openapi.json` 与 `docs/api/course.openapi.json` 的字段说明、路径示例和兼容语义与实现一致。
  - [x] Admin 目标零 DB、受影响 MySQL IT、全量零 DB、完整 MySQL IT、OpenSpec strict、OpenAPI JSON 和 `git diff --check` 全部通过。
  - [x] 规格符合性与代码质量双轴 Review 结论均为“通过”，再更新 Task、进度、QA 和 Retro。
- 测试方式：按 TDD 运行租户对象键、Local/MinIO、通用上传、multipart、课程封面、转码 provider、媒体读取的目标测试；随后执行
  `mvn -pl sts-service-admin -am test`、`mvn -pl sts-service-admin -am -Pintegration-test test`、两份 OpenAPI JSON
  校验、OpenSpec strict 和差异检查。

### 完成记录

- Red：新增 `TenantObjectKeyService` 及 Local/MinIO、multipart、封面、HLS、派生对象键测试后，旧实现仍写出无机构根目录对象。
- Green：集中组件从 `TenantContext` 和机构主数据解析不可变 `orgCode`，所有新写入入口先生成既有相对键再加租户根目录；读取、删除和失败清理使用已保存完整键；HLS
  先查当前机构前缀再回退历史无前缀键，segment 从已登记 manifest 目录推导。
- 安全收口：机构缺失、编码非法或不安全相对键拒绝写入（4051），不退化为无前缀；课程封面同时接受历史无前缀键和当前机构前缀，拒绝其他机构前缀。
- 契约：`docs/api/system-admin.openapi.json` 的 `objectName`/`fileUrl` 说明与示例改为 `{ORG_CODE}/...` 并注明历史兼容；
  `docs/api/course.openapi.json` 封面内部路径更新为 `{ORG_CODE}/course-cover/{yyyy}/{MM}/...`。
- 验证：目标零 DB 54/54；Admin 全量零 DB 727（0 失败、1 个既有 qpdf 条件跳过）；`TenantObjectKeyServiceIT` 1/1 证明 DEMO
  主数据编码；完整 MySQL IT 193/193；OpenAPI JSON、OpenSpec strict、`git diff --check` 通过。
- 双轴 Review：规格符合性“通过”；代码质量“通过”。未修改数据库结构、权限模型、生产依赖或前端；未新增 bucket，未迁移历史对象。
- QA：`engineering/qa/2026-08-27-prefix-file-objects-with-tenant-code.md`；Retro：
  `engineering/retro/2026-08-27-prefix-file-objects-with-tenant-code.md`。
