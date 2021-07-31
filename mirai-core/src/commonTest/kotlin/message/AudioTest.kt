/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/dev/LICENSE
 */

package net.mamoe.mirai.internal.message

import net.mamoe.mirai.internal.message.OnlineAudioImpl.Companion.DOWNLOAD_URL
import net.mamoe.mirai.internal.message.OnlineAudioImpl.Companion.refineUrl
import net.mamoe.mirai.internal.test.AbstractTest
import net.mamoe.mirai.message.data.Audio
import net.mamoe.mirai.message.data.AudioCodec
import net.mamoe.mirai.message.data.OfflineAudio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

internal class AudioTest : AbstractTest() {

    @Test
    fun `test equality`() {
        assertEquals(
            OnlineAudioImpl("name", byteArrayOf(), 1, AudioCodec.SILK, "url", 2),
            OnlineAudioImpl("name", byteArrayOf(), 1, AudioCodec.SILK, "url", 2)
        )
        assertEquals(
            OfflineAudioImplWithPtt("name", byteArrayOf(), 1, AudioCodec.SILK),
            OfflineAudioImplWithPtt("name", byteArrayOf(), 1, AudioCodec.SILK)
        )
        assertEquals(
            OfflineAudio("name", byteArrayOf(), 1, AudioCodec.SILK),
            OfflineAudio("name", byteArrayOf(), 1, AudioCodec.SILK)
        )
    }

    @Test
    fun `test refineUrl`() {
        assertFalse { DOWNLOAD_URL.endsWith("/") }

        assertEquals("", refineUrl(""))
        assertEquals("$DOWNLOAD_URL/test", refineUrl("/test"))
        assertEquals("$DOWNLOAD_URL/test", refineUrl("test"))
        assertEquals("https://custom.com", refineUrl("https://custom.com"))
        assertEquals("http://localhost", refineUrl("http://localhost"))
    }

    @Test
    fun `AudioCodec mangling test`() {
        Audio::class.java.getMethod("getCodec")
    }
}