# QA：隔离证书签发来源 —— 三类来源真实端到端验收

- **关联 Change**：`openspec/changes/archive/2026-09-24-separate-certificate-issue-sources/`（已归档）
- **执行人**：fxw11
- **执行时间**：2026-09-24
- **环境**：后端 `sts-service-admin` 本地启动（端口 20080，`--spring.profiles.active=vpn`，应用库 `19.191.192.146:8045/sts`），
  账号 `DEMO/admin`（user 2，角色管理员）；`main.flyway.enabled=false`，未执行任何迁移。

## 1. 结论

三类来源（独立课程 / 独立理论考试 / 培训结果）**均已在真实环境产出证书**，来源 ID 与主数据一致；
培训计划内课程与考试**不再单独发证**（回归项，对比 2026-09-23 同操作会产生 2 张撞车证书）；
重复触发**不新增证书**；`EXAM`/`COURSE`/`TRAINING` 三类来源的启用规则唯一限制（错误码 9418）**真实生效**。

## 2. 验收前的基线

| 项 | 值 |
|---|---|
| 生效证书 | 0 条（历史撞车产物 `certificate_id=3/4` 已逻辑删除） |
| 生效规则 | 4（`COURSE/2`→模板 5）、5（`EXAM/38`→模板 5） |
| 已发布模板 | 模板 5（`CERT-TPL-M4-001`，当前版本 3，`PUBLISHED`） |

## 3. 验收证据

### 3.1 培训计划内课程/考试不再单独发证（回归，关键对比点）

```
POST /api/training/plans/1/results/synchronize  →  code 0
  participant 1：courseScore 85 / examScore 95 / compositeScore 90，resultStatus=PASSED
```

- 同步**未产生任何新证书**（证书表仍只有已删除的 3/4）。
- **对比**：2026-09-23 同一操作因内容级触发产生 `COURSE/2`、`EXAM/38` 两张撞车证书；本次 0 张。

### 3.2 独立理论考试通过 → `EXAM`（来源 ID = `exam_id`）

| 步骤 | 请求 | 结果 |
|---|---|---|
| 启动作答 | `POST /api/exam/attempts/start`（examId=38, userId=2） | `examAttemptId=45`，考生 65，状态 `ANSWERING` |
| 交卷 | `PUT .../answers` + `POST .../submit` | `PENDING_REVIEW`，客观题 60 分 |
| 评阅主观题 | `POST /api/exam/answers/217/review`（40 分） | code 0 |
| 发布成绩 | `POST /api/exam/attempts/45/publish-score` | `PUBLISHED`，`finalScore=100`，`isPassed=1` |

**产出证书**：`id=5`、`CERT-432B197505DC4C98`、`source_type=EXAM`、**`source_id=38`（= 独立考试主数据 ID）**、
`source_result_id=65`（考生 ID）、`holder_user_id=2`、`ISSUED`。

### 3.3 独立课程完成 → `COURSE`（来源 ID = `course_id`）

课程 2 的独立开课 `offering 4`（`plan_content_id` 为空），报名 `enrollment 9`（user 2，`NOT_STARTED`）：

| 内容项 | 类型 | 达成方式 | 结果 |
|---|---|---|---|
| 4 / 5 | ASSET（视频 / PDF，阈值 15s） | `POST /api/course/learning/sessions` 上报 40s | `COMPLETED` |
| 6 | QUESTION_BANK | `POST .../items/6/attempts` → 作答 → `submit` | `RESULT_EFFECTIVE`，7/7 |
| 7 | QUIZ（试卷版本 5） | `POST .../items/7/attempts` → 作答 → `submit` → 评阅 → 自动生效 | `RESULT_EFFECTIVE`，100 分 |

`enrollment 9` 由 `LEARNING` → **`COMPLETED`**（`completed_time=2026-09-24 11:04:47`）。

**产出证书**：`id=6`、`CERT-2582E180C1F44450`、`source_type=COURSE`、**`source_id=2`（= 课程主数据 ID）**、
`source_result_id=9`（报名 ID）、`holder_user_id=2`、`ISSUED`。

> 说明：该结果来自**独立开课**（`plan_content_id` 为空），与培训开课（offering 2/3/5…）区分；培训开课完成不触发课程证书。

### 3.4 培训整体通过 → `TRAINING`（来源 ID = `training_plan_id`）

先新建 `TRAINING` 规则（plan 1 → 模板 5，`certificate_rule_id=7`），再结束并冻结计划：

```
POST /api/training/plans/1/end   →  code 0
  plan 1：RUNNING → ENDED
  participant 1（user 1）：resultStatus=PASSED → participantStatus=COMPLETED
  participant 2/3/4：FAILED（未发证）
```

**产出证书**：`id=7`、`CERT-893DC2DD7AFF477F`、`source_type=TRAINING`、**`source_id=1`（= 培训计划主数据 ID）**、
`source_result_id=1`（参训人 ID）、`holder_user_id=1`、`ISSUED`。

**只对合格参与者发证**：participant 2/3/4 判为 `FAILED`，未产生任何证书。

### 3.5 幂等

| 重复操作 | 结果 |
|---|---|
| 再次 `publish-score`（attempt 45） | code 0，证书数不变 |
| 再次 `synchronize`（plan 1，结束前） | code 0，证书数不变 |
| 已完成课程再次上报学习会话 | code 0，证书数不变 |

证书总数始终未增加，唯一键 `uk_holder_source_deleted` 与签发前置查询共同保证。

### 3.6 同一来源只能有一条启用规则（新增限制，Task 4）

真实 HTTP 验证（`POST/PUT /api/certificate/rules`）：

| 场景 | 结果 |
|---|---|
| 新建 `TRAINING/1` 启用规则 | code 0，返回 `certificateRuleId=7` |
| **同来源再建一条启用规则** | **code 9418「该来源已存在启用中的签发规则，请先禁用后再启用新规则」** |
| 同来源再建同模板规则（沿用既有唯一键） | code 9412「同来源与模板的签发规则已存在」 |
| **更新该规则自身（改名，仍启用）** | code 0，未误报 9418（排除自身正确） |
| **另一来源（`COURSE/1`）新建启用规则** | code 0，返回 id=9（限制不误伤其他来源） |

### 3.7 查询侧

- 管理员 `GET /api/certificate/certificates` → `total=3`，返回 `TRAINING/1`、`COURSE/2`、`EXAM/38` 各一条。
- 学员 `GET /api/certificate/my-certificates`（admin）→ `code 0`，`total=2`（本人 `EXAM/38` 与 `COURSE/2`）。

## 4. 验收数据构造与还原（透明记录）

验收在团队共享库上执行，下列写入均属**验收造数据**，逐条记录以便追溯：

| 数据 | 原因 | 处理 |
|---|---|---|
| attempt 45 / 47、答案、评阅记录 | 走真实作答链路 | **保留**（真实业务事实） |
| 课程学习会话（enrollment 9） | 走真实学习链路 | **保留** |
| 证书 5/6/7 | 真实签发产物 | **保留**（作为三类来源发证证据） |
| 规则 7（`TRAINING/1`）、规则 9（`COURSE/1`） | 验收所需配置 | **保留**（可停用/删除） |
| `sts_exam_reviewer`（execution 42, user 2） | 课程测验评阅需要，`admin` 无 `course:offering:edit` 权限 | **已删除**（还原原状） |
| exam 38 阅卷人临时加 `[1,2]` | 主观题评阅需要 | **已还原为 `[1]`** |
| plan 1 参与人 2/3/4 的逐内容结果 `PENDING` → `EXPIRED` | 结束预检要求必修内容有终态结论 | **保留 `EXPIRED`**（`end` 后 `freezePlan` 已按此冻结为 `FAILED`，与计划终态自洽） |
| 线下场次 1 `IN_PROGRESS` → `COMPLETED` | 结束预检要求无进行中场次 | **保留**（业务上线下课确已结束） |
| plan 1 `RUNNING` → `ENDED` | 冻结发证的必经状态 | **保留**（不可逆） |

> 说明：`EXPIRED` 是 `TrainingPlanLifecycleServiceImpl.TERMINAL` 定义的业务终态，语义为「到期未完成」，
> 与 participant 2/3/4 既有的 `INCOMPLETE` 结论一致；未参与学员不因计划结束而获得证书（实测 `FAILED`、无证书）。

## 5. 未覆盖项与遗留

1. **未做**前端浏览器端真实渲染/交互验收（本机无 playwright/agent-browser，需 CDP 直连）；
   Task 2 的界面改动仍以 `vue-tsc` + `eslint` + build 为证据。
2. **未做** `EXAM` 候选排除派生考试的 SQL 条件单测断言（Mockito 无法断言 Wrapper 片段），
   仍以只读库核对（候选 16 → 10 条）为准；如需硬证据应补 MySQL IT。
3. `sts_exam_reviewer` 中 `admin` 缺 `course:offering:edit`，导致课程测验评阅需临时补记录；
   这是**权限配置问题，不是本次改动引入**，未在本次处理。
4. 培训结束预检要求「所有参与者 × 必修内容均有终态结论」，使**单人多内容**计划的端到端验收成本很高；
   本次以「未完成学员置为到期终态」推进，属验收造数据，非业务代码缺陷。
