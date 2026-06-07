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
2. 设置本地运行环境变量（不依赖 Azure Key Vault / MySQL）

```bash
set SPRING_PROFILES_ACTIVE=local
set SESSION_TOKEN_SECRET=replace-with-a-long-random-string
set OPENAI_API_KEY=your-openai-key
set OPENAI_ENDPOINT=https://your-openai-endpoint.example.com/
set AZURE_OCR_API_KEY=your-ocr-key
set AZURE_OCR_ENDPOINT=https://your-ocr-endpoint.example.com/
```

3. 使用 JDK 17+ 构建并运行

```bash
mvn spring-boot:run
```

浏览器打开：http://localhost:8080

4. 在微信开发者工具中导入 `miniprogram/`，前端会在开发环境自动连接 `http://127.0.0.1:8080`

5. 如果你希望测试微信登录，再把 `miniprogram/app.js` 中的 `devtoolsWechatLoginEnabled` 改成 `true`，并在开发者工具里重新登录微信

## 小程序配置
- `miniprogram/config.js` 保存非开发环境使用的系统后端地址
- `miniprogram/app.js` 只在确认是开发者工具或开发版时强制切换到 `http://127.0.0.1:8080`
- 真机联调如果要连本地后端，请改用局域网 IP 或 HTTPS 隧道域名，不要使用本机回环地址

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

按下面步骤在本地终端执行（PowerShell）：

1. 先登录并切换订阅
```bash
az login
az account set --subscription <你的订阅ID>
```

2. 定义变量（把值替换成你的实际值）
```bash
$SUBSCRIPTION_ID="<你的订阅ID>"
$TENANT_ID="<你的租户ID>"
$RESOURCE_GROUP="vibeteacher-rg"
$APP_NAME="vibeteacher-github-actions"
$GITHUB_OWNER="<你的GitHub用户名>"
$GITHUB_REPO="vibeteacher_english"
```

3. 创建 Azure AD 应用（App Registration）
```bash
$APP_ID = az ad app create --display-name $APP_NAME --query appId -o tsv
```

4. 创建服务主体（Service Principal）
```bash
az ad sp create --id $APP_ID
```

5. 给资源组授予 Contributor 权限
```bash
az role assignment create --assignee $APP_ID --role Contributor --scope "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$RESOURCE_GROUP"
```

6. 创建 GitHub Actions OIDC 联邦凭据（main 分支）
```bash
$payload = @"
{
	"name": "github-main",
	"issuer": "https://token.actions.githubusercontent.com",
	"subject": "repo:$GITHUB_OWNER/$GITHUB_REPO:ref:refs/heads/main",
	"audiences": ["api://AzureADTokenExchange"]
}
"@

az ad app federated-credential create --id $APP_ID --parameters $payload
```

7. 验证联邦凭据是否已创建
```bash
az ad app federated-credential list --id $APP_ID -o table
```

8. 记录后续要写入 GitHub Secrets 的值
```bash
echo "AZURE_CLIENT_ID=$APP_ID"
echo "AZURE_TENANT_ID=$TENANT_ID"
echo "AZURE_SUBSCRIPTION_ID=$SUBSCRIPTION_ID"
```

### 3) 在 GitHub 设置 Secrets
进入仓库 → Settings → Secrets and variables → Actions → New repository secret，新增：
- AZURE_CLIENT_ID
- AZURE_TENANT_ID
- AZURE_SUBSCRIPTION_ID

### 4) 触发部署
推送到 main 分支或在 Actions 页面手动触发即可。

## Azure Key Vault 与托管身份配置
为了避免前端保存或暴露 API KEY，后端通过托管身份从 Key Vault 读取密钥，并向前端下发短期访问令牌。

### 0) 创建 Key Vault（详细选项）
1. Azure Portal → Create a resource → 搜索并选择 Key Vault → Create
2. 在 Basics 选项卡填写：
	- Subscription：选择你的部署订阅
	- Resource group：选择已有资源组（如 `vibeteacher-rg`）或新建
	- Key vault name：输入全局唯一名称（如 `vibeteacher-kv`）
	- Region：与 App Service 尽量同区域（如 East Asia）
	- Pricing tier：Standard（本项目足够）
3. 在 Access configuration 选项卡选择：
	- Permission model：Azure role-based access control (recommended)
	- Azure Disk Encryption、Template deployment、Azure Virtual Machines 等高级访问选项保持默认（无需开启）
4. 在 Networking 选项卡选择：
	- Public endpoint access：Enable public access from all networks（先用默认，便于快速验证）
	- 如果你有网络隔离要求，再改为 Private endpoint
5. 在其他选项卡保持默认后，点击 Review + create → Create
6. 创建完成后进入该 Key Vault，复制 Vault URI（形如 `https://<vault-name>.vault.azure.net/`），后续用于 `keyvault.url`

### 1) 启用 App Service 系统分配托管身份
1. Azure Portal → App Service → Identity → System assigned → On
2. 保存后等待 10–30 秒生效
3. 记住该托管身份名称（用于后续授权）

### 2) 在 Key Vault 创建 Secret
1. Azure Portal → Key Vault → Secrets → Generate/Import
2. 创建以下 Secret（名称需与代码一致）：
	- AZUREOPENAI-APIKEY（OpenAI API Key）
	- AZUREOPENAI-ENDPOINT（OpenAI 兼容 endpoint URL）
	- AZUREOCR-APIKEY（Azure AI Vision Key）
	- AZUREOCR-ENDPOINT（Azure AI Vision Endpoint）
	- WECHAT-APP-ID
  	- WECHAT-APP-SECRET
  	- MYSQL-DB (Azure MySQL Database Name)
  	- MYSQL-HOST (Azure MySQL Server Name)
  	- MYSQL-USERNAME (Azure MySQL Database Admin User Name)
  	- MYSQL-PASSWORD (Azure MySQL Database Admin Password)
   	- MYSQL-PORT ((Azure MySQL Port Number)

### 3) 授权 Key Vault 访问
建议使用 Azure RBAC 权限模型：
1. Key Vault → Access control (IAM) → Add role assignment
2. 角色选择 Key Vault Secrets User
3. 成员选择你的 App Service 托管身份
4. 保存并等待权限生效（约 1–2 分钟）

### 4) 配置应用参数（App Service）
Azure Portal → App Service → Configuration → Application settings → New application setting：
- keyvault.url = https://<你的KeyVault名>.vault.azure.net/
- keyvault.secretName = AZUREOPENAI-APIKEY
- keyvault.endpointSecretName = AZUREOPENAI-ENDPOINT
- keyvault.ocrSecretName = AZUREOCR-APIKEY
- keyvault.ocrEndpointSecretName = AZUREOCR-ENDPOINT

保存后会自动重启应用。

## Azure OCR 创建与配置

### 1) 当前项目调用的是什么服务
- 服务名称：Azure Computer Vision OCR
- 当前接口：Image Analysis OCR，同步图片识别接口
- 适用场景：手机拍照、截图、普通图片中的英文文字提取

当前后端代码位置：
- [backend/src/main/java/com/vibeteacher/service/OcrService.java](backend/src/main/java/com/vibeteacher/service/OcrService.java)

当前代码会调用：
```text
https://<你的OCR资源>.cognitiveservices.azure.com/computervision/imageanalysis:analyze?api-version=2023-02-01-preview&features=read&language=en
```

你在配置里只需要保存资源根 endpoint，不要把完整 API 路径存进去。

### 2) 如何在 Azure Portal 创建这个 OCR 服务
推荐直接创建 `ComputerVision` 资源。

步骤如下：
1. 打开 Azure Portal
2. 点击 `Create a resource`
3. 搜索 `ComputerVision`
4. 选择后点击 `Create`
5. 在 `Basics` 选项卡填写：
   - Subscription：选择你的订阅
   - Resource group：选择已有资源组，或新建一个
   - Region：尽量与 App Service、Key Vault 同区域
   - Name：输入资源名，例如 `vibeteacher-vision`
   - Pricing tier：测试可先选免费层或较低层级
6. 其余选项保持默认即可
7. 点击 `Review + create`
8. 再点击 `Create`
9. 创建完成后点击 `Go to resource`

如果你的订阅界面没有单独的 `ComputerVision` 创建入口，也可以创建 `Azure AI services` 多服务资源。只要这个资源能提供 `Keys and Endpoint`，并且 endpoint 是 `https://<name>.cognitiveservices.azure.com/` 这种格式，就可以对接本项目当前 OCR 代码。

### 3) endpoint 和 apikey 在哪里拿
资源创建完成后：
1. 进入资源页面
2. 左侧菜单找到 `Keys and Endpoint`
3. 复制：
   - `Key 1` 或 `Key 2`：作为 API Key
   - `Endpoint`：作为 OCR endpoint

典型 endpoint 示例：
```text
https://your-vision-resource.cognitiveservices.azure.com/
```

注意：
- 正确配置的是上面的根地址
- 不要手动填成 `/computervision/imageanalysis:analyze?...` 完整路径
- 当前代码会自动拼接接口路径

### 4) 在 Key Vault 里怎么存
在 Key Vault → Secrets 中创建这两个 secret：
- `AZUREOCR-APIKEY`
- `AZUREOCR-ENDPOINT`

填写规则：
- `AZUREOCR-APIKEY` = 资源页面里的 `Key 1` 或 `Key 2`
- `AZUREOCR-ENDPOINT` = 资源页面里的 `Endpoint`

### 6) 常见报错含义：
- `401` 或 `403`：Key 错了，或者调用的资源不是你的 key 对应的资源
- `404`：endpoint 填错了，或者填成了错误资源的 endpoint
- `400`：图片格式、大小或 content-type 不符合要求，或者 endpoint 被拼成了完整路径导致重复

## 注意
- 该应用仅支持 OpenAI 兼容的 v1 终结点。
- API KEY 仅由后端从 Key Vault 读取，不在前端保存，也不会写入日志或配置文件。
