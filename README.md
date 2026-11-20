# 202611102115_DiTask

双端互通任务管理系统：Android 客户端（`DiTask`）、PC 客户端（`TaskManager.PcClient`）、服务端（`TaskManager.Server`）。

## 本地配置

仓库内不保存真实服务器地址与签名口令，均通过本地配置注入。

### Android 服务端地址

键名：`DITASK_SERVER_URL`

按以下优先级读取：

1. `DiTask/local.properties`（推荐，已被 `.gitignore` 忽略）：
   ```properties
   DITASK_SERVER_URL=http://your-server-host:3000
   ```
2. `~/.gradle/gradle.properties` 中的 `DITASK_SERVER_URL=...`，或命令行 `-PDITASK_SERVER_URL=...`
3. 环境变量 `ORG_GRADLE_PROJECT_DITASK_SERVER_URL`

未配置时默认 `http://10.0.2.2:3000`（Android 模拟器访问宿主机的别名），可直接编译并在模拟器运行。配置示例见 `DiTask/local.properties.example`。

若使用明文 HTTP 且主机不是默认地址，需同步在 `DiTask/app/src/main/res/xml/network_security_config.xml` 中放行该主机，或改用 HTTPS。
