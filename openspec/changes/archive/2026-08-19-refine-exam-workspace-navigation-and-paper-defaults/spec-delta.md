# Spec Delta：考试工作台步骤控制与试卷默认配置

## ADDED Requirements

### Requirement：新建考试步骤受限

系统 SHALL 在新建考试尚未保存时禁用人员配置和发布考试步骤，并在查询参数请求不可访问步骤时展示考试信息步骤。考试情况仍仅对已发布考试展示。

#### Scenario：新建态访问工作台

- **WHEN** 管理员进入新增考试工作台
- **THEN** 仅“设置考试信息”可操作，后续步骤处于禁用状态

#### Scenario：新建态带后续步骤查询参数

- **WHEN** 管理员以 `stage=assignment` 或 `stage=publish` 进入新增考试工作台
- **THEN** 系统展示考试信息步骤，不渲染空白后续面板

### Requirement：试卷规则默认带入

系统 SHALL 在新建考试选择已发布试卷版本后，以该版本详情中的时长、及格比例、题目顺序和多选题计分方式作为考试表单默认值。

#### Scenario：选择已发布试卷版本

- **WHEN** 管理员选择一个已发布试卷版本且版本详情读取成功
- **THEN** 系统将版本的 `durationSeconds`、`passRatio`、`questionOrderMode`、`multiSelectScoring` 写入考试表单，并保留既有分类和封面默认带入

#### Scenario：版本详情读取失败

- **WHEN** 管理员选择试卷版本但版本详情读取失败
- **THEN** 系统提示加载失败且不覆盖当前四项考试规则
