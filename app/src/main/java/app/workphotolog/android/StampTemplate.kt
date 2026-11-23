package app.workphotolog.android

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
    PANEL("Panel"), LEDGER("Ledger"), PLAIN("Plain"), CLOCK("Clock"),
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
) {
    val displayName: String?
        get() = preset?.let { heading.ifBlank { it.label } }

    fun toJson(): String = JSONObject()
        .put("preset", preset?.name)
        .put("color", color.name)
        .put("heading", heading)
        .put("organization", organization)
        .put("worker", worker)
        .put("site", site)
        .put("showTime", showTime)
        .put("showNote", showNote)
        .put("showCoordinates", showCoordinates)
        .put("layout", layout.name)
        .put("position", position.name)
        .put("size", size.name)
        .put("textColor", textColor.name)
        .put("employeeNumber", employeeNumber)
        .put("address", address)
        .put("tags", tags)
        .put("showPhotoCode", showPhotoCode)
        .put("logoId", logoId)
        .put("logoSide", logoSide.name)
        .put("logoZoom", boundedLogoZoom(logoZoom).toDouble())
        .toString()

    companion object {
        val DEFAULT_COPY = StampTemplate(StampPreset.FIELD_NOTE)

        fun fromJson(value: String?): StampTemplate {
            if (value.isNullOrBlank()) return StampTemplate()
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
                logoId = (data.opt("logoId") as? String)?.takeIf { it.isNotBlank() },
                logoSide = data.enum<StampLogoSide>("logoSide") ?: StampLogoSide.LEFT,
                logoZoom = boundedLogoZoom(data.optDouble("logoZoom", 1.0).toFloat()),
            )
        }
    }
}

internal fun boundedLogoZoom(zoom: Float): Float = if (zoom.isFinite()) zoom.coerceIn(1f, 3f) else 1f

private inline fun <reified T : Enum<T>> JSONObject.enum(key: String): T? =
    enumValues<T>().firstOrNull { it.name == opt(key) }

private fun JSONObject.string(key: String): String = opt(key) as? String ?: ""
