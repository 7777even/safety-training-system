# Design：培训标准 / 计划 / 执行后端（M3）

## 包与扫描

| 包                    | 职责                                        |
|----------------------|-------------------------------------------|
| `com.sts.training`   | 类型、标准、模板、计划、参训人、线下场次、签到、线下成绩、附件、奖惩、执行实例生成 |
| `com.sts.evaluation` | 评价表单 / 版本 / 项目 / 评分规则（定义侧；评价作答与综合计算不在本期）  |

`AdminApplication`、`HttpApiExceptionHandler`、`ResultEasyTransAdvice` 增加扫描。监管关系与资源授权复用 `com.sts.org` /
grant 服务，不新建监管表。

## 数据模型

真相源：归档 DDL `05_training.sql` + `06_*` 评价表单四表（form / version / item / score_rule）。

- 计划发布：冻结 `training_type_snapshot_json`、`training_standard_snapshot_json`、`template_snapshot_json` 与
  `source_snapshot_hash`。
- 内容类型：`COURSE` / `EXAM` / `QUESTION_BANK` / `OFFLINE`（无 LIVE）。
- 参训人：`ASSIGN` / `IMPORT` / audience bind；唯一 `(plan, user)`。
- 执行实例（TRAIN-05）：
  - COURSE → 生成或关联 `course_offering`（若课程域已有开课能力则写关联表或复用最小关联字段；暂定：
    `sts_training_plan_content` 上记录 `execution_ref_id` 不改表则用内容 `resource_id` + 幂等查找开课；**采用**独立轻量表
    `sts_training_execution_link`：`plan_content_id` + `execution_type` + `execution_id` 唯一，避免改评审主表语义）。
  - EXAM → link 到 `exam_id`（可复制策略或直接引用已有考试定义；暂定直接引用 `resource_id` 为 examId，开考仍走 exam 域）。
  - OFFLINE → 插入 `sts_training_offline_session`。
- 教师任务：无独立 DDL；`GET /training/teacher-tasks` 按当前用户聚合其 `teacher_user_id` 线下场次及待确认签到数。

若引入 `sts_training_execution_link`，在本 Change migration 中建表并写明扩展理由。

## API 摘要（`/api`）

- `/training/types` CRUD + 可选 seed 初始化 ≥25 类型
- `/training/standards` CRUD + rules 替换 + publish
- `/training/template-categories`、`/training/templates` CRUD + stages/contents + publish
- `/evaluation/forms` CRUD + versions/items + publish
- `/training/plans` CRUD + stages/contents + evaluation-form bind + publish
- `/training/plans/{id}/participants` list + bind-audience + assign
- `/training/plans/{id}/generate-executions` 幂等
- `/training/offline-sessions` CRUD（计划维度）
- `/training/attendance` upsert/confirm
- `/training/offline-scores` upsert
- `/training/attachments` CRUD
- `/training/reward-penalties` CRUD
- `/training/teacher-tasks` 列表

## 权限

`training:type:*`、`training:standard:*`、`training:template:*`、`training:plan:*`、`training:participant:*`、
`training:attendance:*`、`training:offline-score:*`、`training:attachment:*`、`training:reward:*`、`training:teacher:task`；
`evaluation:form:*`。

## 错误码

| 域          | 范围        |
|------------|-----------|
| training   | 8000–8499 |
| evaluation | 8500–8699 |

## 技术取舍

- 类型 seed：migration 或 init 数据写入不少于 25 条正式 `type_code`（编码稳定、幂等）。
- 跨机构引用类型/标准/模板：创建计划时调用已有 ResourceOrgGrant 校验 USE。
- 执行生成幂等：同一 `plan_content_id` 不重复建 link / offline session。
- 综合成绩 / 评价作答 / 问卷：明确不在本期。

## 风险

表多；IT 覆盖关键唯一约束与逻辑删除。课程开课关联若课程 API 不完整，execution_link 仅记录目标 id，不强制调用外部开课成功。
