# QA：M3 培训执行闭环 Task 3

## 范围

OpenSpec Change `2026-07-29-complete-m3-training-execution-loop` Task 3。仅后端与 OpenAPI。

## 已确认规则

- 学员统一 enter 从 participantId + contentId 派生，不接受 userId、examId、assessmentExecutionId、contextType。
- 教师签到确认与线下成绩的执行人来自登录态；非场次教师且无管理权限返回 403/8131。
- 逐内容投影唯一键 `(training_participant_id, plan_content_id, deleted)`。
- 未发布考试不计分、不计入培训学时；要求签退未签退不计完整线下学时。
- 结束门禁失败返回 409/8160，消息含 participantId、planContentId、reason；不允许强制结束。
- finalized 后普通同步不得覆盖投影或快照；取消不把人员标为 PASSED。

## 验证结果

- TDD：先写 enter/同步/门禁/教师零 DB 测试，再实现。
- 目标零 DB：56/56 通过（`TrainingResultSyncServiceImplTest` 5、`TrainingPlanLifecycleServiceImplTest` 5、
  `TrainingWorkflowServiceImplTest` 9、`TrainingAttendanceServiceImplTest` 4、Controller 5、既有执行/计划/Adapter 28）。
- 带 DB IT：未执行。本机 Docker daemon 未启动（`unix:///var/run/docker.sock` 不存在）。不得用零 DB 代替。启动后建议：

```
mvn -pl sts-service-admin -am -Pintegration-test test -Dtest=TrainingPlanMapperIT,FlywayMigrationIT,CourseOfferingMapperIT,ExamMapperIT
```

- `docs/api/training.openapi.json` JSON 可解析；新增 start/end/cancel/enter，结果项补充来源与学时字段。
- `git diff --check` 通过。

## Review

- 规格符合性：通过。学员 enter、教师登录态、投影同步、结束门禁与快照冻结覆盖 Task 3 验收项；旧 exam-attempts 兼容委托 enter。
- 代码质量：通过。培训域仍经 Port 读课程/考试事实；投影不回写来源；越界前端未改。
