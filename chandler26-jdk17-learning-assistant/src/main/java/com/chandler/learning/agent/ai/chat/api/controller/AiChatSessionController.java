package com.chandler.learning.agent.ai.chat.api.controller;

import com.chandler.learning.agent.ai.chat.api.response.ChatMessageResponse;
import com.chandler.learning.agent.ai.chat.api.response.ChatSessionResponse;
import com.chandler.learning.agent.ai.chat.api.response.AdminAiSessionDetailResponse;
import com.chandler.learning.agent.ai.chat.api.response.AdminAiSessionPageResponse;
import com.chandler.learning.agent.ai.chat.application.AiChatSessionService;
import com.chandler.learning.agent.security.LearningPermission;
import com.chandler.learning.agent.security.RequirePermission;
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

    /**
     * 管理员分页查询全部 AI 会话。
     */
    @GetMapping("/admin")
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "管理员分页查询全部 AI 会话")
    public AdminAiSessionPageResponse adminPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sceneCode,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return chatSessionService.adminPage(keyword, sceneCode, provider, success, page, pageSize);
    }

    /**
     * 管理员查看 AI 会话完整诊断信息。
     */
    @GetMapping("/admin/{sessionId}")
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "管理员查看 AI 会话详情")
    public AdminAiSessionDetailResponse adminDetail(
            @PathVariable Long sessionId) {
        return chatSessionService.adminDetail(sessionId);
    }
    /** 会话列表。 */

    @GetMapping
    @Operation(summary = "会话列表")
    public List<ChatSessionResponse> list(@RequestParam(required = false) String agentCode,
                                          @RequestParam(required = false) String businessType,
                                          @RequestParam(required = false) String businessId,
                                          @RequestParam(required = false) String sceneCode) {
        return chatSessionService.listSessions(null, agentCode, businessType, businessId, sceneCode);
    }

    /** 会话详情。 */
    @GetMapping("/{sessionId}")
    @Operation(summary = "会话详情")
    public ChatSessionResponse detail(@PathVariable Long sessionId) {
        return chatSessionService.detail(sessionId);
    }

    /** 会话消息列表。 */
    @GetMapping("/{sessionId}/messages")
    @Operation(summary = "会话消息列表")
    public List<ChatMessageResponse> messages(@PathVariable Long sessionId) {
        return chatSessionService.listMessages(sessionId);
    }

    /** 更新会话标题。 */
    @PostMapping("/{sessionId}/title")
    @Operation(summary = "更新会话标题")
    public void updateTitle(@PathVariable Long sessionId, @RequestParam String title) {
        chatSessionService.updateTitle(sessionId, title);
    }

    /** 删除会话。 */
    @DeleteMapping("/{sessionId}")
    @Operation(summary = "删除会话")
    public void delete(@PathVariable Long sessionId) {
        chatSessionService.delete(sessionId);
    }
}
