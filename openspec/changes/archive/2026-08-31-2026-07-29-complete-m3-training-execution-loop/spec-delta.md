# Spec Delta：培训后端最小可用闭环

完整场景见 `specs/training-execution-loop/spec.md`。

## ADDED Requirements

1. 计划发布冻结课程版本、试卷版本、题库引用、线下规则和完整结构快照。
2. COURSE、EXAM、QUESTION_BANK、OFFLINE 均生成真实执行。
3. EXAM/QUESTION_BANK 使用计划专属 AssessmentExecution 和 Candidate，隔离不同培训上下文。
4. 学员从培训统一入口进入本人 CourseEnrollment 或 Attempt，不接受客户端声明身份上下文。
5. 结果只读取课程、考试和线下正式事实并同步逐内容结果投影，计划结束时冻结投影和最终结果快照。
6. init 与 migration 可复现不少于 25 种正式培训类型。
7. QUESTION_BANK 首次 Attempt 必须按冻结抽题规则生成可复现的题目、顺序、分值和随机种子快照，Candidate 由同一冻结计分规则形成正式结论。
8. 计划专属 EXAM 必须使用计划发布时的资源和策略快照创建，禁止回退读取来源考试的当前策略。
9. 已发布考试结果按 Candidate 的 PASSED/FAILED 正式结论映射；线下内容必须以确认出勤为完成前提，成绩不能代替出勤事实。
10. 教师线下操作必须同时满足 `training:teacher:task` 和本人场次归属；接口权限不能隐式获得管理员代办能力。
11. 成绩聚合枚举和算法必须与 OpenAPI 的 AVERAGE/WEIGHTED_AVERAGE 一致。
12. 最终结果快照必须包含 finalizedTime，并与投影及参训人的归档时间保持一致，归档后不可被普通同步覆盖。
13. EXAM 发布快照必须自足并版本化，包含完整考试策略和批阅人；计划专属执行生成不得依赖来源 Exam、Policy、Execution 或
    Reviewer 的当前状态。
14. 包含非自动判分题目的计划专属考试必须创建计划专属 Reviewer，使作答、批阅和成绩发布形成闭环。
15. 逐内容投影必须分别保存来源域 `sourceStatus` 和培训域 `resultStatus`，最终快照 schemaVersion=2 必须保留两个状态。
16. TRAINING_PRACTICE 缺少有效冻结抽题规则时必须拒绝开考，不能使用课程题库的全量题目兼容分支。
17. 正常结束只允许从 RUNNING 进入 ENDED；PUBLISHED 直接结束必须零副作用失败。
18. 培训标准的 TOTAL_HOURS、ONLINE_RATIO 和 onlineMaxRatio 是计划编排合规规则，个人结论按必修逐内容正式事实产生，不把考试/题库时长重复计入个人学时。
19. 四类内容混合计划必须具有真实 MySQL 纵向验收证据，Mapper 或 mock 测试不能替代。

## MODIFIED Requirements

- executionId 不再允许使用资源 ID；COURSE 指向 CourseOffering，EXAM/QUESTION_BANK 指向 AssessmentExecution，OFFLINE 指向
  OfflineSession。
- generate-executions 从“关联既有实例”改为“按 planContentId 创建或复用计划专属实例”。
- 结果同步从只更新部分平均分改为持久化逐内容投影，返回来源、学时、分数和状态，并在结束时冻结投影和最终快照。
- 结果项的 sourceStatus 从“与 resultStatus 一致”改为来源记录的原始状态；resultStatus 继续表达培训统一结论。
- 计划发布后结构和参训名单只读；参训人导入、扫码和后续追加不进入本次最小范围。
- end 从允许 PUBLISHED/RUNNING 调用收紧为仅 RUNNING；请求响应结构和权限码不变。

## REMOVED Requirements

- 移除前端页面、Mock、联调和 UI 验收要求；前端以 OpenAPI 为准另开。
- 移除岗位矩阵、审批通知、复杂统计、导入、扫码和导出设计。
- 移除持久化执行任务表设计；逐内容结果投影表纳入当前最小范围。

## 延期

岗位矩阵；参训人导入/扫码/导出；审批、通知和短信；培训统计大屏；问卷、综合计算算法、档案、证书和监管预警自身实现；仿真、人脸识别和外部系统联动。
