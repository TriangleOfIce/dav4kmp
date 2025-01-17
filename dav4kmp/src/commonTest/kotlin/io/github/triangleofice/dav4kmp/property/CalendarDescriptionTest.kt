/*
 *
 *  * This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package io.github.triangleofice.dav4kmp.property

import kotlin.test.assertEquals

object CalendarDescriptionTest : PropertyTest() {

    init {
        test("testCalendarDescription") {
            val results =
                parseProperty("<calendar-description xmlns=\"urn:ietf:params:xml:ns:caldav\">My Calendar</calendar-description>")
            val result = results.first() as CalendarDescription
            assertEquals("My Calendar", result.description)
        }
    }
}
