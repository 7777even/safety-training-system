# Design：培训后端最小可用闭环

## 1. 闭环边界

本 Change 采用“最小能力集合、稳定扩展基础”的约束：当前只建设合规和基本可用所必需的发布、执行、事实归集与结果冻结；同时把资源版本、真实
executionId、来源记录 ID、幂等业务键、逐内容结果投影、版本化快照和跨域 Port 设计为稳定边界。后续追加参训人、统计下钻等能力通过独立
OpenSpec Change 增量接入，不得要求重做课程、考试或培训执行模型。尚未形成明确需求的未来接口和流程不提前实现。

### 1.1 业务设计规则：功能最小，底层可扩展

“最小实现”只限制本期对用户开放的业务功能数量，不允许把核心模型做成一次性方案。当前设计必须提前稳定未来扩展共同依赖的数据粒度、标识语义、事实来源、版本边界、幂等约束和模块接口。

判断规则：如果未来增加一个合理能力会迫使系统改变既有 ID 含义、重建核心关联、迁移或猜测历史事实、替换主查询模型，或者改变已发布计划的解释方式，则所需基础设计
MUST 在当前版本完成；如果未来能力可以在既有稳定模型上通过独立流程、策略、接口或查询增量实现，则当前只记录扩展边界，不提前交付该功能。

具体约束：

1. 不为减少当前工作量而混用资源 ID、版本 ID 和执行 ID，也不使用 JSON 代替未来明确需要关系型查询的核心业务粒度。
2. 当前必须保存不可从未来状态可靠还原的事实，包括发布版本、执行实例、来源记录、逐内容结果和结束快照。
3. 表和字段只预留已经能够确定的稳定语义；不增加含义不明的备用字段、空表、通用扩展 JSON 或未确认流程。
4. 扩展能力不得直接依赖其他业务域的内部表结构；培训通过稳定 Port 读取课程、考试和线下事实。
5. 当前验收既要证明最小闭环可用，也要证明已记录的后续能力不需要推翻核心模型。

本 Change 的应用示例：

- **逐内容结果投影现在建设**：未来统计、监管下钻和明细导出都依赖 `trainingParticipantId + planContentId` 粒度。如果当前只保存最终
  JSON，未来需要回填、解析历史快照并切换核心查询模型，因此本期直接建立投影表；统计大屏和复杂导出仍延期。
- **追加参训人功能可以延期**：当前已具备计划专属 CourseOffering/AssessmentExecution、参训人唯一键和幂等
  Enrollment/Candidate 绑定。未来追加只需增加受控流程并补建绑定，不改变执行 ID、结果粒度或历史事实，因此本期不开放追加能力。
- **考试执行隔离现在建设**：如果当前共享来源考试执行，未来支持多计划、补考或独立排期时会串 Candidate
  和成绩，必须迁移历史关联。因此本期即创建计划专属 Exam，并统一以 AssessmentExecution ID 作为 executionId；更多考试策略可以后续增量增加。
- **未知统计维度不提前建字段**：当前投影保存稳定来源、状态、时长、分数和完成时间。未来确认新的统计维度后优先关联计划、人员和内容获得；只有无法稳定关联时才通过独立
  Change 增加字段，避免为了“可能有用”堆积无语义列。

```text
类型/标准/模板
  → 培训计划和具体参训人
  → 发布冻结资源版本
  → 生成课程/考试/题库练习/线下执行
  → 学员学习、作答，教师处理线下事实
  → 同步正式学时和成绩
  → 满足门禁后结束并冻结结果
```

职责不重叠：

- 课程域负责课程版本、开课、报名、学习进度和课程结果。
- 题库/试卷域负责题目、题库和不可变试卷版本。
- 考试域负责 AssessmentExecution、Candidate、Attempt、判分、批阅和成绩发布。
- 培训域负责计划编排、参训人、跨域执行生成、线下事实和培训结果聚合。
- 评价域负责正式综合评价计算；培训只读取其正式结果。

## 2. 内容语义

| contentType   | 产品语义    | resourceId     | resourceVersionId | executionId           |
|---------------|---------|----------------|-------------------|-----------------------|
| COURSE        | 课程      | courseId       | courseVersionId   | courseOfferingId      |
| EXAM          | 测验/理论考试 | 来源 examId      | paperVersionId    | assessmentExecutionId |
| QUESTION_BANK | 理论题库练习  | questionBankId | 空；题目在首次作答冻结       | assessmentExecutionId |
| OFFLINE       | 线下培训    | 空              | 空                 | offlineSessionId      |

EXAM 使用 `assessmentMode=QUIZ|FORMAL_EXAM` 区分测验和理论考试。QUESTION_BANK 仍是题库练习，不替换为“专项练习”。资源 ID、版本
ID 和执行 ID 必须分开，禁止互相冒充。

## 3. 最小数据变化

### 3.1 模板和计划内容

`sts_training_template_content` 增加 `resource_version_id`、`assessment_mode` 和 `execution_config_json`；
`sts_training_plan_content` 增加同样三个字段，并增加：

- `resource_snapshot_json`、`resource_snapshot_hash`：计划发布时冻结。

其中 `resource_version_id` 在 COURSE 中为 courseVersionId、EXAM 中为 paperVersionId，QUESTION_BANK/OFFLINE 为空；
`assessment_mode` 仅 EXAM 使用 QUIZ/FORMAL_EXAM。

`execution_config_json` 只允许 OpenAPI 定义的结构：

- COURSE：`completionRule`。
- EXAM：使用来源考试已发布策略，不额外复制一套自由配置。
- QUESTION_BANK：必须配置 `durationSeconds`、`passScore`、`maxAttempts` 和抽题规则；最小版本只允许可自动判分题型，含主观题的题库拒绝发布到该内容。
- OFFLINE：教师、地点、开始/结束时间、签到窗口、是否要求签退和成绩及格线。

### 3.2 执行关联

复用 `sts_training_execution_link`，保持 `(plan_content_id, execution_type)` 唯一：

- COURSE 指向 CourseOffering。
- EXAM/QUESTION_BANK 改为指向 AssessmentExecution。
- OFFLINE 仍由 OfflineSession 表表达，查询时统一转换为 execution view。

不新增执行任务表。单内容 provision 独立事务；失败时不写成功 link，修复原因后重复调用即可。下游通过 planContentId
唯一键找回已成功但尚未来得及回写 link 的实例。

### 3.3 逐内容结果投影与最终快照

新增 `sts_training_participant_content_result` 作为培训域稳定的逐内容结果投影。一条记录表示一个
`trainingParticipantId + planContentId` 的当前结果，并以该组合建立唯一键。最小字段为：

- `training_plan_id`、`training_participant_id`、`plan_content_id`、`content_type`。
- `execution_id`。
- `source_record_type`、`source_record_id`、`final_attempt_id`（非考试类型可空）。
- `result_status`、`effective_duration_seconds`、`score`、`completed_time`。
- `source_updated_time`、`synchronized_time`、`result_revision`、`finalized_time`。

同时建立满足计划结果、人员下钻和内容统计的最小索引：`(training_plan_id, result_status)`、`(plan_content_id, result_status)`
；不提前建立统计汇总表。

课程、考试和线下事实表仍是业务真相源，投影表不能反向修改来源事实。生成执行时为每个参训人和计划内容幂等建立 `PENDING`
投影行；结果同步按 `trainingParticipantId + planContentId` 读取 CourseEnrollment、ExamCandidate/Attempt 和
Attendance/OfflineScore，并 upsert 对应投影。培训结果查询与参训人聚合统一读取投影，避免未来统计再次改造核心查询模型。

`sts_training_participant` 增加：

- `final_result_snapshot_json`
- `result_finalized_time`
- `final_result_snapshot_hash`

计划运行期间由投影重建参训人聚合字段；计划结束前强制从正式来源同步全部投影，再把投影中的逐内容来源、学时、分数、状态和最终结论冻结到
`final_result_snapshot_json`。快照必须包含 `schemaVersion`，使用规范化 JSON 计算 `final_result_snapshot_hash`。结束时同步填写投影行
`finalized_time`；`result_finalized_time` 非空后，普通同步不得覆盖投影或快照。结束后明细查询可读取已冻结投影，快照作为结束时审计真相保留。

快照中的每个 item 至少包含 `planContentId`、`contentType`、`executionId`、`sourceRecordType`、`sourceRecordId`、考试类
`finalAttemptId`、状态、学时、分数和完成时间。时间、decimal 格式、空值和字段顺序必须由固定 schema 规范化，保证相同结果产生相同
hash。

### 3.4 数据库资产

- `schema-training.sql` 表达最终结构和不少于 25 种正式培训类型的最终初始化数据。
- 新增 Admin Versioned Migration，升级已有库并补 seed。
- 同步 `sts.init.schema-version`。
- 不修改、合并或删除既有 V 文件。

## 4. 发布规则

`publish(planId, expectedVersion)` 必须：

1. 计划为 DRAFT 且版本一致。
2. 存在至少一个阶段、一个必修内容和一个具体参训人。
3. 类型、标准、模板和评价表单版本属于当前机构且可用。
4. COURSE 属于当前机构且已发布，冻结 current courseVersionId。
5. EXAM 属于当前机构且已发布，冻结 paperVersionId 和考试策略；来源 Exam 只作为配置来源，不作为本计划的共享执行。
6. QUESTION_BANK 属于当前机构且已发布/启用，并具有完整练习配置。
7. OFFLINE 具有教师、时间、地点和签到规则。
8. 计划内容要求学时和线上占比不违反所选培训标准。
9. 冻结类型、标准、模板、阶段、内容、资源和评价版本快照及哈希。
10. 更新计划为 PUBLISHED；发布后结构和参训名单不可修改。

本次为控制范围，发布后不追加或删除参训人；新增人员需求留给后续导入/扫码 Change。

## 5. 真实执行生成

培训域保留一个小型 `TrainingExecutionService`，通过两个内部 Port 调用课程和考试，不直接写它们的 Mapper：

```text
TrainingCourseExecutionPort
  ensureOffering(planContentSnapshot)
  ensureEnrollments(courseOfferingId, participants)
  readResult(courseOfferingId, trainingParticipantId)

TrainingAssessmentExecutionPort
  ensureExamExecution(planContentSnapshot)
  ensureCandidates(assessmentExecutionId, participants)
  startAttempt(assessmentExecutionId, trainingParticipantId, currentUserId, clientToken)
  readResult(assessmentExecutionId, trainingParticipantId)
```

### 5.1 COURSE

1. 按 planContentId 创建或复用 CourseOffering。
2. 使用冻结的 courseVersionId；不能改用课程后来发布的新版本。
3. 开课为 ASSIGNED；时间取阶段时间，缺失时取计划时间。
4. 为所有参训人创建或复用 `enrollmentSource=TRAINING` 的 CourseEnrollment，写 trainingParticipantId 和
   sourceBusinessId=planContentId。
5. execution link 指向 courseOfferingId。

课程模块需要增加最小内部入口，允许使用已冻结的 courseVersionId 创建培训开课；不能复用当前会在发布时重新读取
currentVersionId 的普通管理端流程。

### 5.2 EXAM

1. 按 planContentId 创建或复用计划专属 Exam。
2. 从来源 Exam 复制冻结的 paperVersionId、时长、及格线、次数、批阅和可见性策略；开放时间取计划阶段时间，缺失时取计划时间，不照抄来源考试排期。
3. 创建/发布该计划专属 Exam，并取得 AssessmentExecution；考试内部 Port 按 assessmentMode 将 execution.assessmentKind 明确写为
   QUIZ 或 FORMAL_EXAM，不能依赖现有 standalone 默认一律映射 FORMAL_EXAM。
4. 为所有参训人创建 TRAINING Candidate，写 trainingParticipantId 和 sourceBusinessId=planContentId。
5. execution link 指向 assessmentExecutionId。

不能直接复用来源 Exam 的 AssessmentExecution，否则同一学员在多个培训计划引用相同考试时会共享 Candidate。

### 5.3 QUESTION_BANK

1. 按 planContentId 创建或复用 `examType=BANK_PRACTICE` 的计划专属 Exam。
2. 使用冻结 questionBankId 和 executionConfig。
3. 创建 AssessmentExecution 和 TRAINING Candidate。
4. 首次 Attempt 按考试域既有规则冻结题目快照与随机种子。
5. execution link 指向 assessmentExecutionId。

### 5.4 OFFLINE

按 planContentId 创建或复用 OfflineSession，使用冻结的教师、时间、地点和签到规则。签到、签退、成绩、附件和奖惩继续使用既有事实表。

### 5.5 幂等与失败

- 计划级生成逐个调用单内容 provision，不使用一个跨全部内容的大事务。
- CourseOffering、计划专属 Exam 和 OfflineSession 都必须以 planContentId 唯一。
- Enrollment/Candidate 使用下游唯一键；发现既有记录时验证其 trainingParticipantId 和来源一致。
- 单内容失败返回 contentId、类型和脱敏错误；已成功内容保留，重试不重复创建。
- 所有必修内容都存在真实执行后，计划才允许进入 RUNNING。

## 6. 学员和教师入口

统一学员入口：

```text
POST /api/training/learner/participants/{participantId}/contents/{contentId}/enter
```

服务端校验当前登录用户就是参训人、计划为 PUBLISHED/RUNNING、内容属于计划且执行已生成：

- COURSE 返回 courseEnrollmentId。
- EXAM/QUESTION_BANK 创建或恢复 Attempt，返回 examAttemptId。
- OFFLINE 返回场次信息，不创建线上跳转。

请求不接受 userId、examId、assessmentExecutionId 或 contextType。

教师任务继续按 OfflineSession.teacherUserId 查询。签到/签退确认和成绩提交同时校验场次、计划、参训人和教师归属；执行人从登录态派生。

## 7. 结果同步

`synchronizeResults(planId)` 对每名参训人、每个计划内容读取正式来源：

- COURSE：Enrollment 的 learningStatus、totalStudySeconds、finalScore、completedTime。
- EXAM/QUESTION_BANK：Candidate 的 candidateStatus、finalScore、finalAttemptId、resultPublishedTime；未发布成绩不计入。
- OFFLINE：已确认 Attendance 和已评分 OfflineScore；要求签退而未签退时不计完整学时。

结果项必须写入并返回 planContentId、executionId、来源记录 ID、来源状态、学时、分数和完成时间。重复同步按唯一键
upsert，不重复、不清空已有正式结果；来源有新的正式版本时，计划结束前允许刷新投影并递增 resultRevision。

参训人聚合：

- onlineHours：只累计 COURSE 的正式学习秒数；最小版本中 EXAM/QUESTION_BANK 不重复计入培训学时。
- offlineHours：确认后的有效签到区间，不超过场次时长。
- courseScore/examScore/offlineScore：按计划内容配置的平均或加权平均计算。
- compositeScore：只读取评价域正式 calculationRun，不在培训域重新实现公式。

## 8. 状态和结束门禁

最小状态：

```text
DRAFT → PUBLISHED → RUNNING → ENDED
                 ↘ CANCELLED
```

- PUBLISHED：结构冻结，可生成执行。
- RUNNING：全部必修执行已生成后显式开始。
- ENDED：正常完成并冻结结果。
- CANCELLED：提前终止，保留事实但不形成正常通过结果。

`end(planId, expectedVersion)` 必须：

1. 所有必修内容都有真实执行。
2. 不存在进行中的课程学习、考试 Attempt 或线下场次。
3. 先执行一次结果同步。
4. 每名参训人的必修内容都有完成、失败或到期结论。
5. 如绑定 COMPOSITE 评价表单，必须已有正式综合计算结果。
6. 根据冻结的内容规则和标准确定 PASSED/FAILED/INCOMPLETE。
7. 校验每个参训人和必修内容都有唯一投影行，将投影和来源写入参训人 finalResultSnapshot，同时冻结投影、填写
   finalizedTime，再更新计划 ENDED。

任一条件不满足时返回具体 participantId/contentId 和原因，不允许强制结束。取消计划保留已经产生的学习、作答、签到和成绩，不将人员标为
PASSED。

## 9. API 最小变化

- 模板/计划内容 schema 增加 resourceVersionId、assessmentMode、executionConfig 和 resourceSnapshot。
- 计划增加发布预检、start、end、cancel。
- 保留计划级 generate-executions，单内容 provision 用于重试。
- executionId 描述改为按类型指向 CourseOffering、AssessmentExecution 或 OfflineSession。
- 学员增加统一 enter；旧考试入口先兼容委托，不在本次删除。
- 结果接口补充来源 ID、学时、完成和冻结状态。

全部接口遵循 OpenAPI 四条铁律并包含错误码和 `x-permission`。不新增前端实现。

本次不新增权限模型：计划生命周期和执行生成继续使用 `training:plan:publish`，学员使用 `training:learner:task`，教师使用
`training:teacher:task`，结果同步/查看继续使用 `training:result:synchronize` 与 `training:result:list`；数据归属校验不能被权限码替代。

## 10. 逻辑推演

### 正常混合计划

发布冻结四类内容 → 生成 CourseOffering/Enrollment、两个计划专属 AssessmentExecution/Candidate、OfflineSession →
学员学习/作答 → 教师确认线下事实 → 同步结果 → 无进行中事实 → 结束并冻结。链路完整。

### 同一考试被两个计划引用

两个 planContent 各自生成计划专属 Exam 和 AssessmentExecution；同一用户有两个 Candidate，不串成绩。

### 题库练习

QUESTION_BANK 生成 BANK_PRACTICE Exam；首次作答冻结题目，不再进入当前未实现的 resolve 分支。

### 某内容生成失败

其他内容成功保留；失败内容没有成功 link。修复后单独 provision，通过 planContentId 找回可能已创建的下游实例，不重复生成。

### 成绩未发布

Candidate 已交卷但 resultPublishedTime 为空时，只显示待发布，不计入 examScore，也不能满足结束门禁。

### 结束时存在进行中考试

end 返回该 participant/content/attempt 阻塞，计划保持 RUNNING；提交并发布成绩后重试结束。

### 来源资源发布新版本

培训继续使用发布时冻结版本，结束结果写入 finalResultSnapshot；历史不随新版本变化。

## 11. 人工决策记录

已人工确认（2026-08-31）：

1. EXAM/QUESTION_BANK 的 executionId 统一使用 AssessmentExecution ID。
2. 培训引用考试时创建计划专属 Exam，不共享来源考试执行。
3. 当前最小版本发布后不再增删参训人；追加参训人不进入本 Change，按本文件第 12 节留作后续独立 Change。
4. 当前版本直接新增逐内容结果投影表；运行期从正式来源同步投影，结束时冻结投影并生成 participant finalResultSnapshot。

以上决策是后续 Contract、migration 和实现的固定边界；如需改变，必须先更新本 Design 并重新人工确认。

## 12. 后续追加参训人方案（不在本 Change）

后续如需支持发布后漏人补录，应创建独立 OpenSpec Change，并保持以下最小边界：

1. 只允许 PUBLISHED/RUNNING 计划追加，ENDED/CANCELLED 禁止追加。
2. 只追加、不删除；退出另用 WITHDRAWN 留痕，不物理删除历史事实。
3. 追加时冻结人员档案，并对所有 COURSE 补建 TRAINING Enrollment、对 EXAM/QUESTION_BANK 补建 TRAINING Candidate；OFFLINE
   复用既有场次。
4. 所有必修内容仍可进入时才能追加；存在已过期课程、考试或线下场次时拒绝，并返回具体阻塞内容。
5. 单批追加必须原子完成参训记录和全部必修执行绑定；任一补建失败则整批回滚，不能留下“有名单、无任务”的人员。
6. `(trainingPlanId, userId)`、Enrollment 和 Candidate 唯一键保证重复请求幂等；不得补造追加前的签到、学习时长或考试成绩。

该方案与当前计划专属 CourseOffering/AssessmentExecution 设计兼容，后续实施不需要重做本 Change 的执行模型。

## 13. 投影表后续扩展边界

当前版本已经建立逐内容结果投影，但只实现基本结果查询、参训人聚合和结束冻结，不实现统计大屏、监管下钻或复杂导出。

后续扩展遵守：

1. 培训统计、按内容筛选、监管下钻和 SQL 明细导出直接读取该投影，不再改造课程、考试、线下或培训 execution 模型。
2. 新统计维度优先通过计划、人员、内容关联获得；只有已经确认且无法稳定关联的维度才增加投影字段。
3. 汇总性能不足时再创建独立统计汇总或物化视图，不改变逐内容投影的唯一键和来源语义。
4. 结果纠错不得直接编辑投影或 JSON；必须由正式来源更正后执行独立的“结果更正/重新归档”流程。
5. 快照继续作为结束时审计真相；投影用于关系型查询和下钻，两者通过 snapshot item 与投影稳定字段核对。

因此，当前只增加一张必要的结果投影表，却已经固定未来统计和下钻依赖的最小粒度、来源标识及索引边界。

## 14. 实现偏差修正补充设计（2026-08-31）

本节来自代码与 Docker/MySQL 集成测试复核，只修正当前最小闭环已经约定但实现不完整的行为，不增加新业务功能、API
路径、权限模型或数据库表。第 1～13 节与本节冲突时，以本节更精确的执行和验收规则为准。

### 14.1 QUESTION_BANK 抽题、计分与题目快照

QUESTION_BANK 的 `executionConfig` 是本次执行的冻结规则，不能只作为策略 JSON 保存而在 Attempt 创建时忽略。首次 Attempt 必须：

1. 按冻结的 `drawRules` 顺序处理每条规则，并按 `questionType`、`difficulty`、`knowledgePointId` 过滤当前题库中已发布且可用的题目。
2. 每条规则必须恰好抽取 `drawCount` 道题；任一规则候选题不足时拒绝开始 Attempt，并返回能够定位规则的错误，不得自动减少题数或改为抽取全部题目。
3. 同一道题不能因命中多条规则而重复进入一次 Attempt；抽题使用服务端生成的随机种子，种子和最终题目顺序随 Attempt
   题目快照持久化。重试同一个 Attempt 必须读取既有快照，不能重新抽题。
4. 每题分值使用所属规则的 `scorePerQuestion`，试卷总分为各规则 `drawCount × scorePerQuestion` 之和；`passScore` 必须在
   `0～总分` 范围内。发布预检和首次作答都要校验该约束，防止形成无法通过的练习。
5. Candidate 的最终状态、最终分数和最终 Attempt 仍由考试域统一产生；培训域不得自行重新判卷。

最小版本不增加复杂组卷策略、手工选题或主观题批阅，但以上冻结、可复现和计分语义必须实现，为以后扩充抽题维度保留稳定基础。

### 14.2 EXAM 必须从发布快照创建计划专属执行

计划发布是培训执行配置的版本边界。创建计划专属 Exam 时，允许使用来源 `examId` 做归属校验和审计关联，但以下运行参数只能读取计划内容中已经校验
hash 的 `resourceSnapshotJson`：

- `paperVersionId`。
- 考试时长、及格线或及格比例、最大作答次数。
- 题目顺序、评分、部分得分、批阅和成绩可见性策略。
- `assessmentMode` 对应的 `assessmentKind`。

开放时间仍由计划阶段或计划时间确定。来源 Exam 或 ExamPolicy 在计划发布后发生变化，不得改变已发布计划创建出的专属
Exam、AssessmentExecution 或最终解释。快照缺字段、hash 不一致或不能转换为考试域命令时，执行生成必须失败并返回
planContentId，不能回退读取来源当前策略。

### 14.3 正式考试结果状态映射

EXAM/QUESTION_BANK 只在 `resultPublishedTime` 非空时进入正式投影。映射以考试域 Candidate 的正式结论为准：

| Candidate 正式状态 | 培训逐内容结果                         |
|----------------|---------------------------------|
| PASSED         | COMPLETED                       |
| FAILED         | FAILED                          |
| 未发布、未完成或仍在批阅   | PENDING/IN_PROGRESS，不得计分或满足结束门禁 |

培训域只复制 `finalScore`、`finalAttemptId` 和来源状态，不得因本地缺少及格线而把已有分数一律解释为
COMPLETED，也不得用一套不同于冻结考试策略的规则重新判定 PASSED/FAILED。

### 14.4 线下内容完成条件

OFFLINE 的出勤事实与成绩事实不能互相替代：

1. 必须存在已确认 Attendance，才能计算有效线下时长或形成 COMPLETED/FAILED 终态。
2. 配置要求签退时，签到和签退都已确认才计算完整有效时长；缺少签退时保持非终态，不能仅凭 OfflineScore 通过结束门禁。
3. 配置了及格线时，还必须存在已评分 OfflineScore；达到及格线映射 COMPLETED，未达到映射 FAILED。
4. 未配置及格线时，以确认后的出勤规则决定 COMPLETED；孤立成绩只能保留在来源域，不能使培训内容完成。

### 14.5 教师权限与数据归属

教师线下任务和处理接口统一使用既有 `training:teacher:task`，不新增权限码。每次签到/签退确认和成绩提交必须从登录态取得当前用户，并校验
`currentUserId == OfflineSession.teacherUserId`。

如既有管理员确需代办，只能使用已经存在且明确用于管理员处理培训线下执行的权限，并把“是否管理员代办”与普通编辑权限分开判断；不能把接口准入所要求的同一个权限直接作为
`adminActor=true`，否则归属校验恒被绕过。当前代码和契约没有明确该管理员代办权限时，最小版本只允许场次教师本人处理；新增管理员代办权限属于权限语义变化，必须另行人工确认。

### 14.6 成绩聚合的唯一枚举语义

成绩聚合只接受 OpenAPI 已定义的 `AVERAGE` 和 `WEIGHTED_AVERAGE`：

- `AVERAGE`：对存在正式成绩的同类型内容做算术平均。
- `WEIGHTED_AVERAGE`：按内容 `weight` 计算 `Σ(score × weight) / Σ(weight)`；参与计算的权重必须大于
  0，缺少正式成绩的必修内容不能被静默忽略并形成最终通过结论。

实现、持久化值和 OpenAPI 必须使用完全相同的枚举字符串，不兼容未进入契约的 `WEIGHTED` 别名。

### 14.7 最终快照的自描述与不可变性

`finalResultSnapshotJson` 除第 3.3 节字段外必须包含与 `resultFinalizedTime` 相同的 `finalizedTime`
。生成顺序固定为：完成最后一次正式同步 → 确定所有投影终态 → 取得唯一归档时间 → 把同一时间写入所有投影 `finalized_time`、快照
`finalizedTime` 和参训人 `result_finalized_time` → 对不含 hash 自身的规范化快照计算 hash → 持久化快照与 hash。

普通同步不得覆盖任何已归档投影、快照或 hash。后续结果更正仍按第 13 节通过独立流程完成。

### 14.8 修正后的最小推演

1. **题库规则 10 道、每题 10 分、60 分通过**：首次 Attempt 恰好冻结 10 道题和随机种子，总分 100；正式成绩 70 映射
   COMPLETED，重复进入不换题。
2. **来源考试在计划发布后修改策略**：计划专属 Exam 仍使用发布快照中的 paperVersion 和及格策略，两个计划互不影响。
3. **正式考试 59 分且 Candidate=FAILED**：投影必须为 FAILED，不能因为存在 finalScore 而变成 COMPLETED。
4. **线下已有 90 分但没有确认签到**：投影保持非终态并阻塞计划结束；补齐确认出勤后才按规则形成终态。
5. **非场次教师调用线下处理接口**：即使拥有普通培训查看权限也必须拒绝；场次教师本人可以处理。
6. **加权成绩 80×1 与 100×3**：结果必须为 95，不能退化为算术平均 90。
7. **结束归档**：投影、快照和参训人的归档时间一致，重新同步不改变其内容与 hash。

上述七个场景全部通过自动化测试和真实 MySQL 集成测试后，当前培训最小闭环才可重新判定为完成。

## 15. 二次复核补充设计：完整考试冻结与来源追溯（2026-08-31）

### 15.1 设计边界

本节继续遵守“功能最小、底层可扩展”：不增加新的管理页面、业务流程或权限码，只补齐计划专属考试完成既有作答/批阅/成绩发布所必需的冻结事实，并让逐内容投影能够区分“来源域原始状态”和“培训域结果状态”。

当前必须固定的稳定边界是：

1. 已发布计划生成执行时不再依赖来源 Exam、ExamPolicy 或来源 AssessmentExecution 的当前状态。
2. 需要人工批阅的计划专属考试必须在创建时拥有可工作的计划专属批阅人绑定。
3. 投影必须保存真实来源状态，不能用培训结果状态冒充来源状态。
4. 培训题库练习缺少冻结抽题规则时必须失败；课程题库的既有兼容行为不能泄漏到培训执行。

本节不建设批阅人动态改派、结果更正、历史快照升级或管理员代办流程。这些能力以后可以围绕稳定的计划专属
AssessmentExecution、Reviewer 和投影行增量增加，不需要改变本节的 ID、版本或事实语义。

### 15.2 EXAM 发布快照必须自足

EXAM 的 `resourceSnapshotJson` 升级为显式版本化结构，新增 `schemaVersion=2`。快照必须包含创建计划专属
Exam、ExamPolicy、AssessmentExecution 和批阅绑定所需的全部确定性输入：

- 来源审计：`sourceExamId`、来源考试编码和名称。
- 资源版本：`paperVersionId`、`assessmentMode`。
- 考试规则：时长、及格分/及格比例、题目顺序、多选计分和开放策略。
- 当前考试域已有且影响执行的 ExamPolicy 字段：终端允许范围、防切屏/复制、最短作答、补考次数和计分方式、积分、答题明细/解析/成绩/排名可见性等。
- 展示与责任元数据：分类、封面、负责人、通知开关和通知内容；这些字段只用于创建计划专属 Exam，不在 provision 时回读来源。
- `requiresManualReview` 和冻结的 `reviewers`；每个 reviewer 至少包含稳定的 `reviewerUserId` 与 `assignmentSource`。

发布预检必须读取冻结的 paperVersion 题目，判断是否存在非自动判分题目。存在时必须满足：来源考试已有至少一个本机构有效批阅人；否则拒绝发布培训计划，并返回
planContentId。纯自动判分考试允许 reviewers 为空。

快照使用固定字段顺序规范化后计算 `resourceSnapshotHash`。发布后来源考试改名、修改策略、修改批阅人、停用或删除，都不能改变快照解释。旧的
`schemaVersion=1` 或无版本快照如果缺少本节必需字段，只能阻止执行生成并提示取消后重新建立计划；不能临时回读来源补齐，也不能静默猜测默认值。

### 15.3 计划专属考试原子创建与幂等复用

`TrainingAssessmentExecutionPort` 接收由培训域校验 hash 后解析出的完整快照命令。考试域创建执行时遵守：

1. 除按 `paperVersionId` 读取不可变试卷版本和题目外，不查询来源 Exam、ExamPolicy、来源 AssessmentExecution 或来源 Reviewer。
2. 在同一事务中创建计划专属 Exam、完整 ExamPolicy、AssessmentExecution 和计划专属 ExamReviewer；任一步失败整体回滚。
3. Reviewer 绑定到新建的 `assessmentExecutionId`，不得继续绑定来源执行。主观题批阅继续走考试域现有权限和 reviewer 归属校验。
4. AssessmentExecution 的策略快照中额外保存 `trainingPlanContentId` 和 `trainingResourceSnapshotHash`，作为幂等指纹。
5. 按 planContentId 找到既有 Exam/AssessmentExecution 时，必须校验上述指纹及 Reviewer
   集合；完全一致才复用，不一致返回冲突，禁止刷新、覆盖或继续使用不一致执行。

这样以后增加计划级批阅人改派时，只需新增对计划专属 Reviewer 的受控变更流程；不需要重新解释来源考试或迁移已有
Candidate/Attempt。

### 15.4 逐内容投影保存真实来源状态

`sts_training_participant_content_result` 增加可空字段：

```text
source_status varchar(32) null
```

字段含义固定为“`source_record_type/source_record_id` 指向的正式来源事实在同步时的原始状态”，与 `result_status` 分工如下：

| 内容类型               | sourceStatus                    | resultStatus                                      |
|--------------------|---------------------------------|---------------------------------------------------|
| COURSE             | CourseEnrollment.learningStatus | 培训统一 PENDING/IN_PROGRESS/COMPLETED/FAILED/EXPIRED |
| EXAM/QUESTION_BANK | ExamCandidate.candidateStatus   | PASSED→COMPLETED，FAILED→FAILED，其余按门禁映射            |
| OFFLINE_ATTENDANCE | Attendance.attendanceStatus     | 按确认、签退和成绩规则形成培训状态                                 |
| OFFLINE_SCORE      | `SCORED`                        | 按确认出勤和冻结及格线形成 COMPLETED/FAILED                    |

约束：

1. 有来源记录时同步必须同时写 `sourceRecordType`、`sourceRecordId`、`sourceStatus` 和 `sourceUpdatedTime`
   ；没有来源记录时四者可以为空，结果保持 PENDING。
2. `sourceStatus` 只复制来源语义，不参与二次判分；`resultStatus` 才是培训门禁和统计使用的统一状态。
3. OpenAPI 中 `sourceStatus` 改为可空的真实来源状态，禁止描述为“与 resultStatus 一致”。
4. 最终结果快照升级为 `schemaVersion=2`，每个 item 增加 `sourceStatus`。已经归档的 v1 快照不重写；读取端按 schemaVersion
   兼容。
5. migration 只增加字段，不猜测历史来源状态。未归档投影通过下一次正式同步补齐；已归档 v1 数据保持 null，由快照版本表明历史口径。

该字段属于未来审计、统计下钻和结果更正共同依赖的稳定事实，应现在进入投影；不能继续通过运行期跨域查询临时拼装。

### 15.5 培训题库规则的运行期防线

考试域按 AssessmentExecution.sourceType 区分两种行为：

- `TRAINING_PRACTICE`：`drawRules` 缺失、为空、解析失败、候选不足、分值非法或快照 hash 不匹配时拒绝创建 Attempt；不得回退为全题库每题
  1 分。
- `COURSE_BANK_PRACTICE`：保留课程当前“无 drawRules 时冻结全部已发布题目”的既有语义，本 Change 不改变课程 Contract。

同一条 Attempt 创建路径必须先校验 AssessmentExecution 快照完整性，再决定抽题策略。该区分只使用已经冻结的 `sourceType`
，不增加客户端参数。

### 15.6 最小闭环推演

1. **来源考试含主观题**：发布时冻结 reviewer 7、8；生成计划专属 execution 9001，并把 reviewer 7、8 绑定到
   9001；学员提交后可以正常批阅和发布成绩。
2. **发布后来源考试被修改或删除**：仅凭 schemaVersion=2 快照和不可变 paperVersion 创建计划专属考试，不查询来源
   Exam/Policy/Reviewer。
3. **重复 provision**：既有 execution 的 planContentId、资源快照 hash 和 reviewer 集合一致则复用；任一不一致返回冲突，不覆盖已有
   Attempt 上下文。
4. **考试通过结果**：投影保存 `sourceStatus=PASSED`、`resultStatus=COMPLETED`；接口和最终 v2 快照同时保留两个状态。
5. **培训题库策略损坏**：`TRAINING_PRACTICE` 开考失败并返回规则错误；同一课程题库仍按课程既有全量练习语义运行。

以上场景以及 v1/v2 快照兼容、投影 migration、空库初始化和真实 MySQL 回归全部通过后，Task 5 才能完成，M3 培训后端才能重新判定完成。

## 16. 整体复核后的最小收口设计（Task 6，已确认并实施）

本节只修正整体复核确认的生命周期偏差、标准口径歧义和纵向验收缺口，不新增页面、API 路径、权限码、数据库字段或业务外围能力。确认后按
Task 6 实施；已完成状态机收口、口径测试和四类混合计划真实 MySQL 纵向 IT。

### 16.1 结束状态机必须单向

正常生命周期固定为：

```text
DRAFT → PUBLISHED → RUNNING → ENDED
                 ↘ CANCELLED
```

约束：

1. `start` 仍只接受 PUBLISHED，且全部必修执行已生成后才能进入 RUNNING。
2. `end` 只接受 RUNNING。PUBLISHED 直接结束必须返回 409/8163，不得同步、冻结或更新任何结果事实。
3. `cancel` 仍接受 PUBLISHED/RUNNING；取消不等同于正常结束。
4. 不新增状态、不改变请求响应结构；OpenAPI 只补充允许状态和 8163 的精确说明。

这样保留“发布后可预生成执行和进入内容”的当前最小语义，同时保证正常结束一定经过显式开始。

### 16.2 培训标准与个人结果的最小口径

当前 `TOTAL_HOURS`、`ONLINE_RATIO` 和培训类型 `onlineMaxRatio` 定义为**计划编排合规规则**：发布时按计划内容
`requiredHours` 和内容类型校验并冻结，发布后结构不可修改，因此结束时不再使用个人 `onlineHours/offlineHours` 重算同一规则。

个人最终结论只使用冻结内容规则和正式来源事实：

- COURSE：课程域正式完成状态、正式学时和成绩。
- EXAM/QUESTION_BANK：考试域已发布的 PASSED/FAILED。
- OFFLINE：确认出勤、签退要求和冻结及格线。
- 必修内容出现 FAILED 则人员 FAILED；缺少终态或 EXPIRED 则 INCOMPLETE；全部必修完成且没有失败才 PASSED。
- 绑定 COMPOSITE 表单时，仍必须存在评价域正式 calculationRun。

此处不把 `requiredHours` 暗自解释成个人最低实学时。原因是当前设计明确 EXAM/QUESTION_BANK 不累计培训学时，直接以个人累计学时校验
`TOTAL_HOURS` 会使包含考试学时的混合计划无法通过。未来若业务要求“个人实学时达标”，必须在独立 Change 中增加明确的
`minimumEffectiveHours`/学时折算规则，并可直接复用现有逐内容投影，不需要重构执行模型。

Task 6 必须通过测试固定上述口径：计划编排不满足标准时发布失败；计划已发布且全部必修正式完成时，结束不因考试不计学时而误判失败。

### 16.3 真实混合计划后端验收

增加一条最小 Spring/MyBatis + MySQL 纵向 IT，不能用 Mapper 单测或全 mock Service 测试代替。用例只覆盖一个机构、一名参训人和四个必修内容：

1. 准备已发布课程版本、自动判分考试、可自动判分题库、线下配置和已发布标准/模板。
2. 创建计划、阶段、四类内容和参训人，发布后断言资源版本与 hash 已冻结、名单不可修改。
3. 生成 CourseOffering/Enrollment、两个计划专属 AssessmentExecution/Candidate 和 OfflineSession，并验证 executionId
   类型及幂等重试。
4. 显式 start 后写入最小正式事实：课程完成、EXAM/QUESTION_BANK 已发布成绩、线下确认出勤及正式成绩。
5. 同步投影，断言四行来源 ID、sourceStatus/resultStatus、聚合字段和 resultRevision。
6. end 后断言计划 ENDED、投影 finalized、participant v2 快照/hash 可校验，普通同步不改写。
7. 同一测试组另覆盖 PUBLISHED 直接 end 返回 409/8163 且零副作用。

该 IT 是 Task 3 “真实混合计划后端验收”的补证，不扩展为 HTTP 全栈、前端 E2E 或跨全部异常组合。

### 16.4 文档与关闭条件

Task 6 完成后必须同步：

1. Proposal 状态改为全部 Task 已实施。
2. tasks.md 勾选 Task 6，并补独立 QA/Retro。
3. 进度表统一为实际零 DB、Admin MySQL IT、Admin-init IT 和新增混合 IT 数量，移除“IT 待 Docker”。
4. 完成规格符合性和代码质量双轴 Review。

只有状态机修正、口径测试、真实混合 IT 和文档同步全部通过，才恢复 M3/Task 1～6 为“已完成”。
