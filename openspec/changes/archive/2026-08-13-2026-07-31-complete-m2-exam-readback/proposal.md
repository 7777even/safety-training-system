# Proposal：补齐 M2 题库、试卷与考试的读取面和可重现事实

## 背景

M2（题库、试卷、考试）已标记完成，但前端准备关闭 Mock 进入真实联调时暴露出一批「写得进、读不出」的缺口。逐条核对代码、DDL
与路线图后确认，问题分三类：

1. **契约缺口**：考试策略只能写不能读（`ExamVO` 无 `policy`，但创建/更新都接受 `ExamPolicyRequest`）；批阅人只能整体替换不能读取；
   题目素材只能替换不能回显；题库选题与试卷选择缺少服务端筛选，前端只能对单页结果二次过滤，导致分页总数与可选项不准。
2. **阶段验收未达标**：路线图阶段 2 第 6 条要求「组卷规则校验、预览、换题和结果可重现」，但 `randomSeed` 从未落库
   （不在 `PaperVersion` 实体，也不在 `sts_paper_version` 表），随机卷抽题结果不可重现。
   路线图阶段 3 第 2 条要求「冻结人员档案快照」，但 `candidate_profile_snapshot_json` 只写入 `{"userId": N}`，
   而 `ExamAttemptQueryMapper.xml` 正是从该快照读 `$.userName` / `$.userCode`，永远落空并回退到实时 `sys_user`——
   历史考试记录会显示用户改名后的当前姓名，冻结快照形同虚设。
3. **只写不读的表**：`sts_exam_reviewer`、`sts_exam_review_record`、`sts_paper_draw_rule`、`sts_question_asset`、
   `sts_exam_score_publish_log`、`sts_exam_anticheat_event` 六张表有写入路径但没有任何读取面。
   其中 `PaperVersionVO.drawRules` 字段在 Java VO 中已声明但全仓库无任何赋值，恒为 `null`。

## 目标

1. 让考试策略、批阅人、题目素材、抽题规则可以被读回，支撑前端编辑页刷新后正确回显。
2. 让 `randomSeed` 真实落库并可读，兑现「结果可重现」。
3. 让考生档案快照名副其实，历史考试记录不再随用户改名而变化。
4. 为题库选题与试卷选择提供服务端筛选，保证分页正确。
5. 修正契约与实现不一致的注释和枚举，不让前端按错误契约对接。

## 用户价值

- 考试编辑页刷新后能完整回显防作弊与补考策略，不会因为读不到而在保存时覆盖既有配置。
- 组卷页刷新后能读回草稿的题目、抽题规则和随机种子，随机卷可复现。
- 题库选题分页数量与可选题目准确。
- 考试记录与批阅任务显示的是考试当时的人员信息，可用于追溯和审计。

## 功能范围

1. 数据库：`sts_paper_version` 增加 `random_seed`；回填历史 `sts_exam_candidate.candidate_profile_snapshot_json`。
2. 读取面：`ExamVO.policy`；`GET /api/exam/exams/{examId}/reviewers`；
   `GET /api/paper/papers/{paperId}/versions`；`PaperVersionVO.drawRules` / `randomSeed` 装配；
   `QuestionVersionVO.assets`；`GET /api/exam/attempts/{examAttemptId}/anticheat-events`；
   `GET /api/exam/attempts/{examAttemptId}/score-publish-logs`。
3. 筛选：`QuestionQuery.publishedStatus`、`PaperQuery.hasPublishedVersion`。
4. 考生列表改标准分页并补充人员展示字段。
5. 契约修正：`POST /api/course/items` 说明与 `itemType` 枚举；`PaperVO.currentVersionNo` 描述。

## 人工确认记录

- 本 Change 含数据库结构变更（`sts_paper_version.random_seed`）与历史数据修复（考生档案快照回填），
  按根 `AGENTS.md` §1.1 属 L4。已于 2026-07-31 取得人工授权后实施。
- 前端复审曾建议将 `PaperVO.currentVersionNo` 改名为 `currentPaperVersionId`。经核对
  `PaperServiceImpl.buildVersion`、`requirePaperVersion(Long, Integer)` 与 DDL
  `current_version_no int COMMENT '当前版本号'`，确认它是版本序号而非主键，且
  archive / certificate / evaluation / question 五处采用同一约定。**该建议基于错误前提，不予采纳**，
  改为强化字段描述以降低误用风险。

## 非目标范围

- 任何 `frontend/` 改动、页面、Mock、UI。
- 题目版本历史与版本对比接口。
- 批阅改分历史读取接口。
- 学员考试记录列表接口。
- 考生名单 Excel 导入。
- 不修改判分、批阅、成绩发布、补考或练习重置规则。
- 不新增权限码或改变既有权限语义。
- 不修改 `/api/paper/papers/{paperId}/preview` 的定位与返回结构。
