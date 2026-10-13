# PC 学员课程学习最小闭环 QA

## 范围

- 左侧素材学习区、右侧章节目录。
- 复用 `AssetViewer`：文件预览、媒体播放、富文本、链接、直播和受控下载回退。
- 断点恢复、目录切换、离开和完成时的学习会话上报。

## 验证结果

- `node --test tests/learner-course-learning.test.mjs tests/portal-layout-routing.test.mjs`：通过。
- `pnpm exec vue-tsc --noEmit --ignoreDeprecations 5.0`：通过。
- `pnpm build`：通过；仅有既有 externalized module 与 chunk 体积提示。
- `git diff --check`：通过。
- 定向 ESLint：通过；全量 lint 仍有仓库既有问题，未作为本 Task 阻塞项。
- 默认 `VITE_USE_API_MOCK` 保持项目现状，真实接口联调需在验收环境关闭 Mock 后复核。

## 未完成验证

浏览器视觉核对因当前浏览器没有登录会话，被重定向到登录页，未据此宣称视觉验收通过。

## Review

- 已修正周期上报重复累计有效学习时长的问题，改为按检查点上报增量秒数。
- 已将视频断点传入既有 `AssetViewer`，由受控播放器在元数据加载后恢复位置。
- 未新增后端接口、权限语义或数据库结构。

## 本轮优化复核

- PDF 页码输入已取消，避免展示不能驱动 PDF 内部阅读器的伪控制；PDF 页码由 `AssetViewer` 工具栏管理，当前仍不作为学习完成判定。
- 图片和音频在固定素材区域内居中显示。
- 左侧素材区和右侧目录在 PC 端固定高度并各自处理溢出滚动。
- 学习上报成功后重新读取任务快照，目录完成标记与课程进度即时同步。
