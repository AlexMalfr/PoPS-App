package com.malfreyt.alexandre.pops_app

import com.malfreyt.alexandre.pops_app.data.normalizeOasisBaseUrl
import com.malfreyt.alexandre.pops_app.data.DEFAULT_OASIS_BASE_URL
import org.junit.Assert.*
import org.junit.Test

class ServerUrlTest {
    @Test fun acceptsAndNormalizesAlternateInstancesAndLocalMocks() {
        assertEquals("https://example.org/oasis/", normalizeOasisBaseUrl("  https://example.org/oasis  "))
        assertEquals("http://127.0.0.1:8080/", normalizeOasisBaseUrl("http://127.0.0.1:8080"))
        assertEquals("http://10.0.2.2:8080/", normalizeOasisBaseUrl("http://10.0.2.2:8080/"))
    }
    @Test fun emptyServerUsesDefaultInstance() {
        assertEquals(DEFAULT_OASIS_BASE_URL, normalizeOasisBaseUrl(""))
        assertEquals(DEFAULT_OASIS_BASE_URL, normalizeOasisBaseUrl("  "))
    }
    @Test fun rejectsUrlsThatCannotBeUsedAsAnOasisApiBase() {
        listOf("not a URL", "ftp://example.org", "https://user:pass@example.org/", "https://example.org/?query=1", "https://example.org/#fragment")
            .forEach { assertNull(it, normalizeOasisBaseUrl(it)) }
    }
}
