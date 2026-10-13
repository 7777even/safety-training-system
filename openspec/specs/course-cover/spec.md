# course-cover Specification

## Purpose

课程封面在课程语义下上传、关联、受控查看和下载。新对象写入当前机构编码根目录下的 `course-cover/{yyyy}/{MM}/...`
；读取按课程真实关联开放，不暴露存储实现。机器可读契约见
`docs/api/course.openapi.json`。

## Requirements

### Requirement: 课程封面必须形成受控上传与关联闭环

系统 MUST 提供课程语义下的封面图片上传能力。新上传封面 MUST 写入私有 bucket 的
`{ORG_CODE}/course-cover/{yyyy}/{MM}/{uuid}.{ext}` 对象键，并登记当前机构的系统文件记录。课程创建和更新 MUST 接收可空
`coverFileId`，且只允许关联当前机构、未删除、属于课程封面目录并通过图片类型校验的文件。

#### Scenario: 上传并创建带封面的课程

- **WHEN** 具备课程新增或编辑权限的用户上传合法封面图片，并在创建课程时提交返回的 `coverFileId`
- **THEN** 系统把图片写入 `{ORG_CODE}/course-cover/{yyyy}/{MM}/...` 并登记当前机构文件记录
- **AND** 新课程保存该 `coverFileId`，课程详情和后续发布快照可以读取同一关联
- **AND** API 响应不暴露 bucket、对象键、永久地址或存储凭据

#### Scenario: 更新或清空课程封面

- **WHEN** 当前机构管理员更新课程并提交另一个合法课程封面 ID 或提交空值
- **THEN** 系统分别替换或清空课程的 `coverFileId`
- **AND** 系统不因解除关联而直接删除原文件对象

#### Scenario: 拒绝非法封面关联

- **WHEN** 调用方提交跨机构文件、普通系统文件、素材源/派生文件、不存在或已删除文件作为课程封面
- **THEN** 系统拒绝创建或更新并返回明确的 4xx 业务错误
- **AND** 不修改课程已有封面关联，不泄露目标文件详情

#### Scenario: 拒绝伪造或超限图片

- **WHEN** 上传文件为空、超过确认大小上限、格式不在 JPG/JPEG/PNG/WebP 白名单，或扩展名、MIME 与文件签名不一致
- **THEN** 系统返回 HTTP 400 和稳定课程域错误码
- **AND** 不产生可用的课程封面文件记录

#### Scenario: 通用上传不得伪造课程封面来源

- **WHEN** 调用方通过通用单文件上传或分片上传提交保留目录 `course-cover`
- **THEN** 系统返回 HTTP 400 和稳定系统文件错误码
- **AND** 不写入对象、不创建上传会话或课程封面文件记录

### Requirement: 课程封面必须按课程进行受控查看与下载

系统 MUST 提供按 `courseId` 读取当前封面的受控接口。读取 MUST 校验当前机构内课程存在并使用课程真实关联的文件，MUST NOT
允许客户端仅凭任意系统 `fileInfoId` 获取文件。接口 MUST 支持内联查看与显式附件下载，且 MUST NOT 暴露私有存储实现信息。历史无租户前缀的
`course-cover/{yyyy}/{MM}/...` 键 MUST 继续按原键读取。

#### Scenario: 同机构用户查看课程封面

- **WHEN** 已认证用户请求当前机构内已有封面的课程并使用默认查看模式
- **THEN** 系统返回原封面图片字节、正确的安全 Content-Type 和 `Content-Disposition: inline`
- **AND** 响应不包含 bucket、对象键或永久对象地址

#### Scenario: 显式下载课程封面

- **WHEN** 已认证用户请求当前机构内课程封面并指定下载模式
- **THEN** 系统以经过清理的原始文件名返回 `Content-Disposition: attachment`
- **AND** 文件内容与该课程当前关联的封面一致

#### Scenario: 拒绝跨机构或无封面读取

- **WHEN** 用户请求其他机构的课程、已删除课程或没有封面的课程
- **THEN** 系统返回不泄露文件存在性的 404 业务错误
- **AND** 不访问或返回任何对象存储内容

#### Scenario: 已登记封面对象实际不可用

- **WHEN** 课程关联和文件记录有效，但对应 Local 或 MinIO 对象缺失或读取失败
- **THEN** 系统返回 HTTP 404 和稳定课程封面错误码
- **AND** 错误响应不包含 bucket、对象键、本地绝对路径或底层存储异常信息
