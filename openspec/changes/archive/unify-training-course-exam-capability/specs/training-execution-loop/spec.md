# Spec Delta：training-execution-loop

## MODIFIED Requirements

### Requirement: 四类培训内容均可真实执行

系统 MUST 支持 COURSE、EXAM、QUESTION_BANK 和 OFFLINE。EXAM MUST 区分 QUIZ 和 FORMAL_EXAM；QUESTION_BANK MUST
形成题库练习，不得替换为专项练习。最小版本的 QUESTION_BANK MUST 只包含可自动判分题型。EXAM 发布快照 MUST
自足并版本化，包含完整考试策略和批阅人；计划专属执行生成 MUST NOT 依赖来源 Exam、Policy、Execution 或 Reviewer
的当前状态。包含非自动判分题目的计划专属考试 MUST 创建计划专属 Reviewer。QUESTION_BANK 首次 Attempt MUST
按冻结抽题规则生成可复现的题目、顺序、分值和随机种子快照；TRAINING_PRACTICE 缺少有效冻结抽题规则时 MUST 拒绝开考，不得回退到课程题库全量题目。

COURSE 生成 MUST 调用开课能力：创建草稿开课（写入冻结 `courseVersionId`）、发布开课、再以 TRAINING 来源报名。MUST NOT
把开课直接插入为已发布。EXAM 与 QUESTION_BANK 生成 MUST 调用考试能力：创建草稿计划专属 Exam、写入冻结策略与阅卷人、发布考试、再以
TRAINING 来源绑定考生。MUST NOT 由培训适配器直接将考试标为已发布。

开课发布与考试发布 MUST 与独立管理端使用同一套实现。计划内容 EXAM/QUESTION_BANK MUST 保持为考试对象；课程章节测验和题库练习
MUST 仅随 COURSE 开课执行。

#### Scenario: 生成真实执行

- **GIVEN** 已发布计划包含四类内容，且 COURSE 无需阅卷人或已配齐阅卷人
- **WHEN** 管理员生成执行
- **THEN** 系统通过开课发布得到 CourseOffering/Enrollment，通过考试发布得到计划专属
  Exam/AssessmentExecution/Candidate，并得到 BANK_PRACTICE 与 OfflineSession
- **AND** 开课与考试的 `publishStatus` 为 `PUBLISHED`

#### Scenario: 培训课程缺阅卷人时开课保持草稿

- **GIVEN** COURSE 含主观题测验且开课执行未配置阅卷人
- **WHEN** 管理员生成执行
- **THEN** 开课以草稿保留，该内容失败并返回 5068
- **AND** 该开课 `publishStatus` 不是 `PUBLISHED`
- **AND** 不创建培训来源报名、不写入 COURSE 执行关联
- **AND** 其他内容的成功生成不受影响

#### Scenario: 计划专属考试与独立考试走同一发布

- **GIVEN** 已发布计划包含 EXAM 或 QUESTION_BANK
- **WHEN** 系统生成该内容执行
- **THEN** 计划专属 Exam 经考试发布接口变为 `PUBLISHED`
- **AND** 考核执行按该考试的 `planContentId` 与 `examType` 物化来源，而不是旁路改状态

#### Scenario: 题库练习缺少配置

- **GIVEN** QUESTION_BANK 内容缺少时长、及格线、次数或抽题规则
- **WHEN** 管理员发布计划
- **THEN** 系统拒绝发布且不猜测业务默认值

#### Scenario: 计划专属考试使用发布快照

- **GIVEN** 来源考试的当前策略或批阅人已变化
- **WHEN** 系统为已发布计划生成 EXAM 执行
- **THEN** 计划专属 Exam 与 AssessmentExecution 使用发布时冻结的资源、策略和批阅人快照
- **AND** 不读取来源考试的当前状态

#### Scenario: 题库练习按冻结规则开考

- **GIVEN** QUESTION_BANK 内容已冻结抽题规则
- **WHEN** 学员首次进入并开考
- **THEN** 系统按冻结规则生成可复现的题目、顺序、分值和随机种子快照
- **AND** Candidate 由同一冻结计分规则形成正式结论

#### Scenario: 题库练习缺少冻结抽题规则

- **GIVEN** TRAINING_PRACTICE 没有有效冻结抽题规则
- **WHEN** 学员开考
- **THEN** 系统拒绝开考
- **AND** 不使用课程题库的全量题目兼容分支
