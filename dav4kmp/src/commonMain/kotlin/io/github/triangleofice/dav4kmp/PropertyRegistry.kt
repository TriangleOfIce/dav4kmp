/*
 *
 *  * This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package io.github.triangleofice.dav4kmp

import io.github.triangleofice.dav4kmp.property.AddMember
import io.github.triangleofice.dav4kmp.property.AddressData
import io.github.triangleofice.dav4kmp.property.AddressbookDescription
import io.github.triangleofice.dav4kmp.property.AddressbookHomeSet
import io.github.triangleofice.dav4kmp.property.CalendarColor
import io.github.triangleofice.dav4kmp.property.CalendarData
import io.github.triangleofice.dav4kmp.property.CalendarDescription
import io.github.triangleofice.dav4kmp.property.CalendarHomeSet
import io.github.triangleofice.dav4kmp.property.CalendarProxyReadFor
import io.github.triangleofice.dav4kmp.property.CalendarProxyWriteFor
import io.github.triangleofice.dav4kmp.property.CalendarTimezone
import io.github.triangleofice.dav4kmp.property.CalendarUserAddressSet
import io.github.triangleofice.dav4kmp.property.CreationDate
import io.github.triangleofice.dav4kmp.property.CurrentUserPrincipal
import io.github.triangleofice.dav4kmp.property.CurrentUserPrivilegeSet
import io.github.triangleofice.dav4kmp.property.DisplayName
import io.github.triangleofice.dav4kmp.property.GetCTag
import io.github.triangleofice.dav4kmp.property.GetContentLength
import io.github.triangleofice.dav4kmp.property.GetContentType
import io.github.triangleofice.dav4kmp.property.GetETag
import io.github.triangleofice.dav4kmp.property.GetLastModified
import io.github.triangleofice.dav4kmp.property.GroupMembership
import io.github.triangleofice.dav4kmp.property.MaxICalendarSize
import io.github.triangleofice.dav4kmp.property.MaxVCardSize
import io.github.triangleofice.dav4kmp.property.Owner
import io.github.triangleofice.dav4kmp.property.QuotaAvailableBytes
import io.github.triangleofice.dav4kmp.property.QuotaUsedBytes
import io.github.triangleofice.dav4kmp.property.ResourceType
import io.github.triangleofice.dav4kmp.property.ScheduleTag
import io.github.triangleofice.dav4kmp.property.Source
import io.github.triangleofice.dav4kmp.property.SupportedAddressData
import io.github.triangleofice.dav4kmp.property.SupportedCalendarComponentSet
import io.github.triangleofice.dav4kmp.property.SupportedCalendarData
import io.github.triangleofice.dav4kmp.property.SupportedReportSet
import io.github.triangleofice.dav4kmp.property.SyncToken
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlException
import nl.adaptivity.xmlutil.XmlReader

object PropertyRegistry {

    private val factories = mutableMapOf<QName, PropertyFactory>()

    init {
        Dav4jvm.log.info("Registering DAV property factories")
        registerDefaultFactories()
    }

    private fun registerDefaultFactories() {
        register(
            listOf(
                AddMember.Factory,
                AddressbookDescription.Factory,
                AddressbookHomeSet.Factory,
                AddressData.Factory,
                CalendarColor.Factory,
                CalendarData.Factory,
                CalendarDescription.Factory,
                CalendarHomeSet.Factory,
                CalendarProxyReadFor.Factory,
                CalendarProxyWriteFor.Factory,
                CalendarTimezone.Factory,
                CalendarUserAddressSet.Factory,
                CreationDate.Factory,
                CurrentUserPrincipal.Factory,
                CurrentUserPrivilegeSet.Factory,
                DisplayName.Factory,
                GetContentLength.Factory,
                GetContentType.Factory,
                GetCTag.Factory,
                GetETag.Factory,
                GetLastModified.Factory,
                GroupMembership.Factory,
                MaxICalendarSize.Factory,
                MaxVCardSize.Factory,
                Owner.Factory,
                QuotaAvailableBytes.Factory,
                QuotaUsedBytes.Factory,
                ResourceType.Factory,
                ScheduleTag.Factory,
                Source.Factory,
                SupportedAddressData.Factory,
                SupportedCalendarComponentSet.Factory,
                SupportedCalendarData.Factory,
                SupportedReportSet.Factory,
                SyncToken.Factory,
            ),
        )
    }

    /**
     * Registers a property factory, so that objects for all WebDAV properties which are handled
     * by this factory can be created.
     *
     * @param factory property factory to be registered
     */
    fun register(factory: PropertyFactory) {
        Dav4jvm.log.trace("Registering ${factory::class.simpleName} for ${factory.getName()}")
        factories[factory.getName()] = factory
    }

    /**
     * Registers some property factories, so that objects for all WebDAV properties which are handled
     * by these factories can be created.

     * @param factories property factories to be registered
     */
    fun register(factories: Iterable<PropertyFactory>) {
        factories.forEach {
            register(it)
        }
    }

    fun create(name: QName, parser: XmlReader) =
        try {
            factories[name]?.create(parser)
        } catch (e: XmlException) {
            Dav4jvm.log.warn("Couldn't parse $name", e)
            null
        }
}
