package com.chandler.test.example;

import com.alibaba.nls.client.AccessToken;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.OutputFormatEnum;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizer;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerListener;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 阿里云智能语音合成（TTS）SDK 测试示例。
 * <p>
 * 参考官方文档：https://help.aliyun.com/zh/isi/developer-reference/sdk-for-java-9
 */
public class TtsTest {

    private static final Logger log = LoggerFactory.getLogger(TtsTest.class);

    private final String appKey;
    private final NlsClient client;

    public TtsTest(String appKey, String accessKeyId, String accessKeySecret, String url) throws IOException {
        this.appKey = appKey;
        // 1. 获取鉴权 Token
        AccessToken accessToken = new AccessToken(accessKeyId, accessKeySecret);
        accessToken.apply();
        if (accessToken.getToken() == null || accessToken.getToken().isBlank()) {
            throw new IllegalStateException("获取 NLS AccessToken 失败，请检查 AccessKey ID / Secret 是否正确并已在阿里云开通智能语音交互服务权限！");
        }
        log.info("成功获取 NLS AccessToken: {}, 有效期至: {}", accessToken.getToken(), accessToken.getExpireTime());

        // 2. 初始化全局 NlsClient（建议单例复用）
        if (url == null || url.trim().isEmpty()) {
            this.client = new NlsClient(accessToken.getToken());
        } else {
            this.client = new NlsClient(url.trim(), accessToken.getToken());
        }
    }

    /**
     * 创建语音合成监听器。
     *
     * @param outputFile 合成音频输出文件路径
     */
    private SpeechSynthesizerListener createListener(File outputFile) {
        return new SpeechSynthesizerListener() {
            private FileOutputStream fout;
            private boolean firstChunk = true;
            private long startTime;

            @Override
            public void onMessage(ByteBuffer message) {
                try {
                    if (fout == null) {
                        startTime = System.currentTimeMillis();
                        if (outputFile.getParentFile() != null && !outputFile.getParentFile().exists()) {
                            outputFile.getParentFile().mkdirs();
                        }
                        fout = new FileOutputStream(outputFile);
                    }
                    if (firstChunk) {
                        firstChunk = false;
                        log.info("TTS 首包音频流到达延迟: {} ms", (System.currentTimeMillis() - startTime));
                    }
                    byte[] bytes = new byte[message.remaining()];
                    message.get(bytes, 0, bytes.length);
                    fout.write(bytes);
                } catch (IOException e) {
                    log.error("写入合成音频文件异常: {}", e.getMessage(), e);
                }
            }

            @Override
            public void onComplete(SpeechSynthesizerResponse response) {
                try {
                    if (fout != null) {
                        fout.flush();
                        fout.close();
                    }
                } catch (IOException ignored) {
                }
                log.info("语音合成成功完成: name={}, status={}, taskId={}, 输出文件={}",
                        response.getName(), response.getStatus(), response.getTaskId(), outputFile.getAbsolutePath());
            }

            @Override
            public void onFail(SpeechSynthesizerResponse response) {
                try {
                    if (fout != null) {
                        fout.close();
                    }
                } catch (IOException ignored) {
                }
                log.error("语音合成失败: taskId={}, status={}, statusText={}",
                        response.getTaskId(), response.getStatus(), response.getStatusText());
            }
        };
    }

    /**
     * 执行语音合成。
     *
     * @param text       待合成文本
     * @param voice      发音人（如 siyue、zhimiao_emo、zhiwei 等）
     * @param outputFile 输出音频文件
     */
    public void synthesize(String text, String voice, File outputFile) {
        SpeechSynthesizer synthesizer = null;
        try {
            synthesizer = new SpeechSynthesizer(client, createListener(outputFile));
            synthesizer.setAppKey(appKey);
            // 音频格式设置：MP3 / WAV / PCM
            synthesizer.setFormat(OutputFormatEnum.MP3);
            // 采样率设置：16000Hz
            synthesizer.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
            // 发音人
            synthesizer.setVoice(voice != null ? voice : "siyue");
            // 语调与语速（范围 -500 ~ 500，默认 0）
            synthesizer.setPitchRate(0);
            synthesizer.setSpeechRate(0);
            // 待合成文本
            synthesizer.setText(text);
            synthesizer.addCustomedParam("enable_subtitle", false);

            long start = System.currentTimeMillis();
            synthesizer.start();
            log.info("TTS 请求已发送，等待服务端合成响应...");

            // 等待合成结束（2.1.7 及以上版本单位为毫秒）
            synthesizer.waitForComplete();
            log.info("TTS 语音合成总耗时: {} ms", (System.currentTimeMillis() - start));
        } catch (Exception e) {
            log.error("TTS 合成执行异常: {}", e.getMessage(), e);
        } finally {
            if (synthesizer != null) {
                synthesizer.close();
            }
        }
    }

    public void shutdown() {
        if (client != null) {
            client.shutdown();
        }
    }

    public static void main(String[] args) throws IOException {
        String appKey = "";
        String accessKeyId = "";
        String accessKeySecret = "";
        String gatewayUrl = System.getenv().getOrDefault("NLS_GATEWAY_URL", "wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1");

        TtsTest ttsTest = new TtsTest(appKey, accessKeyId, accessKeySecret, gatewayUrl);
        File outputFile = new File("target/tts_test_output.wav");
        String testText = "欢迎使用阿里云智能语音合成服务。英语学习助手为您带来地道流利的发音体验。";
        ttsTest.synthesize(testText, "siyue", outputFile);
        ttsTest.shutdown();
    }
}
