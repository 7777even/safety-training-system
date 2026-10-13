# 设计：移动端学员课程学习

## 页面结构

### 课程列表页

- 复用现有 `GET /api/course/learning/my-enrollments`。
- 使用移动端课程卡片展示课程名称、开课名称、学习状态、进度和继续学习入口。
- 课程卡片点击进入 `/pages/course/detail?enrollmentId={enrollmentId}`。
- 不使用 PC 表格、桌面端侧栏或密集筛选布局。

### 课程学习详情页

- 顶部展示返回入口、课程名称、课程级进度和必修/选修摘要。
- 中部展示当前内容项预览区域；在窄屏下预览区域优先保证可操作尺寸。
- 下方展示课程目录卡片，按章节分组，每个内容项显示类型、标题、学习状态和断点信息。
- 底部展示课程简介与课程附件区域。
- 内容项切换时保留当前页面滚动位置的合理行为，并先上报上一个内容项的会话。

## 数据与接口

仅使用已确认 API Contract：

- `GET /api/course/learning/my-enrollments`
- `GET /api/course/learning/enrollments/{enrollmentId}`
- `GET /api/course/learning/enrollments/{enrollmentId}/tasks`
- `GET /api/asset/assets/{assetId}`
- `GET /api/asset/assets/{assetId}/play-info`
- `GET /api/asset/files/{assetId}/{fileInfoId}`
- `POST /api/course/learning/sessions`
- `POST /api/course/learning/enrollments/{enrollmentId}/items/{itemId}/exam-attempts`

前端不得自行扩展字段、错误码或权限语义。真实接口接入仍以 `docs/api/` 为准；已有 Mock 只用于移动端定向测试和本地预览，联调时遵循现有 Mock First 规则。

## 素材预览策略

| 素材类型 | 移动端策略 | 断点上报 |
| --- | --- | --- |
| 视频 | 使用 uni-app 原生视频能力，支持受控 URL、播放进度和结束事件 | 媒体秒数 |
| 音频 | 使用 uni-app 音频能力，支持播放进度和结束事件 | 媒体秒数 |
| 图片 | 使用图片预览能力，适配长图和窄屏 | 以会话时长为主 |
| PDF | H5 优先内嵌或受控打开；不支持时提供受控下载/打开入口 | 以会话时长为主 |
| 富文本 | 以安全的课程富文本内容渲染 | 以会话时长为主 |
| Office | 不在移动端伪造 Office 查看器；使用受控打开或下载兜底 | 以会话时长为主 |
| 外部链接/直播 | 显示来源信息，通过受控入口打开 | 以会话时长为主 |
| 不支持或预览失败 | 展示原因和受控下载/打开入口 | 记录离开或失败前会话 |

移动端不直接拼接未授权文件 URL；文件访问统一通过现有文件接口或 `play-info` 返回结果。

## 学习会话

- 每个内容项创建独立 `clientTraceId`，附带递增序号保证重复上报幂等。
- `deviceType` 使用移动端现有 Contract 支持的 `H5` 值。
- 进入、切换、页面离开和素材完成时上报；定时上报周期统一为 30 秒，但不阻塞页面退出。
- 视频/音频上报媒体起止秒数；文档和图片按有效学习时长上报，断点字段按 Contract 允许范围发送。
- 上报失败只提示并允许继续学习；页面离开时执行一次尽力上报。

## 兼容性与降级

- 以 H5 为首要验证目标，同时保留 App 与企业微信入口的 uni-app 页面边界。
- 预览能力按平台能力检测，不依赖 PC Web 的 Vue 组件或 Element Plus。
- 播放器加载、自动播放被禁止、格式不支持、网络异常和受控地址失效均提供明确状态。
- 不将浏览器原生状态栏、设备外壳等截图内容实现为业务 UI。

## 风险与取舍

- Office 和部分 PDF 在不同移动浏览器内核中的嵌入行为不一致，因此采用“可预览则预览、否则受控打开/下载”的降级策略。
- 移动端原生播放器的事件模型与 PC 不同，断点恢复以可验证的 `timeupdate`、`ended` 和页面生命周期事件为准。
- 移动端 H5 引入 `hls.js` 播放 HLS；App/企业微信仍保留 uni-app 原生能力边界，具体原生差异暂定。
- `QUESTION_BANK`、`PRACTICE`、`EXAM` 课程内容项暂不接入，延后到题库/考试/练习 Change。
