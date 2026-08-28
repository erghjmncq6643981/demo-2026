package com.chandler.learning.agent.ai.chat.domain.constant;

/** AiContext 业务常量。 */
public final class AiContextBudgetConstants {

public static final int SAFE_USAGE_PERCENT = 90;
        public static final int MIN_OUTPUT_TOKENS = 256;
        public static final int ASCII_CHARACTERS_PER_TOKEN = 4;
        public static final int NON_ASCII_TOKENS_PER_CHARACTER = 2;
        public static final int MESSAGE_OVERHEAD_TOKENS = 4;

        private AiContextBudgetConstants() {
        }
}
