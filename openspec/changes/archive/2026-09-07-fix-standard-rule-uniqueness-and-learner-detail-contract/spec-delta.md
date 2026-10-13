# Spec Delta：培训标准基础规则唯一性与学员详情契约修正

## 变更类型

- 新增能力规格：`training-standard-singleton-rules`
- 修改既有能力：`training-api-contract`（学员参训详情对象契约与 `publishStatus` 必返）
- 不删除或重命名既有 Requirement。

## 机器可读 Delta

正式 Requirements 与 Scenarios 位于：

- `specs/training-standard-singleton-rules/spec.md`
- `specs/training-api-contract/spec.md`

该 Delta 新增或修改以下要求：

1. 同一培训标准的有效 `TOTAL_HOURS`、`ONLINE_RATIO` 各最多一条；替换规则和发布标准都校验。
2. 重复时返回 HTTP 409 / `8016`，并给出「总学时」或「线上学时占比」的明确消息。
3. 学员参训详情的 `participant`、`plan` 为对象；`plan.publishStatus` 必返。
