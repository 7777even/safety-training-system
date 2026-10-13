# 素材转码后端 QA（2026-08-04）

## 范围

OpenSpec Change：`2026-08-04-implement-asset-transcode-backend` Task 1～6，lims 纯后端范围；未修改前端。

## 已执行验证

- Admin 零 DB：`mvn -pl sts-service-admin -am test`，最终 488/488 通过。
- Admin 全量 IT：`mvn -pl sts-service-admin -am -Pintegration-test test`，最终 117/117 通过。
- MinIO 定向 IT：`AwsS3StorageGatewayIT` 4/4，覆盖私有对象、真实 Range、multipart 续传/完成/校验/取消及 bucket 快速失败。
- 转码定向 IT：`TranscodeJobMapperIT` 2/2、`TranscodeResultServiceIT` 1/1、multipart migration IT 1/1。
- FFmpeg 真机：短视频生成 720p H.264/AAC HLS、TS、JPG；短音频生成 128kbps MP3；ffprobe 时长检查通过。
- 契约：`asset.openapi.json`、`system-admin.openapi.json` JSON 解析通过，内部 `$ref` 全部可解析；补齐既存 `NotFoundError`、
  `ConflictError` 悬空引用。
- 静态检查：`openspec validate --strict`、`git diff --check` 通过；无 `frontend/` 差异。

## 2026-08-06 真机补验

- 环境：本机 MinIO `19000`、OnlyOffice Document Server 9.2 `28090` 均可用；OnlyOffice 容器可通过宿主机网关回拉后端内部源文件。
- 缺陷复现：中文 DOCX 首次任务因 `/internal/asset/transcode-sources/**` 被全局登录拦截返回 401，OnlyOffice 报错 `-4`。
- TDD 修复：新增 `SaTokenConfigTest`，先观察内部路由仍匹配登录拦截的失败，再精确排除该路由；普通业务路由继续匹配登录拦截。
- 自动化回归：`mvn -pl sts-service-admin -am test`，472/472 通过。
- DOCX：4,124,897 字节中文样本经人工重试转码成功，生成 16 页、1,605,841 字节 PDF；PDF 嵌入中文字体且文本可提取；
  play-info、206 Range、PDF 文件签名通过；失效一次性令牌无登录重放返回 404。
- PPTX：66,608,235 字节中文样本经 7 个 10MB 分片上传、缺失分片补传和 checksum 校验后合并成功；OnlyOffice 转换生成
  66 页、23,148,597 字节 PDF，嵌入中文字体且中文文本可提取。
- 本地测试数据：初始化器未生成 `sts_org_member`，仅在本地 `sts` 测试库补入两个初始化账号的机构成员关系后执行机构隔离上传；
  未修改 migration 或生产数据。

Task 4 的环境阻塞已解除，但全页视觉复核发现该 DOCX 原文件记录 24 页、OnlyOffice 仅输出 16 页，多页浮动文本框发生重排叠字；
此前仅依据“可打开、中文可提取、字体已嵌入”判定通过不充分，Task 4 已恢复为进行中。源文件由 WPS 生成，包含 120 个浮动锚点、
72 个旧式 VML 文本框。Task 6 仍需统一复核视频、音频、原生 PDF、Excel 和可重试失败样本。

Task 4 独立双轴 Review：规格符合性“通过”；代码质量“通过”。放行范围仅为一次性源文件回拉路径，普通业务接口继续要求登录；
无前端、API Contract 或数据库结构变化。

真机过程中记录的两个 Task 6 问题已按 TDD 修复：multipart 分片请求体改为受 10MB 上限约束的可重复读取请求体；PDF 流式响应只在
首次 REQUEST 执行 Sa-Token 登录/注解鉴权，框架内部 ASYNC 二次分派不重复鉴权。Red 分别复现“第二次读取为空”和“ASYNC 再次调用
checkLogin”；Review 后补齐首次鉴权标记、有界读取及超长/不足分片测试。最终 Green 为目标零 DB 8/8、Admin 零 DB 478/478、
`AwsS3StorageGatewayIT` 4/4。本机 7 个分片并发上传全部一次成功；PDF 32 字节 Range 返回 206、正确 Content-Range 与
`%PDF-1.7`，请求结束后无 Sa-Token async dispatch 异常日志。独立双轴 Review：规格符合性“通过”、代码质量“通过”。

用户确认素材 Word 上传兼容性限制后，按 TDD 新增预检：素材目录拒绝旧 `.doc`、损坏 `.docx` 及包含旧式 VML 文本框的
`.docx`；不按 WPS 生成器一刀切，标准 DOCX 和非素材目录附件继续放行。单文件在写 MinIO 前校验，multipart 在合并并完成
size/checksum 后、登记文件前校验；拒绝时删除合并对象、会话转 `ABORTED`。Red 阶段先观察校验器缺失导致目标测试编译失败；
首次 Review 发现非主文档 XML/VML 的 VML 扫描、ZIP 解压总量和补偿失败契约仍有缺口；两轮补充测试先后稳定观察 6 项失败，再增加有效
OOXML Content Types/主关系/主文档根校验、所有 XML/`.vml` part 的 VML 扫描、含内容目录 entry 在内的单 entry 64MB/总解压
256MB 上限，并确保删除或状态更新
失败不会覆盖原始 5006 且两项补偿都会尝试。最终 Green 为兼容性目标测试 17/17、Admin 零 DB 488/488、全量 MySQL/MinIO IT
117/117；新增 `MultipartWordValidationMinioIT` 真实验证拒绝后对象不存在、会话 `ABORTED`、文件未登记。真实接口上传原异常 DOCX
返回 HTTP 400/code 5006，测试库有效文件记录新增数为 0；WPS 生成但不含 VML 文本框的 `招标文件.docx` 上传返回 200，随后已
通过文件删除接口清理记录和 MinIO 对象。

## 2026-08-10 一次性源文件回拉租户修复

- 复现证据：免登录的 `GET /internal/asset/transcode-sources/{token}` 在
  `InternalTranscodeSourceController.source` 查询 `sts_asset_transcode_job` 时，因没有登录会话可供 Filter 绑定租户而触发
  4050。
- Red：先新增令牌租户载荷与 Controller 上下文测试，旧实现因 `issue` 和 `SourceGrant` 缺少 `organizationId` 在测试编译期失败。
- Green：令牌保存 `organizationId:jobId:fileInfoId`，沿用 5 分钟 Redis TTL，并以 Lua GET+DEL 保持一次性；Controller 在令牌租户内
  查询任务、文件并下载，`callWithTenant` 在成功或异常后均恢复/清理上下文。
- 目标验证：`OneTimeSourceTokenServiceTest`、`InternalTranscodeSourceControllerTest` 共 4/4 通过。
- 全量零 DB：`mvn -pl sts-service-admin -am test`，565/565 通过。
- 未执行 IT：没有 Entity、Mapper、SQL、schema 或真实持久化事务变化，本次风险由零 DB 令牌载荷、TTL 参数、上下文边界及清理测试覆盖。
- Review：规格符合性“通过”；代码质量“通过”。内部路由仍为一次性令牌访问，没有增加全局租户旁路或登录绕过范围。

## 2026-08-04 未执行与阻塞记录

经批准只读执行 `docker ps`，测试机未运行 OnlyOffice Document Server，仓库环境也未注入 `ONLYOFFICE_*` 参数。按用户决定暂缓以下验证：

- 中文 DOCX/PPTX → PDF 真机转换；
- OnlyOffice 中文字体、JWT 配置、容器回拉一次性源文件地址；
- 文档 PDF 产物的 MinIO/play-info 纵向链路。

因此 Task 4、Task 6、COURSE-02 与 M6 不关闭。代码层的 OnlyOffice Provider、JWT、Redis 一次性令牌、SSRF/大小限制和 PDFBox
页数测试已通过，但不替代真机证据。

## Review

- 规格符合性：需补完整样本验收。Task 1～3、5 通过；Task 4 的不兼容 Word 拒绝规则已通过，仍待标准 DOCX 视觉复核；Task 6
  待视频、音频、原生 PDF、Excel 和失败重试统一验收。
- 代码质量：通过。未发现越界前端修改、无关重构、明文凭据或未经批准的数据库结构变化。
