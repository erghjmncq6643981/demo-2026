package com.chandler.fcc.admin.controller;

import com.chandler.fcc.admin.model.CommonResult;
import com.chandler.fcc.admin.model.vo.RecordingMetaVO;
import com.chandler.fcc.admin.service.RecordingQueryService;
import com.chandler.fcc.common.recording.RecordingPathLayout;
import com.chandler.fcc.common.recording.RecordingStorageProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 通话录音复播与下载 REST 控制器
 * <p>
 * 录音文件由 FreeSWITCH 直接写入与控制面共享的存储目录（{@code fcc.recording.base-dir}），
 * 本控制器从 {@code fcc_call_recording.object_key} 记录的同一路径读取文件，
 * 对外提供两种能力：
 * </p>
 * <ul>
 *   <li><b>复播</b>：支持 RFC 7233 HTTP 206 Partial Content（{@code Range: bytes=x-y}），
 *       浏览器播放器可即时响应、拖拽进度条与断点缓冲；</li>
 *   <li><b>下载</b>：以 {@code Content-Disposition: attachment} 返回带业务文件名的原始音频。</li>
 * </ul>
 * <p>
 * 文件不存在时返回 404 并如实说明，不做任何演示音频兜底——避免"有录音"的假象掩盖链路故障。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Tag(name = "录音复播与下载", description = "按通话或录音 ID 流式复播 (HTTP 206) 与下载通话录音")
@RestController
@RequestMapping("/api/admin/recordings")
@RequiredArgsConstructor
public class CallRecordingController {

    private final RecordingQueryService recordingQueryService;
    private final RecordingStorageProperties recordingProperties;

    /**
     * 查询指定通话的录音元数据列表
     *
     * @param callId 业务通话主键 ID
     * @return 录音元数据列表 (含复播与下载地址)
     */
    @Operation(summary = "查询通话录音元数据列表")
    @GetMapping("/{callId}/meta")
    public CommonResult<List<RecordingMetaVO>> listRecordingMeta(@PathVariable("callId") Long callId) {
        return CommonResult.success(recordingQueryService.listByCallId(callId));
    }

    /**
     * 按通话主键复播最新一条录音
     *
     * @param callId 业务通话主键 ID
     */
    @Operation(summary = "按通话ID复播录音 (支持 Range 分块流式)")
    @GetMapping(value = "/{callId}/stream")
    public void streamByCallId(@PathVariable("callId") Long callId,
                              HttpServletRequest request,
                              HttpServletResponse response) throws IOException {
        Optional<RecordingQueryService.ResolvedRecording> resolved = recordingQueryService.resolveLatestByCallId(callId);
        if (resolved.isEmpty()) {
            writeNotFound(response, "该通话暂无可读取的录音文件");
            return;
        }
        serveFileStream(resolved.get().file(), request, response, null);
    }

    /**
     * 按通话主键下载最新一条录音
     *
     * @param callId 业务通话主键 ID
     */
    @Operation(summary = "按通话ID下载录音 (附件形式)")
    @GetMapping(value = "/{callId}/download")
    public void downloadByCallId(@PathVariable("callId") Long callId,
                                HttpServletRequest request,
                                HttpServletResponse response) throws IOException {
        Optional<RecordingQueryService.ResolvedRecording> resolved = recordingQueryService.resolveLatestByCallId(callId);
        if (resolved.isEmpty()) {
            writeNotFound(response, "该通话暂无可下载的录音文件");
            return;
        }
        serveFileStream(resolved.get().file(), request, response, buildDownloadName(resolved.get()));
    }

    /**
     * 按录音业务唯一标识复播录音
     *
     * @param recordingId 录音业务唯一标识
     */
    @Operation(summary = "按录音ID复播录音 (支持 Range 分块流式)")
    @GetMapping(value = "/by-rec-id/{recordingId}/stream")
    public void streamByRecordingId(@PathVariable("recordingId") String recordingId,
                                   HttpServletRequest request,
                                   HttpServletResponse response) throws IOException {
        Optional<RecordingQueryService.ResolvedRecording> resolved = recordingQueryService.resolveByRecordingId(recordingId);
        if (resolved.isEmpty()) {
            writeNotFound(response, "录音文件不存在或尚未落盘: " + recordingId);
            return;
        }
        serveFileStream(resolved.get().file(), request, response, null);
    }

    /**
     * 按录音业务唯一标识下载录音
     *
     * @param recordingId 录音业务唯一标识
     */
    @Operation(summary = "按录音ID下载录音 (附件形式)")
    @GetMapping(value = "/by-rec-id/{recordingId}/download")
    public void downloadByRecordingId(@PathVariable("recordingId") String recordingId,
                                     HttpServletRequest request,
                                     HttpServletResponse response) throws IOException {
        Optional<RecordingQueryService.ResolvedRecording> resolved = recordingQueryService.resolveByRecordingId(recordingId);
        if (resolved.isEmpty()) {
            writeNotFound(response, "录音文件不存在或尚未落盘: " + recordingId);
            return;
        }
        serveFileStream(resolved.get().file(), request, response, buildDownloadName(resolved.get()));
    }

    /**
     * 构建下载文件名
     * <p>
     * 优先使用录音业务标识，便于下载后仍能与系统内的录音记录对应。
     * </p>
     *
     * @param resolved 已解析录音
     * @return 下载文件名
     */
    private String buildDownloadName(RecordingQueryService.ResolvedRecording resolved) {
        RecordingMetaVO meta = resolved.meta();
        String fallback = RecordingPathLayout.fileNameOf(resolved.file(), null);
        if (meta != null && meta.getRecordingId() != null && !meta.getRecordingId().isBlank()) {
            String format = (meta.getMediaFormat() != null && !meta.getMediaFormat().isBlank())
                    ? meta.getMediaFormat()
                    : "wav";
            return meta.getRecordingId() + "." + format;
        }
        return fallback;
    }

    /**
     * 输出录音文件流
     * <p>
     * {@code downloadName} 为空时按复播处理 (支持 Range)；非空时按附件下载处理。
     * </p>
     *
     * @param file         录音文件路径
     * @param request      HTTP 请求 (用于读取 Range 头)
     * @param response     HTTP 响应
     * @param downloadName 下载文件名；null 表示在线复播
     */
    private void serveFileStream(Path file, HttpServletRequest request, HttpServletResponse response,
                                String downloadName) throws IOException {
        long fileLength = Files.size(file);
        String contentType = resolveContentType(file);

        response.setContentType(contentType);
        response.setHeader("Accept-Ranges", "bytes");

        if (downloadName != null) {
            String encoded = URLEncoder.encode(downloadName, StandardCharsets.UTF_8).replace("+", "%20");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
        }

        String rangeHeader = request.getHeader("Range");
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            servePartial(file, fileLength, rangeHeader, response);
        } else {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentLengthLong(fileLength);
            try (InputStream in = Files.newInputStream(file);
                 OutputStream out = response.getOutputStream()) {
                in.transferTo(out);
                out.flush();
            }
        }
    }

    /**
     * 输出 HTTP 206 分块内容
     *
     * @param file       录音文件路径
     * @param fileLength 文件总长度
     * @param rangeHeader Range 请求头取值
     * @param response   HTTP 响应
     */
    private void servePartial(Path file, long fileLength, String rangeHeader,
                             HttpServletResponse response) throws IOException {
        String rangeValue = rangeHeader.substring(6).trim();
        long start = 0;
        long end = fileLength - 1;

        int dashIndex = rangeValue.indexOf('-');
        if (dashIndex != -1) {
            String startStr = rangeValue.substring(0, dashIndex).trim();
            String endStr = rangeValue.substring(dashIndex + 1).trim();
            if (!startStr.isEmpty()) {
                start = parseLongOrDefault(startStr, 0L);
            }
            if (!endStr.isEmpty()) {
                end = parseLongOrDefault(endStr, fileLength - 1);
            }
        }

        if (start > end || start >= fileLength) {
            response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            response.setHeader("Content-Range", "bytes */" + fileLength);
            return;
        }
        if (end >= fileLength) {
            end = fileLength - 1;
        }

        long contentLength = end - start + 1;
        response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        response.setHeader("Content-Range", String.format("bytes %d-%d/%d", start, end, fileLength));
        response.setContentLengthLong(contentLength);

        int bufferSize = recordingProperties.getStreamBufferBytes() > 0
                ? recordingProperties.getStreamBufferBytes() : 8192;
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r");
             OutputStream out = response.getOutputStream()) {
            raf.seek(start);
            byte[] buffer = new byte[bufferSize];
            long remaining = contentLength;
            while (remaining > 0) {
                int read = raf.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read == -1) {
                    break;
                }
                out.write(buffer, 0, read);
                remaining -= read;
            }
            out.flush();
        }
    }

    /**
     * 依据文件扩展名解析音频 MIME 类型
     *
     * @param file 录音文件路径
     * @return MIME 类型
     */
    private String resolveContentType(Path file) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase();
        if (name.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (name.endsWith(".ogg")) {
            return "audio/ogg";
        }
        return "audio/wav";
    }

    /**
     * 输出 404 提示
     *
     * @param response HTTP 响应
     * @param message  提示文案
     */
    private void writeNotFound(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(message);
    }

    /**
     * 安全解析长整型
     *
     * @param value        原始文本
     * @param defaultValue 兜底值
     * @return 解析结果
     */
    private long parseLongOrDefault(String value, long defaultValue) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
