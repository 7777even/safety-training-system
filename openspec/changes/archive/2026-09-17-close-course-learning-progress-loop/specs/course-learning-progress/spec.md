# course-learning-progress Specification

## Purpose

课程学习会话由客户端按活跃区间自动上报，服务端据此累计有效学习时长、判定内容项完成并重算报名进度。
完成阈值来自内容项「最低学习秒数」与材料规则「要求学习秒数」，两者都必须真实参与判定。

## ADDED Requirements

### Requirement: 有效学习时长按活跃区间累计上报

客户端 MUST 在每一次上报检查点取值之前，把「上次结算点 → 当前时刻」的活跃区间累计进有效学习秒数。
结算 MUST NOT 只由可见性变化、媒体播放事件或内容项切换触发。页面不可见期间，以及可计时媒体处于暂停
状态的区间，MUST NOT 计入有效学习时长。服务端 MUST 以请求中的 `validStudySeconds` 累加
`course_item_progress.study_seconds`，MUST NOT 依据会话时长自行推算。

#### Scenario: 文档类内容项心跳上报

- **GIVEN** 学员停留在一个页面可见、且不产生媒体播放事件的文档类内容项上
- **AND** 自上一个检查点以来学员未切换内容项也未切换标签页
- **WHEN** 心跳检查点上报学习会话
- **THEN** 本次上报的 `validStudySeconds` 反映自上次结算点以来的活跃秒数
- **AND** MUST NOT 为 0

#### Scenario: 不可见区间不计入有效时长

- **GIVEN** 学员在学习过程中切换到其他标签页，随后返回
- **WHEN** 下一个检查点上报学习会话
- **THEN** 页面不可见期间的秒数 MUST NOT 计入 `validStudySeconds`

#### Scenario: 可计时媒体暂停期间不计入

- **GIVEN** 内容项的素材为可计时媒体且处于暂停状态
- **WHEN** 检查点上报学习会话
- **THEN** 暂停期间 MUST NOT 计入 `validStudySeconds`

#### Scenario: 结算与清零衔接不重复计数

- **GIVEN** 一次上报已经结算并清零累计秒数
- **WHEN** 下一次检查点上报
- **THEN** 只包含两次上报之间的活跃秒数
- **AND** MUST NOT 重复计入已上报过的区间

### Requirement: 内容项完成阈值由内容项门槛与自定义材料规则共同决定

系统 MUST 按以下顺序解析 ASSET 内容项的完成阈值，遇到第一个大于 0 的值即采用：

1. 该内容项**自定义**材料规则（`source = CUSTOM`）的「要求学习秒数」；
2. 该内容项的「最低学习秒数」`minimumStudySeconds`；
3. 材料规则默认值（机构默认文档页秒数）。

系统 MUST NOT 把缺失、为 0 或无自定义材料规则的阈值当作「立即完成」。内容项门槛 MUST NOT 因存在
未自定义的材料规则默认值而被忽略。QUIZ 与 QUESTION_BANK MUST NOT 按学习时长自动完成，MUST 继续由
成绩生效回调判定。LIVE MUST NOT 按学习时长自动完成。内容项进度一旦为 COMPLETED，MUST NOT 因阈值
变化被回退为未完成。

#### Scenario: 内容项最低学习秒数生效

- **GIVEN** 某 ASSET 内容项未配置自定义材料规则，其「最低学习秒数」为 60
- **WHEN** 学员上报的有效学习秒数累计达到 60
- **THEN** 该内容项进度转为 COMPLETED

#### Scenario: 自定义材料规则覆盖内容项门槛

- **GIVEN** 某 ASSET 内容项「最低学习秒数」为 30，且配置了要求学习秒数为 120 的自定义材料规则
- **WHEN** 学员上报的有效学习秒数累计达到 30 但未达到 120
- **THEN** 该内容项进度 MUST NOT 转为 COMPLETED
- **WHEN** 累计达到 120
- **THEN** 该内容项进度转为 COMPLETED

#### Scenario: 阈值缺失不立即完成

- **GIVEN** 某 ASSET 内容项的自定义材料规则、内容项门槛与材料规则默认值均未给出大于 0 的秒数
- **WHEN** 学员首次上报学习会话
- **THEN** 该内容项进度 MUST NOT 直接转为 COMPLETED

#### Scenario: 测验与题库练习不按时长自动完成

- **GIVEN** 某内容项类型为 QUIZ 或 QUESTION_BANK
- **WHEN** 学员上报学习会话且累计秒数超过任一阈值
- **THEN** 该内容项进度 MUST NOT 因学习时长转为 COMPLETED

### Requirement: 时长模式不再作为可配置项

系统 MUST NOT 要求管理员为内容项配置「时长模式」，学习进度 MUST 由客户端按活跃区间自动上报。
管理端 MUST NOT 展示「时长模式」设置项。`MaterialRuleUpdateRequest.durationMode` 字段留空时，
服务端 MUST 按 `AUTO` 落库，MUST NOT 因该字段缺失或为空拒绝请求。

#### Scenario: 不传时长模式仍可保存材料规则

- **GIVEN** 管理员在材料规则面板调整「要求学习秒数」并保存
- **WHEN** 请求未携带 `durationMode`
- **THEN** 服务端按 `AUTO` 落库并返回成功
- **AND** MUST NOT 返回字段校验错误

#### Scenario: 管理端不再展示时长模式

- **GIVEN** 管理员打开 ASSET 内容项的「材料学习规则」面板
- **THEN** 面板 MUST NOT 出现「时长模式」的自动 / 手动选择项

### Requirement: 契约描述与实现语义一致

`docs/api/course.openapi.json` MUST 与实现保持一致：`attemptCount` MUST 描述为学习会话次数而非作答次数；
已废弃字段 MUST 在描述中标明，且示例值 MUST 落在自身 enum 取值范围内。

#### Scenario: 作答次数字段描述

- **GIVEN** 学习进度契约中的 `attemptCount`
- **THEN** 其 `description` MUST 说明该值按学习会话上报次数累加

#### Scenario: 已废弃字段的示例值

- **GIVEN** 材料规则契约中的 `durationMode`
- **THEN** 其示例值 MUST 为 `AUTO` 或 `MANUAL` 之一
- **AND** 其 `description` MUST 标明该字段已废弃、不再参与完成判定
