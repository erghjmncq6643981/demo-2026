package com.chandler.learning.agent.vocabulary.api.controller;

import com.chandler.learning.agent.vocabulary.application.VocabularyAudioService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * 英语词汇音频流控制器。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/english/audio")
@Tag(name = "英语词汇发音音频")
public class EnglishVocabularyAudioController {

    private final VocabularyAudioService vocabularyAudioService;

    /**
     * 获取单词指定口音的发音音频文件流（支持 365 天 HTTP 强缓存）。
     *
     * @param voiceType 口音（us: 美音, uk: 英音）
     * @param term      单词名称
     */
    @GetMapping(value = "/{voiceType}/{term}", produces = "audio/mpeg")
    @Operation(summary = "获取单词发音音频流")
    public ResponseEntity<Resource> getAudio(@PathVariable String voiceType, @PathVariable String term) {
        Resource audioResource = vocabularyAudioService.resolveAudioResource(term, voiceType);
        if (audioResource == null || !audioResource.exists()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(term + ".mp3").build().toString())
                .body(audioResource);
    }
}
