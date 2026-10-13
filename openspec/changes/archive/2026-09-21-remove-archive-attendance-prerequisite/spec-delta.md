# 正式培训档案最小前置条件增量规格

## MODIFIED Requirements

### Requirement：正式档案基于已结束且结果完整的培训事实

系统 SHALL 仅在培训计划为 `ENDED` 且参训结果完整时生成正式档案和真实文件。系统 SHALL 使用既有计划、机构、参训人、结果、评价、奖惩、证书和附件事实形成快照；系统 SHALL NOT 要求签到或出勤事实作为归档前置。

#### Scenario：完整结果的已结束培训归档

- **WHEN** 培训计划为 `ENDED` 且所有参训人结果不是 `INCOMPLETE`
- **THEN** 系统不返回 `ATTENDANCE` 缺失，授权用户可以生成并下载真实档案文件

#### Scenario：归档版本回读生成文件

- **WHEN** 授权用户已为结果完整的已结束培训生成正式档案
- **THEN** 系统将生成文件的受控 `fileInfoId` 持久化在当前归档版本，查询和下载使用该 ID 打开原始文件

#### Scenario：结果不完整时拒绝归档

- **WHEN** 已结束培训存在 `INCOMPLETE` 参训结果
- **THEN** 系统返回 `missingItems` 中的 `RESULTS`，归档和文件下载返回 `409/9204`，且不生成占位文件

#### Scenario：非结束培训拒绝归档

- **WHEN** 培训计划不是 `ENDED`
- **THEN** 系统保持现有状态冲突拒绝，不生成档案或文件
