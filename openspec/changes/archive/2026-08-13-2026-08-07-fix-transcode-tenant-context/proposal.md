# Proposal：修复转码链路的租户上下文缺失

## 背景

多租户改造（`2026-08-06-rebuild-multi-tenant-isolation`）Task 2 为 `us_sys_file_info`、
`sts_asset_derivative` 等表加上了 `NOT NULL` 的 `organization_id`，并约定归属由
`TenantLineInnerInterceptor` 在写入时自动填充。填充依赖 `TenantContext` 这个 ThreadLocal。

转码执行链路跑在调度线程上，**不经过 `TenantContextFilter`，因而没有租户上下文**：

```text
TranscodeScheduler.poll()            @Scheduled + transcodeTaskExecutor 线程池
  └─ TranscodeJobProcessor.process(jobId)
        └─ TranscodeResultService.succeed(job, output)
              ├─ fileInfoMapper.insert(file)        ← 依赖插件注入 organization_id
              └─ derivativeMapper.insert(derivative) ← 依赖插件注入 organization_id
```

`TenantLineHandlerImpl.ignoreTable` 在「非 Web 线程且上下文缺失」时选择放行（不注入条件、不填充列），
于是这两条 INSERT 不带 `organization_id`，直接违反非空约束。

已用 Testcontainers 真库探针证实：

```text
>>> 有上下文: 1
>>> 无上下文: DataIntegrityViolationException
```

后果：`succeed()` 抛出 → `TranscodeJobProcessor` 捕获 `RuntimeException` → 调用
`resultService.fail(...)` → 素材被置为 `FAILED`。**多租户改造上线后，视频、音频与文档转码全部无法成功。**

两个加重问题的因素：

- **同一个类里写法不一致**：`markProcessing` 与 `finishTranscode` 都是把 `job.getOrganizationId()`
  显式写进 SQL 的，唯独写派生文件这步依赖插件，因此问题只在链路末端暴露。
- **测试是假绿**：`TranscodeResultServiceIT` 继承 `AbstractMysqlIntegrationTest`，
  基类在 `@BeforeEach` 绑定了 DEMO 租户，测试环境有上下文而生产没有，用例通过但保护不了生产。

此外，前台 `GET /api/asset/assets/{assetId}/play-info` 在找不到派生文件时会退回源文件后缀，
返回 `format: "mp4"` 之类契约枚举之外的值，掩盖了「转码没成功」这一事实。

## 目标

1. 转码执行链路在处理业务数据前显式声明租户，使派生文件与其素材归属同一租户。
2. 把「异步/定时任务必须显式声明租户」这条约束固化成不依赖上下文绑定的测试，消除同类假绿。
3. 让 `play-info` 的 `format` 与契约一致，且不把「转码失败」伪装成「可直接播放源文件」。

## 非目标

- 不改动转码算法、FFmpeg / OnlyOffice 参数与派生文件产物格式。
- 不改动 `TenantLineHandlerImpl` 在非 Web 线程的放行策略——该策略是给应用启动、集成测试等
  确实无租户场景留的口子，收紧它属于多租户改造的后续决策，不在本 Change 范围。
- 不重跑或修复历史失败数据；数据订正另行确认。

## 用户价值

- 上传视频后能正常转码并以 HLS 播放，而不是停在失败状态或退化成直链播放源文件。
- 派生文件带正确租户归属，跨租户不可见，不会因为归属缺失而绕过隔离。
- 前端拿到的 `format` 是契约内的稳定取值，不必自行枚举任意文件后缀。

## 影响范围

- 后端：`com.sts.asset.transcode`（`TranscodeJobProcessor`）、`com.sts.asset.service`
  （`AssetMediaAccessService`）。
- 契约：`docs/api/asset.openapi.json` 的 `AssetPlayInfoVO.format` 说明。
- 数据：不新增、不修改表结构。
- 前端：`format` 取值口径变化，以 OpenAPI 为准另开。
