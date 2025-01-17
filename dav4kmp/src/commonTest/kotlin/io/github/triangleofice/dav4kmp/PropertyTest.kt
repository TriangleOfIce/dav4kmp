/*
 *
 *  * This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package io.github.triangleofice.dav4kmp

import io.github.triangleofice.dav4kmp.property.GetETag
import io.kotest.core.spec.style.FunSpec
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.localPart
import kotlin.test.assertEquals
import kotlin.test.assertTrue

object PropertyTest : FunSpec({

    test("testParse_InvalidProperty") {
        val parser = XmlUtils.createReader("<multistatus xmlns='DAV:'><getetag/></multistatus>")
        parser.next()

        // we're now at the start of <multistatus>
        assertEquals(EventType.START_ELEMENT, parser.eventType)
        assertEquals("multistatus", parser.name.localPart)

        // parse invalid DAV:getetag
        assertTrue(Property.parse(parser).isEmpty())

        // we're now at the end of <multistatus>
        assertEquals(EventType.END_ELEMENT, parser.eventType)
        assertEquals("multistatus", parser.name.localPart)
    }

    test("testParse_ValidProperty") {
        val parser = XmlUtils.createReader("<multistatus xmlns='DAV:'><getetag>12345</getetag></multistatus>")
        parser.next()

        // we're now at the start of <multistatus>
        assertEquals(EventType.START_ELEMENT, parser.eventType)
        assertEquals("multistatus", parser.name.localPart)

        val etag = Property.parse(parser).first()
        assertEquals(GetETag("12345"), etag)

        // we're now at the end of <multistatus>
        assertEquals(EventType.END_ELEMENT, parser.eventType)
        assertEquals("multistatus", parser.name.localPart)
    }
})
