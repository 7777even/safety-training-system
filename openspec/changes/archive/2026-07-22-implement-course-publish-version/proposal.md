## Why

课程草稿编排（分类、设置、课程/章节/内容项）已交付，但开课与学习必须绑定**不可变发布版本**。没有 `sts_course_version`
，后续开课无法固定内容快照，历史学习会随草稿改动而漂移。按 `course-module-task-plan` 步骤 4，现在补齐发布版本能力。

## What Changes

- 新增课程不可变发布版本表 `sts_course_version`（migration + Entity）。
- 提供课程发布能力：事务内生成递增 `version_no`、完整 `content_snapshot_json`、`snapshot_hash`，更新
  `sts_course_course.current_version_id` / `publish_status` / `published_time`。
- 提供版本列表与只读详情查询；发布版本禁止更新/删除。
- 发布前校验：课程存在、至少一章、ASSET 引用仍可用（同机构 + SUCCEEDED）；相同内容重复发布幂等（哈希相同则复用当前版本）。
- 可选下线：将课程 `publish_status` 置为 `OFFLINE`（不删除历史版本）。
- 同步 `docs/api/` OpenAPI 契约与权限点（`course:course:publish` 等）。
- **不包含**：开课/报名/学习（步骤 5）、素材上传转码（步骤 1）、前端页面。

## Capabilities

### New Capabilities

- `course-publish-version`: 课程工作草稿的正式发布、不可变版本查询与下线。

### Modified Capabilities

- （无独立 main specs；行为变更相对已归档的草稿编排能力：草稿可继续编辑，发布产生只读版本。）

## Impact

- 后端：`com.sts.course.*`（Entity/Mapper/Service/Controller）、`menus.json` 权限、错误码 50xx 扩展。
- 数据库：`database/migration` + `backend/sql/schema-course.sql`。
- API：`docs/api/course-config-authoring.openapi.json` 增补或新建发布相关 paths（本 Change 写入 `docs/api/`）。
- 依赖：已有草稿编排与 `AssetReferenceService`；默认机构方案 A 仍适用。
- 后续：步骤 5 开课固定引用 `course_version_id`。
