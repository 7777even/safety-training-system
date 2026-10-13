# Spec：素材转码的租户归属与播放信息

## ADDED Requirements

### Requirement：转码执行必须在明确的租户上下文内进行

转码任务由调度线程执行，不经过 HTTP 请求，因此 MUST 在读取到任务实体后显式声明该任务的租户，
随后的全部业务读写 MUST 在该租户上下文内完成。产生的派生文件与文件记录 MUST 归属任务所属租户。

调度器抢占待处理任务的查询 MAY 跨租户执行，但 MUST NOT 依赖「上下文缺失时插件放行」这一兜底行为
来完成任何业务数据的写入。

#### Scenario：无请求上下文时转码成功落库

- **GIVEN** 一个属于租户 A 的视频转码任务处于待处理状态
- **AND** 执行线程没有任何租户上下文
- **WHEN** 调度器处理该任务且转码执行成功
- **THEN** 派生文件记录与文件信息记录均写入成功
- **AND** 两者的 `organization_id` 等于租户 A
- **AND** 素材的处理状态为 `SUCCEEDED`

#### Scenario：转码产物不跨租户可见

- **GIVEN** 租户 A 与租户 B 各有一个已成功转码的视频
- **WHEN** 租户 B 的用户查询派生文件
- **THEN** 只返回租户 B 自己的派生文件
- **AND** 租户 A 的派生文件不出现在结果中

#### Scenario：处理结束后不残留上下文

- **GIVEN** 调度线程刚处理完一个属于租户 A 的任务
- **WHEN** 同一线程被复用处理属于租户 B 的任务
- **THEN** 该任务在租户 B 的上下文内执行
- **AND** MUST NOT 沿用租户 A 的上下文

---

### Requirement：播放信息的 format 取值限于契约枚举

`GET /api/asset/assets/{assetId}/play-info` 返回的 `format` MUST 取自
`hls`、`mp3`、`pdf`、`xls`、`xlsx`、`original` 之一，MUST NOT 返回任意源文件后缀。

`original` 的含义 MUST 明确为「该素材没有可用派生产物，前端按原文件直链处理」。

#### Scenario：视频已转码

- **GIVEN** 一个视频素材存在启用中的 HLS 派生文件
- **WHEN** 查询播放信息
- **THEN** `format` 为 `hls`
- **AND** `url` 指向受控的 HLS 索引地址

#### Scenario：Excel 直接预览

- **GIVEN** 一个后缀为 `xlsx` 的文档素材没有派生文件
- **WHEN** 查询播放信息
- **THEN** `format` 为 `xlsx`
- **AND** 前端据此走表格直显

#### Scenario：无派生产物的其它素材

- **GIVEN** 一个后缀为 `mp4` 的视频素材没有任何启用中的派生文件
- **WHEN** 查询播放信息
- **THEN** `format` 为 `original`
- **AND** MUST NOT 返回 `mp4`
