# Tasks：修复考试作答查询机构表关联

> 状态：已完成（2026-07-31）。

## Task 1：修复考试作答与教师批阅查询

- 状态：已完成
- Owner：lims
- 输入：`ExamAttemptQueryMapper.xml`、`sts_org_organization` schema、既有 `ExamAttemptQueryMapperIT`
- 输出：两条可在真实 MySQL 执行的考试查询
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：无契约变化
- 数据影响：无 migration
- 验收标准：
  - [x] 教师批阅任务查询使用真实机构表
  - [x] 考试作答记录查询使用真实机构表
  - [x] 查询不引用 Audit class B/C 表不存在的 `deleted` 列
  - [x] 查询字段、筛选、数据范围与排序语义不变
  - [x] `ExamAttemptQueryMapperIT` 通过
- 测试方式：
  - TDD：先保留已观察到的真库失败证据
  -
  `mvn -pl sts-service-admin -am -Pintegration-test -Dtest=ExamAttemptQueryMapperIT -Dsurefire.failIfNoSpecifiedTests=false test`
  - 受影响零 DB 测试
  - `openspec validate 2026-07-31-fix-exam-attempt-organization-join --strict`
  - `git diff --check`

## 验证证据

- TDD Red：真实 MySQL 首次执行报 `Table 'sts_organization' doesn't exist`；修正表名后继续暴露
  `Unknown column 'er.deleted'`。
- Green：`ExamAttemptQueryServiceImplTest` 3/3 通过。
- Green：`ExamAttemptQueryMapperIT` 2/2 通过。
- 规格符合性 Review：通过；只修正已确认的真实表名与审计列引用，查询字段、筛选、数据范围和排序未改变。
- 代码质量 Review：通过；未修改 API、权限、数据库结构或前端代码，无无关重构。
