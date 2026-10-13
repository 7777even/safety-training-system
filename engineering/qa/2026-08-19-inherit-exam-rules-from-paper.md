# QA：考试从试卷版本带入作答规则

## 范围

OpenSpec Change `inherit-exam-rules-from-paper` Task 1。创建/草稿更新时从绑定试卷版本带出时长、及格比例、题序和多选计分，允许覆盖；有试卷时及格分按总分×比例计算。无
schema / migration。身份 lims，不含前端。

## 缺陷与修复

- 考试与试卷版本同名规则原先两侧独立必填，绑卷后仍要手填时长/比例/题序/计分。
- 有试卷时请求可同时传互相矛盾的 `passRatio` 与 `passScore`。
- 创建绑卷且未给封面时未带出试卷封面。

## 验证结果

- TDD Red：带出、覆盖、换绑重填、未换绑保留、无试卷 `1001`、版本 `7005`、及格分计算和封面带出先失败。
- Green：`ExamServiceImplTest` 18/18、`ExamAttemptServiceImplTest` 18/18。
- 命令：
  `mvn -pl sts-service-admin -am test -Dtest=ExamServiceImplTest,ExamAttemptServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
  ，36/36 通过。
- OpenAPI JSON 可解析；`git diff --check` 通过。
- 未运行项：无 schema/SQL 变化，未跑 integration-test；未改前端。

## Review

- 规格符合性：通过。绑卷省略则带出、提交则覆盖、换绑重填、未换绑保留、无试卷 1001、缺版本
  7005、有试卷忽略手填及格分、创建带封面且不带分类；开考仍冻考试时长/及格分，总分仍来自试卷。
- 代码质量：通过。逻辑集中在 `ExamServiceImpl`，无无关重构，无前端改动。
