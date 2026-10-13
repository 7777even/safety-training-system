# Figma 页面结构

文件名：`Safety Training System UI`

```text
Safety Training System UI
├── 00 Design System
│   ├── Foundations（颜色、字体、间距、圆角、阴影）
│   ├── Button
│   ├── Card & Tag
│   ├── Form
│   ├── Table
│   └── Empty State
├── 01 Web
│   ├── Login
│   ├── Portal
│   └── Admin Dashboard
├── 02 Mobile
│   ├── Home
│   ├── Course
│   ├── Exam（目录预留）
│   └── Profile
└── 03 Components
    └── 可复用组件总览
```

## 首期交付页面

| 端 | 页面 | 状态 |
| --- | --- | --- |
| Web | Login | 设计 |
| Web | Portal | 设计 |
| Web | Admin Dashboard | 设计 |
| Mobile | Home | 设计 |
| Mobile | Course | 设计 |
| Mobile | Profile | 设计 |
| Mobile | Exam | 仅预留目录 |

## 后续实现映射

- `01 Web/Login` 对应 `frontend/pc-web` 的认证页面。
- `01 Web/Portal` 与 `01 Web/Admin Dashboard` 分别对应 `views/portal`、`views/admin`。
- `02 Mobile` 页面对应 `frontend/mobile-app/pages` 的任务域目录。
- 实现前应确认 Figma 页面、组件状态和 `docs/api/` 中已确认的接口契约；设计中的示例数据不可直接作为接口字段。
