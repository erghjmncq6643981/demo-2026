# 英语学习助手前端说明

完整且已验证的产品功能以后台项目的 `docs/english-vocabulary-learning-assistant-design.md` 为唯一说明。本文只记录前端运行和页面结构。

## 页面结构

- 卡片学习：AI 查词、结构化词卡、发音和加入个人单词本。
- 单词本：个人词条、学习状态、Markdown 笔记和语境精读。
- 词汇大挑战：选择学习计划，通过周/月日历预览场景，完成文章学习和词汇检查。
- 复习：跟敲、例句回顾、记忆结果和下一次复习。
- 个人信息：账户、个人单词本、学习计划和学习设置。
- 系统管理：管理员用户中心、公共词本、AI 任务、模型、Agent、模板、AI 会话和系统日志。

## 本地运行

```bash
npm install
npm run dev
```

默认前端地址为 `http://127.0.0.1:5173`，后端地址为 `http://127.0.0.1:16681`。没有数据库时可打开 `http://127.0.0.1:5173/?preview=1` 使用预览数据验证主要交互。

## 工程验证

```bash
npm run check
npm run lint
npm test
npm run build
npm run e2e
```

Playwright 同时验证桌面和移动端预览页面。功能代码放在 `public/src/features`，跨功能基础能力放在 `public/src/shared`。
