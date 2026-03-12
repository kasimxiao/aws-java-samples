package com.aws.sample;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.transcribe.TranscribeStreamingService;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.transcribestreaming.model.LanguageCode;

/**
 * Transcribe 流式语音转文字测试
 *
 * WAV 文件要求：PCM 编码，16bit，单声道（采样率自动从文件头读取）
 */
class TranscribeStreamingTest {

    /** 测试音频文件路径 */
    private static final String WAV_FILE = "tmp/recording.wav";

    @Test
    void testTranscribeDefault() {
        runTranscribe("默认区域", null);
    }

    @Test
    void testTranscribeFrankfurt() {
        runTranscribe("法兰克福", Region.EU_CENTRAL_1);
    }

    @Test
    void testTranscribeSingapore() {
        runTranscribe("新加坡", Region.AP_SOUTHEAST_1);
    }

    @Test
    void testTranscribeTokyo() {
        runTranscribe("东京", Region.AP_NORTHEAST_1);
    }

    /**
     * 通用转录测试方法
     *
     * @param regionName 区域显示名称
     * @param region     AWS 区域，null 则使用配置文件默认区域
     */
    private void runTranscribe(String regionName, Region region) {
        AwsConfig config = new AwsConfig();

        try (TranscribeStreamingService service = region != null
                ? new TranscribeStreamingService(config, region)
                : new TranscribeStreamingService(config)) {

            AtomicLong firstResponseTime = new AtomicLong(0);
            long startTime = System.currentTimeMillis();

            String result = service.transcribeWavFile(WAV_FILE, LanguageCode.ZH_CN,
                    transcript -> {
                        firstResponseTime.compareAndSet(0, System.currentTimeMillis());
                        System.out.print(transcript);
                    });

            long endTime = System.currentTimeMillis();
            long firstResponse = firstResponseTime.get() > 0 ? firstResponseTime.get() - startTime : -1;

            System.out.println("\n\n===== 完整转录结果（" + regionName + "）=====");
            System.out.println(result);
            System.out.println("\n===== 响应时间统计（" + regionName + "）=====");
            System.out.println("首次响应时间: " + firstResponse + " ms");
            System.out.println("总耗时: " + (endTime - startTime) + " ms");
        }
    }
}
