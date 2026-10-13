# 工作台 Panel 收敛规格增量

## Requirement: 工作台阶段内容以 Panel 承载

系统 SHALL 在考试和试卷工作台的 `admin-detail-page__surface` 中，以对应的阶段 Panel 渲染阶段内容；工作台根组件仅保留跨阶段编排职责。

### Scenario: 考试工作台切换阶段

- **WHEN** 管理员切换考试信息、人员配置或发布核查阶段
- **THEN** 系统在统一详情页内容区域加载对应 Panel，功能和请求语义保持不变

### Scenario: 试卷工作台切换阶段

- **WHEN** 管理员切换基础信息、组卷配置或发布确认阶段
- **THEN** 系统在统一详情页内容区域加载对应 Panel，功能和请求语义保持不变
