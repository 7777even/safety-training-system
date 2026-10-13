# 素材转码后端部署准备

本文记录素材转码后端的测试/部署环境配置。当前支持 MinIO、可恢复分片上传、异步调度、FFmpeg 音视频转码、OnlyOffice 文档转 PDF，
以及 qpdf 全量 PDF 线性化预览。

## 1. MinIO 准备

为 STS 创建独立私有 bucket（默认 `sts-private`）和独立应用账号。不要复用其他系统（例如 Milvus）的 bucket 或 root 凭据。应用账号至少需要：

- 对目标 bucket 执行 ListBucket、GetBucketLocation；
- 对 `source/*` 及后续 `derivative/*` 执行 PutObject、GetObject、DeleteObject；
- 创建、上传、列出、完成和中止 multipart upload。

部署环境默认不允许应用自动建桶；应由运维预先创建并验证私有策略。测试容器可设置 `MINIO_AUTO_CREATE_BUCKET=true`。

## 2. 数据库 migration

应用发布前执行：

```text
backend/sts-service-admin-init/src/main/resources/schema/schema-system.sql
```

该表保存上传会话所有权和恢复信息，不保存分片二进制或 MinIO 凭据。分片明细以 MinIO `ListParts` 为准。

## 3. 环境变量

```bash
STORAGE_TYPE=minio
MINIO_ENDPOINT=http://minio:9000
MINIO_ACCESS_KEY=<STS 专用 access key>
MINIO_SECRET_KEY=<STS 专用 secret key>
MINIO_BUCKET=sts-private
MINIO_REGION=us-east-1
MINIO_AUTO_CREATE_BUCKET=false
MINIO_MULTIPART_PART_SIZE=10485760
MINIO_MULTIPART_EXPIRE_HOURS=24
MINIO_CLEANUP_INTERVAL_MS=3600000
TRANSCODE_PROVIDER=ffmpeg
TRANSCODE_CONCURRENCY=2
TRANSCODE_RUNNING_TIMEOUT_MINUTES=30
TRANSCODE_PROCESS_TIMEOUT_MINUTES=20
FFMPEG_PATH=/usr/bin/ffmpeg
FFPROBE_PATH=/usr/bin/ffprobe
QPDF_PATH=/usr/bin/qpdf
TRANSCODE_WORK_DIR=/var/lib/sts/transcode
PDF_LINEARIZE_TIMEOUT_SECONDS=120
DOCUMENT_TRANSCODE_PROVIDER=onlyoffice
ONLYOFFICE_BASE_URL=http://onlyoffice-ds
ONLYOFFICE_JWT_SECRET=<至少 16 字节的独立密钥>
ONLYOFFICE_INTERNAL_SOURCE_BASE_URL=http://sts-service-admin:8080
ONLYOFFICE_ALLOWED_DOWNLOAD_HOSTS=onlyoffice-ds
ONLYOFFICE_MAX_OUTPUT_BYTES=209715200
ONLYOFFICE_TIMEOUT_SECONDS=120
```

要求：

- `MINIO_ENDPOINT` 必须是后端进程可达的 S3 API 地址，不是 Console 的 9001 地址。
- 密钥只能通过部署平台密钥或环境变量注入，禁止写入 Git、日志或 OpenAPI example。
- AWS SDK v2 S3 Client 强制使用 path-style access，以兼容内网 MinIO endpoint。
- `STORAGE_TYPE=minio` 启动时会检查 bucket；配置缺失或 bucket 不可访问会停止启动，不会静默回退本地磁盘。
- `TRANSCODE_PROVIDER=ffmpeg` 必须与 `STORAGE_TYPE=minio` 配套；FFmpeg/ffprobe 使用绝对路径且运行账号必须可执行。
- `DOCUMENT_TRANSCODE_PROVIDER=onlyoffice` 时 qpdf 是必需运行依赖；`QPDF_PATH` 必须为后端主机或容器内的绝对可执行路径。
- 所有原生 PDF 和 OnlyOffice PDF 均执行 `qpdf --linearize`，不按文件大小跳过；线性化主要改善首屏与 Range 渐进读取，不等同于压缩。
- 无需输入密码即可打开但带修改限制的 PDF，会仅对派生 PREVIEW 移除内部加密；SOURCE 保持原样。PREVIEW 必须继续存放在私有
  bucket 并通过既有鉴权代理访问，禁止改为公开对象。需要非空打开密码的 PDF 不受支持。
- `TRANSCODE_WORK_DIR` 只保存任务级临时文件，应用账号需要读写权限；不要配置为 `/`、上传目录或 MinIO 数据目录。
- OnlyOffice 必须启用与后端一致的 JWT 密钥；`ONLYOFFICE_INTERNAL_SOURCE_BASE_URL` 必须可由 Document Server 容器回拉。
- 下载白名单只填写 Document Server 转换结果实际主机，禁止加入通配符、元数据地址或任意外网主机。

## 4. FFmpeg 转码检查

部署前执行 `ffmpeg -version` 和 `ffprobe -version`，并确认构建包含 `libx264`、AAC 与 HLS。当前产物为：

- VIDEO：最高 720p H.264/AAC 单码率 HLS，5 秒分片（切片边界强制 IDR），并生成 480p JPG 封面；
- AUDIO：AAC 128kbps、5 秒独立切片 HLS（无封面），对象前缀与视频相同 `derivative/{assetId}/{jobId}/hls/`；
- 对象前缀：`derivative/{assetId}/{jobId}/{type}/`。

音频成功不再产出 MP3；play-info 只认 HLS，遗留 `TRANSCODED` 不作为可播格式。`libmp3lame` 不是新音频任务的必需编码器。

转码进程不经过 shell。单进程默认 20 分钟超时，任务超过 30 分钟仍为 RUNNING 时由巡检标记 `FAILED/TIMEOUT`
。数据库提交失败会删除本任务已上传但未登记的派生对象。

### 4.1 源分辨率与播放码率

`FfmpegCommandFactory.video` 使用 `scale=-2:min(720,ih)`：**只缩小、不放大**。未设 `-b:v` / `-maxrate`，视频为 libx264
质量模式，音频 AAC 128 kbps。因此每人播放带宽不是固定值，也不是按源文件像素线性增加。

| 源分辨率             | 学员实际看到的         | 对带宽的含义                              |
|------------------|-----------------|-------------------------------------|
| 640×360 一类亚 720p | 仍是 360p（不放大）    | 码率会明显低于 720p 课                      |
| 1280×720         | 720p            | 点播上限档                               |
| 1920×1080 / 4K   | **先缩成 720p 再播** | 不会按 1080p/4K 去拉播放带宽；多出来的是源存储和转码 CPU |

2026-08-27 用与生产一致的转码命令，对 10 秒、30 fps 合成源测算（平均码率 = HLS `.ts` 总体积 × 8 ÷ 时长）。这是**单文件转码后的平均码率
**，不是培训室 N 人同时播放压测，也不是客户真实课件。

| 源                                                    | 转完 HLS   |   TS 总体积 |            平均码率 |
|------------------------------------------------------|----------|---------:|----------------:|
| `La Paloma.mp4`（历史样片，640×360，约 153 s，HLS 约 7.02 MiB） | 640×360  | 7.02 MiB | 约 **0.37 Mbps** |
| 640×360 `testsrc2`（复杂图案）                             | 640×360  | 1.07 MiB |   **0.90 Mbps** |
| 1280×720 `testsrc2`                                  | 1280×720 | 3.52 MiB |   **2.95 Mbps** |
| 1920×1080 `testsrc2`                                 | 1280×720 | 1.92 MiB |       1.61 Mbps |
| 3840×2160 `testsrc2`                                 | 1280×720 | 1.21 MiB |       1.02 Mbps |
| 1280×720 纯色 / SMPTE 色条                               | 1280×720 | 0.22 MiB |   **0.18 Mbps** |

读数边界：

1. La Paloma 约 0.4 Mbps **不能**代表 720p 培训课；源高度低于 720 时学员一直看低分辨率。
2. 同样 720p，画面可压程度比源像素影响更大：静止约 0.2 Mbps 量级，复杂图案约 3 Mbps 量级。口播/PPT 预期落在两者之间；实拍运动可能达到或超过
   2.95 Mbps。
3. `testsrc2` 从 1080p/4K 缩小后码率低于「原生 720p 复杂图案」，是合成图案被低通滤波；真实摄像头 1080p 缩到 720p，一般接近同场景原生
   720p。
4. 部署规划里的 2.0～2.5 Mbps/路是桌面推演假说，与本次 720p 复杂图案 2.95 Mbps 同一量级，不能替代客户课件「体积 ÷
   时长」。容量公式与问卷见
   [customer-environment-survey.md](./customer-environment-survey.md) §4.1；转码规格见
   [asset-transcode-design.md](../architecture/asset-transcode-design.md) §D3.2。

单人在线看 720p 的网络保障按 **3 Mbps（兆比特/秒）** 预留，即 2.5 × 1.2；换算为 **375 KB/s**（3÷8=0.375 MB/s）。不要写成 3
MB/s（那是 24 Mbps），也不要写成 375 kb/s（那是 0.375 Mbps）。这是规划假说，不是恒定码率承诺。

## 5. qpdf 检查

在执行文档任务的后端主机或容器安装 qpdf。Arch Linux/CachyOS 使用 `pacman -S qpdf`；Debian/Ubuntu 使用
`apt-get install qpdf`。部署前执行：

```text
qpdf --version
qpdf --linearize input.pdf output.pdf
qpdf --check output.pdf
qpdf --check-linearization output.pdf
```

原生 PDF 创建素材后会进入 `DOCUMENT_PREVIEW/PENDING`，完成线性化、完整性和页数一致性校验后才进入 `SUCCEEDED`。Office 文档在
OnlyOffice 返回 PDF 后执行同一流程。源文件始终保留，在线预览只使用当前有效 `PREVIEW` 派生文件。

## 6. 网关限制

分片接口仍经过后端，Nginx/网关单请求体上限应不小于分片大小，建议设置为 12MB；请求超时至少 120 秒。完整 3GB 文件不会经过单个
HTTP 请求。

PDF 预览的多条 Range/206 要真正并行，浏览器到网关需要 HTTP/2（HTTPS，`listen 443 ssl;` + Nginx 1.25.1+ 的 `http2 on;`）。
二进制必须包含 `--with-http_ssl_module` 和 `--with-http_v2_module`。反代上游可继续 HTTP/1.1，但须透传 `Range` /
`Authorization`，且不要对 `application/pdf` 做 gzip。开发环境经 Vite HTTP 代理时不会出现 `h2`。编译参数、运维机现状和验证方式见
`docs/architecture/pdf-progressive-preview-optimization.md` §12。

外部路径：

```text
POST   /api/system/files/multipart/init
POST   /api/system/files/multipart/{uploadId}/parts/{partNumber}
POST   /api/system/files/multipart/{uploadId}/complete
DELETE /api/system/files/multipart/{uploadId}
```

## 7. 启用检查

1. 应用使用 `STORAGE_TYPE=minio` 正常启动。
2. 使用具有 `sys:file:upload` 权限的账号初始化上传。
3. 上传至少两个分片，中断后重新 init，确认返回已上传分片号。
4. complete 返回 `fileInfoId`，MinIO 中出现 `source/yyyy/MM/<uuid>.<ext>`。
5. 数据库会话状态为 `COMPLETED`，文件大小和 SHA-256 与客户端一致。
6. 取消上传后 MinIO 不再保留 incomplete upload；超过 24 小时的会话由清理任务中止。
7. 上传短视频并创建 VIDEO 素材，确认 Job 依次进入 PENDING、RUNNING、SUCCEEDED。
8. MinIO 中出现 m3u8、ts 和 cover.jpg，素材 durationSeconds 与 ffprobe 结果误差不超过 1 秒。
9. 上传音频并确认 MinIO 中出现 m3u8 与 ts（无封面）、素材 durationSeconds 与 ffprobe 误差不超过 1 秒，play-info 返回
   `format=hls`；错误源文件应进入 FAILED 且 failureReason 不包含密钥或签名参数。无 HLS 的音频 play-info 返回 `original`。
10. 使用含中文的 DOCX/PPTX 确认生成 PDF、中文无方框且 pageCount 正确；同一个内部源文件令牌第二次访问必须返回 404。
11. 原生 PDF 创建后产生 `DOCUMENT_PREVIEW` Job，成功产物通过 `qpdf --check-linearization`；XLS/XLSX 不产生 Job，也不虚构
    pageCount。
12. 使用空用户密码且禁止修改的 PDF 验证 SOURCE 仍加密、PREVIEW 不加密且页数一致；使用需要非空打开密码的 PDF 验证任务安全失败，
    日志与 failureReason 不包含密码、Authorization、Token 或签名查询参数。

切回 `STORAGE_TYPE=local` 只影响后续新上传，不会迁移或恢复已经存入 MinIO 的历史对象。
