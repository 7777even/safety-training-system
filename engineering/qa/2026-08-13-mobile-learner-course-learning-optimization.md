# PC 与移动端课程学习优化 QA 记录

## 素材预览故障补验

- 封面、图片、PDF、音频统一通过带 Bearer 的二进制请求生成 Blob URL，避免原生资源请求产生 401。
- PDF Blob 明确设置 `application/pdf`，H5 使用 iframe 预览；Office/PPTX 不再把原始二进制当文本渲染，改走受控打开/下载降级。
- 音频增加播放进度、播放状态和暂停/完成事件；图片、PDF、富文本增加放大或全屏阅读入口。
- 移动端素材回归测试 13/13、`vue-tsc`、定向 ESLint、H5 build、`git diff --check` 通过。

## 本次素材预览故障回归

- 复现：移动端素材文件请求缺少 Bearer，且切换素材时 uni-app H5 video ref 不保证是原生 HTMLVideoElement。
- 修复：受保护文件统一以 `arraybuffer` 请求并生成 H5 Blob URL；媒体清理增加能力检查；HLS 继续使用带 Token 的分片请求。
- 回归：移动端定向测试更新为 12/12，通过；定向 ESLint、`vue-tsc`、H5 build 通过。

## 范围

对应 OpenSpec Change：`2026-08-12-implement-mobile-learner-course-learning` 的 Task 5～7。

本次确认 `QUESTION_BANK`、`PRACTICE`、`EXAM` 课程内容项继续延期，不在课程学习页接入；完成 PC/移动端播放器、真实接口开关和有效学习时长优化。

## 自动化验证

| 验证项 | 结果 |
| --- | --- |
| 移动端课程定向测试 | 通过，11/11 |
| PC 端课程定向测试 | 通过，10/10 |
| 移动端 `vue-tsc` | 通过 |
| 移动端目标文件 ESLint | 通过 |
| 移动端 H5 build | 通过 |
| PC 端 `vue-tsc` | 通过 |
| PC 端目标文件 ESLint | 通过 |
| PC 端 build | 通过 |
| `git diff --check` | 通过；仅有现有 LF/CRLF 提示 |

## 已验证实现

- PC 与移动端课程、素材和考试 API 均默认调用真实接口，只有显式 `VITE_USE_API_MOCK=true` 才启用 Mock。
- PC 使用已有 HLS.js；移动 H5 新增 HLS.js，并为 HLS 分片请求携带 Bearer Token。
- 视频/音频仅在真实播放期间累计有效学习秒数；普通素材按页面可见停留累计。
- 暂停、页面隐藏、切后台和离开均停止累计；检查点统一为 30 秒，完成/离开会补报。
- 切换素材会销毁旧播放器和音频上下文，视频/音频断点通过内容项 key 重新初始化。
- PDF、Office 和原始格式保留受控预览/下载降级；PC 已使用现有 PDF/Office 预览组件。
- 课程考试、题库练习和专项练习显示“暂未开放”，未调用考试启动接口。

## 待真实环境验收

- 当前没有可用的真实学员 Token，因此无法验证素材接口 `asset:asset:list` 对学员角色的实际授权结果。
- 当前线上地址请求无法取得 HTTP 响应，无法完成 401/403、跨机构素材隔离、真实 HLS 分片和断点回读验证。
- 浏览器媒体播放、移动浏览器 PDF/Office 降级和真实页面隐藏行为仍需在浏览器环境补验。

## Review

## 本次 H5 预览与视频回归

- H5 的 PDF、PPTX、图片统一通过受控二进制 Blob 交给 `open-file-viewer`，并保留放大预览工具栏。
- uni-app App、企业微信等非 H5 环境保留受控打开/下载降级，不依赖浏览器专属 viewer。
- HLS 视频解析 uni-app video ref 的真实 `HTMLVideoElement`，并在 manifest、分片请求中统一注入 Bearer Token；切换素材时销毁旧播放器。
- 本次回归：移动端定向测试 13/13、`vue-tsc`、目标 ESLint、H5 build、`git diff --check` 均通过。
- 受限项：当前没有可用的真实学员 Token，尚未在真实登录态完成 HLS `.ts` 分片和企业微信容器实机验收。

- 规格符合性：通过。题库/考试/练习按确认结果延期；PC 与移动端计时、HLS、降级和真实接口开关已覆盖 Task 5～7。
- 代码质量：通过。未新增后端 API 或数据库结构；新增依赖仅为已确认的移动 H5 `hls.js`。
