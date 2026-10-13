# Tasks：移动端学员课程学习

## Task 1：完成移动端课程入口与课程学习详情

- 状态：实现完成，视觉验收待浏览器环境
- Owner：fxw11
- 输入：现有学员课程列表 API、课程详情 API、课程任务快照 API、移动端页面骨架和截图参考
- 输出：移动端课程列表与课程学习详情页，包含课程级摘要、章节目录、学习状态、空态/加载态/错误态和移动端视觉适配
- 前后端影响：仅前端；复用现有 API Contract，不修改后端
- API 依赖：`GET /api/course/learning/my-enrollments`、`GET /api/course/learning/enrollments/{enrollmentId}`、`GET /api/course/learning/enrollments/{enrollmentId}/tasks`
- 数据影响：只读课程报名、课程详情和学习任务快照
- 验收标准：移动端课程列表可展示真实课程状态和进度；进入详情后可按章节查看内容项；窄屏下无横向溢出；支持加载、空数据、接口失败和返回操作
- 测试方式：移动端定向测试、typecheck、lint、H5 build、截图/浏览器视觉检查

## Task 2：完成移动端多格式素材预览与降级入口

- 状态：实现完成，视觉验收待浏览器环境
- Owner：fxw11
- 输入：PC 端素材预览经验、移动端 uni-app 能力、素材详情与 play-info/file Contract
- 输出：移动端素材预览区域，支持视频、音频、图片、PDF、富文本、Office、外链和直播的预览或受控打开/下载降级
- 前后端影响：仅前端；复用现有 API Contract，不修改后端
- API 依赖：`GET /api/asset/assets/{assetId}`、`GET /api/asset/assets/{assetId}/play-info`、`GET /api/asset/files/{assetId}/{fileInfoId}`
- 数据影响：只读素材详情和受控文件访问信息
- 验收标准：支持格式按策略展示；不支持或失败时显示明确原因和受控入口；不直接拼接未授权文件 URL；H5 常见移动浏览器下主要格式可操作
- 测试方式：格式分支定向测试、typecheck、lint、H5 build、浏览器手动检查视频/音频/PDF/图片/Office 降级状态

## Task 3：完成移动端学习断点与会话上报闭环

- 状态：实现完成，自动化验证通过，浏览器与真实接口验收待环境
- Owner：fxw11
- 输入：现有 `POST /api/course/learning/sessions` Contract、PC 端会话上报逻辑和移动端播放器事件
- 输出：内容项进入、切换、离开、播放进度和完成事件上报；恢复视频/音频断点；考试内容项进入现有移动端考试页
- 前后端影响：仅前端；复用现有 API Contract，不修改后端
- API 依赖：`POST /api/course/learning/sessions`、`POST /api/course/learning/enrollments/{enrollmentId}/items/{itemId}/exam-attempts`
- 数据影响：写入现有学习会话事实，不新增字段或表
- 验收标准：进入/切换/离开/完成均触发符合 Contract 的会话上报；重复上报带递增 trace 序号；视频/音频能够恢复后端断点；上报失败不阻塞继续学习；考试内容项可进入移动端考试页
- 测试方式：会话上报单元/定向测试、页面生命周期测试、typecheck、lint、H5 build、浏览器断点与离开流程检查

## Task 4：移动端课程学习端到端验收

- 状态：验收记录完成，浏览器与真实接口验证待环境
- Owner：fxw11
- 输入：Task 1～3 的实现、真实 API Contract、移动端 H5 构建产物
- 输出：课程列表到素材学习再到进度回读的移动端纵向闭环验收记录
- 前后端影响：仅前端；前端以现有 API Contract 为准
- API 依赖：Task 1～3 所列接口
- 数据影响：验证现有课程报名、素材和学习会话事实，不修改数据结构
- 验收标准：学员可从课程列表进入课程、切换内容、预览/降级访问素材、离开后再次进入并看到最新进度；异常路径和无权限资源不越权；OpenSpec 验收证据完整
- 测试方式：移动端定向测试、typecheck、lint、H5 build、浏览器视觉与交互检查、QA 记录、双轴 Review

## Task 5：统一 PC 与移动端真实接口和播放器兼容策略

- 状态：实现完成，自动化验证通过
- Owner：fxw11
- 输入：PC/移动端现有课程学习页面、素材播放 Contract、线上 API 配置
- 输出：默认真实接口、PC/移动端 HLS 播放、PDF/Office/原始文件降级、播放器切换清理和受控地址兼容
- 前后端影响：仅前端；新增移动端 HLS 依赖，不修改后端 API
- API 依赖：现有课程学习与素材 `play-info/file` Contract
- 数据影响：不新增字段或表
- 验收标准：PC/移动端默认真实接口；HLS 在支持 HLS.js 的 H5 浏览器可播放并携带 Bearer；切换素材不会继续播放旧媒体；不支持格式提供受控入口
- 测试方式：前端定向测试、依赖安装检查、typecheck、lint、PC build、移动 H5 build

## Task 6：统一 PC 与移动端有效学习时长口径

- 状态：实现完成，自动化验证通过
- Owner：fxw11
- 输入：现有学习会话 Contract、PC/移动端播放器事件和页面生命周期
- 输出：媒体真实播放计时、非媒体可见停留计时、暂停/切后台/隐藏停止、30 秒检查点上报
- 前后端影响：仅前端；复用现有 `validStudySeconds` 字段
- API 依赖：`POST /api/course/learning/sessions`
- 数据影响：写入现有学习会话事实，不修改数据库
- 验收标准：暂停和隐藏期间不增加有效秒数；恢复后继续计时；离开和完成补报；检查点不短于 30 秒；两端行为一致
- 测试方式：计时器与生命周期定向测试、typecheck、lint、PC build、移动 H5 build

## Task 7：保留课程考试类内容项延期边界并完成权限验收准备

- 状态：实现完成
- Owner：fxw11
- 输入：已确认的课程路线图、课程/素材 OpenAPI、真实学员账号验收条件
- 输出：明确暂不接入 `QUESTION_BANK`、`PRACTICE`、`EXAM`；整理素材 401/403、跨机构资源和断点回读验收项
- 前后端影响：仅前端文档与验收记录，不实现题库/考试/练习接入
- API 依赖：现有 authenticated 课程接口与素材权限接口
- 数据影响：不修改数据
- 验收标准：课程学习页面不误把延期内容当素材学习；QA 记录列出真实账号权限、无权限素材、断点回读和跨机构资源验证步骤
- 测试方式：规格检查、前端定向测试、QA/Retro 记录
