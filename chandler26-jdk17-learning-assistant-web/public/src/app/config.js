export const providerCatalog = {
  deepseek: {
    label: 'DeepSeek',
    baseUrl: 'https://api.deepseek.com',
    chatPath: '/chat/completions',
    models: ['deepseek-chat', 'deepseek-reasoner'],
  },
  kimi: {
    label: 'Kimi',
    baseUrl: 'https://api.moonshot.cn',
    chatPath: '/v1/chat/completions',
    models: ['moonshot-v1-8k', 'moonshot-v1-32k', 'moonshot-v1-128k'],
  },
  doubao: {
    label: '豆包',
    baseUrl: 'https://ark.cn-beijing.volces.com',
    chatPath: '/api/v3/chat/completions',
    models: ['doubao-pro-32k', 'doubao-lite-32k'],
  },
  yuanbao: {
    label: '元宝',
    baseUrl: '',
    chatPath: '/chat/completions',
    models: ['hunyuan-turbos-latest', 'hunyuan-lite'],
  },
}

export const viewMeta = {
  profileView: ['Profile', '个人信息'],
  wordbookView: ['Wordbook', '单词本'],
  studyView: ['Study', '英语学习'],
  reviewView: ['Review', '复习计划'],
}
