# Design：四类考试类型闭环缺口补齐

## 1. 决策总览

| 编号 | 问题 | 决策 | 理由 |
|---|---|---|---|
| ① | 线下考试可被学员空考 | 学员端保留列表但不出「开始考试」；新增管理端录分接口；开考接口硬拒 OFFLINE | 功能项 55 要求「提供进入考试的入口」+ 64 要求展示线下考试结果；直接删掉列表项会让学员看不到自己的线下成绩 |
| ② | 题库练习全量冻结 | 复用培训模块抽题规则能力，考试侧新增配置入口与冻结链路 | 用户明确要求复用既有能力；全量冻结在真实题库（数百题）下不可用 |
| ③ | `PUBLISHED` 无实现 | `switch` 补一个分支 | 前端下拉与契约均为 `SUBMITTED/PUBLISHED/NEVER`，属实现漏分支 |
| ④ | 移动端渲染裸枚举码 | 文案收敛到 `shared/src/utils/exam.ts` | 与 `@shared/utils/attachment` 既有约定一致 |

## 2. 线下考试（OFFLINE）成绩模型

### 2.1 成绩载体复用 `sts_exam_attempt`

学员端「查看成绩」读的是 attempt（`ExamLearnerServiceImpl.getMyAttempt` → `toAttemptVO`），因此线下成绩必须
落一条 attempt 才能被学员看到，而不是只写 candidate。

录分时 upsert（每个 candidate 一条）：

| 字段 | 取值 |
|---|---|
| `attempt_status` | `PUBLISHED` |
| `attempt_type` | `FIRST` |
| `attempt_no` | `1`（已存在则不新增） |
| `total_score_snapshot` | 考试配置的满分（`exam.pass_score` 对应的满分口径，见 2.3） |
| `final_score` | 管理端录入分数 |
| `is_passed` | 由 `final_score >= pass_score_snapshot` 计算 |
| `submitted_time` / `score_published_time` | 录入时间 |
| `used_seconds` | `0` |

`sts_exam_candidate` 同步：`candidate_status` = `PASSED` / `FAILED`，`final_score`、`best_score`、
`final_attempt_id`、`result_published_time`、`result_effective_time`。

**不写** `sts_exam_attempt_question`（线下无题目），因此学员端渲染题目列表时自然为空。

### 2.2 学员端动作

`ExamLearnerServiceImpl.toTaskVO` 的 `availableAction` 对 OFFLINE 分支：

- 未录分（无 attempt 或 `final_score == null`）→ `null`（前端仅展示「待出成绩」）
- 已录分 → `VIEW_RESULT`

`pageMyTasks` 保留 OFFLINE 考试在列表中。

### 2.3 满分与及格口径

线下考试的 execution 已经冻结 `total_score_snapshot=100`（`AssessmentExecutionServiceImpl:103`），
录分接口沿用该快照作为满分口径，`pass_score_snapshot` 用考试配置的 `passScore`。不改动创建时「无试卷必填
时长/及格比例/及格分」的既有校验。

### 2.4 开考硬拒

`ExamAttemptServiceImpl.start` 在冻结题目之前判断：若 `execution.getResourceType()` 为 `OFFLINE_RESULT`，
抛 `BusinessException(400, 7072, "线下考试不支持在线作答")`。这是防御性约束——即使前端漏改或接口被直连，
也不会再走到「把 examId 当 paperVersionId 查题」的错位路径。

### 2.5 管理端录分接口

```
POST /api/exam/exams/{examId}/offline-scores
权限：exam:score:publish
请求：{ "scores": [ { "examCandidateId": 12, "finalScore": 88.5, "remark": "补考通过" } ] }
响应：{ "examId": 3, "recordedCount": 1, "skippedCount": 0 }
错误：7001 考试不存在 / 7006 考试未发布 / 7069 非线下考试 / 7068 分数超出满分 / 7065 考生不属于该考试
```

- 逐条校验分数落在 `[0, totalScoreSnapshot]`，越界整批拒绝（不部分写入）。
- 幂等：同一 candidate 重复录入即覆盖（记 `update_by`/`update_time`），不新增 attempt 行。
- 考试 `publish_status` 必须为 `PUBLISHED/RUNNING/ENDED`，草稿考试返回 7006。

### 2.6 错误码归属（实施期修正）

原草稿曾把 `7064`（分数越界）与 `7063`（未配置抽题规则）写错含义，实施时按既有实现校准：

| 错误码 | 归属 |
|---|---|
| 7006 | 考试未发布（复用既有含义） |
| 7063 | 题库/题池类不可用：未绑定题库、题库不存在、无可用题目、**未配置抽题规则、抽题规则无法解析、及格分超过抽题总分** |
| 7065 | 录入线下成绩时考生不属于本场考试（新增） |
| 7068 | 线下成绩超出满分区间（新增） |
| 7069 | 仅线下考试支持录入成绩（新增） |
| 7072 | 线下考试不支持在线作答（新增） |

7063 采用「扩展既有码语义」而非新造并行码，与 `AssessmentExecutionServiceImpl`、`ExamAttemptServiceImpl` 的既有用法保持一致。

## 3. 题库练习（BANK_PRACTICE）抽题规则

### 3.1 数据模型

`sts_exam_exam` 新增列：

```sql
`draw_rule_json` json NULL COMMENT '题库练习抽题规则（BANK_PRACTICE 专用，结构同培训侧 drawRules）'
```

- init 快照（`schema-exam.sql`）与增量 migration 同步，并提升 `sts.init.schema-version`。
- 历史数据该列为 `NULL`。

### 3.2 DTO 与复用边界

考试域新增 `com.sts.exam.dto.ExamDrawRuleRequest`（字段与 `TrainingQuestionBankDrawRuleRequest` 一致：
`questionType`/`difficulty`/`knowledgePointId`/`drawCount`/`sortOrder`）。

不直接复用培训域 DTO：培训侧规则语义绑定「培训内容执行配置」，考试侧需要独立的契约与校验入口；
但**抽题算法完全复用** `QuestionBankDrawEngine`，不新建引擎。

### 3.3 发布链路

`ExamServiceImpl.publish(examId, extraPolicy)`：

1. 若传入的 `extraPolicy` 已含 `drawRules`（培训来源）→ 原样使用。
2. 否则若 `exam.drawRuleJson` 非空 → 解析为 `drawRules` 注入 `extraPolicy`。
3. `BANK_PRACTICE` 且最终无规则 → 抛 `7063`「题库练习未配置抽题规则」。
4. 其余类型不受影响。

`AssessmentExecutionServiceImpl.buildFromExam` 的 BANK_PRACTICE 分支改为：

- 有规则：`totalScoreSnapshot = Σ(drawCount × scorePerQuestion)`（复用 `QuestionBankDrawEngine.totalScore`），
  `passScoreSnapshot = min(exam.passScore, totalScoreSnapshot)`。
- 无规则：保持 `totalScoreSnapshot = 0`（历史快照兼容，不再由管理端发布路径产生）。

创建/编辑考试时校验：BANK_PRACTICE 且配置了规则时，若 `passScore > 抽题总分` → 400 `7063`「及格分超过抽题总分」。

规则回读：`ExamVO.drawRules`（`List<ExamDrawRuleVO>`）仅在 BANK_PRACTICE 且 JSON 可解析时返回；
JSON 损坏时降级为空并记 warn 日志——发布路径仍会抛 7063，不会把损坏规则放行。

### 3.4 开考冻结

`ExamAttemptServiceImpl` 的 `freezeQuestionBank` 无规则分支在新流程下不可达（发布已拦截），保留作为
历史快照的兼容路径，不删除。

## 4. 答案详情可见性

`ExamLearnerServiceImpl:366` 的 `answerDetailsVisible` switch 增加：

```java
case "PUBLISHED" -> published;
```

`published` 已由 `isPublished(attempt)` 求得，无需新增状态。

`PASSED`/`EXAM_ENDED`/`RETAKE_ENDED` 三个分支保留（DB 列注释与历史数据可能使用），但**不写入契约**
（契约维持与前端一致的 `SUBMITTED/PUBLISHED/NEVER`）。同时把 `sts_exam_policy.answer_detail_mode` 的列注释
与契约对齐，避免再次误导。

## 5. 跨端文案

新增 `shared/src/utils/exam.ts`：

```ts
export function getExamTypeText(examType: string | null | undefined): string
```

映射：`ONLINE`→线上考试、`SPECIAL_PRACTICE`→专项练习、`BANK_PRACTICE`→题库练习、`OFFLINE`→线下考试；
未知值降级为「考试」（绝不回显裸枚举码）。

`mobile-app/src/pages/exam/notice.vue` 改为引用该函数；PC 侧同语义处一并收敛。

## 6. 数据与权限影响

- 数据库：`sts_exam_exam` 加 1 列（json，可空）+ 列注释修正；**不需要数据回填**。
- 权限：录分接口复用既有 `exam:score:publish`，不新增权限点。
- 契约：`exam.openapi.json` 新增 1 个接口、4 个错误码（7065/7068/7069/7072）并扩展 7063 语义、
  `ExamCreateRequest`/`ExamUpdateRequest` 新增 `drawRules` 字段。

## 7. 测试策略

- 单测：`ExamLearnerServiceImplTest`（OFFLINE 动作分支 + `PUBLISHED` 可见性三分支）、
  `ExamServiceImplTest`（发布注入 drawRules / 无规则报错）、`AssessmentExecutionServiceImplTest`
  （抽题总分与及格分折算）、`ExamAttemptServiceImplTest`（OFFLINE 开考拒绝）、
  `OfflineScoreServiceTest`（越界、非线下、跨考生拒绝、幂等覆盖）。
- 跨端：`shared` 文案函数单测。
- 端到端：复用 `.workbuddy/tmp/e2e/` 脚本回归四类考试闭环，重点确认 OFFLINE 已无 START、
  BANK_PRACTICE 冻结题数等于抽题规则配置值。
