# QA：统一考核执行快照与完整性校验

## 范围

OpenSpec Change：`unify-assessment-execution-snapshot` Task 1。

仅交付后端、数据库、Course/Exam/Training OpenAPI 和工程记录；HTTP 路径、请求、成功响应及权限不变，未修改前端。

## 业务闭环结论

1. 课程题库、课程测验、独立考试/练习和培训考试/练习统一保存 v1 完整 `snapshot_json`，旧七个分散快照列已移除。
2. 唯一 Codec 使用固定 camelCase/全字段 ObjectMapper 配置；规范化 hasher 递归排序对象键、保持数组顺序并统一数字表达，再计算
   UTF-8 SHA-256。
3. 所有执行读取在创建 Candidate/Attempt 前校验版本、摘要及来源/资源查询投影；异常统一返回 HTTP 400 / `7063`。
4. 规则未变化的重复发布复用已有执行；规则变化时仅允许删除并重建完全未使用的执行。Candidate、Attempt、Reviewer、课程结果或冻结题池任一存在均拒绝刷新。
5. migration 只接受旧执行表为空的开发库；非空时由具名 CHECK 明确失败，不级联清理业务事实。Admin-init 直接建立最终结构，baseline
   为 `20260915103000`。

## TDD 与零 DB 验证

Red 阶段先加入规范化 Codec 测试，旧实现因不存在统一 Codec 而编译失败；Green 后在 `backend/` 执行：

```text
mvn -pl sts-service-admin \
  -Dtest=AssessmentExecutionSnapshotCodecTest,AssessmentExecutionServiceImplTest,ExamAttemptServiceImplTest,CourseAssessmentAttemptServiceTest test
```

结果：69 项通过，0 失败，0 错误。覆盖对象键/空白/等价数字稳定性、数组保序、未知版本、内容及投影篡改、六类来源构建、课程入口派生、幂等令牌、执行事实刷新门禁和
`7063` 前置拒绝。

曾执行 `mvn -pl sts-service-admin test`：共运行 1013 项，出现 6 个既有 WebSlice 初始化错误，分别位于
`CourseAssessmentManagementControllerTest`、`ExamLearnerControllerWebSliceTest`、`PersonPickerControllerWebSliceTest`、
`TrainingWorkflowControllerWebSliceTest`，均发生在本 Change 未修改的 Controller 测试反射/装载阶段；本 Change
目标测试无失败。未为本任务扩展修复这些无关基线问题。

## MySQL/Testcontainers IT

执行课程/考试/培训受影响纵向 IT：

```text
mvn -pl sts-service-admin -Pintegration-test \
  -Dtest=ExamMapperIT,ExamAttemptQueryMapperIT,ExamReviewerAndCandidateQueryIT,DraftExamParticipantLifecycleIT,TrainingMixedPlanLifecycleIT test
```

结果：16 项通过，0 失败。真实 MySQL 8.0 覆盖 JSON 写入后重新读取和校验、MySQL 物理文本变化、内容篡改返回 `7063`
、查询名称读取、草稿执行不可变替换、课程/考试归属及培训考试/练习生成与结果链。

另执行 `ExamTenantBoundaryIT`，4 项通过，确认新增完整快照字段未绕过既有租户读写边界。

另执行：

```text
mvn -pl sts-service-admin -Pintegration-test -Dtest=FlywayMigrationIT test
mvn -pl sts-service-admin-init -Pintegration-test -Dtest=AdminInitFlywayIT test
```

结果分别为 4 项和 1 项通过。Admin MySQL IT 合计 24 项通过。已验证空旧结构成功升级、旧执行非空时明确失败、最终三列存在且旧七列不存在，以及模块化完整建库与
Flyway baseline 一致。

## 契约与静态验证

- `docs/api/course.openapi.json`、`exam.openapi.json`、`training.openapi.json` 已统一 `7063` 描述，均通过 `jq empty`。
- `openspec validate unify-assessment-execution-snapshot --strict` 通过。
- `git diff --check` 通过。

## 双轴 Review

### 规格符合性

通过。完整快照、固定版本、规范化摘要、投影核对、六类来源统一读取、不可变门禁、不兼容旧执行数据和三份 OpenAPI 均与已确认 Change
一致。HTTP 请求和成功响应未变化，未引入旧字段双读或来源特判 hash。

### 代码质量

通过。审查中补正了旧手写查询仍引用已删除列、刷新门禁遗漏课程结果/冻结题池、无变化发布不应重建、逻辑删除并发结果未检查，以及
Codec 不应依赖全局 ObjectMapper 宽松配置等问题。最终写入/读取只经统一 Codec，查询投影保留索引能力。

## 环境与验收说明

共享数据库 VPN 不可用，因此未查询或修改共享/生产数据；数据库证据全部来自本地 MySQL 8.0 Testcontainers。由于本 Change
明确不兼容旧执行事实，已有开发库应用前必须整体重建。真实 H5 登录态 HTTP 冒烟仍需在可用联调环境执行。
