# MyClaw Web

Vue 3 + TypeScript + Vite 前端，独立的对话工作台，不依赖 Maven 模块。

## 运行

需要 Node.js 22.12+（或 20.19+），版本要求参见 https://vite.dev/guide/ 。

```powershell
cd myclaw-web
npm.cmd install
npm.cmd run dev
```

默认 http://localhost:5173 。Windows 使用 `npm.cmd` 可绕开 PowerShell 的 npm.ps1 执行策略限制。

```powershell
npm.cmd run build     # 先跑 vue-tsc 类型检查，再产出 dist/
npm.cmd run preview
```

## 目录结构

```
src/
├── api/                    # 后端接口，按职责拆分，不掺 UI 概念
│   ├── types.ts            # 线协议类型（Message / ChatResponse / SessionDetail…）
│   ├── http.ts             # ChatApiError、错误解析、通用请求工具
│   ├── sse.ts              # SSE 字节流 → 事件帧
│   ├── chat.ts             # 流式对话与取消
│   ├── sessions.ts         # 会话 CRUD
│   └── index.ts            # 统一出口
├── composables/            # 业务状态，App.vue 从这里取所有数据
│   ├── useSettings.ts      # 演示模式、接口路径、设置弹窗
│   ├── useTheme.ts         # 明暗主题
│   ├── useSessions.ts      # 列表、搜索、选中、增删改
│   ├── useChat.ts          # 发送、流式追加、停止、超时
│   └── useWorkspace.ts     # 编排层，把上面四个串起来
├── components/
│   ├── layout/             # AppSidebar、AppHeader
│   ├── sidebar/            # SessionItem
│   ├── chat/               # WelcomePanel、MessageList、MessageItem、ToolCallPanel、ChatComposer
│   ├── settings/           # SettingsModal
│   └── common/             # IconButton
├── styles/
│   ├── tokens.css          # 设计令牌 + 明暗主题变量
│   ├── base.css            # 全局重置与基础元素
│   ├── markdown.css        # v-html 注入的回答排版（必须全局）
│   └── index.css           # 聚合入口
├── types/index.ts          # 前端视图模型
├── utils/                  # markdown 渲染、错误文案、格式化、localStorage 包装
├── App.vue                 # 只做布局编排，不写业务逻辑
└── main.ts
```

### 分层约定

- **组件不直接调接口**，只接收 props、抛出事件。
- **业务逻辑只写在 composables 里**，`App.vue` 负责把它们接到组件上。
- **颜色只引用 `styles/tokens.css` 的变量**，不要在组件里写死色值，否则切主题会漏。
- **`markdown.css` 必须保持全局**：`v-html` 注入的节点不带 scoped 属性，写进 `<style scoped>` 不会生效。

## 对接后端

默认是演示模式（本地预设回答，不调用模型）。关闭演示模式后走真实接口。

### 流式对话

```http
POST {接口路径}/stream
Content-Type: application/json
Accept: text/event-stream

{"requestId":"请求 UUID","sessionId":"会话 UUID","messages":[{"role":"user","content":"你好"}]}
```

响应为 SSE，支持三种事件：

```
event: delta
data: {"content":"增量文本"}

event: complete
data: {"content":"完整回答","iterations":2,"toolCalls":1,"totalTokens":320,"durationMillis":1530,"tools":[]}

event: error
data: {"code":"MODEL_TIMEOUT","message":"...","retryable":true}
```

`requestId` 由前端生成，用于取消。

### 取消请求

```http
DELETE /api/requests/{requestId}
```

返回 404 视为请求已结束，不算失败。

### 会话管理

```http
GET    /api/sessions                  # 列表
GET    /api/sessions/{id}             # 详情（含消息）
POST   /api/sessions                  # {"id":"UUID","title":"标题"}
PATCH  /api/sessions/{id}             # {"title":"新标题"}
DELETE /api/sessions/{id}             # 删除会话及其消息
DELETE /api/sessions/{id}/messages    # 只清空消息
```

详情里 `status` 不等于 `COMPLETED` 的消息不会进入前端上下文，避免把半截回答带进下一轮。

## 开发代理

开发服务器把 `/api` 代理到 `http://localhost:19090` 并保留路径。改目标时把 `.env.example` 复制成 `.env.local`，修改 `API_PROXY_TARGET` 后重启。生产部署需自行为 `/api` 配置反向代理；Vite 开发代理不会进入构建产物。

不要把模型 API Key 放进前端。

## 行为说明

- 设置（运行模式、接口路径）和主题保存在 localStorage，刷新后保留。
- 请求超过 10 分钟自动停止并保留已收到的内容。
- 停止生成会同时中断浏览器等待并通知服务端取消。
- 主题默认跟随系统 `prefers-color-scheme`，可在顶栏手动切换。
