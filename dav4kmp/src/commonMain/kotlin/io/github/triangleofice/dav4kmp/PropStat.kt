/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package io.github.triangleofice.dav4kmp

import io.github.triangleofice.dav4kmp.Response.Companion.STATUS
import io.github.triangleofice.dav4kmp.XmlUtils.nextText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlReader
import kotlin.jvm.JvmField

/**
 * Represents a WebDAV propstat XML element.
 *
 *     <!ELEMENT propstat (prop, status, error?, responsedescription?) >
 */
data class PropStat(
    val properties: List<Property>,
    val status: HttpStatusCode,
    val error: List<Error>? = null,
) {

    companion object {

        @JvmField
        val NAME = QName(XmlUtils.NS_WEBDAV, "propstat")

        private val ASSUMING_OK = HttpStatusCode(200, "Assuming OK")
        private val INVALID_STATUS = HttpStatusCode(500, "Invalid status line")

        fun parse(parser: XmlReader): PropStat {
            var status: HttpStatusCode? = null
            val prop = mutableListOf<Property>()

            XmlUtils.processTag(parser) {
                when (parser.name) {
                    DavResource.PROP ->
                        prop.addAll(Property.parse(parser))

                    STATUS ->
                        status = try {
                            StatusLine.parse(parser.nextText()).status
                        } catch (e: IllegalStateException) {
                            // invalid status line, treat as 500 Internal Server Error
                            INVALID_STATUS
                        }
                }
            }

            return PropStat(prop, status ?: ASSUMING_OK)
        }
    }

    fun isSuccess() = status.isSuccess()
}
