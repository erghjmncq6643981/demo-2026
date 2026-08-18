package com.chandler.learning.agent.exception;

/** Worker 在批次边界发现统一 AI 任务已被用户取消。 */
public class AiAsyncTaskCancelledException extends RuntimeException {

    public AiAsyncTaskCancelledException() {
        super("AI 异步任务已取消");
    }
}
