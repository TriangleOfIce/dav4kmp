/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package io.github.triangleofice.dav4kmp

import io.github.triangleofice.dav4kmp.XmlUtils.insertTag
import io.github.triangleofice.dav4kmp.exception.DavException
import io.github.triangleofice.dav4kmp.exception.HttpException
import io.github.triangleofice.dav4kmp.property.AddressData
import io.github.triangleofice.dav4kmp.property.GetContentType
import io.github.triangleofice.dav4kmp.property.GetETag
import io.github.triangleofice.dav4kmp.MultiResponseCallback
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.withCharset
import io.ktor.util.logging.Logger
import io.ktor.utils.io.charsets.Charsets
import nl.adaptivity.xmlutil.QName
import kotlin.jvm.JvmOverloads

class DavAddressBook @JvmOverloads constructor(
    httpClient: HttpClient,
    location: Url,
    log: Logger = Dav4jvm.log,
) : DavCollection(httpClient, location, log) {

    companion object {
        val MIME_JCARD = ContentType("application", "vcard+json")
        val MIME_VCARD3_UTF8 = ContentType.Text.VCard.withCharset(Charsets.UTF_8)
        val MIME_VCARD4 = ContentType.Text.VCard.withParameter("version", "4.0")

        val ADDRESSBOOK_QUERY = QName(XmlUtils.NS_CARDDAV, "addressbook-query")
        val ADDRESSBOOK_MULTIGET = QName(XmlUtils.NS_CARDDAV, "addressbook-multiget")
        val FILTER = QName(XmlUtils.NS_CARDDAV, "filter")
    }

    /**
     * Sends an addressbook-query REPORT request to the resource.
     *
     * @param callback called for every WebDAV response XML element in the result
     *
     * @return list of properties which have been received in the Multi-Status response, but
     * are not part of response XML elements
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error
     */
    suspend fun addressbookQuery(callback: MultiResponseCallback): List<Property> {
        /* <!ELEMENT addressbook-query ((DAV:allprop |
                                         DAV:propname |
                                         DAV:prop)?, filter, limit?)>
           <!ELEMENT filter (prop-filter*)>
        */
        val writer = StringBuilder()
        val serializer = XmlUtils.createWriter(writer)
        serializer.startDocument(encoding = "UTF-8")
        serializer.setPrefix("", XmlUtils.NS_WEBDAV)
        serializer.setPrefix("CARD", XmlUtils.NS_CARDDAV)
        serializer.insertTag(ADDRESSBOOK_QUERY) {
            insertTag(PROP) {
                insertTag(GetETag.NAME)
            }
            insertTag(FILTER)
        }
        serializer.endDocument()
        val response = httpClient.prepareRequest {
            url(location)
            method = Report
            // .method("REPORT", writer.toString().toRequestBody(MIME_XML))
            header(HttpHeaders.ContentType, MIME_XML.toString())
            setBody(writer.toString())
            header("Depth", "1")
        }.execute()
        return processMultiStatus(response, callback)
    }

    /**
     * Sends an addressbook-multiget REPORT request to the resource.
     *
     * @param urls         list of vCard URLs to be requested
     * @param contentType  MIME type of requested format; may be "text/vcard" for vCard or
     *                     "application/vcard+json" for jCard. *null*: don't request specific representation type
     * @param version      vCard version subtype of the requested format. Should only be specified together with a [contentType] of "text/vcard".
     *                     Currently only useful value: "4.0" for vCard 4. *null*: don't request specific version
     * @param callback     called for every WebDAV response XML element in the result
     *
     * @return list of properties which have been received in the Multi-Status response, but
     * are not part of response XML elements
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error
     */
    suspend fun multiget(
        urls: List<Url>,
        contentType: String? = null,
        version: String? = null,
        callback: MultiResponseCallback,
    ): List<Property> {
        /* <!ELEMENT addressbook-multiget ((DAV:allprop |
                                            DAV:propname |
                                            DAV:prop)?,
                                            DAV:href+)>
        */
        val writer = StringBuilder()
        val serializer = XmlUtils.createWriter(writer)
        serializer.startDocument(encoding = "UTF-8")
        serializer.setPrefix("", XmlUtils.NS_WEBDAV)
        serializer.setPrefix("CARD", XmlUtils.NS_CARDDAV)
        serializer.insertTag(ADDRESSBOOK_MULTIGET) {
            insertTag(PROP) {
                insertTag(GetContentType.NAME)
                insertTag(GetETag.NAME)
                insertTag(AddressData.NAME) {
                    if (contentType != null) {
                        attribute(null, AddressData.CONTENT_TYPE, null, contentType)
                    }
                    if (version != null) {
                        attribute(null, AddressData.VERSION, null, version)
                    }
                }
            }
            for (url in urls)
                insertTag(HREF) {
                    text(url.encodedPath)
                }
        }
        serializer.endDocument()

        // TODO followRedirects {
        val request = httpClient.prepareRequest {
            url(location)
            method = Report
            setBody(writer.toString())
            header(HttpHeaders.ContentType, MIME_XML)
            header("Depth", "0") // "The request MUST include a Depth: 0 header [...]"
        }.execute()
        return processMultiStatus(request, callback)
    }
}
