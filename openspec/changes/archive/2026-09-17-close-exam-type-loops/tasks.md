# Tasks：四类考试类型闭环缺口补齐

> 每个 Task 为一个可独立验收的业务能力切片。Owner 为实施角色（AI 实施，人工确认范围与合并）。

## T1 线下考试成绩录入（管理端 → 学员可查）

- **Owner**：后端
- **输入**：已发布的 OFFLINE 考试 + 已绑定考生
- **输出**：`POST /api/exam/exams/{examId}/offline-scores` 可用；录分后学员端可见成绩与合格状态
- **后端影响**：新增 `OfflineScoreService`（复用 `ExamCandidateMapper`/`ExamAttemptMapper`/`ExamPolicyMapper`），
  新增 `ExamController` 端点，新增错误码 7006/7065/7068/7069
- **前端影响**：PC 考试成绩页新增「录入线下成绩」入口（仅 OFFLINE 考试可见）
- **API 依赖**：`GET /api/exam/exams/{examId}/candidates`（取考生与当前分数）
- **数据影响**：写 `sts_exam_attempt`（upsert）+ `sts_exam_candidate`
- **验收**：录分后 `GET /api/exam/learner/attempts/{id}` 返回 `finalScore`、`isPassed`、`scoreVisible=true`；
  重复录入覆盖不新增 attempt 行；越界分数整批拒绝
- **测试**：`OfflineScoreServiceTest` 单测 + e2e 脚本回归

## T2 线下考试学员端行为与开考硬拒

- **Owner**：后端 + 前端
- **输入**：OFFLINE 考试（未录分 / 已录分两种状态）
- **输出**：`availableAction` 分别为 `null` / `VIEW_RESULT`；`POST /api/exam/attempts/start` 对 OFFLINE 返回 7072
- **后端影响**：`ExamLearnerServiceImpl.toTaskVO` 增加 OFFLINE 分支；`ExamAttemptServiceImpl.start` 前置拦截
- **前端影响**：PC `views/portal/exams/` 与 mobile `pages/exam/` 任务卡片按 `availableAction` 渲染
  「待出成绩」/「查看成绩」而非「开始考试」
- **数据影响**：无
- **验收**：e2e 实测 OFFLINE 不再出现 START；直连开考接口被拒；录分后学员能从列表进入结果页
- **测试**：`ExamLearnerServiceImplTest` + `ExamAttemptServiceImplTest` 定向用例

## T3 题库练习抽题规则（后端）

- **Owner**：后端
- **输入**：BANK_PRACTICE 考试 + `drawRules`（题型/难度/知识点/抽题数/每题分值）
- **输出**：发布时把规则冻进执行快照；开考按规则抽题；总分与及格分按规则折算
- **后端影响**：
  - `sts_exam_exam` 新增 `draw_rule_json`（init 快照 + migration + `sts.init.schema-version`）
  - `Exam` 实体、`ExamCreateRequest`/`ExamUpdateRequest`、`ExamDrawRuleRequest`
  - `ExamServiceImpl.publish` 注入 `drawRules`；`AssessmentExecutionServiceImpl.buildFromExam` 计算总分/及格分
  - `QuestionBankDrawEngine` 复用（不新增引擎）
- **API 依赖**：无新增接口，仅字段扩展
- **数据影响**：新列可空，无需回填
- **验收**：配置 5 题规则后开考，冻结题数 = 5，总分 = Σ(drawCount × scorePerQuestion)；
  未配置规则时发布报 7063；`passScore > 抽题总分` 时创建/更新报 7063
- **测试**：`ExamServiceImplTest`、`AssessmentExecutionServiceImplTest`、`QuestionBankDrawEngineTest`

## T4 题库练习抽题规则（前端）

- **Owner**：前端
- **输入**：考试定义表单选择 `BANK_PRACTICE`
- **输出**：可增删抽题规则行（题型/难度/知识点/抽题数/每题分值），并实时显示「抽题总数 / 总分」
- **前端影响**：`ExamDefinitionForm.vue` 新增规则编辑区；`constants/exam-options.ts` 复用题型/难度枚举；
  `api/exam.ts` 扩展类型
- **验收**：创建 BANK_PRACTICE 考试能保存并在详情/发布检查中回显规则；无规则时发布前给出阻断提示
- **测试**：`vue-tsc --noEmit` + 改动文件 lint

## T5 答案详情可见性修复

- **Owner**：后端
- **输入**：`answerDetailMode = PUBLISHED` 且成绩已发布
- **输出**：学员端返回 `standardAnswer`、`isCorrectSnapshot`、`analysisSnapshot`
- **后端影响**：`ExamLearnerServiceImpl.answerDetailsVisible` 补 `PUBLISHED` 分支；列注释对齐契约
- **数据影响**：仅改列注释
- **验收**：同一 attempt 下 `SUBMITTED` 与 `PUBLISHED` 均可见答案；`NEVER` 不可见
- **测试**：`ExamLearnerServiceImplTest` 三分支用例

## T6 跨端考试类型文案收敛

- **Owner**：前端（shared）
- **输入**：任意 `examType`（含 `null`、未知值）
- **输出**：`@shared/utils/exam` 的 `getExamTypeText`
- **前端影响**：新增 shared 工具；mobile `pages/exam/notice.vue` 等改引用；PC 同语义处一并收敛
- **验收**：四类返回正确中文；未知值降级为「考试」，不出现裸枚举码
- **测试**：shared 单测（可用 `--experimental-strip-types` 直接跑）+ 两端 `vue-tsc --noEmit`

## T7 契约同步与端到端回归

- **Owner**：后端 + 验证
- **输出**：`docs/api/exam.openapi.json` 同步；四类考试 e2e 全绿
- **内容**：新增接口 summary/description/字段中文说明/example/错误码/`x-permission`；
  `ExamCreateRequest`/`ExamUpdateRequest` 增 `drawRules` 说明
- **验收**：e2e 脚本输出 —— ONLINE/SPECIAL_PRACTICE 保持 70 分及格；BANK_PRACTICE 冻结题数 = 规则配置值且
  及格判定可达；OFFLINE 无 START、录分后学员可见成绩
- **测试**：`.workbuddy/tmp/e2e/exam-flow.mjs` 回归 + `mvn -pl sts-service-admin test`（定向）

## 实施顺序

T5（最小）→ T6（独立）→ T1 → T2 → T3 → T4 → T7。
T1/T2 与 T3/T4 可并行；T7 在所有 Task 完成后执行。

## 完成记录（2026-09-17）

七个 Task 全部实施完成，定向单测与真实端到端回归均通过。

### 验证证据

- **后端定向单测**：`mvn -pl sts-service-admin test "-Dtest=OfflineScoreServiceTest,ExamLearnerServiceImplTest,ExamServiceImplTest,AssessmentExecutionServiceImplTest,ExamAttemptServiceImplTest,ExamControllerTest,QuestionBankDrawEngineTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
  → `Tests run: 144, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS。
- **端到端**：`.workbuddy/tmp/e2e/verify-gaps.mjs`（真实后端 20080 + 远程库 + 真实 SaToken 鉴权）
  → **46/46 通过**，覆盖：
  - A 组（BANK_PRACTICE）：规则落库回读、及格分越界 7063、未配置规则发布 7063、开考后冻结题数 = ΣdrawCount = 5、
    题目分值合计 = Σ(drawCount×分值) = 20、题型与规则一致、每题带 `drawRuleIndex`。
  - B 组（OFFLINE）：未录分 `WAITING_RESULT` 且 `availableAction=null`、开考 7072、越界 7068、跨考生 7065、
    非线下 7069、录入成功、重复录入覆盖、录分后 `RESULT_PUBLISHED` + `VIEW_RESULT` + 学员可见分数/合格/开关。
  - C 组（ONLINE / SPECIAL_PRACTICE）：全流程 70 分及格的答案与解析在 `PUBLISHED` 模式下可见。
- **前端**：`pc-web` / `mobile-app` / `shared` 的 `vue-tsc --noEmit` 与改动路径 `eslint --max-warnings=0` 通过。

### 实施中修正的实施偏差

1. **抽题规则落库形态不一致（真实缺陷）**：`sts_exam_exam.draw_rule_json` 存裸数组 `[...]`，而
   `QuestionBankDrawEngine.parseRules` 只认策略快照的 `{"drawRules":[...]}` 包裹结构，导致总分折算恒为 0、
   及格分越界校验形同虚设。已在 `parseRules` 增加「裸数组 / 包裹结构」双形态解析，并补 2 例单测锁定。
2. **错误码语义校准**：原草稿把「及格分超过抽题总分」写成 7064，把「非线下考试」写成 7006。经核对既有实现，
   7064 已用于「题库练习需完成全部题目后提交」，7006 已用于「考试未发布」，因此统一为
   `7063`（及格分越界）/ `7069`（非线下）/ `7068`（分数越界）/ `7065`（跨考生）/ `7072`（线下禁在线作答）。
   spec-delta.md、tasks.md、design.md 一并校准。

### 需要人工确认 / 遗留事项

- **远程库需手工执行 migration**：`application-vpn.yml` 的 `main.flyway.enabled=false`，新增列
  `sts_exam_exam.draw_rule_json` 已由人工在共享测试库执行 `V20260917170000__exam_draw_rule_and_answer_detail_comment.sql`。
  正式环境发布时须按既定 migration 流程执行，勿依赖自动 Flyway。
- **联调环境需重跑角色初始化**：共享测试库被重置后 `stu_e2e_01` 的学员角色绑定会丢失，表现为
  `exam:attempt:start` 等接口 403。重跑 `.workbuddy/tmp/e2e/setup-student-role.mjs` 即可恢复。
- **独立 BANK_PRACTICE 交卷后为 `PENDING_PUBLISH`（客观分暂为 null）**：属既有「独立练习需管理端发布成绩」语义，
  不在本次 Change 范围，未做改动；如需全客观题自动出分，另立 Change。

