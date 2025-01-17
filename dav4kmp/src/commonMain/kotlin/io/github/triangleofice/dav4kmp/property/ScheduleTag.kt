/*
 *
 *  * This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package io.github.triangleofice.dav4kmp.property

import io.github.triangleofice.dav4kmp.Property
import io.github.triangleofice.dav4kmp.PropertyFactory
import io.github.triangleofice.dav4kmp.QuotedStringUtils
import io.github.triangleofice.dav4kmp.XmlUtils
import io.ktor.client.statement.HttpResponse
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlReader
import kotlin.jvm.JvmField

class ScheduleTag(
    rawScheduleTag: String?,
) : Property {

    companion object {
        @JvmField
        val NAME = QName(XmlUtils.NS_CALDAV, "schedule-tag")

        fun fromResponse(response: HttpResponse) =
            response.headers["Schedule-Tag"]?.let { ScheduleTag(it) }
    }

    /* Value:  opaque-tag
       opaque-tag = quoted-string
    */
    val scheduleTag: String? = rawScheduleTag?.let { QuotedStringUtils.decodeQuotedString(it) }

    override fun toString() = scheduleTag ?: "(null)"

    object Factory : PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlReader) =
            ScheduleTag(XmlUtils.readText(parser))
    }
}
