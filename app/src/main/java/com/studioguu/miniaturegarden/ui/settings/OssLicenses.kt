package com.studioguu.miniaturegarden.ui.settings

import android.content.Context
import com.studioguu.miniaturegarden.R

/** One library in the list the oss-licenses plugin writes at build time, with the licence text that goes with it. */
data class OssLicense(val name: String, val text: String)

/**
 * Reads the two files the oss-licenses plugin generates (res/raw `third_party_license_metadata`
 * and `third_party_licenses`) and gives the libraries by name. FB1122: this replaces the library's
 * own screen (OssLicensesMenuActivity), which needs an AppCompat theme and crashed with the game's.
 *
 * Each metadata line is `offset:length name`: where the licence text of that library lies in the
 * licences file, in bytes.
 */
object OssLicenses {
    fun parse(metadata: String, licenses: ByteArray): List<OssLicense> =
        metadata.lineSequence().mapNotNull { line ->
            val space = line.indexOf(' ')
            if (space <= 0) return@mapNotNull null
            val range = line.substring(0, space).split(':')
            val offset = range.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val length = range.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            if (offset < 0 || length < 0 || offset + length > licenses.size) return@mapNotNull null
            OssLicense(line.substring(space + 1).trim(), String(licenses, offset, length, Charsets.UTF_8).trim())
        }.distinctBy { it.name }.sortedBy { it.name.lowercase() }.toList()

    /** The generated list of this build; empty when the files are missing. */
    fun load(context: Context): List<OssLicense> = runCatching {
        val metadata = context.resources.openRawResource(R.raw.third_party_license_metadata).use { it.readBytes().toString(Charsets.UTF_8) }
        val licenses = context.resources.openRawResource(R.raw.third_party_licenses).use { it.readBytes() }
        parse(metadata, licenses)
    }.getOrDefault(emptyList())
}
