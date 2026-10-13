# Design：修复转码链路的租户上下文缺失

## 1. 在哪一层声明租户

候选三处：

| 方案 | 位置                                          | 评价                                                                |
|----|---------------------------------------------|-------------------------------------------------------------------|
| A  | `TranscodeScheduler.poll()` 提交任务时           | 此时只有 `jobId`，尚未读到 job，拿不到 `organizationId`                        |
| B  | `TranscodeJobProcessor.process()` 读到 job 之后 | job 自带 `organizationId`，且能覆盖 `markProcessing`/`succeed`/`fail` 全程 |
| C  | `TranscodeResultService.succeed()` 内部       | 只覆盖写派生一处，`fail` 与 provider 执行仍在裸上下文中                              |

**选 B。** 理由是租户是「这个任务属于谁」的属性，应当在拿到任务实体后立刻绑定，整个处理过程都在其内进行；
在 C 处补只是把当前这个症状按下去，下次谁在链路里再加一次落库还会重现。

```java
public void process(Long jobId) {
  if (mapper.claimPending(jobId) != 1) return;
  AssetTranscodeJob job = mapper.selectById(jobId);
  if (job == null) return;
  // 任务自带租户，处理全程在该租户上下文内进行
  TenantContext.runWithTenant(job.getOrganizationId(), () -> processInTenant(job));
}
```

`claimPending` 与 `selectById` 保留在上下文之外：调度器需要跨租户抢占待处理任务，这是有意的全局行为，
且这两条 SQL 由 `AssetTranscodeJobMapper` 自带条件控制，不依赖插件。

## 2. 保留还是删除既有的显式传参

`markProcessing`、`finishTranscode`、`fail` 目前把 `job.getOrganizationId()` 显式写进 WHERE。
绑定上下文后这些条件与插件注入的条件重复，但**保留不删**：

- 它们是 `@Update` 注解里的手写 SQL，条件明确、可读；
- 删掉等于把这三处的正确性也交给上下文，一旦上下文绑定出错，UPDATE 会打到别的租户；
- 重复条件不产生语义歧义（同一列同一值），代价仅是 SQL 稍长。

这与业务域「不得手写第二套过滤条件」的要求不冲突：那条针对的是**查询可见范围**，这里是**定位一行确定的记录**。

## 3. 测试如何避免同类假绿

问题根源是 `AbstractMysqlIntegrationTest` 在 `@BeforeEach` 绑定 DEMO 租户，
使「依赖上下文」的缺陷在 IT 中不可见。

本 Change 的 IT MUST 在断言前显式 `TenantContext.clear()`，模拟调度线程的真实形态。
新增用例至少覆盖：

- 无上下文调用 `TranscodeJobProcessor.process`，转码成功后派生文件落库且 `organization_id` 等于 job 的租户；
- 素材最终为 `SUCCEEDED` 而非 `FAILED`；
- 两个租户各自的转码任务互不串扰，派生文件不跨租户可见。

同时给 `TranscodeResultServiceIT` 补一条无上下文用例，让它不再只在有上下文时为真。

## 4. `format` 的取值口径

现状：`AssetMediaAccessService.playInfo` 在无派生文件时返回 `source.getFileSuffix().toLowerCase()`，
是开放集合（任意上传后缀），而契约声明的是闭合枚举 `hls/mp3/pdf/xls/xlsx/original`。

两条路线：

| 方案 | 做法         | 评价                                               |
|----|------------|--------------------------------------------------|
| A  | 契约放开为开放字符串 | 前端要自行枚举 mp4/avi/mov/mkv…，且无法区分「可直接播放」与「转码没成功」    |
| B  | 代码收敛到契约枚举  | 前端只需处理固定几种；Excel 直显所需的 `xls`/`xlsx` 已在枚举内，保留特判即可 |

**选 B。** 具体规则：

```text
有派生文件  → HLS→"hls"；TRANSCODED→"mp3"；PREVIEW→"pdf"
无派生文件  → 源后缀属于 {xls, xlsx} → 原样返回（vue-office 直显需要区分）
             其余一律 → "original"
```

`original` 的语义明确为「没有派生产物，前端按原文件直链处理」。这样 `mp4` 不再出现，
而「视频返回 original」本身就是一个可观测的异常信号，便于发现转码未成功。

## 5. 历史数据

已因本缺陷失败的素材停留在 `FAILED`，其转码任务为终态。本 Change **不自动重跑**：
批量重跑涉及存量数据订正与 MinIO 残留对象清理，口径需人工确认。

修复上线后，可用既有入口 `POST /api/asset/assets/{assetId}/retry-transcode`
（`AssetServiceImpl.retryTranscode`，限 `processStatus = FAILED` 且类型属音视频/可转换文档）
按素材逐个重发。该入口走 Web 线程有租户上下文，且重发后的执行仍由本 Change 修复的调度链路承接。
