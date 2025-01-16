/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package io.github.triangleofice.dav4kmp.property

import io.github.triangleofice.dav4kmp.Dav4jvm
import io.github.triangleofice.dav4kmp.Property
import io.github.triangleofice.dav4kmp.PropertyFactory
import io.github.triangleofice.dav4kmp.XmlUtils
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlReader

data class GetContentLength(
    val contentLength: Long,
) : Property {
    companion object {
        val NAME = QName(XmlUtils.NS_WEBDAV, "getcontentlength")
    }

    object Factory : PropertyFactory {
        override fun getName() = NAME

        override fun create(parser: XmlReader): GetContentLength? {
            XmlUtils.readText(parser)?.let { valueStr ->
                try {
                    return GetContentLength(valueStr.toLong())
                } catch (e: NumberFormatException) {
                    Dav4jvm.log.warn("Couldn't parse $NAME: $valueStr", e)
                }
            }
            return null
        }
    }
}
