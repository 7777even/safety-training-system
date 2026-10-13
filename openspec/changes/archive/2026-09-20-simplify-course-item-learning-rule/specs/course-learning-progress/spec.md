# course-learning-progress Specification Delta

## MODIFIED Requirements

### Requirement: ASSET 内容项以唯一时长字段判定完成

ASSET 内容项 MUST 配置大于 0 的 `minimumStudySeconds`。系统 MUST 在累计有效学习时长达到该值时将内容项置为
COMPLETED。管理端 MUST 将该字段显示为“要求学习时长”，并与内容项一次保存。

#### Scenario: ASSET 时长未满足

- **GIVEN** ASSET 内容项要求学习 60 秒
- **WHEN** 学员累计有效学习 59 秒
- **THEN** 内容项保持 LEARNING

#### Scenario: ASSET 时长满足

- **GIVEN** ASSET 内容项要求学习 60 秒
- **WHEN** 学员累计有效学习达到 60 秒
- **THEN** 内容项转为 COMPLETED

#### Scenario: 无效 ASSET 时长

- **WHEN** 调用方创建、更新或发布要求学习时长小于等于 0 的 ASSET 内容项
- **THEN** 服务端拒绝请求

### Requirement: 非素材内容不使用学习时长

LIVE、QUESTION_BANK、QUIZ 的 `minimumStudySeconds` MUST 为 0，且 MUST 继续由各自业务事实判定完成。

#### Scenario: 非素材内容提交非零时长

- **WHEN** 调用方为非 ASSET 内容项提交非零 `minimumStudySeconds`
- **THEN** 服务端拒绝请求

### Requirement: 历史课程版本门槛保持兼容

新发布快照 MUST 只写 `minimumStudySeconds`。旧快照存在材料规则时，系统 MUST 继续按
“CUSTOM 要求秒数 → 内容项秒数 → DEFAULT 要求秒数”读取完成门槛。

#### Scenario: 旧自定义规则继续生效

- **GIVEN** 已发布旧快照的内容项时长为 30 秒且 CUSTOM 材料规则为 120 秒
- **WHEN** 学员累计有效学习达到 30 秒但未达到 120 秒
- **THEN** 内容项保持 LEARNING

### Requirement: 不暴露未闭环材料控制项

系统 MUST NOT 提供机构默认学习规则或内容项级快进、倍速、水印、辅助音频、离页暂停设置。
课程级倍速和水印设置 MUST 保持既有语义。
`sts_course_setting` MUST 保留历史表和值并标注为暂时隐藏，且 MUST NOT 作为当前页面、API 或业务规则依据。

#### Scenario: 管理员编辑素材内容项

- **WHEN** 管理员打开 ASSET 内容项编辑表单
- **THEN** 只看到一个要求学习时长字段
- **AND** 不存在独立材料规则面板或第二个保存动作

#### Scenario: 检查隐藏的机构课程设置资产

- **WHEN** 执行最新 init SQL 或本次增量 migration
- **THEN** `sts_course_setting` 表及历史值仍然存在
- **AND** 表及配置列 COMMENT 标注为“【暂时隐藏】”
- **AND** 当前系统不存在对应页面或 API
