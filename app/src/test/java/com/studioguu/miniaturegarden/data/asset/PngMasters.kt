package com.studioguu.miniaturegarden.data.asset

import java.io.File

/**
 * Where a picture of the app is kept as PNG (#fb-1111). The app carries its pictures as lossless
 * WebP, which neither `javax.imageio` nor [TestPng] can read; their PNG masters, pixel for pixel
 * the same (tools/webp_art.py checks), are under art/png/ laid out as under app/src/main/.
 */
object PngMasters {
    private val SEP = File.separator

    /**
     * The PNG master of [appFile], a picture under app/src/main/ named with .png or .webp; any
     * other file is given back as it is.
     */
    fun of(appFile: File): File {
        val path = appFile.canonicalPath
        val marker = "${SEP}app${SEP}src${SEP}main${SEP}"
        val at = path.indexOf(marker)
        if (at < 0) return appFile
        val relative = path.substring(at + marker.length).substringBeforeLast('.') + ".png"
        return File(File(path.substring(0, at), "art${SEP}png"), relative)
    }

    /** The WebP [appFile] stands for in the app: the same name, ending in .webp. */
    fun webp(appFile: File): File = File(appFile.parentFile, appFile.nameWithoutExtension + ".webp")
}
