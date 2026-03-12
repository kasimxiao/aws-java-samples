package com.aws.sample.transcribe;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aws.sample.common.AwsConfig;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.transcribestreaming.TranscribeStreamingAsyncClient;
import software.amazon.awssdk.services.transcribestreaming.model.AudioEvent;
import software.amazon.awssdk.services.transcribestreaming.model.AudioStream;
import software.amazon.awssdk.services.transcribestreaming.model.LanguageCode;
import software.amazon.awssdk.services.transcribestreaming.model.MediaEncoding;
import software.amazon.awssdk.services.transcribestreaming.model.Result;
import software.amazon.awssdk.services.transcribestreaming.model.StartStreamTranscriptionRequest;
import software.amazon.awssdk.services.transcribestreaming.model.StartStreamTranscriptionResponseHandler;
import software.amazon.awssdk.services.transcribestreaming.model.TranscriptEvent;

/**
 * AWS Transcribe 流式语音转文字服务
 * 传入 WAV 音频文件，通过流式方式实时返回转录文字
 */
public class TranscribeStreamingService implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(TranscribeStreamingService.class);

    /** 每次发送的音频块大小（字节） */
    private static final int CHUNK_SIZE_IN_BYTES = 1024;

    /** WAV 文件头大小（字节），跳过文件头直接读取 PCM 数据 */
    private static final int WAV_HEADER_SIZE = 44;

    private final TranscribeStreamingAsyncClient client;

    public TranscribeStreamingService(AwsConfig config) {
        this(config, config.getRegion());
    }

    /**
     * 构造方法（指定区域）
     *
     * @param config 配置
     * @param region 目标区域（如 Region.AP_SOUTHEAST_1 新加坡）
     */
    public TranscribeStreamingService(AwsConfig config, software.amazon.awssdk.regions.Region region) {
        this.client = TranscribeStreamingAsyncClient.builder()
                .credentialsProvider(config.getCredentialsProvider())
                .region(region)
                .build();
        logger.info("TranscribeStreamingAsyncClient 初始化，区域: {}", region);
    }

    /**
     * 流式转录 WAV 音频文件（默认中文，16kHz 采样率）
     *
     * @param wavFilePath WAV 文件路径（要求：PCM 编码，16kHz，16bit，单声道）
     * @param onTranscript 每次收到转录文字的回调，参数为当前识别的文字片段
     * @return 完整的转录文本
     */
    public String transcribeWavFile(String wavFilePath, Consumer<String> onTranscript) {
        return transcribeWavFile(wavFilePath, LanguageCode.ZH_CN, 16_000, onTranscript);
    }

    /**
     * 流式转录 WAV 音频文件（自定义语言和采样率）
     *
     * @param wavFilePath          WAV 文件路径（要求：PCM 编码）
     * @param languageCode         语言代码（如 ZH_CN、EN_US）
     * @param mediaSampleRateHertz 采样率（如 16000、8000）
     * @param onTranscript         每次收到转录文字的回调
     * @return 完整的转录文本
     */
    public String transcribeWavFile(String wavFilePath, LanguageCode languageCode,
                                     int mediaSampleRateHertz, Consumer<String> onTranscript) {
        logger.info("开始流式转录，文件: {}，语言: {}，采样率: {}Hz",
                wavFilePath, languageCode, mediaSampleRateHertz);

        InputStream audioStream = getStreamFromWavFile(wavFilePath);
        StringBuilder fullTranscript = new StringBuilder();

        // 构建转录请求
        StartStreamTranscriptionRequest request = StartStreamTranscriptionRequest.builder()
                .languageCode(languageCode)
                .mediaEncoding(MediaEncoding.PCM)
                .mediaSampleRateHertz(mediaSampleRateHertz)
                .build();

        // 构建流式响应处理器
        StartStreamTranscriptionResponseHandler responseHandler = StartStreamTranscriptionResponseHandler.builder()
                .onResponse(r -> logger.info("收到初始响应，请求ID: {}", r.requestId()))
                .onError(e -> logger.error("流式转录出错: {}", e.getMessage(), e))
                .onComplete(() -> logger.info("流式转录完成"))
                .subscriber(event -> {
                    List<Result> results = ((TranscriptEvent) event).transcript().results();
                    if (!results.isEmpty()) {
                        Result result = results.get(0);
                        String transcript = result.alternatives().get(0).transcript();
                        if (transcript != null && !transcript.isEmpty()) {
                            // 只输出最终结果（非部分结果），避免重复
                            if (!result.isPartial()) {
                                fullTranscript.append(transcript);
                            }
                            // 回调每次识别的文字（包含部分结果，实现实时效果）
                            if (onTranscript != null) {
                                onTranscript.accept(transcript);
                            }
                        }
                    }
                })
                .build();

        try {
            client.startStreamTranscription(request,
                    new AudioStreamPublisher(audioStream),
                    responseHandler).get();
        } catch (Exception e) {
            logger.error("流式转录失败: {}", e.getMessage(), e);
            throw new RuntimeException("流式转录失败: " + e.getMessage(), e);
        }

        logger.info("转录结果: {}", fullTranscript);
        return fullTranscript.toString();
    }

    /**
     * 从 WAV 文件获取 PCM 音频流（跳过 WAV 文件头）
     */
    private InputStream getStreamFromWavFile(String wavFilePath) {
        try {
            FileInputStream fis = new FileInputStream(wavFilePath);
            // 跳过 WAV 文件头（44 字节），只发送 PCM 数据
            long skipped = fis.skip(WAV_HEADER_SIZE);
            if (skipped < WAV_HEADER_SIZE) {
                logger.warn("WAV 文件头跳过不完整，预期 {} 字节，实际跳过 {} 字节",
                        WAV_HEADER_SIZE, skipped);
            }
            return fis;
        } catch (IOException e) {
            throw new RuntimeException("无法读取音频文件: " + wavFilePath, e);
        }
    }

    @Override
    public void close() {
        if (client != null) {
            client.close();
            logger.info("TranscribeStreamingAsyncClient 已关闭");
        }
    }

    // ==================== 音频流发布者 ====================

    /**
     * 音频流发布者，将 InputStream 转换为 Reactive Streams 的 Publisher
     * 按块读取音频数据并发送给 Transcribe 服务
     */
    private static class AudioStreamPublisher implements Publisher<AudioStream> {

        private final InputStream inputStream;

        AudioStreamPublisher(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public void subscribe(Subscriber<? super AudioStream> subscriber) {
            subscriber.onSubscribe(new AudioStreamSubscription(subscriber, inputStream));
        }
    }

    /**
     * 音频流订阅实现，负责按需读取音频块并推送
     */
    private static class AudioStreamSubscription implements Subscription {

        private final Subscriber<? super AudioStream> subscriber;
        private final InputStream inputStream;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final AtomicLong demand = new AtomicLong(0);

        AudioStreamSubscription(Subscriber<? super AudioStream> subscriber, InputStream inputStream) {
            this.subscriber = subscriber;
            this.inputStream = inputStream;
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                subscriber.onError(new IllegalArgumentException("请求数量必须为正数"));
                return;
            }

            demand.getAndAdd(n);
            executor.submit(() -> {
                try {
                    do {
                        ByteBuffer audioBuffer = readNextChunk();
                        if (audioBuffer.remaining() > 0) {
                            AudioEvent audioEvent = AudioEvent.builder()
                                    .audioChunk(SdkBytes.fromByteBuffer(audioBuffer))
                                    .build();
                            subscriber.onNext(audioEvent);
                        } else {
                            // 音频数据读取完毕
                            subscriber.onComplete();
                            break;
                        }
                    } while (demand.decrementAndGet() > 0);
                } catch (Exception e) {
                    subscriber.onError(e);
                }
            });
        }

        @Override
        public void cancel() {
            executor.shutdown();
        }

        /**
         * 读取下一块音频数据
         */
        private ByteBuffer readNextChunk() {
            byte[] audioBytes = new byte[CHUNK_SIZE_IN_BYTES];
            try {
                int len = inputStream.read(audioBytes);
                if (len <= 0) {
                    return ByteBuffer.allocate(0);
                }
                return ByteBuffer.wrap(audioBytes, 0, len);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
