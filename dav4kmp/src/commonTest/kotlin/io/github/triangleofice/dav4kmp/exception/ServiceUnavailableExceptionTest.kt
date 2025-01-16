/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package io.github.triangleofice.dav4kmp.exception

import io.github.triangleofice.dav4kmp.HttpUtils
import io.github.triangleofice.dav4kmp.buildRequest
import io.github.triangleofice.dav4kmp.createMockClient
import io.github.triangleofice.dav4kmp.createResponse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeLessThan
import io.ktor.client.request.url
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import korlibs.time.DateTime
import korlibs.time.DateTimeTz
import korlibs.time.minutes
import kotlin.math.abs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

object ServiceUnavailableExceptionTest : FunSpec({

    val httpClient = createMockClient()

    suspend fun buildResponse(headers: Headers = headersOf()) = httpClient.createResponse(
        buildRequest {
            url("http://www.example.com")
            method = HttpMethod.Get
        },
        HttpStatusCode.ServiceUnavailable.description("Try later"),
        headers,
    )

    fun withinTimeRange(d: DateTimeTz, seconds: Int) {
        val msCheck = d.utc.unixMillisLong
        val msShouldBe = DateTime.nowUnixMillisLong() + seconds * 1000
        // assume max. 5 seconds difference for test running
        abs(msCheck - msShouldBe).shouldBeLessThan(5000)
    }

    test("testRetryAfter_NoTime") {
        val e = ServiceUnavailableException(buildResponse())
        assertNull(e.retryAfter)
    }

    test("testRetryAfter_Seconds") {
        val response = buildResponse(headersOf(HttpHeaders.RetryAfter, "120"))
        val e = ServiceUnavailableException(response)
        assertNotNull(e.retryAfter)
        withinTimeRange(e.retryAfter!!, 120)
    }

    test("testRetryAfter_Date") {
        val t = (DateTime.now() + 30.minutes)
        val response = buildResponse(headersOf(HttpHeaders.RetryAfter, HttpUtils.formatDate(t)))
        val e = ServiceUnavailableException(response)
        assertNotNull(e.retryAfter)
        withinTimeRange(e.retryAfter!!, 30 * 60)
    }
})
