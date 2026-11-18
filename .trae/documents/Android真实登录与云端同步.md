# Android 真实登录与云端同步

## Context

DiTask Android 当前使用 Mock 登录，任务仅保存于本机 SQLite；`isPendingSync` 已预留但尚无网络同步。此次接入现有 `http://10.0.2.2:3000` 服务端，实现真实登录、注册和增量同步，与 PC 客户端保持相同接口及“较新 `updatedAt` 覆盖”的规则，同时继续支持离线新增、编辑、状态切换和软删除。

本期不修改服务端和 PC 接口，不引入 Room、Retrofit、Hilt、WorkManager 等新架构。

## 实施方案

### 1. 接入网络权限与最小 HTTP 层

- 在 `AndroidManifest.xml` 增加 `INTERNET` 权限，并通过定向 `networkSecurityConfig` 仅允许示例服务器 `10.0.2.2` 使用 HTTP，不全局放开明文流量。
- 在 `network` 包新增轻量 `ApiClient`：基于 `HttpURLConnection` + `org.json`，统一处理 UTF-8 JSON、Bearer Token、超时、HTTP 错误和服务端 `{message}`。
- 区分网络失败、401 未授权和其他 API 错误；网络失败不得清除本地会话或任务。
- 服务端地址集中配置为 `http://10.0.2.2:3000`，API 路径复用现有 `/api/v1` 契约。
- 备份规则排除保存 JWT 的 `session.xml`，避免令牌随系统备份外带。

### 2. 真实登录与注册

- 扩展 `AuthApi`，新增 `RealAuthApi`，调用：
  - `POST /api/v1/auth/login`
  - `POST /api/v1/auth/register`
- 从返回 JWT 的 payload 解码 `userId`，与 username、token 一并写入 `SessionStore`；本地只用它做数据隔离，鉴权仍由服务端完成。
- 复用 `LoginActivity` 和现有登录卡片，增加登录/注册模式切换、注册密码至少 6 位校验、请求加载态和服务端错误提示，移除 Mock 账号提示。
- 旧 Mock 会话因缺少有效 `userId` 不再直接进入主页面，但保留本地任务；首次真实登录时由当前账号认领这些未归属旧任务。

### 3. 数据库升级与账号隔离

- `Task` 增加可空 `userId`，数据库升级至 v2：
  - `task` 表新增 `userId`，保留原任务和时间戳。
  - 新增 `sync_state(userId, lastSyncTime)`，按账号保存 UTC 毫秒游标。
  - 建立账号维度索引。
- `TaskRepository` 的列表、保存、状态修改、软删除、待同步查询全部按当前 `userId` 限定，杜绝 A/B 账号数据互见或误上传。
- 新任务保存时补齐当前 `userId`；旧任务认领后标记待同步，不篡改其原始 `createdAt/updatedAt`。
- 退出登录或 401 只清会话，不删除任务和各账号同步游标；再次登录可继续使用原离线数据。

### 4. 实现增量同步服务

新增 `TaskApi` 与 `SyncService`，复刻 PC 的核心顺序：

1. 固定本轮 userId/token，并记录 `syncStart`。
2. 查询当前账号全部 `isPendingSync=1` 的任务（含软删除），转换为服务端 DTO。
3. `POST /api/v1/tasks/sync` 上传；确认成功后才清理对应快照的 pending 标记。条件清理需比对快照 `updatedAt`，避免覆盖上传期间产生的新修改。
4. 根据当前账号游标调用 `GET /api/v1/tasks`；有游标时传 URL 编码后的 `lastSyncTime`，无游标时全量拉取。
5. 本地毫秒时间戳与 ISO UTC 字符串双向转换；上传不包含 `isPendingSync`，拉取响应按直接任务数组解析。
6. 在 SQLite 事务中合并：远端 `updatedAt` 较新才覆盖；本地较新或相等则保留；新任务插入且 pending=false；未知远端墓碑不额外落库。
7. 合并成功后，以本轮 `syncStart` 更新该账号游标并同一事务提交。任一步失败均不推进游标，保留可重试状态。
8. 使用 `Mutex` 防止并发重入；上传或拉取返回 401 时回登录页，离线/服务器错误只提示并保留本地修改。

沿用 PC 的客户端时间戳冲突规则，不在本期声称解决多端同时编辑的强一致问题。

### 5. 主页面同步入口与状态

- `MainActivity` 启动时先加载本地列表，再自动同步，网络不得阻塞首屏。
- 菜单新增“立即同步”，展示同步中、已同步、离线/失败和待同步状态。
- 新建、编辑、状态切换、软删除落库后立即刷新列表并请求同步；同步中发生的修改在当前轮结束后补跑一轮。
- 页面处于前台时每 5 分钟自动同步一次；应用关闭后的后台同步不在本期范围。
- 本地任务操作始终可用；自动同步失败不反复弹窗，手动同步失败显示具体原因。

## 关键文件

- 修改：`DiTask/app/src/main/java/com/example/ditask/LoginActivity.kt`、`MainActivity.kt`
- 修改：`data/SessionStore.kt`、`data/Task.kt`、`data/TaskDbHelper.kt`、`data/TaskRepository.kt`
- 修改：`network/AuthApi.kt`、`network/MockAuthApi.kt`
- 新增：`network/ApiClient.kt`、`RealAuthApi.kt`、`TaskApi.kt`、任务 DTO/时间转换，以及 `sync/SyncService.kt`
- 修改：`activity_login.xml`、`activity_main.xml`、`menu_main.xml`、`strings.xml`
- 修改：`AndroidManifest.xml`、备份规则；新增定向 `network_security_config.xml`
- 契约依据：`TaskManager.Server/src/routes/auth.js`、`tasks.js`；`TaskManager.PcClient/Services/SyncService.cs`、`Models/TaskSyncDto.cs`

## 验证

1. 执行 Gradle 编译及现有测试，修复资源、Kotlin 和数据库迁移问题。
2. 增加针对时间/JSON 转换和同步关键分支的测试：首次全量、增量拉取、上传失败保留 pending、拉取失败不推进游标、远端新旧时间合并、软删除、账号隔离和防重入。
3. 连接局域网服务进行真机验收：
   - 注册、重名/弱密码/错误密码提示、登录及重启保持会话。
   - Android 新建中文任务后 PC 可见；PC 新建/修改后 Android 可见，优先级和截止时间一致。
   - 飞行模式下完整离线编辑，恢复网络后所有变更上传；状态修改和软删除双向同步。
   - A 留有待同步任务后切换 B，B 不可见也不能上传 A 的数据；重新登录 A 可继续同步。
   - 无效/过期 token 返回登录页但本地任务保留。
   - 旧 Mock 版本覆盖升级后任务不丢失，并由首个真实账号认领和同步。
