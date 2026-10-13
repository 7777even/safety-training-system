# QA：培训 API 契约 Task 2

## 范围

OpenSpec Change：`2026-07-31-complete-training-api-contract`，Task 2。

仅验证后端、OpenAPI、并发与可信身份语义；不包含前端。

## 已通过

- 目标零 DB 测试：10 个用例，0 失败。
- Admin 全量零 DB：394 个用例，0 失败。
- OpenAPI operation：training 58、evaluation-form 8、evaluation-result 6。
- OpenAPI 关键语义：分类树、表单版本历史、发布 `version`、`evaluationItemCode`、签到确认不接收 `confirmedBy`、教师任务不接收
  `teacherUserId`。
- `openspec validate 2026-07-31-complete-training-api-contract --strict`。
- `git diff --check`。

## MySQL IT

执行：

`mvn -pl sts-service-admin -am -Pintegration-test test`

沙箱外访问 Docker 后，Task 2 定向测试结果：

- `TrainingContractMapperIT`：2/2 通过。
- `EvaluationFormMapperIT`：3/3 通过。
- 合计：5/5 通过。

全量 IT 可正常启动 MySQL 容器，但既存 `ExamAttemptQueryMapperIT` 两条用例引用不存在的 `sts_organization` 表而失败。该 SQL
不在本 Task 修改范围；其余输出中 Task 2 定向用例已全部通过。

## Review

- 规格符合性：通过。实现未进入 Task 3，未修改数据库结构和前端。
- 代码质量：通过。版本冲突在删除子记录前失败；外部执行人字段从请求中移除并由登录态派生；OpenAPI 与实现同步。
