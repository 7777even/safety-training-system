# Proposal：教师任务返回场次乐观锁 version

`POST /training/offline-sessions/{id}/start|complete|cancel` 要求回传场次 `version`。教师任务列表和详情未返回该字段，教师无法从合法读入口取得令牌。

## Why

状态推进使用乐观锁。教师只有 `training:teacher:task`，不能调用管理端场次详情。写接口要 version、读接口不给，闭环断开。

## What Changes

- `TrainingTeacherTaskVO` / `TrainingTeacherTaskDetailVO` 返回场次 `version`。
- 列表与详情组装从场次实体映射。
- OpenAPI 同步字段、注释与 example。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `training-execution-loop`：教师任务读模型补上状态推进所需的乐观锁版本。

## Impact

- 后端：教师任务列表/详情 VO 与组装。
- 契约：`docs/api/training.openapi.json`。
- 无 DDL。
- 仅后端；前端以 OpenAPI 为准另开。

## 非目标

不取消写接口的 version 校验。不让教师调用管理端场次详情。不改学员场次接口。不改前端。
