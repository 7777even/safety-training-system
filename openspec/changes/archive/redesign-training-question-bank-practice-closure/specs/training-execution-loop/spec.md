## MODIFIED Requirements

### Requirement: 培训计划冻结可执行资源

计划发布时 MUST 冻结类型、标准、模板结构、评价表单版本、课程版本、试卷版本、题库练习抽题规则及候选题版本池和线下执行规则。已发布计划结构、参训名单、题库候选池和资源
hash MUST 只读。

#### Scenario: 发布混合培训计划

- **GIVEN** 草稿计划包含有效参训人及 COURSE、EXAM、QUESTION_BANK、OFFLINE 内容
- **WHEN** 管理员携带当前版本发布计划
- **THEN** 系统校验机构、发布状态和执行配置
- **AND** 系统对题库练习使用真实候选池执行全局去重可行性校验
- **AND** 系统冻结全部资源与结构快照并推进为 PUBLISHED

#### Scenario: 来源题库发布后变化

- **GIVEN** 培训计划已经冻结题库练习候选题版本池并发布
- **WHEN** 来源题库新增题目、停用或归档题目、或者题目发布新版本
- **THEN** 已发布计划继续使用原候选题版本池
- **AND** 执行生成和学员开考不读取来源题库当前状态替换冻结池

#### Scenario: 来源课程或考试发布新版本

- **GIVEN** 培训计划已经发布并冻结课程或考试版本
- **WHEN** 来源课程或考试产生新版本
- **THEN** 培训计划继续使用冻结版本且历史结构不变化

#### Scenario: 预检后发布前题库变化

- **GIVEN** 发布预检曾经通过
- **AND** 正式发布前来源题库发生变化
- **WHEN** 管理员正式发布计划
- **THEN** 系统在发布事务内重新构建候选池并重新校验
- **AND** 系统不得因先前预检通过而跳过正式校验

### Requirement: 四类培训内容均可真实执行

系统 MUST 支持 COURSE、EXAM、QUESTION_BANK 和 OFFLINE。EXAM MUST 区分 QUIZ 和 FORMAL_EXAM；QUESTION_BANK MUST
形成不评分的完成型题库练习，不得替换为专项练习。题库练习 MAY 抽取客观题、填空题和简答题；可自动判断题 MUST 给出对错，不可自动判断题
MUST 只记录作答且不得进入批阅。EXAM 发布快照 MUST 自足并版本化，包含完整考试策略和批阅人；计划专属执行生成 MUST NOT 依赖来源
Exam、Policy、Execution、Reviewer 或实时题库的当前状态。QUESTION_BANK 首次 Attempt MUST 按冻结规则和冻结候选池生成可复现的题目、顺序、判定模式和随机种子快照。

#### Scenario: 生成真实执行

- **GIVEN** 已发布计划包含四类内容
- **WHEN** 管理员生成执行
- **THEN** 系统创建真实 CourseOffering/Enrollment、计划专属 Exam/AssessmentExecution/Candidate、BANK_PRACTICE
  Execution/Candidate 和 OfflineSession
- **AND** 题库练习执行池从计划发布快照复制，不从实时题库重建

#### Scenario: 题库练习缺少配置

- **GIVEN** QUESTION_BANK 内容缺少时长、最大练习次数或抽题规则
- **WHEN** 管理员发布计划
- **THEN** 系统拒绝发布且不猜测业务默认值
- **AND** 系统不要求题库练习配置及格线或每题分值

#### Scenario: 题库含未抽取简答题

- **GIVEN** 来源题库同时含单选题和简答题
- **AND** 抽题规则只抽取单选题且候选充足
- **WHEN** 管理员执行发布预检或正式发布
- **THEN** 系统允许发布
- **AND** 未被规则命中的简答题不产生 `8127`

#### Scenario: 题库练习抽取简答题

- **GIVEN** 抽题规则包含简答题且候选充足
- **WHEN** 管理员发布并生成题库练习执行
- **THEN** 系统将命中的简答题以 `RECORD_ONLY` 判定模式冻结
- **AND** 系统不要求配置阅卷人

#### Scenario: 计划专属考试使用发布快照

- **GIVEN** 来源考试的当前策略或批阅人已变化
- **WHEN** 系统为已发布计划生成 EXAM 执行
- **THEN** 计划专属 Exam 与 AssessmentExecution 使用发布时冻结的资源、策略和批阅人快照
- **AND** 不读取来源考试的当前状态

#### Scenario: 题库练习按冻结规则开考

- **GIVEN** QUESTION_BANK 内容已冻结抽题规则和候选题版本池
- **WHEN** 学员首次进入并开考
- **THEN** 系统按冻结资产生成可复现的题目、顺序、判定模式和随机种子快照
- **AND** Candidate 由练习完成事实形成正式结论，不形成分数或通过状态

#### Scenario: 题库练习缺少冻结抽题资产

- **GIVEN** TRAINING_PRACTICE 缺少有效冻结规则、冻结候选池或候选池 hash 不一致
- **WHEN** 生成执行或学员开考
- **THEN** 系统拒绝该内容继续执行并返回稳定业务错误
- **AND** 系统不回退到来源题库实时题目或课程题库全量兼容分支

### Requirement: 培训执行幂等且上下文隔离

每个 planContent MUST 只有一个真实执行；重复生成不得重复创建实例。EXAM/QUESTION_BANK MUST 使用计划专属
AssessmentExecution，不能共享来源考试 Candidate，也不能用资源 ID 冒充 executionId。COURSE 的 executionId MUST 指向
CourseOffering，EXAM/QUESTION_BANK MUST 指向 AssessmentExecution，OFFLINE MUST 指向 OfflineSession。generate-executions
MUST 按 planContentId 创建或复用计划专属实例，不得关联来源资源的既有执行。题库练习重复生成时 MUST 比较发布快照 hash 和执行题池
hash，MUST NOT 用实时题库覆盖既有执行池。

#### Scenario: 同一题库用于两个计划

- **GIVEN** 两个计划分别发布且引用同一来源题库
- **WHEN** 分别生成执行
- **THEN** 系统创建两个相互隔离的计划专属执行池和 Candidate
- **AND** 任一计划后续题库变化或作答事实不影响另一计划

#### Scenario: 同一考试用于两个计划

- **GIVEN** 两个计划引用相同来源考试
- **WHEN** 分别生成执行
- **THEN** 系统创建两个计划专属 AssessmentExecution 和两个 Candidate
- **AND** 两个计划的作答与成绩互不影响

#### Scenario: 单内容重试

- **GIVEN** 某内容首次生成因基础设施原因失败而其他内容成功
- **WHEN** 修复原因后重试该内容
- **THEN** 系统保留其他成功执行并通过 planContentId 创建或找回唯一实例
- **AND** 找回题库练习执行时校验其题池指纹与发布快照一致

### Requirement: 培训结果由正式来源同步到逐内容投影

系统 MUST 按 trainingParticipantId + planContentId 读取课程、考试、题库练习和线下正式事实，并幂等更新唯一的逐内容结果投影。投影
MUST 保存 executionId、来源记录、学时、可空分数，并分别保存来源域 `sourceStatus` 和培训域 `resultStatus`；正式事实仍是业务真相源。未发布考试成绩
MUST 不展示、不计分；已发布考试结果 MUST 按 Candidate 的 PASSED/FAILED 正式结论映射。培训题库练习 MUST 以全部作答并提交形成
`COMPLETED`，其分数和通过状态 MUST 为空，正确率反馈 MUST NOT 进入考试成绩聚合。EXAM/QUESTION_BANK MUST NOT 重复计入培训学时。最终快照
MUST 保留来源状态、培训结论、题库练习反馈摘要和 finalizedTime，并与投影及参训人的归档时间保持一致。

#### Scenario: 题库练习完成后同步

- **GIVEN** 培训题库练习已将全部抽中题目作答并提交
- **WHEN** 来源事实同步到培训投影
- **THEN** 对应内容 `resultStatus=COMPLETED`
- **AND** 分数、及格状态和获得学时为空或为契约约定的不适用值
- **AND** 可判断题正确率与不判定题数量作为练习反馈保存，不进入考试成绩聚合

#### Scenario: 题库练习尚未完成

- **GIVEN** 题库练习 Attempt 仍有未作答题目或尚未提交
- **WHEN** 同步或查询培训结果
- **THEN** 对应投影保持 `IN_PROGRESS`
- **AND** 系统不得提前写入完成、通过或不通过结论

#### Scenario: 同步正式结果

- **GIVEN** 课程完成、考试成绩发布、题库练习完成或线下成绩保存
- **WHEN** 系统同步培训结果
- **THEN** 系统更新唯一逐内容投影及参训人聚合字段，并保留可追溯的正式来源

#### Scenario: 考试成绩未发布

- **GIVEN** Candidate 已交卷但考试成绩未发布
- **WHEN** 同步或查询培训结果
- **THEN** 系统显示待发布且不计入 examScore

#### Scenario: 投影区分来源状态与培训结论

- **GIVEN** 来源业务状态与培训统一结论使用不同枚举
- **WHEN** 系统同步逐内容投影或冻结最终快照
- **THEN** `sourceStatus` 保存来源记录的原始状态
- **AND** `resultStatus` 保存培训统一结论
- **AND** 最终快照同时保留两个字段

#### Scenario: 计划级同步仍可全量补账

- **GIVEN** 历史投影落后于来源事实
- **WHEN** 管理员调用计划级结果同步
- **THEN** 系统按每名未冻结参训人、每个内容项刷新投影并重建聚合
- **AND** 已冻结参训人不得覆盖
- **AND** 题库练习仍按完成事实而不是分数事实映射

## ADDED Requirements

### Requirement: 发布预检必须返回规则级可执行性

系统 MUST 使用与真实抽题相同的候选过滤和全局唯一匹配能力校验培训题库练习。单条规则候选不足、跨规则去重后不足以及难度或知识点过滤后不足
MUST 在发布前返回 `8166`，不得推迟到学员开考才返回 `7063`。

发布阻塞项 MUST 按适用场景返回可选的 `stageId`、`stageName`、`contentName`、`resourceId`、`resourceName`、`ruleIndex`、
`questionType`、`difficulty`、`knowledgePointId`、`requiredCount`、`availableCount` 和中文 `suggestion`。

#### Scenario: 单条规则候选不足

- **GIVEN** 第 1 条规则需要 5 题但全局最多可分配 3 题
- **WHEN** 管理员执行发布预检
- **THEN** 返回 `8166`、`ruleIndex=1`、`requiredCount=5`、`availableCount=3`
- **AND** 返回内容、资源和中文处理建议

#### Scenario: 多规则去重后不足

- **GIVEN** 两条规则分别单独统计均看似充足但共享同一批候选
- **AND** 全局唯一分配不能满足全部需求
- **WHEN** 管理员执行发布预检
- **THEN** 返回无法满足规则的结构化 `8166` 阻塞项
- **AND** 系统不得把同一道题重复计入两条规则的可用数量

### Requirement: 培训题库练习必须冻结两级题目快照

系统 MUST 在计划发布时冻结规则可使用的题目版本候选池，在每次 Attempt 开始时再冻结本次实际抽中题目。计划候选池 MUST 有规范化
hash；执行生成 MUST 复制并校验该候选池；Attempt MUST 保存随机种子、题目版本、命中规则、判定模式和题序。

#### Scenario: 发布后题库变更不影响开考

- **GIVEN** 计划已经发布并冻结候选题版本池
- **AND** 来源题库随后停用题目或发布新版本
- **WHEN** 生成执行并开始新的 Attempt
- **THEN** 系统仍从发布时冻结版本池抽题
- **AND** 不因来源变化发生候选不足或版本漂移

#### Scenario: 同一 Attempt 重入

- **GIVEN** 学员已经创建包含随机种子和题目快照的 Attempt
- **WHEN** 使用相同业务身份和幂等令牌重新进入
- **THEN** 系统返回原题目集合和题序
- **AND** 不生成新种子或重新抽题
