# Tasks：培训题库练习与公共抽题闭环

> 状态：已完成（2026-09-11）。Owner：lims。仅后端，前端以 OpenAPI 为准另开。

## 1. 发布前形成可执行的公共抽题结论

- [x] 1.1 完成公共候选池、抽题引擎和规则级发布预检

- **状态**：已完成（2026-09-11；QA：`engineering/qa/2026-09-11-training-question-draw-precheck.md`）
- **Owner**：lims
- **输入**：Design D2～D5；`question-draw-engine` 全部规格；`training-execution-loop`「发布预检必须返回规则级可执行性」。
- **输出**：
  1. 场景无关的候选、规则、校验、随机匹配模型和纯抽题引擎；
  2. 统一候选池查询，题目启用/归档/已发布版本/知识点/判定能力口径一致；
  3. 培训发布预检不再整库排斥简答题，按规则返回全局去重后的候选不足；
  4. `TrainingPublishIssueVO` 和 Training OpenAPI 增加规则定位字段及 `8166`；
  5. 题库练习配置契约取消 `passScore`、`scorePerQuestion` 的新语义要求，历史字段标记兼容但不参与新练习计分。
- **前后端影响**：仅后端；前端以 `training.openapi.json` 的新预检字段和配置语义另开，不在本 Task 修改页面或 Mock。
- **API 依赖**：既有 `POST /api/training/plans/{trainingPlanId}/publish-precheck`；扩展响应字段和错误码，不新增权限或路径。
- **数据影响**：本 Task 不落题池表；候选读取涉及真实 SQL、题目版本和知识点关系，必须补 Mapper IT。
- **验收标准**：
  1. 题库含简答题、规则只抽单选时预检可通过；规则抽简答且候选充足也可通过。
  2. 单规则候选不足返回 `8166` 和完整规则定位信息。
  3. 相同题型多规则、宽窄重叠规则按全局唯一匹配判断；存在合法分配时任意测试种子均成功，不存在时稳定定位不足规则。
  4. 难度、知识点、停用、归档、草稿版本过滤与真实候选一致。
  5. 相同池/规则/种子结果可复现；公共引擎不包含培训 DTO、分值和状态逻辑。
  6. `8127` 不再因未命中简答题产生，只用于候选判定策略数据异常。
- **测试方式**：严格 TDD。先补公共引擎单元测试、Validator 零 DB 测试、候选池 Mapper MySQL IT、Controller/OpenAPI
  契约测试并观察目标失败；再最小实现。运行受影响后端模块定向测试与 `git diff --check`。
- **暂停条件**：如果最大匹配诊断无法稳定定义 `availableCount`，或发现题目判定能力无法由现有题型/`gradingMode`
  /答案资产唯一派生，立即暂停并标记“需人工决策”。

## 2. 发布时冻结候选池并生成隔离执行

- [x] 2.1 完成计划发布题池、执行题池和可复现 Attempt 冻结链

- **状态**：已完成（2026-09-11；QA：`engineering/qa/2026-09-11-training-question-practice-closure.md`）
- **Owner**：lims
- **输入**：Task 1 公共候选/抽题能力；Design D5～D7；`training-execution-loop`「培训题库练习必须冻结两级题目快照」。
- **输出**：
  1. 计划题池快照和考核执行题池的 init SQL、增量 migration、schema version、Entity、Mapper；
  2. 正式发布事务内重验、冻结规范化候选版本池并计算 hash；
  3. 生成执行只从发布快照复制题池，并在创建/复用时校验计划内容与池指纹；
  4. Attempt 只从执行池抽题，显式保存随机种子、规则归属、判定模式和题序；
  5. 快照缺失/损坏时稳定失败，不回退实时题库，不留下半个 Attempt。
- **前后端影响**：仅后端；前端接口路径不变，错误语义和快照异常按 OpenAPI 读取。
- **API 依赖**：既有计划 publish、generate-executions、培训 learner enter 和 Attempt 详情；必要的错误码/字段同步 Training 与
  Exam OpenAPI。
- **数据影响**：新增两类题池快照资产并调整 `sts.init.schema-version`；不得修改已执行 migration。若 Attempt
  随机种子或判定模式需要新列，必须同时同步 init、migration、Entity 和读取链。
- **验收标准**：
  1. 预检后题库变化时，正式发布重新校验并冻结发布瞬间候选池。
  2. 发布后题库新增、停用、归档、改版不改变执行池和新 Attempt 的可选版本。
  3. 生成执行重复调用幂等；既有池指纹不同则拒绝复用且不覆盖。
  4. 同一幂等令牌/Attempt 重入返回相同题目和顺序；新一轮可以用新种子得到另一合法组合。
  5. 计划池、执行池和 Attempt 快照的真实 SQL、唯一约束、事务回滚与 hash 校验通过 MySQL IT。
  6. 现有随机试卷、正式考试和课程练习的冻结及评分回归不变化。
- **测试方式**：严格 TDD。先补发布事务、执行生成、Attempt 启动零 DB 测试和题池 MySQL IT观察失败；再实施 migration
  与最小代码。运行受影响模块定向单元测试/IT、migration/init 一致性检查及 `git diff --check`。
- **暂停条件**：发现需要改变题目版本不可变约束、跨域直接读取培训表、历史已发布计划自动回填或覆盖正式快照时立即暂停并取得人工确认。

## 3. 不评分练习提交并回写培训完成事实

- [x] 3.1 完成混合题型作答、自动反馈和培训结果闭环

- **状态**：已完成（2026-09-11；QA：`engineering/qa/2026-09-11-training-question-practice-closure.md`）
- **Owner**：lims
- **输入**：Task 2 冻结 Attempt；Design D1、D2、D8、D9；`learner-exam-reading` 与 `training-execution-loop` 练习结果规格。
- **输出**：
  1. 全部作答门禁和练习完成终态；
  2. `AUTO_CORRECT`、`AUTO_WRONG`、`NOT_EVALUATED` 答案语义；
  3. 可判断数、正确数、错误数、不判定数和可空正确率汇总；
  4. 分数/通过字段对新练习为空，简答不批阅、不进错题；
  5. Attempt、Candidate、培训逐内容投影和计划聚合在同一业务链自动刷新；
  6. Training/Exam OpenAPI、错误码、示例、QA、双轴 Review 和 Retro 同步。
- **前后端影响**：仅后端；前端以 OpenAPI 另开，分别展示“正确/错误/不判定”和练习完成摘要。
- **API 依赖**：既有答案暂存、Attempt 提交/详情、培训学员详情与结果接口；不新增阅卷、发布成绩或权限接口。
- **数据影响**：按 Design D1 使练习分数事实可空或使用等价非伪造资产；新增/调整状态枚举与汇总字段必须同步
  init、migration、Entity、Mapper 和全部调用方。历史 Attempt 不重算。
- **验收标准**：
  1. 混合 8 道可判断题和 2 道简答题全部作答后立即完成；8 题判对错，2 题为 `NOT_EVALUATED`。
  2. 正确率只以可判断题为分母；全部为不可判断题时 `accuracyRate=null`，不显示零分/不及格/零正确率。
  3. 未全部作答时提交被拒绝且 Attempt 可继续，不产生半成品完成投影。
  4. 简答题不产生批阅任务、错题、分数或 `isCorrect=false`；参考答案只作学习对照。
  5. 完成后 Candidate 和培训逐内容投影为完成，练习不进入考试成绩和学时聚合。
  6. 独立考试、课程测验的主观题批阅与成绩发布保持原语义。
  7. 新快照版本使用新语义；历史已发布计划、执行和 Attempt 不重算、不改判。
- **测试方式**：严格 TDD。先补混合题型、全主观、未答提交、错题排除、投影自动刷新、历史兼容的零 DB 测试和必要 MySQL
  IT；再最小实现。完成后执行规格符合性与代码质量双轴 Review、目标端 API 契约校验、真实端到端 QA，更新 `tasks.md`、进度表、QA 和
  Retro。
- **暂停条件**：若现有下游强制要求题库练习必须有 `finalScore/isPassed`，或历史/新快照无法可靠分流，标记“需人工决策”，不得用零分或默认通过绕过。

## 完成门槛

1. 规格符合性：配置、预检、发布冻结、执行生成、随机开考、作答提交、反馈和培训投影使用同一已确认语义。
2. 数据真实性：不判定题不算错、不造零分；新计划使用发布题池，历史事实不重算。
3. 技术闭环：公共引擎纯净可复用，候选 SQL、题池 migration、事务、hash、幂等和隔离均有定向测试。
4. 契约闭环：Training/Exam OpenAPI 与 Controller/DTO/VO/错误码一致，前端无需猜测状态或统计公式。
5. 流程闭环：Review、真实端到端 QA、文档、进度表和 Retro 全部同步后才能关闭 Change。

## 完成记录

- Task 2、Task 3 已按确认顺序完成；最终定向验证为零 DB 182/182、Admin MySQL/Testcontainers IT 8/8、Admin-init IT 1/1。
- 数据基线升级至 `20260911152000`，新增计划题池、执行题池、不评分结果和培训反馈摘要 migration。
- Training/Exam OpenAPI、QA、规格符合性与代码质量双轴 Review、Retro 和进度表已同步。
- MySQL VPN 环境不可连接不影响本轮真实 SQL 验证；所有新增表、列和约束均已在 MySQL 8.0 Testcontainers 中验证。
