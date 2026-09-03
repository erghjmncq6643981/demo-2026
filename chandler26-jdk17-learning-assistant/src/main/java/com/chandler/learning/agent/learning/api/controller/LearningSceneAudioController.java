package com.chandler.learning.agent.learning.api.controller;

import com.chandler.learning.agent.learning.application.SceneArticleAudioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * 场景学习单元文章音频流控制器。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/english/learning/scene-units")
@Tag(name = "场景文章音频流")
public class LearningSceneAudioController {

    private final SceneArticleAudioService sceneArticleAudioService;

    /**
     * 获取指定场景单元已生成的 MP3 语音音频流（若不存在返回 404）。
     */
    @GetMapping(value = "/{unitId}/audio", produces = "audio/mpeg")
    @Operation(summary = "获取场景文章已生成的音频流")
    public ResponseEntity<Resource> getAudio(@PathVariable Long unitId) {
        Resource audioResource = sceneArticleAudioService.getExistingSceneAudio(unitId);
        if (audioResource == null || !audioResource.exists()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("scene-" + unitId + ".mp3").build().toString())
                .body(audioResource);
    }

    /**
     * 按需触发生成并返回场景文章的完整 MP3 语音音频流。
     */
    @PostMapping(value = "/{unitId}/audio/generate", produces = "audio/mpeg")
    @Operation(summary = "按需生成并返回场景文章音频流")
    public ResponseEntity<Resource> generateAudio(
            @PathVariable Long unitId,
            @org.springframework.web.bind.annotation.RequestParam(name = "forceRefresh", required = false, defaultValue = "false") boolean forceRefresh) {
        Resource audioResource = sceneArticleAudioService.generateOrGetSceneAudio(unitId, forceRefresh);
        if (audioResource == null || !audioResource.exists()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("scene-" + unitId + ".mp3").build().toString())
                .body(audioResource);
    }
}
