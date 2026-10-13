# Design：补齐课程封面上传与受控读取

## 1. 设计原则

- Owner 为 `lims`，仅实施后端、`docs/api/`、相关 OpenSpec 和工程记录；前端以 OpenAPI 为准另开。
- 复用既有私有对象存储和 `us_sys_file_info`，不为课程封面另建表或另建 bucket。
- 文件访问以课程资源为授权边界，客户端不拼接对象键，也不能把系统文件 ID 当作通用下载凭证。
- 新路径规则只作用于新上传课程封面；旧文件记录和对象键按原值继续读取。

## 2. 对象键与文件登记

新增集中式对象键生成能力，为课程封面生成：

```text
course-cover/{yyyy}/{MM}/{uuid}.{ext}
```

- 日期使用应用运行时的当前日期，与现有 `source/{yyyy}/{MM}/...` 风格一致。
- 原文件名不得进入对象路径；扩展名仅保留经过白名单规范化的安全后缀。
- `us_sys_file_info.bucket_name` 继续记录业务目录标识 `course-cover`；真实 MinIO bucket 仍由存储配置决定。
- `object_name`、`file_url` 保持私有存储语义，不在课程 API 响应中返回。
- Local 与 MinIO 存储适配器必须生成一致的业务前缀和年月层级，避免环境行为分叉。

`source` 与 `derivative` 的生成逻辑不在本 Change 中修改。

## 3. 上传接口

新增 `CourseCoverController`：

```text
POST /api/course/covers
Content-Type: multipart/form-data
part: file
```

响应 `CourseCoverUploadVO`：

- `fileInfoId`：后续写入课程的封面文件 ID。
- `fileName`：原始文件名的安全展示值。
- `fileSize`：文件字节数。
- `contentType`：服务端确认的图片类型。

接口允许具备 `course:course:add` 或 `course:course:edit` 任一权限的当前机构用户调用，不新增权限码。服务端限制：

1. 文件非空且不超过暂定的 10 MiB。
2. 允许 `jpg/jpeg/png/webp`；扩展名、声明 MIME 与文件签名必须一致，不能只信任客户端 `Content-Type`。
3. 文件写入对象存储成功后登记 `us_sys_file_info`；数据库登记失败时尽力删除已写入对象。
4. 响应不返回 bucket、对象键、`fileUrl` 或存储凭据。
5. `course-cover` 是课程封面来源可信性所需的保留业务目录；既有通用单文件和分片上传接口 MUST 拒绝调用方提交该目录，并在
   `docs/api/system-admin.openapi.json` 中声明 400/5001 错误语义，防止绕过图片校验伪造封面记录。

图片大小上限标记为**暂定**，实施前随本 Change 一并人工确认；如果产品没有另行指定，采用 10 MiB。

## 4. 课程保存与关联校验

`CourseCreateRequest` 和 `CourseUpdateRequest` 增加可空 `coverFileId`，Converter 同步映射。创建或更新时若提供该字段，Service
必须校验：

1. 文件记录存在、未删除且属于当前机构。
2. `bucket_name=course-cover`，禁止把素材源文件、派生文件或普通系统附件冒充课程封面。
3. 文件后缀与服务端确认的图片类型属于允许集合。
4. 更新传 `null` 表示清空封面；省略与显式 `null` 在当前 PUT 全量更新语义下均表示清空。

课程发布继续把 `coverFileId` 冻结进既有课程版本快照。课程删除、换封面或清空封面时只解除引用，不删除文件对象。

## 5. 受控查看与下载

在 `CourseCoverController` 新增：

```text
GET /api/course/courses/{courseId}/cover?download=false
```

- 服务端先按当前机构查询课程，再读取该课程真实关联的 `coverFileId`；没有封面或已登记对象实际不可用时返回 HTTP 404 +
  课程域错误码，
  不向客户端透传底层 bucket、对象键、本地路径或 SDK 异常信息。
- 当前机构内已认证用户可读取课程封面，以支持管理端和学员课程列表/详情；跨机构课程统一按不存在处理。
- `download=false`（默认）返回 `Content-Disposition: inline`；`download=true` 返回安全文件名的 `attachment`。
- 返回服务端确认的图片 `Content-Type`、`Content-Length`、`ETag` 或等价缓存校验信息；本次图片不要求 Range。
- 不接受独立 `fileInfoId` 路径参数，防止调用方枚举系统文件；不暴露对象键和存储地址。

读取接口是二进制响应，不经过 JSON `Result` 包装；401、403、404 仍由统一异常处理返回标准错误外壳。

## 6. API、权限与数据影响

- API：新增两个路径；课程创建/更新请求正式增加可空 `coverFileId`；更新 `docs/api/course.openapi.json`，并同步通用文件上传对
  `course-cover` 保留目录的既有接口错误语义到 `docs/api/system-admin.openapi.json`。
- 权限：上传沿用 `course:course:add` 或 `course:course:edit`；读取要求已认证并受当前机构隔离，不新增权限码。
- 数据：无数据库结构变化；新增文件继续写入 `us_sys_file_info`，课程复用既有 `cover_file_id`。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。

## 7. TDD 与验证

Red 阶段先增加：

1. Controller standalone 测试：上传参数、权限声明、内联/下载响应头、非法请求不进入 Service。
2. Service 纯 Mockito 测试：合法图片上传；空文件、超限、伪造 MIME/后缀、跨机构、错误目录和不存在文件被拒绝；创建/更新正确保存或清空
   `coverFileId`。
3. 带 DB IT：`us_sys_file_info` 的机构隔离和真实课程 `cover_file_id` 映射；复用既有 schema，不新建测试 DDL。
4. 存储测试：Local/MinIO 新封面对象键满足 `course-cover/yyyy/MM/uuid.ext`，现有 source 与 derivative 断言保持不变。

Green 阶段只实现使上述行为通过的最小代码；Refactor 只提取必要的文件类型校验、对象键生成和安全响应构造，不重构无关素材访问链。

验证命令按 Admin 两层规则执行：

- `mvn -pl sts-service-admin -am test`
- `mvn -pl sts-service-admin -am -Pintegration-test test`
- `openspec validate 2026-08-27-complete-course-cover-api --strict`
- `git diff --check`

## 8. 风险与取舍

- 同机构已认证用户可以读取已知课程 ID 对应的封面，这是支持学员课程卡片的最小可见性；课程其他敏感详情仍按各自业务接口授权。
- 上传后未绑定课程的文件会成为孤儿，本 Change 不自动清理，以避免在未建立引用计数前误删；后续可独立设计过期清理。
- 不处理旧目录整理，避免对象搬迁与数据库对象键更新带来的不可逆风险。
