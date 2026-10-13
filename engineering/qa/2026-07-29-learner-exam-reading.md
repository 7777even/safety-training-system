# QA：学员考试任务与作答恢复

日期：2026-07-29  
OpenSpec Change：`2026-07-29-complete-learner-exam-read-contract` Task 1

## 覆盖范围

- 当前登录学员的考试任务查询与状态/动作映射。
- 本人 Attempt 校验、暂存答案和答案项恢复。
- 成绩未发布时的总分、分项分、正确性和统计字段裁剪。
- `ExamAnswerVO` / `ExamAnswerItemVO`、OpenAPI、权限种子与 JSON 契约有效性。

## 已执行

| 命令                                                                                                                                       | 结果                                 |
|------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------|
| `jq empty docs/api/exam.openapi.json`                                                                                                    | 通过                                 |
| `git diff --check`                                                                                                                       | 通过                                 |
| `mvn -pl sts-service-admin -am -Dtest=ExamLearnerServiceImplTest,ExamLearnerControllerTest -Dsurefire.failIfNoSpecifiedTests=false test` | 通过，5 tests / 0 failures / 0 errors |

零 DB 用例覆盖：本人任务、已发布结果、暂存答案恢复、未发布成绩裁剪、他人 Attempt 403，以及 Controller 路径与分页参数委托。

## 未执行

`mvn -pl sts-service-admin -am -Pintegration-test -Dtest=ExamMapperIT -Dsurefire.failIfNoSpecifiedTests=false test`
未通过环境启动：Testcontainers 报告没有有效 Docker 环境，`/var/run/docker.sock` 不存在。该结果不表示 IT 通过；待 Docker
可用后必须重跑，并补充本 Change 关联读取的专用 IT。

## 结论

零 DB、OpenAPI JSON 和差异检查通过；真实 MySQL 持久化验证受 Docker 环境阻塞，Task 保持“已实现，IT 环境阻塞”。

# 2026-07-30 补充验证：答案与解析字段

- Red：新增已发布成绩场景，首次因 `ExamAttemptQuestionVO` / `ExamAttemptOptionVO` 缺少标准答案、正确答案快照、解析和正确选项字段而编译失败。
- Green：补充字段及服务端裁剪；正确答案、标准答案和正确选项由成绩发布状态与 `answerDetailMode` 共同控制，解析额外要求
  `isAnalysisVisible=1`。
- 目标测试：`ExamLearnerServiceImplTest` 4/4 通过。
- Admin 全量零 DB：385/385 通过。
- `exam.openapi.json` 已同步字段中文注释、可空性和 example。
