/*
 *
 *  * This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package io.github.triangleofice.dav4kmp

import io.ktor.client.statement.HttpResponse
import io.ktor.http.Url
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.bits.withMemory
import korlibs.time.DateFormat
import korlibs.time.DateTime
import korlibs.time.DateTimeTz
import korlibs.time.format
import korlibs.time.locale.ExtendedTimezoneNames
import korlibs.time.parse

object HttpUtils {

    private const val httpDateFormatStr = "EEE, dd MMM yyyy HH:mm:ss zzz"
    private val httpDateFormat = DateFormat(httpDateFormatStr).withTimezoneNames(ExtendedTimezoneNames)

    private val extendedDateFormats by lazy {
        listOf(
            httpDateFormatStr, // RFC 822, updated by RFC 1123 with any TZ
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEEE, dd-MMM-yy HH:mm:ss zzz", // RFC 850, obsoleted by RFC 1036 with any TZ.
            "EEE MMM d HH:mm:ss yyyy", // ANSI C's asctime() format
            // Alternative formats.
            "EEE, dd-MMM-yyyy HH:mm:ss z",
            "EEE, dd-MMM-yyyy HH-mm-ss z",
            "EEE, dd MMM yy HH:mm:ss z",
            "EEE dd-MMM-yyyy HH:mm:ss z",
            "EEE dd MMM yyyy HH:mm:ss z",
            "EEE dd-MMM-yyyy HH-mm-ss z",
            "EEE dd-MMM-yy HH:mm:ss z",
            "EEE dd MMM yy HH:mm:ss z",
            "EEE,dd-MMM-yy HH:mm:ss z",
            "EEE,dd-MMM-yyyy HH:mm:ss z",
            "EEE, dd-MM-yyyy HH:mm:ss z",
            /* RI bug 6641315 claims a cookie of this format was once served by www.yahoo.com */
            "EEE MMM d yyyy HH:mm:ss z",
        ).map { DateFormat(it) }
    }

    /**
     * Gets the resource name (the last segment of the path) from an URL.
     * Empty if the resource is the base directory.
     *
     * * `dir` for `https://example.com/dir/`
     * * `file` for `https://example.com/file`
     * * `` for `https://example.com` or  `https://example.com/`
     *
     * @return resource name
     */
    fun fileName(url: Url): String {
        val pathSegments = url.pathSegments.dropLastWhile { it == "" }
        return pathSegments.lastOrNull() ?: ""
    }

    fun listHeader(response: HttpResponse, name: String): Array<String> =
        response.headers.getAll(name)?.filter { it.isNotEmpty() }?.toTypedArray() ?: emptyArray()

    /**
     * Formats a date for use in HTTP headers using [httpDateFormat].
     *
     * @param date date to be formatted
     * @return date in HTTP-date format
     */
    fun formatDate(date: DateTime): String = httpDateFormat.format(date)

    /**
     * Parses a HTTP-date.
     *
     * @param dateStr date with format specified by RFC 7231 section 7.1.1.1
     * or in one of the obsolete formats (copied from okhttp internal date-parsing class)
     *
     * @return date, or null if date could not be parsed
     */
    fun parseDate(dateStr: String): DateTimeTz? {
        for (df in extendedDateFormats) {
            try {
                return df.parse(dateStr)
            } catch (_: Exception) {
            }
        }
        Dav4jvm.log.warn("Couldn't parse date: $dateStr, ignoring")
        return null
    }
}

suspend fun ByteReadChannel.isEmpty() = isClosedForRead || withMemory(1) { mem -> peekTo(mem, 0, 0, 1, 1) } == 0L
