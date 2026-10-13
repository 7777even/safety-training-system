# Tasks：补齐 M2 读取面与可重现事实

> 状态：已人工确认（含 L4 数据库变更授权，2026-07-31）。
>
> Owner：lims
>
> 执行限制：批次 A 涉及数据库结构与历史数据修复，必须先于批次 B 完成并通过 IT；批次 C 只改文档。

## Task 1：随机卷种子落库与考生档案快照修复（批次 A，L4）

- 状态：已完成（2026-07-31）
- Owner：lims
- 输入：路线图阶段 2 第 6 条「结果可重现」、阶段 3 第 2 条「冻结人员档案快照」；
  `sts_paper_version` / `sts_exam_candidate` 现状；`ExamAttemptQueryMapper.xml` 的快照读取惯用法
- 输出：migration + `backend/sql` 同步；`PaperVersion.randomSeed` 落库与回显；
  `ExamCandidateProfileSnapshotAssembler`；相关零 DB 测试与 MySQL IT
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：`PaperVersionVO` 增加 `randomSeed`；不新增路径
- 数据影响：
  - `sts_paper_version` 新增 `random_seed` 列
  - 历史 `sts_exam_candidate.candidate_profile_snapshot_json` 回填
  - migration `V20260731100000__add_paper_random_seed_and_fix_candidate_snapshot.sql`
- 验收标准：
  - [x] 组卷时 `randomSeed` 落库；未提供时系统生成并记录实际使用的种子
  - [x] 版本详情返回 `randomSeed`；历史版本返回 `null`，不编造种子
  - [x] 新建考生时快照包含账号、姓名、机构、机构名称、部门路径、工号、职务七项，缺失写 `null` 不省略键
  - [x] `ExamServiceImpl` 与 `ExamAttemptServiceImpl` 两处不再手工拼接 JSON 字符串
  - [x] 历史快照回填后，`ExamAttemptQueryMapper.xml` 的 `COALESCE` 能命中快照而非回退
  - [x] `backend/sql/schema-question.sql` 与 `schema-exam.sql` 与 migration 一致
- 测试方式：
  - 零 DB：组装器字段完整性、关联缺失时写 `null`、种子透传
  - MySQL IT：`random_seed` 真实读写、回填 SQL 结果、快照 JSON 键完整性
  - `mvn -pl sts-service-admin -am test`、`mvn -pl sts-service-admin -am -Pintegration-test test`

## Task 2：读取面补齐（批次 B）

- 状态：已完成（2026-07-31）
- Owner：lims
- 输入：Task 1；既有 `sts_exam_policy` / `sts_exam_reviewer` / `sts_paper_draw_rule` /
  `sts_question_asset` / `sts_exam_score_publish_log` / `sts_exam_anticheat_event` 事实
- 输出：策略回显、批阅人读取、试卷版本列表与抽题规则装配、题目素材回显、
  两处服务端筛选、考生列表分页、两个遗漏读取面；同步 OpenAPI、零 DB 测试与 IT
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：
  - 新增 `GET /api/exam/exams/{examId}/reviewers`（权限 `exam:exam:list`）
  - 新增 `GET /api/paper/papers/{paperId}/versions`（权限 `paper:paper:list`）
  - 新增 `GET /api/exam/attempts/{examAttemptId}/anticheat-events`（权限 `exam:attempt:list`）
  - 新增 `GET /api/exam/attempts/{examAttemptId}/score-publish-logs`（权限 `exam:score:publish`）
  - **破坏性变更**：`GET /api/exam/exams/{examId}/candidates` 返回由 `List` 改为 `PageResult`
  - `ExamVO` 增加 `policy`；`PaperVersionVO` 增加 `drawRules`；`QuestionVersionVO` 增加 `assets`
  - `QuestionQuery` 增加 `publishedStatus`；`PaperQuery` 增加 `hasPublishedVersion`
  - 不新增权限码
- 数据影响：仅读取既有表，无结构或数据变更
- 验收标准：
  - [x] 考试详情返回完整策略；分页列表不返回策略
  - [x] 批阅人接口返回用户 ID、姓名、账号、机构与分配来源
  - [x] 试卷版本列表可得到草稿版本的 `paperVersionId`
  - [x] 版本详情返回 `drawRules` 与 `randomSeed`，`drawRules` 不再恒为 `null`
  - [x] 题目详情的当前版本返回素材列表
  - [x] 题目按 `publishedStatus`、试卷按 `hasPublishedVersion` 服务端筛选，分页总数与条件一致
  - [x] 考生列表分页并返回姓名、账号、机构、部门
  - [x] 防作弊事件与成绩发布流水可读取
  - [x] 不修改判分、批阅、成绩发布、补考与练习重置规则
- 测试方式：
  - 零 DB：VO 装配、Controller 参数与分页绑定、policy 只在详情装配
  - MySQL IT：两处 `exists` 子查询与逻辑删除交互、reviewers / candidates 的 join、分页稳定性
  - `mvn -pl sts-service-admin -am test`、`mvn -pl sts-service-admin -am -Pintegration-test test`

## Task 3：契约注释与枚举修正（批次 C）

- 状态：已完成（2026-07-31）
- Owner：lims
- 输入：`CourseSnapshotAssembler.OPEN_ITEM_TYPES` 实际白名单；`PaperServiceImpl` 版本号语义
- 输出：`docs/api/course.openapi.json`、`paper.openapi.json`、`exam.openapi.json`、`question.openapi.json`
- 前后端影响：仅后端文档
- API 依赖：`ItemCreateRequest.itemType` 移除 `KNOWLEDGE_POINT`；不新增路径
- 数据影响：无
- 验收标准：
  - [x] `POST /api/course/items` 说明与实现一致，覆盖 `examId` 使用、互斥关系与发布校验
  - [x] `itemType` 枚举不再包含实现会拒绝的 `KNOWLEDGE_POINT`
  - [x] `PaperVO.currentVersionNo` 描述明确它是版本序号而非 `paperVersionId`，并指明去哪取 `paperVersionId`
  - [x] 不改字段名，不破坏 archive / certificate / evaluation / question 的同名约定
- 测试方式：
  - JSON 解析、`$ref` 完整性、Controller↔OpenAPI operation 集合比对、Schema↔DTO/VO 字段比对、
    枚举取值域↔DDL 列注释比对
  - `openspec validate --strict`、`git diff --check`

---

## 实施验证记录（2026-07-31）

- Red：批次 A 组装器不存在导致编译失败；批次 B 的 `getPolicy` / `getDrawRuleId` / `getAssets` 均先失败再实现。
- Green：Admin 全量零 DB **429/429**；全量 MySQL IT **104/104**（基线 93 + 新增 11）。
- Controller ↔ OpenAPI operation 集合：exam 26↔26、paper 10↔10、question 23↔23、course 55↔55，遗漏与多余均为 0。
- Schema ↔ DTO/VO 字段交叉比对 204 个，不一致 0。
- `openspec validate --strict`、`git diff --check` 通过。

### 实施中发现并修正的额外问题

1. **`sts_question_version` / `sts_paper_version` 无 `deleted` 列**。二者继承 `BaseAuditEntity` 而非 `BaseEntity`
   ——不可变版本本就不应被删除。最初的 `exists` 子查询误加了 `v.deleted = 0`，被 IT 以
   `Unknown column 'v.deleted'` 拦下并改正。
2. **`GET /api/paper/papers/{paperId}/versions` 一度漏建**。批次 B 只补了 VO 与装配，端点在批次 C 的
   Controller↔契约比对中被发现缺失，已补齐并配 Red 测试。
3. **两处既存契约缺字段**（非本轮引入）：`StartAttemptRequest.trainingParticipantId`、
   `QuestionVersionVO.blankAnswers` / `keywords`。属同一类「后端有、契约无」缺陷，一并补齐。

### 遗留项

- `asset.openapi.json` 与 `course.openapi.json` 仍有悬空 `$ref`（`responses/NotFoundError`、
  `responses/ConflictError`，合计被 21 个 operation 引用），属素材域与课程域既存缺陷，不在本 Change 范围。
- 题目版本历史、批阅改分历史读取、学员考试记录列表、考生名单 Excel 导入：按 proposal 非目标范围，待 M2.1 确认。
