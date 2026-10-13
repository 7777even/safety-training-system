# QA：按分层职责收口培训接口

## 范围

OpenSpec Change：`align-training-layer-apis`。

覆盖培训标准口径相对类型尺子、模板发布不强制执行配置、创建/apply-template 复制课表、计划发布预检指定资源，以及
`docs/api/training.openapi.json`。未改数据库结构、权限码或前端。

## 自动化验证

- 后端零 DB：
  `mvn -pl sts-service-admin -am '-Dtest=TrainingStandardServiceImplTest,TrainingTemplateServiceImplTest,TrainingPlanServiceImplTest,TrainingPlanControllerTest,TrainingPlanPublishValidatorTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`
  → 70 项通过，0 失败、0 错误、0 跳过。
- `docs/api/training.openapi.json` JSON 解析通过。
- `git diff --check` 通过。

## 覆盖场景

1. 标准总学时低于类型已配置正数学时最小值时返回 `400/8017`，规则不落库；线上占比宽于类型上限返回 `400/8018`。
2. 指定 COURSE 规则只写规则表，不生成模板或计划阶段。
3. 仅有课程引用且 `executionConfig` 为空的模板可发布；无阶段或无内容发布返回 `400/8042`。
4. 创建计划带同类型已发布模板时复制阶段内容；类型不一致返回 `400/8157` 且不插入计划。
5. 草稿 `POST /training/plans/{id}/apply-template` 覆盖课表；已发布计划返回 `409/8158`；`PUT` 只改 `sourceTemplateId` 不复制。
6. 绑定标准缺指定课程时预检 `code=8156`；内容已覆盖指定线下资源或未绑标准时不产生 8156。

## MySQL IT

未运行。本 Change 不涉及 Entity、Mapper、自定义 SQL、schema、索引或持久化事务语义；零 DB 已覆盖复制与预检分支。IT 配置已同步
`TrainingPlanServiceImpl` 构造参数，便于后续混合计划纵向 IT 编译。

## 真实登录态待验收

需要在非生产环境由前端按 OpenAPI 对接后验证：

1. 管理端从已发布模板创建计划后，详情立即含复制的阶段内容。
2. 草稿再次 apply-template 覆盖已改课表；已发布计划不能复制。
3. 标准指定课程未编入计划时预检与发布失败，补齐后可通过。
