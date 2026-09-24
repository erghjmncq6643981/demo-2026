# Chandler Learning Assistant Web

英语学习助手前端，与 `chandler26-jdk17-learning-assistant` 后端配套使用。完整产品能力以后台项目 `docs/english-vocabulary-learning-assistant-design.md` 为准。

## 本地运行

```bash
npm install
npm run dev
```

- 前端：`http://127.0.0.1:5173`
- 后端：`http://127.0.0.1:16681`
- 无数据库预览：`http://127.0.0.1:5173/?preview=1`

## 工程结构

- `public/src/features`：按业务域组织的页面、状态和 API 适配器。
- `public/src/shared`：跨业务通用能力。
- `tests/unit`：单元测试。
- `tests/e2e`：桌面与移动端端到端测试。

## 验证

```bash
npm run check
npm run lint
npm test
npm run build
npm run e2e
```

前端工程规则以根目录和本项目 `AGENTS.md` 为准。
