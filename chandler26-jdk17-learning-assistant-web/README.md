# Chandler Learning Assistant Web

零依赖前端原型，用于连接 `chandler26-jdk17-learning-assistant` 后端。

## 启动

```bash
/usr/local/bin/node server.mjs
```

默认地址：

```text
http://127.0.0.1:5173
```

后端默认地址：

```text
http://localhost:16681
```

页面会先调用后端词汇缓存接口，命中缓存时不再调用 AI；未命中或选择强制刷新时才通过后端 Agent 调用模型，并保存结果到 MySQL。
