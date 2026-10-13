# Spec Delta 摘要：按分层职责收口培训接口

完整需求在 `specs/training-layer-responsibility/spec.md`、`specs/training-api-contract/spec.md`、
`specs/training-execution-loop/spec.md`。

## 变更类型

- 新增能力：`training-layer-responsibility`
- 修改能力：`training-api-contract`、`training-execution-loop`

## 需求增量

1. 系统 MUST NOT 提供类型→标准或标准→模板的课表带入接口。
2. 系统 MUST 在创建计划或 apply-template 时服务端复制已发布同类型模板课表。
3. 系统 MUST 在计划发布预检中检查标准指定课程/考试/线下。
4. 系统 MUST NOT 把模板执行配置作为模板发布条件。
5. 标准总学时与线上占比 MUST 不得宽于类型尺子。
