package com.rou

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayInputStream
import java.util.zip.InflaterInputStream

/**
 * rou.video 把 m3u8 播放列表和 TS 分片都包了一层 PNG 外壳:
 * PNG chunks 里藏一个 type 为 "roUd" 的 chunk, 首字节为 flag,
 * flag & 1 == 1 时剩余字节是 zlib 压缩的数据, 否则是原文.
 * (与站内播放器 JS 的解包逻辑一致)
 *
 * ExoPlayer 无法直接解析, 这里在 OkHttp 层把包拆掉再交给播放器.
 * 非 PNG 壳的响应原样放行.
 */
class RouVideoInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val body = response.body ?: return response
        val bytes = try {
            body.bytes()
        } catch (e: Exception) {
            return response
        }
        val unwrapped = unwrapPng(bytes)
        val outBytes = unwrapped ?: bytes
        val contentType = if (unwrapped != null) {
            "application/octet-stream".toMediaTypeOrNull()
        } else {
            body.contentType()
        }
        return response.newBuilder()
            .body(outBytes.toResponseBody(contentType))
            .build()
    }

    companion object {
        private val PNG_MAGIC = byteArrayOf(
            137.toByte(), 80, 78, 71, 13, 10, 26, 10
        )

        /** 是 PNG 壳就返回拆包后的字节, 否则返回 null */
        fun unwrapPng(data: ByteArray): ByteArray? {
            if (data.size < 8 + 12) return null
            for (i in PNG_MAGIC.indices) {
                if (data[i] != PNG_MAGIC[i]) return null
            }
            var pos = 8
            while (pos + 8 <= data.size) {
                val len = readU32BE(data, pos)
                if (len > (data.size - pos - 12).toLong()) return null // 越界, 非法
                val type = String(data, pos + 4, 4, Charsets.US_ASCII)
                if (type == "roUd") {
                    if (len < 1) return null
                    val flag = data[pos + 8].toInt() and 0xFF
                    val payload = data.copyOfRange(pos + 9, (pos + 8 + len).toInt())
                    return if (flag and 1 != 0) {
                        try {
                            InflaterInputStream(ByteArrayInputStream(payload)).readBytes()
                        } catch (e: Exception) {
                            null
                        }
                    } else {
                        payload
                    }
                }
                if (type == "IEND") break
                pos += (8 + len + 4).toInt()
            }
            return null
        }

        private fun readU32BE(data: ByteArray, pos: Int): Long {
            return ((data[pos].toLong() and 0xFF) shl 24) or
                ((data[pos + 1].toLong() and 0xFF) shl 16) or
                ((data[pos + 2].toLong() and 0xFF) shl 8) or
                (data[pos + 3].toLong() and 0xFF)
        }
    }
}
