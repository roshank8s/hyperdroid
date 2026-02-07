package com.hyperdroid.model

enum class OSType(val label: String, val isDebianBased: Boolean) {
    DEBIAN("Debian", true),
    UBUNTU("Ubuntu", true),
    ALPINE("Alpine", false),
    FEDORA("Fedora", false),
    ARCH("Arch", false),
    CUSTOM("Custom", false)
}
