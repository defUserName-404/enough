package app.enough.update.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    private fun release(tag: String = "v1.2.3", extra: String = "", assets: String = ASSETS) =
        """{"tag_name":"$tag",$extra"assets":[$assets]}"""

    @Test fun version_parse_and_order() {
        assertEquals(Version(1, 2, 3), Version.parse("v1.2.3"))
        assertEquals(Version(0, 1, 0), Version.parse("0.1.0-debug"))
        assertNull(Version.parse("1.2"))
        assertNull(Version.parse("latest"))
        assertTrue(Version(1, 10, 0) > Version(1, 9, 9))
    }

    @Test fun parses_latest() {
        val r = UpdateCheck.parseLatest(release())!!
        assertEquals(Version(1, 2, 3), r.version)
        assertEquals("https://x/enough-1.2.3.apk", r.apkUrl)
        assertEquals("https://x/enough-1.2.3.apk.sha256", r.sha256Url)
    }

    @Test fun rejects_draft_prerelease_and_missing_assets() {
        assertNull(UpdateCheck.parseLatest(release(extra = """"draft":true,""")))
        assertNull(UpdateCheck.parseLatest(release(extra = """"prerelease":true,""")))
        assertNull(UpdateCheck.parseLatest(release(assets = """{"name":"enough-1.2.3.apk","browser_download_url":"https://x/a"}""")))
        assertNull(UpdateCheck.parseLatest(release(tag = "nightly")))
        assertNull(UpdateCheck.parseLatest("not json"))
    }

    @Test fun newer_only_when_strictly_greater() {
        assertTrue(UpdateCheck.isNewer("1.2.2", Version(1, 2, 3)))
        assertFalse(UpdateCheck.isNewer("1.2.3", Version(1, 2, 3)))
        assertFalse(UpdateCheck.isNewer("1.2.3-debug", Version(1, 2, 3)))
        assertTrue(UpdateCheck.isNewer("0.0.0-dev", Version(0, 0, 1)))
    }

    @Test fun parses_sha256_file() {
        val h = "ab".repeat(32)
        assertEquals(h, UpdateCheck.parseSha256("${h.uppercase()}  enough-1.2.3.apk\n"))
        assertNull(UpdateCheck.parseSha256("oops"))
    }

    @Test fun parses_latest_redirect_location() {
        val r = UpdateCheck.parseLatestRedirect(
            "https://github.com/defUserName-404/enough/releases/tag/v1.2.3",
        )!!
        assertEquals(Version(1, 2, 3), r.version)
        assertEquals("${UpdateCheck.RELEASES_BASE}/releases/download/v1.2.3/enough-1.2.3.apk", r.apkUrl)
        assertEquals("${UpdateCheck.RELEASES_BASE}/releases/download/v1.2.3/enough-1.2.3.apk.sha256", r.sha256Url)
        assertEquals(Version(1, 0, 0), UpdateCheck.parseLatestRedirect("/releases/tag/1.0.0")!!.version)
        assertNull(UpdateCheck.parseLatestRedirect("https://github.com/x/y/releases/latest"))
        assertNull(UpdateCheck.parseLatestRedirect("https://github.com/x/y/releases/tag/nightly"))
        assertNull(UpdateCheck.parseLatestRedirect(""))
    }

    private companion object {
        const val ASSETS = """{"name":"enough-1.2.3.apk","browser_download_url":"https://x/enough-1.2.3.apk"},""" +
            """{"name":"enough-1.2.3.apk.sha256","browser_download_url":"https://x/enough-1.2.3.apk.sha256"}"""
    }
}
