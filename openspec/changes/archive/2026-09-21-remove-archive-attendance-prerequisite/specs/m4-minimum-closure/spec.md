## MODIFIED Requirements

### Requirement: 档案必须提供个人、企业和一期三个视角

系统 MUST 只从结果完整的已结束培训形成正式档案，并在个人、企业和一期三个视角展示一致的计划、人员、成绩、评价、奖惩、影像、附件和统计事实。系统 SHALL NOT 要求签到、出勤、扫码、定位或现场证据；存在结果不完整参训人时 MUST 拒绝生成正式可下载档案，不得写空快照。系统 MUST 将真实生成文件的受控 `fileInfoId` 持久化在当前归档版本，并在查询与下载时回读该文件。

#### Scenario: 结束培训归档

- **GIVEN** 培训状态为 `ENDED` 且参训结果完整
- **WHEN** 管理员更新档案
- **THEN** 系统在个人、企业和一期三个视角展示一致事实
- **AND** 生成真实可下载档案文件并持久化当前版本文件 ID

#### Scenario: 未结束培训归档

- **GIVEN** 培训状态不是 `ENDED`
- **WHEN** 请求形成或更新正式档案
- **THEN** 系统拒绝请求

#### Scenario: 结果不完整

- **GIVEN** 培训状态为 `ENDED` 且存在 `INCOMPLETE` 参训结果
- **WHEN** 请求形成或更新正式档案
- **THEN** 系统拒绝请求且不写空快照
- **AND** 三个视角查询返回 `missingItems` 中的 `RESULTS`

### Requirement: lims 协作方以后端可交互为 M4 归档标准

lims 协作方 MUST 以问卷、综合结果、专家查课、三级档案查询与真实文件端点、证书的后端可交互 API 与 OpenAPI 为 Change 归档标准。系统 MUST NOT 用空档案快照或占位证书文件伪装完成。前端页面验收不在 lims 范围。

#### Scenario: 后端可交互后归档

- **GIVEN** 问卷、自动综合分、专家查课、档案查询和文件端点、证书后端 API 可交互，且 OpenAPI 已对齐
- **WHEN** lims 确认归档本 Change
- **THEN** 允许按后端口径归档
- **AND** 不把空档案快照或占位文件当作正式档案完成
