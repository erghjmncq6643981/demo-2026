package com.chandler.learning.agent.controller;

import com.chandler.learning.agent.domain.dto.ChatMessageResponse;
import com.chandler.learning.agent.domain.dto.ChatSessionResponse;
import com.chandler.learning.agent.service.AiChatSessionService;
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

    @GetMapping
    @Operation(summary = "会话列表")
    public List<ChatSessionResponse> list(@RequestParam(required = false) String agentCode,
                                          @RequestParam(required = false) String businessType,
                                          @RequestParam(required = false) String businessId) {
        return chatSessionService.listSessions(agentCode, businessType, businessId);
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "会话详情")
    public ChatSessionResponse detail(@PathVariable Long sessionId) {
        return chatSessionService.detail(sessionId);
    }

    @GetMapping("/{sessionId}/messages")
    @Operation(summary = "会话消息列表")
    public List<ChatMessageResponse> messages(@PathVariable Long sessionId) {
        return chatSessionService.listMessages(sessionId);
    }

    @PostMapping("/{sessionId}/title")
    @Operation(summary = "更新会话标题")
    public void updateTitle(@PathVariable Long sessionId, @RequestParam String title) {
        chatSessionService.updateTitle(sessionId, title);
    }

    @DeleteMapping("/{sessionId}")
    @Operation(summary = "删除会话")
    public void delete(@PathVariable Long sessionId) {
        chatSessionService.delete(sessionId);
    }
}
