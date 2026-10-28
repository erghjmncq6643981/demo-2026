-- V119: 增强单词卡片认知画像、高频搭配与构词积木 Prompt 模板

UPDATE ai_prompt_template
SET content = '请为英语词汇「{{term}}」生成结构化深度学习卡片。注意：1. 目标受众为中文母语学习者，所有释义、短语搭配翻译、例句 translation 必须输出精准地道的【中文】。2. 无论输入的是原形还是屈折变形，词卡主体必须严格基于单词原形（Lemma）生成，term 和 lemma 统一输出标准原形（如输入 slogans 则输出 slogan），并在 inflections 中列出常见变形。3. 重点输出三项深度认知内容：① cognitive_image（母语者认知画像/画面感核心义）：含 title（精炼形象小标题，如“一个偏‘体能充沛’的活力”）、description（通俗生动的母语心智画面描述，解释为什么是这个意思，指出与易混近义词的核心差别）、usage_note（易混拼写/搭配避坑/语体风格，若无则空字符串）；② collocations（高频地道搭配）：精选 3-4 个考试与母语日常中最高频的实用短语，每项含 phrase（纯英文短语）和 meaning（中文搭配释义）；③ etymology（构词积木与词族拓展）：含 components 构词拆解数组（每项含 type [prefix/root/suffix]、text 语素、origin 词源出处如“拉丁语 vigere”、meaning 语素含义），以及 word_family 派生词族列表（每项含 word、part_of_speech、meaning）。只输出合法 JSON，不要输出 Markdown。字段包括 term、lemma、inflections、is_valid、language、phonetic (含 uk, us)、definitions (每项含 part_of_speech、meaning中文释义、english英文释义)、cognitive_image、collocations、etymology、examples (每项含 sentence、translation中文翻译)、synonyms、antonyms、memory_tips。',
    update_time = NOW()
WHERE code = 'english_vocab_card_json';

UPDATE ai_prompt_template
SET content = '请为词汇数组 {{terms}} 批量生成结构化深度学习卡片。注意：1. 目标受众为中文母语学习者，所有释义、短语搭配、例句翻译必须输出精准地道的【中文】。2. 词卡主体严格基于单词原形（Lemma）生成，term 和 lemma 统一填入标准原形，并在 inflections 中列出其变形。3. 每张词卡重点输出三项认知内容：① cognitive_image（母语心智画像，含 title 形象标题、description 画面描述与近义词辨析、usage_note 避坑提示）；② collocations（高频地道短语搭配，精选3-4项，含 phrase 和 meaning）；③ etymology（构词积木 components 数组含 type, text, origin, meaning，以及 word_family 派生词族列表）。只输出合法 JSON，不要输出 Markdown。根字段为 cards 数组，每个输入词对应一项。字段包括 term、lemma、inflections、is_valid、language、phonetic、definitions、cognitive_image、collocations、etymology、examples、synonyms、antonyms、memory_tips。',
    update_time = NOW()
WHERE code = 'english_vocab_cards_batch_json';
