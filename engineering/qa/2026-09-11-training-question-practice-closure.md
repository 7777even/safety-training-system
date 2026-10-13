# QA：培训题库练习冻结与不评分结果闭环

## 范围

OpenSpec Change：`redesign-training-question-bank-practice-closure` Task 2.1、3.1。

本轮仅交付后端、数据库、Training/Exam OpenAPI 和工程记录；未修改前端。Task 1 的公共候选池、最大匹配抽题和发布预检证据见
`engineering/qa/2026-09-11-training-question-draw-precheck.md`。

## 业务闭环结论

1. 正式发布重新读取真实候选并校验，事务内冻结计划题池、题目版本、判定模式、规则和 SHA-256 指纹。
2. 生成执行只从计划冻结池复制执行池；缺失、损坏或指纹不一致统一返回 `8167`，不回退实时题库。
3. 新 Attempt 只从执行池抽题，保存随机种子、规则序号、题目版本、判定模式和题序；同令牌重入不重抽，未达 `maxAttempts` 可开始新轮次。
4. 新版培训题库练习不评分、不判通过、不进入人工批阅。全部作答后提交即完成；`AUTO` 题写 `AUTO_CORRECT/AUTO_WRONG`，
   `RECORD_ONLY` 写 `NOT_EVALUATED`。
5. Attempt、Candidate、培训逐内容投影和最终结果 v3 快照均保留完成事实及反馈摘要；题库练习不进入考试成绩或培训学时聚合。
6. 历史无冻结池、具有总分快照的 Attempt 继续原计分路径，不重算、不覆盖。

## TDD 与定向测试

实现前先以目标测试观察到冻结池类型/服务、Attempt 不评分字段、完成投影和结果契约缺失；随后完成最小实现并回归。

在 `backend/` 执行：

```text
mvn -pl sts-service-admin \
  -Dtest=QuestionDrawEngineTest,QuestionCandidatePoolServiceTest,QuestionBankDrawEngineTest,TrainingPlanPublishValidatorTest,TrainingPlanServiceImplTest,TrainingPlanControllerTest,TrainingQuestionPoolSnapshotServiceTest,TrainingExecutionProvisionerTest,AssessmentExecutionQuestionPoolServiceTest,TrainingAssessmentExecutionAdapterTest,ExamAttemptServiceImplTest,ExamLearnerServiceImplTest,TrainingResultSyncServiceImplTest test
```

结果：182 项通过，0 失败，0 错误，0 跳过。

覆盖：发布重验与冻结、JSON 规范化 hash、执行池复制/复用/损坏拒绝、源题库变化隔离、同令牌幂等、新轮次、混合题型、全不可判断题、未全部作答拒绝、本人反馈裁剪、Candidate
完成、培训投影及 v3 最终快照反馈摘要、历史计分兼容。

## MySQL/Testcontainers IT

执行：

```text
mvn -pl sts-service-admin -Pintegration-test \
  -Dtest=com.sts.question.mapper.QuestionDrawCandidatePoolMapperIT,com.sts.training.mapper.TrainingQuestionPoolPersistenceIT,com.sts.training.service.TrainingMixedPlanLifecycleIT test
```

结果：5 项通过，0 失败。使用 MySQL 8.0 Testcontainers 验证：

- 真实候选 SQL 的启用、归档、最新已发布版本、草稿排除、知识点和答案资产口径；
- 两级题池精确版本、租户隔离、唯一约束和判定模式持久化；
- 不评分 Attempt/答案可空分数、随机种子、规则归属及反馈摘要列；
- 培训逐内容反馈摘要列及正确率可空语义；
- 混合计划生成、结果同步、计划结束和最终快照链路。

另执行 `mvn -pl sts-service-admin -Pintegration-test -Dtest=FlywayMigrationIT test`，3 项通过；其中旧基线升级场景真实依次执行本
Change 的 3 条 migration，并断言新增表、列及既有数据兼容。Admin MySQL/Testcontainers IT 合计 8/8。

MySQL VPN 当前不可用，因此未连接共享数据库；本轮 DDL/Mapper/事务验证不依赖该环境。

另执行 `mvn -pl sts-service-admin-init -Dtest=AdminInitFlywayIT test`，结果 1 项通过，验证模块化完整建库、种子数据及 Flyway
baseline `20260911152000`。

## 契约与静态验证

- `docs/api/training.openapi.json`：同步预检定位字段、`8166/8167`、冻结执行、练习完成状态、逐内容及 Attempt 反馈摘要和可空成绩。
- `docs/api/exam.openapi.json`：同步 Attempt 随机依据、题目判定模式、答案判定结果、不评分字段和 `8167`。
- 两份 OpenAPI 均通过 `jq empty`。
- `openspec validate redesign-training-question-bank-practice-closure --strict` 通过。
- `git diff --check` 通过。

## 双轴 Review

### 规格符合性

通过。配置、预检、正式发布、执行生成、随机开考、全部作答门禁、反馈、培训逐内容投影和最终快照均使用已确认语义；简答未暗中进入批阅，未命中的简答不阻塞发布，冻结后不依赖源题库变化。

### 代码质量

通过。公共抽题引擎保持纯函数且不依赖培训 DTO/Mapper/错误码；培训域持有计划池，考试域持有执行池和
Attempt；指纹、幂等和历史分流均为显式边界。审查中补正了 `score=null` 的多选部分判分空指针风险、损坏判定模式的稳定 `8167`
，以及新一轮全主观练习必须清空上一轮正确率的问题。

## 环境说明

后端真实 MySQL 生命周期 IT 已覆盖本次核心持久化和业务编排；尚未使用共享 VPN 环境执行真实浏览器/HTTP 登录态联调。前端按
OpenAPI 另开，不属于本 Change 的实施范围。
