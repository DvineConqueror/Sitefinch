package app.workphotolog.android

import android.content.Context
import android.location.Address
import kotlinx.coroutines.withTimeoutOrNull

enum class AddressPart(val label: String) {
    BUILDING_NUMBER("Building number"),
    BUILDING("Building name"),
    STREET("Street"),
    NEIGHBORHOOD("Barangay / neighborhood"),
    CITY("Municipality / city"),
    PROVINCE("Province / county"),
    REGION("Region / state"),
    POSTAL_CODE("Postal code"),
    COUNTRY("Country"),
}

internal val defaultAddressParts = setOf(AddressPart.CITY, AddressPart.PROVINCE)

internal fun Address.stampParts(precise: Boolean): Map<AddressPart, String> {
    val province = if (countryCode.equals("PH", ignoreCase = true)) subAdminArea ?: adminArea else subAdminArea
    return mapOf(
        AddressPart.BUILDING_NUMBER to subThoroughfare.takeIf { precise },
        AddressPart.BUILDING to premises.takeIf { precise },
        AddressPart.STREET to thoroughfare.takeIf { precise },
        AddressPart.NEIGHBORHOOD to subLocality.takeIf { precise },
        AddressPart.CITY to locality,
        AddressPart.PROVINCE to province,
        AddressPart.REGION to adminArea,
        AddressPart.POSTAL_CODE to postalCode.takeIf { precise },
        AddressPart.COUNTRY to countryName,
    ).mapNotNull { (part, value) ->
        value?.trim()?.takeIf(String::isNotEmpty)?.boundedStampText()?.let { part to it }
    }.toMap()
}

internal suspend fun resolveStampAddress(context: Context, fix: LocationFix?): Map<AddressPart, String> {
    if (fix == null) return emptyMap()
    return withTimeoutOrNull(4_000) {
        findAddresses(context, query = null, fix = fix)
            .map { it.stampParts(fix.precision == "precise") }
            .maxByOrNull { parts -> parts.size + defaultAddressParts.count { it in parts } * 3 }
    }.orEmpty()
}

internal fun StampTemplate.displayAddress(includeRecorded: Boolean): String {
    if (!showAddress) return ""
    if (!includeRecorded || recordedAddress.isEmpty()) return address.trim()
    if (addressParts.isEmpty()) return ""
    val street = listOfNotNull(
        recordedAddress[AddressPart.BUILDING_NUMBER].takeIf { AddressPart.BUILDING_NUMBER in addressParts },
        recordedAddress[AddressPart.STREET].takeIf { AddressPart.STREET in addressParts },
    ).joinToString(" ")
    return AddressPart.entries.mapNotNull { part ->
        when {
            part == AddressPart.BUILDING_NUMBER -> street.takeIf(String::isNotEmpty)
            part == AddressPart.STREET -> null
            part in addressParts -> recordedAddress[part]
            else -> null
        }
    }.distinctBy { it.lowercase() }.joinToString(", ").ifBlank { address.trim() }.boundedStampText()
}
