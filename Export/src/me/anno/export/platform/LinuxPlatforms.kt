package me.anno.export.platform

import me.anno.io.AutoSaveable

class LinuxPlatforms : AutoSaveable() {
    var x64 = true
    var arm64 = true
    var arm32 = false
    val any get() = x64 or arm64 or arm32
}