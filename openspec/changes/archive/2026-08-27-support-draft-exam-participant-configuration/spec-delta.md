# Spec Delta：草稿考试人员配置与执行归属

## 变更类型

- 新增能力规格：`draft-exam-participant-configuration`
- 不删除或重命名既有 Requirement。
- 与 `course-assessment-source-separation`、`system-participant-selection`、`exam-paper-rule-defaults` 现行规格保持兼容。

## 机器可读 Delta

正式 Requirements 与 Scenarios 位于：

- `specs/draft-exam-participant-configuration/spec.md`

该 Delta 新增以下四项要求：

1. 独立考试支持发布前配置具体考生和阅卷人。
2. 草稿批阅人读取无副作用地返回空集合。
3. 独立考试发布在原执行对象上冻结最终快照。
4. 删除草稿考试时清理未投入执行的人员归属。

