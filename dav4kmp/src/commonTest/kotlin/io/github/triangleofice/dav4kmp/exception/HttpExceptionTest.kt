/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package io.github.triangleofice.dav4kmp.exception

import io.github.triangleofice.dav4kmp.buildRequest
import io.github.triangleofice.dav4kmp.createMockClient
import io.github.triangleofice.dav4kmp.createResponse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.content.ByteArrayContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.core.toByteArray

object HttpExceptionTest : FunSpec({

    val responseMessage = "Unknown error"

    test("testHttpFormatting") {
        val request = buildRequest {
            method = HttpMethod.Post
            url("http://example.com")
            header(HttpHeaders.ContentType, "text/something")
            setBody(ByteArrayContent("REQUEST\nBODY".toByteArray()))
        }

        val response = createMockClient().createResponse(
            request,
            HttpStatusCode.InternalServerError.description(responseMessage),
            headersOf(HttpHeaders.ContentType, "text/something-other"),
            "SERVER\r\nRESPONSE",
        )
        val e = HttpException(response)
        e.message.shouldContain("500")
        e.message.shouldContain(responseMessage)
        e.requestBody.shouldContain("REQUEST\nBODY")
        e.responseBody.shouldContain("SERVER\r\nRESPONSE")
    }
})
