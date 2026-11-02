package com.bookorbit.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCachePolicyTest {
    @Test
    fun `reads are kept for offline use`() {
        assertTrue(OfflineCachePolicy.isCacheable("GET", "/api/v1/dashboard/scrollers/continue-reading"))
        assertTrue(OfflineCachePolicy.isCacheable("GET", "/api/v1/books/12"))
        assertTrue(OfflineCachePolicy.isCacheable("POST", "/api/v1/libraries/3/books"))
        assertTrue(OfflineCachePolicy.isCacheable("POST", "/api/v1/books/query"))
    }

    @Test
    fun `writes, files and what the app keeps itself are not`() {
        assertFalse(OfflineCachePolicy.isCacheable("POST", "/api/v1/books/files/5/progress"))
        assertFalse(OfflineCachePolicy.isCacheable("PATCH", "/api/v1/books/12/status"))
        assertFalse(OfflineCachePolicy.isCacheable("GET", "/api/v1/books/files/5/serve"))
        assertFalse(OfflineCachePolicy.isCacheable("GET", "/api/v1/books/12/cover"))
        assertFalse(OfflineCachePolicy.isCacheable("GET", "/api/v1/books/files/5/progress"))
        assertFalse(OfflineCachePolicy.isCacheable("GET", "/api/v1/books/12/annotations"))
        assertFalse(OfflineCachePolicy.isCacheable("GET", "/api/v1/reader/preferences/5"))
        assertFalse(OfflineCachePolicy.isCacheable("GET", "/api/v1/auth/me"))
    }
}
