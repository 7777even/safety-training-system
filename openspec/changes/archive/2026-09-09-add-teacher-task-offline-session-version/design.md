# Design：教师任务返回场次乐观锁 version

## Context

`add-offline-session-state-machine` 已让 start/complete/cancel 收 `version`，教师详情给出 `availableActions`。管理端
`TrainingOfflineSessionVO` 已有 `version`，权限是 `training:plan:list`。教师任务 VO 未带出版本。

## Goals / Non-Goals

**Goals:**

- 教师任务列表和详情返回场次乐观锁 `version`。
- 该值用于 start/complete/cancel 原样回传。

**Non-Goals:**

- 服务端替教师读取最新 version。
- 扩大教师权限去调管理端场次详情。
- 学员场次返回 version。

## Decisions

### D1：字段放在父 VO

`version` 加在 `TrainingTeacherTaskVO`。详情继承列表，两处一起有。教师可在列表直接开始/取消，不必先开详情。

### D2：映射来源

列表 `toTaskVO`、详情 `getTeacherTask` 均 `vo.setVersion(session.getVersion())`。冲突仍为 409 / `8204`。

### D3：契约

OpenAPI 两个 schema、列表/详情 description 与 example 写清：该 `version` 供 start/complete/cancel 回传。学员线下场次接口继续不返回版本。

## Risks

- 列表不带 version 时，前端若在列表点开始仍会断。因此父 VO 必须带。
