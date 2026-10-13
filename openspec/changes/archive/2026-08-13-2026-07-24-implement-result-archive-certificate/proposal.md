# Proposal：实现综合结果 / 档案 / 证书后端（M4）

## 背景

M3 培训执行后端已完成，具备参训人、线下成绩、奖惩与评价表单定义。M4 在稳定执行事实上建设问卷、评价作答、不可变综合成绩批次、三级档案与证书签发闭环。DDL
真相源：`06_certificate_survey_evaluation_archive_regulation.sql`（不含监管预警表，属 M5）。评价表单定义已由 M3 TRAIN-03 落地，本
Change 不重复建 form 表。

## 目标

交付 lims 纯后端 M4 纵向闭环：

1. RESULT-01：问卷定义、不可变版本、投放实例、参与人、作答与基础统计。
2. RESULT-02：评价记录（专家查课/人员/课程质量等）提交与明细。
3. RESULT-03：综合成绩来源归集、不可变计算批次、确认正式结果与替代重算。
4. ARCHIVE-01：培训期次档案、归档版本、参训人/资产快照与导出任务最小 API。
5. CERT-01：证书模板、版本、元素、签发规则。
6. CERT-02：幂等签发、查看、撤销留痕、人工重发（新证书号）。
7. RESULT-04：OpenAPI、权限、进度表、QA 关闭 M4。

## 用户价值

管理员可投放问卷与确认正式综合成绩；可归档一期培训并签发/撤销证书；学员可查看已发布问卷与本人证书（API 层）。

## 功能范围

- 包：`com.sts.survey`、扩展 `com.sts.evaluation`（record/calculation）、`com.sts.archive`、`com.sts.certificate`
- migration + schema IT；OpenAPI 分文件；零 DB + Mapper IT；menus 权限
- 错误码：survey 9000+、evaluation-record/calc 8700+、archive 9200+、certificate 9400+

## 非目标范围

- 任何 `frontend/` 改动、页面、Mock、UI
- 监管预警（M5）、运营工作台统计（OPS）、素材转码（M6）
- 真实 PDF/图片证书渲染引擎（签发可写占位 fileId + 内容快照 JSON）
- 问卷高级图表与匿名脱敏全链路产品细则（API 保留 is_anonymous 字段与最小脱敏）

## 前后端影响

仅后端；前端以 OpenAPI 为准另开。
