package com.aws.sample;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.transcribe.TranscribeStreamingService;

/**
 * Transcribe 流式语音转文字测试
 *
 * 使用前请准备一个 WAV 文件（PCM 编码，16kHz，16bit，单声道）
 * 可使用 ffmpeg 转换：
 * ffmpeg -i input.mp3 -ar 16000 -ac 1 -f wav output.wav
 */
class TranscribeStreamingTest {

    @Test
    void testTranscribeWavFile() {
        AwsConfig config = new AwsConfig();
        String wavFilePath = "tmp/test-audio.wav";

        try (TranscribeStreamingService service = new TranscribeStreamingService(config)) {
            // 记录首次响应时间
            AtomicLong firstResponseTime = new AtomicLong(0);
            long startTime = System.currentTimeMillis();

            String result = service.transcribeWavFile(wavFilePath, transcript -> {
                // 记录首次收到转录文字的时间
                firstResponseTime.compareAndSet(0, System.currentTimeMillis());
                System.out.print(transcript);
            });

            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            long firstResponse = firstResponseTime.get() > 0 ? firstResponseTime.get() - startTime : -1;

            System.out.println("\n\n===== 完整转录结果 =====");
            System.out.println(result);
            System.out.println("\n===== 响应时间统计 =====");
            System.out.println("首次响应时间: " + firstResponse + " ms");
            System.out.println("总耗时: " + totalTime + " ms");
        }
    }

    @Test
    void testTranscribeEnglishWavFile() {
        AwsConfig config = new AwsConfig();
        String wavFilePath = "/tmp/test-audio-en.wav";

        try (TranscribeStreamingService service = new TranscribeStreamingService(config)) {
            AtomicLong firstResponseTime = new AtomicLong(0);
            long startTime = System.currentTimeMillis();

            String result = service.transcribeWavFile(wavFilePath,
                    software.amazon.awssdk.services.transcribestreaming.model.LanguageCode.EN_US,
                    16_000,
                    transcript -> {
                        firstResponseTime.compareAndSet(0, System.currentTimeMillis());
                        System.out.print(transcript);
                    });

            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            long firstResponse = firstResponseTime.get() > 0 ? firstResponseTime.get() - startTime : -1;

            System.out.println("\n\n===== Full Transcript =====");
            System.out.println(result);
            System.out.println("\n===== 响应时间统计 =====");
            System.out.println("首次响应时间: " + firstResponse + " ms");
            System.out.println("总耗时: " + totalTime + " ms");
        }
    }
}
