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
