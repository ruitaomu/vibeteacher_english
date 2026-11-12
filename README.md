# VibeTeacher English

基于 **Spring Boot + 微信小程序原生框架** 的英语学习助手，当前包含 **写作练习、阅读助手、学习进度、我的成就、词汇本** 五个核心页面。

项目采用前后端分离：
- **后端**：Spring Boot 3 + JPA + Flyway + H2/MySQL
- **前端**：微信小程序原生 JS / WXML / WXSS
- **AI 能力**：OpenAI 兼容接口
- **OCR**：Azure OCR
- **部署**：Azure App Service + GitHub Actions

## 当前功能

### 1. 写作练习
- 支持学习级别选择：
  - 中学 7-12 年级
  - 大学英语四级 / 六级
  - TOEFL 写作
  - GRE 写作
- 支持模型选择：`gpt-5.4-mini`、`gpt-5.4`
- 支持文本输入、拍照识别、相册导入 OCR
- 支持附加修改要求
- 支持“需要扩写”
- 返回内容包括：
  - 修正后的文本
  - 修改总结
  - 分项修改建议与原因
  - 词汇推荐
  - 新成就

### 2. 阅读助手
- 独立于写作练习的新 Tab
- 支持模型选择：`gpt-5.4-mini`、`gpt-5.4`
- 支持文本输入、拍照识别、相册导入 OCR
- 支持按学习级别（中学 7-12 年级、四级 / 六级、TOEFL、GRE）**获取推荐文章**并填入文本框：
  - 来源：Simple English Wikipedia、Wikinews、PLOS 期刊摘要（均为 Creative Commons 许可），以及 `reading.articles.rss-feeds` 中配置的 RSS 源
  - RSS 源只使用 feed 自身提供的标题、正文和链接，不抓取网页；启用前请确认该站点的使用条款
  - 难度用 Flesch-Kincaid 年级指数自动估算，仅供参考
  - 页面会展示标题、来源、许可和原文链接
  - “换一篇”会排除最近看过的文章（最多 30 篇），优先给出没看过的；没有其他可用文章时显示提示并保留当前内容
  - RSS 源会随机读取历史分页（WordPress 的 `?paged=N`，上限由 `reading.articles.rss-max-pages` 配置），避免只在最新十几条里打转
  - 没有难度完全匹配的文章时，会在难度最接近的几篇里随机选择
- 支持：
  - 全文整体翻译
  - 逐句翻译
  - 每句的重点单词、重点短语、典型句式提取
  - 单句“句子结构讲解”
- 重点单词 / 短语 / 典型句式可由用户**手动点击加入词汇本**

### 3. 学习进度
- 查看用户统计信息
- 查看连续打卡情况
- 查看历史写作记录

### 4. 我的成就
- 查看已获得成就
- 展示与提交、打卡、词汇使用相关的成长反馈

### 5. 词汇本
- 查看个人词汇积累
- 写作练习会自动加入 AI 推荐词汇
- 阅读助手中的学习点仅在用户点击时加入

## 小程序页面与 Tab

当前 `miniprogram/app.json` 中配置了 5 个页面：

| Tab | 路径 | 说明 |
|---|---|---|
| 写作练习 | `pages/index/index` | 英文写作输入、OCR、AI 批改 |
| 阅读助手 | `pages/reading/reading` | 阅读分析、逐句翻译、结构讲解 |
| 学习进度 | `pages/progress/progress` | 统计信息和历史记录 |
| 我的成就 | `pages/achievements/achievements` | 成就展示 |
| 词汇本 | `pages/vocabulary/vocabulary` | 个人词汇积累 |

## 主要后端接口

### 会话与登录

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/api/session` | 获取匿名会话令牌 |
| POST | `/api/auth/wechat` | 微信登录换取用户令牌 |

### 写作与 OCR

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/api/revise` | 写作练习智能批改 |
| POST | `/api/ocr` | 图片文字识别 |

### 阅读助手

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/api/reading/analyze` | 全文翻译 + 逐句翻译 + 学习点提取 |
| POST | `/api/reading/structure` | 单句句子结构讲解 |
| GET | `/api/reading/article?level=中学9年级` | 按学习级别拉取一篇推荐文章 |

推荐文章接口支持可重复的可选参数 `excludeUrl`（最近看过的文章链接，第一个为当前文章），用于换篇时避免重复；返回结果不缓存。

### 用户数据

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/user/stats` | 获取学习统计 |
| GET | `/api/user/revisions` | 获取历史写作记录 |
| GET | `/api/user/achievements` | 获取成就列表 |
| GET | `/api/user/vocabulary` | 获取词汇本 |
| GET | `/api/user/vocabulary/library` | 获取词汇本（兼容入口） |
| POST | `/api/user/vocabulary` | 写入词汇本 |
| POST | `/api/user/vocabulary/usage` | 记录词汇使用情况 |
| POST | `/api/user/submission` | 记录提交行为 |

## 认证方式

项目当前使用两种身份模式：

1. **匿名模式**
   - 小程序默认先调用 `/api/session`
   - 适合写作练习、阅读助手等无需持久化身份的场景

2. **微信登录模式**
   - 词汇本、学习进度、成就等持久化数据页面依赖微信登录
   - 登录成功后，后端将用户数据与 `userId` 绑定

## 运行方式

### 1. 启动后端

在项目根目录执行：

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

后端默认监听：

```text
http://127.0.0.1:8080
```

### 2. 启动小程序

用微信开发者工具导入 `miniprogram/` 目录。

当前小程序逻辑：
- 在 **开发者工具** 或 **develop 环境** 下，自动连接 `http://127.0.0.1:8080`
- 其他环境默认读取 `miniprogram/config.js` 中的生产地址：

```js
module.exports = {
  serverBase: 'https://vibeteacher.azurewebsites.net'
};
```

## 主要配置

### 后端默认配置

`backend/src/main/resources/application.yml` 当前关键配置：

- 端口：`8080`
- 默认数据源：H2 文件数据库
  - `jdbc:h2:file:./data/vibeteacher`
- `spring.jpa.hibernate.ddl-auto: update`
- `spring.flyway.enabled: true`
- 文件上传限制：`20MB`

### 常用环境变量

| 变量名 | 说明 |
|---|---|
| `SESSION_TOKEN_SECRET` | 会话令牌签名密钥 |
| `SPRING_DATASOURCE_URL` | 数据库连接地址 |
| `SPRING_DATASOURCE_USERNAME` | 数据库用户名 |
| `SPRING_DATASOURCE_PASSWORD` | 数据库密码 |
| `KEYVAULT_WECHAT_APP_ID_SECRET_NAME` | 微信 AppId 的 Key Vault Secret 名 |
| `KEYVAULT_WECHAT_APP_SECRET_SECRET_NAME` | 微信 AppSecret 的 Key Vault Secret 名 |

### Azure Key Vault 相关配置

当前项目通过 Key Vault 读取以下敏感配置：
- OpenAI API Key
- OpenAI Endpoint
- OCR API Key
- OCR Endpoint
- 微信 AppId / AppSecret
- MySQL 连接信息

## 技术栈

### 后端
- Java 17
- Spring Boot 3.2.5
- Spring Data JPA
- Flyway
- H2 / MySQL
- OpenAI Java SDK 4.16.1
- Azure Identity
- Azure Key Vault Secrets

### 小程序
- 微信小程序原生框架
- JavaScript
- WXML
- WXSS

## 仓库结构

```text
vibeteacher_web/
├─ backend/                 # Spring Boot 后端
│  ├─ src/main/java/com/vibeteacher/
│  │  ├─ api/               # 控制器
│  │  ├─ api/dto/           # DTO
│  │  ├─ service/           # 业务服务
│  │  ├─ security/          # 令牌逻辑
│  │  ├─ persistence/       # JPA 实体与仓库
│  │  └─ config/            # 配置类
│  └─ src/main/resources/
│     ├─ application.yml
│     └─ db/migration/
├─ miniprogram/             # 微信小程序
│  ├─ pages/
│  │  ├─ index/             # 写作练习
│  │  ├─ reading/           # 阅读助手
│  │  ├─ progress/          # 学习进度
│  │  ├─ achievements/      # 我的成就
│  │  └─ vocabulary/        # 词汇本
│  ├─ assets/icons/         # Tab 图标
│  ├─ app.js
│  ├─ app.json
│  └─ config.js
├─ data/                    # 本地数据文件
└─ .github/workflows/       # CI/CD
```

## 部署

当前仓库包含 GitHub Actions 工作流：

`/.github/workflows/azure-deploy.yml`

触发方式：
- push 到 `main`
- 手动触发 `workflow_dispatch`

工作流会：
1. 安装 JDK 17
2. 执行 `backend` 下 Maven 打包
3. 使用 Azure OIDC 登录
4. 部署 `backend/target/writing-assistant-1.0.0.jar` 到 Azure Web App

默认目标：
- Azure Web App：`vibeteacher`
- Resource Group：`vibeteacher-rg`

## 开发说明

- 写作练习和阅读助手都依赖后端会话令牌
- 词汇本、成就、学习进度依赖微信登录后的持久化身份
- 如果在微信开发者工具里联调微信登录失败，优先检查开发者工具登录态和微信配置
- OCR / OpenAI / 微信登录相关密钥不应写入前端代码
- 本机需要通过代理才能访问维基百科等文章来源时，后端 JVM 需加 `-Djava.net.useSystemProxies=true`（Java 默认不走 Windows 系统代理），否则推荐文章只剩可直连的来源

## 备注

如果你要继续扩展本项目，推荐优先从以下方向入手：
- 阅读助手结果的前端展示优化
- 词汇本手动管理能力（删除、分组、标记掌握）
- 更多考试场景与模型配置
- 后端接口测试与小程序端异常态补齐
