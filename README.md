# 英语写作学习助手

这是一个基于 Spring Boot + Vue (CDN) 的“英语写作学习助手”。
- 前端：Vue 3 + 原生 HTML/JS
- 后端：Spring Boot
- 调用模型：OpenAI SDK v1 GA API
- 安全性：API KEY 仅在运行时输入，不落盘、不写入配置

## 功能
- 按学习级别（中学7-12年级、大学英语四级、大学英语六级）给出修改建议
- 输出修正后的文本、修改原因（中文）
- 保持原文意思不变

## 本地运行
1. 进入后端目录
2. 使用 JDK 17+ 构建并运行

```bash
mvn spring-boot:run
```

浏览器打开：http://localhost:8080

## 生产部署（Azure App Service）

下面步骤会把 Spring Boot 打包后的 Jar 直接部署到 Azure App Service（Linux）。

### 1) 你需要协助的操作
- 请先登录 Azure：`az login`
- 如果有多个订阅，请选择正确订阅：`az account set --subscription <订阅ID>`

### 2) 创建资源
```bash
az group create --name vibeteacher-rg --location eastasia
az appservice plan create --name vibeteacher-plan --resource-group vibeteacher-rg --sku B1 --is-linux
az webapp create --resource-group vibeteacher-rg --plan vibeteacher-plan --name <你的全局唯一站点名> --runtime "JAVA:17-java17"
```

### 3) 打包
在项目根目录执行：
```bash
cd backend
mvn clean package
```

### 4) 部署 Jar
```bash
az webapp deploy --resource-group vibeteacher-rg --name <你的站点名> --src-path target/writing-assistant-1.0.0.jar --type jar
```

部署成功后访问：
```
https://<你的站点名>.azurewebsites.net
```

## DevOps 自动化（GitHub Actions 示例）
如需我帮你生成 GitHub Actions 工作流文件，请告诉我仓库地址与要使用的 Azure 凭据方式（Publish Profile 或 OIDC）。

## 注意
- 该应用仅支持 OpenAI 兼容的 v1 终结点。
- API KEY 只在内存中使用，不会写入日志或配置文件。
