# Spec Delta：培训标准 / 计划 / 执行（M3）

## ADDED Requirements

### Requirement: 培训类型与标准

系统 MUST 支持机构维护培训类型（稳定 type_code）与培训标准及规则，并支持标准发布。监管关系 MUST 复用已有 org 监管
API，不得重复建表。

### Requirement: 培训模板可引用

系统 MUST 支持模板分类、模板、阶段与内容编排；模板发布后可供计划引用；引用不得回写源模板。

### Requirement: 评价表单不可变版本

系统 MUST 支持评价表单定义；发布写新 version_no，旧版本只读；计划绑定具体 form_version_id。

### Requirement: 培训计划发布冻结快照

计划发布时 MUST 冻结类型/标准/模板快照 JSON 与哈希；参训人可通过指派或 audience 绑定写入，且 (plan, user) 唯一。

### Requirement: 执行实例幂等生成

计划发布后生成执行关联时，对同一 plan_content MUST 幂等：COURSE/EXAM 写 execution_link，OFFLINE 建线下场次。

### Requirement: 线下执行事实

系统 MUST 支持签到 upsert/确认、线下成绩、附件、奖惩；教师可查询本人线下任务。

## REMOVED Requirements

无。

## MODIFIED Requirements

无。
