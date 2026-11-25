package app.workphotolog.android

internal enum class ExploreCategory(val label: String) {
    ALL("All"),
    DAILY("Daily work"),
    INSPECTION("Inspection"),
    ASSETS("Assets"),
    HANDOVER("Handover"),
    SHIPPING("Shipping"),
    ATTENDANCE("Attendance"),
    SECURITY("Security"),
    RETAIL("Retail"),
}

internal data class StarterTemplate(
    val title: String,
    val description: String,
    val tag: String,
    val template: StampTemplate,
    val category: ExploreCategory? = null,
    val sampleSubject: String? = null,
) {
    fun matches(query: String): Boolean = query.trim().split(Regex("\\s+")).all { term ->
        title.contains(term, ignoreCase = true) ||
            description.contains(term, ignoreCase = true) ||
            category?.label?.contains(term, ignoreCase = true) == true
    }
}

internal val starterTemplates = listOf(
    StarterTemplate("Clean photo", "Capture without a stamp", "templateClean", StampTemplate()),
    StarterTemplate("Field note", "Record an observation", "templateFieldNote", StampTemplate.DEFAULT_COPY),
    StarterTemplate(
        "Time & place", "Show time and address", "templateClock",
        StampTemplate(StampPreset.SITE_VISIT, StampColor.JADE, heading = "Time & place", layout = StampLayout.CLOCK),
    ),
    StarterTemplate(
        "Site visit", "Document a site check", "templateSiteVisit",
        StampTemplate(StampPreset.SITE_VISIT, StampColor.BLUE, layout = StampLayout.LEDGER),
    ),
    StarterTemplate(
        "Completion", "Mark work finished", "templateCompletion",
        StampTemplate(StampPreset.COMPLETION, StampColor.AMBER, layout = StampLayout.PLAIN),
    ),
    StarterTemplate(
        "Inspection", "Record a finding", "templateInspection",
        StampTemplate(StampPreset.SITE_VISIT, StampColor.JADE, heading = "Inspection", layout = StampLayout.LEDGER),
    ),
    StarterTemplate(
        "Arrival", "Log a crew’s arrival", "templateArrival",
        StampTemplate(StampPreset.SITE_VISIT, StampColor.AMBER, heading = "Arrival", size = StampSize.COMPACT),
    ),
    StarterTemplate(
        "Safety round", "Document a safety check", "templateSafety",
        StampTemplate(StampPreset.FIELD_NOTE, StampColor.COPPER, heading = "Safety round"),
    ),
    StarterTemplate(
        "Asset record", "Identify equipment on site", "templateAsset",
        StampTemplate(StampPreset.SITE_VISIT, StampColor.LILAC, heading = "Asset record", showPhotoCode = true),
    ),
    StarterTemplate(
        "Service visit", "Log a maintenance call", "templateService",
        StampTemplate(StampPreset.COMPLETION, StampColor.JADE, heading = "Service visit", layout = StampLayout.LEDGER),
    ),
    StarterTemplate(
        "Work report", "Capture a progress update", "templateReport",
        StampTemplate(StampPreset.FIELD_NOTE, StampColor.BLUE, heading = "Work report", layout = StampLayout.LEDGER),
    ),
    StarterTemplate(
        "Cleaning", "Record a finished clean", "templateCleaning",
        StampTemplate(StampPreset.COMPLETION, StampColor.LILAC, heading = "Cleaning", layout = StampLayout.PLAIN),
    ),
)

internal val exploreTemplates = listOf(
    StarterTemplate(
        "Shift log", "Clock a crew onto the site", "exploreShiftLog",
        StampTemplate(
            StampPreset.SITE_VISIT, StampColor.JADE, heading = "Shift log", layout = StampLayout.CLOCK,
            showNote = false,
        ),
        ExploreCategory.DAILY,
    ),
    StarterTemplate(
        "Progress journal", "Record a daily milestone and note", "exploreProgress",
        StampTemplate(
            StampPreset.FIELD_NOTE, StampColor.BLUE, heading = "Progress journal", layout = StampLayout.LEDGER,
            size = StampSize.LARGE, position = StampPosition.BOTTOM_RIGHT,
        ),
        ExploreCategory.DAILY,
    ),
    StarterTemplate(
        "Hazard report", "Make a safety note easy to spot", "exploreHazard",
        StampTemplate(
            StampPreset.FIELD_NOTE, StampColor.COPPER, heading = "Hazard report", layout = StampLayout.PANEL,
            showStyledNote = true, styledNoteStyle = StampNoteStyle.ALERT,
        ),
        ExploreCategory.INSPECTION,
    ),
    StarterTemplate(
        "Condition survey", "Record an inspection and site conditions", "exploreCondition",
        StampTemplate(
            StampPreset.SITE_VISIT, StampColor.LILAC, heading = "Condition survey", layout = StampLayout.LEDGER,
            showWeather = true, showPhotoCode = true,
        ),
        ExploreCategory.INSPECTION,
    ),
    StarterTemplate(
        "Equipment audit", "Match an asset to its photo code", "exploreEquipment",
        StampTemplate(
            StampPreset.FIELD_NOTE, StampColor.OLIVE, heading = "Equipment audit", layout = StampLayout.PLAIN,
            showPhotoCode = true, textColor = StampTextColor.MINT,
        ),
        ExploreCategory.ASSETS,
    ),
    StarterTemplate(
        "Utility locate", "Show a recorded position and direction", "exploreUtility",
        StampTemplate(
            StampPreset.SITE_VISIT, StampColor.BLUE, heading = "Utility locate", layout = StampLayout.LEDGER,
            showCoordinates = true, showMap = true, showCompass = true,
        ),
        ExploreCategory.ASSETS,
    ),
    StarterTemplate(
        "Handover brief", "Include a person and contact for follow-up", "exploreHandover",
        StampTemplate(
            StampPreset.COMPLETION, StampColor.JADE, heading = "Handover brief", layout = StampLayout.PANEL,
            showBusinessCard = true, position = StampPosition.TOP_RIGHT,
        ),
        ExploreCategory.HANDOVER,
    ),
    StarterTemplate(
        "Closeout record", "File a finished result with its photo code", "exploreCloseout",
        StampTemplate(
            StampPreset.COMPLETION, StampColor.AMBER, heading = "Closeout record", layout = StampLayout.PLAIN,
            showPhotoCode = true, position = StampPosition.TOP_LEFT,
        ),
        ExploreCategory.HANDOVER,
    ),
    StarterTemplate(
        "Parcel delivery", "Record a parcel handoff with its local photo code", "exploreParcel",
        StampTemplate(
            StampPreset.COMPLETION, StampColor.BLUE, heading = "Parcel delivery",
            layout = StampLayout.PANEL, showPhotoCode = true,
        ),
        ExploreCategory.SHIPPING,
    ),
    StarterTemplate(
        "Loading dock", "Log the time and condition of a loaded shipment", "exploreDock",
        StampTemplate(
            StampPreset.SITE_VISIT, StampColor.AMBER, heading = "Loading dock", layout = StampLayout.CLOCK,
            position = StampPosition.BOTTOM_RIGHT, typography = StampTypography.CONDENSED,
        ),
        ExploreCategory.SHIPPING,
    ),
    StarterTemplate(
        "Goods received", "Document received cartons and visible package damage", "exploreGoodsReceived",
        StampTemplate(
            StampPreset.FIELD_NOTE, StampColor.JADE, heading = "Goods received", layout = StampLayout.LEDGER,
            showPhotoCode = true, textColor = StampTextColor.MINT,
        ),
        ExploreCategory.SHIPPING,
    ),
    StarterTemplate(
        "Clock in", "Capture the start of a shift with device time", "exploreClockIn",
        StampTemplate(
            StampPreset.SITE_VISIT, StampColor.JADE, heading = "Clock in", layout = StampLayout.CLOCK,
            showNote = false, size = StampSize.COMPACT,
        ),
        ExploreCategory.ATTENDANCE,
    ),
    StarterTemplate(
        "Clock out", "Capture the end of a shift with a short handover note", "exploreClockOut",
        StampTemplate(
            StampPreset.COMPLETION, StampColor.LILAC, heading = "Clock out", layout = StampLayout.CLOCK,
            position = StampPosition.BOTTOM_RIGHT, textColor = StampTextColor.BLUSH,
        ),
        ExploreCategory.ATTENDANCE,
    ),
    StarterTemplate(
        "Crew check-in", "Pair a crew photo with the employee and job details you enter", "exploreCrewCheckIn",
        StampTemplate(
            StampPreset.FIELD_NOTE, StampColor.OLIVE, heading = "Crew check-in", layout = StampLayout.LEDGER,
            typography = StampTypography.CONDENSED, showPhotoCode = true,
        ),
        ExploreCategory.ATTENDANCE,
    ),
    StarterTemplate(
        "Patrol checkpoint", "Record the time, post, and a patrol note", "explorePatrol",
        StampTemplate(
            StampPreset.SITE_VISIT, StampColor.BLUE, heading = "Patrol checkpoint", layout = StampLayout.LEDGER,
            largeClock = true, size = StampSize.COMPACT,
        ),
        ExploreCategory.SECURITY,
    ),
    StarterTemplate(
        "Gate log", "Document entry or exit at a gate", "exploreGateLog",
        StampTemplate(
            StampPreset.SITE_VISIT, StampColor.OLIVE, heading = "Gate log", layout = StampLayout.CLOCK,
            showPhotoCode = true, typography = StampTypography.CONDENSED,
        ),
        ExploreCategory.SECURITY,
    ),
    StarterTemplate(
        "Incident note", "Add an observation and highlight text you enter", "exploreIncident",
        StampTemplate(
            StampPreset.FIELD_NOTE, StampColor.COPPER, heading = "Incident note", showPhotoCode = true,
            showStyledNote = true, styledNoteStyle = StampNoteStyle.ALERT, position = StampPosition.TOP_LEFT,
        ),
        ExploreCategory.SECURITY,
    ),
    StarterTemplate(
        "Quality check", "Document a workmanship or product inspection", "exploreQuality",
        StampTemplate(
            StampPreset.FIELD_NOTE, StampColor.OLIVE, heading = "Quality check", layout = StampLayout.LEDGER,
            showPhotoCode = true, typography = StampTypography.CONDENSED,
        ),
        ExploreCategory.INSPECTION,
    ),
    StarterTemplate(
        "Vehicle walkaround", "Record visible vehicle condition before or after use", "exploreVehicle",
        StampTemplate(
            StampPreset.SITE_VISIT, StampColor.COPPER, heading = "Vehicle walkaround",
            layout = StampLayout.PANEL, showPhotoCode = true, position = StampPosition.BOTTOM_RIGHT,
        ),
        ExploreCategory.INSPECTION,
    ),
    StarterTemplate(
        "Premises survey", "Record the condition of a building or unit", "explorePremises",
        StampTemplate(
            StampPreset.SITE_VISIT, StampColor.AMBER, heading = "Premises survey", layout = StampLayout.PLAIN,
            typography = StampTypography.SERIF, textColor = StampTextColor.WARM, position = StampPosition.TOP_RIGHT,
        ),
        ExploreCategory.INSPECTION,
    ),
    StarterTemplate(
        "Shelf audit", "Record stock availability and shelf condition", "exploreShelfAudit",
        StampTemplate(
            StampPreset.FIELD_NOTE, StampColor.JADE, heading = "Shelf audit", layout = StampLayout.LEDGER,
            size = StampSize.COMPACT, showPhotoCode = true,
        ),
        ExploreCategory.RETAIL,
    ),
    StarterTemplate(
        "Display refresh", "Photograph a finished retail display", "exploreDisplay",
        StampTemplate(
            StampPreset.COMPLETION, StampColor.LILAC, heading = "Display refresh", layout = StampLayout.PLAIN,
            typography = StampTypography.SERIF, position = StampPosition.TOP_LEFT,
        ),
        ExploreCategory.RETAIL,
    ),
    StarterTemplate(
        "Store opening", "Log opening time and shop readiness", "exploreStoreOpening",
        StampTemplate(
            StampPreset.SITE_VISIT, StampColor.AMBER, heading = "Store opening", layout = StampLayout.CLOCK,
            textColor = StampTextColor.GOLD, size = StampSize.COMPACT,
        ),
        ExploreCategory.RETAIL,
    ),
    StarterTemplate(
        "Campus activity", "Document a school or organization activity for a report", "exploreCampus",
        StampTemplate(
            StampPreset.FIELD_NOTE, StampColor.BLUE, heading = "Campus activity", layout = StampLayout.PANEL,
            showPhotoCode = true, position = StampPosition.BOTTOM_RIGHT,
        ),
        ExploreCategory.DAILY,
    ),
    StarterTemplate(
        "Room turnover", "Document a guest room after cleaning or maintenance", "exploreRoomTurnover",
        StampTemplate(
            StampPreset.COMPLETION, StampColor.JADE, heading = "Room turnover", layout = StampLayout.LEDGER,
            size = StampSize.COMPACT, textColor = StampTextColor.MINT,
        ),
        ExploreCategory.HANDOVER,
    ),
    StarterTemplate(
        "Build progress", "Record a construction milestone or work in progress", "exploreBuildProgress",
        StampTemplate(
            StampPreset.FIELD_NOTE, StampColor.COPPER, heading = "Build progress", layout = StampLayout.LEDGER,
            showPhotoCode = true, typography = StampTypography.CONDENSED, size = StampSize.LARGE,
        ),
        ExploreCategory.DAILY,
    ),
)
