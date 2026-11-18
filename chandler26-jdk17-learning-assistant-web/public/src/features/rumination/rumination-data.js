/**
 * 知识反刍模块 - 预置精选场景与拓扑图谱数据集
 * 真实契合自考考纲与 4D 衍生体系。
 */

export const PRESET_SCENARIOS = [
  {
    code: 'produce_kitchen',
    name: '生鲜采购与厨房料理',
    icon: '🥦',
    totalWords: 39,
    dueWords: 5,
    description: '涵盖常见果蔬生鲜、烹饪刀工处理动词、加工下游产物及一日三餐词汇。',
  },
  {
    code: 'time_calendar_seasons',
    name: '日历、时间与四季作息',
    icon: '🗓️',
    totalWords: 58,
    dueWords: 0,
    description: '涵盖十二月份、星期循环、四季作息节令与时间刻度表达体系。',
  },
  {
    code: 'clothing_accessories',
    name: '服装穿戴与个人配饰',
    icon: '👔',
    totalWords: 43,
    dueWords: 0,
    description: '涵盖上身外搭、下身裤装裙摆、随身精品配饰与经典色彩美学。',
  },
  {
    code: 'animal_kingdom',
    name: '动物王国与自然生灵',
    icon: '🐾',
    totalWords: 44,
    dueWords: 0,
    description: '涵盖农场伴侣、野生动物、水生海洋生物及飞禽昆虫生态。',
  },
  {
    code: 'human_body_anatomy',
    name: '人体结构、五官与身心',
    icon: '🧍',
    totalWords: 33,
    dueWords: 0,
    description: '涵盖头颅容貌五官、肢体骨骼、躯干器官与身心感官知觉。',
  },
  {
    code: 'family_relationships',
    name: '家庭成员与亲属谱系',
    icon: '👨‍👩‍👧‍👦',
    totalWords: 28,
    dueWords: 0,
    description: '涵盖父母双亲、儿女子孙、三代近亲以及姻亲连带家族脉络。',
  },
  {
    code: 'numbers_quantities',
    name: '数词系统与度量表达',
    icon: '🔢',
    totalWords: 47,
    dueWords: 0,
    description: '涵盖基础基数词、大数进位体系、序数词排行与倍数份额。',
  },
  {
    code: 'weather_nature_directions',
    name: '天气气候与地理方位',
    icon: '⛅',
    totalWords: 36,
    dueWords: 0,
    description: '涵盖东南西北方位派生、天气气象现象、气候属性与球类户外运动。',
  },
  {
    code: 'appliances_devices',
    name: '家用设备与装置',
    icon: '🔌',
    totalWords: 34,
    dueWords: 0,
    description: '涵盖起居视听通信、厨卫清洁、温控空气及日常家电装置。',
  },
  {
    code: 'room_spaces',
    name: '房间格局与起居空间',
    icon: '🏠',
    totalWords: 54,
    dueWords: 0,
    description: '涵盖房屋主要功能空间、建筑构成、房间陈设及起居动词。',
  },
]

export const PRODUCE_GRAPH_DATA = {
  nodes: [
    // 场景核心枢纽
    { id: 'hub', nodeType: 'hub', label: '果蔬料理', subLabel: '场景核心', status: 'mastered' },

    // 词本核心词 (自考考纲词)
    { id: 'potato', nodeType: 'core', term: 'potato', definition: '土豆/马铃薯', status: 'vague' },
    { id: 'tomato', nodeType: 'core', term: 'tomato', definition: '西红柿/番茄', status: 'mastered' },
    { id: 'cabbage', nodeType: 'core', term: 'cabbage', definition: '卷心菜/甘蓝', status: 'vague' },
    { id: 'apple', nodeType: 'core', term: 'apple', definition: '苹果', status: 'mastered' },
    { id: 'onion', nodeType: 'core', term: 'onion', definition: '洋葱', status: 'unlearned' },
    { id: 'carrot', nodeType: 'core', term: 'carrot', definition: '胡萝卜', status: 'vague' },
    { id: 'mushroom', nodeType: 'core', term: 'mushroom', definition: '蘑菇/菌菇', status: 'mastered' },
    { id: 'grape', nodeType: 'core', term: 'grape', definition: '葡萄', status: 'unlearned' },

    // 4D 衍生 - 动作动词 (Action Verbs)
    { id: 'peel', nodeType: 'expansion', term: 'peel', definition: '削皮/剥皮', dimension: 'action', status: 'vague' },
    { id: 'boil', nodeType: 'expansion', term: 'boil', definition: '水煮/煮沸', dimension: 'action', status: 'mastered' },
    { id: 'chop', nodeType: 'expansion', term: 'chop', definition: '切碎/切块', dimension: 'action', status: 'vague' },
    { id: 'harvest', nodeType: 'expansion', term: 'harvest', definition: '采摘/收割', dimension: 'action', status: 'unlearned' },

    // 4D 衍生 - 下游加工 (Downstream Products)
    { id: 'mashed_potato', nodeType: 'expansion', term: 'mashed potato', definition: '土豆泥', dimension: 'downstream', status: 'unlearned' },
    { id: 'ketchup', nodeType: 'expansion', term: 'ketchup', definition: '番茄酱', dimension: 'downstream', status: 'unlearned' },
    { id: 'wine', nodeType: 'expansion', term: 'wine', definition: '葡萄酒/果酒', dimension: 'downstream', status: 'mastered' },
    { id: 'salad', nodeType: 'expansion', term: 'salad', definition: '沙拉/拌菜', dimension: 'downstream', status: 'mastered' },

    // 4D 衍生 - 感官修饰 (Attributes)
    { id: 'crisp', nodeType: 'expansion', term: 'crisp', definition: '爽脆的', dimension: 'attribute', status: 'unlearned' },
    { id: 'juicy', nodeType: 'expansion', term: 'juicy', definition: '多汁的', dimension: 'attribute', status: 'mastered' },
    { id: 'ripe', nodeType: 'expansion', term: 'ripe', definition: '成熟的', dimension: 'attribute', status: 'vague' },
  ],
  edges: [
    // 枢纽辐射到核心词
    { source: 'hub', target: 'potato', relationType: 'peer', label: '' },
    { source: 'hub', target: 'tomato', relationType: 'peer', label: '' },
    { source: 'hub', target: 'cabbage', relationType: 'peer', label: '' },
    { source: 'hub', target: 'apple', relationType: 'peer', label: '' },
    { source: 'hub', target: 'onion', relationType: 'peer', label: '' },
    { source: 'hub', target: 'carrot', relationType: 'peer', label: '' },
    { source: 'hub', target: 'grape', relationType: 'peer', label: '' },

    // 动作关联
    { source: 'potato', target: 'peel', relationType: 'action', label: '🔪 削皮' },
    { source: 'potato', target: 'boil', relationType: 'action', label: '🔪 水煮' },
    { source: 'onion', target: 'chop', relationType: 'action', label: '🔪 切碎' },
    { source: 'grape', target: 'harvest', relationType: 'action', label: '🔪 采收' },

    // 下游加工关联
    { source: 'potato', target: 'mashed_potato', relationType: 'downstream', label: '🥫 加工' },
    { source: 'tomato', target: 'ketchup', relationType: 'downstream', label: '🥫 加工' },
    { source: 'grape', target: 'wine', relationType: 'downstream', label: '🥫 酿造' },
    { source: 'cabbage', target: 'salad', relationType: 'downstream', label: '🥫 拌制' },

    // 感官属性关联
    { source: 'cabbage', target: 'crisp', relationType: 'attribute', label: '' },
    { source: 'tomato', target: 'juicy', relationType: 'attribute', label: '' },
    { source: 'apple', target: 'ripe', relationType: 'attribute', label: '' },
  ],
}

export const HEALTHCARE_GRAPH_DATA = {
  nodes: [
    { id: 'hub', nodeType: 'hub', label: '就医与健康', subLabel: '场景核心', status: 'mastered' },
    { id: 'fever', nodeType: 'core', term: 'fever', definition: '发烧/发热', status: 'vague' },
    { id: 'cough', nodeType: 'core', term: 'cough', definition: '咳嗽', status: 'mastered' },
    { id: 'headache', nodeType: 'core', term: 'headache', definition: '头痛', status: 'vague' },
    { id: 'medicine', nodeType: 'core', term: 'medicine', definition: '药物/医学', status: 'mastered' },
    { id: 'pill', nodeType: 'core', term: 'pill', definition: '药片/药丸', status: 'unlearned' },
    { id: 'doctor', nodeType: 'core', term: 'doctor', definition: '医生', status: 'mastered' },
    { id: 'nurse', nodeType: 'core', term: 'nurse', definition: '护士', status: 'vague' },
    { id: 'hospital', nodeType: 'core', term: 'hospital', definition: '医院', status: 'unlearned' },
  ],
  edges: [
    { source: 'hub', target: 'fever', relationType: 'peer', label: '' },
    { source: 'hub', target: 'cough', relationType: 'peer', label: '' },
    { source: 'hub', target: 'headache', relationType: 'peer', label: '' },
    { source: 'hub', target: 'medicine', relationType: 'peer', label: '' },
    { source: 'hub', target: 'pill', relationType: 'peer', label: '' },
    { source: 'hub', target: 'doctor', relationType: 'peer', label: '' },
    { source: 'hub', target: 'nurse', relationType: 'peer', label: '' },
    { source: 'hub', target: 'hospital', relationType: 'peer', label: '' },
  ],
}

export const DINING_GRAPH_DATA = {
  nodes: [
    { id: 'hub', nodeType: 'hub', label: '餐饮与社交', subLabel: '场景核心', status: 'mastered' },
    { id: 'coffee', nodeType: 'core', term: 'coffee', definition: '咖啡', status: 'mastered' },
    { id: 'tea', nodeType: 'core', term: 'tea', definition: '茶', status: 'mastered' },
    { id: 'bread', nodeType: 'core', term: 'bread', definition: '面包', status: 'vague' },
    { id: 'butter', nodeType: 'core', term: 'butter', definition: '黄油', status: 'unlearned' },
    { id: 'cheese', nodeType: 'core', term: 'cheese', definition: '奶酪', status: 'vague' },
    { id: 'beef', nodeType: 'core', term: 'beef', definition: '牛肉', status: 'mastered' },
    { id: 'menu', nodeType: 'core', term: 'menu', definition: '菜单', status: 'unlearned' },
    { id: 'bill', nodeType: 'core', term: 'bill', definition: '账单', status: 'vague' },
  ],
  edges: [
    { source: 'hub', target: 'coffee', relationType: 'peer', label: '' },
    { source: 'hub', target: 'tea', relationType: 'peer', label: '' },
    { source: 'hub', target: 'bread', relationType: 'peer', label: '' },
    { source: 'hub', target: 'butter', relationType: 'peer', label: '' },
    { source: 'hub', target: 'cheese', relationType: 'peer', label: '' },
    { source: 'hub', target: 'beef', relationType: 'peer', label: '' },
    { source: 'hub', target: 'menu', relationType: 'peer', label: '' },
    { source: 'hub', target: 'bill', relationType: 'peer', label: '' },
  ],
}

export function getPresetGraphData(scenarioCode) {
  if (scenarioCode === 'healthcare') return HEALTHCARE_GRAPH_DATA
  if (scenarioCode === 'dining_cafe') return DINING_GRAPH_DATA
  return PRODUCE_GRAPH_DATA
}

/** 词汇针对性 4D 裂变延伸词典（严格按照当前选中的词条进行地道关联衍生，绝不机械拼接） */
export const FISSION_EXPANSIONS = {
  // 生鲜采购与厨房料理
  peel: [
    {
      node: { id: 'paring_knife', nodeType: 'expansion', term: 'paring knife', definition: '削皮小刀', dimension: 'downstream', status: 'unlearned' },
      edge: { relationType: 'downstream', label: '' },
    },
    {
      node: { id: 'rind', nodeType: 'expansion', term: 'rind', definition: '瓜果外皮', dimension: 'peer', status: 'unlearned' },
      edge: { relationType: 'peer', label: '' },
    },
    {
      node: { id: 'zest', nodeType: 'expansion', term: 'zest', definition: '刨柠檬风味皮', dimension: 'action', status: 'vague' },
      edge: { relationType: 'action', label: '' },
    },
  ],
  potato: [
    {
      node: { id: 'sweet_potato', nodeType: 'expansion', term: 'sweet potato', definition: '红薯/番薯', dimension: 'peer', status: 'unlearned' },
      edge: { relationType: 'peer', label: '' },
    },
    {
      node: { id: 'roast', nodeType: 'expansion', term: 'roast', definition: '烘烤/烤制', dimension: 'action', status: 'unlearned' },
      edge: { relationType: 'action', label: '' },
    },
    {
      node: { id: 'chip', nodeType: 'expansion', term: 'chip', definition: '炸薯片/脆片', dimension: 'downstream', status: 'mastered' },
      edge: { relationType: 'downstream', label: '' },
    },
  ],
  wine: [
    {
      node: { id: 'vinegar', nodeType: 'expansion', term: 'vinegar', definition: '果醋/食醋', dimension: 'downstream', status: 'mastered' },
      edge: { relationType: 'downstream', label: '' },
    },
    {
      node: { id: 'ferment', nodeType: 'expansion', term: 'ferment', definition: '发酵/酿造', dimension: 'action', status: 'unlearned' },
      edge: { relationType: 'action', label: '' },
    },
  ],
  apple: [
    {
      node: { id: 'cider', nodeType: 'expansion', term: 'cider', definition: '苹果酒/果醋', dimension: 'downstream', status: 'mastered' },
      edge: { relationType: 'downstream', label: '' },
    },
    {
      node: { id: 'pie', nodeType: 'expansion', term: 'pie', definition: '苹果派/甜点', dimension: 'downstream', status: 'unlearned' },
      edge: { relationType: 'downstream', label: '' },
    },
  ],
  tomato: [
    {
      node: { id: 'puree', nodeType: 'expansion', term: 'puree', definition: '番茄泥/浓酱', dimension: 'downstream', status: 'unlearned' },
      edge: { relationType: 'downstream', label: '' },
    },
    {
      node: { id: 'stew', nodeType: 'expansion', term: 'stew', definition: '慢火炖煮', dimension: 'action', status: 'vague' },
      edge: { relationType: 'action', label: '' },
    },
  ],
  cabbage: [
    {
      node: { id: 'pickle', nodeType: 'expansion', term: 'pickle', definition: '腌酸菜/泡菜', dimension: 'downstream', status: 'unlearned' },
      edge: { relationType: 'downstream', label: '' },
    },
    {
      node: { id: 'shred', nodeType: 'expansion', term: 'shred', definition: '切细丝/备料', dimension: 'action', status: 'vague' },
      edge: { relationType: 'action', label: '' },
    },
  ],
  onion: [
    {
      node: { id: 'caramelize', nodeType: 'expansion', term: 'caramelize', definition: '焦糖化慢炒', dimension: 'action', status: 'unlearned' },
      edge: { relationType: 'action', label: '' },
    },
    {
      node: { id: 'ring', nodeType: 'expansion', term: 'onion ring', definition: '油炸洋葱圈', dimension: 'downstream', status: 'mastered' },
      edge: { relationType: 'downstream', label: '' },
    },
  ],
  carrot: [
    {
      node: { id: 'grate', nodeType: 'expansion', term: 'grate', definition: '擦细丝/磨碎', dimension: 'action', status: 'vague' },
      edge: { relationType: 'action', label: '' },
    },
    {
      node: { id: 'crunchy', nodeType: 'expansion', term: 'crunchy', definition: '清脆爽口的', dimension: 'attribute', status: 'unlearned' },
      edge: { relationType: 'attribute', label: '' },
    },
  ],
  mushroom: [
    {
      node: { id: 'saute', nodeType: 'expansion', term: 'sauté', definition: '嫩煎/爆炒', dimension: 'action', status: 'unlearned' },
      edge: { relationType: 'action', label: '' },
    },
  ],

  // 医院就医与身体健康 (医疗健康核心衍生)
  fever: [
    {
      node: { id: 'thermometer', nodeType: 'expansion', term: 'thermometer', definition: '体温计/温度计', dimension: 'attribute', status: 'unlearned' },
      edge: { relationType: 'attribute', label: '' },
    },
    {
      node: { id: 'chills', nodeType: 'expansion', term: 'chills', definition: '畏寒/发冷', dimension: 'attribute', status: 'unlearned' },
      edge: { relationType: 'attribute', label: '' },
    },
    {
      node: { id: 'sweat', nodeType: 'expansion', term: 'sweat', definition: '出汗/排汗', dimension: 'action', status: 'vague' },
      edge: { relationType: 'action', label: '' },
    },
    {
      node: { id: 'flu', nodeType: 'expansion', term: 'flu', definition: '流行性感冒', dimension: 'peer', status: 'mastered' },
      edge: { relationType: 'peer', label: '' },
    },
  ],
  cough: [
    {
      node: { id: 'syrup', nodeType: 'expansion', term: 'syrup', definition: '止咳糖浆', dimension: 'downstream', status: 'unlearned' },
      edge: { relationType: 'downstream', label: '' },
    },
    {
      node: { id: 'sore_throat', nodeType: 'expansion', term: 'sore throat', definition: '咽喉疼痛', dimension: 'peer', status: 'vague' },
      edge: { relationType: 'peer', label: '' },
    },
    {
      node: { id: 'phlegm', nodeType: 'expansion', term: 'phlegm', definition: '痰/痰液', dimension: 'attribute', status: 'unlearned' },
      edge: { relationType: 'attribute', label: '' },
    },
  ],
  headache: [
    {
      node: { id: 'migraine', nodeType: 'expansion', term: 'migraine', definition: '偏头痛', dimension: 'peer', status: 'unlearned' },
      edge: { relationType: 'peer', label: '' },
    },
    {
      node: { id: 'painkiller', nodeType: 'expansion', term: 'painkiller', definition: '止痛药', dimension: 'downstream', status: 'mastered' },
      edge: { relationType: 'downstream', label: '' },
    },
    {
      node: { id: 'dizziness', nodeType: 'expansion', term: 'dizziness', definition: '头晕/眩晕', dimension: 'attribute', status: 'vague' },
      edge: { relationType: 'attribute', label: '' },
    },
  ],
  pill: [
    {
      node: { id: 'capsule', nodeType: 'expansion', term: 'capsule', definition: '胶囊药丸', dimension: 'peer', status: 'unlearned' },
      edge: { relationType: 'peer', label: '' },
    },
    {
      node: { id: 'dose', nodeType: 'expansion', term: 'dose', definition: '服药剂量', dimension: 'attribute', status: 'unlearned' },
      edge: { relationType: 'attribute', label: '' },
    },
    {
      node: { id: 'swallow', nodeType: 'expansion', term: 'swallow', definition: '温水吞服', dimension: 'action', status: 'vague' },
      edge: { relationType: 'action', label: '' },
    },
  ],
  medicine: [
    {
      node: { id: 'prescription', nodeType: 'expansion', term: 'prescription', definition: '医生处方单', dimension: 'downstream', status: 'unlearned' },
      edge: { relationType: 'downstream', label: '' },
    },
    {
      node: { id: 'pharmacy', nodeType: 'expansion', term: 'pharmacy', definition: '配药药房', dimension: 'peer', status: 'mastered' },
      edge: { relationType: 'peer', label: '' },
    },
    {
      node: { id: 'treatment', nodeType: 'expansion', term: 'treatment', definition: '医疗对症方案', dimension: 'downstream', status: 'vague' },
      edge: { relationType: 'downstream', label: '' },
    },
  ],
  doctor: [
    {
      node: { id: 'stethoscope', nodeType: 'expansion', term: 'stethoscope', definition: '听诊器', dimension: 'attribute', status: 'unlearned' },
      edge: { relationType: 'attribute', label: '' },
    },
    {
      node: { id: 'diagnose', nodeType: 'expansion', term: 'diagnose', definition: '问诊诊断', dimension: 'action', status: 'vague' },
      edge: { relationType: 'action', label: '' },
    },
    {
      node: { id: 'surgeon', nodeType: 'expansion', term: 'surgeon', definition: '主刀外科医师', dimension: 'peer', status: 'unlearned' },
      edge: { relationType: 'peer', label: '' },
    },
  ],
  nurse: [
    {
      node: { id: 'injection', nodeType: 'expansion', term: 'injection', definition: '注射打针', dimension: 'action', status: 'unlearned' },
      edge: { relationType: 'action', label: '' },
    },
    {
      node: { id: 'bandage', nodeType: 'expansion', term: 'bandage', definition: '医用绷带', dimension: 'downstream', status: 'unlearned' },
      edge: { relationType: 'downstream', label: '' },
    },
  ],
  hospital: [
    {
      node: { id: 'emergency', nodeType: 'expansion', term: 'emergency', definition: '急诊科通道', dimension: 'peer', status: 'mastered' },
      edge: { relationType: 'peer', label: '' },
    },
    {
      node: { id: 'ward', nodeType: 'expansion', term: 'ward', definition: '住院病房', dimension: 'peer', status: 'unlearned' },
      edge: { relationType: 'peer', label: '' },
    },
    {
      node: { id: 'clinic', nodeType: 'expansion', term: 'clinic', definition: '门诊部/诊所', dimension: 'peer', status: 'mastered' },
      edge: { relationType: 'peer', label: '' },
    },
  ],
  stomach: [
    {
      node: { id: 'nausea', nodeType: 'expansion', term: 'nausea', definition: '恶心/反胃', dimension: 'attribute', status: 'unlearned' },
      edge: { relationType: 'attribute', label: '' },
    },
    {
      node: { id: 'digest', nodeType: 'expansion', term: 'digest', definition: '肠胃消化', dimension: 'action', status: 'vague' },
      edge: { relationType: 'action', label: '' },
    },
  ],
  tooth: [
    {
      node: { id: 'dentist', nodeType: 'expansion', term: 'dentist', definition: '牙科医生', dimension: 'peer', status: 'unlearned' },
      edge: { relationType: 'peer', label: '' },
    },
    {
      node: { id: 'cavity', nodeType: 'expansion', term: 'cavity', definition: '龋齿/蛀牙', dimension: 'attribute', status: 'unlearned' },
      edge: { relationType: 'attribute', label: '' },
    },
  ],

  // 餐饮社交与咖啡时光
  coffee: [
    {
      node: { id: 'espresso', nodeType: 'expansion', term: 'espresso', definition: '意式浓缩', dimension: 'downstream', status: 'mastered' },
      edge: { relationType: 'downstream', label: '' },
    },
    {
      node: { id: 'latte', nodeType: 'expansion', term: 'latte', definition: '拿铁咖啡', dimension: 'peer', status: 'mastered' },
      edge: { relationType: 'peer', label: '' },
    },
    {
      node: { id: 'bean', nodeType: 'expansion', term: 'coffee bean', definition: '烘焙咖啡豆', dimension: 'peer', status: 'unlearned' },
      edge: { relationType: 'peer', label: '' },
    },
  ],
  tea: [
    {
      node: { id: 'brew', nodeType: 'expansion', term: 'brew', definition: '冲泡茶汤', dimension: 'action', status: 'vague' },
      edge: { relationType: 'action', label: '' },
    },
    {
      node: { id: 'teapot', nodeType: 'expansion', term: 'teapot', definition: '泡茶瓷壶', dimension: 'downstream', status: 'unlearned' },
      edge: { relationType: 'downstream', label: '' },
    },
  ],
  bread: [
    {
      node: { id: 'toast', nodeType: 'expansion', term: 'toast', definition: '烤吐司面包', dimension: 'downstream', status: 'mastered' },
      edge: { relationType: 'downstream', label: '' },
    },
    {
      node: { id: 'bakery', nodeType: 'expansion', term: 'bakery', definition: '烘焙面包房', dimension: 'peer', status: 'unlearned' },
      edge: { relationType: 'peer', label: '' },
    },
  ],
  order: [
    {
      node: { id: 'menu', nodeType: 'expansion', term: 'menu', definition: '餐点酒水单', dimension: 'downstream', status: 'mastered' },
      edge: { relationType: 'downstream', label: '' },
    },
    {
      node: { id: 'waiter', nodeType: 'expansion', term: 'waiter', definition: '餐厅服务员', dimension: 'peer', status: 'unlearned' },
      edge: { relationType: 'peer', label: '' },
    },
  ],
  bill: [
    {
      node: { id: 'receipt', nodeType: 'expansion', term: 'receipt', definition: '消费小票', dimension: 'downstream', status: 'mastered' },
      edge: { relationType: 'downstream', label: '' },
    },
    {
      node: { id: 'tip', nodeType: 'expansion', term: 'tip', definition: '服务小费', dimension: 'downstream', status: 'unlearned' },
      edge: { relationType: 'downstream', label: '' },
    },
  ],

  // 交通出行与差旅住宿
  flight: [
    {
      node: { id: 'boarding_pass', nodeType: 'expansion', term: 'boarding pass', definition: '乘机登机牌', dimension: 'downstream', status: 'mastered' },
      edge: { relationType: 'downstream', label: '' },
    },
    {
      node: { id: 'terminal', nodeType: 'expansion', term: 'terminal', definition: '机场航站楼', dimension: 'peer', status: 'unlearned' },
      edge: { relationType: 'peer', label: '' },
    },
    {
      node: { id: 'delay', nodeType: 'expansion', term: 'delay', definition: '航班延误', dimension: 'attribute', status: 'vague' },
      edge: { relationType: 'attribute', label: '' },
    },
  ],
  hotel: [
    {
      node: { id: 'check_in', nodeType: 'expansion', term: 'check-in', definition: '办理入住', dimension: 'action', status: 'mastered' },
      edge: { relationType: 'action', label: '' },
    },
    {
      node: { id: 'reservation', nodeType: 'expansion', term: 'reservation', definition: '客房预订', dimension: 'downstream', status: 'unlearned' },
      edge: { relationType: 'downstream', label: '' },
    },
  ],
  train: [
    {
      node: { id: 'platform', nodeType: 'expansion', term: 'platform', definition: '候车站台', dimension: 'peer', status: 'unlearned' },
      edge: { relationType: 'peer', label: '' },
    },
    {
      node: { id: 'schedule', nodeType: 'expansion', term: 'schedule', definition: '列车时刻表', dimension: 'downstream', status: 'mastered' },
      edge: { relationType: 'downstream', label: '' },
    },
  ],

  // 职场沟通与商务谈判
  meeting: [
    {
      node: { id: 'agenda', nodeType: 'expansion', term: 'agenda', definition: '会议议程表', dimension: 'downstream', status: 'mastered' },
      edge: { relationType: 'downstream', label: '' },
    },
    {
      node: { id: 'minutes', nodeType: 'expansion', term: 'minutes', definition: '会议纪要', dimension: 'downstream', status: 'unlearned' },
      edge: { relationType: 'downstream', label: '' },
    },
    {
      node: { id: 'discuss', nodeType: 'expansion', term: 'discuss', definition: '商榷研讨', dimension: 'action', status: 'vague' },
      edge: { relationType: 'action', label: '' },
    },
  ],
  contract: [
    {
      node: { id: 'clause', nodeType: 'expansion', term: 'clause', definition: '合同法律条款', dimension: 'attribute', status: 'unlearned' },
      edge: { relationType: 'attribute', label: '' },
    },
    {
      node: { id: 'sign', nodeType: 'expansion', term: 'sign', definition: '签字盖章', dimension: 'action', status: 'mastered' },
      edge: { relationType: 'action', label: '' },
    },
  ],
  project: [
    {
      node: { id: 'deadline', nodeType: 'expansion', term: 'deadline', definition: '截止日期', dimension: 'attribute', status: 'vague' },
      edge: { relationType: 'attribute', label: '' },
    },
    {
      node: { id: 'milestone', nodeType: 'expansion', term: 'milestone', definition: '阶段里程碑', dimension: 'downstream', status: 'unlearned' },
      edge: { relationType: 'downstream', label: '' },
    },
  ],

  // 校园学习与学术备考
  exam: [
    {
      node: { id: 'syllabus', nodeType: 'expansion', term: 'syllabus', definition: '考纲大纲', dimension: 'attribute', status: 'unlearned' },
      edge: { relationType: 'attribute', label: '' },
    },
    {
      node: { id: 'score', nodeType: 'expansion', term: 'score', definition: '卷面分数', dimension: 'downstream', status: 'mastered' },
      edge: { relationType: 'downstream', label: '' },
    },
    {
      node: { id: 'review', nodeType: 'expansion', term: 'review', definition: '复习备考', dimension: 'action', status: 'vague' },
      edge: { relationType: 'action', label: '' },
    },
  ],
  thesis: [
    {
      node: { id: 'abstract', nodeType: 'expansion', term: 'abstract', definition: '论文提要', dimension: 'attribute', status: 'unlearned' },
      edge: { relationType: 'attribute', label: '' },
    },
    {
      node: { id: 'citation', nodeType: 'expansion', term: 'citation', definition: '文献引用', dimension: 'downstream', status: 'mastered' },
      edge: { relationType: 'downstream', label: '' },
    },
  ],
}

export const SAMPLE_RUMINATION_ARTICLE = {
  title: 'A Sunday Recipe: From Fresh Produce to Savory Dining',
  scenarioSummary: '在一个阳光明媚的周日早晨，主人公前往农贸市集采买生鲜食材，并亲手烹制经典土豆泥与新鲜蔬菜沙拉。',
  article: `Every Sunday morning, I love visiting the vibrant neighborhood market where fresh produce is displayed in bountiful wooden crates. Today, I picked up several large, firm potatoes, a basket of ripe tomatoes, and a head of green cabbage with crisp leaves.

Back in my kitchen, preparation began with simple culinary steps. To make smooth mashed potato, you must first carefully peel the thin outer skin of each potato, cut them into even chunks, and boil them in lightly salted water until tender. Meanwhile, I chopped the fresh vegetables to toss together a colorful garden salad. The tomatoes were delightfully juicy, bringing a bright burst of natural sweetness to the meal.

Taking the time to turn raw market harvests into nourishing comfort food made the entire weekend feel deeply satisfying.`,
  translation: `每周日早晨，我总喜欢光顾热闹的社区市集，那里新鲜的农副产物整齐陈列在丰盛的木箱中。今天，我挑了几个大而结实的土豆、一篮成熟的西红柿，以及一整颗叶片爽脆的新鲜卷心菜。

回到厨房，料理从最基础的烹饪步骤开始。要制作丝滑细腻的土豆泥，必须先仔细削去每个土豆薄薄的外皮，切成均匀的小块，放入淡盐水中煮熟直至软糯。与此同时，我把新鲜蔬菜切碎，拌制了一份色彩鲜艳的田园沙拉。西红柿格外多汁，为整道餐点注入了明亮清甜的自然滋味。

花些时间把市集采买的原生态食材转化为治愈人心的美味餐点，让整个周末都变得格外充实舒畅。`,
  targetWords: ['potato', 'cabbage', 'peel', 'boil', 'mashed potato', 'juicy', 'crisp'],
  probes: [
    {
      index: 1,
      type: 'cloze',
      typeName: '语境完形检查',
      targetWord: 'peel',
      question: 'Before you boil the potatoes to make soup, you should first [ ______ ] the dirty outer skin.',
      hint: '线索：用刀具刮除或削去果蔬外皮的动作。',
      options: ['A. harvest', 'B. peel', 'C. juicy', 'D. wine'],
      correctAnswer: 'B. peel',
      explanation: '在烹饪语境中，处理土豆皮对应的动作是 peel（削皮/剥皮）；harvest 为采摘收割，juicy 是形容词多汁的，wine 为葡萄酒。',
    },
    {
      index: 2,
      type: 'collocation',
      typeName: '地道搭配检查',
      targetWord: 'mashed potato',
      question: 'Which of the following dishes is prepared by boiling potatoes and crushing them into a creamy texture?',
      hint: '线索：煮熟后压制成泥状的常见西餐主食。',
      options: ['A. tomato juice', 'B. mashed potato', 'C. vinegar salad', 'D. raw cabbage'],
      correctAnswer: 'B. mashed potato',
      explanation: 'mashed potato 是经典的土豆加工下游食品（土豆泥），其动词搭配正是 crush/whip into smooth texture。',
    },
    {
      index: 3,
      type: 'transfer',
      typeName: '迁移生活对答',
      targetWord: 'juicy',
      question: 'Friend: "How do these summer tomatoes taste?" — You: "They are wonderfully [ ______ ] and burst with sweet flavor!"',
      hint: '线索：形容果实成熟、水分饱满的感官特征。',
      options: ['A. boiled', 'B. juicy', 'C. peeled', 'D. chopped'],
      correctAnswer: 'B. juicy',
      explanation: 'juicy 完美描述番茄或水果一口咬下水分充沛的口感特征；boiled（水煮的）和 peeled（削好皮的）是状态词，无法匹配 burst with flavor 的感官语境。',
    },
  ],
}
