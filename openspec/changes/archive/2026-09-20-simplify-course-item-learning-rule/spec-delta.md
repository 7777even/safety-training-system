# Spec Delta：课程内容项材料学习规则最小化闭环

## Modified Capability

- `course-learning-progress`

正式要求位于 `specs/course-learning-progress/spec.md`。

## Delta

1. ASSET 内容项 MUST 以 `minimumStudySeconds` 作为唯一要求学习时长，且值 MUST 大于 0。
2. LIVE、QUESTION_BANK、QUIZ 的 `minimumStudySeconds` MUST 为 0，MUST NOT 按该字段自动完成。
3. 新课程版本快照 MUST NOT 写入 `materialRule`；旧快照 MUST 保持原有效门槛兼容。
4. 系统 MUST NOT 提供机构课程默认学习规则或独立材料规则配置接口。
5. 内容项 MUST NOT 重复配置快进、倍速、水印、辅助音频或离页暂停；倍速和水印只使用课程级设置。
6. 管理端 MUST 在同一内容表单保存素材与要求学习时长，MUST NOT 提供第二个材料规则保存动作。
7. `sts_course_setting` MUST 保留历史表和值并标注为暂时隐藏，MUST NOT 作为当前页面、API 或业务规则依据。
