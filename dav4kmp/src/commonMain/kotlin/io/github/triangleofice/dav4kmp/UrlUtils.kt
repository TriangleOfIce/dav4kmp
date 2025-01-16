/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package io.github.triangleofice.dav4kmp

import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.decodeURLPart
import io.ktor.http.encodedPath

object UrlUtils {

    /**
     * Compares two URLs in WebDAV context. If two URLs are considered *equal*, both
     * represent the same WebDAV resource (e.g. `http://host:80/folder1` and `http://HOST/folder1#somefragment`).
     *
     * It decodes %xx entities in the path, so `/my@dav` and `/my%40dav` are considered the same.
     * This is important to process multi-status responses: some servers serve a multi-status
     * response with href `/my@dav` when you request `/my%40dav` and vice versa.
     *
     * This method does not deal with trailing slashes, so if you want to compare collection URLs,
     * make sure they both (don't) have a trailing slash before calling this method, for instance
     * with [omitTrailingSlash] or [withTrailingSlash].
     *
     * @param url1 the first URL to be compared
     * @param url2 the second URL to be compared
     *
     * @return whether [url1] and [url2] (usually) represent the same WebDAV resource
     */
    fun equals(url1: Url, url2: Url): Boolean {
        // if okhttp thinks the two URLs are equal, they're in any case
        // (and it's a simple String comparison)
        if (url1 == url2) {
            return true
        }

        // drop #fragment parts and convert to URI
        val uri1 = URLBuilder(url1.toString()).apply {
            host = host.lowercase()
            encodedPath = encodedPath.decodeURLPart()
            fragment = ""
        }.build()
        val uri2 = URLBuilder(url2.toString()).apply {
            host = host.lowercase()
            encodedPath = encodedPath.decodeURLPart()
            fragment = ""
        }.build()

        return uri1 == uri2
    }

    /**
     * Gets the first-level domain name (without subdomains) from a host name.
     * Also removes trailing dots.
     *
     * @param host name (e.g. `www.example.com.`)
     *
     * @return domain name (e.g. `example.com`)
     */
    fun hostToDomain(host: String?): String? {
        if (host == null) {
            return null
        }

        // remove optional dot at end
        val withoutTrailingDot = host.removeSuffix(".")

        // split into labels
        val labels = withoutTrailingDot.split('.')
        return if (labels.size >= 2) {
            labels[labels.size - 2] + "." + labels[labels.size - 1]
        } else {
            withoutTrailingDot
        }
    }

    /**
     * Ensures that a given URL doesn't have a trailing slash after member names.
     * If the path is the root path (`/`), the slash is preserved.
     *
     * @param url URL to process (e.g. 'http://host/test1/')
     *
     * @return URL without trailing slash (except when the path is the root path), e.g. `http://host/test1`
     */
    fun omitTrailingSlash(url: Url): Url {
        val hasTrailingSlash = url.pathSegments.last() == ""

        return if (hasTrailingSlash) {
            URLBuilder(url).apply { pathSegments = pathSegments.dropLast(1) }.build()
        } else {
            url
        }
    }

    /**
     * Ensures that a given URL has a trailing slash after member names.
     *
     * @param url URL to process (e.g. 'http://host/test1')
     *
     * @return URL with trailing slash, e.g. `http://host/test1/`
     */
    fun withTrailingSlash(url: Url): Url {
        val hasTrailingSlash = url.pathSegments.last() == ""

        return if (hasTrailingSlash) {
            url
        } else {
            URLBuilder(url).apply { pathSegments += "" }.build()
        }
    }
}
