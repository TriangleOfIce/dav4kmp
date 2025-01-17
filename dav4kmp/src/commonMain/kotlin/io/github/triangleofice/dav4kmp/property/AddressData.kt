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
import io.github.triangleofice.dav4kmp.XmlUtils
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlReader
import kotlin.jvm.JvmField

data class AddressData(
    val card: String?,
) : Property {

    companion object {
        @JvmField
        val NAME = QName(XmlUtils.NS_CARDDAV, "address-data")

        // attributes
        const val CONTENT_TYPE = "content-type"
        const val VERSION = "version"
    }

    object Factory : PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlReader) =
            // <!ELEMENT address-data (#PCDATA)>
            AddressData(XmlUtils.readText(parser))
    }
}
