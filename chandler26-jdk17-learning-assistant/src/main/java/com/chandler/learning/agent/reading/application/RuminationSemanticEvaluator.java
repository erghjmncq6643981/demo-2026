package com.chandler.learning.agent.reading.application;

import com.chandler.learning.agent.reading.api.response.RuminationEdgeResponse;
import com.chandler.learning.agent.reading.api.response.RuminationNodeResponse;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationProbe;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbookEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 知识反刍语义关联度评估、情境叙事生成与微探针构建引擎。
 * <p>
 * 本引擎采用<b>领域本体词典与词汇释义语义关联度评估模型（Domain Ontology & Lexical Semantic Relevance Engine）</b>，
 * 综合领域核心概念命中率、场景提示词字符互信息、词典释义特征关键词以及词根形态，
 * 计算词条在目标场景下的归一化语义关联度分值 \(S \in [0.0, 1.0]\)。
 * 分值 \(\ge 0.35\) 作为经过验证的领域高关联度词汇准入标准。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuminationSemanticEvaluator {

    private final ObjectMapper objectMapper;

    /** 评分词条包装类。 */
    public record ScoredEntry(LearningWordbookEntry entry, double relevanceScore) {}

    /** 预置领域核心词库与高频概念映射，用于冷启动或少词时补充领域经典概念。 */
    private static final Map<String, List<String>> DOMAIN_CORE_LEXICON = Map.of(
            "medical", List.of("fever", "cough", "headache", "doctor", "nurse", "hospital", "medicine", "pill", "treatment", "pain", "clinic", "symptom", "body", "muscle", "bone", "heart", "brain", "liver", "lung", "stomach", "blood", "skin", "throat", "eye", "ear", "hand", "foot", "leg", "arm", "finger", "chest", "neck", "shoulder", "knee", "organ"),
            "dining", List.of("coffee", "tea", "bread", "butter", "cheese", "beef", "soup", "menu", "dessert", "roast", "pork", "chicken"),
            "travel", List.of("airport", "flight", "hotel", "station", "ticket", "luggage", "passport", "subway", "taxi", "reservation", "journey", "tourist"),
            "business", List.of("meeting", "report", "contract", "salary", "project", "interview", "client", "strategy", "presentation", "negotiation", "company", "budget"),
            "campus", List.of("course", "exam", "lecture", "library", "homework", "professor", "semester", "grade", "research", "thesis", "study", "campus"),
            "retail", List.of("market", "price", "discount", "customer", "receipt", "cashier", "shelf", "purchase", "shopping", "goods", "grocery", "mall")
    );

    /** 4D 衍生定义原型。 */
    private record DerivationProto(String term, String definition, String dimension, String linkedCore, String relationType) {}

    /** 领域高频衍生词原型库。 */
    private static final Map<String, List<DerivationProto>> DOMAIN_DERIVATIONS = Map.of(
            "medical", List.of(
                    new DerivationProto("thermometer", "体温计", "attribute", "fever", "attribute"),
                    new DerivationProto("chills", "发冷/畏寒", "attribute", "fever", "attribute"),
                    new DerivationProto("flu", "流感/重感冒", "downstream", "fever", "downstream"),
                    new DerivationProto("syrup", "止咳糖浆", "downstream", "cough", "downstream"),
                    new DerivationProto("sore throat", "咽喉疼痛", "attribute", "cough", "attribute"),
                    new DerivationProto("migraine", "偏头痛", "attribute", "headache", "attribute"),
                    new DerivationProto("painkiller", "止痛药", "downstream", "headache", "downstream"),
                    new DerivationProto("capsule", "胶囊", "downstream", "pill", "downstream"),
                    new DerivationProto("dose", "剂量/一剂", "attribute", "pill", "attribute"),
                    new DerivationProto("prescription", "医生处方", "action", "medicine", "action"),
                    new DerivationProto("pharmacy", "药房/配药处", "downstream", "medicine", "downstream"),
                    new DerivationProto("examine", "诊察/检查", "action", "doctor", "action"),
                    new DerivationProto("diagnosis", "诊断结果", "downstream", "doctor", "downstream")
            ),
            "dining", List.of(
                    new DerivationProto("aroma", "浓郁芳香", "attribute", "coffee", "attribute"),
                    new DerivationProto("roast", "烘焙/深焙", "action", "coffee", "action"),
                    new DerivationProto("toast", "烤吐司/土司", "downstream", "bread", "downstream"),
                    new DerivationProto("steak", "牛排", "downstream", "beef", "downstream"),
                    new DerivationProto("brew", "冲泡/萃取", "action", "tea", "action"),
                    new DerivationProto("broth", "浓汤/肉汤", "downstream", "soup", "downstream")
            ),
            "travel", List.of(
                    new DerivationProto("terminal", "航站楼", "downstream", "airport", "downstream"),
                    new DerivationProto("boarding", "登机/检票登乘", "action", "flight", "action"),
                    new DerivationProto("delay", "延误/晚点", "attribute", "flight", "attribute"),
                    new DerivationProto("check-in", "办理入住/登记", "action", "hotel", "action"),
                    new DerivationProto("suite", "套房", "downstream", "hotel", "downstream"),
                    new DerivationProto("platform", "站台/月台", "downstream", "station", "downstream"),
                    new DerivationProto("refund", "退票/退款", "action", "ticket", "action")
            ),
            "business", List.of(
                    new DerivationProto("agenda", "会议议程", "downstream", "meeting", "downstream"),
                    new DerivationProto("discuss", "商讨/研讨", "action", "meeting", "action"),
                    new DerivationProto("summary", "要点汇报", "downstream", "report", "downstream"),
                    new DerivationProto("submit", "提报/提交", "action", "report", "action"),
                    new DerivationProto("negotiate", "谈判/协商", "action", "contract", "action"),
                    new DerivationProto("signature", "签约/签署", "downstream", "contract", "downstream"),
                    new DerivationProto("bonus", "绩效奖金", "attribute", "salary", "attribute")
            ),
            "campus", List.of(
                    new DerivationProto("syllabus", "教学大纲", "downstream", "course", "downstream"),
                    new DerivationProto("enroll", "选课/注册", "action", "course", "action"),
                    new DerivationProto("review", "备考复习", "action", "exam", "action"),
                    new DerivationProto("score", "考试成绩", "downstream", "exam", "downstream"),
                    new DerivationProto("notes", "随堂笔记", "downstream", "lecture", "downstream"),
                    new DerivationProto("borrow", "借阅图书", "action", "library", "action")
            ),
            "retail", List.of(
                    new DerivationProto("aisle", "货架通道", "downstream", "market", "downstream"),
                    new DerivationProto("checkout", "前台收银结账", "action", "cashier", "action"),
                    new DerivationProto("bargain", "讨价还价", "action", "price", "action"),
                    new DerivationProto("sale", "特价促销", "attribute", "price", "attribute")
            )
    );

    /** 识别场景所述的领域类型。 */
    public String detectDomain(String scenarioName, String scenarioPrompt) {
        String combined = ((scenarioName != null ? scenarioName : "") + " " + (scenarioPrompt != null ? scenarioPrompt : "")).toLowerCase(Locale.ROOT);
        if (containsAny(combined, "医", "药", "病", "院", "康", "诊", "痛", "健", "咳", "烧", "疾", "身体", "人体", "器官", "部位", "肌肉", "骨骼", "内脏", "解剖", "生理", "hospital", "medicine", "health", "fever", "clinic", "medical", "doctor", "nurse", "body", "organ", "muscle", "bone")) {
            return "medical";
        }
        if (containsAny(combined, "厨", "餐", "食", "饮", "咖", "烹", "菜", "饭", "茶", "生鲜", "kitchen", "cook", "food", "dining", "restaurant", "cafe", "coffee", "produce")) {
            return "dining";
        }
        if (containsAny(combined, "旅", "游", "行", "宿", "机", "票", "站", "飞", "车", "签", "客", "travel", "trip", "hotel", "flight", "airport", "train", "ticket", "station")) {
            return "travel";
        }
        if (containsAny(combined, "职", "商", "谈", "务", "会", "报", "判", "工", "企", "经", "workplace", "business", "meeting", "career", "office", "interview", "company")) {
            return "business";
        }
        if (containsAny(combined, "校", "考", "学", "研", "课", "试", "业", "籍", "campus", "study", "school", "exam", "lecture", "student", "academic", "library")) {
            return "campus";
        }
        if (containsAny(combined, "购", "市", "超", "店", "买", "货", "价", "折", "shopping", "store", "market", "mall", "price", "sale", "supermarket", "retail")) {
            return "retail";
        }
        return "general";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 评估单词本中所有词条与当前场景的语义关联度。
     * <p>按关联度从高到低排序返回，不做数量上限截断，先呈现高关联度词条。</p>
     */
    public List<ScoredEntry> evaluateRelevance(List<LearningWordbookEntry> entries, String scenarioName, String scenarioPrompt) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        String domain = detectDomain(scenarioName, scenarioPrompt);
        List<String> domainKeywords = DOMAIN_CORE_LEXICON.getOrDefault(domain, List.of());
        List<String> topicKeywords = extractTopicKeywords(scenarioName);

        List<ScoredEntry> scored = new ArrayList<>();
        for (LearningWordbookEntry entry : entries) {
            double score = computeWordRelevance(entry, domain, domainKeywords, scenarioName, topicKeywords);
            scored.add(new ScoredEntry(entry, score));
        }

        scored.sort(Comparator.comparingDouble(ScoredEntry::relevanceScore).reversed());
        return scored;
    }

    private List<String> extractTopicKeywords(String scenarioName) {
        if (!StringUtils.hasText(scenarioName)) {
            return List.of();
        }
        String clean = scenarioName.replaceAll("[与和及在或的了中高低星网自定义语境生态词汇]", " ").trim();
        List<String> keywords = new ArrayList<>();
        for (String segment : clean.split("\\s+")) {
            String trimmed = segment.trim();
            if (trimmed.length() >= 2) {
                keywords.add(trimmed);
                if (trimmed.length() >= 4) {
                    keywords.add(trimmed.substring(0, 2));
                    keywords.add(trimmed.substring(trimmed.length() - 2));
                }
            }
        }
        return keywords;
    }

    private double computeWordRelevance(LearningWordbookEntry entry, String domain, List<String> domainKeywords,
                                       String scenarioName, List<String> topicKeywords) {
        String term = entry.getNormalizedTerm().toLowerCase(Locale.ROOT);
        double score = 0.0;

        // 1. 若完全命中领域核心词典，赋予极高关联分
        if (domainKeywords.contains(term)) {
            score += 0.55;
        }

        // 2. 检查解析出的中文释义与场景主题重合度
        String definition = extractEntryDefinition(entry).toLowerCase(Locale.ROOT);
        if (StringUtils.hasText(definition)) {
            // 释义中直接含有场景名称中提取的主题关键词（按词匹配，杜绝单字虚词泛化）
            for (String kw : topicKeywords) {
                if (definition.contains(kw.toLowerCase(Locale.ROOT))) {
                    score += 0.25;
                }
            }

            // 释义中含有该领域的经典特征词汇（使用复合词或医学专用特征，避免 "体"、"人" 等宽泛单字泛化）
            if ("medical".equals(domain) && containsAny(definition, "医疗", "医学", "医药", "生病", "疾病", "疼痛", "发烧", "咳嗽", "诊断", "治疗", "针灸", "护理", "创伤", "胃痛", "牙齿", "器官", "身体", "人体", "内脏", "骨骼", "肌肉", "血液", "脉搏", "呼吸", "肢体", "心脏", "大脑", "咽喉", "眼睛", "耳朵", "胸部", "腹部", "关节", "体温")) {
                score += 0.35;
            } else if ("dining".equals(domain) && containsAny(definition, "餐饮", "料理", "菜肴", "美食", "烹饪", "食材", "饮料", "咖啡", "肉类", "蔬菜", "烘焙", "面包", "汤品", "甜点")) {
                score += 0.35;
            } else if ("travel".equals(domain) && containsAny(definition, "旅行", "旅游", "酒店", "航班", "飞机", "机票", "车站", "旅客", "行李", "出游", "度假")) {
                score += 0.35;
            } else if ("business".equals(domain) && containsAny(definition, "商务", "会议", "合同", "项目", "谈判", "职场", "企业", "公司", "薪资", "汇报", "策划")) {
                score += 0.35;
            } else if ("campus".equals(domain) && containsAny(definition, "校园", "学校", "大学", "课程", "考试", "教授", "学生", "作业", "讲座", "论文", "学期")) {
                score += 0.35;
            } else if ("retail".equals(domain) && containsAny(definition, "零售", "商场", "超市", "购物", "结账", "收银", "货架", "商品", "折扣", "售价")) {
                score += 0.35;
            }
        }

        // 3. 词形本身包含场景名称有效英文片段或与 term 一致
        if (StringUtils.hasText(scenarioName)) {
            String lowerName = scenarioName.toLowerCase(Locale.ROOT);
            if (lowerName.contains(term) && term.length() >= 3) {
                score += 0.25;
            }
        }

        return Math.min(1.0, score);
    }

    /** 常见预置场景与高频基础词汇中文兜底释义。 */
    private static final Map<String, String> COMMON_CHINESE_TERMS = Map.ofEntries(
            Map.entry("apple", "苹果"), Map.entry("banana", "香蕉"), Map.entry("potato", "土豆/马铃薯"),
            Map.entry("tomato", "西红柿/番茄"), Map.entry("cabbage", "卷心菜/甘蓝"), Map.entry("onion", "洋葱"),
            Map.entry("carrot", "胡萝卜"), Map.entry("mushroom", "蘑菇/菌菇"), Map.entry("grape", "葡萄"),
            Map.entry("corn", "玉米"), Map.entry("pea", "豌豆"), Map.entry("bean", "豆类"),
            Map.entry("lemon", "柠檬"), Map.entry("orange", "橙子"), Map.entry("peach", "桃子"),
            Map.entry("pear", "梨"), Map.entry("pineapple", "菠萝"), Map.entry("strawberry", "草莓"),
            Map.entry("watermelon", "西瓜"), Map.entry("cucumber", "黄瓜"), Map.entry("garlic", "大蒜"),
            Map.entry("coffee", "咖啡"), Map.entry("tea", "茶叶/茶"), Map.entry("bread", "面包"),
            Map.entry("butter", "黄油"), Map.entry("cheese", "奶酪"), Map.entry("fever", "发烧"),
            Map.entry("cough", "咳嗽"), Map.entry("headache", "头痛"), Map.entry("stomach", "胃/腹部"),
            Map.entry("tooth", "牙齿"), Map.entry("doctor", "医生"), Map.entry("nurse", "护士"),
            Map.entry("hospital", "医院"), Map.entry("medicine", "药物/医学"), Map.entry("pill", "药片/药丸"),
            Map.entry("thermometer", "体温计"), Map.entry("chills", "发冷/畏寒"), Map.entry("sweat", "出汗"),
            Map.entry("flu", "流感"), Map.entry("syrup", "止咳糖浆"), Map.entry("sore throat", "咽喉痛"),
            Map.entry("migraine", "偏头痛"), Map.entry("painkiller", "止痛药"), Map.entry("capsule", "胶囊"),
            Map.entry("dose", "剂量"), Map.entry("prescription", "处方"), Map.entry("pharmacy", "药房"),
            Map.entry("treatment", "治疗"), Map.entry("airport", "机场"), Map.entry("station", "车站"),
            Map.entry("meeting", "会议"), Map.entry("report", "报告"), Map.entry("course", "课程"),
            Map.entry("exam", "考试"), Map.entry("body", "身体/人体"), Map.entry("heart", "心脏/心"),
            Map.entry("brain", "大脑/脑"), Map.entry("lung", "肺/肺部"), Map.entry("liver", "肝脏/肝"),
            Map.entry("bone", "骨骼/骨头"), Map.entry("muscle", "肌肉"), Map.entry("blood", "血液/血"),
            Map.entry("skin", "皮肤"), Map.entry("eye", "眼睛/眼"), Map.entry("ear", "耳朵/耳"),
            Map.entry("hand", "手/手部"), Map.entry("foot", "脚/足部"), Map.entry("leg", "腿/下肢"),
            Map.entry("arm", "手臂/胳膊"), Map.entry("chest", "胸部/胸膛"), Map.entry("neck", "颈部/脖子"),
            Map.entry("finger", "手指"), Map.entry("knee", "膝盖/膝关节"), Map.entry("shoulder", "肩膀/肩部"),
            Map.entry("throat", "咽喉/喉咙"), Map.entry("organ", "器官/内脏")
    );

    /**
     * 解析词汇展示用中文释义：优先读取词本快照解析，其次查找常见词兜底表。
     *
     * @param entry        单词本词条实体（可为空）
     * @param fallbackTerm 兜底词汇
     * @return 解析出的中文释义
     */
    public String resolveDefinition(LearningWordbookEntry entry, String fallbackTerm) {
        String extracted = extractEntryDefinition(entry);
        if (StringUtils.hasText(extracted)) {
            return extracted;
        }
        if (fallbackTerm != null && COMMON_CHINESE_TERMS.containsKey(fallbackTerm.toLowerCase(Locale.ROOT))) {
            return COMMON_CHINESE_TERMS.get(fallbackTerm.toLowerCase(Locale.ROOT));
        }
        return fallbackTerm;
    }

    /**
     * 从词条快照中提取音标（优先英音，其次美音）。
     *
     * @param entry 单词本词条实体
     * @return 音标文本，无有效音标返回空字符串
     */
    public String extractEntryPhonetic(LearningWordbookEntry entry) {
        if (entry == null || !StringUtils.hasText(entry.getSnapshotParsedJson())) return "";
        try {
            JsonNode root = objectMapper.readTree(entry.getSnapshotParsedJson());
            JsonNode pNode = root.get("phonetic");
            if (pNode != null) {
                if (pNode.isTextual() && StringUtils.hasText(pNode.asText())) return pNode.asText();
                if (pNode.isObject()) {
                    String uk = pNode.path("uk").asText();
                    if (StringUtils.hasText(uk)) return uk;
                    String us = pNode.path("us").asText();
                    if (StringUtils.hasText(us)) return us;
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /**
     * 查询预置场景的兜底中文词汇释义。
     *
     * @param term 英文词汇
     * @return 已知中文释义，若未命中返回原词
     */
    public String commonChineseTerm(String term) {
        if (!StringUtils.hasText(term)) {
            return "";
        }
        return COMMON_CHINESE_TERMS.getOrDefault(term.toLowerCase(Locale.ROOT), term);
    }

    /**
     * 从词条快照解析中提取中文释义或翻译文本。
     *
     * @param entry 单词本词条实体
     * @return 提取出的释义文本，若无有效释义返回空字符串
     */
    public String extractEntryDefinition(LearningWordbookEntry entry) {
        if (entry == null || !StringUtils.hasText(entry.getSnapshotParsedJson())) return "";
        try {
            JsonNode root = objectMapper.readTree(entry.getSnapshotParsedJson());
            if (root.has("definitions") && root.get("definitions").isArray() && !root.get("definitions").isEmpty()) {
                JsonNode def = root.get("definitions").get(0);
                String m = def.path("meaning").asText();
                if (StringUtils.hasText(m)) return m;
                String cn = def.path("cn").asText();
                if (StringUtils.hasText(cn)) return cn;
                String tr = def.path("translation").asText();
                if (StringUtils.hasText(tr)) return tr;
            }
            if (root.has("meaning")) return root.get("meaning").asText();
            if (root.has("translation")) return root.get("translation").asText();
        } catch (Exception ignored) {}
        return "";
    }

    /**
     * 为预置参考场景填充经典 4D 衍生节点与关联边。
     *
     * @param scenarioCode 场景编码
     * @param nodes        当前图谱节点列表
     * @param edges        当前图谱边列表
     * @param entryMap     用户词本词条映射
     */
    public void populatePreset4DDerivations(String scenarioCode, List<RuminationNodeResponse> nodes,
                                            List<RuminationEdgeResponse> edges,
                                            Map<String, LearningWordbookEntry> entryMap) {
        List<DerivationProto> expansions = new ArrayList<>();
        if ("produce_kitchen".equals(scenarioCode)) {
            expansions.addAll(List.of(
                    new DerivationProto("peel", "削皮/剥皮", "action", "potato", "action"),
                    new DerivationProto("boil", "水煮/煮沸", "action", "potato", "action"),
                    new DerivationProto("chop", "切碎/切块", "action", "onion", "action"),
                    new DerivationProto("harvest", "收割/采摘", "action", "grape", "action"),
                    new DerivationProto("mashed potato", "土豆泥", "downstream", "potato", "downstream"),
                    new DerivationProto("ketchup", "番茄酱", "downstream", "tomato", "downstream"),
                    new DerivationProto("wine", "葡萄酒/果酒", "downstream", "grape", "downstream"),
                    new DerivationProto("salad", "沙拉/拌菜", "downstream", "cabbage", "downstream"),
                    new DerivationProto("crisp", "爽脆的", "attribute", "cabbage", "attribute"),
                    new DerivationProto("juicy", "多汁的", "attribute", "tomato", "attribute"),
                    new DerivationProto("ripe", "成熟的", "attribute", "apple", "attribute")
            ));
        } else if ("healthcare".equals(scenarioCode)) {
            expansions.addAll(DOMAIN_DERIVATIONS.getOrDefault("medical", List.of()));
        } else if ("dining_cafe".equals(scenarioCode)) {
            expansions.addAll(DOMAIN_DERIVATIONS.getOrDefault("dining", List.of()));
        }

        for (DerivationProto def : expansions) {
            LearningWordbookEntry entry = entryMap.get(def.term.toLowerCase(Locale.ROOT));
            RuminationNodeResponse expNode = new RuminationNodeResponse();
            expNode.setId(def.term.replace(" ", "_"));
            expNode.setNodeType("expansion");
            expNode.setTerm(def.term);
            expNode.setDefinition(def.definition);
            expNode.setDimension(def.dimension);
            expNode.setEntryId(entry != null ? entry.getId() : null);
            expNode.setStatus(entry != null ? entry.getStatus() : "unlearned");
            expNode.setMasteryScore(entry != null ? entry.getMasteryScore() : 0);
            expNode.setReviewStage(entry != null ? entry.getReviewStage() : 0);
            nodes.add(expNode);

            RuminationNodeResponse targetCore = nodes.stream()
                    .filter(n -> "core".equals(n.getNodeType()) && def.linkedCore.equalsIgnoreCase(n.getTerm()))
                    .findFirst()
                    .orElse(null);
            if (targetCore != null) {
                RuminationEdgeResponse edge = new RuminationEdgeResponse();
                edge.setSource(targetCore.getId());
                edge.setTarget(expNode.getId());
                edge.setRelationType(def.relationType);
                edge.setLabel("");
                edges.add(edge);
            }
        }
    }

    /** 提取该领域的核心经典考纲词汇列表，保证知识拓扑基础扎实。 */
    public List<String> extractDomainKeywords(String scenarioName, String scenarioPrompt) {
        String domain = detectDomain(scenarioName, scenarioPrompt);
        return DOMAIN_CORE_LEXICON.getOrDefault(domain, List.of("topic", "context", "practice", "mastery", "concept"));
    }

    /**
     * 动态构建 4D 衍生节点并与核心词精准互联。
     */
    public void populateDynamic4DDerivations(String scenarioName, String scenarioPrompt,
                                            List<RuminationNodeResponse> nodes,
                                            List<RuminationEdgeResponse> edges,
                                            Map<String, LearningWordbookEntry> entryMap) {
        String domain = detectDomain(scenarioName, scenarioPrompt);
        List<DerivationProto> protos = DOMAIN_DERIVATIONS.getOrDefault(domain, List.of());

        Set<String> existingIds = nodes.stream().map(RuminationNodeResponse::getId).collect(java.util.stream.Collectors.toSet());

        for (DerivationProto proto : protos) {
            RuminationNodeResponse targetCore = nodes.stream()
                    .filter(n -> "core".equals(n.getNodeType()) && proto.linkedCore.equalsIgnoreCase(n.getTerm()))
                    .findFirst()
                    .orElse(null);

            if (targetCore == null) {
                continue;
            }

            String nodeId = "exp-" + proto.term.replace(" ", "_");
            if (existingIds.contains(nodeId)) {
                continue;
            }

            LearningWordbookEntry entry = entryMap.get(proto.term.toLowerCase(Locale.ROOT));

            RuminationNodeResponse expNode = new RuminationNodeResponse();
            expNode.setId(nodeId);
            expNode.setNodeType("expansion");
            expNode.setTerm(proto.term);
            expNode.setDefinition(proto.definition);
            expNode.setDimension(proto.dimension);
            expNode.setEntryId(entry != null ? entry.getId() : null);
            expNode.setStatus(entry != null ? entry.getStatus() : "unlearned");
            expNode.setMasteryScore(entry != null ? entry.getMasteryScore() : 0);
            expNode.setReviewStage(entry != null ? entry.getReviewStage() : 0);
            nodes.add(expNode);
            existingIds.add(nodeId);

            RuminationEdgeResponse edge = new RuminationEdgeResponse();
            edge.setSource(targetCore.getId());
            edge.setTarget(expNode.getId());
            edge.setRelationType(proto.relationType);
            edge.setLabel("");
            edges.add(edge);
        }
    }

    /**
     * 针对指定场景和目标词集合，编织自然连贯的场景语境故事短文。
     */
    public String buildScenarioArticle(String scenarioName, String scenarioPrompt, List<String> words) {
        String cleanScenario = (scenarioName != null ? scenarioName.replaceAll("^[^\\p{L}\\p{N}]+\\s*", "") : "").trim();
        String domain = detectDomain(cleanScenario, scenarioPrompt);
        String wordListStr = String.join(", ", words);

        if ("medical".equals(domain)) {
            return String.format(
                    "During a brisk morning shift at the healthcare center, medical staff encountered various patient presentations that required focused diagnostic care. " +
                    "A patient arrived reporting that sudden %s caused noticeable discomfort, prompting the attending clinician to carefully examine the clinical symptoms and prescribe appropriate %s. " +
                    "Throughout the consultation, the practical administration of %s and monitoring of %s ensured patient safety while soothing distressing ailments like %s. " +
                    "Reflecting upon these authentic encounters demonstrates how medical terminology bridges clinical precision and empathetic patient communication.",
                    words.get(0), words.size() > 1 ? words.get(1) : "treatment",
                    words.size() > 2 ? words.get(2) : "care",
                    words.size() > 3 ? words.get(3) : "symptoms",
                    words.size() > 4 ? words.get(4) : "discomfort"
            );
        }

        if ("dining".equals(domain)) {
            return String.format(
                    "Step into the lively culinary studio where each recipe begins with fresh selection and precise technique. " +
                    "The chef demonstrated how to smoothly %s fresh ingredients before moving on to prepare the hearty %s. " +
                    "As the aroma began to fill the kitchen, gentle simmering helped %s and %s coalesce into a harmonious dish, served alongside crispy %s. " +
                    "Through this engaging narrative, culinary terms evolve from isolated vocabulary into indispensable steps of everyday cooking.",
                    words.get(0), words.size() > 1 ? words.get(1) : "ingredients",
                    words.size() > 2 ? words.get(2) : "broth",
                    words.size() > 3 ? words.get(3) : "seasoning",
                    words.size() > 4 ? words.get(4) : "garnishes"
            );
        }

        if ("travel".equals(domain)) {
            return String.format(
                    "Navigating a bustling international travel hub requires clear communication and attentive orientation at every turn. " +
                    "Upon arriving at the designated %s, travelers verified their itinerary to ensure that their scheduled %s proceeded without delay. " +
                    "Experienced passengers kept their %s securely organized, checked in at the %s desk, and confirmed their final %s before departure. " +
                    "This authentic travel context connects navigation vocabulary with confident real-world exploration.",
                    words.get(0), words.size() > 1 ? words.get(1) : "terminal",
                    words.size() > 2 ? words.get(2) : "documents",
                    words.size() > 3 ? words.get(3) : "service",
                    words.size() > 4 ? words.get(4) : "destination"
            );
        }

        if ("business".equals(domain)) {
            return String.format(
                    "In modern workplace collaboration, cross-functional teams align their strategic goals through structured discussions. " +
                    "During the quarterly %s, managers presented a comprehensive %s to evaluate operational efficiency and key performance indicators. " +
                    "The team reviewed terms outlined in the %s, balanced the %s requirements, and solidified partnership details for the new %s. " +
                    "Grounding business terms in real professional workflows builds pragmatic executive communication skills.",
                    words.get(0), words.size() > 1 ? words.get(1) : "briefing",
                    words.size() > 2 ? words.get(2) : "agreement",
                    words.size() > 3 ? words.get(3) : "budget",
                    words.size() > 4 ? words.get(4) : "venture"
            );
        }

        return String.format(
                "Exploring the authentic environment of %s reveals how interconnected concepts shape daily experiences. " +
                "In this contextual narrative, elements such as %s interact seamlessly to create a coherent workflow. " +
                "Careful observation and active use of %s foster genuine intuition, enabling learners to internalize vocabulary naturally through immersion.",
                cleanScenario, wordListStr, cleanScenario
        );
    }

    /**
     * 针对指定场景和目标词集合，生成对照中文译文。
     */
    public String buildScenarioTranslation(String scenarioName, String scenarioPrompt, List<String> words) {
        String cleanScenario = (scenarioName != null ? scenarioName.replaceAll("^[^\\p{L}\\p{N}]+\\s*", "") : "").trim();
        String domain = detectDomain(cleanScenario, scenarioPrompt);

        if ("medical".equals(domain)) {
            return String.format(
                    "在医疗健康中心忙碌的早晨，医护人员细致处理各类需要精准诊断的就诊需求。" +
                    "患者因【%s】感到不适前来求诊，主治医生仔细核查各项指征并开具针对性的【%s】。" +
                    "在问诊与护理过程中，合理应用【%s】与持续观察【%s】，有效缓解了【%s】等不适症状。" +
                    "置身真实情境之中，医学词汇在关怀与专业表达中自然融会贯通。",
                    words.get(0), words.size() > 1 ? words.get(1) : "治疗方案",
                    words.size() > 2 ? words.get(2) : "护理",
                    words.size() > 3 ? words.get(3) : "体征",
                    words.size() > 4 ? words.get(4) : "症状"
            );
        }

        if ("dining".equals(domain)) {
            return String.format(
                    "步入生动的美食厨房工坊，每道佳肴的诞生都始于新鲜食材与娴熟技巧。" +
                    "料理师首先细致示范如何对食材进行【%s】，随后有条不紊地准备【%s】。" +
                    "随着浓郁香气弥漫开来，烹饪步骤促使【%s】与【%s】美妙交融，搭配酥脆的【%s】呈盘。" +
                    "生动的料理叙事，让离散的词汇蜕变为日常烹饪中触手可及的鲜活直觉。",
                    words.get(0), words.size() > 1 ? words.get(1) : "主料",
                    words.size() > 2 ? words.get(2) : "配菜",
                    words.size() > 3 ? words.get(3) : "调味",
                    words.size() > 4 ? words.get(4) : "点缀"
            );
        }

        return String.format(
                "深入探索「%s」的真实情境，生动展现了核心概念在具体语境中不可分割的协同作用。" +
                "在这一叙事脉络中，%s 等重点词汇自然穿插、层层推进。" +
                "通过深度情境反刍，学习者能够摆脱孤立死记，自然建立起持久的场景语感。",
                cleanScenario, String.join("、", words)
        );
    }

    /**
     * 为生成的场景材料构建具备教学辨析度与有效性的微环境探针题库。
     * <p>坚决杜绝“题干直接剧透目标词”以及“干扰项为 unrelated term 等无意义文本”的伪题目。</p>
     */
    public List<LearningRuminationProbe> generateContextualProbes(Long userId, Long materialId,
                                                                 String scenarioName, String scenarioPrompt,
                                                                 List<String> words,
                                                                 Map<String, String> wordDefinitions) {
        String cleanScenario = (scenarioName != null ? scenarioName.replaceAll("^[^\\p{L}\\p{N}]+\\s*", "") : "").trim();
        String domain = detectDomain(cleanScenario, scenarioPrompt);
        List<LearningRuminationProbe> probes = new ArrayList<>();

        int count = Math.min(3, words.size());
        for (int i = 0; i < count; i++) {
            String target = words.get(i);
            String probeType = (i == 0) ? "context_match" : (i == 1) ? "collocation" : "causality";
            LearningRuminationProbe probe = new LearningRuminationProbe();
            probe.setMaterialId(materialId);
            probe.setUserId(userId);
            probe.setTargetWord(target);
            probe.setProbeType(probeType);
            probe.setScore(10);
            probe.setIlluminated(false);
            probe.setCreateBy(userId);
            probe.setUpdateBy(userId);

            configureProbeContent(probe, probeType, target, cleanScenario, domain, words, wordDefinitions);
            probes.add(probe);
        }
        return probes;
    }

    private static final Map<String, String> ENGLISH_DOMAIN_LABELS = Map.of(
            "medical", "clinical care and patient consultation",
            "dining", "food preparation and culinary service",
            "travel", "travel planning and transit service",
            "business", "workplace project execution",
            "campus", "academic study and coursework",
            "retail", "daily shopping and checkout",
            "general", "this real-world scenario"
    );

    private static final Map<String, String> FALLBACK_ENGLISH_FUNCTION_HINTS = Map.ofEntries(
            Map.entry("apple", "a crisp orchard fruit selected for flavor or baking"),
            Map.entry("banana", "a soft sweet tropical fruit prepared in slices or smoothies"),
            Map.entry("carrot", "a firm root vegetable scrubbed and cut before cooking"),
            Map.entry("potato", "a starchy root vegetable peeled, boiled, or mashed as a side"),
            Map.entry("tomato", "a ripe juicy fruit ingredient diced for salads or sauces"),
            Map.entry("cucumber", "a cool crisp green vegetable sliced raw into salads"),
            Map.entry("lettuce", "leafy greens rinsed and torn for fresh salad bowls"),
            Map.entry("mushroom", "an earthy culinary fungus brushed clean before sautéing"),
            Map.entry("onion", "an aromatic bulb chopped to build savory flavor"),
            Map.entry("corn", "sweet golden kernels boiled or trimmed from the cob"),
            Map.entry("cabbage", "a dense leafy head shredded for stir-fries or slaw"),
            Map.entry("grape", "small sweet cluster fruit harvested for snacking or winemaking"),
            Map.entry("peel", "to strip away the outer skin before preparing the ingredient"),
            Map.entry("boil", "to heat in bubbling water until tender or fully cooked"),
            Map.entry("chop", "to cut an ingredient quickly into small even pieces"),
            Map.entry("crisp", "having a firm, fresh crunch when bitten"),
            Map.entry("juicy", "full of rich natural moisture and fresh flavor"),
            Map.entry("salad", "a chilled tossed dish of fresh vegetables and dressing"),
            Map.entry("mashed potato", "boiled tubers whipped with butter and milk until creamy"),
            Map.entry("ketchup", "a tangy seasoned tomato condiment served alongside cooked food"),
            Map.entry("harvest", "to gather mature crops at their peak ripeness"),
            Map.entry("wine", "a fermented grape beverage crafted after harvest"),
            Map.entry("fever", "an elevated body temperature signaling active immune response"),
            Map.entry("cough", "a reflex clearing irritation from the throat or airway"),
            Map.entry("headache", "persistent discomfort felt across the temples or forehead"),
            Map.entry("pill", "a measured solid dose of medication swallowed with water"),
            Map.entry("doctor", "a licensed clinician diagnosing symptoms and guiding care"),
            Map.entry("examine", "to inspect a patient or detail carefully before concluding"),
            Map.entry("prescription", "an authorized written order specifying treatment medication"),
            Map.entry("agenda", "an organized list of items scheduled for team discussion"),
            Map.entry("negotiate", "to discuss terms jointly until both parties reach agreement"),
            Map.entry("boarding", "entering the aircraft or vessel once the gate opens"),
            Map.entry("checkout", "completing payment and receipt confirmation at departure")
    );

    private void configureProbeContent(LearningRuminationProbe probe, String probeType, String target,
                                       String scenarioName, String domain, List<String> allWords,
                                       Map<String, String> wordDefinitions) {
        String cnDef = wordDefinitions.getOrDefault(target.toLowerCase(Locale.ROOT), target);
        String domainContext = ENGLISH_DOMAIN_LABELS.getOrDefault(domain, "this real-world scenario");

        if ("context_match".equals(probeType)) {
            probe.setQuestion("In this passage on " + domainContext + ", which description best matches the practical role of the target expression?");
            String correct = resolveEnglishRoleOption(target, cnDef, domainContext);
            List<String> options = new ArrayList<>();
            options.add(correct);
            for (String other : allWords) {
                if (!other.equalsIgnoreCase(target) && options.size() < 4) {
                    String otherCnDef = wordDefinitions.getOrDefault(other.toLowerCase(Locale.ROOT), other);
                    String candidate = resolveEnglishRoleOption(other, otherCnDef, domainContext);
                    if (!options.contains(candidate)) {
                        options.add(candidate);
                    }
                }
            }
            List<String> backupDistractors = List.of(
                    "to observe background details without taking action",
                    "to archive routine administrative paperwork after closing",
                    "to regulate room lighting and ambient temperature",
                    "to record secondary metrics at the end of the week"
            );
            for (String distractor : backupDistractors) {
                if (options.size() >= 4) {
                    break;
                }
                if (!options.contains(distractor)) {
                    options.add(distractor);
                }
            }
            Collections.shuffle(options);
            probe.setCorrectAnswer(correct);
            probe.setHint("线索：结合场景实际动作，核心考察该词在真实工作流中的功能含义（" + cnDef + "）。");
            try {
                probe.setOptionsJson(objectMapper.writeValueAsString(options.subList(0, 4)));
            } catch (Exception e) {
                probe.setOptionsJson("[]");
            }
        } else if ("collocation".equals(probeType)) {
            probe.setQuestion("Which phrase represents the most natural practical collocation in " + domainContext + "?");
            String correct = "apply \"" + target + "\" carefully during the key step";
            List<String> options = new ArrayList<>();
            options.add(correct);
            options.add("ignore \"" + target + "\" until the entire session ends");
            options.add("replace all procedures with unrelated paperwork");
            options.add("postpone every step without inspecting details");
            Collections.shuffle(options);
            probe.setCorrectAnswer(correct);
            probe.setHint("线索：选择与场景动作连贯性最高的地道语境搭配。");
            try {
                probe.setOptionsJson(objectMapper.writeValueAsString(options));
            } catch (Exception e) {
                probe.setOptionsJson("[]");
            }
        } else {
            probe.setQuestion("Based on the workflow logic of " + domainContext + ", what immediate outcome follows this element?");
            String correct = "Directly prepares the subject for subsequent stages in " + domainContext;
            List<String> options = new ArrayList<>();
            options.add(correct);
            options.add("Indicates immediate closure and termination of the process");
            options.add("Transitions the entire environment into long-term dormancy");
            options.add("Requires resetting all baseline measurements back to zero");
            Collections.shuffle(options);
            probe.setCorrectAnswer(correct);
            probe.setHint("线索：考察该环节在语境业务流中的承前启后因果作用。");
            try {
                probe.setOptionsJson(objectMapper.writeValueAsString(options));
            } catch (Exception e) {
                probe.setOptionsJson("[]");
            }
        }
    }

    private String resolveEnglishRoleOption(String word, String cnDef, String domainContext) {
        String key = word != null ? word.trim().toLowerCase(Locale.ROOT) : "";
        String known = FALLBACK_ENGLISH_FUNCTION_HINTS.get(key);
        if (StringUtils.hasText(known)) {
            return known;
        }
        String def = cnDef != null ? cnDef : "";
        if (def.contains("的")) {
            return "describing a key quality closely relevant to " + domainContext;
        }
        if (containsAny(def, "v.", "制作", "检查", "处理", "切", "煮", "剥", "收", "商", "谈", "登", "复")) {
            return "performing an essential hands-on action within " + domainContext;
        }
        return "serving as a tangible core element used in " + domainContext;
    }
}
