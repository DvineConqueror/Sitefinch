package app.workphotolog.android

private fun fieldKit(
    title: String,
    description: String,
    key: String,
    category: ExploreCategory,
    mark: BuiltInStampMark,
    color: StampColor,
    layout: StampLayout,
    customize: StampTemplate.() -> StampTemplate,
) = StarterTemplate(
    title,
    description,
    "kit" + key,
    StampTemplate(
        preset = StampPreset.SITE_VISIT,
        heading = title,
        color = color,
        layout = layout,
        backgroundOpacity = if (layout == StampLayout.TICKET) 0.82f else 1f,
        logoId = mark.id,
        logoShape = StampLogoShape.ROUNDED,
    ).customize(),
    category,
    sampleSubject = when (category) {
        ExploreCategory.SHIPPING -> "Harbor depot"
        ExploreCategory.SECURITY -> "East gate"
        ExploreCategory.INSPECTION -> "Walkway survey"
        ExploreCategory.HANDOVER -> "Unit handover"
        ExploreCategory.ATTENDANCE -> "Morning crew"
        ExploreCategory.RETAIL -> "Market outlet"
        ExploreCategory.ASSETS -> "Pump station"
        else -> "Field activity"
    },
)

internal val fieldKitTemplates = listOf(
    fieldKit(
        "Parcel route", "Delivery details and recorded coordinates", "ParcelRoute",
        ExploreCategory.SHIPPING, BuiltInStampMark.PARCEL, StampColor.BLUE, StampLayout.TICKET,
    ) {
        copy(
            showCoordinates = true,
            showPhotoCode = true,
            tags = "Delivery",
        )
    },
    fieldKit(
        "Patrol post", "Security rounds and observations", "PatrolPost",
        ExploreCategory.SECURITY, BuiltInStampMark.PATROL, StampColor.OLIVE, StampLayout.TICKET,
    ) {
        copy(
            showCompass = true,
            showStyledNote = true,
            styledNoteStyle = StampNoteStyle.ALERT,
        )
    },
    fieldKit(
        "Dispatch scan", "Outgoing shipments and tracking notes", "DispatchScan",
        ExploreCategory.SHIPPING, BuiltInStampMark.PARCEL, StampColor.AMBER, StampLayout.LEDGER,
    ) {
        copy(
            showPhotoCode = true,
            showStyledNote = true,
            tags = "Dispatch",
        )
    },
    fieldKit(
        "Public works visit", "Site visits for public infrastructure", "CivicVisit",
        ExploreCategory.INSPECTION, BuiltInStampMark.CIVIC, StampColor.BLUE, StampLayout.CLOCK,
    ) {
        copy(
            showCoordinates = true,
            logoShape = StampLogoShape.CIRCLE,
        )
    },
    fieldKit(
        "Contact card", "A site photo with contact details", "ContactCard",
        ExploreCategory.HANDOVER, BuiltInStampMark.CREW, StampColor.JADE, StampLayout.PANEL,
    ) {
        copy(
            showBusinessCard = true,
            businessCardRole = "Site contact",
            showNote = false,
        )
    },
    fieldKit(
        "Shift entry", "Arrival time and employee details", "ShiftEntry",
        ExploreCategory.ATTENDANCE, BuiltInStampMark.CREW, StampColor.JADE, StampLayout.TICKET,
    ) {
        copy(
            largeClock = true,
            showCoordinates = true,
            showAltitude = true,
            showCompass = true,
            showPhotoCode = true,
        )
    },
    fieldKit(
        "Branch record", "Outlet details and site conditions", "BranchRecord",
        ExploreCategory.RETAIL, BuiltInStampMark.RETAIL, StampColor.LILAC, StampLayout.LEDGER,
    ) {
        copy(
            showCoordinates = true,
            showWeather = true,
            tags = "Branch visit",
        )
    },
    fieldKit(
        "Parcel handoff", "Recipient details and local photo code", "ParcelHandoff",
        ExploreCategory.SHIPPING, BuiltInStampMark.PARCEL, StampColor.JADE, StampLayout.TICKET,
    ) {
        copy(
            showPhotoCode = true,
            showBusinessCard = true,
            businessCardRole = "Handoff contact",
            logoSide = StampLogoSide.RIGHT,
        )
    },
    fieldKit(
        "Crew attendance", "Crew names and shift notes", "CrewAttendance",
        ExploreCategory.ATTENDANCE, BuiltInStampMark.CREW, StampColor.OLIVE, StampLayout.CLOCK,
    ) {
        copy(
            showStyledNote = true,
            styledNoteStyle = StampNoteStyle.HIGHLIGHT,
            tags = "Attendance",
        )
    },
    fieldKit(
        "Inspection record", "Inspection details and elevation", "InspectionRecord",
        ExploreCategory.INSPECTION, BuiltInStampMark.CIVIC, StampColor.JADE, StampLayout.LEDGER,
    ) {
        copy(
            showCoordinates = true,
            showAltitude = true,
            showPhotoCode = true,
            logoShape = StampLogoShape.CIRCLE,
        )
    },
    fieldKit(
        "Kitchen shift", "Kitchen crew start and shift notes", "KitchenShift",
        ExploreCategory.ATTENDANCE, BuiltInStampMark.RETAIL, StampColor.COPPER, StampLayout.TICKET,
    ) {
        copy(
            largeClock = true,
            showStyledNote = true,
            showPhotoCode = true,
            tags = "Kitchen crew",
        )
    },
    fieldKit(
        "Daily attendance", "Device time and optional weather", "DailyAttendance",
        ExploreCategory.ATTENDANCE, BuiltInStampMark.CREW, StampColor.BLUE, StampLayout.CLOCK,
    ) {
        copy(
            showWeather = true,
            clockScale = 1.22f,
            textColor = StampTextColor.COOL,
        )
    },
    fieldKit(
        "Counter check", "Service counter and opening notes", "CounterCheck",
        ExploreCategory.RETAIL, BuiltInStampMark.RETAIL, StampColor.AMBER, StampLayout.TICKET,
    ) {
        copy(
            largeClock = true,
            size = StampSize.COMPACT,
            tags = "Counter",
        )
    },
    fieldKit(
        "District inspection", "Site title with coordinates", "DistrictInspection",
        ExploreCategory.INSPECTION, BuiltInStampMark.CIVIC, StampColor.LILAC, StampLayout.TICKET,
    ) {
        copy(
            showCoordinates = true,
            showAltitude = true,
            logoSide = StampLogoSide.RIGHT,
        )
    },
    fieldKit(
        "Construction progress", "Work scope and progress notes", "ConstructionProgress",
        ExploreCategory.DAILY, BuiltInStampMark.BUILD, StampColor.COPPER, StampLayout.LEDGER,
    ) {
        copy(
            size = StampSize.LARGE,
            showStyledNote = true,
            styledNoteStyle = StampNoteStyle.HIGHLIGHT,
            tags = "Construction",
        )
    },
    fieldKit(
        "Shift exit", "Departure time and handover contact", "ShiftExit",
        ExploreCategory.ATTENDANCE, BuiltInStampMark.CREW, StampColor.LILAC, StampLayout.TICKET,
    ) {
        copy(
            largeClock = true,
            showBusinessCard = true,
            businessCardRole = "Shift contact",
            showPhotoCode = true,
        )
    },
    fieldKit(
        "Venue visit", "A compact place and time stamp", "VenueVisit",
        ExploreCategory.RETAIL, BuiltInStampMark.RETAIL, StampColor.COPPER, StampLayout.PLAIN,
    ) {
        copy(
            largeClock = true,
            showNote = false,
            logoShape = StampLogoShape.CIRCLE,
            typography = StampTypography.SERIF,
        )
    },
    fieldKit(
        "Class activity", "Classroom activity and report notes", "ClassActivity",
        ExploreCategory.DAILY, BuiltInStampMark.SCHOOL, StampColor.AMBER, StampLayout.PANEL,
    ) {
        copy(
            showStyledNote = true,
            tags = "Class activity",
            logoShape = StampLogoShape.CIRCLE,
        )
    },
    fieldKit(
        "Kiosk opening", "Opening time and supervisor details", "KioskOpening",
        ExploreCategory.RETAIL, BuiltInStampMark.RETAIL, StampColor.JADE, StampLayout.CLOCK,
    ) {
        copy(
            showNote = false,
            showBusinessCard = true,
            businessCardRole = "Opening supervisor",
        )
    },
    fieldKit(
        "Store check-in", "Store time, location and note", "StoreCheckIn",
        ExploreCategory.RETAIL, BuiltInStampMark.RETAIL, StampColor.OLIVE, StampLayout.TICKET,
    ) {
        copy(
            largeClock = true,
            showPhotoCode = true,
            showMap = true,
            tags = "Store visit",
        )
    },
    fieldKit(
        "Utility installation", "Coordinates, elevation and work notes", "UtilityInstallation",
        ExploreCategory.ASSETS, BuiltInStampMark.UTILITY, StampColor.BLUE, StampLayout.LEDGER,
    ) {
        copy(
            showCoordinates = true,
            showAltitude = true,
            showStyledNote = true,
            tags = "Installation",
        )
    },
)
