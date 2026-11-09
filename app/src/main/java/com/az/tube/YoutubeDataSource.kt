package com.az.tube

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource.HttpDataSourceException
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import androidx.media3.datasource.HttpUtil
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getVisionOsUserAgent
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.isVisionOsStreamingUrl
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.isWebStreamingUrl
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

/**
 * Downloads media from googlevideo.com the way YouTube's own clients do: byte ranges go in a
 * `range` URL parameter and requests are POSTs. A plain GET with a Range header gets throttled.
 * Same approach as NewPipe's YoutubeHttpDataSource.
 */
@UnstableApi
class YoutubeDataSource(private val client: OkHttpClient) : BaseDataSource(true) {

    class Factory(private val client: OkHttpClient) : DataSource.Factory {
        override fun createDataSource(): DataSource = YoutubeDataSource(client)
    }

    private var dataSpec: DataSpec? = null
    private var call: Call? = null
    private var response: Response? = null
    private var input: InputStream? = null
    private var bytesToRead = C.LENGTH_UNSET.toLong()
    private var bytesRead = 0L
    private var opened = false
    private var requestNumber = 0L

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        bytesRead = 0
        bytesToRead = C.LENGTH_UNSET.toLong()
        transferInitializing(dataSpec)

        val uri = dataSpec.uri
        val position = dataSpec.position
        val length = dataSpec.length
        val unset = C.LENGTH_UNSET.toLong()
        val isPlayback = uri.path == "/videoplayback" && uri.query != null

        var url = uri.toString()
        val builder = Request.Builder()
        if (isPlayback) {
            if (!url.contains("&rn=")) url += "&rn=" + requestNumber++
            if (position != 0L || length != unset) {
                val end = if (length != unset) (position + length - 1).toString() else ""
                url += "&range=$position-$end"
            }
            builder.post(POST_BODY.toRequestBody())
        } else {
            HttpUtil.buildRangeRequestHeader(position, length)?.let { builder.header("Range", it) }
        }
        builder.url(url)
        dataSpec.httpRequestHeaders.forEach { (k, v) -> builder.header(k, v) }
        if (isWebStreamingUrl(url)) {
            builder.header("Origin", YOUTUBE)
            builder.header("Referer", "$YOUTUBE/")
            builder.header("Sec-Fetch-Dest", "empty")
            builder.header("Sec-Fetch-Mode", "cors")
            builder.header("Sec-Fetch-Site", "cross-site")
        }
        builder.header(
            "User-Agent",
            if (isVisionOsStreamingUrl(url)) getVisionOsUserAgent(null) else DownloaderImpl.USER_AGENT,
        )

        val newCall = client.newCall(builder.build())
        call = newCall
        val res = try {
            newCall.execute()
        } catch (e: IOException) {
            throw HttpDataSourceException.createForIOException(e, dataSpec, HttpDataSourceException.TYPE_OPEN)
        }
        if (!res.isSuccessful) {
            val headers = res.headers.toMultimap()
            val code = res.code
            val message = res.message
            res.close()
            throw InvalidResponseCodeException(code, message, null, headers, dataSpec, Util.EMPTY_BYTE_ARRAY)
        }
        response = res
        input = res.body.byteStream()

        // A server that ignored our Range header sends everything from byte 0.
        val skip = if (!isPlayback && res.code == 200 && position != 0L) position else 0L
        val contentLength = res.body.contentLength()
        bytesToRead = when {
            length != unset -> length
            contentLength >= 0 -> contentLength - skip
            else -> unset
        }
        opened = true
        transferStarted(dataSpec)
        if (skip > 0) skipFully(skip, dataSpec)
        return bytesToRead
    }

    private fun skipFully(count: Long, spec: DataSpec) {
        var left = count
        val buf = ByteArray(8192)
        while (left > 0) {
            val r = input!!.read(buf, 0, min(left, buf.size.toLong()).toInt())
            if (r == -1) {
                throw HttpDataSourceException(
                    spec,
                    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
                    HttpDataSourceException.TYPE_OPEN,
                )
            }
            left -= r
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        var toRead = length
        if (bytesToRead != C.LENGTH_UNSET.toLong()) {
            val remaining = bytesToRead - bytesRead
            if (remaining == 0L) return C.RESULT_END_OF_INPUT
            toRead = min(toRead.toLong(), remaining).toInt()
        }
        val r = try {
            input!!.read(buffer, offset, toRead)
        } catch (e: IOException) {
            throw HttpDataSourceException.createForIOException(e, dataSpec!!, HttpDataSourceException.TYPE_READ)
        }
        if (r == -1) return C.RESULT_END_OF_INPUT
        bytesRead += r
        bytesTransferred(r)
        return r
    }

    override fun getUri(): Uri? = response?.request?.url?.toString()?.toUri() ?: dataSpec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        response?.headers?.toMultimap() ?: emptyMap()

    override fun close() {
        try {
            call?.cancel()
            input?.close()
            response?.close()
        } catch (_: Exception) {
        } finally {
            input = null
            response = null
            call = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    private companion object {
        const val YOUTUBE = "https://www.youtube.com"
        val POST_BODY = byteArrayOf(0x78, 0)
    }
}
