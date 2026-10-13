# 2026-08-27 后端相关 OpenSpec Change 归档

本次只处理活跃目录中**涉及后端**的 Change（纯后端，或含后端 API/契约/落库的混合 Change）。纯前端 Change 仍留在
`openspec/changes/`，不在 lims 协作范围。

归档目录统一为 `openspec/changes/archive/2026-08-27-<原 Change 名>/`。

## 1. 已完成，直接归档

| Change                                                              | 主规格                                                                          |
|---------------------------------------------------------------------|------------------------------------------------------------------------------|
| `2026-07-30-add-exam-review-attempt-list-apis`                      | 并入 `exam-attempt-query`；见该目录 `archive-record.md`（tasks.md 与进度表 EXAM-08 曾不一致） |
| `2026-08-17-support-restricted-pdf-preview`                         | `restricted-pdf-preview`                                                     |
| `2026-08-19-add-review-comment-readback`                            | 并入 `exam-attempt-query` / `learner-exam-reading`                             |
| `2026-08-19-sync-exam-paper-default-contract-and-review-regression` | 试卷默认规则已有 `exam-paper-rule-defaults`；工作台展示未提升为主规格                             |
| `2026-08-20-fix-exam-publish-lock-and-workbench-tabs`               | 批阅锁定并入 `exam-attempt-query`；工作台页签为前端，未提升                                     |
| `2026-08-20-redesign-exam-score-disposition-and-retake-eligibility` | 补考资格只读判定并入 `exam-retake`                                                     |
| `2026-08-25-integrate-flyway-mysql`                                 | `database-schema-delivery`                                                   |
| `2026-08-25-separate-course-assessment-from-exam`                   | `course-assessment-source-separation`                                        |
| `2026-08-26-fix-course-offering-assessment-configuration`           | 课程考核分域已覆盖；开课 UI 未提升                                                          |
| `2026-08-26-reject-empty-transcode-success`                         | 并入 `asset-transcode`                                                         |
| `2026-08-27-complete-course-cover-api`                              | `course-cover`                                                               |
| `2026-08-27-fix-course-assessment-result-visibility`                | 并入 `course-assessment-source-separation` / `learner-exam-reading`            |
| `2026-08-27-prefix-file-objects-with-tenant-code`                   | `tenant-file-object-prefix`                                                  |

## 2. 不能按完成态归档：写记录后归档

每条目录内有 `archive-record.md`。

| Change                                               | 原因摘要                                   | 后续                        |
|------------------------------------------------------|----------------------------------------|---------------------------|
| `2026-07-27-implement-organization-master-data`      | Task 1 未关闭；Task 2～3 待确认；机构语义已被后续租户开户改写 | BASE-01A 仍进行中，重启须新 Change |
| `2026-07-29-complete-learner-exam-read-contract`     | 后端已实现，历史 IT 勾选未关                       | EXAM-06 待验收在前端/联调         |
| `2026-07-29-link-course-items-to-exam-results`       | 已被课程考核与独立考试分离替代                        | 禁止恢复 `examId` 课程项         |
| `2026-08-04-complete-m1-m2-m3-contracts`             | 空壳，无 proposal/tasks                    | 无                         |
| `2026-08-04-complete-m3-reference-resource-contract` | Task 1 查询已落地；Task 2～3 未关               | 若发布冻结仍缺，另开 Change         |
| `2026-08-04-implement-asset-transcode-backend`       | COURSE-02/M6 已关闭，但 Task 4/6 真机样本未全勾    | 标准 DOCX 视觉复核另开，不阻塞当前版本    |

## 3. 未归档（纯前端或前端主责，本次不处理）

包括但不限于：课程/考试/素材管理端与学员端页面、Mock 同步、浏览器会话隔离、PC 布局、移动学习页等。它们仍在
`openspec/changes/`。

`2026-07-29-complete-m3-training-execution-loop` 仍活跃：主责是 PC 培训执行前端，Task 4 才是后端编排。后端可引用资源缺口见已归档
`complete-m3-reference-resource-contract` 的 `archive-record.md`，不把该前端 Change 一并归档。

## 4. 进度表

已把对应行的 OpenSpec 路径改为归档目录；BASE-01A 保持进行中并指向本记录。
