/*
 *
 *  * This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package io.github.triangleofice.dav4kmp.property

import io.github.triangleofice.dav4kmp.Property
import io.github.triangleofice.dav4kmp.XmlUtils
import io.kotest.core.spec.style.FunSpec

open class PropertyTest : FunSpec() {

    companion object {

        fun parseProperty(s: String): List<Property> {
            val parser = XmlUtils.createReader("<test>$s</test>")
            parser.nextTag() // move into <test>
            return Property.parse(parser)
        }
    }
}
