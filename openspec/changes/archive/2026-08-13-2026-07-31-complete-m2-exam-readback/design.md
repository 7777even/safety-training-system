# Design：M2 读取面补齐与可重现事实修复

## 1. 既有事实与边界

- `sts_exam_policy` 一考试一行，已由 `ExamCreateRequest.policy` / `ExamUpdateRequest.policy` 写入，
  并被 `ExamAttemptServiceImpl` 的补考与成绩回写逻辑读取，但不出现在任何读接口。
- `sts_exam_reviewer` 由 `PUT /exam/exams/{examId}/reviewers` 整体替换（先 delete 再 insert），无读取路径。
- `sts_paper_draw_rule` 由 `PaperServiceImpl.buildVersion` 写入，无读取路径。
- `sts_question_asset` 由 `QuestionServiceImpl.replaceAssets` 写入，无读取路径。
- `sts_exam_candidate.candidate_profile_snapshot_json` 的 DDL 注释要求存
  「账号、姓名、机构、部门路径、工号、岗位和职务」，实际只写 `{"userId": N}`。
- `ExamAttemptQueryMapper.xml` 的 `learnerFields` 片段已确立「快照优先、回退 `sys_user`」的读取惯用法，
  本 Change 的人员展示字段一律复用该写法，不另造第二套。

## 2. 数据库变更（批次 A）

单个 migration `V20260731100000__add_paper_random_seed_and_fix_candidate_snapshot.sql`，
同步 `backend/sql/schema-question.sql`（试卷表所在文件）与 `backend/sql/schema-exam.sql`。

### 2.1 随机卷种子

```sql
ALTER TABLE `sts_paper_version`
  ADD COLUMN `random_seed` bigint DEFAULT NULL COMMENT '随机卷抽题种子（仅RANDOM试卷，用于复现抽题结果）'
  AFTER `draw_setting_mode`;
```

历史行保持 `NULL`，表示该版本生成时未记录种子、不可复现。**不为历史数据编造种子**。

### 2.2 考生档案快照回填

快照 JSON 采用固定 7 键结构，取不到的键写 `null` 而非省略，以便区分「无此人」与「未快照」：

```json
{"userId": 1, "userCode": null, "userName": null, "organizationId": null,
 "organizationName": null, "deptPath": null, "employeeNo": null, "jobTitle": null}
```

回填用 `LEFT JOIN`，关联用户已删除时对应键为 `null`。回填是尽力而为的历史修复，
真正的正确性由后续新建考生时的组装器保证。

### 2.3 写入侧统一

新建 `com.sts.exam.service.ExamCandidateProfileSnapshotAssembler`，
替换 `ExamServiceImpl.java:180` 与 `ExamAttemptServiceImpl.java:696` 两处字符串拼接。
组装器用 `ObjectMapper` 序列化，不手工拼 JSON。

## 3. 读取面设计（批次 B）

### 3.1 考试策略回显

`ExamVO` 增加 `policy: ExamPolicyVO`。**仅在详情接口装配，分页列表不装配**——列表页不需要策略，
逐行查 policy 会形成 N+1。`ExamPolicyVO` 字段与 `ExamPolicy` 实体一一对应，不做裁剪：策略字段本身
就是给编辑页回显用的，裁剪会重新制造「读不全」问题。

### 3.2 批阅人读取

| 接口                                       | 权限               | 说明       |
|------------------------------------------|------------------|----------|
| `GET /api/exam/exams/{examId}/reviewers` | `exam:exam:list` | 返回列表，不分页 |

批阅人数量是人工指派的小集合，不分页。新建 `ExamReviewerQueryMapper` + XML，
`JOIN sys_user` 取姓名工号、`JOIN` 机构表取机构名，复用 `learnerFields` 的 `COALESCE` 写法。

### 3.3 试卷草稿回读

不新增 `/draft` 接口。原因：`compose()` 产生的 DRAFT 版本会推进 `paper.currentVersionNo`
（`buildVersion` 中该赋值在 `if (publish)` 之外），草稿本身可达；真正的障碍是
①`toVersionVO` 不装配 `drawRules`，②没有从 `paperId` 拿到 `paperVersionId` 的入口。

因此改为：

1. 修复 `toVersionVO`，装配 `drawRules`（新建 `PaperDrawRuleVO`，替代当前直接复用
   `PaperDrawRuleRequest` 的分层污染）与 `randomSeed`。
2. 新增 `GET /api/paper/papers/{paperId}/versions`，返回版本摘要列表，权限 `paper:paper:list`。

二者合并后 `GET /papers/versions/{paperVersionId}` 即可完整回读草稿，并顺带交付「试卷版本列表」。

### 3.4 题目素材回显

`QuestionVersionVO` 增加 `assets: List<QuestionAssetVO>`，在 `toVersionVO` 与既有
`options` / `blankAnswers` / `keywords` 同处装配。不新增端点——素材是版本的组成部分，
与选项、答案同级，单独开接口会让前端多一次往返。

### 3.5 服务端筛选

两处筛选都需跨版本表判定，用 `LambdaQueryWrapper.exists(...)` 子查询实现，
保持现有 `page()` 的 Wrapper 写法，不新建 Mapper XML：

- `QuestionQuery.publishedStatus`：
  `EXISTS (SELECT 1 FROM sts_question_version v WHERE v.question_id = ... AND v.published_status = ? AND v.deleted = 0)`
- `PaperQuery.hasPublishedVersion`：
  `EXISTS (SELECT 1 FROM sts_paper_version v WHERE v.paper_id = ... AND v.published_status = 'PUBLISHED' AND v.deleted = 0)`

`hasPublishedVersion` 用布尔语义而非 `currentVersionStatus`：考试新建页要问的是
「这份试卷有没有可用的发布版本」，而不是「当前版本处于什么状态」——后者在草稿覆盖发布版后会给出误导答案。

### 3.6 考生列表改分页

`GET /api/exam/exams/{examId}/candidates` 返回类型由 `List<ExamCandidateVO>` 改为
`PageResult<ExamCandidateVO>`，支持 `query.learnerKeyword` / `candidateStatus` / `organizationId`。

这是**破坏性变更**。前端尚未进入真实联调，此刻变更成本最低；若等到联调后再改，
会同时影响已写好的页面。人员展示字段来自批次 A 修好的快照，同样 `COALESCE` 回退。

### 3.7 两张遗漏的只写不读表

| 接口                                                          | 权限                   |
|-------------------------------------------------------------|----------------------|
| `GET /api/exam/attempts/{examAttemptId}/anticheat-events`   | `exam:attempt:list`  |
| `GET /api/exam/attempts/{examAttemptId}/score-publish-logs` | `exam:score:publish` |

均为纯读取，按 Attempt 维度返回，不分页（单次作答的事件与流水量有限）。

## 4. 契约修正（批次 C）

- `POST /api/course/items` 的 summary「仅 ASSET/LIVE」与实现不符，实现经
  `CourseSnapshotAssembler.OPEN_ITEM_TYPES` 支持 `ASSET/LIVE/EXAM/QUESTION_BANK/PRACTICE`。按实现改写。
- `ItemCreateRequest.itemType` 枚举含 `KNOWLEDGE_POINT`，但该值不在
  `OPEN_ITEM_TYPES` 中，实现会拒绝。**契约对外承诺了实现拒绝的取值**，从枚举移除。
- `PaperVO.currentVersionNo` 不改名，描述强化为「当前版本序号（非 `paperVersionId`）；
  创建考试所需的 `paperVersionId` 请从版本列表或版本详情接口获取」。

## 5. 测试分层

按 CLAUDE.md §5.2 / §5.3 划线：

- **零 DB**：VO 装配逻辑、Controller 参数与分页绑定、policy 只在详情装配、快照组装器的字段完整性
  （含关联数据缺失时写 `null` 而非省略）。
- **MySQL IT**：`random_seed` 真实读写、快照回填 SQL、两处 `exists` 子查询筛选与逻辑删除的交互、
  reviewers / candidates 的 join 结果、分页稳定性。`exists` 子查询与 join 属于「只有真 SQL 能证明」的语义，
  不在零 DB 用 mock 硬凑。
