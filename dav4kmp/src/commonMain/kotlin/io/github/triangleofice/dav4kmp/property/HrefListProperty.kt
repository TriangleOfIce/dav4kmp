/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package io.github.triangleofice.dav4kmp.property

import io.github.triangleofice.dav4kmp.DavResource
import io.github.triangleofice.dav4kmp.Property
import io.github.triangleofice.dav4kmp.PropertyFactory
import io.github.triangleofice.dav4kmp.XmlUtils
import nl.adaptivity.xmlutil.XmlReader

abstract class HrefListProperty : Property {

    val hrefs = mutableListOf<String>()

    val href
        get() = hrefs.firstOrNull()

    override fun toString() = "href=[" + hrefs.joinToString(", ") + "]"

    abstract class Factory : PropertyFactory {

        fun create(parser: XmlReader, list: HrefListProperty): HrefListProperty {
            XmlUtils.readTextPropertyList(parser, DavResource.HREF, list.hrefs)
            return list
        }
    }
}
