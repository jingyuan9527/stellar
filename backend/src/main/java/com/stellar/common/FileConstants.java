package com.stellar.common;

import java.util.Set;

/**
 * 文件相关常量：扩展名白名单与图片/音频分组，上传校验与列表过滤共用。
 */
public final class FileConstants {

    /** 图片扩展名白名单 */
    public static final Set<String> IMAGE_EXT = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "ico");

    /** 音频扩展名白名单 */
    public static final Set<String> AUDIO_EXT = Set.of(
            "mp3", "wav", "m4a", "aac", "ogg");

    /** 上传允许的扩展名全集（图片 + 音频） */
    public static final Set<String> ALLOWED_EXT = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "ico",
            "mp3", "wav", "m4a", "aac", "ogg");

    private FileConstants() {
    }
}
