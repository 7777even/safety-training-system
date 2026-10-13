# QA：支持草稿考试配置考生与阅卷人

日期：2026-08-27  
OpenSpec：`openspec/changes/2026-08-27-support-draft-exam-participant-configuration/`  
Owner：lims

## 验收范围

- 草稿考试无执行对象时，批阅人读取返回空数组且不写库。
- 首次有效考生/阅卷人保存按需物化唯一执行对象；空保存不创建。
- 发布在原执行对象上冻结最终快照，保留人员关系；失败整体回滚。
- 草稿删除清理未投入执行的人员归属；异常 Attempt 拒绝删除。
- 草稿执行对象不得进入学员任务或允许开始作答。
- `docs/api/exam.openapi.json` 同步草稿空读、空数组清空和 `7062` 适用条件。

## TDD 证据

1. 零 DB 先扩展 `ExamServiceImplTest`、`AssessmentExecutionServiceImplTest`、作答与学员入口测试，覆盖空读、按需物化、发布刷新、删除清理和草稿开考拒绝。
2. 真库 `DraftExamParticipantLifecycleIT` 首次运行暴露出行锁 SQL 误用物理表名 `sts_exam`（实际为 `sts_exam_exam`）。
3. 修正表名后，事务回滚场景因测试事务 `REQUIRED` 嵌套与 MyBatis 一级缓存看不到 savepoint 回滚；改为 `PROPAGATION_NESTED`
   并用 `JdbcTemplate` 读原始列后转绿。

## 自动化结果

| 验证                                                                                                                                                | 结果                                          |
|---------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------|
| 受影响零 DB：`ExamServiceImplTest`、`AssessmentExecutionServiceImplTest`、`ExamAttemptServiceImplTest`、`ExamLearnerServiceImplTest`、`ExamControllerTest` | 85 个通过，0 失败                                 |
| `mvn -pl sts-service-admin -am test`                                                                                                              | 共运行 755 个，0 失败，1 个既有条件跳过                    |
| `DraftExamParticipantLifecycleIT`                                                                                                                 | 4 个通过：发布刷新保留人员、删除清理与唯一键复用、作答阻止删除、发布/批阅人替换回滚 |
| `mvn -pl sts-service-admin -am -Pintegration-test test`                                                                                           | 197 个通过，0 失败                                |
| `python3 -c "import json; json.load(open('docs/api/exam.openapi.json'))"`                                                                         | 通过                                          |
| `git diff --check`（本 Change 后端与 OpenAPI）                                                                                                          | 通过                                          |

## Review

- 规格符合性：通过。实现范围与确认后的 Task 1 一致；接口路径、权限、字段和数据库结构未改；前端不在本协作范围。
- 代码质量：通过。行锁使用正确物理表名；发布失败与批阅人替换失败由真库事务回滚证明；学员任务与开考仍看 `Exam.publishStatus`。

## 未执行项

- 未实施前端页面、Mock 或联调，前端以 `docs/api/exam.openapi.json` 为准另开。
- 未跑真实并发压测；并发首次保存依赖考试行锁与 `uk_execution_exam_deleted`，由重复 `ensureStandalone` 与唯一键收口。
- 未操作生产环境或生产数据。
