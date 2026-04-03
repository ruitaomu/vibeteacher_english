# VibeTeacher 项目总结文档

## 📋 项目概览

**项目名称**: VibeTeacher  
**项目类型**: 英语写作学习助手（前后端分离架构）  
**目标用户**: 中学生（7-12年级）、大学生（四六级）、TOEFL/GRE 考生  

**核心功能**: 利用 AI 大模型对学生英文写作进行智能批改，提供修改建议、词汇优化、学习进度追踪等一站式学习服务。

---

## 🏗️ 技术架构

### 后端技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 3.2.5 |
| 语言 | Java | 17 |
| ORM | Spring Data JPA + Hibernate | - |
| 数据库 | MySQL（生产）/ H2（开发） | - |
| 数据库迁移 | Flyway | 10.17.3 |
| AI 集成 | OpenAI Java SDK | 4.16.1 |
| OCR | Azure Computer Vision | - |
| 密钥管理 | Azure Key Vault + Azure Identity | - |
| 认证 | 自定义 JWT（HMAC-SHA256） | - |
| 部署 | Azure App Service（Linux） | - |
| CI/CD | GitHub Actions | - |

### 前端技术栈

| 类别 | 技术 |
|------|------|
| 平台 | 微信小程序原生框架 |
| 语言 | JavaScript（原生） |
| 样式 | WXSS |
| 认证 | 匿名会话令牌 + 微信授权登录 |

---

## 📁 项目结构

```
vibeteacher_web/
├── README.md                          # 项目文档（部署指南、配置说明）
├── .gitignore                         # Git 忽略配置
│
├── backend/                           # Spring Boot 后端
│   ├── pom.xml                        # Maven 依赖配置
│   ├── mvnw / mvnw.cmd               # Maven Wrapper
│   ├── .mvn/                          # Maven 配置
│   │
│   ├── src/main/java/com/vibeteacher/
│   │   ├── Application.java           # Spring Boot 启动类
│   │   │
│   │   ├── api/                       # REST 控制器层
│   │   │   ├── RevisionController.java      # 写作批改 + 会话令牌
│   │   │   ├── OcrController.java           # OCR 图片识别
│   │   │   ├── WechatAuthController.java    # 微信登录
│   │   │   ├── UserProgressController.java  # 用户进度/成就/词汇
│   │   │   └── dto/                   # 数据传输对象
│   │   │       ├── RevisionRequest.java     # 批改请求
│   │   │       ├── RevisionResponse.java    # 批改响应
│   │   │       ├── VocabularyItem.java      # 词汇项
│   │   │       └── RevisionHistoryItem.java # 历史记录
│   │   │
│   │   ├── service/                   # 业务逻辑层
│   │   │   ├── OpenAIService.java           # AI 模型调用（含级别指导）
│   │   │   ├── OcrService.java              # Azure OCR 调用
│   │   │   ├── KeyVaultService.java         # Azure Key Vault 密钥管理
│   │   │   ├── UserProgressService.java     # 进度/成就/词汇管理
│   │   │   ├── RevisionPersistenceService.java  # 修改记录持久化
│   │   │   ├── WechatAuthService.java       # 微信 OAuth
│   │   │   └── WechatAuthException.java     # 微信认证异常
│   │   │
│   │   ├── security/
│   │   │   └── TokenService.java            # JWT 令牌签发/验证
│   │   │
│   │   ├── model/                     # 领域模型
│   │   │   ├── UserProgress.java
│   │   │   └── Achievement.java
│   │   │
│   │   ├── persistence/               # 数据访问层
│   │   │   ├── entity/                # JPA 实体
│   │   │   │   ├── UserProgressEntity.java
│   │   │   │   ├── UserVocabularyEntity.java
│   │   │   │   ├── UserAchievementEntity.java
│   │   │   │   ├── RevisionEntity.java
│   │   │   │   └── ErrorOccurrenceEntity.java
│   │   │   │
│   │   │   └── repo/                  # Spring Data Repository
│   │   │       ├── UserProgressRepository.java
│   │   │       ├── UserVocabularyRepository.java
│   │   │       ├── UserAchievementRepository.java
│   │   │       ├── RevisionRepository.java
│   │   │       └── ErrorOccurrenceRepository.java
│   │   │
│   │   └── config/                    # 配置类
│   │       ├── DataSourceConfig.java
│   │       ├── WebConfig.java
│   │       └── DatabaseSchemaInitializer.java
│   │
│   └── src/main/resources/
│       ├── application.yml            # 应用配置
│       └── db/migration/              # Flyway 数据库迁移脚本
│           ├── V1__init_user_progress_tables.sql
│           └── V2__add_revision_and_error_tables.sql
│
├── miniprogram/                       # 微信小程序前端
│   ├── app.js                         # 全局逻辑（认证、令牌管理）
│   ├── app.json                       # 页面路由 + TabBar 配置
│   ├── app.wxss                       # 全局样式
│   ├── project.config.json            # 小程序项目配置
│   ├── sitemap.json                   # 站点地图
│   │
│   ├── pages/                         # 页面
│   │   ├── index/                     # 写作练习主页
│   │   ├── progress/                  # 学习进度页
│   │   ├── achievements/              # 成就页
│   │   └── vocabulary/                # 词汇本页
│   │
│   ├── utils/
│   │   └── user.js                    # 用户工具函数
│   │
│   └── assets/                        # 图标资源
│
├── data/                              # 本地 H2 数据库文件目录（gitignore）
│
└── .github/
    └── workflows/
        └── azure-deploy.yml           # GitHub Actions CI/CD
```

---

## 🎯 核心功能模块

### 1. 写作批改（核心功能）

**API 端点**: `POST /api/revise`

**支持级别**:
- 中学：7-12 年级
- 大学：英语四级（CET-4）、英语六级（CET-6）
- 出国考试：TOEFL 写作、GRE 写作

**支持模型**: gpt-5.2、gpt-5.3

**返回内容**:
- 修正后的完整文本
- 修改摘要
- 问题列表（类型、原文、建议修改、原因说明）
- 词汇建议

**特性**:
- 支持附加要求（用户自定义要求）
- 支持自动扩写

---

### 2. OCR 图片文字识别

**API 端点**: `POST /api/ocr`

**功能**:
- 使用 Azure Computer Vision 识别图片中的英文文本
- 支持拍照识别和相册选择
- 适用于手写/印刷体英文作文识别

---

### 3. 用户认证系统

**双模式认证**:

| 模式 | 端点 | 说明 |
|------|------|------|
| 匿名模式 | `POST /api/session` | 获取临时会话令牌（JWT，1小时有效期） |
| 微信登录 | `POST /api/auth/wechat` | 通过微信 code 换取令牌 |

**令牌特性**:
- 自定义轻量 JWT 实现
- HMAC-SHA256 签名
- 1 小时 TTL
- 无需完整 Spring Security 链式过滤

---

### 4. 学习进度追踪

**API 端点**:
- `GET /api/user/stats` - 用户统计数据
- `GET /api/user/revisions` - 历史修改记录（分页）
- `GET /api/user/achievements` - 成就列表

**追踪指标**:
- 总提交次数
- 连续打卡天数
- 最长连续打卡天数
- 历史修改记录

---

### 5. 词汇本

**API 端点**: `GET/POST /api/user/vocabulary`

**功能**:
- 自动收录 AI 建议的词汇
- 统计建议词汇的使用情况
- 支持用户查看和管理个人词汇本

---

### 6. 成就系统

**9 种成就类型**:
1. 🎓 初来乍到 - 首次使用
2. 📝 十次达成 - 提交 10 次修改
3. 🔥 连签 7 天 - 连续打卡 7 天
4. 🔥 连签 15 天 - 连续打卡 15 天
5. 🔥 连签 30 天 - 连续打卡 30 天
6. 📚 学以致用 - 实践应用
7. 💡 词汇实践者 - 使用建议词汇5次
8. 💡 词汇达人 - 使用建议词汇10次
9. 💡 词汇大师 - 使用建议词汇20次

**机制**: 自动检测和授予

---

## 🗄️ 数据库设计

### 数据表结构

| 表名 | 说明 | 主要字段 |
|------|------|----------|
| `user_progress` | 用户进度 | 提交次数、连续天数、最长连续天数 |
| `user_vocabulary` | 用户词汇本 | 词汇、释义、使用次数 |
| `user_achievement` | 用户成就 | 成就类型、获得时间 |
| `revision_record` | 修改记录 | 原文、修改后文本、问题列表、时间戳 |
| `error_occurrence` | 错误类型记录 | 错误类型、出现次数 |

### 数据库迁移

- 使用 **Flyway** 管理数据库版本
- 迁移脚本位于 `src/main/resources/db/migration/`
- `V1__init_user_progress_tables.sql` - 初始化用户进度相关表
- `V2__add_revision_and_error_tables.sql` - 添加修改记录和错误表

---

## 🔐 安全架构

### 密钥管理

| 密钥 | 存储方式 | 读取方式 |
|------|----------|----------|
| OpenAI API Key | Azure Key Vault | 托管身份（Managed Identity） |
| OCR Key | Azure Key Vault | 托管身份 |
| 微信 AppSecret | Azure Key Vault | 托管身份 |
| MySQL 密码 | Azure Key Vault | 托管身份 |
| Session Token Secret | 环境变量 | `SESSION_TOKEN_SECRET` |

**安全特性**:
1. ✅ API Key 不落盘 - 所有敏感密钥通过 Azure Key Vault 存储
2. ✅ 托管身份认证 - 使用 OIDC 免密访问 Key Vault
3. ✅ 密钥缓存 - Key Vault 读取带 5 分钟缓存
4. ✅ 本地开发回退 - 未配置 Key Vault 时回退到环境变量

### JWT 令牌安全

- 自定义轻量实现（非 Spring Security JWT）
- HMAC-SHA256 签名算法
- 1 小时有效期
- 支持匿名会话和微信登录两种模式

---

## 🚀 部署与 CI/CD

### 部署流程

```
Push to main → GitHub Actions → Build JAR → Azure OIDC Login → Deploy to Azure App Service
```

### CI/CD 配置

**文件**: `.github/workflows/azure-deploy.yml`

**触发条件**:
- 推送到 `main` 分支
- 手动触发（workflow_dispatch）

**部署步骤**:
1. Checkout 代码
2. 设置 JDK 17
3. Maven 打包（`mvn clean package -DskipTests`）
4. Azure OIDC 登录
5. 部署 JAR 到 Azure Web App

**目标环境**:
- 应用名: `vibeteacher`
- 资源组: `vibeteacher-rg`
- 平台: Azure App Service（Linux）

---

## 📊 配置说明

### application.yml 关键配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| 端口 | 8080 | 服务端口 |
| 数据源 | H2 文件（`./data/vibeteacher`） | 开发环境默认 |
| JPA ddl-auto | validate | 依赖 Flyway 管理 schema |
| Flyway | 启用 | 自动执行数据库迁移 |
| 文件上传限制 | 20MB | 支持图片上传 |

### 环境变量

| 变量名 | 用途 |
|--------|------|
| `SESSION_TOKEN_SECRET` | JWT 签名密钥 |
| `KEY_VAULT_URL` | Azure Key Vault 地址 |
| `MYSQL_HOST/PORT/DB/USER/PASSWORD` | MySQL 连接配置 |

---

## 🎨 前端页面结构

### 小程序页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 写作练习 | `pages/index` | 主页，输入/拍照/OCR 识别作文，提交 AI 批改 |
| 学习进度 | `pages/progress` | 查看统计数据、连续打卡、历史记录 |
| 我的成就 | `pages/achievements` | 查看已获得的成就 |
| 词汇本 | `pages/vocabulary` | 查看和管理个人词汇本 |

### TabBar 配置

- 4 个底部标签
- 主色调: 蓝色（`#2563eb`）
- 图标资源位于 `assets/` 目录

---

## 📦 核心依赖

### Maven 依赖（pom.xml）

```xml
<!-- 核心依赖 -->
spring-boot-starter-web          # Web 服务
spring-boot-starter-data-jpa     # 数据访问
mysql-connector-j                # MySQL 驱动
h2                               # 本地开发数据库
flyway-core                      # 数据库迁移
flyway-mysql                     # Flyway MySQL 支持
azure-identity                   # Azure 身份认证
azure-security-keyvault-secrets  # Key Vault 密钥管理
openai-java-core                 # OpenAI SDK
openai-java-client-okhttp        # OpenAI HTTP 客户端
```

---

## 🔄 典型使用流程

```
1. 用户打开微信小程序
   ↓
2. 获取匿名会话令牌（或微信登录）
   ↓
3. 输入/拍照/选择图片作文
   ↓
4. （可选）OCR 识别图片文字
   ↓
5. 提交到后端 /api/revise
   ↓
6. 后端调用 OpenAI API 进行智能批改
   ↓
7. 返回修改建议、词汇优化等
   ↓
8. 前端展示结果
   ↓
9. 自动更新学习进度、词汇本、成就等
```

---

## 📝 开发建议

### 本地开发环境

1. **后端**:
   - JDK 17+
   - Maven 3.6+
   - 运行: `cd backend && ./mvnw spring-boot:run`
   - 默认使用 H2 文件数据库（`./data/vibeteacher`）

2. **前端**:
   - 微信开发者工具
   - 导入 `miniprogram/` 目录
   - 配置后端 API 地址

### 扩展方向

- [ ] 增加更多英语考试级别（如 IELTS）
- [ ] 支持更多 AI 模型（如 Claude、Gemini）
- [ ] 增加教师端审核和管理功能
- [ ] 增加学生间协作和分享功能
- [ ] 增加数据导出功能（PDF/Word）
- [ ] 增加实时聊天/答疑功能

---

## 📞 项目信息

- **代码仓库**: Git 管理
- **部署平台**: Azure App Service
- **CI/CD**: GitHub Actions
- **文档**: 详见 `README.md`

---

*文档生成时间: 2026年4月3日*
