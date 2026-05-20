package com.catchpro.app.data.region

data class KoreaProvince(
    val name: String,
    val fullName: String,
    val districts: List<String>,
    val aliases: Set<String>,
)

data class KoreaAdministrativeSelection(
    val province: KoreaProvince,
    val district: String,
    val town: String? = null,
) {
    val isWholeDistrict: Boolean
        get() = town == null

    val displayLabel: String
        get() = when {
            province.name == "세종" && isWholeDistrict -> province.fullName
            province.name == "세종" -> "${province.fullName} ${town.orEmpty()}".trim()
            isWholeDistrict -> "${province.fullName} $district"
            else -> "${province.fullName} $district ${town.orEmpty()}".trim()
        }

    val legacyKeyword: String
        get() = when {
            province.name == "세종" && isWholeDistrict -> "세종"
            province.name == "세종" -> "세종 ${town.orEmpty()}".trim()
            isWholeDistrict -> "${province.name} $district"
            else -> "${province.name} $district ${town.orEmpty()}".trim()
        }
}

object KoreaAdministrativeAreas {
    private val provinceAliasOverrides: Map<String, String> = mapOf(
        "서울특별시" to "서울",
        "부산광역시" to "부산",
        "대구광역시" to "대구",
        "인천광역시" to "인천",
        "광주광역시" to "광주",
        "대전광역시" to "대전",
        "울산광역시" to "울산",
        "세종특별자치시" to "세종",
        "경기도" to "경기",
        "강원특별자치도" to "강원",
        "충청북도" to "충북",
        "충청남도" to "충남",
        "전북특별자치도" to "전북",
        "전라남도" to "전남",
        "경상북도" to "경북",
        "경상남도" to "경남",
        "제주특별자치도" to "제주",
    )

    val provinces: List<KoreaProvince> = KoreaAdministrativeTownData.townsByProvinceAndDistrict.map { (fullName, districts) ->
        val shortName = provinceAliasOverrides[fullName] ?: fullName
        KoreaProvince(
            name = shortName,
            fullName = fullName,
            districts = districts.keys.toList(),
            aliases = provinceAliases(shortName, fullName),
        )
    }

    private val provinceByShortName: Map<String, KoreaProvince> = provinces.associateBy { it.name }
    private val provinceByFullName: Map<String, KoreaProvince> = provinces.associateBy { it.fullName }

    private val districtSelections: List<KoreaAdministrativeSelection> = provinces.flatMap { province ->
        province.districts.map { district ->
            KoreaAdministrativeSelection(
                province = province,
                district = district,
            )
        }
    }

    private val townSelections: List<KoreaAdministrativeSelection> = provinces.flatMap { province ->
        province.districts.flatMap { district ->
            townsForDistrict(province.name, district).map { town ->
                KoreaAdministrativeSelection(
                    province = province,
                    district = district,
                    town = town,
                )
            }
        }
    }
    private val townOccurrenceCounts: Map<String, Int> = townSelections
        .mapNotNull { it.town?.normalizeRegionValue() }
        .groupingBy { it }
        .eachCount()
    private val allDistrictNames: Set<String> = provinces
        .flatMap { it.districts }
        .map { district -> district.normalizeRegionValue() }
        .toSet()

    private val allSelections: List<KoreaAdministrativeSelection> = districtSelections + townSelections
    private val keywordOrder: List<String> = allSelections.map { it.displayLabel }
    private val selectionByCanonicalKeyword: Map<String, KoreaAdministrativeSelection> = allSelections.associateBy {
        it.displayLabel.normalizeRegionValue()
    }

    private val aliasToCanonicalKeyword: Map<String, String> = buildMap {
        allSelections.forEach { selection ->
            val canonical = selection.displayLabel.normalizeRegionValue()
            put(canonical, selection.displayLabel)
            put(selection.legacyKeyword.normalizeRegionValue(), selection.displayLabel)
            if (selection.isWholeDistrict) {
                put("${selection.displayLabel} 전체".normalizeRegionValue(), selection.displayLabel)
                put("${selection.legacyKeyword} 전체".normalizeRegionValue(), selection.displayLabel)
            }
        }
        put("세종".normalizeRegionValue(), "세종특별자치시")
        put("세종시".normalizeRegionValue(), "세종특별자치시")
    }

    val knownKeywords: Set<String> = keywordOrder.toSet()

    fun findProvince(name: String): KoreaProvince? {
        return provinceByShortName[name] ?: provinceByFullName[name]
    }

    fun composeKeyword(
        provinceName: String,
        districtName: String,
    ): String {
        val province = findProvince(provinceName) ?: return listOf(provinceName, districtName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
        return selectionForParts(
            province = province,
            districtName = districtName,
            townName = null,
        )?.displayLabel ?: listOf(province.fullName, districtName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
    }

    fun composeTownKeyword(
        provinceName: String,
        districtName: String,
        townName: String,
    ): String {
        val province = findProvince(provinceName) ?: return listOf(provinceName, districtName, townName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
        return selectionForParts(
            province = province,
            districtName = districtName,
            townName = townName,
        )?.displayLabel ?: listOf(province.fullName, districtName, townName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
    }

    fun canonicalKeywordOrNull(keyword: String): String? {
        return aliasToCanonicalKeyword[keyword.normalizeRegionValue()]
    }

    fun isAdministrativeKeyword(keyword: String): Boolean {
        return canonicalKeywordOrNull(keyword) != null
    }

    fun provinceForKeyword(keyword: String): KoreaProvince? {
        return selectionForKeywordOrNull(keyword)?.province
    }

    fun selectionForKeywordOrNull(keyword: String): KoreaAdministrativeSelection? {
        val canonical = aliasToCanonicalKeyword[keyword.normalizeRegionValue()] ?: return null
        return selectionByCanonicalKeyword[canonical.normalizeRegionValue()]
    }

    fun townsForDistrict(
        provinceName: String,
        districtName: String,
    ): List<String> {
        val province = findProvince(provinceName) ?: return emptyList()
        return KoreaAdministrativeTownData.townsByProvinceAndDistrict[province.fullName]
            ?.get(districtName)
            .orEmpty()
    }

    fun sortKeywords(keywords: Collection<String>): List<String> {
        val canonicalRequested = keywords.mapNotNull(::canonicalKeywordOrNull).toSet()
        val knownSorted = keywordOrder.filter(canonicalRequested::contains)
        val unknownSorted = keywords
            .map(String::trim)
            .filter(String::isNotBlank)
            .filterNot(::isAdministrativeKeyword)
            .toSet()
            .sorted()
        return knownSorted + unknownSorted
    }

    fun coverageLabelForKeyword(keyword: String): String {
        val selection = selectionForKeywordOrNull(keyword) ?: return keyword.trim()
        if (!selection.isWholeDistrict) {
            return selection.displayLabel
        }

        val townCount = townsForDistrict(selection.province.name, selection.district).size
        return if (townCount > 0) {
            "${selection.displayLabel} 전체 (${townCount}개 동/읍/면 포함)"
        } else {
            "${selection.displayLabel} 전체"
        }
    }

    fun coverageLabelsForKeywords(keywords: Collection<String>): List<String> {
        return sortKeywords(keywords).map(::coverageLabelForKeyword)
    }

    fun coverageDetailsForKeyword(keyword: String): List<String> {
        val selection = selectionForKeywordOrNull(keyword) ?: return emptyList()
        if (!selection.isWholeDistrict) return emptyList()
        return townsForDistrict(selection.province.name, selection.district)
    }

    fun matchesKeyword(
        keyword: String,
        haystack: String,
    ): Boolean {
        val normalizedKeyword = keyword.normalizeRegionValue()
        val normalizedHaystack = haystack.normalizeRegionValue()

        if (normalizedKeyword.isBlank() || normalizedHaystack.isBlank()) return false

        val selection = selectionForKeywordOrNull(normalizedKeyword)
        if (selection == null) {
            return normalizedHaystack.contains(normalizedKeyword, ignoreCase = true)
        }

        val hasProvince = selection.province.aliases.any { alias ->
            normalizedHaystack.contains(alias.normalizeRegionValue(), ignoreCase = true)
        }
        val hasDistrict = selection.province.name == "세종" ||
            normalizedHaystack.contains(selection.district.normalizeRegionValue(), ignoreCase = true)
        val hasTown = selection.town == null ||
            normalizedHaystack.contains(selection.town.normalizeRegionValue(), ignoreCase = true)

        if (hasProvince && hasDistrict && hasTown) {
            return true
        }

        val fallbackTownMatch = when {
            selection.town != null -> {
                val normalizedTown = selection.town.normalizeRegionValue()
                if (
                    townOccurrenceCounts[normalizedTown] == 1 &&
                    normalizedHaystack.contains(normalizedTown, ignoreCase = true)
                ) {
                    selection.town
                } else {
                    null
                }
            }

            else -> townsForDistrict(selection.province.name, selection.district)
                .firstOrNull { town ->
                    val normalizedTown = town.normalizeRegionValue()
                    townOccurrenceCounts[normalizedTown] == 1 &&
                        normalizedHaystack.contains(normalizedTown, ignoreCase = true)
                }
        } ?: return false

        val containsConflictingProvince = provinces.any { province ->
            province.name != selection.province.name &&
                province.aliases.any { alias ->
                    normalizedHaystack.contains(alias.normalizeRegionValue(), ignoreCase = true)
                }
        }
        if (containsConflictingProvince) return false

        val containsConflictingDistrict = allDistrictNames.any { district ->
            district != selection.district.normalizeRegionValue() &&
                normalizedHaystack.contains(district, ignoreCase = true)
        }
        if (containsConflictingDistrict) return false

        return normalizedHaystack.contains(fallbackTownMatch.normalizeRegionValue(), ignoreCase = true)
    }

    private fun selectionForParts(
        province: KoreaProvince,
        districtName: String,
        townName: String?,
    ): KoreaAdministrativeSelection? {
        return allSelections.firstOrNull { selection ->
            selection.province.name == province.name &&
                selection.district == districtName &&
                selection.town == townName
        }
    }

    private fun String.normalizeRegionValue(): String {
        return replace(Regex("\\s+"), " ").trim()
    }

    private fun provinceAliases(
        shortName: String,
        fullName: String,
    ): Set<String> {
        return buildSet {
            add(shortName)
            add(fullName)
            when {
                fullName.endsWith("특별시") || fullName.endsWith("광역시") || fullName.endsWith("특별자치시") -> {
                    add("${shortName}시")
                }
                fullName.endsWith("특별자치도") || fullName.endsWith("도") -> {
                    add("${shortName}도")
                }
            }
        }
    }
}
