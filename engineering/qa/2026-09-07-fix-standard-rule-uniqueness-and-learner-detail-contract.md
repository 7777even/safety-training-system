# QA：培训标准基础规则唯一性与学员详情契约修正

## 范围

OpenSpec Change `2026-09-07-fix-standard-rule-uniqueness-and-learner-detail-contract` Task 1～2。仅后端与
`docs/api/training.openapi.json`；前端以 OpenAPI 为准另开。

## 验收口径

- 替换规则、发布标准时 `TOTAL_HOURS` / `ONLINE_RATIO` 各最多一条；重复返回 409/`8016`。
- 指定资源规则仍可多条；空规则列表表示清空。
- 学员参训详情契约中 `participant`、`plan` 为对象；`plan.publishStatus` 必返。

## TDD 与实现证据

1. 先扩展 `TrainingStandardServiceImplTest`：重复总学时/线上占比应 409/`8016` 且不写库。实现前失败为 8014
   版本冲突（尚未做唯一性校验即进入乐观锁更新）。
2. 在 `replaceRules` / `publish` 写入前计数基础规则后，定向零 DB 通过。
3. 修正 `TrainingLearnerParticipantDetailVO` OpenAPI `$ref`、example 与 `TrainingPlanVO.publishStatus`
   required；详情测试断言对象与状态。

## 验证结果

- 受影响零 DB：
  `mvn -pl sts-service-admin -am test -Dtest=TrainingStandardServiceImplTest,TrainingWorkflowServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
  → 26 项（标准 11 + 工作流 15），0 失败，0 错误，0 跳过。
- 带 DB IT：未运行。本 Change 不改 Entity、Mapper、SQL、schema 或持久化事务。
- Admin 全量零 DB：未运行，以受影响测试为准。
- `docs/api/training.openapi.json`：`python3` JSON 解析通过；`8016`、学员详情 `$ref`、`publishStatus` required 与嵌套
  example 已核对。
- `git diff --check`：通过。

## Review

- 规格符合性：通过。未给标准增加 `trainingNature`；未改评价表单绑定。
- 代码质量：通过。无越界重构；错误消息区分总学时与线上占比。
- 需人工决策：无。
