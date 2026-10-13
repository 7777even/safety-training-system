# pdf-progressive-viewer Specification

## Purpose

PC Web 与移动 H5 对受控大 PDF PREVIEW 按页渐进加载与单 Canvas 渲染；生成链在 qpdf 线性化前将 Page Tree
规范为有界层级，使 PDF.js Range 初始化不必读取整份文件。

## Requirements

### Requirement: PDF PREVIEW 的 Page Tree 必须保持有界

系统生成 PDF PREVIEW 时，MUST 在 qpdf 线性化前将扁平 Page Tree 规范化为有界层级。每个 `/Pages` 节点的直接 `/Kids` MUST NOT
超过 32；页面数量超过单层容量时 MUST 递归增加中间 `/Pages` 节点。规范化 MUST NOT 修改 SOURCE、页面顺序或页数。

#### Scenario：生成 648 页扁平 PDF 的 PREVIEW

- **WHEN** 输入 PDF 的根 `/Pages` 直接包含 648 个页面引用
- **THEN** 生成链先创建中间 `/Pages` 节点，再执行 qpdf 线性化
- **AND** 每个节点的 `/Parent`、`/Count` 和 `/Kids` 保持一致
- **AND** 最终 PREVIEW 通过语法、线性化、页数和结构校验

#### Scenario：Page Tree 规范化失败

- **WHEN** 页面引用、可继承属性或输出校验失败
- **THEN** 系统不得上传或登记半成品 PREVIEW
- **AND** 清理本次生成的中间文件
- **AND** SOURCE 保持不变

### Requirement: 受控 PDF 必须按页渐进加载

PC Web 与移动 H5 在获得有权访问的 PDF play-info 后，MUST 使用受控 URL 和当前 Bearer 凭证通过 PDF.js 读取文件，MUST 保持
HTTP Range 可用，MUST NOT 在显示目标页前将完整文件读取为 Blob 或 ArrayBuffer。

#### Scenario：打开大 PDF

- **WHEN** 用户打开一个包含数百页的线性化 PDF PREVIEW
- **THEN** 前端只初始化 PDF 文档并读取目标页
- **AND** MUST NOT 为获取布局而遍历调用所有页面的 `getPage`
- **AND** 当前页所需 Range 可以并行发出，以重叠往返时间
- **AND** MUST NOT 在用户未翻页且当前页已停止请求时持续预取后续页面
- **AND** 当前页滚到顶或底后，滚轮继续翻页时仍只渲染目标页，不得把后续页面排进 DOM
- **AND** 默认预览必须按预览区宽高适配完整一页，不得只显示不足一页的局部
- **AND** 学员学习页改为连续平铺时，仍不得为获取布局而遍历全部页面，MUST 用首页尺寸估算总高度并只渲染视口窗口

#### Scenario：读取受保护 PDF

- **WHEN** PDF URL 为受控文件代理地址
- **THEN** PDF.js 请求携带当前 Bearer Token
- **AND** Token 不出现在 URL、日志或 DOM 属性中
- **AND** 合法 Range 请求由既有文件代理返回精确的部分内容

### Requirement: PDF 页面渲染资源必须保持有界

PC Web 与移动 H5 MUST 按场景选择阅读方式，且页面总数增加时活动 Canvas、页面 DOM、事件监听和布局计算 MUST
保持常量级，不得按文档总页数创建渲染节点或扫描全部页面。

- 素材库和管理端预览 MUST 使用分页阅读，只保留当前页 Canvas。
- 学员学习页 MUST 使用连续平铺：页面按宽度适配后自上而下排列，文档有多长学习页就有多长，用户滚动整页阅读；MUST
  只挂载当前视口附近的有限页面窗口，MUST 按页码顺序加载可见页，MUST NOT 把全部页面写入 DOM。

#### Scenario：查看 648 页 PDF

- **WHEN** 文档加载完成并显示任意一页
- **THEN** 分页预览只保留当前渲染所需的 Canvas 和状态节点
- **AND** 学习页连续平铺只保留视口附近有限个页面槽位
- **AND** 不创建 648 个页面 wrapper
- **AND** 离开视口或切换页面时释放或取消旧页面渲染资源

#### Scenario：快速跳页

- **WHEN** 用户连续输入或点击多个目标页
- **THEN** 前端取消过期渲染任务
- **AND** 只允许最后一次目标页结果更新界面和学习页码

#### Scenario：学员学习页连续平铺阅读 PDF

- **WHEN** 学员在课程学习页打开 PDF 素材
- **THEN** 页面按预览区宽度适配并纵向平铺
- **AND** 用户通过滚动而不是翻页阅读后续页面
- **AND** 当前页由视口位置更新，并继续回传既有学习页码字段

### Requirement: 课程 PDF 页码语义必须保持稳定

PC 学员课程 PDF 预览 MUST 继续支持恢复上次成功阅读页、报告总页数和报告当前页。当前页只可在目标页成功显示后更新，不得因输入、加载失败或过期异步结果写入错误断点。

#### Scenario：恢复课程 PDF 断点

- **WHEN** 学习任务提供合法 `initialPageNo`
- **THEN** PDF 预览器首先显示该页
- **AND** 页面成功显示后回传相同页码
- **AND** 后续学习会话继续使用既有页码字段上报

#### Scenario：断点超出文档范围

- **WHEN** `initialPageNo` 小于 1 或大于当前文档总页数
- **THEN** 前端将其裁剪到合法范围
- **AND** 不发起不存在页面的读取请求

### Requirement: PDF 预览失败必须安全降级

PC Web 与移动 H5 MUST 区分文档加载、密码、网络和页面渲染失败，展示稳定中文状态，并提供既有受控打开或下载入口。失败处理 MUST
NOT 暴露对象存储地址，MUST NOT 静默改为无鉴权访问或完整文件预下载。

#### Scenario：PDF 无法加载

- **WHEN** 受控请求返回无权限、Range 错误、损坏文件或渲染异常
- **THEN** 页面停止相关加载和渲染任务
- **AND** 展示明确失败状态与受控降级操作
- **AND** 切换素材或离开页面后不再继续发送该 PDF 的网络请求
