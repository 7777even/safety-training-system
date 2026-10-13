# Proposal：统一封面写入字段与受控读取协议

封面文件已统一存 `us_sys_file_info` 与私有对象。对外门面不统一：培训类型用 `imageFileId` 且无下载；课程学员列表吐
`coverFileId`；考试学员任务不给封面入口。调用方拿到 fileId 也无法安全直读私有对象。

## Why

列表把内部文件主键交给前端，既不能展示，又绕开业务对象授权。各域应按同一协议：管理端写入 `coverFileId`，读取走绑定业务对象的受控
GET，学员列表只给业务主键和相对 `coverUrl`。

## What Changes

- 管理端写入字段一律 `coverFileId`。培训类型 DTO/VO/OpenAPI 的 `imageFileId` 改为 `coverFileId`；表列
  `sts_training_type.image_file_id` 重命名为 `cover_file_id`。
- 受控读取：`GET /api/{域}/{业务对象}/{id}/cover?download=false`。路径只有业务 ID，不接受 `fileInfoId`。成功返回原始图片字节，不包
  Result。无图返回该域 404。
- 培训补齐：`GET /api/training/types/{trainingTypeId}/cover`（管理端）、
  `GET /api/training/learner/plans/{trainingPlanId}/cover`（学员）。
- 学员列表：课程去掉 `coverFileId` 改为 `coverUrl`；考试任务增加 `coverUrl`；培训任务增加 `trainingTypeId` 与 `coverUrl`
  。无封面时
  `coverUrl` 为 `null`。
- 管理端详情/创建/更新仍保留 `coverFileId` 以便编辑。课程快照内部字段不变。

## Capabilities

### New Capabilities

- `cover-access-protocol`：封面写入、受控 GET、学员列表 `coverUrl` 的统一约定。

### Modified Capabilities

- `training-api-contract`：类型字段改名、类型/学员计划封面 GET、学员任务封面字段。
- `course-learning-api`：学员课程列表/任务/学习课程 VO 用 `coverUrl`。
- `exam-learner-api`：学员考试任务增加 `coverUrl`。

## Impact

- 后端：培训类型/工作流、课程学员组装、考试学员任务；对应零 DB、Mapper IT 与 OpenAPI。
- 契约：`docs/api/training.openapi.json`、`course.openapi.json`、`exam.openapi.json`。
- DDL：`sts_training_type.image_file_id` → `cover_file_id`；无通用 `GET /files/{id}`、无新权限码。
- 仅后端；前端以 OpenAPI 为准另开。

## 功能项追溯

- 功能项 5：培训类型可维护封面图，学员任务需展示类型图。
- 功能项 48、56：我的课程、我的考试、我的培训各自展示封面，入口不同、授权绑业务对象。

## 目标

1. 学员端不再依赖 `coverFileId` 拼下载。
2. 培训类型封面可按类型 ID 或可见计划 ID 受控读取。
3. 课程/考试/培训学员列表封面协议一致。

## 非目标范围

- 不改 `frontend/`、Mock、联调或页面。
- 不迁移历史文件对象；已确认将培训类型列 `image_file_id` 重命名为 `cover_file_id`。
- 不提供通用 `GET /api/files/{fileId}`。
- 不把素材 HLS 封面并进同一 Controller。
- 本期不为试卷、问卷新增受控 GET；其管理端写入仍用 `coverFileId`。

## 前后端影响

仅后端；前端以 OpenAPI 为准另开。
