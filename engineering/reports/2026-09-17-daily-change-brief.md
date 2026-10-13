# 2026-09-17 变更同步说明（课程学习 / 考试闭环补齐）

**作者：** fxw11
**范围：** 本日提交的 10 个 commit，涉及课程学习、课程测验、考试工作台、题库、素材与共享层
**用途：** 同步给同事，说明「之前是什么状态、缺了什么、卡在哪、这次补了什么、闭环到哪一步」
**不含：** 添加字段显示、样式微调、小 bug 类改动（如需逐条 diff 见各 commit）

---

## 零、一句话结论

今天真正解决的不是「又做了几个页面」，而是**三处承诺了但没生效的能力**：

1. **课程学习进度不可信**——学员端上报的有效学习时长在大多数场景恒为 0，管理端配的学习时长阈值
   全是死字段，进度既算不对也拦不住。
2. **四类考试里有两类没有闭环**——线下考试（OFFLINE）既没有成绩入口也没拦住开考，学员会被引导去
   做一场冻结了无关题目的空卷；题库练习（BANK_PRACTICE）在考试工作台没有抽题规则入口，必然把
   整个题库全量冻结。
3. **课程测验交卷后，课程侧没有「交卷」这件事**——管理端看不到记录，学员端出现「待学习 + 查看已交卷」
   的自相矛盾。

以上三处均已修复并做完真实环境验证；另有一批功能点补充项（题库编辑、线下成绩录入、抽题规则配置、
名称回显统一、素材上传口径统一、学习侧四项开关）。

---

## 一、缺漏的闭环补充项（承诺过但没闭环，本次补齐）

### 1.1 课程学习进度上报与完成阈值（Change `2026-09-17-close-course-learning-progress-loop`）

| 项 | 说明 |
|---|---|
| **之前的情况** | 管理端内容编排页能配「最低学习秒数 30 / 时长模式 自动 / 要求学习秒数 60」，学员端也在上报学习会话（30 秒心跳），但两边对不上：管理端「报名与学习」详情大量字段是「—」，进度百分比长期不动。 |
| **缺少了什么** | ① 客户端计时结算点缺失：`syncStudyClock()` 只在「切标签页 / 媒体播放暂停 / 切换内容项」被调用，心跳上报前不结算，直接读累计值再清零 → **纯文档类内容项（PDF/图片）只要用户不切标签页，上报的有效秒数恒为 0**（实测某会话持续 8 分 23 秒、有效秒数 0）。<br>② `minimumStudySeconds`（最低学习秒数）是**死字段**：只在 DTO/Entity/快照/VO/DDL 里存在，后端没有任何业务逻辑读它。<br>③ `durationMode`（时长模式）是**死字段**：只在建默认规则时写死 `AUTO`、快照原样透传，自动与手动行为完全一致。<br>④ `requiredSeconds` 是唯一生效的秒数，但缺失或为 null 时回退为 **0**，判定 `studySeconds >= 0` 恒成立 → **首次上报即判为已完成**。<br>⑤ `openspec/specs/` 下没有覆盖「学习会话上报 / 完成判定」的能力规格，规则只散落在已归档 change 的 design 里，这是字段长期失效没被发现的直接原因。 |
| **阻塞了什么** | 学习记录、学习进度、学时三条口径同时失真：进度不可信 → 课程完成规则（`ALL_REQUIRED_ITEMS`）与培训学时投影都建立在错误数据上 → 「课程学习」这条主线无法验收。 |
| **做了什么** | ① 两端（PC + 移动端）在 `reportCurrentSession()` 取值前先 `syncStudyClock(now)`，覆盖心跳、切内容项、页面卸载、媒体自然结束四条路径。<br>② 阈值改为**三级解析**：自定义材料规则「要求学习秒数」(`source=CUSTOM`) → 内容项「最低学习秒数」→ 材料规则默认值 → 0；**任一档 > 0 即停止回退；全为 0 时不得判定完成**。第三档必须保留，否则未配置阈值的内容项又回到「首次上报即完成」。<br>③ 「时长模式」按功能项口径**退役**：功能项 67 只要求「设置学习时长」，未要求模式选择。管理端移除该单选，接口不再必填、留空按 `AUTO` 落库（DB 列保留，不加 migration）。<br>④ 契约修正：`attemptCount` 描述改「学习会话次数」（实现本就是会话次数，不是作答次数）、`durationMode` 标废弃并移出 required、`minimumStudySeconds` 补齐与材料规则的覆盖关系，并校正 example 中不在 enum 内的取值。<br>⑤ **新建能力规格** `course-learning-progress`，把上报、结算、阈值、进度重算固化为可追溯的 MUST 约束，避免再次「配了就以为生效」。 |
| **闭环到哪一步** | 学员端有效时长真实产生且不可见区间不计入；阈值三级解析真实参与判定；管理端不再暴露未实现的设置。 |
| **验证证据** | 后端 Red→Green：改前 `CourseLearningServiceImplTest` 21 用例 2 失败（期望 `LEARNING` 实得 `COMPLETED`）→ 改后 32/32 全绿（含 `CourseItemServiceImplTest`）。<br>真实端到端（真实 Chrome + 后端 20080 + 团队测试库）：文档类内容项静置 100 秒捕获 3 次心跳 `validStudySeconds = 30/30/29`（修复前同一账号同一内容项历史会话全为 0）；切走标签页 60 秒的隐藏窗口跨两次心跳，有效秒数分别为 30/10/**0**/19，内容项累计 59 = 四次之和（同时证明不重复计数）；移动端 uni H5 走真实 UI 登录，心跳 30/29 且 `device_type=H5`；阈值判定矩阵 A(CUSTOM/60 → 30 秒不完成) / B(DEFAULT/60 → 回退内容项 30 秒完成) / C(全 0 → 不完成) / D(要求秒数 0 视为未配置) 全中；`PUT material-rule` 请求体不含 `durationMode` → 200 且落 `AUTO`（旧实例上同一请求返回 400）。 |
| **遗留** | 「可计时媒体暂停期间不计入」只有代码层守卫，没有端到端证据——测试库里只有 1 条 DOCUMENT 素材，没有视频与 HLS 派生产物，构造真实 HLS 播放需要「上传视频 → 转码 → 写 `sts_asset_derivative` → 发布新课程版本」，属需人工确认的数据构造，未擅自执行。<br>两端口径不一致：PC 用 `format === 'hls'`、移动端用 `assetType ∈ {VIDEO, AUDIO}`，建议后续统一。<br>心跳 `endReason` 恒为 `USER_EXIT`，周期检查点与用户退出无法区分，做时长对账时容易误读。 |

### 1.2 课程测验交卷事实可见性（Change `2026-09-17-surface-course-quiz-submitted-facts`）

| 项 | 说明 |
|---|---|
| **之前的情况** | 含主观题的课程测验交卷后作答进入 `PENDING_REVIEW`，管理端「报名与学习 → 分块进度」只显示 2 项（课程学习、题库练习），已交卷的「课程测验」缺失；合计进度 66.67% = 2/3，说明内容项在快照里、只是没有进度记录。学员端课程目录则出现「状态=待学习 / 按钮=查看已交卷」的自相矛盾；移动端结果页待批阅时只有一张等待卡片，学员看不到自己交上去的答案（PC 端同一接口是能看的）。 |
| **缺少了什么** | 内容项进度**只在成绩生效时**由 `applyEffectiveAssessmentResult` 写入。含主观题的测验交卷后停在待批阅，课程侧就完全没有交卷事实。题库练习因为提交即自动生效，所以一直有记录——这解释了「为什么练习有、测验没有」。`openspec/specs/course-assessment-source-separation` 只约束「最终成绩生效后完成」，没规定进度记录何时产生，所以严格说不是规格违规，但管理端承诺了「作答轨迹」，判为缺陷。 |
| **阻塞了什么** | 管理端无法看到报名学员的测验参与情况；学员端状态文案与按钮互相矛盾；移动端学员在待批阅期间无法回看自己提交的答案，容易产生「我到底交上去了没有」的疑问。 |
| **做了什么** | ① 交卷即按 `assessmentExecutionId + courseEnrollmentId + courseItemId` **幂等投影**内容项进度：转 `LEARNING`、作答次数记 1、首次开始时间与最近学习时间取交卷时间；新增 `CourseAssessmentSubmitted` 只带参与事实。<br>② **两条铁律**：交卷投影 MUST NOT 写 `course_assessment_result`（该表唯一键承担生效回调的幂等，提前插入会让生效回调被判重复而 return，内容项**永远无法转为已完成**）；MUST NOT 记分、MUST NOT 标记完成。<br>③ 生效回调的作答次数由 `attemptCount + 1` 改为 `Math.max(attemptCount, 1)`，避免同一份答卷被交卷与生效**重复计数**（用保留 `+1` 的对照测试跑出 `attemptCount=2` 作为证据）。<br>④ 移动端课程目录（详情页 / 学习页）状态文案按考核状态统一收敛：`QUIZ` 看 `scorePublishStatus` / `examTaskStatus`，题库练习不计分仍看 `progressStatus`。<br>⑤ 移动端结果页待批阅分支改为「等待成绩发布 hero + 答题回看列表」，答案展示复用 PC 同口径的 `getLearnerAnswerLines`；顺带修掉生效分支原来只渲染 `answerText` 导致选择题显示为空的问题。 |
| **闭环到哪一步** | 交卷事实在课程侧可见（管理端有作答次数与时间轨迹）；学员端文案自洽；待批阅期间可回看本人答案，生效后切换为成绩 + 标准答案 + 解析。 |
| **验证证据** | `mvn -pl sts-service-admin -am test -Dtest=CourseLearningServiceImplTest,ExamAttemptServiceImplTest` → `Tests run: 74, Failures: 0, Errors: 0`；移动端三页 lint / `vue-tsc` 通过、`uni build -p h5` Build complete。<br>**注意：这一条只有单测 + 静态验证，没有做真实端到端点击。** |
| **遗留** | 该内容项的**考核资源引用锁定点提前**：`assertAssessmentReferenceMutable` 按「该内容项是否已有进度记录」判定能否改考核资源，所以测验内容项的试卷版本锁定点从「首份成绩生效」提前到「首份交卷」。这是已确认的语义变更，与「已有参与事实不可改」一致。<br>管理端「课程测验结果」面板语义未变（仍只读 `course_assessment_result`，只在生效后有行）。 |

### 1.3 四类考试类型闭环（Change `2026-09-17-close-exam-type-loops`）

背景：`examType` 四类在前端只有三行差异（是否必绑已发布试卷版本、是否必绑题库），此后全部收敛到同一条作答链。
真实环境实测结果是 **ONLINE 与 SPECIAL_PRACTICE 已闭环，其余两类有问题**：

| 类型 | 修复前实测 | 问题性质 |
|---|---|---|
| ONLINE | 全流程 70 分及格 | 闭环 |
| SPECIAL_PRACTICE | 全流程 70 分及格 | 闭环 |
| BANK_PRACTICE | 冻结题库**全部已发布题**、每题 1 分 | 缺抽题规则入口 |
| OFFLINE | 冻结 **0** 题、最终 **0 分不及格** | **未闭环** |

#### (1) 线下考试（OFFLINE）未闭环

| 项 | 说明 |
|---|---|
| **之前的情况** | `resource_type=OFFLINE_RESULT` 全仓**只有 1 处写入、没有任何读取分支**：既没有线下成绩录入接口，也没有拦住开考。 |
| **缺少了什么** | ① 学员端 `pageMyTasks` 不按 `examType` 过滤，线下考试照样出现在「我的考试」并给出「开始考试」。<br>② 开考时走 `freezeQuestions(attempt, execution.getResourceId())`，而 OFFLINE 的 `resourceId = examId`——**把 examId 当成 paperVersionId 去查题**，两个自增 ID 空间重叠，可能冻结到无关试卷的题目，或 0 题。<br>③ 随后自动判分空转、重算得 0 分、交卷进 `PENDING_PUBLISH`，发布后学员看到「0 分不及格」。<br>④ 成绩本该由教师线下录入，系统里却没有入口。 |
| **阻塞了什么** | 线下考试在系统里表现为「必须在线做、做了必挂」，与「线下组织、线下判卷、线上录分」的业务预期完全相反；同时那条 ID 错位路径会污染无关试卷的题目快照。 |
| **做了什么** | ① 开考硬拒（错误码 **7072**「线下考试不支持在线作答」），关掉 ID 错位路径。<br>② 新增 `POST /api/exam/exams/{examId}/offline-scores`（权限 `exam:score:publish`）录入成绩：upsert `ExamAttempt`（`submitReason=OFFLINE_IMPORT`、`attemptStatus=PUBLISHED`）并同步 `ExamCandidate`，**重复录入为覆盖，不新增 attempt 行**。<br>③ 未录分时 `taskStatus=WAITING_RESULT` 且 `availableAction=null`（前端显示「待出成绩」），录分后转 `RESULT_PUBLISHED` + `VIEW_RESULT`。<br>④ 两端任务卡按 `availableAction` 渲染，不再对线下考试给「开始考试」。<br>⑤ 错误码校准：越界 **7068**、跨考生 **7065**、非线下考试录分 **7069**。 |
| **闭环到哪一步** | 线下考试「安排 → 线下执行 → 管理端录分 → 学员查分与合格状态」闭环；不再产生虚假的 0 分答卷。 |
| **验证证据** | e2e 46/46：未录分 `WAITING_RESULT` 且 `availableAction=null`、开考 7072、越界 7068、跨考生 7065、非线下 7069、录入成功、重复录入覆盖、录分后 `RESULT_PUBLISHED + VIEW_RESULT` 且学员可见分数与合格状态。 |

#### (2) 题库练习（BANK_PRACTICE）缺抽题规则

| 项 | 说明 |
|---|---|
| **之前的情况** | 考试工作台**没有抽题规则配置入口**（`drawRules` 只在培训模板/计划内容项里能配）。 |
| **缺少了什么** | ① 必然走 `freezeQuestionBank` 的无规则分支，把题库里**全部已发布题**按每题 1 分全量冻结——题库大就变几百题。<br>② 无规则分支**不校验「及格分 > 抽题总分」**（只有有规则分支才有）。<br>③ 考试定义只落库一份「绑哪个题库」，没有「怎么抽、抽多少、每题几分」。 |
| **阻塞了什么** | 题库练习无法按「按题型/难度/知识点抽 N 题、每题 X 分」配置，及格线失去意义；题目分值口径（1 分/题）与业务预期不符。 |
| **做了什么** | ① `sts_exam_exam` 新增 `draw_rule_json`（裸数组、可空）+ migration + init 快照与 schema-version。<br>② 管理端考试定义表单新增规则编辑区（题型/难度/知识点/抽题数/每题分值），实时显示「合计抽取 N 题 / M 分」；只读详情与发布核查回显同口径中文描述。<br>③ 发布时把规则冻进执行快照；开考按规则抽题，每题带 `drawRuleIndex`。<br>④ 总分 = Σ(`drawCount` × `scorePerQuestion`)；及格分超过抽题总分、或未配置规则发布/创建时报 **7063**。 |
| **闭环到哪一步** | 题库练习可按规则抽题，冻结题数与分值可预期、可核对。 |
| **验证证据** | e2e：规则落库回读、及格分越界 7063、未配置规则发布 7063、开考后冻结题数 = ΣdrawCount = 5、题目分值合计 = 20、题型与规则一致。 |

**⚠️ 本次实测暴露并修掉的真实缺陷（重要）**：`draw_rule_json` 落库存的是**裸数组** `[...]`，而
`QuestionBankDrawEngine.parseRules` 只认策略快照的 `{"drawRules":[...]}` 包裹结构 → **总分折算恒为 0、
及格分越界校验形同虚设**。已在引擎里兼容「裸数组 / 包裹」双形态解析并补 2 例单测锁定。
后续改动该引擎时**不要再收紧**（两个调用点分别传 `draw_rule_json` 与 `policySnapshotJson`）。

#### (3) 答案详情可见性 `answerDetailMode = PUBLISHED` 无实现

| 项 | 说明 |
|---|---|
| **之前的情况** | 前端「成绩发布后可见」这个选项在后端**没有对应实现分支**。 |
| **缺少了什么** | `ExamLearnerServiceImpl.answerDetailsVisible` 只处理了部分取值，选 `PUBLISHED` 等于学员永远看不到标准答案与解析。 |
| **阻塞了什么** | 管理端选了「成绩发布后可见」的考试，学员拿不到答案与解析（这是「考卷详情」验收里明确要的能力）。 |
| **做了什么** | 补 `PUBLISHED` 分支，列注释对齐契约；`SUBMITTED / PUBLISHED / NEVER` 三种取值的可见性由单测锁定。 |
| **闭环到哪一步** | 与线上考试/专项练习的答案可见性策略一致。 |
| **验证证据** | e2e C 组：ONLINE / SPECIAL_PRACTICE 全流程 70 分及格的答案与解析在 `PUBLISHED` 模式下可见。 |

#### (4) 跨端考试类型文案

移动端 `pages/exam/notice.vue` 的类型映射只识别 `ONLINE`，其余类型**把裸枚举码渲染给学员**。
已把 `examType` 中文映射收敛到 `@shared/utils/exam`（未知值降级为「考试」，绝不暴露内部码）。

#### 考试闭环整体验证

- 后端定向单测 7 个类：`Tests run: 144, Failures: 0, Errors: 0` / BUILD SUCCESS。
- 真实端到端（真实后端 20080 + 团队测试库 + 真实鉴权）`verify-gaps.mjs`：**46/46 通过**。

---

## 二、功能点补充项（此前未实现，本次新增）

| # | 功能点 | 之前 | 本次做了 | 验证 |
|---|---|---|---|---|
| 1 | **题库中心编辑题库** | 题库只能新建，不能改名称/分类/知识点 | 新增编辑入口，复用 `PUT /api/question/banks/{id}`；同时重做题目详情与新建页界面（分区卡片、选项编辑、题型与判分联动） | `vue-tsc` / `eslint` |
| 2 | **题目详情「题干素材」** | 只能从素材库下拉选一个 | 改为「本地上传 / 从素材库选择 + 已关联素材清单」（名称、类型、编码、移除） | `vue-tsc` / `eslint` |
| 3 | **线下成绩录入入口** | 无 | 考试成绩页新增「录入线下成绩」（仅 OFFLINE 可见）；见 1.3 | e2e |
| 4 | **抽题规则配置与回显** | 考试工作台无该入口 | 见 1.3 | e2e |
| 5 | **学习侧四项课程开关（学员端）** | `isSpeedPlayAllowed` / `isStudyAnticheatEnabled` / `isStudyDescriptionEnabled` / `isWatermarkEnabled` 四个字段管理端能存，学员侧**完全没有实现**；`CourseLearningCourseVO` 也未投影，纯前端拿不到值 | 后端补 4 个只读字段投影（快照早已冻结，无需 migration）；行为全在前端：**倍速三态**（true 放行 / false 不显控件并回置速率 / 不传不干预，仅对 HLS 生效）、**防作弊**复用既有 `POST /api/exam/attempts/{id}/anticheat-events`（学员可用、不限考试类型，切屏记 `SWITCH_AWAY`、复制粘贴记 `COPY`，交卷后立即停采并解绑避免跳转结果页被记成切屏）、**学时说明**、**学习水印**（平铺当前用户名，`pointer-events:none`） | 后端 6 + 36 用例全绿；两端 `vue-tsc`；H5 build |
| 6 | **人员主键 → 姓名统一回显** | 「只有主键、需要显示姓名」的场景各页面各写一份实现，口径互不相同，其中一处因没有 try/catch，`person-picker` 403 会让整个页面加载失败 | 新增 `composables/use-user-name.ts` 统一口径：只走 `/api/common/person-picker`（`/api/system/users` 需 `sys:user:list`，业务角色 403）、不传 `query.status`（历史版本可能由已停用账号发布）、失败降级「用户 #id」、分页自增并累积；替换 5 处重复实现（顺带修掉成绩处置页 403 整页失败） | `vue-tsc` / `eslint` |
| 7 | **素材上传口径统一 + 选择器迁公共目录** | 体积上限在页面内各自硬编码，与契约和后端校验不一致 | `constants/asset.ts` 的 `ASSET_UPLOAD_TYPE_CONFIG` 成为**唯一口径**；素材选择器三件套由 `course/components` 迁至 `src/components/asset-picker` | `vue-tsc` / `eslint` / 全仓无旧路径残留 |
| 8 | **跨端附件与考试类型展示工具** | 同一组格式化函数每端各写一份，移动端课程详情页漏了两处，显示裸字节 `1198217` 与裸契约码 `SOURCE_DOWNLOAD_DISABLED` | 抽出 `shared/utils/attachment.ts`、`shared/utils/exam.ts`、`shared/utils/study.ts`，三处统一引用，未知码降级为可读文案、不再把内部码泄漏到学员界面 | shared `tsc` + 两端 `vue-tsc` + H5 build + 运行时取值核对 |
| 9 | **菜单权限模型文档** | 权限链路只散在代码里，学员端报「无权访问该报名学习数据」需要重新翻一遍 | 产出 `docs/architecture/menu-permission-configuration-guide.md`（权限解析口径、403 对照表、211 节点菜单树、分角色勾选清单、菜单缺口清单）与 `menu-role-matrix.csv` | — |
| 10 | **管理端界面重做** | 课程测验批阅是 50% 抽屉里的 4 列表格（长文本折叠、评分要三步）、报名学习详情无分区感、试卷工作台组卷配置是长条 | 统一到既有只读页结构（hero 概览卡 + 指标卡 + `.snapshot-section` 白卡包表格）；批阅改单题卡片 + 双栏答案对照 + 快捷给分 + 待批阅/已批阅筛选 | `vue-tsc` / `eslint`（**未做真实浏览器点击验证**） |

---

## 三、对同事有影响的口径变化（务必知悉）

1. **素材上传体积上限变了**：资源库页面此前本地硬编码，图片 20MB、音频 500MB；
   现统一到 `constants/asset.ts`（对齐 `asset` 契约与后端 6025 校验）——
   **图片 16MB、音频/视频 2GB、文档 512MB**。页面不得再自定义限制。
2. **`durationMode`（时长模式）已废弃**：管理端不再提供该设置，接口不再要求必填，留空按 `AUTO` 落库。
   DB 列保留、未加 migration；**后端不得再读它做任何判定**。
3. **`attemptCount` 语义 = 学习会话次数**（每次有效上报累加），不是作答次数，契约描述已改。
4. **课程测验内容项的考核资源锁定点提前**：由「首份成绩生效」提前到「首份交卷」。
5. **线下考试不再有「开始考试」入口**：学员端 `availableAction` 现在可能为 `null`（未录分时），
   前端渲染要按 `null` 处理（共享类型 `LearnerExamTask.availableAction` 已放宽为 `ExamAction | null`）。
6. **新增 DB 列需手工执行 migration**：`sts_exam_exam.draw_rule_json`，脚本
   `V20260917170000__exam_draw_rule_and_answer_detail_comment.sql`。因为 `application-vpn.yml` 里
   `main.flyway.enabled=false`，**migration 不会自动执行**，共享测试库已由人工执行过，正式环境按既有流程走。
7. **共享测试库被重置后要重跑角色初始化**：`stu_e2e_01` 的学员角色绑定会丢失，表现为
   `exam:attempt:start` 等接口 403，重跑 `.workbuddy/tmp/e2e/setup-student-role.mjs` 恢复。
8. **`reviewComment` 在学员端恒为 `null` 是设计，不是 bug**：只有管理端具备 `exam:attempt:list` 时才返回。
   批阅意见是内部留痕 + 教师回显，**不会展示给学员**，写文案时不要写「将随成绩反馈给学员」。

---

## 四、本日提交清单（`main`，自 `c41a029` 起）

| # | Commit | 类型 | 内容 |
|---|---|---|---|
| 1 | `38d28da` | chore | 忽略本地 `.workbuddy` 工作目录 |
| 2 | `79ecf75` | docs | 菜单权限模型与角色勾选矩阵 |
| 3 | `1f4e2d1` | refactor | 素材上传口径统一 + 选择器迁入公共目录 |
| 4 | `92fed17` | fix | 开课发布写回课程版本，修复阅卷人校验 5068 |
| 5 | `41d8468` | feat | **课程学习与测验闭环**（进度上报 / 交卷事实 / 学习开关） |
| 6 | `d2e716b` | refactor | 人员名称回显统一为 `useUserNameResolver` |
| 7 | `a97206c` | feat | **四类考试类型闭环**（抽题规则 / 线下成绩 / 答案可见性 / 类型文案） |
| 8 | `e62cc59` | feat | 题库中心编辑题库 + 题目详情/新建界面 |
| 9 | `6b0da0d` | fix | 题目版本回显补齐填空答案与判分关键词 |
| 10 | `814d146` | feat | 管理端界面重做（批阅 / 报名学习详情 / 试卷工作台） |

**关于拆分粒度**：课程侧三个能力（进度上报闭环、交卷事实、四项学习开关）的改动在两端 `learning.vue`
与 `course.openapi.json` 上重叠，按 hunk 拆会让提交之间互相引用尚未存在的符号，故合并为第 5 个提交，
正文按能力分节说明。同理，课程测验的交卷接线落在 `ExamAttemptServiceImpl`，随第 7 个提交进入
（第 7 个提交正文已注明）。

**说明**：本次未改动 `backend/sts-service-admin/src/main/resources/application.yml`。
本地联调期间临时切换过 `spring.profiles.active`（`test` → `vpn` 指向远程库），该改动**没有纳入提交**，
工作区已与主线一致。需要连远程库联调的同事请自行临时切换，勿提交。

---

## 五、验证覆盖程度（诚实说明）

**有真实端到端证据的：**

- 课程学习进度闭环：真实 Chrome + 后端 20080 + 团队测试库，心跳有效秒数、隐藏区间不计入、
  两端一致、阈值判定矩阵、不传 `durationMode` 保存，全部实测。
- 四类考试类型：真实后端 + 真实鉴权，`verify-gaps.mjs` 46/46。

**只有单测 / 静态验证的（未做真实点击）：**

- 课程测验交卷事实：后端 74 用例全绿 + 移动端 lint/typecheck/build，**未起真实后端点一遍**。
- 题库编辑、题目详情题干素材：`vue-tsc` + `eslint`。
- 管理端界面重做（批阅卡、报名学习详情、试卷工作台）：`vue-tsc` + `eslint`，**未经真实浏览器验证**，
  `ElProgress` 自定义 slot 排版与 `.el-table` 圆角裁切需在真实页面复核。
- 人员名称回显：未确认 `person-picker` 对业务中心管理员账号是否放行、不传 `query.status` 的实际返回。

**验证期间在团队测试库写入过数据**：`enrollment 1` 的学习会话与进度已被
`.workbuddy/e2e-out/threshold-restore.sql` 还原（快照回 `CUSTOM/60/30`，内容项与报名回 `NOT_STARTED/0`），
会话行 205～210 保留。

---

## 六、遗留与需人工决策

**需人工决策：**

1. `application.yml` 默认 profile 是否改为 `vpn`（见第四节）。
2. 「可计时媒体暂停不计入」是否要投入构造 HLS 素材（上传视频 → 转码 → 写 `sts_asset_derivative` →
   发布新课程版本）做端到端验证。
3. 独立 BANK_PRACTICE 交卷后仍为 `PENDING_PUBLISH`（客观分暂为 null），属既有「独立练习需管理端
   发布成绩」语义；**如需全客观题自动出分，需另立 Change**。
4. 菜单缺口：`门户→培训中心`(#9527) 无子节点、无 `perms`，`training:learner:task` 只在应用中心下的
   #7518 存在 ⇒ **学员端培训中心链路必然 403**；另有 43 个后端 `@SaCheckPermission` 权限码在
   `menus.json` 中无节点，无法通过角色界面授权。

**已知未做（范围外）：**

- 防快进、倍速限制、离页暂停计时、水印的「管理端材料规则字段」仍未完整消费（功能项 75，延期项）。
- LIVE 内容项完成路径未定义（直播参与事实缺失），本期维持不自动完成。
- `creditAwarded` 仍恒为 0，学分发放链路未实现。
- 学习进度表对文档类内容项仍渲染「最近位置(秒) / 结束位置(秒)」为「—」，列口径未调整。
- 仿真软件/教学/练习/考试、防作弊与人脸识别本期延期。
