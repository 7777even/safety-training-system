# 2026-09-18 变更同步说明（培训成绩闭环 / 考试流程统一 / 跨端展示）

**作者：** fxw11（部分条目由 Codex 会话产出，已在文中标注负责人）
**范围：** 本日 16 个 commit（15 个按能力拆分 + 1 个文档同步），涉及培训成绩与结果同步、
考试工作台（题库练习 / 线下考试 / 草稿发布）、填空题作答、菜单权限、门户与移动端学员端、管理端工作台视觉规范
**用途：** 同步给同事，说明「之前是什么状态、缺了什么、卡在哪、这次补了什么、闭环到哪一步、哪里还没验」
**不含：** 纯字段显示补充与样式/小 bug 类改动（如培训计划列表补三列、防作弊提示条宽度、未声明事件透传），
这类只列在第四节提交清单里，不单独解释

---

## 零、一句话结论

今天真正要同步的不是「又做了几个页面」，而是**四条断掉的链**和**一套被统一的口径**：

1. **培训课程成绩永远是空的**——课程测验早就出分，但没人把课程测验的分写进课程报名 `final_score`，
   培训综合成绩因此拿不到课程分（功能项 56 / 74 承诺的能力一直没落地）。
2. **题库练习和培训题库练习「同名不同义」**——独立工作台的 `BANK_PRACTICE` 被当成正式考试处理：
   要配及格比例及格分、要配批阅人、简答题要进待批阅、学员端挂着一个不会生效的「及格分」。
3. **草稿考试只要配过人就发不出去**——发布时把「人员配置」误判为不可逆业务事实，直接返回 7003。
4. **填空题的多空作答等于没作答**——前端一个输入框塞多个空位，暂存进 `answerText` 永远命中不了
   后端按 `BLANK` items 的判分，学员填对了也判不出来。

另外统一了一套**学员端与工作台的展示口径**（门户三中心 / 移动端三页 / 批阅页 / 工作台 `ws-*` 规范），
这部分的改动量最大，但对同事的影响主要是「照抄哪套结构」，不涉及业务语义。

---

## 一、缺漏的闭环补充项（承诺过或设计上应当闭环，本次补齐）

### 1.1 培训课程正式成绩（Change `2026-09-18-close-training-course-score-loop`，Owner：fxw11）

| 项 | 说明 |
|---|---|
| **之前的情况** | 课程内测验（`COURSE_QUIZ`）早就产生生效最终分，培训结果却读课程报名的 `finalScore`；该字段**从来没有任何代码写过**。表现是：课程已完成、章节测验有分，培训工作台里课程成绩仍是空，综合成绩算不出课程那一项。 |
| **缺少了什么** | 完全没有「课程内测验 → 课程正式成绩」的汇总环节。培训域要读课程的正式分，但**不掌握也不会算**课程内测验的完成规则范围（哪些测验算入、是否要求课程完成）。课程域也没有对外暴露这个计算能力。 |
| **为什么无法闭环** | 两侧都不该越界：培训域直接读 `ExamCandidate` 会绕过课程完成规则；课程域自己往 `Enrollment.final_score` 写又会与独立课程语义混在一起。没有一份「谁算、按什么算、什么时候算」的口径，就只能一直空着。 |
| **做了什么修复** | ① 新建唯一计算口径 `TrainingCourseScoreCalculator`：**仅培训绑定**（报名有 `trainingParticipantId` 且开课绑定了计划内容项）**且课程已完成**时，取完成规则范围内 `COURSE_QUIZ` 项的**当前生效最终分算术平均**（2 位小数）；任一纳入测验未出分则返回 null；**0 分是有效成绩**；题库练习不参与。② 课程侧通过内部端口 `TrainingCourseExecutionPort.reconcileTrainingFinalScore(offeringId, participantId)` 暴露，培训域不直接访问课程成绩事实。③ 培训结果同步（`synchronizeResults`）前先补历史：**只对未冻结参训人**补空分并重建投影。 |
| **闭环到哪一步** | 课程内测验出分即自动写入课程正式成绩（培训绑定场景），培训结果与综合成绩能拿到课程分；历史空分在同步时补齐；**独立课程、独立报名、题库练习、已冻结参训人、已有非空成绩、获得学时全部不变**。 |
| **验证证据** | 定向零 DB **91/91**（`CourseLearningServiceImplTest` / 新增 `TrainingCourseScoreCalculatorTest` / `TrainingWorkflowServiceImplTest` / `TrainingResultSyncServiceImplTest`）；覆盖多测验平均、0 分参与、题库练习排除、独立课程隔离；`openspec validate --strict`、`git diff --check` 通过；QA/Retro 已产出。 |
| **遗留** | `TrainingMixedPlanLifecycleIT` 因 **Docker Hub 拉取 `testcontainers/ryuk` 超时**未进入业务断言（**没有伪报通过**）；真实登录态与历史补账验收待执行。 |

### 1.2 题库练习与线下考试的流程统一（Change `2026-09-18-unify-question-bank-practice-review-flow`，Owner：fxw11）

| 项 | 说明 |
|---|---|
| **之前的情况** | 培训题库练习是「不评分练习」，独立考试工作台里的 `BANK_PRACTICE` 却是**正式考试语义**：创建/更新必填及格比例与及格分，发布时按 `Σ(抽题数 × 每题分值)` 冻结总分并按及格分冻结及格线，交卷后客观题自动判分、**简答题进入待批阅**，还要管理端「发布成绩」；学员端练习挂着一个永远不会生效的及格分。线下考试（`OFFLINE`）同样被要求配批阅人。 |
| **缺少了什么** | 「哪些考试类型真的需要人工批阅、哪些真的需要及格线」没有收敛成一条口径。结果是：练习被迫走批阅与发布流程（实际上没人批）、线下考试被要求配一个不参与录分的批阅人。 |
| **为什么无法闭环** | 判分、成卷、发布三处分散判断——`AssessmentExecutionServiceImpl` 管冻结口径、`ExamAttemptServiceImpl` 管交卷归档、`ExamServiceImpl` 管创建校验，只有三处口径一致才能真闭环；此前只统一了「培训题库练习」一路。 |
| **做了什么修复** | ① **创建/更新**：题库练习不再要求及格比例与及格分，落库以 0 占位；移除「及格分不能超过抽题总分」的 7063 校验；非题库练习仍要求五项完整。② **冻结**：`BANK_PRACTICE` 的 `totalScoreSnapshot`/`passScoreSnapshot` 一律置空。③ **交卷**（`isUnscoredTrainingPractice` → `isUnscoredQuestionBankPractice`）：稳定识别「独立（`assessmentKind=BANK_PRACTICE`）」与「培训（`TRAINING_PRACTICE` + 总分为空）」两路；客观题自动判分并重算得分，简答题只记录作答；**交卷即 `RESULT_EFFECTIVE`**，同步候选人分数，不再进入 `PENDING_REVIEW`/`PENDING_PUBLISH`；重复调用发布接口幂等返回。④ **新增成绩发布状态 `EFFECTIVE`（已生效）**，贯通学员任务/作答、批阅查询 mapper 的 CASE 与筛选、管理端状态、`exam-review-state`/`score-disposition-state`。⑤ **发布门禁**：批阅人仅对 `ONLINE`/`SPECIAL_PRACTICE` 展示、保存与校验，题库练习与线下考试只要求考生。⑥ **前端**：抽题规则由内联表格改为弹窗 `ExamDrawRuleDialog`，按真实候选池（题型/难度/知识点，仅已发布且启用）限制重抽与超抽；题库练习表单隐藏及格比例与及格分数；mock 同步。⑦ **线下考试**：满分默认 100，及格分高于 100 时满分同步上移（后端、录分弹窗上限、mock 的 7068 区间三处一致），录分时把考试开放开始时间写入线下场次开始时间。 |
| **闭环到哪一步** | 练习就是练习：抽题 → 作答 → 自动形成可核对得分 → 立即生效，不再出现及格结论、待批阅任务与「发布正式成绩」入口；线下考试只在录分环节依赖 `exam:score:publish`，不再要求批阅人。 |
| **验证证据** | 后端定向单测（`AssessmentExecutionServiceImplTest` / `ExamAttemptServiceImplTest` / `ExamLearnerServiceImplTest` / `ExamServiceImplTest` / `OfflineScoreServiceTest`）、PC 定向 Node、PC 与移动端 `vue-tsc`、ESLint、OpenAPI JSON 解析、`git diff --check` 全部通过；契约（`docs/api/exam.openapi.json`）同步更新。 |
| **遗留** | ① 抽题规则弹窗与新口径**只在 mock 与单测层闭环，未做真实环境点击验收**。② **历史题库练习作答仍带旧的总分/及格线快照**，按旧语义展示，未做数据迁移（要迁移需单独确认）。 |

### 1.3 草稿考试发布无法刷新执行快照（Change `2026-09-18-fix-draft-exam-publish-refresh`，Owner：fxw11）

| 项 | 说明 |
|---|---|
| **之前的情况** | 考试在**人员配置阶段**就会提前物化考核执行对象并保存考生、阅卷人；等到发布时 `refreshStandalone` 需要刷新最终资源与策略快照，却把「人员配置记录」也算成不可逆业务事实，于是**草稿只要配过人，发布就返回 7003**。管理员的操作顺序（先配人再发布，还是先发布再配人）直接决定能不能发出去。 |
| **缺少了什么** | 「刷新快照前到底要拦什么」的判据过宽：真正不可逆的是**作答、冻结题池、课程考核结果**，人员配置是可保留、可重算的。另外刷新方式只能整体替换执行对象，替换就会丢人员关联。 |
| **为什么无法闭环** | 快照刷新与人员关联绑在同一个对象上：不删原执行对象就无法刷新（尤其是摘要/唯一键变化时），删了又丢考生与阅卷人。 |
| **做了什么修复** | ① `assertExecutionUnused` → `assertExecutionRefreshable`：只拦作答、冻结题池、课程考核结果，**人员配置不再阻断**。② 新增 `copySnapshot`：**原地更新**快照并保留执行对象 ID 与考生/阅卷人关联。③ 并发更新失败仍返回 7004，存在业务事实仍返回 7003（语义未变）。 |
| **闭环到哪一步** | 「先配人 → 再发布」的正常操作顺序可用；发布会刷新资源与策略快照但不动人员。 |
| **验证证据** | `AssessmentExecutionServiceImplTest` 新增「已配置人员后刷新成功」回归；`DraftExamParticipantLifecycleIT` 同步为刷新 / 删除 / 唯一键复用场景。 |
| **遗留** | tasks 中「部署环境下题库练习真实发布验收」**未勾选**，需在可用环境补一次真实发布。 |

### 1.4 填空题按空位作答（Change `2026-09-18-fix-fill-blank-answering`，Owner：Codex）

| 项 | 说明 |
|---|---|
| **之前的情况** | 填空题在前端被当成普通文本题：**一个输入框承载多个空位**，界面既不告知分隔符，暂存也只写 `answerText`。后端多空自动判分只读 `BLANK` items 并按 `itemKey` 匹配冻结的 `blankNo`，因此**学员填对也判不出分**，也不知道该填几个空。 |
| **缺少了什么** | 作答快照没有「这题有几个空」这个信息。后端不给数量，前端只能猜；猜错就整题错。而正确答案又不能下发（脱敏规则）。 |
| **为什么无法闭环** | 「空位数量」本身不属于答案，但当时没有任何一个既安全又能表达它的字段，所以只能退化成单输入框。 |
| **做了什么修复** | ① 契约（`ExamAttemptQuestionVO` / `docs/api/exam.openapi.json` / `@shared/types/learner-exam`）新增**只读 `blankCount`**：后端从服务端冻结答案快照的 `blanks` 数量派生，**只下发数量**，`correctAnswerSnapshotJson` 仍按原脱敏规则处理。② PC 作答页、移动端独立考试作答页、移动端课程考核作答页统一改为「第 N 空」逐空输入框，暂存为 `itemKey = 1..blankCount` 的 `BLANK` items，**全部空位填写才算已答**。③ 兼容历史单空作答（第 1 空回填旧 `answerText`），多空历史答卷不静默改写既有成绩。 |
| **闭环到哪一步** | 填空题作答、恢复、答题卡已答状态与自动判分走同一条链；跨端（PC / H5 / 移动课程考核）口径一致。 |
| **验证证据** | `ExamLearnerServiceImplTest` **23/23**（覆盖两空 `blankCount=2` 与未发布答案快照的脱敏）；PC 跨端定向回归 **4/4**；PC 与移动端 `vue-tsc`、PC 作答页 ESLint、OpenAPI JSON、`openspec validate --strict`、`git diff --check` 通过；QA/Retro 已产出。 |
| **遗留** | 没有可用的非生产学员登录态与含多空题的冻结作答，**未做真实 PC/H5 浏览器验收**；移动课程考核页仍被提交前已存在的 ESLint `Event` 全局声明问题阻断（非本次引入）。 |

### 1.5 菜单权限节点缺口（Change `2026-09-18-sync-teacher-task-menu`，Owner：fxw11）

| 项 | 说明 |
|---|---|
| **之前的情况** | 线上菜单树里已经出现「教学任务」页面节点，但**没有 `training:teacher:task` 权限、也不归属应用中心模块**，初始化菜单（`menus.json`）里更是完全没有这个节点；门户培训中心缺 `training:learner:task` 按钮节点。结果是**新建库与线上菜单配置不一致**，教师进不去教学任务，学员培训链路在菜单层面没有授权入口（与 `docs/architecture/menu-role-matrix.csv` 里标注的「缺 `training:learner:task` 按钮节点」对应）。 |
| **缺少了什么** | 菜单是**运行期数据**，只改种子文件修不了已部署库：`menus.json` 只插入缺失 ID，不会去修已有 `9565` 的空权限。所以既缺种子节点，也缺一条能把已部署库修回来的迁移。 |
| **为什么无法闭环** | 以往菜单问题只靠改 `menus.json`，在已有环境上不生效；而手工改库又不允许（禁止直接改数据库）。 |
| **做了什么修复** | ① `menus.json` 新增 `9565` 教学任务（`training:teacher:task`，`/admin/business-center/training/teacher-tasks`）与 `9564` 培训列表按钮（`training:learner:task`），并修正培训计划菜单的**失效路由 `program` → `plan`**。② 前端菜单树基准 `current-online-menu-tree.json` 同步。③ 新增**幂等 Flyway 数据迁移** `V20260918154500__sync_teacher_task_menu.sql`（固定菜单 ID + `ON DUPLICATE KEY UPDATE`），用于修复已部署库的空权限节点。④ 菜单角色矩阵 CSV 补两个节点、去掉缺权限标注。 |
| **闭环到哪一步** | 新建库与线上菜单配置一致；已部署库可通过 migration 收敛到同一状态，重复执行不产生重复菜单。 |
| **验证证据** | `MenuPermissionConsistencyTest` **2/2**、`FlywayMigrationResourcesTest` **1/1**、MySQL Testcontainers `FlywayMigrationIT#shouldRepairExistingTeacherTaskMenuAndAddLearnerTrainingPermission` **1/1**（覆盖已有空权限 `9565` 修复、`9564` 新增、重复执行不再迁）；两个 JSON 解析与 `git diff --check` 通过。 |
| **遗留** | 已部署环境若 `main.flyway.enabled=false`（当前 `application-vpn.yml` 即是），**migration 不会自动执行**，需按既有流程手工执行 SQL；`menus.json` 里仍有若干后端权限码没有对应菜单节点，属既有缺口，未在本次范围。 |

---

## 二、功能点补充项（此前未实现，本次新增）

| # | 功能点 | 之前 | 本次做了 | 验证 |
|---|---|---|---|---|
| 1 | **管理端工作台视觉规范 `ws-*`** | 概览卡、指标、字段表、核查清单各页面自己定义，同一个「卡 + 标签 + 指标」在不同工作台长得不一样；页头普遍只有标题，看不到对象状态 | 新增 `src/styles/admin-workspace.less`（402 行）承载 `ws-hero`/`ws-section`/`ws-kv`/`ws-metric`/`ws-check-list`/`ws-tip`；试卷工作台、已发布试卷版本、评价表单工作台与只读版本补页头状态标签；阶段表单改三列、评价表单标签列加宽 | `vue-tsc` exit 0、改动文件 ESLint 0 warning、新增 2 个测试文件用例通过（**未做真实浏览器点击**） |
| 2 | **门户三中心信息层级** | 学员端卡片字段散乱：得分用内联 `style="color:green/red"` 上色、状态 Tag 与散装字段混排、封面比例不统一，看不出哪个是身份哪个是事实 | 学习 / 考试 / 培训中心统一为「类型角标 → 身份（标题+副标题）→ 状态 Tag → 键值对齐的事实表 → 结论块」；分数改三态语义药丸（**卡片内联色值 0**）；开放时间收敛为「不限制开放时间 / 截至 X / X 起 / X ~ Y」四种文案；课程学习页改为两端对齐信息区并新增右下角**滚动辅助工具**（长按连续滚动、一键到顶/底，按 `overflowY` 向上解析真实滚动容器） | 真实渲染（Chrome + vite 5173 + 后端 20080）：考试中心 5 卡内联色值 0、学习中心进度 33.33%、课程学习页 chips=3 且无重复 `ElAlert`、一键到底 `scrollTop=43430`（= max）、标准发布确认「共 4 条 · 启用 4 条」，`webErrors: []` |
| 3 | **移动端三页对齐 PC 字段口径** | 移动端与 PC「同源不同口径」：PC 有任务状态筛选与「可报名课程」入口而移动端完全没有；卡片不展示考试类型与开放时间区间（会出现「未设置 至 未设置」）；分数用进度条表达 | 三页统一骨架（hero 横幅 → 卡片身份 → facts 字段面板 → 高亮块 → 右对齐主按钮）；字段口径抄 PC（`compactDateTime` / `getOpenTimeText` / `getPeriodText` / `getTrainingPeriodText` / `getSourceText` / 得分三态）；学习页补 `query.learningStatus` 筛选与「可报名课程」区块（`GET /api/course/learning/offerings` + `POST /api/course/offerings/{id}/self-enroll`，复用既有契约，无后端与权限变更）；考试页补 `query.taskStatus` 筛选 | 真实渲染（Chrome 390×844 + uni H5 5174 + 真实登录）：筛选真实发出 `query.learningStatus=COMPLETED`、`query.taskStatus=RESULT_PUBLISHED`；DEMO 环境「可报名课程」接口真实返回空列表，用请求拦截注入契约数据验证渲染与选课成功；`vue-tsc` exit 0、6 文件 ESLint 0 error/0 warning |
| 4 | **教师批阅页改双栏答案对照** | 50% 抽屉里的 4 列表格（长文本折叠、评分要三步），没有任何批阅进度反馈 | 单题批阅卡：「学员答案 \| 参考答案」双栏（左栏品牌色承重、右栏中性，未作答时左栏降为中性，两栏天然等高、窄视口回落单栏）+ 圆形题号徽章 + 满分/状态 Tag + 快捷给分（满分 / 半分 / 0 分）+ 顶部批阅完成度 | 新脚本 **42/42**（双栏几何等高无裁切、长文本压力后仍并排、快捷给分草稿不污染服务端事实、全程 0 个非 GET 请求） |
| 5 | **成绩处置页顶部被压缩** | 成绩发布流水一变长，顶部最终成绩与动作区就被压扁 | 根因是承载卡在 flex 里被拉成确定高且内部 grid 用默认 `auto` 行（流水 1 行时动作区 120px，31 行时掉到 65px），叠加摘要区写死 `height: 108px` 裁切。改为 `flex: 1 0 auto` + `grid-auto-rows: max-content` + 滚动只留页面容器 + 摘要改 `min-height` | DOM 克隆表格行施压（1 行 vs 31 行）两视口各 18 项全 PASS，摘要 116px / 动作区 65px / 核查区 72px 在两种行数下完全相等 |
| 6 | **考试列表与详情回显资源名称** | 列表只显示 `paperVersionId`，详情找不到引用时回退成「试卷版本 #30」「题库 #70」，管理员不跳去资源管理页就确认不了绑定关系 | `ExamVO` 新增只读 `paperName` / `paperVersionNo` / `questionBankName`（无新接口、无新增请求字段，资源已删除时保持空值、不把内部 ID 当展示名）；PC 列表补「考试类型」与开放开始/结束时间 | `ExamServiceImplTest` 45/45、PC 定向 Node 名称回显断言、`pnpm typecheck`、目标 ESLint |
| 7 | **培训计划列表补安排信息** | 列表只有编码、名称、性质、最低学时与状态，识别计划要进工作台 | 新增培训时间、培训地点、创建时间三列（复用既有 `TrainingPlanVO` 字段，地点空显示 `-`） | PC 定向 Node 6/6、目标 ESLint、typecheck |

---

## 三、对同事有影响的口径变化（务必知悉）

1. **题库练习不再有及格分与及格比例**：创建/更新接口不再要求这两项，落库以 0 占位；执行快照的
   `totalScoreSnapshot`/`passScoreSnapshot` 对 `BANK_PRACTICE` **一律置空**；前端不得再为题库练习
   展示及格分数与及格比例。**别再按「练习也要及格线」去写校验或文案。**
2. **新增成绩发布状态 `EFFECTIVE`（已生效）**，同时成绩流水快照新增 `NOT_APPLICABLE`（不判定）。
   共享类型 `scorePublishStatus` 已扩为 `'PUBLISHED' | 'UNPUBLISHED' | 'EFFECTIVE'`
   （`@shared/types/learner-exam`、`course-learning`），**前端所有对 `scorePublishStatus` 的判断都要穷举新取值**，
   管理端筛选与 mapper 的 CASE 分支已同步。
3. **发布门禁按考试类型收敛**：批阅人只对 `ONLINE` / `SPECIAL_PRACTICE` 展示、保存与校验；
   `BANK_PRACTICE` 与 `OFFLINE` **只要求至少一名考生**。线下录分由 `exam:score:publish` 权限控制，
   **该权限不是批阅人配置**。
4. **`paperScorePerQuestion` 变为历史兼容字段**：题库练习未提交时，客观题按默认 1 分形成核对得分，
   简答题不产生分值；旧口径的「及格分超过抽题总分 7063」校验已删除（仅保留「未配置规则」的 7063）。
5. **线下考试满分规则**：默认满分 100，**及格分高于 100 时满分同步为该及格分**；录入成绩必须落在
   0 到执行快照满分之间（7068 的 example 已从「0 到 100」改为「0 到执行快照满分之间」）；
   录分时会写入考试开放开始时间作为线下场次开始时间。
6. **填空题契约新增只读 `blankCount`**；答案项改为 `itemKey = 1..N` 的 `BLANK` items，
   **全部空位填写才算已答**；历史单空作答仍兼容回填旧 `answerText`。跨端渲染必须用 `blankCount` 决定输入框数量。
7. **`ExamAttemptQuestionVO.startedAt` 语义扩展**：线上考试=开始作答时间，**线下考试=考试开放开始时间**（契约描述已改）。
8. **菜单新增 `9564`（培训列表按钮，`training:learner:task`）与 `9565`（教学任务，`training:teacher:task`）**；
   已部署库需执行**幂等迁移** `V20260918154500__sync_teacher_task_menu.sql`
   （当前 `application-vpn.yml` 里 `main.flyway.enabled=false`，**不会自动执行**，按既有流程手工执行）；
   培训计划菜单路由由 `program` 修正为 `plan`。
9. **培训课程的 `final_score` 现在会被写入**（仅「培训绑定 + 课程已完成」场景，取完成范围内课程测验正式分平均）；
   独立课程与独立报名不受影响；培训结果同步接口的描述已更新（**同步前会为未冻结参训人补齐缺失课程成绩**）。
10. **草稿考试发布不再因「已配人员」返回 7003**：存在作答、冻结题池、课程考核结果才拒绝；
    并发更新失败仍为 7004，刷新为原地更新、不改变执行对象 ID、不增删考生或阅卷人。
11. **前端 mock 已同步新口径**（`BANK_PRACTICE` 无及格线、`EFFECTIVE`、线下满分上移、抽题规则弹窗），
    用 mock 调试的同事请拉最新代码，否则会看到与新契约不一致的假数据。

---

## 四、本日提交清单（`main`，自 `b687daad` 起，按能力拆分）

| # | Commit | 类型 | 内容 |
|---|---|---|---|
| 1 | `1ad076e7` | feat | **培训课程正式成绩闭环**（计算口径 / 内部端口 / 历史补账） |
| 2 | `5b3db786` | fix | **菜单同步**：教学任务 9565、培训列表按钮 9564 + 幂等迁移 |
| 3 | `d7c249fe` | feat | 培训计划列表补培训时间、地点与创建时间 |
| 4 | `4926ac57` | refactor | 管理端工作台 `ws-*` 视觉规范 + 页头状态标签 |
| 5 | `6e0427f1` | feat | **题库练习流程统一 + 草稿考试发布快照刷新**（两个 Change 合并，见下） |
| 6 | `2c030884` | fix | **填空题按空位作答**，跨端统一 `BLANK` 答案项 |
| 7 | `cce2fa20` | feat | 考试列表与详情回显试卷、题库名称 |
| 8 | `dff07e7a` | feat | 门户三中心与课程学习页信息层级重构 |
| 9 | `882f10d0` | feat | 移动端三页对齐 PC 字段，补筛选与可报名课程 |
| 10 | `cfb5b56c` | feat | 批阅页改双栏答案对照，修复成绩处置页顶部被压缩 |
| 11 | `14ba7d28` | test | 锁定考试工作台事件透传回归 |
| 12 | `2a100051` | test | 补齐集成测试装配新增的构造参数 |
| 13 | `b620c40b` | fix | 收窄防作弊提示条宽度避免右侧溢出 |
| 14 | `ba6f2df2` | chore | 归档 2026-09-15～17 已完成变更至 `archive/` |
| 15 | `618dc640` | docs | 修正归档文档中的培训计划路由与表格格式 |
| 16 | *(本说明所在提交)* | docs | 进度表 3.12 增量记录 + 本同步说明 |

**关于拆分粒度**：第 5 个提交含两个 Change（`unify-question-bank-practice-review-flow`、
`fix-draft-exam-publish-refresh`）——两者都改 `AssessmentExecutionServiceImpl` 的独立考试执行对象与
题库练习冻结口径、并共用同一份测试类，按 hunk 拆会让其中一个提交引用尚未落地的符号，故合并为一个提交、
正文分节说明。同理填空题的后端 hunk 与题库练习的判分改动同处 `ExamLearnerServiceImpl`，
已按 hunk 精确剥离（`setBlankCount` / `resolveBlankCount` 归填空题提交）。

**说明（本地联调配置）**：本次未提交任何本地环境改动。`application.yml` 的 profile（`test` → `vpn`）、
`pc-web/vite.config.ts` 与 `mobile-app/vite.config.ts` 的代理指向都属个人联调配置，**保持在工作区外、不进主线**；
需要连远程库或局域网后端联调的同事请自行临时切换，勿提交。

---

## 五、验证覆盖程度（诚实说明）

**有真实环境证据的：**

- 门户三中心 / 课程学习页 / 标准发布确认面板：真实 Chrome + vite 5173 + 后端 20080 + 真实登录（内联色值 0、
  滚动辅助工具一键到底/到顶、`webErrors: []`）。
- 移动端三页：真实 Chrome 390×844 + uni H5 dev 5174 + 真实登录（筛选取值真实下发；可报名课程用请求拦截
  注入契约数据验证渲染与选课）。
- 教师批阅页 / 成绩处置页：真实浏览器几何断言（42/42、18/18），含 DOM 克隆施压。
- 菜单迁移：MySQL Testcontainers 上 `FlywayMigrationIT` 1/1（真实 MySQL、真实 SQL）。

**只有单测 / 定向测试 / 静态验证的（未做真实点击）：**

- 培训课程成绩闭环：定向单测 91/91 + OpenSpec strict，**MySQL IT 因 Docker Hub 拉 `ryuk` 超时未跑**，
  真实登录态与历史补账未验。
- 题库练习流程统一：后端定向单测 + PC/移动 `vue-tsc` + ESLint，**抽题规则弹窗与新口径未做真实点击**。
- 草稿考试发布刷新：单测 + `DraftExamParticipantLifecycleIT`，**部署环境题库练习真实发布未验**。
- 填空题：后端 23/23 + PC 定向 4/4 + 两端 typecheck，**无真实多空题作答数据，未做 PC/H5 浏览器验收**。
- 管理端 `ws-*` 规范：`vue-tsc` + ESLint + 源码断言，**未做真实浏览器逐页复核**。
- 考试资源名称回显：单测 45/45 + 定向 Node 断言。

---

## 六、遗留与需人工决策

**需人工决策：**

1. **历史题库练习作答的数据迁移**：现存作答带旧的总分/及格线快照，当前按旧语义展示。
   是否需要清洗（例如把旧快照的及格线置空、状态对齐 `NOT_APPLICABLE`）属数据变更，需确认后再动。
2. **4 个 Change 缺 QA/Retro 记录**：`sync-teacher-task-menu`、`unify-question-bank-practice-review-flow`、
   `fix-draft-exam-publish-refresh`、`display-exam-resource-names` 只有 `tasks.md` 验证记录，
   未按 L3 流程补 `engineering/qa|retro/2026-09-18-*.md`。
3. **批阅页对 `EFFECTIVE` 的整体只读**：`exam-review-state.isScorePublished()` 把题库练习的自动生效
   也算作「已发布」，于是批阅页整页只读（含 `ElAlert` 与禁用态），而概览的得分进度可能仍是 0/2，
   观感上像矛盾。这是既有语义、非本次引入，要改需与维护该文件的改动一起确认。
4. **`TrainingMixedPlanLifecycleIT` 的环境依赖**：Docker Hub 拉取 `testcontainers/ryuk` 超时。
   需要一条可用的镜像获取路径（预拉镜像 / 内网 registry）才能补这次课程成绩的 MySQL 级验证。
5. **已部署环境的 migration 执行方式**：`main.flyway.enabled=false` 下菜单迁移不会自动跑，
   是否统一改为「发布时按清单手工执行」仍需确认。

**已知未做（范围外）：**

- 题库练习「历史作答口径」与线下考试真实录分链路的端到端验收。
- 移动端「可报名课程」在真实有数据环境下的验收（DEMO 环境接口返回空列表，未构造 PUBLISHED + OPEN 开课）。
- 移动端课程考核页既有 ESLint `Event` 全局声明问题（提交前已存在，非本次引入）。
- 仿真软件 / 教学 / 练习 / 考试、防作弊与人脸识别本期延期；HLS 与派生媒体预览仍待 M5 后另立 Change。
