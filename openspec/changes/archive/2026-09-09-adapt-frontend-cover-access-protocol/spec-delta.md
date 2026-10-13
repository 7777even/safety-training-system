# Spec Delta：前端封面受控访问

## ADDED Requirements

### Requirement：前端只按受控封面路径展示学员封面

PC Web 与移动端必须仅在学员接口返回非空 `coverUrl` 时通过当前登录态读取该相对路径；不得使用学员响应中的文件主键或推导对象路径。

#### Scenario：无封面任务

- **WHEN** 学员列表项的 `coverUrl` 为 null
- **THEN** 页面展示统一占位且不请求封面

#### Scenario：培训任务封面

- **WHEN** 学员培训任务返回计划级 `coverUrl`
- **THEN** 页面按该路径读取封面，不由 `trainingTypeId` 推导管理端地址

### Requirement：培训类型使用统一封面写入字段

培训类型管理端的详情、创建与更新请求必须使用 `coverFileId`，已有类型预览必须使用类型受控封面接口。
