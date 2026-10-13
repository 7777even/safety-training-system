# QA：补齐培训课程正式成绩闭环

## 范围

OpenSpec Change：`2026-09-18-close-training-course-score-loop`。

- 培训绑定、已完成课程按完成规则范围内 `COURSE_QUIZ` 当前正式分产生课程成绩。
- 既有培训结果同步入口在重建投影前补齐未冻结参训人的历史空分。
- 独立课程、题库练习、既有非空课程成绩、冻结参训人和获得学时规则不变。

## 自动化验证

- TDD Red：新增历史补账方法的测试先因方法不存在失败；实现后再收敛为共享纯计算器测试。
- `mvn -pl sts-service-admin -am test "-Dtest=TrainingCourseExecutionAdapterTest,CourseLearningServiceImplTest,TrainingCourseScoreCalculatorTest,TrainingWorkflowServiceImplTest,TrainingResultSyncServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false"`：91/91 通过。
- `openspec validate 2026-09-18-close-training-course-score-loop --strict`：通过。
- `docs/api/training.openapi.json`：JSON 解析通过。
- `git diff --check`：通过。

## 覆盖结论

1. 两个必修课程测验 80/90 的课程正式成绩为 85.00；选修测验与题库练习不参与。
2. 0 分是有效分：0/90 的课程正式成绩为 45.00。
3. 培训绑定必须同时具备 `trainingParticipantId` 与 `planContentId`；独立课程不会写课程汇总成绩。
4. 同步只枚举 `resultFinalizedTime` 为空的参训人，且只补空课程成绩，再复用既有投影同步。

## MySQL 集成测试

已尝试 `TrainingMixedPlanLifecycleIT`。测试 JVM 的 Windows classpath 根目录校验已通过 `JAVA_TOOL_OPTIONS=-Djdk.net.URLClassPath.disableClassPathURLCheck=true` 绕过；随后 Testcontainers 拉取 `testcontainers/ryuk:0.11.0` 时，Docker Desktop 无 HTTPS 代理且访问 Docker Hub 443 超时，未进入业务断言。因此本轮没有伪报 MySQL IT 通过。

## 人工验收待办

在非生产环境准备一个培训绑定课程：两个必修章节测验（含 0 分场景）均已生效、课程状态已完成。调用结果同步后，确认课程内容成绩等于平均分；再核对独立课程报名的 `finalScore` 未被写入。本次未做真实登录态验收。
