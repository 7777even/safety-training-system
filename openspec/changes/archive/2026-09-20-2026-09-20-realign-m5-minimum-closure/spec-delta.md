# Spec Delta：M5 中山 50 最小闭环

## ADDED Requirements

### Requirement：M5 SHALL 只暴露中山 50 功能项要求的产品能力

#### Scenario：无功能项依据的既有能力

- **GIVEN** 既有 M5 提供教师工作台、通用状态统计、可配置预警和人工处置流程
- **WHEN** 本 Change 实施
- **THEN** 系统 SHALL 删除对应公开代码、API、菜单、权限和 OpenAPI
- **AND** SHALL 保留既有数据库结构与历史数据
- **AND** SHALL 使用标准 COMMENT 标注原用途和隐藏状态。

### Requirement：监管工作台 SHALL 展示功能项规定的园区数据

#### Scenario：监管人员查看园区概览

- **GIVEN** 当前登录用户属于监管租户并具有监管工作台权限
- **WHEN** 打开监管工作台
- **THEN** 系统 SHALL 展示企业/培训机构、培训计划、培训类型、特种作业、参训人员和待查看预警概览
- **AND** SHALL 提供证书、课程制作和数据中心快捷入口
- **AND** 快捷入口 SHALL NOT 绕过目标模块权限。

### Requirement：学习统计 SHALL 使用统一且可下钻的最小口径

#### Scenario：园区与机构统计一致

- **GIVEN** 园区下存在多个机构及真实课程报名
- **WHEN** 监管人员分别查看园区汇总和机构明细
- **THEN** 员工账号数、上课次数、上课人数、参与课程数、人均上课次数和参与率 SHALL 使用 Design 第 5.2 节口径
- **AND** 园区汇总 SHALL 等于机构明细的同口径聚合。

### Requirement：培训计划学时不足 SHALL 自动产生预警

#### Scenario：保存学时不足计划

- **GIVEN** 企业培训计划选择了培训类型和初培或复审性质
- **WHEN** 保存后的安排总学时低于该培训类型对应要求学时
- **THEN** 系统 SHALL 自动生成或刷新一条待查看预警
- **AND** SHALL 记录计划学时、要求学时和差额
- **AND** 重复保存 SHALL NOT 生成重复预警。

#### Scenario：计划补足学时

- **GIVEN** 当前计划存在有效学时预警
- **WHEN** 企业将计划学时修正为满足培训类型要求
- **THEN** 系统 SHALL 自动结束当前预警
- **AND** 默认待查看列表 SHALL 不再显示该预警。

### Requirement：监管预警 SHALL 只提供查看闭环

#### Scenario：查看待查看预警

- **GIVEN** 监管工作台存在待查看预警
- **WHEN** 监管人员打开预警详情
- **THEN** 系统 SHALL 标记预警为已查看
- **AND** 展示企业、培训计划、培训类型、培训性质、计划学时、要求学时和差额
- **AND** 支持跳转到来源培训计划。

### Requirement：监管统计与预警 SHALL 提供真实导出

#### Scenario：按当前筛选导出

- **GIVEN** 监管人员具有对应数据查看和导出权限
- **WHEN** 导出机构统计、学习统计或预警列表
- **THEN** 系统 SHALL 返回可打开的 UTF-8 CSV 文件
- **AND** 文件内容 SHALL 与当前筛选及页面口径一致
- **AND** SHALL NOT 使用固定文件 ID 或空任务记录代替文件。

### Requirement：企业管理员工作台 SHALL 提供权限化入口与可解释概览

#### Scenario：企业管理员打开工作台

- **GIVEN** 企业管理员具有部分业务模块权限
- **WHEN** 打开企业工作台
- **THEN** 系统 SHALL 只展示已授权模块入口
- **AND** 展示本企业真实课程、考试、培训、参训结果和分类待办概览
- **AND** 点击概览 SHALL 进入对应业务列表。

### Requirement：M5 完成状态 SHALL 以真实纵向闭环为准

#### Scenario：仅后端或占位实现

- **GIVEN** M5 仍有链路只有后端、使用 Mock、占位文件或缺少跨租户登录态验证
- **WHEN** 评审 M5 状态
- **THEN** M5 SHALL NOT 标记为已完成。

## MODIFIED Requirements

### Requirement：机构类型 SHALL 区分企业和培训机构

#### Scenario：创建培训机构

- **WHEN** 管理员按培训机构分类创建受监管机构
- **THEN** 系统 SHALL 保存明确的 `TRAINING_INSTITUTION` 类型
- **AND** 监管统计 SHALL 将其与企业分别统计
- **AND** SHALL NOT 通过名称或描述推断机构类型。

## REMOVED Requirements

以下旧 M5 产品要求被移除，但数据库结构和历史数据保留：

- 教师工作台和 M5 学员工作台摘要；
- 课程、考试、培训、综合结果通用状态统计和通用 ID 下钻；
- 通用导出任务中心；
- 预警规则/条件 CRUD、自定义指标/比较符/阈值和线上占比预警；
- 手工执行预警检测；
- 预警分派、评论、人工关闭、忽略和重开。

