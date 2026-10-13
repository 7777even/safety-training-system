# Tasks：支持草稿考试配置考生与阅卷人

> 状态：已于 2026-08-27 获得人工确认，Task 1 已完成。

## Task 1：完成草稿考试人员配置与发布冻结闭环

- **状态**：已完成
- **Owner**：lims
- **输入**：功能项 16 及功能项 17 的阅卷人相关语义；现行独立考试、统一人员选择、统一考核执行与考试规则规格；
  `docs/api/exam.openapi.json`；本 Change 的 proposal、design 与 spec delta。
- **输出**：草稿批阅人空读；首次有效人员保存按需物化唯一执行归属；发布时在原执行对象上冻结最终快照并保留人员关系；草稿删除完整清理；
  同步后的 OpenAPI、自动化测试、QA / Retro 与进度证据。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：保持 `GET /api/exam/exams/{examId}/reviewers`、
  `POST /api/exam/exams/{examId}/candidates/bind-selection`、`PUT /api/exam/exams/{examId}/reviewers`、
  考试更新/发布/删除接口的现有路径、字段和权限；更新 `docs/api/exam.openapi.json` 的草稿空读、空阅卷人数组有效语义与 `7062`
  适用条件说明。
- **数据影响**：不改变数据库结构，不新增 migration；复用 `sts_assessment_execution`、`sts_exam_candidate`、
  `sts_exam_reviewer` 现有唯一键和逻辑删除语义；发布刷新不得改变执行对象 ID；草稿删除不得遗留活动归属或人员事实。
- **验收标准**：
  1. 存在的 `DRAFT` 考试尚无执行对象时，读取阅卷人返回 HTTP 200 与空数组，且无数据库写入。
  2. 首次保存非空考生或阅卷人时只创建一个独立考试执行对象，并把人员关系保存到该对象；空保存不创建对象。
  3. 重复保存和并发首次保存不产生第二个活动执行对象或重复人员关系。
  4. 已配置人员后修改草稿并发布，执行对象使用最终资源、考试规则和策略快照，摘要与最终快照一致，
     `assessmentExecutionId`、Candidate 和 Reviewer 保持不变。
  5. 发布任一步骤失败时，考试发布状态、执行快照和人员关系按事务整体回滚。
  6. 删除已配置人员的草稿考试后，不存在活动执行对象、Candidate 或 Reviewer 残留；异常存在 Attempt 时拒绝删除。
  7. 执行对象提前存在不会让草稿考试进入学员任务或允许开始作答。
  8. 非草稿考试缺失执行对象仍返回 `7062`；考试不存在、资源无效和发布状态等既有错误语义保持不变。
  9. 接口路径、权限、请求/响应结构、发布后编辑规则和数据库结构均未改变。
- **测试方式**：
  1. 按 Red → Green → Refactor，先扩展 `ExamServiceImplTest`、`AssessmentExecutionServiceImplTest` 和必要的作答入口零 DB
     测试并观察失败。
  2. 在 `backend/` 运行受影响零 DB 测试，并执行 `mvn -pl sts-service-admin -am test`。
  3. 增加目标 MySQL IT，覆盖唯一执行对象、真实快照更新、关联保留、删除清理和事务回滚；执行
     `mvn -pl sts-service-admin -am -Pintegration-test test`。Docker 不可用时如实记录，不能以零 DB 替代。
  4. 校验 `docs/api/exam.openapi.json` JSON 结构、Controller tags、字段说明、example、权限和错误码描述。
  5. 完成规格符合性与代码质量双轴 Review；通过后更新本 Task、进度表、QA / Retro。
