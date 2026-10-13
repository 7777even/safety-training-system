# Spec Delta：M4 中山 50 最小闭环

## ADDED Requirements

### Requirement：M4 SHALL 只暴露中山 50 功能项要求的产品能力

#### Scenario：无功能项依据的既有能力

- **GIVEN** 既有代码提供人工计算批次、档案版本历史、证书撤销或重发等能力
- **WHEN** 本 Change 实施
- **THEN** 系统 SHALL 删除对应公开代码、API、菜单、权限和 OpenAPI
- **AND** SHALL 保留既有数据库结构和历史数据
- **AND** SHALL 使用标准 COMMENT 标注原用途、隐藏状态和重新启用条件。

### Requirement：问卷 SHALL 形成设计到扫码统计的最小闭环

#### Scenario：问卷投放与填写

- **GIVEN** 管理员已完成题目和答案设计并通过 PC/手机预览
- **WHEN** 管理员发起投放并由用户扫码提交
- **THEN** 系统 SHALL 保存一份有效答卷并阻止同一用户重复提交
- **AND** 管理员 SHALL 查看基础结果统计
- **AND** 填写人结果可见性 SHALL 服从管理员设置。

### Requirement：综合分 SHALL 根据考试与奖惩自动计算

#### Scenario：来源事实变化

- **GIVEN** 培训已绑定启用的综合评分表
- **WHEN** 参训人的考试正式成绩或奖惩记录变化
- **THEN** 系统 SHALL 自动刷新当前综合分
- **AND** SHALL NOT 要求管理员创建、确认或替代计算批次。

#### Scenario：查看已结束培训结果

- **GIVEN** 培训已经结束
- **WHEN** 管理员查看培训结果
- **THEN** 系统 SHALL 展示计划整体信息、学员成绩与详情、奖惩和基础统计。

### Requirement：专家查课 SHALL 形成表单引用与意见记录闭环

#### Scenario：专家提交查课意见

- **GIVEN** 培训已引用一个专家查课表
- **WHEN** 专家提交查课意见
- **THEN** 系统 SHALL 保存提交人、提交时间和完整意见
- **AND** 管理员 SHALL 能在对应培训中查看。

### Requirement：档案 SHALL 提供个人、企业和一期三个视角

#### Scenario：结束培训归档

- **GIVEN** 培训状态为 `ENDED` 且计划、人员、签到、成绩、评价、奖惩、影像和附件事实完整
- **WHEN** 管理员更新档案
- **THEN** 系统 SHALL 在个人、企业和一期三个视角展示一致事实
- **AND** SHALL 生成真实可下载档案文件。

#### Scenario：未结束培训归档

- **GIVEN** 培训状态不是 `ENDED`
- **WHEN** 请求形成或更新正式档案
- **THEN** 系统 SHALL 拒绝请求。

### Requirement：证书 SHALL 按来源合格结果自动生成真实文件

#### Scenario：学员首次达标

- **GIVEN** 课程、理论考试或培训已配置启用的证书模板
- **WHEN** 学员第一次达到来源对象的合格标准
- **THEN** 系统 SHALL 自动且幂等地签发证书
- **AND** SHALL 生成可预览、可下载的真实图片或 PDF 文件。

#### Scenario：学员查看证书

- **GIVEN** 学员已获得证书
- **WHEN** 学员进入个人中心“我的证书”
- **THEN** 系统 SHALL 只返回该登录学员本人的证书
- **AND** SHALL 支持在线查看和下载。

## MODIFIED Requirements

### Requirement：M4 完成状态 SHALL 以真实纵向闭环为准

M4 不再允许按“纯后端完成、前端另开”关闭。问卷、综合结果、专家查课、三级档案和证书五条链路均完成真实页面、真实
API、真实数据、权限和端到端验收后，才可标记为已完成。

## REMOVED Requirements

以下旧 M4 产品要求被移除，但数据库结构保留：

- 综合成绩人工计算批次、正式确认、替代重算和批次历史；
- 档案版本历史、快照哈希查看和异步导出任务中心；
- 证书撤销、人工重发、到期管理和客户端任意手工签发；
- 问卷独立版本管理、匿名高级规则和复杂目标绑定；
- 无功能项依据的通用人员评价、课程质量评价产品入口。
