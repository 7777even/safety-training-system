## ADDED Requirements

### Requirement: 本人学习详情按既有课程下载模式表达附件状态

系统 MUST 使用课程定义既有的 `NONE`、`ALL`、`PARTIAL` 枚举判断冻结附件是否允许下载，不得引入课程创建与更新接口不接受的下载模式。

#### Scenario: 全部允许模式返回可下载附件

- **GIVEN** 开课冻结课程的 `sourceDownloadMode=ALL`
- **AND** 附件属于冻结 `downloadAssets` 清单且附件、当前素材、源文件和存储状态均有效
- **WHEN** 报名本人查询课程学习详情
- **THEN** 系统返回 `downloadable=true` 和课程学习域受控 `downloadUrl`
- **AND** 不返回 `SOURCE_DOWNLOAD_DISABLED`

#### Scenario: 部分允许模式只返回冻结清单附件

- **GIVEN** 开课冻结课程的 `sourceDownloadMode=PARTIAL`
- **AND** 某附件属于冻结 `downloadAssets` 清单且附件、当前素材、源文件和存储状态均有效
- **WHEN** 报名本人查询课程学习详情
- **THEN** 该附件返回 `downloadable=true` 和课程学习域受控 `downloadUrl`
- **AND** 未进入冻结 `downloadAssets` 清单的素材不作为课程附件返回

#### Scenario: 禁止或未知模式失败关闭

- **GIVEN** 开课冻结课程的 `sourceDownloadMode=NONE`、空值或其他未知值
- **WHEN** 报名本人查询课程学习详情
- **THEN** 冻结附件返回 `downloadable=false`、`downloadUrl=null` 和 `SOURCE_DOWNLOAD_DISABLED`

### Requirement: 学员附件下载使用一致的课程模式门禁

系统 MUST 在实际输出文件前使用与学习详情相同的课程下载模式判断，并继续执行报名本人、冻结归属及文件有效性校验。

#### Scenario: 全部允许或部分允许模式下载冻结附件

- **GIVEN** 报名本人和报名/开课状态允许下载
- **AND** 冻结课程的 `sourceDownloadMode` 是 `ALL` 或 `PARTIAL`
- **AND** 路径素材属于启用的冻结附件且当前素材和源文件有效
- **WHEN** 用户请求课程学习域附件下载接口
- **THEN** 系统返回源文件流

#### Scenario: 禁止或未知模式拒绝实际下载

- **GIVEN** 冻结课程的 `sourceDownloadMode` 是 `NONE`、空值或其他未知值
- **WHEN** 报名本人请求附件下载
- **THEN** 系统返回业务码 5087
- **AND** 不读取素材、文件或对象存储
