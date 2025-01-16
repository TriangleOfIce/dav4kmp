/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package io.github.triangleofice.dav4kmp

import io.github.triangleofice.dav4kmp.Response
import io.github.triangleofice.dav4kmp.XmlUtils.insertTag
import io.github.triangleofice.dav4kmp.exception.ConflictException
import io.github.triangleofice.dav4kmp.exception.DavException
import io.github.triangleofice.dav4kmp.exception.ForbiddenException
import io.github.triangleofice.dav4kmp.exception.HttpException
import io.github.triangleofice.dav4kmp.exception.NotFoundException
import io.github.triangleofice.dav4kmp.exception.PreconditionFailedException
import io.github.triangleofice.dav4kmp.exception.ServiceUnavailableException
import io.github.triangleofice.dav4kmp.exception.UnauthorizedException
import io.github.triangleofice.dav4kmp.property.SyncToken
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.http.withCharset
import io.ktor.util.InternalAPI
import io.ktor.util.logging.Logger
import io.ktor.utils.io.bits.loadByteArray
import io.ktor.utils.io.bits.withMemory
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.EOFException
import io.ktor.utils.io.core.toByteArray
import io.ktor.utils.io.errors.IOException
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmOverloads
import io.github.triangleofice.dav4kmp.Response as DavResponse

/**
 * Represents a WebDAV resource at the given location and allows WebDAV
 * requests to be performed on this resource.
 *
 * Requests are executed synchronously (blocking). If no error occurs, the given
 * callback will be called. Otherwise, an exception is thrown. *These callbacks
 * don't need to close the response.*
 *
 * To cancel a request, interrupt the thread. This will cause the requests to
 * throw `InterruptedException` or `InterruptedIOException`.
 *
 * @param httpClient    [OkHttpClient] to access this object (must not follow redirects)
 * @param location      location of the WebDAV resource
 * @param log           will be used for logging
 */
open class DavResource @JvmOverloads constructor(
    val httpClient: HttpClient,
    location: Url,
    val log: Logger = Dav4jvm.log,
) {

    companion object {
        const val MAX_REDIRECTS = 5

        const val HTTP_MULTISTATUS = 207
        val MIME_XML = ContentType.Application.Xml.withCharset(Charsets.UTF_8)

        val PROPFIND = QName(XmlUtils.NS_WEBDAV, "propfind")
        val PROPERTYUPDATE = QName(XmlUtils.NS_WEBDAV, "propertyupdate")
        val SET = QName(XmlUtils.NS_WEBDAV, "set")
        val REMOVE = QName(XmlUtils.NS_WEBDAV, "remove")
        val PROP = QName(XmlUtils.NS_WEBDAV, "prop")
        val HREF = QName(XmlUtils.NS_WEBDAV, "href")

        val XML_SIGNATURE = "<?xml".toByteArray()

        // HTTP Methods
        val Move = HttpMethod("MOVE")
        val Copy = HttpMethod("COPY")
        val MKCol = HttpMethod("MKCOL")
        val Propfind = HttpMethod("PROPFIND")
        val Proppatch = HttpMethod("PROPPATCH")
        val Search = HttpMethod("SEARCH")
        val Report = HttpMethod("REPORT")

        /**
         * Creates a request body for the PROPPATCH request.
         */
        internal fun createProppatchXml(
            setProperties: Map<QName, String>,
            removeProperties: List<QName>,
        ): String {
            // build XML request body
            val writer = StringBuilder()
            val serializer = XmlUtils.createWriter(writer)
            serializer.startDocument(encoding = "UTF-8")
            serializer.setPrefix("d", XmlUtils.NS_WEBDAV)
            serializer.insertTag(PROPERTYUPDATE) {
                namespaceAttr("d", XmlUtils.NS_WEBDAV) // Fixes an issue where the namespace is not present
                // DAV:set
                if (setProperties.isNotEmpty()) {
                    serializer.insertTag(SET) {
                        for (prop in setProperties) {
                            serializer.insertTag(PROP) {
                                serializer.insertTag(prop.key) {
                                    text(prop.value)
                                }
                            }
                        }
                    }
                }

                // DAV:remove
                if (removeProperties.isNotEmpty()) {
                    serializer.insertTag(REMOVE) {
                        for (prop in removeProperties) {
                            insertTag(PROP) {
                                insertTag(prop)
                            }
                        }
                    }
                }
            }

            serializer.endDocument()
            return writer.toString()
        }
    }

    /**
     * URL of this resource (changes when being redirected by server)
     */
    var location: Url
        private set // allow internal modification only (for redirects)

    init {
        this.location = location
        // Let the client follow redirects while we listen for changes
        require(httpClient.pluginOrNull(HttpRedirect) != null) { "httpClient must follow redirects automatically!" }
        httpClient.monitor.subscribe(HttpRedirect.HttpResponseRedirect) { response ->
            if (response.request.url != this.location || response.headers[HttpHeaders.Location] == null) return@subscribe
            this.location = URLBuilder(this.location).takeFrom(response.headers[HttpHeaders.Location]!!).build()
        }
    }

    override fun toString() = location.toString()

    /**
     * Gets the file name of this resource. See [HttpUtils.fileName] for details.
     */
    fun fileName() = HttpUtils.fileName(location)

    /**
     * Sends an OPTIONS request to this resource without HTTP compression (because some servers have
     * broken compression for OPTIONS). Doesn't follow redirects.
     *
     * @param callback called with server response unless an exception is thrown
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on HTTPS -> HTTP redirect
     */
    @Throws(IOException::class, HttpException::class, CancellationException::class)
    suspend fun options(callback: CapabilitiesCallback) {
        val response = httpClient.prepareRequest {
            method = HttpMethod.Options
            header("Content-Length", "0")
            url(location)
            header("Accept-Encoding", "identity") // disable compression
        }.execute()
        checkStatus(response)
        callback.onCapabilities(
            HttpUtils.listHeader(response, "DAV").map { it.trim() }.toSet(),
            response,
        )
    }

    /**
     * Sends a MOVE request to this resource. Follows up to [MAX_REDIRECTS] redirects.
     * Updates [location] on success.
     *
     * @param destination where the resource shall be moved to
     * @param forceOverride whether resources are overwritten when they already exist in destination
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error or HTTPS -> HTTP redirect
     */
    @Throws(IOException::class, HttpException::class, DavException::class, CancellationException::class)
    suspend fun move(destination: Url, forceOverride: Boolean, callback: ResponseCallback) {
        // TODO emulate followRedirects
        val response = httpClient.prepareRequest {
            method = Move
            header("Content-Length", "0")
            header("Destination", destination.toString())
            if (forceOverride) header("Overwrite", "F")
            url(location)
        }.execute()

        checkStatus(response)
        if (response.status == HttpStatusCode.MultiStatus) {
                /* Multiple resources were to be affected by the MOVE, but errors on some
        of them prevented the operation from taking place.
        [_] (RFC 4918 9.9.4. Status Codes for MOVE Method) */
            throw HttpException(response)
        }

        // update location
        val nPath = response.headers[HttpHeaders.Location] ?: destination.toString()
        location = URLBuilder(location).takeFrom(nPath).build()

        callback.onResponse(response)
    }

    /**
     * Sends a COPY request for this resource. Follows up to [MAX_REDIRECTS] redirects.
     *
     * @param destination where the resource shall be copied to
     * @param forceOverride whether resources are overwritten when they already exist in destination
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error or HTTPS -> HTTP redirect
     */
    @Throws(IOException::class, HttpException::class, DavException::class, CancellationException::class)
    suspend fun copy(destination: Url, forceOverride: Boolean, callback: ResponseCallback) {
        // TODO followRedirects
        val response = httpClient.prepareRequest {
            method = Copy
            header("Content-Length", "0")
            header("Destination", destination.toString())
            if (forceOverride) header("Overwrite", "F")
            url(location)
        }.execute()

        checkStatus(response)

        if (response.status == HttpStatusCode.MultiStatus) {
                /* Multiple resources were to be affected by the COPY, but errors on some
        of them prevented the operation from taking place.
        [_] (RFC 4918 9.8.5. Status Codes for COPY Method) */
            throw HttpException(response)
        }

        callback.onResponse(response)
    }

    /**
     * Sends a MKCOL request to this resource. Follows up to [MAX_REDIRECTS] redirects.
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on HTTPS -> HTTP redirect
     */
    @Throws(IOException::class, HttpException::class, CancellationException::class)
    suspend fun mkCol(xmlBody: String?, callback: ResponseCallback) {
        // TODO followRedirects {
        val response = httpClient.prepareRequest {
            method = MKCol
            setBody(xmlBody)
            header(HttpHeaders.ContentType, MIME_XML)
            url(location)
        }.execute()
        checkStatus(response)
        callback.onResponse(response)
    }

    /**
     * Sends a HEAD request to the resource.
     *
     * Follows up to [MAX_REDIRECTS] redirects.
     *
     * @param callback called with server response unless an exception is thrown
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on HTTPS -> HTTP redirect
     */
    suspend fun head(callback: ResponseCallback) {
        // TODO followRedirects {
        val response = httpClient.prepareRequest {
            method = HttpMethod.Head
            url(location)
        }.execute()

        checkStatus(response)
        callback.onResponse(response)
    }

    /**
     * Sends a GET request to the resource. Sends `Accept-Encoding: identity` to disable
     * compression, because compression might change the ETag.
     *
     * Follows up to [MAX_REDIRECTS] redirects.
     *
     * @param accept   value of `Accept` header (always sent for clarity; use *&#47;* if you don't care)
     * @param callback called with server response unless an exception is thrown
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on HTTPS -> HTTP redirect
     */
    @Deprecated("Use get(accept, headers, callback) with explicit Accept-Encoding instead")
    @Throws(IOException::class, HttpException::class, CancellationException::class)
    suspend fun get(accept: String, callback: ResponseCallback) =
        get(accept, Headers.build { append(HttpHeaders.AcceptEncoding, "identity") }, callback)

    /**
     * Sends a GET request to the resource. Follows up to [MAX_REDIRECTS] redirects.
     *
     * Note: Add `Accept-Encoding: identity` to [headers] if you want to disable compression
     * (compression might change the returned ETag).
     *
     * @param accept   value of `Accept` header (always sent for clarity; use *&#47;* if you don't care)
     * @param headers  additional headers to send with the request
     * @param callback called with server response unless an exception is thrown
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on HTTPS -> HTTP redirect
     */
    suspend fun get(accept: String, headers: Headers?, callback: ResponseCallback) {
        // TODO followRedirects {
        val response = httpClient.prepareRequest {
            method = HttpMethod.Get
            url(location)
            if (headers != null) {
                this.headers.appendAll(headers)
            }
            header(HttpHeaders.Accept, accept)
        }.execute()

        checkStatus(response)
        callback.onResponse(response)
    }

    /**
     * Sends a GET request to the resource for a specific byte range. Make sure to check the
     * response code: servers may return the whole resource with 200 or partials with 206.
     *
     * Follows up to [MAX_REDIRECTS] redirects.
     *
     * @param accept   value of `Accept` header (always sent for clarity; use *&#47;* if you don't care)
     * @param offset   zero-based index of first byte to request
     * @param size     number of bytes to request
     * @param headers  additional headers to send with the request
     * @param callback called with server response unless an exception is thrown
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on high-level errors
     */
    @Throws(IOException::class, HttpException::class, CancellationException::class)
    suspend fun getRange(
        accept: String,
        offset: Long,
        size: Int,
        headers: Headers? = null,
        callback: ResponseCallback,
    ) {
        // TODO followRedirects {
        val response = httpClient.prepareRequest {
            method = HttpMethod.Get
            url(location)
            if (headers != null) {
                this.headers.appendAll(headers)
            }
            val lastIndex = offset + size - 1
            header(HttpHeaders.Accept, accept)
            header(HttpHeaders.Range, "bytes=$offset-$lastIndex")
        }.execute()

        checkStatus(response)
        callback.onResponse(response)
    }

    /**
     * Sends a PUT request to the resource. Follows up to [MAX_REDIRECTS] redirects.
     *
     * When the server returns an ETag, it is stored in response properties.
     *
     * @param body          new resource body to upload
     * @param ifETag        value of `If-Match` header to set, or null to omit
     * @param ifScheduleTag value of `If-Schedule-Tag-Match` header to set, or null to omit
     * @param ifNoneMatch   indicates whether `If-None-Match: *` ("don't overwrite anything existing") header shall be sent
     * @param callback      called with server response unless an exception is thrown
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on HTTPS -> HTTP redirect
     */
    @Throws(IOException::class, HttpException::class, CancellationException::class)
    suspend fun put(
        body: Any,
        contentType: ContentType,
        ifETag: String? = null,
        ifScheduleTag: String? = null,
        ifNoneMatch: Boolean = false,
        callback: ResponseCallback,
    ) {
        // TODO followRedirects {
        val response = httpClient.prepareRequest {
            method = HttpMethod.Put
            header(HttpHeaders.ContentType, contentType)
            setBody(body)
            url(location)
            if (ifETag != null) {
                // only overwrite specific version
                header(HttpHeaders.IfMatch, QuotedStringUtils.asQuotedString(ifETag))
            }
            if (ifScheduleTag != null) {
                // only overwrite specific version
                header(
                    HttpHeaders.IfScheduleTagMatch,
                    QuotedStringUtils.asQuotedString(ifScheduleTag),
                )
            }
            if (ifNoneMatch) {
                // don't overwrite anything existing
                header(HttpHeaders.IfNoneMatch, "*")
            }
        }.execute()

        checkStatus(response)
        callback.onResponse(response)
    }

    /**
     * Sends a DELETE request to the resource. Warning: Sending this request to a collection will
     * delete the collection with all its contents!
     *
     * Follows up to [MAX_REDIRECTS] redirects.
     *
     * @param ifETag        value of `If-Match` header to set, or null to omit
     * @param ifScheduleTag value of `If-Schedule-Tag-Match` header to set, or null to omit
     * @param callback      called with server response unless an exception is thrown
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP errors, or when 207 Multi-Status is returned
     *         (because then there was probably a problem with a member resource)
     * @throws DavException on HTTPS -> HTTP redirect
     */
    @Throws(IOException::class, HttpException::class, CancellationException::class)
    suspend fun delete(ifETag: String? = null, ifScheduleTag: String? = null, callback: ResponseCallback) {
        // TODO followRedirects {
        val response = httpClient.prepareRequest {
            method = HttpMethod.Delete
            url(location)
            if (ifETag != null) {
                header("If-Match", QuotedStringUtils.asQuotedString(ifETag))
            }
            if (ifScheduleTag != null) {
                header("If-Schedule-Tag-Match", QuotedStringUtils.asQuotedString(ifScheduleTag))
            }
        }.execute()

        checkStatus(response)

        if (response.status == HttpStatusCode.MultiStatus) {
                /* If an error occurs deleting a member resource (a resource other than
           the resource identified in the Request-URI), then the response can be
           a 207 (Multi-Status). […] (RFC 4918 9.6.1. DELETE for Collections) */
            throw HttpException(response)
        }

        callback.onResponse(response)
    }

    /**
     * Sends a PROPFIND request to the resource. Expects and processes a 207 Multi-Status response.
     *
     * Follows up to [MAX_REDIRECTS] redirects.
     *
     * @param depth    "Depth" header to send (-1 for `infinity`)
     * @param reqProp  properties to request
     * @param callback called for every XML response element in the Multi-Status response
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error (like no 207 Multi-Status response) or HTTPS -> HTTP redirect
     */
    @Throws(IOException::class, HttpException::class, DavException::class, CancellationException::class)
    suspend fun propfind(depth: Int, vararg reqProp: QName, callback: MultiResponseCallback) {
        // build XML request body
        val writer = StringBuilder()
        val serializer = XmlUtils.createWriter(writer)
        serializer.setPrefix("", XmlUtils.NS_WEBDAV)
        serializer.setPrefix("CAL", XmlUtils.NS_CALDAV)
        serializer.setPrefix("CARD", XmlUtils.NS_CARDDAV)
        serializer.startDocument(encoding = "UTF-8")
        serializer.insertTag(PROPFIND) {
            insertTag(PROP) {
                for (prop in reqProp)
                    insertTag(prop)
            }
        }
        serializer.endDocument()

        // TODO followRedirects {
        val response = httpClient.prepareRequest {
            url(location)
            method = Propfind
            setBody(writer.toString())
            header(HttpHeaders.ContentType, MIME_XML)
            header("Depth", if (depth >= 0) depth.toString() else "infinity")
        }.execute()
        processMultiStatus(response, callback)
    }

    /**
     * Sends a PROPPATCH request to the server in order to set and remove properties.
     *
     * @param setProperties     map of properties that shall be set (values currently have to be strings)
     * @param removeProperties  list of names of properties that shall be removed
     * @param callback  called for every XML response element in the Multi-Status response
     *
     * Follows up to [MAX_REDIRECTS] redirects.
     *
     * Currently expects a 207 Multi-Status response although servers are allowed to
     * return other values, too.
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error (like no 207 Multi-Status response) or HTTPS -> HTTP redirect
     */
    suspend fun proppatch(
        setProperties: Map<QName, String>,
        removeProperties: List<QName>,
        callback: MultiResponseCallback,
    ) {
        // TODO followRedirects {
        val rqBody = createProppatchXml(setProperties, removeProperties)

        val response = httpClient.prepareRequest {
            url(location)
            method = Proppatch
            setBody(rqBody)
            header(HttpHeaders.ContentType, MIME_XML)
        }.execute()
        // TODO handle not only 207 Multi-Status
        // http://www.webdav.org/specs/rfc4918.html#PROPPATCH-status

        processMultiStatus(response, callback)
    }

    /**
     * Sends a SEARCH request (RFC 5323) with the given body to the server.
     *
     * Follows up to [MAX_REDIRECTS] redirects. Expects a 207 Multi-Status response.
     *
     * @param search    search request body (XML format, DAV:searchrequest or DAV:query-schema-discovery)
     * @param callback  called for every XML response element in the Multi-Status response
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error (like no 207 Multi-Status response) or HTTPS -> HTTP redirect
     */
    suspend fun search(search: String, callback: MultiResponseCallback) {
        // /TODO followRedirects {
        val response = httpClient.prepareRequest {
            url(location)
            method = Search
            setBody(search)
            header(HttpHeaders.ContentType, MIME_XML)
        }.execute()
        processMultiStatus(response, callback)
    }

    // status handling

    /**
     * Checks the status from an HTTP response and throws an exception in case of an error.
     *
     * @throws HttpException (with XML error names, if available) in case of an HTTP error
     */
    protected suspend fun checkStatus(response: HttpResponse) {
        val status = response.status
        if (status.isSuccess()) {
            // everything OK
            return
        }

        throw when (status) {
            HttpStatusCode.Unauthorized -> UnauthorizedException(response)

            HttpStatusCode.Forbidden -> ForbiddenException(response)

            HttpStatusCode.NotFound -> NotFoundException(response)

            HttpStatusCode.Conflict -> ConflictException(response)

            HttpStatusCode.PreconditionFailed -> PreconditionFailedException(response)

            HttpStatusCode.ServiceUnavailable -> ServiceUnavailableException(response)

            else -> HttpException(response)
        }
    }

    /**
     * Validates a 207 Multi-Status response.
     *
     * @param response will be checked for Multi-Status response
     *
     * @throws DavException if the response is not a Multi-Status response
     */
    @OptIn(InternalAPI::class)
    suspend fun assertMultiStatus(response: HttpResponse) {
        if (response.status != HttpStatusCode.MultiStatus) {
            throw DavException(
                "Expected 207 Multi-Status, got ${response.status}",
                httpResponse = response,
            )
        }
        val bodyChannel = response.content
        log.trace("AssertMultiStatus: Checking if content is available ${response.contentLength()}->${bodyChannel.isClosedForRead}")
        if (response.contentLength() == 0L || bodyChannel.isEmpty()) {
            throw DavException(
                "Got 207 Multi-Status without content!",
                httpResponse = response,
            )
        }
        val contentType = response.contentType()
        contentType?.let { mimeType ->
            if (!(mimeType.match(ContentType.Application.Xml) || mimeType.match(ContentType.Text.Xml))) {
                /* Content-Type is not application/xml or text/xml although that is expected here.
                   Some broken servers return an XML response with some other MIME type. So we try to see
                   whether the response is maybe XML although the Content-Type is something else. */
                try {
                    val firstBytes = ByteArray(XML_SIGNATURE.size)
                    log.trace("AssertMultiStatus: Malformed contentType $mimeType, checking for XML")
                    withMemory(XML_SIGNATURE.size) { memory ->
                        log.trace("AssertMultiStatus: Peeking into memory")
                        bodyChannel.peekTo(memory, 0)
                        log.trace("Got $memory")
                        memory.loadByteArray(0, firstBytes)
                    }
                    log.trace("AssertMultiStatus: First bytes were ${firstBytes.decodeToString()}")
                    if (XML_SIGNATURE.contentEquals(firstBytes)) {
                        Dav4jvm.log.warn("Received 207 Multi-Status that seems to be XML but has MIME type $mimeType")

                        // response is OK, return and do not throw Exception below
                        return
                    }
                } catch (e: Exception) {
                    Dav4jvm.log.warn("Couldn't scan for XML signature", e)
                }

                throw DavException("Received non-XML 207 Multi-Status", httpResponse = response)
            }
        } ?: log.warn("Received 207 Multi-Status without Content-Type, assuming XML")
    }

    // Multi-Status handling

    /**
     * Processes a Multi-Status response.
     *
     * @param reader   the Multi-Status response is read from this
     * @param callback called for every XML response element in the Multi-Status response
     *
     * @return list of properties which have been received in the Multi-Status response, but
     * are not part of response XML elements (like `sync-token` which is returned as [SyncToken])
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error (like an invalid XML response)
     */
    protected suspend fun processMultiStatus(response: HttpResponse, callback: MultiResponseCallback): List<Property> {
        checkStatus(response)
        assertMultiStatus(response)
        val responseProperties = mutableListOf<Property>()
        val parser = XmlUtils.createReader(response.bodyAsText())

        fun parseMultiStatus() {
            // <!ELEMENT multistatus (response*, responsedescription?,
            //                        sync-token?) >
            XmlUtils.processTag(parser) {
                when (parser.name) {
                    DavResponse.RESPONSE ->
                        Response.parse(parser, location, callback)

                    SyncToken.NAME ->
                        XmlUtils.readText(parser)?.let {
                            responseProperties += SyncToken(it)
                        }
                }
            }
        }

        try {
            var didParse: Boolean = false
            XmlUtils.processTag(parser, DavResponse.MULTISTATUS, targetDepth = 1) {
                didParse = true
                parseMultiStatus()
            }
            if (!didParse) throw DavException("Multi-Status response didn't contain multistatus XML element")
            return responseProperties
        } catch (e: EOFException) {
            throw DavException("Incomplete multistatus XML element", e)
        } catch (e: XmlException) {
            throw DavException("Couldn't parse multistatus XML element", e)
        }
    }
}
