# QA：考试作答查询真实库兼容修复

## 范围

OpenSpec Change `2026-07-31-fix-exam-attempt-organization-join` Task 1。仅修复教师批阅任务与考试作答分页的聚合查询 SQL。

## 缺陷与修复

- 机构关联误用了不存在的 `sts_organization`，改为 schema 真相源 `sts_org_organization`。
- 查询对 Audit class B/C 表误加了 `deleted` 条件；移除 `sts_exam_reviewer`、`sts_exam_attempt_question` 和
  `sts_exam_review_record` 不存在的逻辑删除列引用。
- 保留考试、作答、候选人、答案和机构等实际具有逻辑删除列的过滤。

## 验证结果

- TDD Red：真实 MySQL 依次暴露机构表不存在和 `er.deleted` 列不存在。
- 零 DB：`ExamAttemptQueryServiceImplTest` 3/3 通过。
- MySQL IT：`ExamAttemptQueryMapperIT` 2/2 通过。
- OpenSpec strict 与 `git diff --check` 通过。

## Review

- 规格符合性：通过。两条查询均使用真实 schema，未改变对外 API、权限或查询业务语义。
- 代码质量：通过。修复范围集中在目标 Mapper，无 migration、依赖或无关重构。
