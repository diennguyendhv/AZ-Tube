package com.az.tube

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

/** Shared HTTP client used for YouTube pages and the player. */
object Net {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}

/** Lets NewPipeExtractor talk to YouTube through OkHttp. */
object DownloaderImpl : Downloader() {
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:153.0) Gecko/20100101 Firefox/153.0"

    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody()
        val builder = okhttp3.Request.Builder()
            .method(request.httpMethod(), body)
            .url(request.url())
            .header("User-Agent", USER_AGENT)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        Net.client.newCall(builder.build()).execute().use { res ->
            if (res.code == 429) {
                throw ReCaptchaException("YouTube yêu cầu xác minh (quá nhiều yêu cầu)", request.url())
            }
            return Response(
                res.code,
                res.message,
                res.headers.toMultimap(),
                res.body.string(),
                res.request.url.toString(),
            )
        }
    }
}
