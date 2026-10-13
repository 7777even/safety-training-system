# Design：综合结果 / 档案 / 证书后端（M4）

## 包与扫描

| 包                     | 职责                               |
|-----------------------|----------------------------------|
| `com.sts.survey`      | 问卷、版本、题目/选项、投放、参与人、作答、统计         |
| `com.sts.evaluation`  | 扩展：评价记录/明细；计算批次/来源（form 定义复用 M3） |
| `com.sts.archive`     | 培训档案主表、版本、参训快照、资产、导出任务           |
| `com.sts.certificate` | 模板/版本/元素、签发规则、证书、撤销              |

`AdminApplication`、`ResultEasyTransAdvice` 增加扫描。不建监管预警表。

## 数据模型

- 问卷：draft 题库可改；publish 写 `sts_survey_version` 快照 JSON + hash，旧版本只读；instance 绑定 audience/plan。
- 评价记录：绑定已发布 `form_version_id`；SUBMITTED 后明细不可覆盖（改则新记录或拒绝，暂定拒绝覆盖）。
- 计算批次：按参训人 + form_version 递增 `run_no`；CONFIRMED 时 `official_marker=1` 唯一；重算新建 run 并 SUPERSEDE 旧正式。
- 档案：对 ENDED 计划生成 `sts_archive_training` + `sts_archive_version` 快照 hash；不可覆盖，更新只能新版本。
- 证书：签发幂等 `(holder, source_type, source_id, template)`；撤销写 revocation；重发先撤销再建新证书号。

## API 摘要

- `/survey/surveys` CRUD + questions replace + publish
- `/survey/instances` create/list + bind participants / audience
- `/survey/instances/{id}/submit` 作答；`/survey/instances/{id}/stats` 统计
- `/evaluation/records` create/submit/get
- `/evaluation/calculation-runs` calculate / confirm / list
- `/archive/trainings` create-from-plan / versions / get
- `/certificate/templates` CRUD + publish version/elements
- `/certificate/rules` CRUD
- `/certificate/certificates` issue / list / revoke / reissue

## 技术取舍

- 证书文件：`certificate_file_id` 允许占位（如 0 或专用占位文件 ID），内容以 snapshot JSON 为准。
- 综合分：最小实现按 score_rule 权重聚合参训人 course/exam/offline/reward 字段与线下成绩表。
- 问卷统计：选项计数与评分均值最小集。

## 风险

表多；IT 覆盖唯一约束与 official_marker；与 M3 participant 字段回写 `official_calculation_run_id`。
