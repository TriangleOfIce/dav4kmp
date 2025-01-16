package io.github.triangleofice.dav4kmp

import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.save
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.http.takeFrom
import io.ktor.util.InternalAPI
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import korlibs.time.DateTime
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job

val HttpClient.lastMockRequest
    get() = (engine as MockEngine).requestHistory.last()

val HttpClient.lastMockResponse
    get() = (engine as MockEngine).responseHistory.last()

fun createMockClient(
    handler: MockRequestHandler = {
        respondError(HttpStatusCode.InternalServerError)
    },
) = HttpClient(MockEngine) {
    engine {
        addHandler(handler)
    }
    install(HttpRedirect) {
        checkHttpMethod = false
    }
}

fun HttpClient.changeMockHandler(handler: MockRequestHandler) {
    if (engine !is MockEngine) error("Only possible with MockEngine")
    val config = (engine as MockEngine).config
    config.requestHandlers.clear()
    config.addHandler(handler)
}

fun Url.resolve(path: String) = URLBuilder(this).apply {
    takeFrom(path)
}.build()

@OptIn(InternalAPI::class)
suspend fun HttpClient.createResponse(
    request: HttpRequestBuilder,
    status: HttpStatusCode,
    headers: Headers = headersOf(),
    body: String? = null,
) = HttpClientCall(
    this,
    request.build(),
    HttpResponseData(
        statusCode = status,
        requestTime = GMTDate(DateTime.nowUnixMillisLong()),
        headers = headers,
        version = HttpProtocolVersion.HTTP_1_1,
        body = body?.let { ByteReadChannel(it) } ?: ByteReadChannel.Empty,
        callContext = Job() + CoroutineName("Fake call response"),
    ),
).save().response

fun buildRequest(block: HttpRequestBuilder.() -> Unit) = HttpRequestBuilder().apply(block)
