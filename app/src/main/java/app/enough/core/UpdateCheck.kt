package app.enough.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** MAJOR.MINOR.PATCH. Anything after `-` or `+` (e.g. `-debug`, `-dev`) is ignored. */
data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
    override fun compareTo(other: Version): Int =
        compareValuesBy(this, other, Version::major, Version::minor, Version::patch)

    override fun toString() = "$major.$minor.$patch"

    companion object {
        fun parse(raw: String): Version? {
            val core = raw.trim().removePrefix("v").substringBefore('-').substringBefore('+')
            val p = core.split('.').map { it.toIntOrNull() ?: return null }
            return if (p.size == 3) Version(p[0], p[1], p[2]) else null
        }
    }
}

data class ReleaseInfo(val version: Version, val apkUrl: String, val sha256Url: String)

/** Pure parsing for GitHub release discovery. No networking here. */
object UpdateCheck {
    private val APK_NAME = Regex("""enough-\d+\.\d+\.\d+\.apk""")
    private val SHA256 = Regex("""\b[0-9a-fA-F]{64}\b""")
    private val TAG_PATH = Regex("""/releases/tag/(v?\d+\.\d+\.\d+)\b""")

    const val RELEASES_BASE = "https://github.com/defUserName-404/enough"

    /** Null when the release is a draft or prerelease, has no valid tag, or lacks the APK or its checksum. */
    fun parseLatest(json: String): ReleaseInfo? {
        val o = try { Json.parseToJsonElement(json).jsonObject } catch (e: Exception) { return null }
        if (o.flag("draft") || o.flag("prerelease")) return null
        val version = Version.parse(o["tag_name"]?.jsonPrimitive?.content ?: return null) ?: return null
        val assets = try {
            o["assets"]?.jsonArray?.map { it.jsonObject } ?: return null
        } catch (e: Exception) { return null }
        fun url(name: String) = assets.firstOrNull { it["name"]?.jsonPrimitive?.content == name }
            ?.get("browser_download_url")?.jsonPrimitive?.content
        val apk = assets.mapNotNull { it["name"]?.jsonPrimitive?.content }.firstOrNull { APK_NAME.matches(it) } ?: return null
        return ReleaseInfo(version, url(apk) ?: return null, url("$apk.sha256") ?: return null)
    }

    /**
     * From a `Location` (or full URL) like `.../releases/tag/v1.2.3`, build download URLs that match
     * CI asset names (`enough-1.2.3.apk` + `.sha256`). Null if the path/tag is not a semver release.
     */
    fun parseLatestRedirect(location: String): ReleaseInfo? {
        val tag = TAG_PATH.find(location.trim())?.groupValues?.get(1) ?: return null
        val version = Version.parse(tag) ?: return null
        val apk = "enough-$version.apk"
        val base = "$RELEASES_BASE/releases/download/v$version"
        return ReleaseInfo(version, "$base/$apk", "$base/$apk.sha256")
    }

    fun isNewer(current: String, latest: Version): Boolean {
        val cur = Version.parse(current) ?: return true
        return latest > cur
    }

    /** `sha256sum` output: "<hash>  <file>". Returns the lowercase hash or null. */
    fun parseSha256(text: String): String? = SHA256.find(text)?.value?.lowercase()

    private fun JsonObject.flag(key: String): Boolean = try { this[key]?.jsonPrimitive?.boolean == true } catch (e: Exception) { false }
}
