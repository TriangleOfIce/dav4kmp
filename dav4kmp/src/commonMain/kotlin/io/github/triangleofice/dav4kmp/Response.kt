/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package io.github.triangleofice.dav4kmp

import io.github.triangleofice.dav4kmp.Dav4jvm.log
import io.github.triangleofice.dav4kmp.XmlUtils.nextText
import io.github.triangleofice.dav4kmp.property.ResourceType
import io.github.triangleofice.dav4kmp.MultiResponseCallback
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlReader
import kotlin.reflect.KClass

/**
 * Represents a WebDAV response XML Element.
 *
 *     <!ELEMENT response (href, ((href*, status)|(propstat+)),
 *                         error?, responsedescription? , location?) >
 */
data class Response(
    /**
     * URL of the requested resource. For instance, if `this` is a result
     * of a PROPFIND request, the `requestedUrl` would be the URL where the
     * PROPFIND request has been sent to (usually the collection URL).
     */
    val requestedUrl: Url,

    /**
     * URL of this response (`href` element)
     */
    val href: Url,

    /**
     * status of this response (`status` XML element)
     */
    val status: StatusLine?,

    /**
     * property/status elements (`propstat` XML elements)
     */
    val propstat: List<PropStat>,

    /**
     * list of precondition/postcondition elements (`error` XML elements)
     */
    val error: List<Error>? = null,

    /**
     * new location of this response (`location` XML element), used for redirects
     */
    val newLocation: Url? = null,
) {

    enum class HrefRelation {
        SELF, MEMBER, OTHER
    }

    /**
     * All properties from propstat elements with empty status or status code 2xx.
     */
    val properties: List<Property> by lazy {
        if (isSuccess()) {
            propstat.filter { it.isSuccess() }.map { it.properties }.flatten()
        } else {
            emptyList()
        }
    }

    /**
     * Convenience method to get a certain property with empty status or status code 2xx
     * from the current response.
     */
    inline operator fun <reified T : Property> get(clazz: KClass<T>) =
        properties.filterIsInstance<T>().firstOrNull()

    /**
     * Returns whether the request was successful.
     *
     * @return true: no status XML element or status code 2xx; false: otherwise
     */
    fun isSuccess() = status?.status?.isSuccess() ?: true

    /**
     * Returns the name (last path segment) of the resource.
     */
    fun hrefName() = HttpUtils.fileName(href)

    companion object {

        val RESPONSE = QName(XmlUtils.NS_WEBDAV, "response")
        val MULTISTATUS = QName(XmlUtils.NS_WEBDAV, "multistatus")
        val STATUS = QName(XmlUtils.NS_WEBDAV, "status")
        val LOCATION = QName(XmlUtils.NS_WEBDAV, "location")

        /**
         * Parses an XML response element.
         */
        fun parse(parser: XmlReader, location: Url, callback: MultiResponseCallback) {
            var href: Url? = null
            var status: StatusLine? = null
            val propStat = mutableListOf<PropStat>()
            var error: List<Error>? = null
            var newLocation: Url? = null

            XmlUtils.processTag(parser) {
                when (parser.name) {
                    DavResource.HREF -> {
                        var sHref = parser.nextText()
                        if (!sHref.startsWith("/")) {
                            /* According to RFC 4918 8.3 URL Handling, only absolute paths are allowed as relative
                               URLs. However, some servers reply with relative paths. */
                            val firstColon = sHref.indexOf(':')
                            if (firstColon != -1) {
                                /* There are some servers which return not only relative paths, but relative paths like "a:b.vcf",
                                   which would be interpreted as scheme: "a", scheme-specific part: "b.vcf" normally.
                                   For maximum compatibility, we prefix all relative paths which contain ":" (but not "://"),
                                   with the root path to allow resolving by Url. */
                                var hierarchical = false
                                try {
                                    if (sHref.substring(firstColon, firstColon + 3) == "://") {
                                        hierarchical = true
                                    }
                                } catch (e: IndexOutOfBoundsException) {
                                    // no "://"
                                }
                                if (!hierarchical) {
                                    sHref = "/${location.encodedPath.drop(1)}$sHref"
                                }
                            }
                        }
                        href = URLBuilder(location).takeFrom(sHref).build()
                    }

                    STATUS ->
                        status = try {
                            StatusLine.parse(parser.nextText())
                        } catch (e: IllegalStateException) {
                            log.warn("Invalid status line, treating as HTTP error 500")
                            StatusLine(
                                HttpProtocolVersion.HTTP_1_1,
                                HttpStatusCode(500, "Invalid status line"),
                            )
                        }

                    PropStat.NAME ->
                        PropStat.parse(parser).let { propStat += it }

                    Error.NAME ->
                        error = Error.parseError(parser)

                    LOCATION ->
                        // TODO are invalid urls possible?
                        newLocation = Url(parser.nextText())
                }
            }

            if (href == null) {
                log.warn("Ignoring XML response element without valid href")
                return
            }

            // if we know this resource is a collection, make sure href has a trailing slash
            // (for clarity and resolving relative paths)
            propStat.filter { it.isSuccess() }
                .map { it.properties }
                .filterIsInstance<ResourceType>()
                .firstOrNull()
                ?.let { type ->
                    if (type.types.contains(ResourceType.COLLECTION)) {
                        href = UrlUtils.withTrailingSlash(href!!)
                    }
                }

            // log.log(Level.FINE, "Received properties for $href", if (status != null) status else propStat)

            // Which resource does this <response> represent?
            val relation = when {
                UrlUtils.equals(
                    UrlUtils.omitTrailingSlash(href!!),
                    UrlUtils.omitTrailingSlash(location),
                ) ->
                    HrefRelation.SELF

                else -> {
                    if (location.protocol == href!!.protocol && location.host == href!!.host && location.port == href!!.port) {
                        val locationSegments = location.pathSegments
                        val hrefSegments = href!!.pathSegments

                        // don't compare trailing slash segment ("")
                        var nBasePathSegments = locationSegments.size
                        if (locationSegments[nBasePathSegments - 1] == "") {
                            nBasePathSegments--
                        }

                        /* example:   locationSegments  = [ "davCollection", "" ]
                                      nBasePathSegments = 1
                                      hrefSegments      = [ "davCollection", "aMember" ]
                        */
                        var relation = HrefRelation.OTHER
                        if (hrefSegments.size > nBasePathSegments) {
                            val sameBasePath =
                                (0 until nBasePathSegments).none { locationSegments[it] != hrefSegments[it] }
                            if (sameBasePath) {
                                relation = HrefRelation.MEMBER
                            }
                        }

                        relation
                    } else {
                        HrefRelation.OTHER
                    }
                }
            }

            callback.onResponse(
                Response(
                    location,
                    href!!,
                    status,
                    propStat,
                    error,
                    newLocation,
                ),
                relation,
            )
        }
    }
}
