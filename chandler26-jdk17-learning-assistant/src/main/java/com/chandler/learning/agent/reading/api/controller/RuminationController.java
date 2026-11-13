package com.chandler.learning.agent.reading.api.controller;

import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import com.chandler.learning.agent.reading.api.request.RuminationCustomScenarioRequest;
import com.chandler.learning.agent.reading.api.request.RuminationFissionRequest;
import com.chandler.learning.agent.reading.api.request.RuminationGraphBagUpdateRequest;
import com.chandler.learning.agent.reading.api.request.RuminationGraphPositionsUpdateRequest;
import com.chandler.learning.agent.reading.api.request.RuminationGraphSaveRequest;
import com.chandler.learning.agent.reading.api.request.RuminationMaterialGenerateRequest;
import com.chandler.learning.agent.reading.api.request.RuminationProbeSubmitRequest;
import com.chandler.learning.agent.reading.api.request.RuminationSentencePracticeSubmitRequest;
import com.chandler.learning.agent.reading.api.response.RuminationAsyncTaskItemResponse;
import com.chandler.learning.agent.reading.api.response.RuminationFissionResponse;
import com.chandler.learning.agent.reading.api.response.RuminationGraphResponse;
import com.chandler.learning.agent.reading.api.response.RuminationGraphSaveResponse;
import com.chandler.learning.agent.reading.api.response.RuminationMaterialAudioStatusResponse;
import com.chandler.learning.agent.reading.api.response.RuminationMaterialResponse;
import com.chandler.learning.agent.reading.api.response.RuminationProbeSubmitResponse;
import com.chandler.learning.agent.reading.api.response.RuminationScenarioResponse;
import com.chandler.learning.agent.reading.api.response.RuminationSentencePracticeSubmitResponse;
import com.chandler.learning.agent.reading.application.RuminationAudioService;
import com.chandler.learning.agent.reading.application.RuminationGraphTaskSubmissionService;
import com.chandler.learning.agent.reading.application.RuminationMaterialTaskSubmissionService;
import com.chandler.learning.agent.reading.application.RuminationSentencePracticeService;
import com.chandler.learning.agent.reading.application.RuminationService;
import com.chandler.learning.agent.security.CurrentUserContext;
import com.chandler.learning.agent.task.api.response.AiAsyncTaskResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 知识反刍接口控制器。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/learning/rumination")
@Tag(name = "知识反刍")
public class RuminationController {

    private final CurrentUserContext currentUserContext;
    private final RuminationService ruminationService;
    private final RuminationAudioService ruminationAudioService;
    private final RuminationMaterialTaskSubmissionService materialTaskSubmissionService;
    private final RuminationGraphTaskSubmissionService graphTaskSubmissionService;
    private final RuminationSentencePracticeService sentencePracticeService;

    /** 查询指定词本的主题场景列表及词汇统计。 */
    @GetMapping("/scenarios")
    @Operation(summary = "查询主题场景列表及词汇统计")
    public List<RuminationScenarioResponse> listScenarios(
            @RequestParam Long wordbookId) {
        LearningUser user = currentUserContext.requireUser();
        return ruminationService.listScenarios(user.getId(), wordbookId);
    }

    /** 获取指定场景下的星系图谱拓扑数据。 */
    @GetMapping("/graph")
    @Operation(summary = "获取指定场景下的星系图谱拓扑数据")
    public RuminationGraphResponse getGraph(
            @RequestParam Long wordbookId,
            @RequestParam(defaultValue = "produce_kitchen") String scenarioCode) {
        LearningUser user = currentUserContext.requireUser();
        return ruminationService.getGraph(user.getId(), wordbookId, scenarioCode);
    }

    /** 自定义新场景宇宙初始化（兼容直连调用）。 */
    @PostMapping("/scenarios/custom")
    @Operation(summary = "自定义新场景宇宙初始化（同步）")
    public RuminationGraphResponse initCustomScenario(@Valid @RequestBody RuminationCustomScenarioRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return ruminationService.initCustomScenario(user.getId(), request);
    }

    /** 提交异步自定义主题场景构建任务，立即返回 taskId 并按提交快照异步生成落库。 */
    @PostMapping("/scenarios/custom/async")
    @Operation(summary = "提交异步自定义主题场景构建任务")
    public AiAsyncTaskResponse submitCustomScenarioTask(
            @Valid @RequestBody RuminationCustomScenarioRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return graphTaskSubmissionService.submitCustomScenarioTask(user.getId(), request);
    }

    /** 校验版本号并发持久化保存场景星系图谱与词袋状态。 */
    @PostMapping("/graph/save")
    @Operation(summary = "持久化保存场景星系图谱与词袋状态")
    public RuminationGraphSaveResponse saveGraph(@Valid @RequestBody RuminationGraphSaveRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return ruminationService.saveGraph(user.getId(), request);
    }

    /** 增量更新部分节点固定坐标，避免覆盖并发衍生裂变与词袋变更。 */
    @PostMapping("/graph/positions")
    @Operation(summary = "增量更新星系节点坐标位置")
    public RuminationGraphSaveResponse updateNodePositions(
            @Valid @RequestBody RuminationGraphPositionsUpdateRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return ruminationService.updateNodePositions(user.getId(), request);
    }

    /** 增量添加或移除单个词袋词汇，避免整图快照互相覆盖。 */
    @PostMapping("/graph/bag")
    @Operation(summary = "增量变更词袋单个词条")
    public RuminationGraphSaveResponse updateBagItem(
            @Valid @RequestBody RuminationGraphBagUpdateRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return ruminationService.updateBagItem(user.getId(), request);
    }

    /** 查询场景材料文章列表。 */
    @GetMapping("/materials")
    @Operation(summary = "查询场景材料文章列表")
    public List<RuminationMaterialResponse> listMaterials(
            @RequestParam Long wordbookId,
            @RequestParam(required = false) String scenarioCode) {
        LearningUser user = currentUserContext.requireUser();
        List<RuminationMaterialResponse> materials = ruminationService.listMaterials(user.getId(), wordbookId, scenarioCode);
        sentencePracticeService.enrichMaterialResponses(user.getId(), materials);
        return materials;
    }

    /** 针对词袋词汇同步编织场景材料文章与微环境探针（兼容直连调用）。 */
    @PostMapping("/materials/generate")
    @Operation(summary = "针对词袋词汇同步编织场景材料文章与微环境探针")
    public RuminationMaterialResponse generateMaterial(
            @Valid @RequestBody RuminationMaterialGenerateRequest request) {
        LearningUser user = currentUserContext.requireUser();
        RuminationMaterialResponse response = ruminationService.generateMaterial(user.getId(), request);
        sentencePracticeService.enrichMaterialResponse(user.getId(), response);
        return response;
    }

    /** 提交异步编织场景材料与微环境探针任务，立即返回 taskId 并按提交快照隔离执行。 */
    @PostMapping("/materials/generate/async")
    @Operation(summary = "提交异步编织场景材料与微环境探针任务")
    public AiAsyncTaskResponse submitGenerateMaterialTask(
            @Valid @RequestBody RuminationMaterialGenerateRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return materialTaskSubmissionService.submitGenerateTask(user.getId(), request);
    }

    /** 基于已有场景材料重新生成一份新文章与微环境探针，保留历史记录且不扣除当前图谱词袋。 */
    @PostMapping("/materials/{materialId}/regenerate/async")
    @Operation(summary = "基于历史场景材料异步重新生成")
    public AiAsyncTaskResponse submitRegenerateMaterialTask(
            @PathVariable Long materialId,
            @RequestParam(required = false) String requestId) {
        LearningUser user = currentUserContext.requireUser();
        return materialTaskSubmissionService.submitRegenerateTask(user.getId(), materialId, requestId);
    }

    /** 查询指定单词本和场景最新一次材料异步生成任务状态，供页面刷新后恢复进度追踪。 */
    @GetMapping("/materials/task")
    @Operation(summary = "查询知识反刍场景材料最新异步任务")
    public AiAsyncTaskResponse getLatestMaterialTask(
            @RequestParam Long wordbookId,
            @RequestParam String scenarioCode) {
        LearningUser user = currentUserContext.requireUser();
        return materialTaskSubmissionService.findLatestTaskForScenario(user.getId(), wordbookId, scenarioCode);
    }

    /** 查询单个场景材料文章详情与微环境探针状态。 */
    @GetMapping("/materials/{materialId}")
    @Operation(summary = "查询知识反刍场景材料详情")
    public RuminationMaterialResponse getMaterialDetail(@PathVariable Long materialId) {
        LearningUser user = currentUserContext.requireUser();
        RuminationMaterialResponse detail = ruminationService.getMaterialDetail(user.getId(), materialId);
        sentencePracticeService.enrichMaterialResponse(user.getId(), detail);
        return detail;
    }

    /** 获取指定知识反刍场景材料已生成的 MP3 音频流（若未就绪返回 404）。 */
    @GetMapping(value = "/materials/{materialId}/audio", produces = "audio/mpeg")
    @Operation(summary = "获取反刍场景材料已生成的音频流")
    public ResponseEntity<Resource> getMaterialAudio(@PathVariable Long materialId) {
        LearningUser user = currentUserContext.requireUser();
        Resource audioResource = ruminationAudioService.getExistingMaterialAudio(user.getId(), materialId);
        if (audioResource == null || !audioResource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("rumination-" + materialId + ".mp3").build().toString())
                .body(audioResource);
    }

    /** 查询指定场景材料的 AI 语音合成就绪状态与异步任务进度。 */
    @GetMapping("/materials/{materialId}/audio/status")
    @Operation(summary = "查询反刍场景材料音频状态")
    public RuminationMaterialAudioStatusResponse getMaterialAudioStatus(@PathVariable Long materialId) {
        LearningUser user = currentUserContext.requireUser();
        return ruminationAudioService.getAudioStatus(user.getId(), materialId);
    }

    /** 异步提交知识反刍场景材料全文语音生成任务。 */
    @PostMapping("/materials/{materialId}/audio/async")
    @Operation(summary = "异步提交反刍场景材料语音生成任务")
    public RuminationMaterialAudioStatusResponse generateMaterialAudioAsync(
            @PathVariable Long materialId,
            @RequestParam(name = "forceRefresh", required = false, defaultValue = "false") boolean forceRefresh) {
        LearningUser user = currentUserContext.requireUser();
        return ruminationAudioService.submitAudioGenerationTask(user.getId(), materialId, forceRefresh);
    }

    /** 按需同步生成并返回知识反刍场景材料的完整 MP3 语音音频流。 */
    @PostMapping(value = "/materials/{materialId}/audio/generate", produces = "audio/mpeg")
    @Operation(summary = "按需生成并返回反刍场景材料音频流")
    public ResponseEntity<Resource> generateMaterialAudio(
            @PathVariable Long materialId,
            @RequestParam(name = "forceRefresh", required = false, defaultValue = "false") boolean forceRefresh) {
        LearningUser user = currentUserContext.requireUser();
        Resource audioResource = ruminationAudioService.generateOrGetMaterialAudio(user.getId(), materialId, forceRefresh);
        if (audioResource == null || !audioResource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("rumination-" + materialId + ".mp3").build().toString())
                .body(audioResource);
    }

    /** 提交阅读后按句原句跟敲或目标词遮挡补全巩固记录。 */
    @PostMapping("/materials/practice/submit")
    @Operation(summary = "提交场景材料按句跟敲或遮挡补全巩固结果")
    public RuminationSentencePracticeSubmitResponse submitSentencePractice(
            @Valid @RequestBody RuminationSentencePracticeSubmitRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return sentencePracticeService.submitSentencePractice(user.getId(), request);
    }

    /** 针对选定词汇节点执行默认 AI 模型实时 4D 衍生裂变（兼容直连调用）。 */
    @PostMapping("/fission")
    @Operation(summary = "节点 4D 衍生裂变（同步）")
    public RuminationFissionResponse fissionNode(@Valid @RequestBody RuminationFissionRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return ruminationService.fissionNode(user.getId(), request);
    }

    /** 提交异步节点 4D 衍生裂变任务，绑定原词本、场景与父节点快照并立即返回 taskId。 */
    @PostMapping("/fission/async")
    @Operation(summary = "提交异步节点 4D 衍生裂变任务")
    public AiAsyncTaskResponse submitFissionTask(@Valid @RequestBody RuminationFissionRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return graphTaskSubmissionService.submitFissionTask(user.getId(), request);
    }

    /** 获取指定衍生裂变任务持久化应用到原场景后的增量节点与连线结果。 */
    @GetMapping("/fission/tasks/{taskId}/result")
    @Operation(summary = "获取节点衍生裂变任务结构化结果")
    public RuminationFissionResponse getFissionTaskResult(@PathVariable Long taskId) {
        LearningUser user = currentUserContext.requireUser();
        return graphTaskSubmissionService.getFissionTaskResult(user.getId(), taskId);
    }

    /** 查询当前单词本下三类知识反刍异步任务（材料、场景构建、衍生裂变）用于刷新与跨场景追踪恢复。 */
    @GetMapping("/tasks")
    @Operation(summary = "查询知识反刍工作台异步任务聚合列表")
    public List<RuminationAsyncTaskItemResponse> listRuminationTasks(
            @RequestParam Long wordbookId,
            @RequestParam(required = false) String scenarioCode) {
        LearningUser user = currentUserContext.requireUser();
        return graphTaskSubmissionService.listRuminationTasks(user.getId(), wordbookId, scenarioCode);
    }

    /** 提交微场景探针作答并结算复习记录。 */
    @PostMapping("/probes/submit")
    @Operation(summary = "提交微场景探针作答并结算复习记录")
    public RuminationProbeSubmitResponse submitProbe(
            @Valid @RequestBody RuminationProbeSubmitRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return ruminationService.submitProbe(user.getId(), request);
    }
}
