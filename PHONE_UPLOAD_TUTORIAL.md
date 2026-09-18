# 手机端上传代码到 GitHub 仓库 —— 详细操作教程

本教程适配 iQOO OriginOS4 / OriginOS5，全程在手机端完成，无需电脑。
适用于本项目（三战助手）以及任何 Android 源码项目。

---

## 一、准备工作（一次性）

### 1. 安装 GitHub 账号与 App
1. 注册 GitHub 账号（<https://github.com/signup>），已有跳过。
2. 在 iQOO 自带应用商店或官网下载安装 **Termux**（安卓终端模拟器，用于 git 命令）。
   - OriginOS 应用商店可能没有 Termux，请到 F-Droid 下载：<https://f-droid.org/packages/com.termux/>
   - 或 GitHub 官方版：<https://github.com/termux/termux-app/releases>
3. 安装 **GitHub 官方 App**（可选，用于查看构建状态）：应用商店搜索 "GitHub"。

### 2. 在 GitHub 网页端创建空仓库
1. 手机浏览器打开 <https://github.com/new>（登录后）。
2. Repository name 填：`ThreeKingdomsStrategyAssistant`
3. **不要勾选** "Add a README"、"Add .gitignore"、"license"（保持空仓库，避免后续 pull 冲突）。
4. 点击 **Create repository**。
5. 创建后页面会显示仓库地址，形如：
   `https://github.com/你的用户名/ThreeKingdomsStrategyAssistant.git`
   **记下这个地址**，后面要用。

---

## 二、配置 Termux 环境（一次性）

打开 Termux，逐条执行以下命令：

```bash
# 1. 更新软件源
pkg update -y && pkg upgrade -y

# 2. 安装 git
pkg install -y git

# 3. 配置 git 用户信息（替换为你的信息）
git config --global user.name "你的GitHub用户名"
git config --global user.email "你的GitHub邮箱"

# 4. 授予 Termux 访问手机存储权限（弹窗点允许）
termux-setup-storage
```

### 配置免密推送（Personal Access Token）

GitHub 已不支持密码推送，必须用 Token：

1. 手机浏览器打开 <https://github.com/settings/tokens>
2. 点击 **Generate new token (classic)**
3. Note 填 `termux-upload`，Expiration 选 `90 days` 或自定义
4. 勾选 **repo**（完整勾选 repo 大项）
5. 点击 **Generate token**
6. **立即复制生成的 token**（形如 `ghp_xxxxxxxxxxxx`），只显示一次！

在 Termux 中配置凭据（后续 push 免密）：

```bash
# 启用凭据存储
git config --global credential.helper store
```

---

## 三、把项目代码放进 Termux

### 方法 A：用文本编辑器逐个创建（推荐，对应本项目目录结构）

1. 在 Termux 中安装 nano 编辑器：
   ```bash
   pkg install -y nano
   ```

2. 创建项目根目录并进入：
   ```bash
   mkdir -p ~/ThreeKingdomsStrategyAssistant
   cd ~/ThreeKingdomsStrategyAssistant
   git init
   git branch -M main      # 默认分支改名为 main
   ```

3. 按照本文档（README）中给出的【完整目录树】，逐个用 nano 创建文件：
   ```bash
   # 示例：创建 settings.gradle.kts
   nano settings.gradle.kts
   ```
   - 粘贴对应代码块内容
   - 按 `Ctrl+O` → 回车 保存
   - 按 `Ctrl+X` 退出
   - 用 `mkdir -p 路径` 创建子目录，例如：
     ```bash
     mkdir -p app/src/main/java/com/threecamp/assistant/accessibility
     mkdir -p app/src/main/java/com/threecamp/assistant/ocr
     mkdir -p app/src/main/java/com/threecamp/assistant/engine
     mkdir -p app/src/main/java/com/threecamp/assistant/data
     mkdir -p app/src/main/java/com/threecamp/assistant/notify
     mkdir -p app/src/main/java/com/threecamp/assistant/widget
     mkdir -p app/src/main/java/com/threecamp/assistant/ui
     mkdir -p app/src/main/res/values
     mkdir -p app/src/main/res/xml
     mkdir -p app/src/main/res/layout
     mkdir -p app/src/main/res/drawable
     mkdir -p app/src/main/res/mipmap-anydpi-v26
     mkdir -p .github/workflows
     mkdir -p gradle
   ```

### 方法 B：从电脑端 push 后手机端只做拉取/编辑（更省力）
若你已经在电脑上把代码 push 到仓库，手机端只需：
```bash
cd ~
git clone https://github.com/你的用户名/ThreeKingdomsStrategyAssistant.git
cd ThreeKingdomsStrategyAssistant
```

---

## 四、首次推送代码到 GitHub

在项目根目录 `~/ThreeKingdomsStrategyAssistant` 下：

```bash
# 1. 添加远程仓库（替换为你的仓库地址）
git remote add origin https://github.com/你的用户名/ThreeKingdomsStrategyAssistant.git

# 2. 添加全部文件
git add .

# 3. 提交
git commit -m "init: 三战助手 MVP 项目骨架"

# 4. 首次推送
git push -u origin main
```

推送时会提示输入用户名和密码：
- Username：你的 GitHub 用户名
- Password：粘贴上面生成的 **Token**（不是 GitHub 密码！）

> 因为之前配置了 `credential.helper store`，**只需输入一次**，之后 push 自动免密。

推送成功后，打开浏览器访问你的仓库，应能看到全部文件。

---

## 五、触发 GitHub Actions 自动构建 APK

1. 推送完成后，仓库页面点击 **Actions** 标签。
2. 应能看到名为 **Build Debug APK** 的工作流自动运行（push 到 main 自动触发）。
3. 等待约 5-10 分钟，运行结束出现绿色 ✓ 表示构建成功。
4. 点进这次运行 → 滚动到底部 **Artifacts** 区域 → 点击 `three-camp-assistant-debug-apk` 下载 APK。

### 手动触发（workflow_dispatch）
1. Actions 页面左侧选择 **Build Debug APK**。
2. 右上角点击 **Run workflow** → 选择 `main` 分支 → 点击绿色 **Run workflow** 按钮。
3. 等待构建完成后同样在 Artifacts 下载。

---

## 六、在 iQOO OriginOS 上安装 APK

1. 下载得到的 APK 文件（一般在 `Download` 文件夹）。
2. 文件管理器点击 APK 安装。
3. OriginOS 会提示"未知来源应用"，按引导允许该来源安装。
4. 安装完成后打开"三战助手"。

---

## 七、日常更新代码（增量推送）

修改 / 新增文件后：

```bash
cd ~/ThreeKingdomsStrategyAssistant
git add .
git commit -m "feat: 描述本次修改"
git push
```

push 到 main 后 GitHub Actions 会自动重新构建 APK。

---

## 八、常见问题

### Q1：push 时报 `Authentication failed`
- Token 已过期或未勾选 repo 权限。重新生成 Token，再执行：
  ```bash
  git push
  # 重新输入用户名和新 Token
  ```

### Q2：Termux 中 nano 粘贴中文乱码
- 长按 Termux 屏幕 → Paste，确认使用剪贴板纯文本。
- 或先在记事本 App 整理好再粘贴。

### Q3：GitHub Actions 构建失败
- 打开 Actions 运行详情查看红色错误日志。
- 常见原因：文件路径写错、依赖名拼错、缺文件。
- 修正后重新 push 即可重新触发构建。

### Q4：下载 APK 时浏览器报"无法下载"
- GitHub 下载 artifact 需登录账号；手机浏览器先登录 GitHub 再点击下载。

### Q5：OriginOS 杀后台导致无障碍服务中断
- 设置 → 电池 → 后台耗电管理 → 找到"三战助手" → 允许后台高耗电。
- 设置 → 快捷与辅助 → 无障碍 → 三战助手 → 开启（重启后需检查是否仍开启）。
- i 控件 → 性能增强 → 加入"游戏免打扰白名单"避免游戏时被限制。

---

## 九、安全说明

- Token 等同于账号密码，**不要分享给他人**，不要提交到代码仓库。
- 若怀疑泄露，立即到 <https://github.com/settings/tokens> 删除并重新生成。
- 本项目所有 OCR 与数据均在本地，不上传任何游戏数据到网络。
