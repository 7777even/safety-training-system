# 培训资料范围收口与学员侧只读 Retro

- 起因：`2026-09-24-close-training-material-loop` 只做完了「管理端上传 + 受控读取」，
  留下三处与直觉相反的口径——(a) 奖惩佐证有两个上传口（奖惩记录的 `evidenceFileId` 与资料类型 `REWARD_PENALTY`），
  且档案奖惩表**没有佐证列**；(b) 档案取资料**完全不过滤 `status`**，管理员把资料停用后档案里照样出现；
  (c) 学员端**一个字都没有**，功能项 48「支持查看已上传的附件资料」属未覆盖需求。
- 处理：
  - 类型收口为 `GENERAL`/`IMAGE`/`VIDEO`/`EVALUATION`，历史 `REWARD_PENALTY` 资料经 `V20260924180000` **有损降级为 `GENERAL`**；
  - 档案三视角与归档素材登记统一追加 `status = 1`，统计随查询自然收敛，不写第二套算法；
  - 档案奖惩表新增「佐证」列，按 `training:reward:list` 门控，复用既有 `getTrainingRewardEvidence` + `AssetViewer`；
  - 新增学员侧只读端点（列表 + 受控文件），只本人、只 `GENERAL`、只启用，**响应不含 `fileInfoId`**；
  - PC 门户与移动端新增「培训资料」区块，**只预览不下载**；
  - **零权限变更**：沿用 `training:learner:task`，未把管理端 `training:attachment:list` 发给学员。
- 踩坑（都记进 skill 了）：
  1. **上传组件是「选文件 → 自动上传 → 点「完成」」三步**，`finish()` 才 `emit('success')` 回填 `fileInfoId`。
     只 `DOM.setFileInputFiles` 就点保存，服务端不会报错，前端只弹一句「请先上传资料文件。」——
     表现是**表单静默不落库**，查了半天才发现漏点「完成」。
  2. **移动端预览层 `z-index` 必须高于 `uni-page-head` 的 998**（我一开始写 30，标题被导航栏盖住）。
  3. **全局 `.el-card__body` 是纵向 flex**（`styles/element-plus.less:70`），
     门户资料卡片必须显式 `flex-direction: row`，否则资料行竖排居中。
  4. **移动端 `PdfPreview` 的 `layout="continuous"` 依赖窗口滚动**，放进固定全屏层后 page1 会「沉底」渲染到视口外；
     改 `page` 模式即可（组件内部有滚动载体兜底）。
  5. 学员侧**不下发文件名**，移动端只能**按字节头判定格式**（`%PDF` / `FFD8FF` / `89504E47` 等），
     与后端按内容分流保持一致；不要指望 `File.type`。
- 验证：后端真实编译 1033 源文件 + 定向零 DB **73/73**；Testcontainers MySQL IT **23/23**
  （Flyway 9 / 档案 6 / 资料 5 / 学员工作流 3）；PC & 移动端 typecheck、定向 ESLint、H5 build 全通过；
  PC 静态契约 12/12、移动端 6/6；`openspec validate --strict`、`git diff --check` 通过。
  真实登录态纵向验收：学员侧 13 组 HTTP 用例逐条命中（含 **md5 与源文件逐字节一致**）、
  两端页面渲染（移动端 PDF/图片预览、门户在线预览、档案奖惩佐证预览、工作台类型下拉只剩 4 项）、
  **管理端 UI 全链路 9 步**（上传 → 档案含 → 停用 → 档案不含 → 启用 → 档案含 → 删除）、
  学员端停用态页面补证（停用资料不出现 + 全不可见时不渲染区块）。
  证据 `engineering/qa/2026-09-24-close-training-material-scope-loop.md`。
- 后续（非阻塞）：
  - 移动端**原生分支**（`// #ifdef H5` 之外）未在真机验证；
  - 跨租户拒绝在真实环境只由 IT 覆盖（本环境单租户 `DEMO`）；
  - 历史 `REWARD_PENALTY` 资料降级为 `GENERAL` 是**有损**改写，回滚需按归档记录人工反推；
  - 学员端「按阶段重组培训内容 + 资料」（功能项 48 的另一半）另开 Change。
