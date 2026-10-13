# Tasks：统一考核执行快照与完整性校验

> 状态：已完成；用户于 2026-09-15 确认按本方案实施。
>
> Owner：`lims`。仅后端；前端以 OpenAPI 为准另开。
>
> 本 Change 只包含一个业务 Task；按 TDD、定向验证、双轴 Review、QA/Retro 和进度同步完整闭环。

## Task 1：完成统一、版本化且可验证的考核执行快照

- 状态：已完成
- Owner：`lims`
- 输入：功能项 14～16、22、48～49；现有课程/独立考试/培训执行生成与开考链；用户确认开发期不考虑旧数据兼容。
- 输出：所有 `AssessmentExecution` 共用 v1 完整 `snapshot_json`、统一 Codec、规范化 SHA-256 和不可变门禁；课程开考不再因
  MySQL JSON 文本规范化误报 `7063`。
- 前后端影响：仅后端；HTTP 请求和成功响应保持不变，前端按更新后的 OpenAPI 错误语义处理。
- API 依赖：同步 `docs/api/course.openapi.json`、`exam.openapi.json`、`training.openapi.json` 中现有开考/进入接口的 `7063`
  描述；不新增路径、参数、权限或错误码。
- 数据影响：重构 `sts_assessment_execution`，新增 `snapshot_schema_version`、`snapshot_json`，保留 `snapshot_hash`
  ；移除七个旧快照字段；查询列降为投影并与快照核对。同步 Admin migration、Admin-init `schema-exam.sql` 和
  `sts.init.schema-version`。不迁移旧执行数据；存在旧执行行时 migration 明确失败，开发数据库须整体重建。
- 实施步骤：
  1. [x] Red：先增加 Codec、各来源构建和真实 MySQL JSON 往返失败测试，确认当前课程分支可复现 `7063`。
  2. [x] Green：建立强类型 v1 快照、规范化 hasher 和唯一 Codec；重构课程、独立考试及培训执行创建/读取链。
  3. [x] Green：完成最终 schema、增量 migration、Entity/Mapper 调整和不可变刷新门禁。
  4. [x] Refactor：仅消除本 Task 产生或暴露的重复序列化/hash 逻辑，删除旧分支和旧字段访问，不扩展业务规则。
  5. [x] 同步 OpenAPI、架构说明、Task 状态、进度表、QA 和 Retro；完成规格符合性与代码质量双轴 Review。
- 验收标准：
  1. 同一 v1 快照不论对象键顺序、MySQL JSON 展示空白或等价十进制输入如何变化，规范化 hash 均一致。
  2. 课程题库和课程测验写入 MySQL 后重新读取并开始作答成功，不误报“考核执行快照已损坏”。
  3. 独立考试/练习、培训考试/练习均只通过统一 Codec 生成和读取快照，不存在来源特判 hash。
  4. 修改快照任一来源、资源或规则字段但不更新 hash 时返回 `7063`，且不新增 Candidate、Attempt 或答案。
  5. 查询投影与快照不一致时返回 `7063`；正常查询仍可使用既有来源、资源和状态索引。
  6. 已有关联业务事实的执行对象不能原地刷新；未使用执行对象按确认规则重建。
  7. migration 与 Admin-init 最终结构一致，旧七个快照字段不存在；非空旧执行库不会被 migration 静默清理。
  8. HTTP 请求、成功响应、权限、课程完成、考试成绩和培训结果语义不变；三份 OpenAPI 的 `7063` 描述与实现一致。
- 测试方式：目标零 DB 单元/服务/Controller 测试；`AssessmentExecution` 及六类来源的 MySQL IT；Admin-init 空库和 Flyway
  migration IT；受影响 Admin 模块定向测试与编译；OpenAPI JSON 解析；
  `openspec validate unify-assessment-execution-snapshot --strict`；`git diff --check`。

## 实施暂停条件

出现以下任一情况立即暂停并标记“需人工决策”：

1. 需要保留或转换任何旧 AssessmentExecution、Candidate、Attempt、答案、批阅、课程结果或培训结果。
2. 需要改变现有课程完成、考试成绩发布、练习计分、培训投影或权限语义。
3. 查询性能要求必须为快照内部新增索引或额外投影字段。
4. migration 与 Admin-init 无法在不静默删除业务数据的前提下形成一致最终 schema。
