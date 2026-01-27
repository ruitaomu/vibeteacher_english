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

## DevOps 自动化（GitHub Actions + OIDC）

### 1) GitHub Actions 工作流
工作流文件位于 [.github/workflows/azure-deploy.yml](.github/workflows/azure-deploy.yml)。

### 2) 创建 Azure AD 应用与 OIDC 联邦
> 需要在 Azure 中创建一个应用（服务主体）并授予资源组权限，然后为 GitHub Actions 配置联邦凭据。

示例步骤：
1. 创建应用
2. 创建服务主体
3. 赋予资源组权限（Contributor）
4. 添加 GitHub Actions OIDC 联邦凭据（subject 使用仓库 main 分支）

### 3) 在 GitHub 设置 Secrets
进入仓库 → Settings → Secrets and variables → Actions → New repository secret，新增：
- AZURE_CLIENT_ID
- AZURE_TENANT_ID
- AZURE_SUBSCRIPTION_ID

### 4) 触发部署
推送到 main 分支或在 Actions 页面手动触发即可。

## Azure Key Vault 与托管身份配置
为了避免前端保存或暴露 API KEY，后端通过托管身份从 Key Vault 读取密钥，并向前端下发短期访问令牌。

### 1) 启用 App Service 系统分配托管身份
Azure Portal → App Service → Identity → System assigned → On

### 2) Key Vault 设置机密
在 Key Vault 中创建 Secret（例如 `OPENAI_API_KEY`）。

### 3) 授权 Key Vault 访问
为 App Service 的托管身份授予 `Secret Get` 权限：
- Key Vault → Access policies（或 Access control/IAM）
- 添加身份并授予 Secret 读取权限

### 4) 配置应用参数
在 App Service → Configuration 添加以下应用设置：
- keyvault.url = https://<你的KeyVault名>.vault.azure.net/
- keyvault.secretName = <Secret 名称>

## 注意
- 该应用仅支持 OpenAI 兼容的 v1 终结点。
- API KEY 仅由后端从 Key Vault 读取，不在前端保存，也不会写入日志或配置文件。
