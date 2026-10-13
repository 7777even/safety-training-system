# Mock 目录

仅在 API Contract 确认后创建 Mock；字段、状态码和错误场景必须与契约一致。Mock 文件必须标记 `TODO replace real api`。

当前素材库和课程管理默认使用浏览器内存 Mock：

- `asset.ts`：素材、文件上传关联与转码任务。
- `course-foundation.ts`：分类、机构设置和课程草稿。
- `course-operations.ts`：课程编排、版本、开课、报名和管理员学习详情。

Mock 数据仅在当前页面会话内有效，刷新页面后恢复种子数据。后端接口完成后设置 `VITE_USE_API_MOCK=false` 可切回保留的真实请求分支，联调通过后应删除 Mock 文件和 API 中的 Mock 分支。
