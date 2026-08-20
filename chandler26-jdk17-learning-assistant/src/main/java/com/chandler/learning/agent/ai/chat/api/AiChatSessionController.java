package com.chandler.learning.agent.ai.chat.api;

import com.chandler.learning.agent.ai.chat.api.ChatMessageResponse;
import com.chandler.learning.agent.ai.chat.api.ChatSessionResponse;
import com.chandler.learning.agent.ai.chat.api.AdminAiSessionDetailResponse;
import com.chandler.learning.agent.ai.chat.api.AdminAiSessionPageResponse;
import com.chandler.learning.agent.ai.chat.application.AiChatSessionService;
import com.chandler.learning.agent.identity.application.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 对话会话控制器。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ai/chat-sessions")
@Tag(name = "AI 对话会话")
public class AiChatSessionController {

    private final AiChatSessionService chatSessionService;
    private final AuthService authService;

    /**
     * 管理员分页查询全部 AI 会话。
     */
    @GetMapping("/admin")
    @Operation(summary = "管理员分页查询全部 AI 会话")
    public AdminAiSessionPageResponse adminPage(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sceneCode,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        authService.requireAdmin(authorization);
        return chatSessionService.adminPage(keyword, sceneCode, provider, success, page, pageSize);
    }

    /**
     * 管理员查看 AI 会话完整诊断信息。
     */
    @GetMapping("/admin/{sessionId}")
    @Operation(summary = "管理员查看 AI 会话详情")
    public AdminAiSessionDetailResponse adminDetail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long sessionId) {
        authService.requireAdmin(authorization);
        return chatSessionService.adminDetail(sessionId);
    }

    @GetMapping
    @Operation(summary = "会话列表")
    public List<ChatSessionResponse> list(@RequestParam(required = false) String agentCode,
                                          @RequestParam(required = false) String businessType,
                                          @RequestParam(required = false) String businessId,
                                          @RequestParam(required = false) String sceneCode) {
        return chatSessionService.listSessions(null, agentCode, businessType, businessId, sceneCode);
    }

    /**
     * 查询 {@code detail} 相关业务。
     */
    @GetMapping("/{sessionId}")
    @Operation(summary = "会话详情")
    public ChatSessionResponse detail(@PathVariable Long sessionId) {
        return chatSessionService.detail(sessionId);
    }

    /**
     * 处理 {@code messages} 相关业务。
     */
    @GetMapping("/{sessionId}/messages")
    @Operation(summary = "会话消息列表")
    public List<ChatMessageResponse> messages(@PathVariable Long sessionId) {
        return chatSessionService.listMessages(sessionId);
    }

    /**
     * 更新 {@code updateTitle} 相关业务。
     */
    @PostMapping("/{sessionId}/title")
    @Operation(summary = "更新会话标题")
    public void updateTitle(@PathVariable Long sessionId, @RequestParam String title) {
        chatSessionService.updateTitle(sessionId, title);
    }

    /**
     * 更新 {@code delete} 相关业务。
     */
    @DeleteMapping("/{sessionId}")
    @Operation(summary = "删除会话")
    public void delete(@PathVariable Long sessionId) {
        chatSessionService.delete(sessionId);
    }
}
