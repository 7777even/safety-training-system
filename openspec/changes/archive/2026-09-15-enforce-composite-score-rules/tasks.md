# Tasks：收紧培训综合成绩配置、计算门禁与来源追溯

> 状态：实施中。Owner：fxw11。最后核验：2026-09-15。

## Task 1：约束综合评价表单为可追溯公式

- 输入：评价表单项目、评分规则、现有评价表单 OpenAPI。
- 输出：`COMPOSITE` 发布校验、固定来源编辑器和契约/测试。
- 前后端影响：后端、PC。
- API 依赖：`evaluation-form.openapi.json` 的发布请求和错误码。
- 数据影响：无结构变更；保留既有项目 `weight` 字段，仅消除其独立编辑语义。
- 验收标准：
  - [X] 课程、考试、线下来源最多各一条 `WEIGHT` 规则，权重闭合为 1。
  - [X] `MANUAL`、`REWARD_PENALTY`、`ADJUSTMENT`、重复来源和无对应项目的规则均被服务端拒绝。
  - [X] PC 不能构造不合规的 `COMPOSITE` 表单，服务端仍是最终边界。
  - [X] PC 综合评价使用课程、考试、线下固定来源卡片配置；必填项显示红色标识，来源缺失、重复、权重为空或未闭合时在当前步骤显示中文错误提示。
  - [X] 定向 Java 单测通过：`EvaluationFormServiceImplTest` 7 项。

## Task 2：收紧计划内容层聚合和表单绑定

- 输入：草稿计划阶段内容、已发布综合评价版本。
- 输出：同类别聚合配置、绑定/发布预检和契约/测试。
- 前后端影响：后端、PC。
- API 依赖：`training.openapi.json` 的内容字段及评价表单绑定接口。
- 数据影响：复用既有 `isScored`、`scoreWeight`、`scoreAggregationMode` 字段，不新增结构。
- 验收标准：
  - [X] PC 可配置算术平均或加权平均；同类内容混用或加权权重不闭合时，草稿保存被阻止。
  - [X] 后端在计划绑定和发布预检时拒绝同类混用、加权权重不闭合及表单来源无计分内容。
  - [X] 题库练习始终不参与综合成绩。

## Task 3：严格计算门禁与逐内容审计

- 输入：已冻结的计划、参训人正式内容成绩和合规表单版本。
- 输出：缺分拒绝、逐内容来源快照/查询和 PC 历史展开。
- 前后端影响：后端、PC。
- API 依赖：`evaluation-result.openapi.json` 新增来源查询及 8806/8807 错误语义。
- 数据影响：向既有 `sts_evaluation_calculation_source` 写入逐内容审计数据；无 migration。
- 验收标准：
  - [X] 表单引用的类别聚合分缺失时不创建批次；真实 0 分允许计算。
  - [X] 逐内容缺分的后端门禁（不能仅依赖类别聚合分）和缺分内容名称返回。
  - [X] 多考试/多线下的类别汇总、表单权重和最终综合分可从历史来源明细复算。
  - [X] 已确认成绩、结束计划和既有历史批次不被改写。
  - [X] 定向 Java 单测通过：`CalculationRunServiceImplTest` 7 项。

## Task 4：验证、Review 与真实环境验收

- 输入：Task 1～3 产物。
- 输出：Red/Green 定向测试、双轴 Review、QA/Retro 和真实环境证据。
- 前后端影响：后端、PC。
- API 依赖：已更新 OpenAPI。
- 数据影响：无额外数据写入。
- 验收标准：
  - [X] 受影响 Java 单测、MySQL IT、PC Node 测试、typecheck、目标 ESLint 与 `git diff --check` 通过。
  - [X] 已完成当前代码范围的 Java 定向单测 63 项、PC 来源 API 定向测试、typecheck 与目标 ESLint。
  - [X] 非生产环境以两项考试、两项线下内容、缺一项分数和真实 0 分四种情形验收。
  - [X] 完成规格符合性与代码质量双轴 Review，并更新 QA、Retro、进度记录。
