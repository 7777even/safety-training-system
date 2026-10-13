# training-material Specification

## Purpose
TBD - created by archiving change 2026-09-24-close-training-material-loop. Update Purpose after archive.

## Requirements

### Requirement: 培训资料挂培训计划并可选关联阶段

系统 MUST 将培训资料挂在培训计划（`trainingPlanId` 必填）下，并 MAY 关联到该计划下的培训阶段（`planStageId` 可空）。系统 MUST NOT 要求资料必须绑定线下培训阶段；线上与线下培训期间产生的影像和附件 MUST 由同一资料能力承载。资料类型 MUST 收敛为 `GENERAL`、`IMAGE`、`VIDEO`、`REWARD_PENALTY`、`EVALUATION`。

#### Scenario: 上传培训过程影像并关联阶段

- **GIVEN** 一个 `RUNNING` 状态的培训计划
- **WHEN** 管理员上传培训现场视频并选择关联到该计划的线下培训阶段
- **THEN** 系统以 `VIDEO` 类型保存该资料
- **AND** 回读结果包含名称、类型、关联阶段与文件信息

#### Scenario: 不关联阶段的一般资料

- **GIVEN** 一个 `RUNNING` 状态的培训计划
- **WHEN** 管理员上传与具体阶段无关的制度文件
- **THEN** 系统允许 `planStageId` 为空并真实保存

### Requirement: 培训资料文件必须受控读取

系统 MUST 提供按资料记录读取原始文件的受控端点。记录不存在、文件不存在或不属于当前机构时，系统 MUST NOT 返回文件内容，且 MUST NOT 提供按任意文件 ID 下载的入口。培训计划结束后资料 MUST 仍可只读预览。

#### Scenario: 跨机构读取被拒绝

- **GIVEN** 一条属于其他机构的培训资料记录
- **WHEN** 当前机构管理员请求该记录的文件
- **THEN** 系统拒绝请求且不返回任何文件内容

#### Scenario: 无文件或记录不存在

- **GIVEN** 一条资料记录不存在，或其关联文件不可读
- **WHEN** 请求该记录的文件
- **THEN** 系统返回 404 且不泄露其他文件

### Requirement: 培训资料写入必须校验归属与状态

系统 MUST 在新增、修改、删除资料前校验计划存在且属于当前机构、状态为 `RUNNING`。系统 MUST 拒绝 `planStageId` 不属于该计划的请求，以及引用当前机构不可见文件的请求。计划为 `ENDED` 或已冻结时，资料 MUST 只读。

#### Scenario: 冻结后写入被拒绝

- **GIVEN** 一个 `ENDED` 状态的培训计划
- **WHEN** 管理员调用资料新增、修改或删除
- **THEN** 系统拒绝请求且既有资料保持不变

#### Scenario: 阶段归属错误被拒绝

- **GIVEN** 一个 `RUNNING` 状态的培训计划
- **WHEN** 提交关联到其他计划阶段的资料
- **THEN** 系统拒绝请求且不写入资料

### Requirement: 培训资料必须在工作台可维护

培训计划工作台 MUST 在「过程与结果」展示培训资料列表，并在权限与计划状态允许时提供新增、编辑、删除与预览。表单 MUST 从本计划阶段选择关联对象，MUST NOT 要求手输全局主键；文件 MUST 经统一上传能力取得文件 ID。已结束计划 MUST 只读展示。

#### Scenario: 维护资料并回读

- **GIVEN** 一个 `RUNNING` 计划、具备 `training:attachment:add` 权限的管理员
- **WHEN** 管理员新增、编辑或删除一条培训资料
- **THEN** 界面从服务端重新读取资料列表
- **AND** 有文件的资料可预览或下载原始文件
