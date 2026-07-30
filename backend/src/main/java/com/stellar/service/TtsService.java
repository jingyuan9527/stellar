package com.stellar.service;

import com.stellar.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 语音合成服务，基于 Microsoft Edge 在线 TTS（WebSocket 协议）。
 * <p>
 * 通过 wss://speech.platform.bing.com 的非官方 Edge ReadAloud 接口，
 * 发送 SSML 请求并接收 MP3 音频流。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TtsService {

    private static final String TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4";
    private static final String WSS_URL =
            "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
                    + "?TrustedClientToken=" + TRUSTED_CLIENT_TOKEN;
    private static final String SEC_MS_GEC_VERSION = "1-143.0.3650.75";
    private static final String ORIGIN = "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0";

    /** Windows 文件时间纪元偏移（1601-01-01 与 1970-01-01 之间的秒数） */
    private static final long WIN_EPOCH = 11644473600L;

    /** 单段 SSML 文本的最大字节数（UTF-8 编码后） */
    private static final int MAX_TEXT_BYTES = 4096;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'",
                    Locale.US)
            .withZone(ZoneOffset.UTC);

    private final ExternalCallLogger externalCallLogger;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 合成语音，返回 MP3 字节数组。
     *
     * @param text   合成文本
     * @param voice  发音人，如 zh-CN-XiaoxiaoNeural
     * @param rate   语速 0.5 ~ 2.0，默认 1.0
     * @param pitch  音调 0 ~ 2.0，默认 1.0
     * @param volume 音量 0 ~ 1.0，默认 1.0
     * @return MP3 音频字节
     */
    public byte[] synthesize(String text, String voice, Double rate, Double pitch, Double volume) {
        if (text == null || text.isBlank()) {
            throw new BusinessException("合成文本不能为空");
        }

        String rateStr = formatPercent(rate, 1.0);
        String pitchStr = formatPitch(pitch, 1.0);
        String volumeStr = formatPercent(volume, 1.0);

        String cleaned = removeIncompatibleChars(text);
        String escaped = escapeXml(cleaned);
        List<String> chunks = splitText(escaped);

        log.info("语音合成开始: voice={}, chunks={}, totalChars={}",
                voice, chunks.size(), text.length());

        long start = System.currentTimeMillis();
        String callParams = "voice=" + voice + ", chunks=" + chunks.size()
                + ", totalChars=" + text.length() + ", rate=" + rateStr
                + ", pitch=" + pitchStr + ", volume=" + volumeStr;
        try {
            ByteArrayOutputStream audio = new ByteArrayOutputStream();
            for (int i = 0; i < chunks.size(); i++) {
                log.debug("合成第 {}/{} 段", i + 1, chunks.size());
                byte[] chunkAudio = synthesizeChunk(chunks.get(i), voice, rateStr, pitchStr, volumeStr);
                audio.write(chunkAudio, 0, chunkAudio.length);
            }

            if (audio.size() == 0) {
                throw new BusinessException("语音合成失败：未收到音频数据");
            }

            externalCallLogger.success("Edge TTS", WSS_URL, callParams + ", resultBytes=" + audio.size(),
                    System.currentTimeMillis() - start);
            log.info("语音合成完成: {} bytes", audio.size());
            return audio.toByteArray();
        } catch (Exception e) {
            externalCallLogger.failure("Edge TTS", WSS_URL, callParams, e.getMessage(),
                    System.currentTimeMillis() - start);
            throw e;
        }
    }

    /**
     * 合成单段文本，通过 WebSocket 连接 Edge TTS 服务。
     */
    private byte[] synthesizeChunk(String escapedText, String voice,
                                   String rate, String pitch, String volume) {
        String secMsGec = generateSecMsGec();
        String connectionId = UUID.randomUUID().toString().replace("-", "");
        String url = WSS_URL
                + "&ConnectionId=" + connectionId
                + "&Sec-MS-GEC=" + secMsGec
                + "&Sec-MS-GEC-Version=" + SEC_MS_GEC_VERSION;

        String ssml = buildSsml(escapedText, voice, rate, pitch, volume);
        String date = formatDate();
        String configMsg = buildConfigMessage(date);
        String ssmlMsg = buildSsmlMessage(date, ssml);

        CompletableFuture<byte[]> audioFuture = new CompletableFuture<>();
        String muid = generateMuid();

        httpClient.newWebSocketBuilder()
                .header("Origin", ORIGIN)
                .header("User-Agent", USER_AGENT)
                .header("Pragma", "no-cache")
                .header("Cache-Control", "no-cache")
                .header("Cookie", "muid=" + muid + ";")
                .buildAsync(URI.create(url), new EdgeTtsListener(audioFuture, configMsg, ssmlMsg))
                .exceptionally(ex -> {
                    audioFuture.completeExceptionally(
                            new BusinessException("语音合成服务连接失败: " + ex.getMessage()));
                    return null;
                });

        try {
            return audioFuture.get(30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new BusinessException("语音合成超时，请稍后重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("语音合成被中断");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException("语音合成失败: "
                    + (cause != null ? cause.getMessage() : "未知错误"));
        }
    }

    // ======================== Edge TTS 协议 ========================

    /**
     * 生成 Sec-MS-GEC 令牌。
     * <p>
     * 算法：取当前 Unix 时间戳，加上 Windows 纪元偏移，向下取整到 5 分钟，
     * 转换为 100 纳秒单位（×10^7），与 TrustedClientToken 拼接后做 SHA-256，
     * 返回大写十六进制摘要。
     */
    private String generateSecMsGec() {
        long unixTime = Instant.now().getEpochSecond();
        long winTime = unixTime + WIN_EPOCH;
        winTime -= winTime % 300;
        long ticks = winTime * 10_000_000L;
        String strToHash = ticks + TRUSTED_CLIENT_TOKEN;
        return sha256Hex(strToHash);
    }

    /**
     * 计算 SHA-256，返回大写十六进制字符串。
     */
    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().withUpperCase().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException("SHA-256 算法不可用");
        }
    }

    /**
     * 生成随机 MUID（32 位大写十六进制）。
     */
    private String generateMuid() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return HexFormat.of().withUpperCase().formatHex(bytes);
    }

    /**
     * 构建 SSML 文档。
     */
    private String buildSsml(String escapedText, String voice,
                             String rate, String pitch, String volume) {
        return "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>"
                + "<voice name='" + voice + "'>"
                + "<prosody pitch='" + pitch + "' rate='" + rate + "' volume='" + volume + "'>"
                + escapedText
                + "</prosody>"
                + "</voice>"
                + "</speak>";
    }

    /**
     * 格式化当前时间为 Edge TTS 要求的日期字符串。
     */
    private String formatDate() {
        return DATE_FORMATTER.format(Instant.now());
    }

    /**
     * 构建 speech.config 配置消息。
     */
    private String buildConfigMessage(String date) {
        return "X-Timestamp:" + date + "\r\n"
                + "Content-Type:application/json; charset=utf-8\r\n"
                + "Path:speech.config\r\n\r\n"
                + "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{"
                + "\"sentenceBoundaryEnabled\":\"true\",\"wordBoundaryEnabled\":\"false\""
                + "},"
                + "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\""
                + "}}}}\r\n";
    }

    /**
     * 构建 SSML 请求消息。
     */
    private String buildSsmlMessage(String date, String ssml) {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        return "X-RequestId:" + requestId + "\r\n"
                + "Content-Type:application/ssml+xml\r\n"
                + "X-Timestamp:" + date + "Z\r\n"
                + "Path:ssml\r\n\r\n"
                + ssml;
    }

    // ======================== 文本处理 ========================

    /**
     * XML 转义：仅转义 &、<、>（与 Python xml.sax.saxutils.escape 一致）。
     */
    private String escapeXml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * 移除服务不支持的字符（控制字符 0-8、11-12、14-31），替换为空格。
     */
    private String removeIncompatibleChars(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 0 && c <= 8) || (c >= 11 && c <= 12) || (c >= 14 && c <= 31)) {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 按字节长度拆分文本，每段 UTF-8 编码后不超过 MAX_TEXT_BYTES 字节。
     * 优先在句末标点（。！？!?\n）处拆分。
     */
    private List<String> splitText(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= MAX_TEXT_BYTES) {
            return List.of(text);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + 1300, text.length());
            if (end < text.length()) {
                for (int i = end; i > start; i--) {
                    char c = text.charAt(i - 1);
                    if (c == '\n' || c == '。' || c == '！' || c == '？'
                            || c == '!' || c == '?') {
                        end = i;
                        break;
                    }
                }
            }
            String chunk = text.substring(start, end);
            while (chunk.getBytes(StandardCharsets.UTF_8).length > MAX_TEXT_BYTES
                    && end > start + 1) {
                end = start + (end - start) / 2;
                chunk = text.substring(start, end);
            }
            chunks.add(chunk);
            start = end;
        }
        return chunks;
    }

    /**
     * 将滑块值（0.5~2.0，默认 1.0）转换为百分比字符串，如 +50%、-50%。
     */
    private String formatPercent(Double value, double defaultValue) {
        double v = (value != null ? value : defaultValue);
        int percent = (int) Math.round((v - 1.0) * 100);
        return String.format("%+d%%", percent);
    }

    /**
     * 将音调滑块值（0~2.0，默认 1.0）转换为 Hz 偏移字符串，如 +50Hz、-100Hz。
     */
    private String formatPitch(Double value, double defaultValue) {
        double v = (value != null ? value : defaultValue);
        int percent = (int) Math.round((v - 1.0) * 100);
        return String.format("%+dHz", percent);
    }

    // ======================== WebSocket 监听器 ========================

    /**
     * Edge TTS WebSocket 监听器，负责发送配置/SSML 消息并收集音频数据。
     */
    private static class EdgeTtsListener implements WebSocket.Listener {

        private final CompletableFuture<byte[]> future;
        private final String configMsg;
        private final String ssmlMsg;
        private final ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
        private final StringBuilder textBuffer = new StringBuilder();
        private final ByteArrayOutputStream binaryBuffer = new ByteArrayOutputStream();

        EdgeTtsListener(CompletableFuture<byte[]> future, String configMsg, String ssmlMsg) {
            this.future = future;
            this.configMsg = configMsg;
            this.ssmlMsg = ssmlMsg;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.sendText(configMsg, true);
            webSocket.sendText(ssmlMsg, true);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            binaryBuffer.write(bytes, 0, bytes.length);

            if (last) {
                byte[] message = binaryBuffer.toByteArray();
                binaryBuffer.reset();
                processBinaryMessage(message);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String message = textBuffer.toString();
                textBuffer.setLength(0);
                if (message.contains("Path:turn.end")) {
                    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "ok");
                    future.complete(audioBuffer.toByteArray());
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            if (!future.isDone()) {
                future.completeExceptionally(
                        new BusinessException("语音合成失败: " + error.getMessage()));
            }
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (!future.isDone()) {
                if (audioBuffer.size() > 0) {
                    future.complete(audioBuffer.toByteArray());
                } else {
                    future.completeExceptionally(
                            new BusinessException("语音合成失败: 连接已关闭 (" + statusCode + ")"));
                }
            }
            return null;
        }

        /**
         * 解析二进制消息，提取音频数据。
         * <p>
         * 二进制消息格式：[2字节大端头长度][头文本][音频数据]。
         * 头文本包含 "Path:audio" 的消息为音频数据。
         */
        private void processBinaryMessage(byte[] message) {
            if (message.length < 2) {
                return;
            }
            int headerLen = ((message[0] & 0xFF) << 8) | (message[1] & 0xFF);
            if (headerLen + 2 > message.length) {
                return;
            }

            int headerEnd = Math.min(headerLen, message.length - 2);
            String header = new String(message, 2, headerEnd, StandardCharsets.US_ASCII);

            if (header.contains("Path:audio")) {
                int audioStart = headerLen + 2;
                if (audioStart < message.length) {
                    audioBuffer.write(message, audioStart, message.length - audioStart);
                }
            }
        }
    }
}
