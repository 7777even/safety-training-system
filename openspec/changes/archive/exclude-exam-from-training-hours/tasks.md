# Tasks：考试与题库退出培训学时

> 状态：已完成（2026-09-10）。Owner：lims。仅后端；前端以 OpenAPI 为准另开。

## 1. 完成考试与题库阶段不填学时

- [x] 1.1 完成考试与题库阶段不填学时

- **状态**：已完成（2026-09-10）
- **Owner**：lims
- **输入**：design D2；spec「考试与题库不得填写安排学时」。
- **输出**：模板/计划保存与 apply-template 将 EXAM/QUESTION_BANK 学时统一为 0；正数 400/8165。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：既有模板/计划阶段保存、apply-template；新增错误码 8165。
- **数据影响**：无 DDL；新写这两类 `requiredHours=0`。
- **验收标准**：
  1. 考试/题库省略、null 或 0 可保存，落库 0。
  2. 提交正数返回 400/8165，消息区分考试与题库，不写库。
  3. apply-template 将模板中考试/题库正数学时拷成 0。
  4. 课程与线下正数学时仍可保存。
- **测试方式**：TDD；先改 `TrainingPlanServiceImplTest`、`TrainingTemplateServiceImplTest` 观察失败，再最小实现；运行上述零
  DB。

## 2. 完成发布与预警按课程和线下合计学时

- [x] 2.1 完成发布与预警按课程和线下合计学时

- **状态**：已完成（2026-09-10）
- **Owner**：lims
- **输入**：design D3～D5；spec「安排学时与获得学时不得混用」修订；Task 1 保存口径。
- **输出**：发布预检、发布冻结、监管学时预警与 OpenAPI/架构说明按课程+线下合计；考试/题库不进占比。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：既有 publish / precheck；`training.openapi.json` 字段与 8165；必要时 `regulation.openapi.json` 合计说明。
- **数据影响**：无 DDL；草稿发布冻结时将考试/题库行写成 0；不改写已发布行。
- **验收标准**：
  1. 总学时只加 COURSE+OFFLINE；线上只加 COURSE；考试正数（历史行）不改变合计。
  2. 课程+线下不够类型/标准学时返回 8121；考试灌数不能让发布通过。
  3. 线上占比按课程/(课程+线下)；考试不抬高占比。
  4. 草稿发布冻结后考试/题库 `requiredHours` 为 0。
  5. `PLAN_TOTAL_HOURS` / `PLAN_ONLINE_RATIO` 与发布同一合计。
  6. 学员考试获得学时仍为 0；线下合格仍发安排学时。
  7. OpenAPI 标明考试/题库不填学时、8165、发布合计；架构文档一句同步。
- **测试方式**：TDD；先改 `TrainingPlanPublishValidatorTest`（含 `mixedExamHoursMeetStandard` 改为考试不能凑满）、
  `WarningDetectServiceImplTest`、既有结果投影回归；运行上述零 DB；`git diff --check`。无 DDL，不跑 IT。

## 完成门槛

1. 规格符合性：考试/题库不填、不计入学时；发布与预警公式一致。
2. 代码质量：无前端改动，无 DDL，无越界重构。
3. 确认后才实施；完成后更新本文件与进度表，写 QA / Retro。
