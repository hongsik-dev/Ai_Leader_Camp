package com.catchpro.app.observation

fun String.toObservationPackageFilters(): List<String> = lineSequence()
    .flatMap { line -> line.split(',').asSequence() }
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()
    .toList()

fun List<String>.matchesObservedPackage(packageName: String): Boolean {
    if (isEmpty()) return true
    return any { filter -> filter.matchesPackage(packageName) }
}

private fun String.matchesPackage(packageName: String): Boolean {
    val normalized = trim()
    if (normalized.isBlank()) return false

    return if (normalized.endsWith("*")) {
        packageName.startsWith(normalized.removeSuffix("*"), ignoreCase = true)
    } else {
        packageName.equals(normalized, ignoreCase = true) ||
            packageName.startsWith("$normalized.", ignoreCase = true) ||
            packageName.contains(normalized, ignoreCase = true)
    }
}
