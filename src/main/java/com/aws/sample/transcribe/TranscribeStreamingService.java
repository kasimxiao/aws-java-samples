package com.aws.sample.transcribe;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
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

    /** WAV 文件头大小（字节） */
    private static final int WAV_HEADER_SIZE = 44;

    private final TranscribeStreamingAsyncClient client;
    private final String regionName;

    public TranscribeStreamingService(AwsConfig config) {
        this(config, config.getRegion());
    }

    /**
     * 构造方法（指定区域）
     */
    public TranscribeStreamingService(AwsConfig config, software.amazon.awssdk.regions.Region region) {
        long t0 = System.currentTimeMillis();
        this.client = TranscribeStreamingAsyncClient.builder()
                .credentialsProvider(config.getCredentialsProvider())
                .region(region)
                .build();
        this.regionName = region.id();
        logger.info("[阶段1-客户端初始化] {}ms | 区域: {}", System.currentTimeMillis() - t0, region);
    }

    /**
     * 流式转录 WAV 音频文件（默认中文，自动读取采样率）
     */
    public String transcribeWavFile(String wavFilePath, Consumer<String> onTranscript) {
        return transcribeWavFile(wavFilePath, LanguageCode.ZH_CN, onTranscript);
    }

    /**
     * 流式转录 WAV 音频文件（指定语言，自动读取采样率）
     */
    public String transcribeWavFile(String wavFilePath, LanguageCode languageCode,
                                     Consumer<String> onTranscript) {
        int sampleRate = readWavSampleRate(wavFilePath);
        return transcribeWavFile(wavFilePath, languageCode, sampleRate, onTranscript);
    }

    /**
     * 流式转录 WAV 音频文件（自定义语言和采样率）
     */
    public String transcribeWavFile(String wavFilePath, LanguageCode languageCode,
                                     int mediaSampleRateHertz, Consumer<String> onTranscript) {
        // ===== 阶段2：文件读取 =====
        long t0 = System.currentTimeMillis();
        File audioFile = new File(wavFilePath);
        long fileSizeKB = audioFile.length() / 1024;
        InputStream audioStream = getStreamFromWavFile(wavFilePath);
        long t1 = System.currentTimeMillis();
        logger.info("[阶段2-文件读取] {}ms | 文件: {} | 大小: {}KB | 语言: {} | 采样率: {}Hz",
                t1 - t0, wavFilePath, fileSizeKB, languageCode, mediaSampleRateHertz);

        StringBuilder fullTranscript = new StringBuilder();

        // 各阶段时间戳记录
        AtomicLong requestSentTime = new AtomicLong(0);       // 请求发出时间
        AtomicLong firstChunkSentTime = new AtomicLong(0);    // 首块音频发送时间
        AtomicLong lastChunkSentTime = new AtomicLong(0);     // 末块音频发送时间
        AtomicLong firstResponseTime = new AtomicLong(0);     // 首个 HTTP 响应时间
        AtomicLong firstTranscriptTime = new AtomicLong(0);   // 首次转录文字时间
        AtomicLong lastTranscriptTime = new AtomicLong(0);    // 最后一次转录时间
        AtomicLong streamCompleteTime = new AtomicLong(0);    // 流完成时间
        AtomicInteger transcriptCount = new AtomicInteger(0); // 转录事件计数
        AtomicInteger finalCount = new AtomicInteger(0);      // 最终结果计数
        AtomicInteger partialCount = new AtomicInteger(0);    // 部分结果计数

        // ===== 阶段3：构建请求 =====
        long t2 = System.currentTimeMillis();
        StartStreamTranscriptionRequest request = StartStreamTranscriptionRequest.builder()
                .languageCode(languageCode)
                .mediaEncoding(MediaEncoding.PCM)
                .mediaSampleRateHertz(mediaSampleRateHertz)
                .build();
        long t3 = System.currentTimeMillis();
        logger.info("[阶段3-构建请求] {}ms", t3 - t2);

        // 构建流式响应处理器
        StartStreamTranscriptionResponseHandler responseHandler = StartStreamTranscriptionResponseHandler.builder()
                .onResponse(r -> {
                    long now = System.currentTimeMillis();
                    firstResponseTime.set(now);
                    logger.info("[阶段5-首个响应] +{}ms（从请求发出）| 请求ID: {} | SessionId: {}",
                            now - requestSentTime.get(), r.requestId(), r.sessionId());
                })
                .onError(e -> {
                    long now = System.currentTimeMillis();
                    logger.error("[错误] +{}ms | {}", now - requestSentTime.get(), e.getMessage(), e);
                })
                .onComplete(() -> {
                    long now = System.currentTimeMillis();
                    streamCompleteTime.set(now);
                    logger.info("[阶段8-流完成] +{}ms（从请求发出）", now - requestSentTime.get());
                })
                .subscriber(event -> {
                    long now = System.currentTimeMillis();
                    List<Result> results = ((TranscriptEvent) event).transcript().results();
                    if (!results.isEmpty()) {
                        Result result = results.get(0);
                        String transcript = result.alternatives().get(0).transcript();
                        if (transcript != null && !transcript.isEmpty()) {
                            int count = transcriptCount.incrementAndGet();
                            lastTranscriptTime.set(now);

                            if (result.isPartial()) {
                                partialCount.incrementAndGet();
                            } else {
                                finalCount.incrementAndGet();
                                fullTranscript.append(transcript);
                            }

                            // 首次转录
                            if (firstTranscriptTime.compareAndSet(0, now)) {
                                logger.info("[阶段6-首次转录] +{}ms（从请求发出）| +{}ms（从首个响应）",
                                        now - requestSentTime.get(),
                                        firstResponseTime.get() > 0 ? now - firstResponseTime.get() : -1);
                            }

                            // 每次转录事件详情
                            logger.info("[转录 #{}] +{}ms | 部分={} | 文字: {}",
                                    count, now - requestSentTime.get(),
                                    result.isPartial(), transcript);

                            if (onTranscript != null) {
                                onTranscript.accept(transcript);
                            }
                        }
                    }
                })
                .build();

        // ===== 阶段4：发送请求（含 TLS 握手 + HTTP/2 协商）=====
        try {
            long t4 = System.currentTimeMillis();
            requestSentTime.set(t4);
            logger.info("[阶段4-发送请求] 开始连接 {}...", regionName);

            client.startStreamTranscription(request,
                    new AudioStreamPublisher(audioStream, t4, firstChunkSentTime, lastChunkSentTime),
                    responseHandler).get();

            long t5 = System.currentTimeMillis();

            // ===== 耗时汇总 =====
            long reqStart = requestSentTime.get();
            long firstChunk = firstChunkSentTime.get();
            long lastChunk = lastChunkSentTime.get();
            long firstResp = firstResponseTime.get();
            long firstTrans = firstTranscriptTime.get();
            long lastTrans = lastTranscriptTime.get();
            long streamDone = streamCompleteTime.get();

            logger.info("╔══════════════════════════════════════════════════╗");
            logger.info("║           Transcribe 流式转录耗时分析           ║");
            logger.info("╠══════════════════════════════════════════════════╣");
            logger.info("║ 区域: {}", regionName);
            logger.info("║ 文件: {} ({}KB)", wavFilePath, fileSizeKB);
            logger.info("║ 采样率: {}Hz | 语言: {}", mediaSampleRateHertz, languageCode);
            logger.info("╠══════════════════════════════════════════════════╣");
            logger.info("║ [阶段1] 客户端初始化      （构造时已记录）");
            logger.info("║ [阶段2] 文件读取           {}ms", t1 - t0);
            logger.info("║ [阶段3] 构建请求           {}ms", t3 - t2);
            logger.info("║ [阶段4] 连接建立(TLS+H2)   {}ms", firstChunk > 0 ? firstChunk - reqStart : -1);
            logger.info("║ [阶段5] 首个HTTP响应       +{}ms（从请求发出）", firstResp > 0 ? firstResp - reqStart : -1);
            logger.info("║ [阶段6] 首次转录文字       +{}ms（从请求发出）| +{}ms（从首个响应）",
                    firstTrans > 0 ? firstTrans - reqStart : -1,
                    firstTrans > 0 && firstResp > 0 ? firstTrans - firstResp : -1);
            logger.info("║ [阶段7] 音频发送           {}ms（首块到末块）| 末块 +{}ms（从请求发出）",
                    firstChunk > 0 && lastChunk > 0 ? lastChunk - firstChunk : -1,
                    lastChunk > 0 ? lastChunk - reqStart : -1);
            logger.info("║ [阶段8] 流完成             +{}ms（从请求发出）| +{}ms（从末块发送）",
                    streamDone > 0 ? streamDone - reqStart : -1,
                    streamDone > 0 && lastChunk > 0 ? streamDone - lastChunk : -1);
            logger.info("╠══════════════════════════════════════════════════╣");
            logger.info("║ 转录事件: {} 次（部分: {} | 最终: {}）",
                    transcriptCount.get(), partialCount.get(), finalCount.get());
            logger.info("║ 端到端总耗时: {}ms（从文件读取到流完成）", t5 - t0);
            logger.info("║ 转录结果: {}", fullTranscript);
            logger.info("╚══════════════════════════════════════════════════╝");

        } catch (Exception e) {
            logger.error("流式转录失败: {}", e.getMessage(), e);
            throw new RuntimeException("流式转录失败: " + e.getMessage(), e);
        }

        return fullTranscript.toString();
    }

    /**
     * 从 WAV 文件头读取采样率（字节 24-27，小端序）
     */
    private int readWavSampleRate(String wavFilePath) {
        try (FileInputStream fis = new FileInputStream(wavFilePath)) {
            byte[] header = new byte[WAV_HEADER_SIZE];
            int read = fis.read(header);
            if (read < WAV_HEADER_SIZE) {
                throw new RuntimeException("WAV 文件头不完整: " + wavFilePath);
            }
            int sampleRate = (header[24] & 0xFF)
                    | ((header[25] & 0xFF) << 8)
                    | ((header[26] & 0xFF) << 16)
                    | ((header[27] & 0xFF) << 24);
            logger.info("从 WAV 文件头读取采样率: {}Hz", sampleRate);
            return sampleRate;
        } catch (IOException e) {
            throw new RuntimeException("无法读取 WAV 文件头: " + wavFilePath, e);
        }
    }

    /**
     * 从 WAV 文件获取 PCM 音频流（跳过文件头）
     */
    private InputStream getStreamFromWavFile(String wavFilePath) {
        try {
            FileInputStream fis = new FileInputStream(wavFilePath);
            long skipped = fis.skip(WAV_HEADER_SIZE);
            if (skipped < WAV_HEADER_SIZE) {
                logger.warn("WAV 文件头跳过不完整，预期 {} 字节，实际 {} 字节", WAV_HEADER_SIZE, skipped);
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

    private static class AudioStreamPublisher implements Publisher<AudioStream> {
        private final InputStream inputStream;
        private final long startTime;
        private final AtomicLong firstChunkSentTime;
        private final AtomicLong lastChunkSentTime;

        AudioStreamPublisher(InputStream inputStream, long startTime,
                             AtomicLong firstChunkSentTime, AtomicLong lastChunkSentTime) {
            this.inputStream = inputStream;
            this.startTime = startTime;
            this.firstChunkSentTime = firstChunkSentTime;
            this.lastChunkSentTime = lastChunkSentTime;
        }

        @Override
        public void subscribe(Subscriber<? super AudioStream> subscriber) {
            subscriber.onSubscribe(new AudioStreamSubscription(
                    subscriber, inputStream, startTime, firstChunkSentTime, lastChunkSentTime));
        }
    }

    private static class AudioStreamSubscription implements Subscription {
        private final Subscriber<? super AudioStream> subscriber;
        private final InputStream inputStream;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final AtomicLong demand = new AtomicLong(0);
        private final AtomicInteger chunkCount = new AtomicInteger(0);
        private final AtomicLong totalBytesSent = new AtomicLong(0);
        private final long startTime;
        private final AtomicLong firstChunkSentTime;
        private final AtomicLong lastChunkSentTime;

        AudioStreamSubscription(Subscriber<? super AudioStream> subscriber, InputStream inputStream,
                                long startTime, AtomicLong firstChunkSentTime, AtomicLong lastChunkSentTime) {
            this.subscriber = subscriber;
            this.inputStream = inputStream;
            this.startTime = startTime;
            this.firstChunkSentTime = firstChunkSentTime;
            this.lastChunkSentTime = lastChunkSentTime;
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
                            long now = System.currentTimeMillis();
                            int bytes = audioBuffer.remaining();
                            int count = chunkCount.incrementAndGet();
                            long total = totalBytesSent.addAndGet(bytes);
                            lastChunkSentTime.set(now);

                            // 记录首块发送时间
                            firstChunkSentTime.compareAndSet(0, now);

                            // 首块、每50块、输出进度
                            if (count == 1 || count % 50 == 0) {
                                logger.info("[发送] 块 #{} | +{}ms | 本块 {}B | 累计 {}KB",
                                        count, now - startTime, bytes, total / 1024);
                            }

                            AudioEvent audioEvent = AudioEvent.builder()
                                    .audioChunk(SdkBytes.fromByteBuffer(audioBuffer))
                                    .build();
                            subscriber.onNext(audioEvent);
                        } else {
                            long now = System.currentTimeMillis();
                            lastChunkSentTime.set(now);
                            logger.info("[发送完毕] 共 {} 块 | {}KB | 耗时 {}ms（从请求发出）",
                                    chunkCount.get(), totalBytesSent.get() / 1024, now - startTime);
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
