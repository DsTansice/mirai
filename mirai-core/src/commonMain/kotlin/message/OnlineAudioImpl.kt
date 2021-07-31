/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/dev/LICENSE
 */

package net.mamoe.mirai.internal.message

import kotlinx.io.core.toByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.mamoe.mirai.internal.message.OnlineAudioImpl.Serializer
import net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody
import net.mamoe.mirai.internal.utils.io.serialization.loadAs
import net.mamoe.mirai.internal.utils.io.serialization.toByteArray
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.*


/**
 * ## Audio Implementation Overview
 *
 * ```
 *                     (api)Audio
 *                          |
 *                    /------------------\
 *         (api)OnlineAudio        (api)OfflineAudio
 *              |                         |
 *              |                  /---------------------|
 * (core)OnlineAudioImpl      (api)OfflineAudioImpl    |
 *                                                      /
 *                         (core)OfflineAudioImplWithPtt
 * ```
 *
 * - [OnlineAudioImpl]: 实现从 [ImMsgBody.Ptt] 解析
 * - `OfflineAudioImpl`: 支持用户手动构造
 * - [OfflineAudioImplWithPtt]: 在 `OfflineAudioImpl` 基础上添加 [AudioPttSupport] 支持
 *
 * ## Equality
 *
 * - [OnlineAudio] != [OfflineAudio]
 * - `OfflineAudioImpl` may == [OfflineAudioImplWithPtt], provided [OfflineAudioImplWithPtt.originalPtt] is `null`.
 *
 * ## Converting [Audio] to [ImMsgBody.Ptt]
 *
 * Always call [Audio.toPtt]
 */
internal interface AudioPttSupport : MessageContent { // Audio is sealed in mirai-core-api
    /**
     * 原协议数据. 用于在接受到其他用户发送的语音时能按照原样发回. 注意, 这不是缓存.
     */
    var originalPtt: ImMsgBody.Ptt?

    /**
     * 序列化缓存
     */
    val serialCache: ComputeOnNullMutableProperty<String>
}

internal fun Audio.toPtt(): ImMsgBody.Ptt {
    if (this is AudioPttSupport) {
        this.originalPtt?.let { return it }
    }
    return ImMsgBody.Ptt(
        fileName = this.filename.toByteArray(),
        fileMd5 = this.fileMd5,
        boolValid = true,
        fileSize = this.fileSize.toInt(),
        fileType = 4,
        pbReserve = byteArrayOf(0),
        format = this.codec.id
    )
}

/**
 * @see Serializer
 */
internal class OnlineAudioImpl private constructor(
    override val filename: String,
    override val fileMd5: ByteArray,
    override val fileSize: Long,
    override val url: String,
    override val codec: AudioCodec,
    override val length: Long,
    @Suppress("UNUSED_PARAMETER") primaryConstructorMarker: Nothing?
) : OnlineAudio, AudioPttSupport,
    @Suppress("DEPRECATION") // compatibility
    net.mamoe.mirai.message.data.Voice(filename, fileMd5, fileSize, codec.id, url) {

    constructor(
        filename: String,
        fileMd5: ByteArray,
        fileSize: Long,
        codec: AudioCodec,
        url: String,
        length: Long,
    ) : this(filename, fileMd5, fileSize, refineUrl(url), codec, length, null)

    override val urlForDownload: String
        get() = url.takeIf { it.isNotBlank() }
            ?: throw UnsupportedOperationException("Could not fetch URL for audio $filename")

    override var originalPtt: ImMsgBody.Ptt? = null
        set(value) {
            field = value
            serialCache.set(null)
        }

    override val serialCache = computeOnNullMutableProperty {
        serializePttElem(originalPtt)
    }

    private val _stringValue: String by lazy { "[mirai:audio:${filename}]" }
    override fun toString(): String = _stringValue
    override fun contentToString(): String = "[语音消息]"

    @Suppress("DuplicatedCode")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as OnlineAudioImpl

        if (filename != other.filename) return false
        if (!fileMd5.contentEquals(other.fileMd5)) return false
        if (fileSize != other.fileSize) return false
        if (url != other.url) return false
        if (codec != other.codec) return false
        if (length != other.length) return false
        if (originalPtt != other.originalPtt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + filename.hashCode()
        result = 31 * result + fileMd5.contentHashCode()
        result = 31 * result + fileSize.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + codec.hashCode()
        result = 31 * result + length.hashCode()
        result = 31 * result + (originalPtt?.hashCode() ?: 0)
        return result
    }


    companion object {
        fun serializePttElem(ptt: ImMsgBody.Ptt?): String {
            ptt ?: return ""
            return ptt.toByteArray(ImMsgBody.Ptt.serializer()).toUHexString("")
        }

        fun deserializePttElem(ptt: String): ImMsgBody.Ptt? {
            if (ptt.isBlank()) return null
            return ptt.hexToBytes().loadAs(ImMsgBody.Ptt.serializer())
        }

        fun serializer(): KSerializer<OnlineAudioImpl> = Serializer


        fun refineUrl(url: String) = when {
            url.isBlank() -> ""
            url.startsWith("http") -> url
            url.startsWith("/") -> "$DOWNLOAD_URL$url"
            else -> "$DOWNLOAD_URL/$url"
        }

        @Suppress("HttpUrlsUsage")
        const val DOWNLOAD_URL = "http://grouptalk.c2c.qq.com"
    }

    private object Serializer : KSerializer<OnlineAudioImpl> by Surrogate.serializer().map(
        resultantDescriptor = Surrogate.serializer().descriptor.copy(OnlineAudio.SERIAL_NAME),
        deserialize = {
            OnlineAudioImpl(
                filename = filename,
                fileMd5 = fileMd5,
                fileSize = fileSize,
                url = url,
                codec = codec,
                length = length,
            ).also { v -> v.originalPtt = deserializePttElem(it.ptt) }
        },
        serialize = {
            Surrogate(
                filename = filename,
                fileMd5 = fileMd5,
                fileSize = fileSize,
                url = url,
                codec = codec,
                length = length,
                ptt = serialCache.get()
            )
        }
    ) {
        @Serializable
        @SerialName(OnlineAudio.SERIAL_NAME)
        private class Surrogate(
            val filename: String,
            val fileMd5: ByteArray,
            val fileSize: Long,
            val url: String,
            val codec: AudioCodec,
            val length: Long,
            val ptt: String = "",
        )
    }
}

@SerialName(OfflineAudio.SERIAL_NAME)
@Serializable(OfflineAudioImplWithPtt.Serializer::class)
internal class OfflineAudioImplWithPtt(
    override val filename: String,
    override val fileMd5: ByteArray,
    override val fileSize: Long,
    override val codec: AudioCodec
) : OfflineAudio, AudioPttSupport {
    override fun toString(): String = "[mirai:audio:${filename}]"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OfflineAudio

        if (filename != other.filename) return false
        if (!fileMd5.contentEquals(other.fileMd5)) return false
        if (fileSize != other.fileSize) return false
        if (codec != other.codec) return false

        if (originalPtt != null) {
            if (other !is AudioPttSupport) return false
            if (other.originalPtt != this.originalPtt) return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = filename.hashCode()
        result = 31 * result + fileMd5.contentHashCode()
        result = 31 * result + fileSize.hashCode()
        result = 31 * result + codec.hashCode()
        return result
    }

    override var originalPtt: ImMsgBody.Ptt? = null
        set(value) {
            field = value
            serialCache.set(null)
        }

    override val serialCache = computeOnNullMutableProperty {
        OnlineAudioImpl.serializePttElem(originalPtt)
    }

    object Serializer : KSerializer<OfflineAudio> by Surrogate.serializer().map(
        resultantDescriptor = Surrogate.serializer().descriptor.copy(OfflineAudio.SERIAL_NAME),
        deserialize = {
            val ptt = OnlineAudioImpl.deserializePttElem(it.ptt)
            if (ptt != null) {
                OfflineAudioImplWithPtt(
                    filename = filename,
                    fileMd5 = fileMd5,
                    fileSize = fileSize,
                    codec = codec,
                ).also { v -> v.originalPtt = OnlineAudioImpl.deserializePttElem(it.ptt) }
            } else {
                OfflineAudio(
                    filename = filename,
                    fileMd5 = fileMd5,
                    fileSize = fileSize,
                    codec = codec,
                )
            }
        },
        serialize = {
            Surrogate(
                filename = filename,
                fileMd5 = fileMd5,
                fileSize = fileSize,
                codec = codec,
                ptt = this.castOrNull<AudioPttSupport>()?.serialCache?.get() ?: ""
            )
        }
    ) {
        @Serializable
        @SerialName(OnlineAudio.SERIAL_NAME)
        private class Surrogate(
            val filename: String,
            val fileMd5: ByteArray,
            val fileSize: Long,
            val codec: AudioCodec,
            val ptt: String = "",
        )
    }
}
