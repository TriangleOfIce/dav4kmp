package io.github.triangleofice.dav4kmp.exception

/**
 * Represents an invalid XML (WebDAV) property. This is for instance thrown
 * when parsing something like `<multistatus>...<getetag><novalue/></getetag>`
 * because a text value would be expected.
 */
class InvalidPropertyException(message: String) : Exception(message)
