package com.chandler.learning.agent.support;

import org.springframework.http.HttpStatus;

/**
 * 后端通用业务常量。
 * <p>
 * 只收纳跨类共享或业务含义明确的值，避免把普通循环下标也抽象成常量。
 */
public final class LearningConstants {

    public static final int DEFAULT_SEQUENCE = 0;
    public static final int FIRST_SEQUENCE = 1;
    public static final int SEQUENCE_STEP = 1;
    public static final int ZERO = 0;
    public static final String SQL_LIMIT_ONE = "LIMIT 1";
    public static final String DEFAULT_CHAT_PATH = "/chat/completions";
    public static final String DEFAULT_AGENT_TYPE = Agent.TYPE_CHAT;
    public static final String DEFAULT_TEMPLATE_TYPE = PromptTemplate.TYPE_USER;
    public static final String VOCABULARY_AGENT_CODE = "english_vocabulary";
    public static final String VOCABULARY_TEMPLATE_CODE = "english_vocab_card_json";
    public static final String ARTICLE_AGENT_CODE = "english_article";
    public static final String ARTICLE_TEMPLATE_CODE = "english_vocab_article_json";
    public static final String VOCABULARY_PLAN_AGENT_CODE = "english_vocabulary_plan";
    public static final String VOCABULARY_PLAN_TEMPLATE_CODE = "english_vocab_scene_unit_json";
    public static final String VOCABULARY_BATCH_TEMPLATE_CODE = "english_vocab_cards_batch_json";
    public static final String VOCABULARY_ANALYSIS_AGENT_CODE = "english_vocabulary";
    public static final String VOCABULARY_ANALYSIS_TEMPLATE_CODE = "english_vocab_catalog_analysis_json";
    public static final int DEFAULT_JWT_EXPIRE_DAYS = 30;

    /**
     * 处理 {@code LearningConstants} 相关业务。
     */
    private LearningConstants() {
    }

    /**
     * Audit 类。
     */
    public static final class Audit {
        public static final long SYSTEM_USER_ID = 0L;
        public static final int INITIAL_VERSION = 0;

        /**
         * 处理 {@code Audit} 相关业务。
         */
        private Audit() {
        }
    }

    /**
     * Agent 类。
     */
    public static final class Agent {
        public static final String TYPE_CHAT = "chat";
        public static final String TYPE_ANALYSIS = "analysis";
        public static final String TYPE_ASSISTANT = "assistant";

        /**
         * 处理 {@code Agent} 相关业务。
         */
        private Agent() {
        }
    }

    /**
     * PromptTemplate 类。
     */
    public static final class PromptTemplate {
        public static final String TYPE_SYSTEM = "system";
        public static final String TYPE_USER = "user";
        public static final String TYPE_ANALYSIS = "analysis";

        /**
         * 处理 {@code PromptTemplate} 相关业务。
         */
        private PromptTemplate() {
        }
    }

    /**
     * Auth 类。
     */
    public static final class Auth {
        public static final String BEARER_PREFIX = "Bearer ";
        public static final int PASSWORD_MIN_LENGTH = 6;
        public static final int PASSWORD_SALT_BYTES = 16;
        public static final int PASSWORD_HASH_PART_COUNT = 3;
        public static final int PASSWORD_SALT_PART_INDEX = 1;
        public static final int PASSWORD_DIGEST_PART_INDEX = 2;
        public static final String PASSWORD_HASH_SEPARATOR = ":";
        public static final int BEARER_PREFIX_LENGTH = BEARER_PREFIX.length();
        public static final int PHONE_MAX_LENGTH = 32;
        public static final int EMAIL_MAX_LENGTH = 128;
        public static final int PHONE_MASK_THRESHOLD = 7;
        public static final int PHONE_MASK_PREFIX_LENGTH = 3;
        public static final int PHONE_MASK_SUFFIX_LENGTH = 4;
        public static final int EMAIL_MASK_VISIBLE_PREFIX_LENGTH = 2;
        public static final String CONTACT_MASK = "****";

        /**
         * 处理 {@code Auth} 相关业务。
         */
        private Auth() {
        }
    }

    /**
     * Jwt 类。
     */
    public static final class Jwt {
        public static final int TOKEN_PART_COUNT = 3;
        public static final int SIGNATURE_PART_INDEX = 2;
        public static final long SECONDS_PER_DAY = 86_400L;

        /**
         * 处理 {@code Jwt} 相关业务。
         */
        private Jwt() {
        }
    }

    /**
     * Crypto 类。
     */
    public static final class Crypto {
        public static final String API_KEY_PREFIX = "enc:v1:";
        public static final String API_KEY_CIPHER = "AES/GCM/NoPadding";
        public static final int API_KEY_IV_LENGTH = 12;
        public static final int API_KEY_TAG_BITS = 128;
        public static final int API_KEY_CIPHER_PART_COUNT = 2;
        public static final int API_KEY_MASK_THRESHOLD = 10;
        public static final int API_KEY_MASK_PREFIX_LENGTH = 6;
        public static final int API_KEY_MASK_SUFFIX_LENGTH = 4;
        public static final int API_KEY_FINGERPRINT_LENGTH = 16;

        /**
         * 处理 {@code Crypto} 相关业务。
         */
        private Crypto() {
        }
    }

    /**
     * ModelClient 类。
     */
    public static final class ModelClient {
        public static final int EMPTY_SIZE = 0;
        public static final int FIRST_CHOICE_INDEX = 0;

        /**
         * 处理 {@code ModelClient} 相关业务。
         */
        private ModelClient() {
        }
    }

    /**
     * ChatSession 类。
     */
    public static final class ChatSession {
        public static final int MAX_HISTORY_SIZE = 20;
        public static final int MAX_HISTORY_CHARS = 24_000;
        public static final int MESSAGE_SEQUENCE_RETRY_COUNT = 3;
        public static final String ROLE_SYSTEM = "system";
        public static final String ROLE_USER = "user";
        public static final String ROLE_ASSISTANT = "assistant";
        public static final String BUSINESS_TYPE_LEARNING = "learning";
        public static final String SCENE_ENGLISH_VOCABULARY = "english_vocabulary";
        public static final String SCENE_ENGLISH_ARTICLE = "english_article";
        public static final String SCENE_ENGLISH_VOCABULARY_PLAN = "english_vocabulary_plan";
        public static final String SCENE_MATH = "math";
        public static final String SCENE_PINYIN = "pinyin";
        public static final String SCENE_WRITING = "writing";
        public static final String SCENE_TITLE_ENGLISH_VOCABULARY = "英语词汇学习";

        /**
         * 处理 {@code ChatSession} 相关业务。
         */
        private ChatSession() {
        }
    }

    /** AI 审计记录的默认安全边界。 */
    public static final class AiAudit {
        public static final int DEFAULT_MAX_CONTENT_LENGTH = 120_000;
        public static final int MAX_ERROR_MESSAGE_LENGTH = 1_000;

        private AiAudit() {
        }
    }

    /**
     * SystemLog 类。
     */
    public static final class SystemLog {
        public static final int DEFAULT_LIMIT = 80;
        public static final String DEFAULT_LIMIT_PARAM = "80";
        public static final int MIN_LIMIT = 1;
        public static final int MAX_LIMIT = 200;
        public static final String TYPE_SYSTEM = "system";
        public static final String TYPE_AUTH = "auth";
        public static final String TYPE_AI = "ai";
        public static final String TYPE_AI_MODEL = "ai_model";
        public static final String TYPE_CACHE = "cache";
        public static final String TYPE_REVIEW = "review";
        public static final String TYPE_WORDBOOK = "wordbook";
        public static final String TYPE_AGENT = "agent";
        public static final String TYPE_PREFERENCE = "preference";
        public static final String TYPE_VOCABULARY_IMPORT = "vocabulary_import";
        public static final String TYPE_LEARNING_PLAN = "learning_plan";
        public static final String TYPE_ERROR = "error";
        public static final String DEFAULT_TYPE = TYPE_SYSTEM;
        public static final String DEFAULT_TITLE = "系统日志";
        public static final String SOURCE_CLIENT = "client";
        public static final String SOURCE_SERVER = "server";

        /**
         * 处理 {@code SystemLog} 相关业务。
         */
        private SystemLog() {
        }
    }

    /**
     * UserPreference 类。
     */
    public static final class UserPreference {
        public static final String KEY_SPEECH_VOICE_TYPE = "speech.voice_type";
        public static final String KEY_SPEECH_SENTENCE_VOICE_NAME = "speech.sentence_voice_name";
        public static final String KEY_SPEECH_SENTENCE_RATE = "speech.sentence_rate";
        public static final String KEY_SPEECH_SENTENCE_PITCH = "speech.sentence_pitch";
        public static final String VOICE_TYPE_US = "us";
        public static final String VOICE_TYPE_UK = "uk";
        public static final double SENTENCE_RATE_DEFAULT = 0.78D;
        public static final double SENTENCE_RATE_MIN = 0.55D;
        public static final double SENTENCE_RATE_MAX = 1.15D;
        public static final double SENTENCE_PITCH_DEFAULT = 1D;
        public static final double SENTENCE_PITCH_MIN = 0.8D;
        public static final double SENTENCE_PITCH_MAX = 1.2D;

        /**
         * 处理 {@code UserPreference} 相关业务。
         */
        private UserPreference() {
        }
    }

    /**
     * Vocabulary 类。
     */
    public static final class Vocabulary {
        public static final int DEFAULT_LOOKUP_COUNT = 1;
        public static final int EXACT_MATCH_SCORE = 100;
        public static final int FUZZY_MATCH_CANDIDATE_LIMIT = 1_000;
        public static final int FUZZY_MATCH_MIN_SCORE = 45;
        public static final int FUZZY_MATCH_MAX_SCORE = 99;
        public static final int MIN_MATCH_SCORE = 0;
        public static final int PREFIX_SCORE_BOOST = 12;
        public static final int SAME_INITIAL_SCORE_BOOST = 6;
        public static final int COMMON_PREFIX_MIN_LENGTH = 2;
        public static final int COMMON_PREFIX_SCORE_BOOST = 8;
        public static final int CONTAINS_SCORE_BOOST = 8;
        public static final int EDIT_DISTANCE_INSERT_DELETE_COST = 1;
        public static final int EDIT_DISTANCE_SAME_COST = 0;
        public static final int EDIT_DISTANCE_REPLACE_COST = 1;

        /**
         * 处理 {@code Vocabulary} 相关业务。
         */
        private Vocabulary() {
        }
    }

    /**
     * VocabularyInsight 类。
     */
    public static final class VocabularyInsight {
        public static final int MAX_RELATIONS = 80;
        public static final int VISIBLE_RELATION_LIMIT = 24;
        public static final int SAME_TAG_LIMIT = 12;
        public static final int TAG_WEIGHT_PART_OF_SPEECH = 90;
        public static final int TAG_WEIGHT_MEANING_TOPIC = 70;
        public static final int TAG_WEIGHT_COLLOCATION = 58;
        public static final int TAG_WEIGHT_WORD_FAMILY = 62;
        public static final int TAG_WEIGHT_DIFFICULTY = 45;
        public static final int RELATION_SCORE_SYNONYM = 92;
        public static final int RELATION_SCORE_ANTONYM = 82;
        public static final int RELATION_SCORE_WORD_FAMILY = 78;
        public static final int RELATION_SCORE_COLLOCATION = 70;
        public static final int RELATION_SCORE_TAG_OVERLAP = 60;
        public static final int HARD_DEFINITION_COUNT = 5;
        public static final int HARD_WORD_LENGTH = 12;
        public static final int MEDIUM_DEFINITION_COUNT = 3;
        public static final int MEDIUM_WORD_LENGTH = 8;
        public static final int TAG_VALUE_MAX_LENGTH = 128;
        public static final int PART_OF_SPEECH_MAX_LENGTH = 50;
        public static final int MEANING_MAX_LENGTH = 512;
        public static final int MATCH_TYPE_MAX_LENGTH = 50;
        public static final String TAG_TYPE_PART_OF_SPEECH = "part_of_speech";
        public static final String TAG_TYPE_MEANING_TOPIC = "meaning_topic";
        public static final String TAG_TYPE_DIFFICULTY = "difficulty";
        public static final String RELATION_TYPE_SYNONYM = "synonym";
        public static final String RELATION_TYPE_ANTONYM = "antonym";
        public static final String RELATION_TYPE_WORD_FAMILY = "word_family";
        public static final String RELATION_TYPE_TAG_OVERLAP = "tag_overlap";
        public static final String RELATION_TYPE_COLLOCATION = "collocation";
        public static final String MATCH_TYPE_PARSED_TEXT = "parsed_text";
        public static final String MATCH_TYPE_PARSED_OBJECT = "parsed_object";
        public static final String MATCH_TYPE_CACHED_EXACT = "cached_exact";
        public static final String MATCH_TYPE_EXACT = "exact";
        public static final String MATCH_TYPE_FUZZY = "fuzzy";
        public static final String DIFFICULTY_EASY = "easy";
        public static final String DIFFICULTY_MEDIUM = "medium";
        public static final String DIFFICULTY_HARD = "hard";
        public static final String SOURCE_PARSED_JSON = "parsed_json";

        /**
         * 处理 {@code VocabularyInsight} 相关业务。
         */
        private VocabularyInsight() {
        }
    }

    /**
     * Review 类。
     */
    public static final class Review {
        public static final String STATUS_FAMILIAR = "familiar";
        public static final String STATUS_FORGOTTEN = "forgotten";
        public static final String STATUS_VAGUE = "vague";
        public static final String RESULT_REMEMBERED = "remembered";
        public static final String RESULT_VAGUE = "vague";
        public static final String RESULT_FORGOTTEN = "forgotten";
        public static final int INITIAL_STAGE = 0;
        public static final int INITIAL_MASTERY = 0;
        public static final int MIN_MASTERY = 0;
        public static final int MAX_MASTERY = 100;
        public static final int FAMILIAR_MASTERY_THRESHOLD = 70;
        public static final int REMEMBERED_MASTERY_DELTA = 15;
        public static final int VAGUE_MASTERY_DELTA = 5;
        public static final int FORGOTTEN_MASTERY_DELTA = 20;
        public static final int VAGUE_REVIEW_DELAY_DAYS = 1;
        public static final int FORGOTTEN_REVIEW_DELAY_HOURS = 4;
        public static final int SLEEP_START_HOUR = 0;
        public static final int SLEEP_END_HOUR = 6;
        public static final int DAY_END_HOUR = 24;
        public static final int RESTART_DEFAULT_LIMIT = 10;
        public static final String RESTART_DEFAULT_LIMIT_PARAM = "10";
        public static final int RESTART_MIN_LIMIT = 1;
        public static final int RESTART_MAX_LIMIT = 100;
        public static final int[] INTERVAL_DAYS = {0, 1, 2, 4, 7, 15, 30, 60};

        /**
         * 处理 {@code Review} 相关业务。
         */
        private Review() {
        }
    }

    /**
     * Wordbook 类。
     */
    public static final class Wordbook {
        public static final String DEFAULT_NAME = "默认单词本";
        public static final String DEFAULT_DESCRIPTION = "自动创建的英语词汇学习单词本";

        /**
         * 处理 {@code Wordbook} 相关业务。
         */
        private Wordbook() {
        }
    }

    /**
     * Article 类。
     */
    public static final class Article {
        public static final int MIN_SELECTED_WORDS = 1;
        public static final int MAX_SELECTED_WORDS = 20;
        public static final int PRACTICE_QUESTION_COUNT = 3;
        public static final int DEFAULT_HISTORY_LIMIT = 10;
        public static final String DEFAULT_HISTORY_LIMIT_PARAM = "10";
        public static final int MIN_HISTORY_LIMIT = 1;
        public static final int MAX_HISTORY_LIMIT = 50;
        public static final int DEFAULT_LOOKUP_COUNT = 1;
        public static final String STATUS_GENERATED = "generated";
        public static final String STATUS_IN_PROGRESS = "in_progress";
        public static final String STATUS_COMPLETED = "completed";
        public static final String STAGE_READING = "reading";
        public static final String STAGE_VOCABULARY = "vocabulary";
        public static final String STAGE_CHECK = "check";
        public static final String STAGE_COMPLETED = "completed";

        /**
         * 处理 {@code Article} 相关业务。
         */
        private Article() {
        }
    }

    /**
     * 词表导入规则。
     */
    public static final class VocabularyImport {
        public static final String FORMAT_MARKDOWN = "markdown";
        public static final String STATUS_PARSING = "parsing";
        public static final String STATUS_REVIEWING = "reviewing";
        public static final String STATUS_PUBLISHED = "published";
        public static final String STATUS_FAILED = "failed";
        public static final String VERSION_STATUS_REVIEWING = "reviewing";
        public static final String VERSION_STATUS_PUBLISHED = "published";
        public static final String CATALOG_STATUS_DRAFT = "draft";
        public static final String CATALOG_STATUS_PUBLISHED = "published";
        public static final String VISIBILITY_PRIVATE = "private";
        public static final String VISIBILITY_PUBLIC = "public";
        public static final String SOURCE_SELF_STUDY = "self_study";
        public static final String SOURCE_CET4 = "cet4";
        public static final String SOURCE_CET6 = "cet6";
        public static final String SOURCE_IELTS = "ielts";
        public static final String REVIEW_NOT_REQUIRED = "not_required";
        public static final String REVIEW_PENDING = "pending";
        public static final String REVIEW_CONFIRMED = "confirmed";
        public static final String WARNING_SUSPICIOUS_SPLIT = "suspicious_split";
        public static final int DEFAULT_PAGE = 1;
        public static final int DEFAULT_PAGE_SIZE = 100;
        public static final int MAX_PAGE_SIZE = 500;

        private VocabularyImport() {
        }
    }

    /**
     * 场景化学习计划规则。日期只做建议，不参与接口限流或状态校验。
     */
    public static final class ScenePlan {
        public static final String STATUS_NOT_STARTED = "not_started";
        public static final String STATUS_ACTIVE = "active";
        public static final String STATUS_COMPLETED = "completed";
        public static final String STATUS_PAUSED = "paused";
        public static final String STATUS_CANCELLED = "cancelled";
        public static final String UNIT_READY = "ready";
        public static final String UNIT_IN_PROGRESS = "in_progress";
        public static final String UNIT_COMPLETED = "completed";
        public static final String TIER_CORE = "core";
        public static final String TIER_EXTENDED = "extended";
        public static final String TIER_SUPPLEMENTARY = "supplementary";
        public static final String TIER_REVIEW = "review";
        public static final String MASTERY_RECOGNITION = "recognition";
        public static final String MASTERY_SPELLING = "spelling";
        public static final String PROGRESS_UNSEEN = "unseen";
        public static final String PROGRESS_EXPOSED = "exposed";
        public static final String PROGRESS_LEARNING = "learning";
        public static final String PROGRESS_REVIEWING = "reviewing";
        public static final String PROGRESS_MASTERED = "mastered";
        public static final String ASSESSMENT_MEANING_CHOICE = "meaning_choice";
        public static final String ASSESSMENT_COPY_TYPING = "copy_typing";
        public static final String ASSESSMENT_MEANING_SPELLING = "meaning_spelling";
        public static final String CHECK_CORRECT = "correct";
        public static final String CHECK_INCORRECT = "incorrect";
        public static final int MIN_CORE_WORDS = 8;
        public static final int MAX_CORE_WORDS_PER_UNIT = 50;
        public static final int RECOGNITION_PASS_SCORE = 70;
        public static final int SPELLING_PASS_SCORE = 70;

        private ScenePlan() {
        }
    }

    /**
     * AI 词卡按需批量生成规则。
     */
    public static final class VocabularyCard {
        public static final String STATUS_MISSING = "missing";
        public static final String STATUS_QUEUED = "queued";
        public static final String STATUS_GENERATING = "generating";
        public static final String STATUS_READY = "ready";
        public static final String STATUS_FAILED = "failed";
        public static final String STATUS_NOT_REQUIRED = "not_required";
        public static final String JOB_PENDING = "pending";
        public static final String JOB_RUNNING = "running";
        public static final String JOB_COMPLETED = "completed";
        public static final String JOB_PARTIAL_FAILED = "partial_failed";
        public static final String JOB_FAILED = "failed";
        public static final String JOB_CANCELLED = "cancelled";
        public static final String ITEM_PENDING = "pending";
        public static final String ITEM_GENERATING = "generating";
        public static final String ITEM_COMPLETED = "completed";
        public static final String ITEM_FAILED = "failed";
        public static final String ITEM_CACHE_HIT = "cache_hit";
        public static final int DEFAULT_BATCH_SIZE = 15;
        public static final int MIN_BATCH_SIZE = 10;
        public static final int MAX_BATCH_SIZE = 20;

        private VocabularyCard() {
        }
    }

    /** AI 异步任务状态和调度规则。 */
    public static final class AiTask {
        public static final String TYPE_SCENE_MATERIAL = "scene_material";
        public static final String TYPE_VOCABULARY_CARD = "vocabulary_card";
        public static final String TYPE_VOCABULARY_CATALOG_ANALYSIS = "vocabulary_catalog_analysis";
        public static final String STATUS_PENDING = "pending";
        public static final String STATUS_RUNNING = "running";
        public static final String STATUS_COMPLETED = "completed";
        public static final String STATUS_PARTIAL_FAILED = "partial_failed";
        public static final String STATUS_FAILED = "failed";
        public static final String STATUS_CANCELLED = "cancelled";
        public static final String EXECUTION_IMMEDIATE = "immediate";
        public static final String EXECUTION_SCHEDULED = "scheduled";
        public static final String EXECUTION_LOW_COST_WINDOW = "low_cost_window";
        public static final int DEFAULT_PRIORITY = 50;
        public static final int DEFAULT_MAX_RETRY_COUNT = 2;
        public static final int DEFAULT_PAGE_SIZE = 50;
        public static final int MAX_PAGE_SIZE = 100;

        private AiTask() {
        }
    }

    /** 公共词本语义索引分析规则。 */
    public static final class VocabularyAnalysis {
        public static final String STATUS_PENDING = "pending";
        public static final String STATUS_RUNNING = "running";
        public static final String STATUS_COMPLETED = "completed";
        public static final String STATUS_PARTIAL_FAILED = "partial_failed";
        public static final String STATUS_FAILED = "failed";
        public static final String ITEM_PENDING = "pending";
        public static final String ITEM_RUNNING = "running";
        public static final String ITEM_COMPLETED = "completed";
        public static final String ITEM_FAILED = "failed";
        public static final String ENTRY_READY = "ready";
        public static final String ENTRY_LOW_CONFIDENCE = "low_confidence";
        public static final String ENTRY_FAILED = "failed";
        public static final String SOURCE_AI = "ai";
        public static final String STRATEGY_VERSION = "semantic_coordinator_v1";
        public static final int DEFAULT_BATCH_SIZE = 25;
        public static final int MIN_BATCH_SIZE = 10;
        public static final int MAX_BATCH_SIZE = 50;
        public static final int MAX_TAG_COUNT = 6;
        public static final int MAX_RELATED_COUNT = 12;
        public static final double LOW_CONFIDENCE_THRESHOLD = 0.55D;

        private VocabularyAnalysis() {
        }
    }

    /**
     * Activity 类。
     */
    public static final class Activity {
        public static final int MIN_DAYS = 7;
        public static final int MAX_DAYS = 366;

        /**
         * 处理 {@code Activity} 相关业务。
         */
        private Activity() {
        }
    }

    /**
     * 稳定错误码、默认中文提示和默认 HTTP 状态。
     * <p>
     * 业务代码优先使用 {@code LearningAssistantException.badRequest(code)}，
     * 只有确实需要上下文时才传入覆盖消息。
     */
    public enum ErrorCode {
        AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "请先登录"),
        AUTH_EXPIRED(HttpStatus.UNAUTHORIZED, "登录状态已过期，请重新登录"),
        AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "用户名或密码错误"),
        USER_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "用户已存在"),
        USER_DISABLED(HttpStatus.FORBIDDEN, "用户已被禁用"),
        PASSWORD_TOO_SHORT(HttpStatus.BAD_REQUEST, "密码长度不足"),
        PASSWORD_INCORRECT(HttpStatus.BAD_REQUEST, "原密码不正确"),
        PHONE_INVALID(HttpStatus.BAD_REQUEST, "手机号格式不正确"),
        EMAIL_INVALID(HttpStatus.BAD_REQUEST, "邮箱格式不正确"),
        JWT_INVALID(HttpStatus.UNAUTHORIZED, "登录凭证无效"),
        JWT_SIGN_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "登录凭证生成失败"),
        JSON_SERIALIZE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "数据序列化失败"),
        JSON_PARSE_FAILED(HttpStatus.BAD_REQUEST, "数据格式不正确"),
        API_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "请填写模型 API Key"),
        API_KEY_CRYPTO_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "模型 API Key 处理失败"),
        API_KEY_CIPHER_INVALID(HttpStatus.BAD_REQUEST, "模型 API Key 密文无效"),
        SYSTEM_UNEXPECTED(HttpStatus.INTERNAL_SERVER_ERROR, "系统异常，请稍后重试"),
        EXTERNAL_SERVICE_CALL_FAILED(HttpStatus.BAD_GATEWAY, "外部服务调用失败"),
        MODEL_CONFIG_NOT_FOUND(HttpStatus.NOT_FOUND, "模型配置不存在"),
        AI_PROVIDER_MISSING(HttpStatus.BAD_REQUEST, "未配置 AI 服务供应商"),
        AI_PROVIDER_DISABLED(HttpStatus.BAD_REQUEST, "AI 服务供应商已停用"),
        AI_PROVIDER_API_KEY_MISSING(HttpStatus.BAD_REQUEST, "AI 服务 API Key 未配置"),
        AI_PROVIDER_BASE_URL_MISSING(HttpStatus.BAD_REQUEST, "AI 服务地址未配置"),
        AI_MODEL_NAME_MISSING(HttpStatus.BAD_REQUEST, "AI 模型名称未配置"),
        AI_MODEL_CALL_FAILED(HttpStatus.BAD_GATEWAY, "AI 模型调用失败"),
        AI_MODEL_BALANCE_INSUFFICIENT(HttpStatus.BAD_GATEWAY, "AI 模型余额不足"),
        AI_INVOCATION_SCENE_INVALID(HttpStatus.BAD_REQUEST, "AI 调用场景无效"),
        AI_RESPONSE_PARSE_FAILED(HttpStatus.BAD_REQUEST, "AI 返回内容格式无效"),
        AGENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Agent 不存在"),
        AGENT_DISABLED(HttpStatus.BAD_REQUEST, "Agent 已停用"),
        AGENT_CODE_EXISTS(HttpStatus.BAD_REQUEST, "Agent 编码已存在"),
        CHAT_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "AI 会话不存在"),
        CHAT_MESSAGE_SEQUENCE_CONFLICT(HttpStatus.CONFLICT, "AI 会话消息写入冲突，请重试"),
        PROMPT_TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "Prompt 模板不存在"),
        PROMPT_TEMPLATE_DISABLED(HttpStatus.BAD_REQUEST, "Prompt 模板已停用"),
        PROMPT_TEMPLATE_CODE_EXISTS(HttpStatus.BAD_REQUEST, "Prompt 模板编码已存在"),
        PROMPT_TEMPLATE_LAST_NOT_DELETABLE(HttpStatus.BAD_REQUEST, "最后一个 Prompt 模板不可删除"),
        VOCABULARY_EMPTY(HttpStatus.BAD_REQUEST, "没有可学习的词汇"),
        VOCABULARY_RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "词汇记录不存在"),
        WORDBOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "单词本不存在"),
        WORDBOOK_NOT_EMPTY(HttpStatus.BAD_REQUEST, "单词本不为空，无法删除"),
        WORDBOOK_TRANSFER_INVALID(HttpStatus.BAD_REQUEST, "单词本转移参数无效"),
        ENTRY_NOT_FOUND(HttpStatus.NOT_FOUND, "单词本词条不存在"),
        ARTICLE_WORDS_EMPTY(HttpStatus.BAD_REQUEST, "请选择文章词汇"),
        ARTICLE_WORD_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "文章词汇数量超出限制"),
        ARTICLE_WORDS_INVALID(HttpStatus.BAD_REQUEST, "文章词汇无效"),
        ARTICLE_RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "文章学习记录不存在"),
        ARTICLE_STAGE_INVALID(HttpStatus.BAD_REQUEST, "文章学习阶段无效"),
        ARTICLE_PRACTICE_INCOMPLETE(HttpStatus.BAD_REQUEST, "请先完成文章练习"),
        ARTICLE_AI_RESPONSE_INVALID(HttpStatus.BAD_REQUEST, "文章 AI 返回内容无效"),
        AGENT_LAST_NOT_DELETABLE(HttpStatus.BAD_REQUEST, "最后一个 Agent 不可删除"),
        HASH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "内容摘要计算失败"),
        VOCABULARY_IMPORT_INVALID(HttpStatus.BAD_REQUEST, "词表导入内容无效"),
        VOCABULARY_IMPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "词表导入任务不存在"),
        VOCABULARY_IMPORT_NOT_REVIEWED(HttpStatus.BAD_REQUEST, "词表仍有疑似断词未确认"),
        VOCABULARY_IMPORT_ALREADY_PUBLISHED(HttpStatus.BAD_REQUEST, "词表已经发布"),
        VOCABULARY_CATALOG_NOT_FOUND(HttpStatus.NOT_FOUND, "公共词本不存在"),
        LEARNING_PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "学习计划不存在"),
        LEARNING_PLAN_COMPLETED(HttpStatus.BAD_REQUEST, "学习计划已经完成"),
        LEARNING_PLAN_UNIT_NOT_FOUND(HttpStatus.NOT_FOUND, "学习场景不存在"),
        LEARNING_SCENE_MATERIAL_NOT_FOUND(HttpStatus.NOT_FOUND, "场景材料不存在"),
        AI_ASYNC_TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "AI 异步任务不存在"),
        AI_ASYNC_TASK_EXECUTION_MODE_INVALID(HttpStatus.BAD_REQUEST, "AI 任务执行方式无效"),
        LEARNING_PLAN_UNIT_ACTIVE(HttpStatus.BAD_REQUEST, "当前已有正在学习的场景"),
        LEARNING_PLAN_UNIT_INCOMPLETE(HttpStatus.BAD_REQUEST, "当前场景尚未完成"),
        LEARNING_PLAN_NO_WORDS(HttpStatus.BAD_REQUEST, "词表中没有可学习词汇"),
        LEARNING_SCENE_PARSE_FAILED(HttpStatus.BAD_REQUEST, "场景材料解析失败"),
        LEARNING_ASSESSMENT_INVALID(HttpStatus.BAD_REQUEST, "词汇检查内容无效"),
        LEARNING_PLAN_STATE_ERROR(HttpStatus.BAD_REQUEST, "学习计划状态不允许当前操作");

        private final HttpStatus status;
        private final String defaultMessage;

        ErrorCode(HttpStatus status, String defaultMessage) {
            this.status = status;
            this.defaultMessage = defaultMessage;
        }

        public HttpStatus getStatus() {
            return status;
        }

        public String getDefaultMessage() {
            return defaultMessage;
        }

        public String getCode() {
            return name();
        }

        @Override
        public String toString() {
            return name();
        }
    }
}
