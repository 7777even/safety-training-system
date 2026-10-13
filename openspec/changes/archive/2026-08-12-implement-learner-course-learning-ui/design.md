# 设计：学员课程学习界面最小实现

## 页面结构

- 课程列表页保留“我的课程”能力。
- 点击“开始学习”进入隐藏路由 `/portal/class/learning/:enrollmentId`。
- 学习页左侧为素材区，右侧为章节目录；移动端不在本 Change 改造。

## 数据流

1. 调用 `GET /api/course/learning/enrollments/{enrollmentId}/tasks` 获取课程快照、章节、内容项和进度。
2. 当前内容项为 `ASSET` 且存在 `assetId` 时，调用 `GET /api/asset/assets/{assetId}/play-info` 获取受控素材地址。
3. 使用 `POST /api/course/learning/sessions` 上报进入、切换、离开和完成事件。
4. `EXAM` 沿用课程考试启动接口，`LIVE` 展示快照中的直播信息。

## 学习会话

- 每个内容项创建一个客户端会话标识。
- 周期上报使用带序号的 `clientTraceId`，重复请求保持幂等。
- 上报当前素材的开始时间、结束时间、有效学习秒数、媒体秒数或文档页码。
- 页面离开和内容切换时执行最终上报；失败只提示，不阻塞页面离开。

## 后续优化记号

## 课程级详情与学习页信息区

- 复用 `GET /api/course/learning/enrollments/{enrollmentId}` 作为学习详情页课程级聚合数据入口。
- 响应增加 `course` 和 `downloadAssets`；课程字段来自学员已报名开课对应的已发布课程快照。
- `downloadAssets` 只返回附件素材引用，前端按 `assetId` 调用现有素材详情接口获取名称、文件信息和预览/下载状态。
- 学员端不调用后台课程详情和后台课程附件接口，不新增学员后台课程权限。
- 页面下方增加“课程简介”和“课程附件”Tab；附件下载继续使用素材模块的受控文件地址。

## 素材展示策略

- PDF、PPT/PPTX、Office、图片、文本复用既有 `AssetViewer` 与 `@open-file-viewer`。
- 视频、音频复用 `AssetViewer` 内部的受控媒体播放器，并传入后端断点。
- 富文本、链接、直播按资源详情契约展示。
- 不支持或预览失败时，由 `AssetViewer` 展示明确的受控下载入口；页面不直接暴露未授权文件地址。
- 学习页额外查询 `GET /api/asset/assets/{assetId}` 获取资源类型、名称、富文本和外链字段；文件类资源再查询既有 `play-info`。

- 独立实时进度/心跳接口。
- 更精确的防快进和有效学习时长校验。
- 断网恢复与异常关闭补报。
- 课程介绍、评论、笔记、评分统计。
- 倍速、清晰度切换、弹题和防录屏。
