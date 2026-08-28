package com.chandler.learning.agent.common.exception;

import org.springframework.http.HttpStatus;

/** 稳定错误码、默认中文提示与 HTTP 状态。 */
public enum LearningErrorCode {

AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "请先登录"),
        AUTH_EXPIRED(HttpStatus.UNAUTHORIZED, "登录状态已过期，请重新登录"),
        AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "用户名或密码错误"),
        USER_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "用户已存在"),
        USER_NOT_FOUND(HttpStatus.NOT_FOUND, "用户不存在"),
        USER_DISABLED(HttpStatus.FORBIDDEN, "用户已被禁用"),
        ADMIN_REQUIRED(HttpStatus.FORBIDDEN, "仅系统管理员可执行此操作"),
        LAST_ADMIN_REQUIRED(HttpStatus.BAD_REQUEST, "至少保留一个启用的系统管理员"),
        ADMIN_SELF_OPERATION_FORBIDDEN(HttpStatus.BAD_REQUEST, "不能对当前登录管理员执行此操作"),
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
        MODEL_CONFIG_NOT_BOUND(HttpStatus.BAD_REQUEST, "Agent 未绑定模型配置"),
        MODEL_CONFIG_IN_USE(HttpStatus.CONFLICT, "模型配置正在被 Agent 使用"),
        AI_PROVIDER_MISSING(HttpStatus.BAD_REQUEST, "未配置 AI 服务供应商"),
        AI_PROVIDER_UNSUPPORTED(HttpStatus.BAD_REQUEST, "AI 服务供应商不受支持"),
        AI_PROVIDER_DISABLED(HttpStatus.BAD_REQUEST, "AI 服务供应商已停用"),
        AI_PROVIDER_API_KEY_MISSING(HttpStatus.BAD_REQUEST, "AI 服务 API Key 未配置"),
        AI_PROVIDER_BASE_URL_MISSING(HttpStatus.BAD_REQUEST, "AI 服务地址未配置"),
        AI_MODEL_NAME_MISSING(HttpStatus.BAD_REQUEST, "AI 模型名称未配置"),
        AI_MODEL_UNSUPPORTED(HttpStatus.BAD_REQUEST, "AI 模型不受支持或已经下线"),
        AI_MODEL_CALL_FAILED(HttpStatus.BAD_GATEWAY, "AI 模型调用失败"),
        AI_PROMPT_TOO_LARGE(HttpStatus.BAD_REQUEST, "AI 请求上下文过长，请减少本次输入后重试"),
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
        AI_ASYNC_TASK_TYPE_INVALID(HttpStatus.BAD_REQUEST, "AI 异步任务类型无效"),
        AI_ASYNC_TASK_STEP_NOT_FOUND(HttpStatus.NOT_FOUND, "AI 异步任务步骤不存在"),
        AI_ASYNC_TASK_OPERATION_FORBIDDEN(HttpStatus.FORBIDDEN, "无权操作该 AI 异步任务"),
        AI_ASYNC_TASK_RETRY_EXCEEDED(HttpStatus.BAD_REQUEST, "AI 异步任务已达到最大重试次数"),
        AI_ASYNC_TASK_EXECUTION_MODE_INVALID(HttpStatus.BAD_REQUEST, "AI 任务执行方式无效"),
        LEARNING_PLAN_GENERATION_IN_PROGRESS(HttpStatus.CONFLICT, "该学习计划正在生成场景材料，请稍后查看"),
        LEARNING_PLAN_UNIT_ACTIVE(HttpStatus.BAD_REQUEST, "当前已有正在学习的场景"),
        LEARNING_PLAN_UNIT_INCOMPLETE(HttpStatus.BAD_REQUEST, "当前场景尚未完成"),
        LEARNING_PLAN_NO_WORDS(HttpStatus.BAD_REQUEST, "词表中没有可学习词汇"),
        LEARNING_SCENE_PARSE_FAILED(HttpStatus.BAD_REQUEST, "场景材料解析失败"),
        LEARNING_ASSESSMENT_INVALID(HttpStatus.BAD_REQUEST, "词汇检查内容无效"),
        LEARNING_PLAN_STATE_ERROR(HttpStatus.BAD_REQUEST, "学习计划状态不允许当前操作");

        private final HttpStatus status;
        private final String defaultMessage;

        LearningErrorCode(HttpStatus status, String defaultMessage) {
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
