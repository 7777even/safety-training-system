# Design：培训题库练习与公共抽题闭环

## Context

当前 `TrainingPlanPublishValidator` 通过 `selectCurrentQuestionTypes` 扫描整库题型，只要题库含 `ESSAY` 就返回 `8127`
，与实际抽题规则无关；预检不校验题型、难度、知识点过滤后的数量，也不处理多规则全局去重。真正抽题直到
`ExamAttemptServiceImpl.freezeQuestionBank` 才读取实时题库，并使用 `QuestionBankDrawEngine` 顺序随机抽取，因此发布成功后仍可能因候选不足返回
`7063`。

计划发布快照目前只保存题库标识与执行配置。生成执行仍传实时 `questionBankId`，Attempt
开始时再次读取题目状态和最新已发布版本。计划发布到开考之间的停用、归档、改版会改变执行结果，不符合“发布是版本边界”和可追溯要求。

业务已确认培训题库练习不是考试：整轮不评分、不判断通过/不通过；能自动判断的题目给出对错，不能自动判断的题目只记录作答，不要求人工批阅。现有考试
Attempt、答案、Candidate 和结果投影仍以分数/及格为中心，需要显式区分练习完成事实与考试成绩事实。

约束：

- `git config user.name=lims`，本 Change 仅实施后端、OpenAPI、OpenSpec 和工程记录。
- `docs/api/*.openapi.json` 是唯一机器可读契约。
- 题目版本不可变；Attempt 仍需冻结题干、选项、答案依据和本次随机结果。
- 发布预检是辅助提示，正式发布必须自行重验，不能信任客户端或先前预检。
- 公共抽题能力可复用机械规则，但不得把培训练习的“不评分”扩散到考试、试卷或其他练习场景。

## Goals / Non-Goals

**Goals:**

- 培训题库练习以“全部作答并提交”为完成条件，不形成分数和通过结论。
- `AUTO` 题形成对错；`RECORD_ONLY` 题只形成已作答事实，不进入错题或正确率分母。
- 发布预检、正式发布和开考使用同一候选过滤与全局去重算法。
- 计划发布冻结题目版本候选池；执行生成和开考不依赖实时题库。
- 每个 Attempt 的随机结果由种子复现，同一 Attempt 重入不重新抽题。
- 建立场景无关的公共抽题引擎，允许其他场景按自己的冻结和评分策略复用。
- 结构化返回规则级阻塞项，让调用方能定位阶段、内容、资源和抽题规则。

**Non-Goals:**

- 人工批阅、阅卷人、练习成绩发布、排名、及格线。
- 简答关键词自动判断算法。
- 改变课程题库练习、独立练习、随机试卷和正式考试的现有业务结论。
- 前端页面和交互实现。
- 重算历史 Attempt 或覆盖已发布计划的既有正式快照。

## Decisions

### D1：培训题库练习使用完成事实和判定反馈，不使用成绩事实

新快照版本中的 `QUESTION_BANK` 配置保留 `durationSeconds`、`maxAttempts` 和 `drawRules`，不再要求 `passScore`；规则不再要求
`scorePerQuestion`。为兼容旧调用方，后端可接收历史字段，但新快照忽略其计分语义，OpenAPI 将其标记为该场景已废弃。

本轮提交必须满足全部抽中题目已作答。完成后：

- Attempt 进入结果已生效的终态，培训投影为 `COMPLETED`；
- Candidate 使用练习完成状态，不写 `PASSED/FAILED`；
- `totalScore`、`passScore`、`objectiveScore`、`subjectiveScore`、`finalScore`、`isPassed` 对新练习均为空；
- 保留 `evaluatedCount`、`correctCount`、`wrongCount`、`unassessedCount`、`accuracyRate`；
- `evaluatedCount=0` 时 `accuracyRate=null`，不得伪造 `0%`。

数据库中承载考试分数的非空列需要调整为可空，或增加等价的练习结果资产；实现前以最小且不伪造分数的方案为准，并同步
migration/init/Entity。禁止为了兼容非空列而把未评分练习写成零分不及格。

### D2：单题判定能力由冻结策略决定

候选题在冻结时写入 `evaluationMode`：

- `AUTO`：当前实现具备确定性自动判断能力，交卷后写 `AUTO_CORRECT` 或 `AUTO_WRONG`；
- `RECORD_ONLY`：只确认是否作答，交卷后写 `NOT_EVALUATED`，`isCorrect` 和得分为空。

初始映射为：单选、多选、判断和具备完整答案规则的填空可为 `AUTO`；简答及 `gradingMode=MANUAL` 的题为 `RECORD_ONLY`
。映射由独立策略组件集中维护，不由培训页面或抽题引擎硬编码。简答关键词自动判断在未有已确认算法前不得进入 `AUTO`。

`RECORD_ONLY` 不创建批阅任务、不进入 `PENDING_REVIEW`、不计入错题、不计入正确率分母。交卷后可以按契约展示参考答案/解析，但只能用于学习对照，不能给出对错结论。

### D3：公共抽题内核不依赖数据库和业务 DTO

在考试/题目公共业务层建立纯 `QuestionDrawEngine`，输入规范化候选、规则和随机种子，输出校验或抽题结果。核心模型仅包含：

```text
QuestionCandidate(questionId, questionVersionId, questionType, difficulty,
                  knowledgePointIds, evaluationMode)
QuestionDrawRule(ruleIndex, questionType, difficulty, knowledgePointId, drawCount)
QuestionDrawResult(randomSeed, drawnQuestions)
RuleAvailability(ruleIndex, requiredCount, availableCount, satisfied)
```

引擎负责题型、难度、知识点过滤，全局题目唯一性、可行性匹配和随机复现；不访问 Mapper，不抛培训错误码，不包含分值、及格线、Candidate
或 Attempt。随机试卷需要分值时由试卷场景在公共结果之外附加分值。

候选数据库读取由 `QuestionCandidatePoolService`
统一完成，口径为本机构指定题库中启用、未归档、拥有可用已发布不可变版本的题目，并一次装配题型、难度、知识点和判定能力。预检与正式冻结均调用该服务，消除当前版本与“最新已发布版本”口径分裂。

### D4：使用随机优先级下的全局唯一匹配

顺序 shuffle/截取会让宽规则随机抢占窄规则候选。公共引擎将每条规则展开为题目槽位，以随机种子确定候选优先级，再使用能够回溯重分配的最大匹配完成全局唯一分配。规则
`sortOrder`、规则索引和槽位顺序只决定最终题序和诊断优先级，不牺牲已有合法分配。

由此保证：只要候选与规则存在完整唯一匹配，不同随机种子都能成功；种子只改变选中题目和顺序。校验不需要随机种子即可判断是否存在完整匹配；失败时按稳定规则顺序返回未满足规则，
`availableCount` 定义为全局唯一约束下该规则最多成功分配的槽位数。

### D5：预检返回规则级结构化问题，正式发布原子重验

`TrainingPublishIssueVO` 增加可选字段：

```text
stageId, stageName, contentName, resourceId, resourceName,
ruleIndex, questionType, difficulty, knowledgePointId,
requiredCount, availableCount, suggestion
```

候选不足使用 `400/8166`，消息格式为“第 N 条规则需要 X 题，当前最多可分配 Y 题”。规则字段非法继续使用 `8118`。`8127`
不再表达“整库含简答题”；仅用于候选数据的题型/判分配置无法形成合法 `AUTO` 或 `RECORD_ONLY` 策略等数据异常。

`publish-precheck` 只读返回全部问题。正式 `publish` 在同一事务重新加载候选池、执行同一校验、写冻结池和
hash，再推进计划状态；预检通过不保证后续发布跳过重验。

### D6：计划发布冻结候选版本池，执行生成复制运行池

计划内容 `resourceSnapshotJson` 使用新 `schemaVersion`，保存题库标识、规范化规则、候选版本清单、判定模式和候选池
hash。候选清单按稳定键排序后计算 hash，避免查询顺序造成不同指纹。

为避免超大 JSON 承担运行查询，新增：

- 培训计划题池快照表：按 `planContentId + questionId` 保存发布时 `questionVersionId`、筛选属性、知识点 JSON、判定模式和顺序；
- 考核执行题池表：按 `assessmentExecutionId + questionId` 保存从计划快照复制的运行池，并记录来源计划内容和池 hash。

计划 JSON 快照保留清单摘要与 hash作为审计边界，关系表用于校验和运行查询。正式发布写计划快照；生成执行校验 hash
后复制到执行池。考试域开考只读取执行池，不反查培训表或实时题库。

发布后源题库新增、停用、归档、改版均不改变已发布计划。紧急撤销应通过取消计划或另行确认的资源撤销能力完成，禁止静默替换冻结池。

### D7：Attempt 冻结本次随机结果

每次新 Attempt 生成服务端随机种子，从执行冻结池调用公共引擎，随后冻结题目版本、题干、选项、答案依据、`evaluationMode`
、规则索引和题序。随机种子必须有 Attempt 级明确存储，不再只重复藏在每题正确答案 JSON 中。

同一 `clientAttemptToken` 或已有进行中 Attempt 的重试必须读取原 Attempt 和题目快照。新的合法练习轮次生成新种子，可以抽到不同组合。

执行池缺失、hash 不一致或公共引擎在已经过发布校验的冻结池上失败，均视为执行快照完整性错误，不得回退实时题库；生成执行接口按内容返回失败，开考接口返回稳定业务错误且不留下半个
Attempt。

### D8：练习提交、反馈和培训投影一次闭环

提交前校验所有抽中题目均已作答；未满足时返回可定位的未答数量/题目标识且 Attempt 保持作答中。满足后：

1. `AUTO` 题调用既有客观判定器，写正确/错误；
2. `RECORD_ONLY` 题写 `NOT_EVALUATED`；
3. 汇总可判断数、正确数、错误数、不判定数和正确率；
4. Attempt/Candidate 进入练习完成终态；
5. 同一事务按 `trainingParticipantId + planContentId` 刷新逐内容投影为 `COMPLETED`；
6. 后续计划聚合只读取完成事实，不把练习计为考试成绩或学时。

答案详情对本人返回本人答案。结果生效后，`AUTO` 题可返回对错、正确答案和解析；`RECORD_ONLY` 题返回“不判定”和参考答案/解析，但
`isCorrect=null`。内部批阅意见始终为空。

### D9：公共能力采用渐进接入

本 Change 先让培训计划题库练习完整使用公共候选池和抽题引擎。现有随机试卷、课程题库练习、独立题库练习只允许在测试证明语义不变后迁移机械抽题部分；不得在本
Change 顺带改变其冻结时点、计分、完成或批阅规则。

## Risks / Trade-offs

- [最大匹配比顺序随机更复杂] → 保持引擎纯函数，以性质测试覆盖唯一性、可行性和种子复现；候选规模记录耗时指标。
- [候选池快照较大] → JSON 仅存审计摘要，关系表承担运行；按稳定字段存储，禁止复制题干和选项到计划池。
- [计划发布事务写入行数增加] → 使用批量写入并设置合理单计划候选上限；超过上限返回明确问题，不形成部分发布。
- [历史计划没有发布时题池] → 历史已发布计划保持旧 schema/旧语义；不伪造其发布时快照。若需要迁移必须另有审计标识和人工确认。
- [通用引擎误改考试行为] → 场景适配器持有评分和状态策略；本 Change 的考试/试卷回归只验证未变化。
- [填空题数据不完整却被视为 AUTO] → 判定策略检查答案规则完整性，不完整时降为 `RECORD_ONLY` 或以 `8127` 阻塞，具体以一致且可解释为准。
- [发布预检与发布间题库变化] → 正式发布事务内重新加载、校验和冻结，预检不作为授权事实。

## Migration Plan

1. 新增题池快照和执行池表，调整练习所需结果字段可空性/状态枚举；先部署兼容读写代码和 migration。
2. 部署公共候选池、抽题引擎和新 OpenAPI，但以快照 schema 版本分流。
3. 新发布或重新发布的草稿使用新 schema、不评分配置和发布冻结池。
4. 历史已发布计划、执行和 Attempt 继续旧路径，不重算、不覆盖；管理端读取时标明旧练习结果语义。
5. 观察新计划的预检、冻结、抽题和提交指标后，再评估其他场景迁移公共引擎。

回滚时保留新增表和新快照数据，停止创建新 schema；已按新语义产生的练习事实不得回写成旧计分事实。若代码回滚无法读取新
schema，必须先停止新计划进入，而非删除快照或 Attempt。

## Open Questions

无。以下决策随本 Change 人工确认一并确认：培训题库练习不评分；简答默认只记录作答；全部作答并提交即完成；历史正式事实不重算。
