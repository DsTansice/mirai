/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/dev/LICENSE
 */

@file:JvmBlockingBridge
@file:Suppress("NOTHING_TO_INLINE", "unused")

package net.mamoe.mirai.message.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.mamoe.kjbb.JvmBlockingBridge
import net.mamoe.mirai.contact.AudioSupported
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.utils.ExternalResource
import net.mamoe.mirai.utils.NotStableForInheritance
import net.mamoe.mirai.utils.safeCast

/**
 * 语音消息
 *
 * ## 上传和发送语音
 *
 * 使用 [AudioSupported.uploadAudio] 上传语音到服务器并取得 [Audio] 消息实例, 然后通过 [Contact.sendMessage] 发送.
 *
 * ## 下载语音
 *
 * 使用 [OnlineAudio.urlForDownload] 获取文件下载链接.
 *
 * @since 2.7
 */
public sealed interface Audio : MessageContent {
    public companion object Key :
        AbstractPolymorphicMessageKey<MessageContent, Audio>(MessageContent, { it.safeCast() })

    /**
     * 文件名称
     */
    public val filename: String

    /**
     * 文件 MD5
     */
    public val fileMd5: ByteArray

    /**
     * 文件大小 bytes. 服务器支持最大文件大小约为 1MB.
     */
    public val fileSize: Long

    /**
     * 编码方式.
     */
    public val codec: AudioCodec

    /**
     * @return `"[mirai:audio:${filename}]"`
     */
    public override fun toString(): String
    public override fun contentToString(): String = "[语音消息]"
}


/**
 * 在线语音消息, 即从消息事件中接收到的语音消息.
 *
 * [OnlineAudio] 可以获取[语音长度][length]以及[下载链接][urlForDownload].
 *
 * @since 2.7
 */
@NotStableForInheritance
public interface OnlineAudio : Audio { // 协议实现
    /**
     * 下载链接 HTTP URL.
     * @return `"http://xxx"`
     */
    public val urlForDownload: String

    /**
     * 语音长度秒数
     */
    public val length: Long

    public companion object Key :
        AbstractPolymorphicMessageKey<Audio, OnlineAudio>(Audio, { it.safeCast() }) {

        public const val SERIAL_NAME: String = "OnlineAudio"
    }
}

/**
 * 离线语音消息
 *
 * [OnlineAudio] 仅拥有协议上必要的四个属性:
 * - 文件名 [filename]
 * - 文件 MD5 [fileMd5]
 * - 文件大小 [fileSize]
 * - 编码方式 [codec]
 *
 * 由于 [OfflineAudio] 是由本地 [ExternalResource] 经过 [AudioSupported.uploadAudio] 上传到服务器得到的, 故无[下载链接][OnlineAudio.urlForDownload].
 *
 * @since 2.7
 */
@NotStableForInheritance
public interface OfflineAudio : Audio {
    // API 实现

    public companion object Key :
        AbstractPolymorphicMessageKey<Audio, OfflineAudio>(Audio, { it.safeCast() }) {

        /**
         * 构造 [OfflineAudio].
         */
        @JvmStatic
        public fun create(
            filename: String,
            fileMd5: ByteArray,
            fileSize: Long,
            codec: AudioCodec,
        ): OfflineAudio = OfflineAudioImpl(filename, fileMd5, fileSize, codec)

        public const val SERIAL_NAME: String = "OfflineAudio"
    }
}

/**
 * 构造 [OfflineAudio].
 * @since 2.7
 */
public inline fun OfflineAudio(
    filename: String,
    fileMd5: ByteArray,
    fileSize: Long,
    codec: AudioCodec,
): OfflineAudio = OfflineAudio.create(filename, fileMd5, fileSize, codec)


/**
 * 语音编码方式.
 *
 * @since 2.7
 */
@Serializable
public enum class AudioCodec(
    public val id: Int,
    public val formatName: String,
) {
    /**
     * 低音质编码格式
     */
    AMR(0, "amr"),

    /**
     * 高音质编码格式
     */
    SILK(1, "silk");

    public companion object {
        private val VALUES = values()

        @JvmStatic
        public fun fromId(id: Int): AudioCodec = VALUES.first { it.id == id }

        @JvmStatic
        public fun fromFormatName(formatName: String): AudioCodec = VALUES.first { it.formatName == formatName }

        @JvmStatic
        public fun fromIdOrNull(id: Int): AudioCodec? = VALUES.find { it.id == id }

        @JvmStatic
        public fun fromFormatNameOrNull(formatName: String): AudioCodec? =
            VALUES.find { it.formatName == formatName }
    }
}


// 用户手动构造
@SerialName(OfflineAudio.SERIAL_NAME)
@Serializable
internal class OfflineAudioImpl(
    override val filename: String,
    override val fileMd5: ByteArray,
    override val fileSize: Long,
    override val codec: AudioCodec
) : OfflineAudio {
    // see  net.mamoe.mirai.internal.message.OfflineAudioImplWithPtt

    override fun toString(): String = "[mirai:audio:${filename}]"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OfflineAudioImpl

        if (filename != other.filename) return false
        if (!fileMd5.contentEquals(other.fileMd5)) return false
        if (fileSize != other.fileSize) return false
        if (codec != other.codec) return false

        return true
    }

    override fun hashCode(): Int {
        var result = filename.hashCode()
        result = 31 * result + fileMd5.contentHashCode()
        result = 31 * result + fileSize.hashCode()
        result = 31 * result + codec.hashCode()
        return result
    }
}