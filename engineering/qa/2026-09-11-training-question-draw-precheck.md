# QA：培训题库公共抽题与发布预检

## 范围

OpenSpec Change：`redesign-training-question-bank-practice-closure` Task 1.1。

本轮仅交付公共候选池、公共抽题引擎、真实开考适配、规则级发布预检和 Training OpenAPI。无 DDL、未改前端；发布题池冻结与不评分提交结果分别留在已确认
Change 的 Task 2、Task 3。

## TDD 证据

先观察到三组目标失败：

1. 公共抽题引擎测试因 `QuestionDrawEngine` 尚不存在而编译失败；
2. 候选池测试因查询行模型和候选池服务尚不存在而编译失败；
3. 发布预检测试因新错误码、定位字段和候选池依赖尚不存在而编译失败。

最小实现完成后，在 `backend/` 执行定向零 DB 测试：

```text
mvn -pl sts-service-admin \
  -Dtest=com.sts.question.draw.QuestionDrawEngineTest,com.sts.question.service.QuestionCandidatePoolServiceTest,com.sts.exam.service.QuestionBankDrawEngineTest,com.sts.exam.service.ExamAttemptServiceImplTest,com.sts.training.service.TrainingPlanPublishValidatorTest,com.sts.training.service.TrainingPlanServiceImplTest,com.sts.training.controller.TrainingPlanControllerTest test
```

结果：116 项通过，0 失败。

## MySQL IT

执行：

```text
mvn -pl sts-service-admin -Pintegration-test \
  -Dtest=com.sts.question.mapper.QuestionDrawCandidatePoolMapperIT test
```

结果：1 项通过，验证启用/停用、归档、最新已发布版本、草稿排除、知识点装配和正确答案数量 SQL。测试使用 Testcontainers MySQL，不依赖
VPN 数据库。

## 覆盖场景

1. 题库含简答题而规则只抽单选时不再返回 8127。
2. 规则直接抽简答题且候选充足时允许发布，判定模式为 `RECORD_ONLY`。
3. 单规则按难度、知识点过滤后不足时返回 8166 和完整定位字段。
4. 同题型多规则共享候选时按题目全局不重复计算，不再逐规则独立误判。
5. 宽规则与窄规则重叠但存在合法分配时，多个随机种子均可成功。
6. 已发布题目的评分模式或答案资产无法形成合法判定策略时，仅命中的规则返回 8127。
7. 相同候选池、规则和种子产生相同题序；既有开考适配保留分值兼容行为。

## 契约与静态验证

- `docs/api/training.openapi.json` JSON 解析通过，已同步 ESSAY、历史分数字段语义、8166 和全部规则定位字段及示例。
- `openspec validate redesign-training-question-bank-practice-closure --strict` 通过。
- `git diff --check` 通过。

## 双轴 Review

1. 规格符合性：Task 1 通过。预检、正式发布重验和当前实时开考已复用同一候选池与公共最大匹配；未实现 Task 2/3 的冻结池和结果语义。
2. 代码质量：通过。公共引擎不依赖 Spring、Mapper、培训 DTO、分值和业务错误码；候选 SQL 有真实 MySQL
   IT；既有考试适配器只转换机械抽题结果，保留原业务错误码和分值兼容层。

## 后续验收

Task 2 完成前，已发布计划仍未冻结题目版本池；Task 3 完成前，既有 Attempt 结果模型仍是历史计分模型。因此本记录只关闭 Task
1，不代表整个题库练习业务闭环已完成。
