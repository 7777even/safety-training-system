# Tasks：按分层职责收口培训接口

> 状态：已完成。Owner：lims。

## 1. 完成标准口径保存与类型尺子校验

- [x] 1.1 完成标准口径保存与类型尺子校验

- **状态**：已完成
- **Owner**：lims
- **输入**：design D4、D7；`TrainingStandardServiceImpl` 规则保存/发布。
- **输出**：`TOTAL_HOURS` 不得低于类型已配置正数学时的最小值（8017）；`ONLINE_RATIO` 不得宽于类型上限（8018）；指定资源规则仍只写规则表；同步
  OpenAPI 错误码与字段说明。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：既有标准规则替换与发布路径，不新增带入接口。
- **数据影响**：无表结构变更。
- **验收标准**：
  1. 标准总学时低于类型较小基线时 400/8017，规则不落库。
  2. 标准线上占比宽于类型上限时 400/8018。
  3. 保存 COURSE 规则不创建模板或计划阶段。
- **测试方式**：Red → Green 扩展标准 Service 零 DB。

## 2. 完成模板课表保存发布且不要求执行配置

- [x] 2.1 完成模板课表保存发布且不要求执行配置

- **状态**：已完成
- **Owner**：lims
- **输入**：design D5；模板阶段替换与发布。
- **输出**：无执行配置的模板可保存并发布；无阶段或无内容发布返回 8042；契约标明 executionConfig 可选。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：既有模板 stages/publish。
- **数据影响**：无。不删除 execution_config_json 列。
- **验收标准**：
  1. 仅有课程引用、executionConfig 为空的模板可发布。
  2. 空课表模板发布 400/8042。
  3. 不得因缺线下执行配置返回 8117。
- **测试方式**：Red → Green 扩展模板 Service 零 DB。

## 3. 完成从模板创建计划与草稿覆盖复制

- [x] 3.1 完成从模板创建计划与草稿覆盖复制

- **状态**：已完成
- **Owner**：lims
- **输入**：design D2、D3；`TrainingPlanServiceImpl.create`。
- **输出**：创建带合格 `sourceTemplateId` 时事务内复制课表；新增 `POST /plans/{id}/apply-template`；不合格模板 8157；非草稿
  8158；PUT 来源 ID 不复制；同步 OpenAPI。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：新 apply-template；创建接口语义增强。
- **数据影响**：无新表。
- **验收标准**：
  1. 同类型已发布模板创建计划后详情含复制的阶段内容。
  2. 类型不一致创建失败且无计划行。
  3. 草稿 apply-template 覆盖课表；已发布计划 409/8158。
  4. 只更新 sourceTemplateId 不改变已有阶段。
- **测试方式**：Red → Green 扩展计划 Service 零 DB；必要时补 Controller 透传。

## 4. 完成计划发布对标准指定资源的预检

- [x] 4.1 完成计划发布对标准指定资源的预检

- **状态**：已完成
- **Owner**：lims
- **输入**：design D6；`TrainingPlanPublishValidator`。
- **输出**：绑定标准时缺指定课/考/线下则预检 issues 含 8156 且发布失败；无标准不产生 8156；同步 OpenAPI。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：既有 publish-precheck 与 publish。
- **数据影响**：无。
- **验收标准**：
  1. 缺指定课程时预检 ready=false、code=8156，发布后仍为草稿。
  2. 内容已覆盖指定资源时不因 8156 失败。
  3. 未绑标准的计划不出现 8156。
- **测试方式**：Red → Green 扩展 `TrainingPlanPublishValidatorTest`。

## 完成门槛

1. 规格符合性：无类型/标准带入接口；唯一复制为创建与 apply-template；预检卡指定资源；模板不强制执行配置。
2. 代码质量：无前端、无表结构、无新权限码。
3. 契约：`docs/api/training.openapi.json` 四条铁律。
