# Design：题库 / 试卷 / 考试后端（M2）

## 包与扫描

| 包                  | 职责                                                   |
|--------------------|------------------------------------------------------|
| `com.sts.question` | 知识点、题库分类/题库、题目主表与版本/选项/填空/关键词/素材、用户题目状态、导入批次         |
| `com.sts.paper`    | 试卷分类、试卷、试卷版本、抽题规则、版本题目快照                             |
| `com.sts.exam`     | 考试分类/考试/策略、考生、批阅人、作答尝试与题目/选项快照、答案、批阅、成绩发布、防作弊事件、练习重置 |

`AdminApplication`、`HttpApiExceptionHandler`、`ResultEasyTransAdvice` 增加上述包扫描。

## 数据模型

真相源：已评审 DDL `03_question_paper.sql`、`04_exam.sql`；公共导入表来自 `01_org_common.sql`（`sts_common_import_batch` /
`_error`，本期一并建表）。

- 题目类型：`SINGLE` / `MULTIPLE` / `JUDGE` / `FILL` / `ESSAY`（API 描述中 SHORT 对齐 ESSAY）
- 题目/试卷发布：写新版本行，旧 `version_no` 只读；主表更新 `current_version_no`
- 随机卷：保存 `sts_paper_draw_rule`；发布时解析为固定 `sts_paper_version_question` 快照（可选 seed）
- 考试开考：从 `paper_version` 冻结 `sts_exam_attempt_question` / `_option` 与正确答案快照 JSON

## API 设计（摘要）

前缀 `/api`（context path）；权限 `question:*` / `paper:*` / `exam:*`。

- 知识点树 CRUD：`/question/knowledge-points`
- 题库分类 / 题库 CRUD：`/question/bank-categories`、`/question/banks`
- 题目 CRUD + 发布新版本：`/question/questions`、`/{id}/publish`
- 素材关联：`/question/questions/{id}/assets`（仅 assetId，无转码）
- 批量导入：`POST /question/banks/{id}/import`（JSON 行列表 → import_batch + 题目）
- 错题状态 upsert：`PUT /question/user-states`
- 试卷 CRUD + 组卷/预览/发布：`/paper/papers`、`/{id}/compose`、`/{id}/preview`、`/{id}/publish`
- 考试 CRUD + 发布：`/exam/exams`、`/{id}/publish`
- 考生绑定（audience）：`POST /exam/exams/{id}/candidates/bind-audience`
- 批阅人：`PUT /exam/exams/{id}/reviewers`
- 作答：`POST /exam/attempts/start`、`PUT .../answers`、`POST .../submit`、`POST .../timeout-submit`
- 批阅 / 发布成绩：`POST /exam/answers/{id}/review`、`POST /exam/attempts/{id}/publish-score`
- 补考 / 练习重置：`POST /exam/candidates/{id}/retake`、`POST /exam/candidates/{id}/practice-reset`
- 防作弊事件：`POST /exam/attempts/{id}/anticheat-events`

## 权限

`question:knowledge:*`、`question:bank:*`、`question:question:*`、`question:import`、`question:user-state:*`；
`paper:paper:*`、`paper:publish`；
`exam:exam:*`、`exam:publish`、`exam:attempt:*`、`exam:review`、`exam:score:publish`。

## 错误码

| 域        | 范围        |
|----------|-----------|
| question | 6000–6199 |
| paper    | 6500–6599 |
| exam     | 7000–7199 |

（课程占用 50xx，不用 5000–5199。）

## 课程回接（EXAM-04）

`CourseItemServiceImpl.OPEN_ITEM_TYPES` 增加 `EXAM` / `QUESTION_BANK` / `PRACTICE`。创建时 `refBusinessId`（或现有
assetId/扩展字段）指向 examId；完成判定读考生已发布成绩（passed）——最小钩子，暂定。

## 技术取舍

- 导入：无 Apache POI 则 JSON 批量导入写 import_batch；模板类型记 `EXCEL` 以兼容后续。
- 随机卷：发布时一次抽题固化，不在开考时再抽。
- 防作弊：仅落库，无强制策略引擎。
- 答题幂等：`client_attempt_token` + 交卷状态机幂等。

## 风险

DDL 表多，IT 覆盖关键表映射与唯一约束；全链路联调依赖前端另开。
