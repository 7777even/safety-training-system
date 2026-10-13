# Design：文件对象统一增加租户编码根目录

## 1. 核心规则

所有新对象键使用：

```text
{ORG_CODE}/{EXISTING_OBJECT_KEY}
```

其中 `ORG_CODE` 来自当前 `TenantContext` 对应的 `sts_org_organization.org_code`，由服务端查询并规范化为数据库保存值。机构编码全局唯一且创建后不可修改，
调用方不得通过请求参数覆盖。

典型结果：

```text
ACME/source/2026/08/{uuid}.pdf
ACME/course-cover/2026/08/{uuid}.png
ACME/derivative/{assetId}/{jobId}/hls/index.m3u8
ACME/general/{existing-local-layout}
```

本 Change 只在既有对象键前增加租户根目录，不顺带统一 Local 与 MinIO 既有业务目录差异，不改变年月规则。

## 2. 租户编码解析

新增集中式租户对象键组件，职责为：

1. 从 `TenantContext.requireTenantId()` 取得当前机构 ID。
2. 查询未删除的机构主数据并取得不可变 `orgCode`。
3. 校验编码符合既有 `[A-Z0-9_-]{2,64}` 约束。
4. 将编码作为单个安全路径段拼接到既有相对对象键之前。
5. 租户上下文缺失、机构不存在或编码非法时拒绝写入，不得退化为无前缀对象。

异步转码任务已有 `organizationId`，`TranscodeJobProcessor` 会在执行 provider 前绑定 `TenantContext`，因此与 HTTP
上传共用同一解析规则，不新增旁路。

## 3. 写入路径

所有新写入入口必须经过集中组件：

- `StorageService` 的 Local/MinIO 单文件上传。
- 通用 MinIO multipart 初始化生成的源文件对象键。
- 课程封面上传。
- `DocPreviewProvider` 和 `FfmpegProvider` 生成的派生对象键及失败清理键。

对象键生成顺序为：先按现有规则生成业务相对键，再添加当前租户编码根目录。数据库继续保存最终完整对象键。

## 4. 历史兼容

兼容原则为“已保存键即真相源”：

- 下载、预览、删除、失败补偿和转码源文件读取直接使用数据库或任务中保存的完整 `objectName`，不得二次添加租户前缀。
- 历史 `source/...`、`derivative/...`、`course-cover/...` 和通用目录对象保持可读。
- 新对象使用 `{ORG_CODE}/...`；不进行后台迁移或双写。
- 课程封面关联校验同时接受合法历史无前缀键和当前机构编码前缀键；其他机构编码前缀即使文件记录被伪造也必须拒绝。
- HLS 播放以数据库登记的 manifest 对象键为锚点推导同目录 segment，兼容有前缀和无前缀两种布局，不硬编码拼接新前缀。

## 5. API Contract

HTTP 路径、请求参数、权限和错误码不新增。`docs/api/system-admin.openapi.json` 的 `FileInfoVO.objectName`、`fileUrl`
说明与上传/查询示例需改为
带机构编码根目录的新对象键，并注明历史记录可能仍是无前缀格式。

课程封面响应仍不暴露对象键，`docs/api/course.openapi.json` 只需将新封面内部路径说明更新为
`{ORG_CODE}/course-cover/{yyyy}/{MM}/...`。

## 6. 数据与权限影响

- 数据库：无表结构或 Migration 变化；新记录保存带前缀完整键，历史记录不更新。
- 权限：沿用现有上传、课程和素材权限；对象根目录只能由当前租户上下文决定。
- 存储：继续使用同一私有 bucket；机构编码是对象键首段，不是新 bucket。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。

## 7. TDD 与验证

Red 阶段先覆盖：

1. 租户对象键组件能从机构 ID 解析编码，拒绝上下文缺失、不存在机构和非法编码。
2. Local、MinIO、通用单文件、multipart、课程封面、文档预览与视频派生的新键均以当前机构编码开头。
3. 两个机构上传同名文件时进入不同根目录。
4. 课程封面严格校验拒绝其他机构编码前缀，但继续接受历史无前缀键。
5. 历史 source、course-cover 和 derivative 键继续读取；HLS manifest 与 segment 同时覆盖新旧布局。
6. 写入失败补偿、派生清理使用已经生成的完整键，不发生重复加前缀。

验证按 Admin 两层规则执行目标零 DB 测试和受影响 MySQL IT，再执行 Admin 全量零 DB、完整 MySQL IT、两份 OpenAPI JSON
校验、OpenSpec strict 与
`git diff --check`。

## 8. 风险与取舍

- 每次首次解析机构编码会增加机构主数据读取；实现可在组件内部做受控缓存，但机构编码不可变仍须以数据库为真相源，缓存不得改变失败语义。
- 新旧对象键长期并存会增加运维展示复杂度，但避免了不可逆迁移和引用中断。
- 对象键前缀不是权限替代品；数据库租户插件、业务资源授权和私有 bucket 策略继续生效。

