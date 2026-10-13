# 移动端协作规范

## 技术与入口

移动端采用 uni-app + Vue 3 + TypeScript，面向移动 H5、App 和企业微信入口预留适配空间。

## 初期功能

首页、课程学习、考试答题、个人中心。

## 实施边界

- App 和企业微信的登录、消息、原生能力等具体细节尚未确认，必须标记“暂定”；只预留边界，不提前过度实现。
- 围绕移动任务、触控和窄屏信息层级设计，禁止复制 PC 页面结构、导航层级或密集表格。
- API、Mock、权限展示和契约规则遵从 `frontend/AGENTS.md`。

## 工程实现规范

- 使用 pnpm workspace 管理依赖，引用 `@safety/shared` 必须使用 `workspace:*`；禁止 npm/yarn。
- 使用 uni-app 的页面机制维护 `pages.json`，不得强行使用 Vue Router；页面放入 `pages` 下对应的移动业务目录。
- Vue 文件使用 `<script setup lang="ts">`、Composition API 和 scoped Less；组件、页面使用 PascalCase，目录使用 kebab-case。
- 移动端不得使用 Element Plus；基础样式统一使用 Less，优先保证触控、小屏、弱网与移动交互体验。
- `api/request.ts` 只能封装 `uni.request` 的通用能力；真实接口必须先更新 `docs/api`，不得自行定义业务字段、错误码或权限语义。
- Pinia 用于跨页面复杂状态，user store 仅保留 token、userInfo、roles 基础结构。EventBus 仅按需用于轻量事件，不传递业务状态。
- `constants` 管理常量，`libs` 管理通用工具，`types` 管理 TypeScript 类型，`mock` 仅存放与契约一致的 Mock；禁止魔法值与复制 PC 页面结构。
