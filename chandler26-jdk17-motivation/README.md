# 宝贝激励助手

## 启动脚本

项目分为后端和前端两个仓库，脚本都放在后端仓库根目录的 `scripts/` 下。

### 单独启动后端

```bash
./scripts/start-backend.sh
```

### 停止后端

```bash
./scripts/stop-backend.sh
```

### 重启后端

```bash
./scripts/restart-backend.sh
```

### 单独启动前端

```bash
./scripts/start-frontend.sh
```

### 停止前端

```bash
./scripts/stop-frontend.sh
```

### 重启前端

```bash
./scripts/restart-frontend.sh
```

### 同时启动前后端

```bash
./scripts/start-all.sh
```

### 同时停止前后端

```bash
./scripts/stop-all.sh
```

### 同时重启前后端

```bash
./scripts/restart-all.sh
```

### 说明

- 脚本会把进程 PID 和日志写到 `./.runtime/`
- 停止脚本优先按 PID 停，找不到 PID 时会按端口补停
- 如果你手动改了端口，脚本里的默认端口也要一起改

## 默认地址

- 后端：`http://127.0.0.1:17680`
- 前端：`http://127.0.0.1:5174`
- 日志：`./.runtime/backend.log` 和 `./.runtime/frontend.log`
