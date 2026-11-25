package app.workphotolog.android

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

enum class StampPreset(val label: String) {
    FIELD_NOTE("Field note"),
    SITE_VISIT("Site visit"),
    COMPLETION("Completion"),
}

enum class StampColor(val label: String, val accent: Int, internal val background: Int) {
    OLIVE("Olive", 0xffd5e98c.toInt(), 0xf0262c21.toInt()),
    AMBER("Amber", 0xffffcb75.toInt(), 0xf032261b.toInt()),
    BLUE("Blue", 0xffa7d8ff.toInt(), 0xf01d2834.toInt()),
    COPPER("Copper", 0xffffb5a3.toInt(), 0xf039241f.toInt()),
    LILAC("Lilac", 0xffdec4ff.toInt(), 0xf02e2437.toInt()),
    JADE("Jade", 0xffa8ecd2.toInt(), 0xf01f312c.toInt()),
}

enum class StampLayout(val label: String) {
    PANEL("Panel"), LEDGER("Ledger"), PLAIN("Plain"), CLOCK("Clock"), TICKET("Ticket"),
}

enum class StampPosition(val label: String, internal val right: Boolean, internal val bottom: Boolean) {
    TOP_LEFT("Top left", false, false),
    TOP_RIGHT("Top right", true, false),
    BOTTOM_LEFT("Bottom left", false, true),
    BOTTOM_RIGHT("Bottom right", true, true),
}

enum class StampSize(val label: String, val scale: Float) {
    COMPACT("Compact", 0.82f), STANDARD("Standard", 1f), LARGE("Large", 1.20f),
}

enum class StampTextColor(val label: String, val argb: Int, internal val secondary: Int) {
    LIGHT("Light", 0xfff7f7ee.toInt(), 0xffe2e3d7.toInt()),
    WARM("Warm", 0xffffe6bb.toInt(), 0xfff5dfbf.toInt()),
    COOL("Cool", 0xffdceeff.toInt(), 0xffc9dce8.toInt()),
    GOLD("Gold", 0xffffd680.toInt(), 0xfff3d5a0.toInt()),
    MINT("Mint", 0xffbcf6d9.toInt(), 0xffb4d8c6.toInt()),
    BLUSH("Blush", 0xffffd1d9.toInt(), 0xffebc7ce.toInt()),
}

enum class StampLogoSide(val label: String) {
    LEFT("Left"), RIGHT("Right"),
}

enum class StampLogoShape(val label: String) {
    CIRCLE("Circle"), SQUARE("Square"), ROUNDED("Rounded"),
}

enum class StampNoteStyle(val label: String) {
    PLAIN("Plain"), HIGHLIGHT("Highlight"), ALERT("Alert"),
}

enum class StampBackground(val label: String) {
    PRESET("Preset"), SOLID("Solid color"), NONE("None"),
}

enum class StampTypography(val label: String, internal val family: String?) {
    SYSTEM("System", null),
    CONDENSED("Condensed", "sans-serif-condensed"),
    SERIF("Serif", "serif"),
    MONO("Mono", "monospace"),
}

enum class StampGroup(val label: String) {
    WHOLE("Whole stamp"), LOGO("Logo"), CLOCK("Clock"), DETAILS("Details"), MAP("Map"),
    TITLE("Title"), PROJECT("Project"), COMPANY("Company"), EMPLOYEE("Employee"), EMPLOYEE_NUMBER("Employee number"), SITE("Site"),
    ADDRESS("Address"), COORDINATES("Coordinates"), ALTITUDE("Altitude"), COMPASS("Compass"),
    WEATHER("Weather"), TAGS("Tags"), NOTE("Capture note"), STYLED_NOTE("Custom text"),
    CONTACT("Contact card"), PHOTO_CODE("Photo code"),
}

data class StampLayerStyle(
    val scale: Float = 1f,
    val opacity: Float = 1f,
    val colorArgb: Int? = null,
    val typography: StampTypography? = null,
) {
    internal fun bounded() = copy(scale = scale.boundedStampValue(0.5f, 2f), opacity = opacity.boundedStampValue(0f, 1f))
}

data class StampOffset(val x: Float = 0f, val y: Float = 0f) {
    internal fun bounded() = StampOffset(
        x.boundedStampValue(-1f, 1f, 0f),
        y.boundedStampValue(-1f, 1f, 0f),
    )

    internal fun toJson() = JSONObject().put("x", x.toDouble()).put("y", y.toDouble())
}

data class StampTemplate(
    val preset: StampPreset? = null,
    val color: StampColor = StampColor.OLIVE,
    val heading: String = "",
    val organization: String = "",
    val worker: String = "",
    val site: String = "",
    val showTime: Boolean = true,
    val showNote: Boolean = true,
    val showCoordinates: Boolean = false,
    val layout: StampLayout = StampLayout.PANEL,
    val position: StampPosition = StampPosition.BOTTOM_LEFT,
    val size: StampSize = StampSize.STANDARD,
    val textColor: StampTextColor = StampTextColor.LIGHT,
    val employeeNumber: String = "",
    val address: String = "",
    val tags: String = "",
    val showPhotoCode: Boolean = false,
    val logoId: String? = null,
    val logoSide: StampLogoSide = StampLogoSide.LEFT,
    val logoZoom: Float = 1f,
    val showLogo: Boolean = true,
    val showCompany: Boolean = true,
    val showTitle: Boolean = true,
    val showSite: Boolean = true,
    val showAddress: Boolean = true,
    val showTags: Boolean = true,
    val showEmployeeName: Boolean = true,
    val showEmployeeNumber: Boolean = true,
    val showBusinessCard: Boolean = false,
    val showStyledNote: Boolean = false,
    val showWeather: Boolean = false,
    val showMap: Boolean = false,
    val showAltitude: Boolean = false,
    val showCompass: Boolean = false,
    val logoShape: StampLogoShape = StampLogoShape.SQUARE,
    val businessCardName: String = "",
    val businessCardRole: String = "",
    val businessCardContact: String = "",
    val styledNote: String = "",
    val weather: String = "",
    val styledNoteStyle: StampNoteStyle = StampNoteStyle.PLAIN,
    val background: StampBackground = StampBackground.PRESET,
    val backgroundOpacity: Float = 1f,
    val primaryColorArgb: Int? = null,
    val accentColorArgb: Int? = null,
    val textColorArgb: Int? = null,
    val largeClock: Boolean? = null,
    val typography: StampTypography = StampTypography.SYSTEM,
    val textScale: Float = 1f,
    val clockScale: Float = 1f,
    val stampOffset: StampOffset = StampOffset(),
    val logoOffset: StampOffset = StampOffset(),
    val clockOffset: StampOffset = StampOffset(),
    val detailsOffset: StampOffset = StampOffset(),
    val addressParts: Set<AddressPart> = defaultAddressParts,
    val recordedAddress: Map<AddressPart, String> = emptyMap(),
    val layerEditing: Boolean = false,
    val layerOffsets: Map<StampGroup, StampOffset> = emptyMap(),
    val layerStyles: Map<StampGroup, StampLayerStyle> = emptyMap(),
    val mapZoom: Float = 1f,
    val showProject: Boolean? = null,
    val onlineMap: Boolean = true,
) {
    val displayName: String?
        get() = preset?.let { heading.ifBlank { it.label } }

    val usesLargeClock: Boolean
        get() = largeClock ?: (layout == StampLayout.CLOCK)

    val projectVisible: Boolean get() = showProject ?: showTitle

    fun offsetFor(group: StampGroup): StampOffset = when (group) {
        StampGroup.WHOLE -> stampOffset
        StampGroup.LOGO -> logoOffset
        StampGroup.CLOCK -> clockOffset
        StampGroup.DETAILS -> detailsOffset
        else -> layerOffsets[group] ?: defaultOffsetFor(group)
    }

    internal fun defaultOffsetFor(group: StampGroup): StampOffset = when (group) {
        StampGroup.WHOLE, StampGroup.LOGO, StampGroup.CLOCK, StampGroup.DETAILS -> StampOffset()
        else -> detailsOffset
    }

    fun withOffset(group: StampGroup, offset: StampOffset): StampTemplate {
        val bounded = offset.bounded()
        return when (group) {
            StampGroup.WHOLE -> copy(stampOffset = bounded)
            StampGroup.LOGO -> copy(logoOffset = bounded)
            StampGroup.CLOCK -> copy(clockOffset = bounded)
            StampGroup.DETAILS -> copy(detailsOffset = bounded)
            else -> copy(layerOffsets = if (bounded == detailsOffset) layerOffsets - group else layerOffsets + (group to bounded))
        }
    }

    fun styleFor(group: StampGroup): StampLayerStyle = layerStyles[group] ?: StampLayerStyle()

    fun withLayerStyle(group: StampGroup, style: StampLayerStyle): StampTemplate {
        val value = style.bounded()
        return copy(layerStyles = if (value == StampLayerStyle()) layerStyles - group else layerStyles + (group to value))
    }

    fun toJson(): String = JSONObject()
        .put("preset", preset?.name)
        .put("color", color.name)
        .put("heading", heading.boundedStampText())
        .put("organization", organization.boundedStampText())
        .put("worker", worker.boundedStampText())
        .put("site", site.boundedStampText())
        .put("showTime", showTime)
        .put("showNote", showNote)
        .put("showCoordinates", showCoordinates)
        .put("layout", layout.name)
        .put("position", position.name)
        .put("size", size.name)
        .put("textColor", textColor.name)
        .put("employeeNumber", employeeNumber.boundedStampText())
        .put("address", address.boundedStampText())
        .put("tags", tags.boundedStampText())
        .put("showPhotoCode", showPhotoCode)
        .put("logoId", logoId?.takeIf(::knownStampLogoId))
        .put("logoSide", logoSide.name)
        .put("logoZoom", boundedLogoZoom(logoZoom).toDouble())
        .put("showLogo", showLogo)
        .put("showCompany", showCompany)
        .put("showTitle", showTitle)
        .put("showProject", showProject)
        .put("showSite", showSite)
        .put("showAddress", showAddress)
        .put("addressParts", JSONArray(addressParts.map { it.name }))
        .put("recordedAddress", JSONObject(recordedAddress.mapKeys { it.key.name }.mapValues { it.value.boundedStampText() }))
        .put("showTags", showTags)
        .put("showEmployeeName", showEmployeeName)
        .put("showEmployeeNumber", showEmployeeNumber)
        .put("showBusinessCard", showBusinessCard)
        .put("showStyledNote", showStyledNote)
        .put("showWeather", showWeather)
        .put("showMap", showMap)
        .put("onlineMap", onlineMap)
        .put("showAltitude", showAltitude)
        .put("showCompass", showCompass)
        .put("logoShape", logoShape.name)
        .put("businessCardName", businessCardName.boundedStampText())
        .put("businessCardRole", businessCardRole.boundedStampText())
        .put("businessCardContact", businessCardContact.boundedStampText())
        .put("styledNote", styledNote.boundedStampText())
        .put("weather", weather.boundedStampText())
        .put("styledNoteStyle", styledNoteStyle.name)
        .put("background", background.name)
        .put("backgroundOpacity", backgroundOpacity.boundedStampValue(0f, 1f).toDouble())
        .put("primaryColorArgb", primaryColorArgb)
        .put("accentColorArgb", accentColorArgb)
        .put("textColorArgb", textColorArgb)
        .put("largeClock", largeClock)
        .put("typography", typography.name)
        .put("textScale", textScale.boundedStampValue(0.75f, 1.5f).toDouble())
        .put("clockScale", clockScale.boundedStampValue(0.75f, 1.6f).toDouble())
        .put("stampOffset", stampOffset.bounded().toJson())
        .put("logoOffset", logoOffset.bounded().toJson())
        .put("clockOffset", clockOffset.bounded().toJson())
        .put("detailsOffset", detailsOffset.bounded().toJson())
        .put("layerEditing", layerEditing)
        .put("layerOffsets", JSONObject(layerOffsets.mapKeys { it.key.name }.mapValues { it.value.bounded().toJson() }))
        .put("layerStyles", JSONObject(layerStyles.mapKeys { it.key.name }.mapValues { (_, style) ->
            val value = style.bounded()
            JSONObject().put("scale", value.scale.toDouble()).put("opacity", value.opacity.toDouble())
                .put("color", value.colorArgb)
                .put("typography", value.typography?.name)
        }))
        .put("mapZoom", mapZoom.boundedStampValue(0.6f, 2f).toDouble())
        .toString()

    companion object {
        val DEFAULT_COPY = StampTemplate(StampPreset.FIELD_NOTE)

        fun fromJson(value: String?): StampTemplate {
            if (value == null || value.length > MAX_STAMP_JSON_LENGTH || value.isBlank()) return StampTemplate()
            val data = try {
                JSONObject(value)
            } catch (_: JSONException) {
                return StampTemplate()
            }
            return StampTemplate(
                preset = data.enum<StampPreset>("preset"),
                color = data.enum<StampColor>("color") ?: StampColor.OLIVE,
                heading = data.string("heading"),
                organization = data.string("organization"),
                worker = data.string("worker"),
                site = data.string("site"),
                showTime = data.optBoolean("showTime", true),
                showNote = data.optBoolean("showNote", true),
                showCoordinates = data.optBoolean("showCoordinates", false),
                layout = data.enum<StampLayout>("layout") ?: StampLayout.PANEL,
                position = data.enum<StampPosition>("position") ?: StampPosition.BOTTOM_LEFT,
                size = data.enum<StampSize>("size") ?: StampSize.STANDARD,
                textColor = data.enum<StampTextColor>("textColor") ?: StampTextColor.LIGHT,
                employeeNumber = data.string("employeeNumber"),
                address = data.string("address"),
                tags = data.string("tags"),
                showPhotoCode = data.optBoolean("showPhotoCode", false),
                logoId = (data.opt("logoId") as? String)?.takeIf(::knownStampLogoId),
                logoSide = data.enum<StampLogoSide>("logoSide") ?: StampLogoSide.LEFT,
                logoZoom = boundedLogoZoom(data.optDouble("logoZoom", 1.0).toFloat()),
                showLogo = data.optBoolean("showLogo", true),
                showCompany = data.optBoolean("showCompany", true),
                showTitle = data.optBoolean("showTitle", true),
                showProject = if (data.isNull("showProject")) null else data.optBoolean("showProject"),
                showSite = data.optBoolean("showSite", true),
                showAddress = data.optBoolean("showAddress", true),
                addressParts = data.optJSONArray("addressParts")?.let { parts ->
                    val names = (0 until parts.length()).map { parts.optString(it) }.toSet()
                    AddressPart.entries.filterTo(mutableSetOf()) { it.name in names }
                } ?: defaultAddressParts,
                recordedAddress = data.optJSONObject("recordedAddress")?.let { parts ->
                    AddressPart.entries.mapNotNull { part ->
                        parts.string(part.name).takeIf(String::isNotBlank)?.let { part to it }
                    }.toMap()
                }.orEmpty(),
                showTags = data.optBoolean("showTags", true),
                showEmployeeName = data.optBoolean("showEmployeeName", true),
                showEmployeeNumber = data.optBoolean("showEmployeeNumber", true),
                showBusinessCard = data.optBoolean("showBusinessCard", false),
                showStyledNote = data.optBoolean("showStyledNote", false),
                showWeather = data.optBoolean("showWeather", false),
                showMap = data.optBoolean("showMap", false),
                onlineMap = data.optBoolean("onlineMap", true),
                showAltitude = data.optBoolean("showAltitude", false),
                showCompass = data.optBoolean("showCompass", false),
                logoShape = data.enum<StampLogoShape>("logoShape") ?: StampLogoShape.SQUARE,
                businessCardName = data.string("businessCardName"),
                businessCardRole = data.string("businessCardRole"),
                businessCardContact = data.string("businessCardContact"),
                styledNote = data.string("styledNote"),
                weather = data.string("weather"),
                styledNoteStyle = data.enum<StampNoteStyle>("styledNoteStyle") ?: StampNoteStyle.PLAIN,
                background = data.enum<StampBackground>("background") ?: StampBackground.PRESET,
                backgroundOpacity = data.optDouble("backgroundOpacity", 1.0).toFloat().boundedStampValue(0f, 1f),
                primaryColorArgb = data.color("primaryColorArgb"),
                accentColorArgb = data.color("accentColorArgb"),
                textColorArgb = data.color("textColorArgb"),
                largeClock = data.opt("largeClock") as? Boolean,
                typography = data.enum<StampTypography>("typography") ?: StampTypography.SYSTEM,
                textScale = data.optDouble("textScale", 1.0).toFloat().boundedStampValue(0.75f, 1.5f),
                clockScale = data.optDouble("clockScale", 1.0).toFloat().boundedStampValue(0.75f, 1.6f),
                stampOffset = data.offset("stampOffset"),
                logoOffset = data.offset("logoOffset"),
                clockOffset = data.offset("clockOffset"),
                detailsOffset = data.offset("detailsOffset"),
                layerEditing = data.optBoolean("layerEditing"),
                layerOffsets = data.optJSONObject("layerOffsets")?.let { offsets ->
                    StampGroup.entries.filter { offsets.optJSONObject(it.name) != null }
                        .associateWith { offsets.offset(it.name) }
                }.orEmpty(),
                layerStyles = data.optJSONObject("layerStyles")?.let { styles ->
                    StampGroup.entries.mapNotNull { group ->
                        styles.optJSONObject(group.name)?.let {
                            group to StampLayerStyle(
                                it.optDouble("scale", 1.0).toFloat(),
                                it.optDouble("opacity", 1.0).toFloat(),
                                it.color("color"),
                                StampTypography.entries.firstOrNull { font -> font.name == it.optString("typography") },
                            ).bounded()
                        }
                    }.toMap()
                }.orEmpty(),
                mapZoom = data.optDouble("mapZoom", 1.0).toFloat().boundedStampValue(0.6f, 2f),
            )
        }
    }
}

private fun JSONObject.color(key: String): Int? {
    val value = (opt(key) as? Number)?.toDouble() ?: return null
    return value.toInt().takeIf { it.toDouble() == value }
}

private fun JSONObject.offset(key: String): StampOffset {
    val coordinates = optJSONObject(key) ?: return StampOffset()
    return StampOffset(
        coordinates.optDouble("x", 0.0).toFloat(),
        coordinates.optDouble("y", 0.0).toFloat(),
    ).bounded()
}

internal fun Float.boundedStampValue(minimum: Float, maximum: Float, fallback: Float = 1f): Float =
    if (isFinite()) coerceIn(minimum, maximum) else fallback

internal fun boundedLogoZoom(zoom: Float): Float = if (zoom.isFinite()) zoom.coerceIn(1f, 3f) else 1f

private inline fun <reified T : Enum<T>> JSONObject.enum(key: String): T? =
    enumValues<T>().firstOrNull { it.name == opt(key) }

private fun JSONObject.string(key: String): String = (opt(key) as? String)?.boundedStampText().orEmpty()

private const val MAX_STAMP_JSON_LENGTH = 65_536
private const val MAX_STAMP_TEXT_LENGTH = 512

internal fun String.boundedStampText(): String {
    if (length <= MAX_STAMP_TEXT_LENGTH) return this
    val limit = if (this[MAX_STAMP_TEXT_LENGTH - 1].isHighSurrogate()) MAX_STAMP_TEXT_LENGTH - 1
        else MAX_STAMP_TEXT_LENGTH
    return substring(0, limit)
}
