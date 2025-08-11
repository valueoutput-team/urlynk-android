package com.valueoutput.urlynk

import java.util.*

/**
 * Represents the core link configuration and its validation logic.
 */
data class LinkModel(
    /**
     * The unique link identifier to update.
     * Found at the end of the generated link, for example:
     * https://urlynk.in/<LINK_ID> or https://your.branded.domain/<LINK_ID>.
     * Send `null` when creating a new link.
     */
    val id: String? = null,

    /**
     * The original URL that will be shortened.
     * This is the only required parameter; all other parameters are optional.
     */
    val url: String,

    /** Custom domain for the link. Must be verified on URLynk. */
    val domain: String? = null,

    /** UTC timestamp (milliseconds since epoch) after which the link becomes active. Null means active immediately. */
    val startTime: Long? = null,

    /** Password for link access. */
    val password: String? = null,

    /**
     * Callback URL for receiving notifications or webhook events.
     * Must be a valid, self-authorized HTTP/HTTPS POST endpoint.
     */
    val callbackURL: String? = null,

    /** List of expiry conditions for the link (click-based or time-based). */
    val expiry: List<ExpiryModel>? = null,

    /** Restrictions applied to the link, such as OS/device limitations, location rules, etc. */
    val restrictions: RestrictionModel? = null,

    /** Smart routing rules that redirect based on OS, device, time, or location. */
    val smartRouting: SmartRoutingModel? = null
) {
    fun validate(): String? {
        val now = System.currentTimeMillis()

        if (!url.isValidHttpUrl()) return "Data is an invalid URL"
        if (startTime != null && startTime < now) return "Start time cannot be lesser than now"
        if (domain != null && !domain.matches(Regex("^[a-zA-Z0-9.-]+$"))) return "Invalid domain"
        if (callbackURL != null && !callbackURL.isValidHttpUrl()) return "Invalid callback URL"

        expiry?.let {
            if (it.size > ExpiryType.entries.size) return "Max expiry size can be ${ExpiryType.entries.size}"
            for (e in it) {
                when (e.type) {
                    ExpiryType.CLICK_BASED -> if (e.value <= 0) return "Expiry clicks must be greater than 0"
                    ExpiryType.TIME_BASED -> if (e.value < now + 24 * 60 * 60 * 1000) return "Expiry time must be more than 24 hours from now"
                }
            }
        }

        restrictions?.let { r ->
            if (r.clicksPerDevice != null && r.clicksPerDevice <= 0) return "Clicks per device must be greater than 0"

            if (r.os != null) {
                if (r.os.size > OSType.entries.size) return "Max restrictions os size can be ${OSType.entries.size}"
                if (hasDuplicates(r.os)) return "Restrictions os list contains duplicates"
            }

            if (r.devices != null) {
                if (r.devices.size > DeviceType.entries.size) return "Max restrictions device size can be ${DeviceType.entries.size}"
                if (hasDuplicates(r.devices)) return "Restrictions device list contains duplicates"
            }

            r.workingHrs?.forEach { (start, end) ->
                if (start > end) return "Start hr cannot be greater than end hr: workingHrs"
                if (start !in 0..23) return "Start hr must be between 0 and 23: workingHrs"
                if (end !in 0..23) return "End hr must be between 0 and 23: workingHrs"
            }

            r.inclLoc?.forEach { e ->
                if(e.address.isBlank()) return "Address cannot be blank: inclLoc"
                if(e.boundingBox.size != 4) return "Bounding box size must be 4: inclLoc"
                if(e.boundingBox[0] !in -90.0..90.0) return "southLat must be between -90 and 90: inclLoc"
                if(e.boundingBox[1] !in -90.0..90.0) return "northLat must be between -90 and 90: inclLoc"
                if(e.boundingBox[2] !in -180.0..180.0) return "westLng must be between -180 and 180: inclLoc"
                if(e.boundingBox[3] !in -180.0..180.0) return "eastLng must be between -180 and 180: inclLoc"
                if(e.boundingBox[0] > e.boundingBox[1]) return "southLat cannot be greater than northLat: inclLoc"
                if(e.boundingBox[2] > e.boundingBox[3]) return "westLng cannot be greater than eastLng: inclLoc"
            }

            r.exclLoc?.forEach { e ->
                if(e.address.isBlank()) return "Address cannot be blank: exclLoc"
                if(e.boundingBox.size != 4) return "Bounding box size must be 4: exclLoc"
                if(e.boundingBox[0] !in -90.0..90.0) return "southLat must be between -90 and 90: exclLoc"
                if(e.boundingBox[1] !in -90.0..90.0) return "northLat must be between -90 and 90: exclLoc"
                if(e.boundingBox[2] !in -180.0..180.0) return "westLng must be between -180 and 180: exclLoc"
                if(e.boundingBox[3] !in -180.0..180.0) return "eastLng must be between -180 and 180: exclLoc"
                if(e.boundingBox[0] > e.boundingBox[1]) return "southLat cannot be greater than northLat: exclLoc"
                if(e.boundingBox[2] > e.boundingBox[3]) return "westLng cannot be greater than eastLng: exclLoc"
            }
        }

        smartRouting?.let { sr ->
            sr.osBased?.forEach { e ->
                if (!e.url.isValidHttpUrl()) return "Data must be a valid URL: osBased smartRoutings"
                if (e.targets.size > OSType.entries.size) return "Max smartRouting osBased target size can be ${OSType.entries.size}"
                if (hasDuplicates(e.targets)) return "smartRouting osBased targets contain duplicates"
            }

            sr.deviceBased?.forEach { e ->
                if (!e.url.isValidHttpUrl()) return "Data must be a valid URL: deviceBased smartRoutings"
                if (e.targets.size > DeviceType.entries.size) return "Max smartRouting deviceBased target size can be ${DeviceType.entries.size}"
                if (hasDuplicates(e.targets)) return "smartRouting deviceBased targets contain duplicates"
            }

            sr.timeBased?.forEach { e ->
                if (!e.url.isValidHttpUrl()) return "Data must be a valid URL: timeBased smartRoutings"
                e.targets.forEach { (start, end) ->
                    if (start > end) return "Start hr cannot be greater than end hr: timeBased smartRoutings"
                    if (start !in 0..23) return "Start hr must be between 0 and 23: timeBased smartRoutings"
                    if (end !in 0..23) return "End hr must be between 0 and 23: timeBased smartRoutings"
                }
            }

            sr.locBased?.forEach { l ->
                if (!l.url.isValidHttpUrl()) return "Data must be a valid URL: locBased smartRoutings"
                l.targets.forEach { e ->
                    if(e.address.isBlank()) return "Address cannot be blank: locBased smartRoutings"
                    if(e.boundingBox.size != 4) return "Bounding box size must be 4: locBased smartRoutings"
                    if(e.boundingBox[0] !in -90.0..90.0) return "southLat must be between -90 and 90: locBased smartRoutings"
                    if(e.boundingBox[1] !in -90.0..90.0) return "northLat must be between -90 and 90: locBased smartRoutings"
                    if(e.boundingBox[2] !in -180.0..180.0) return "westLng must be between -180 and 180: locBased smartRoutings"
                    if(e.boundingBox[3] !in -180.0..180.0) return "eastLng must be between -180 and 180: locBased smartRoutings"
                    if(e.boundingBox[0] > e.boundingBox[1]) return "southLat cannot be greater than northLat: locBased smartRoutings"
                    if(e.boundingBox[2] > e.boundingBox[3]) return "westLng cannot be greater than eastLng: locBased smartRoutings"
                }
            }
        }

        return null
    }

    fun toJson(): Map<String, Any?> {
        val tz = TimeZone.getDefault()
        val offsetMillis = tz.getOffset(Calendar.getInstance().timeInMillis)
        val utcOffset = offsetMillis / 3600000.0

        return mapOf(
            "_id" to id,
            "data" to url,
            "domain" to domain,
            "password" to password,
            "startTime" to startTime,
            "utcOffset" to utcOffset,
            "callbackURL" to callbackURL,
            "expiry" to expiry?.map { it.toJson() },
            "restrictions" to restrictions?.toJson(),
            "smartRouting" to smartRouting?.toJson()
        )
    }

    private fun <T> hasDuplicates(list: List<T>): Boolean = list.size != list.toSet().size
}


/**
 * Expiry configuration for a link.
 */
data class ExpiryModel(
    /** Expiry threshold value. Meaning depends on [type]:
     * - Click-based: number of allowed clicks
     * - Time-based: UTC timestamp (milliseconds since epoch)
     */
    val value: Long,
    /** Expiry type: click-based or time-based. */
    val type: ExpiryType
) {
    fun toJson() = mapOf("value" to value, "type" to type.ordinal)
}

/**
 * Restrictions applied to a link.
 */
data class RestrictionModel(
    /** Allowed operating systems. Null means no OS restriction. */
    val os: List<OSType>? = null,

    /** Maximum number of clicks per device. Null means unlimited. */
    val clicksPerDevice: Int? = null,

    /** Whitelisted locations. Null means no worldwide access. */
    val inclLoc: List<LocModel>? = null,

    /** Blacklisted locations. Null means no blacklist. */
    val exclLoc: List<LocModel>? = null,

    /** Allowed device types. Null means no device restriction. */
    val devices: List<DeviceType>? = null,

    /**
     * Allowed working hours, represented as a list of (startHour, endHour) pairs in 24-hour format.
     * - startHour is interpreted as startHour:00:00 (inclusive).
     * - endHour is interpreted as endHour:59:59 (inclusive).
     */
    val workingHrs: List<Pair<Int, Int>>? = null
) {
    fun toJson(): Map<String, Any> {
        val json = mutableMapOf<String, Any>()
        clicksPerDevice?.let { json["clicksPerDevice"] = it }
        os?.let { if (it.isNotEmpty()) json["os"] = it.map { o -> o.ordinal } }
        devices?.let { if (it.isNotEmpty()) json["devices"] = it.map { d -> d.ordinal } }
        inclLoc?.let { if (it.isNotEmpty()) json["inclLoc"] = it.map { b -> b.toJson() } }
        exclLoc?.let { if (it.isNotEmpty()) json["exclLoc"] = it.map { b -> b.toJson() } }
        workingHrs?.let {
            if (it.isNotEmpty()) json["workingHrs"] = it.map { (start, end) -> mapOf("startHr" to start, "endHr" to end) }
        }
        return json
    }
}

/**
 * Smart routing configuration for conditional redirection.
 */
data class SmartRoutingModel(
    /** Routes based on operating system type. */
    val osBased: List<RoutingModel<OSType>>? = null,

    /** Routes based on physical location (address). */
    val locBased: List<RoutingModel<LocModel>>? = null,

    /** Routes based on device type. */
    val deviceBased: List<RoutingModel<DeviceType>>? = null,

    /** Routes based on time ranges (startHour, endHour). */
    val timeBased: List<RoutingModel<Pair<Int, Int>>>? = null
) {
    fun toJson(): Map<String, Any> {
        val json = mutableMapOf<String, Any>()
        osBased?.let { if (it.isNotEmpty()) json["osBased"] = it.map { r -> r.toJson { cond -> cond.ordinal } } }
        locBased?.let { if (it.isNotEmpty()) json["locBased"] = it.map { r -> r.toJson { cond -> cond.toJson() } } }
        deviceBased?.let { if (it.isNotEmpty()) json["deviceBased"] = it.map { r -> r.toJson { cond -> cond.ordinal } } }
        timeBased?.let {
            if (it.isNotEmpty()) json["timeBased"] = it.map { r -> r.toJson { cond -> mapOf("startHr" to cond.first, "endHr" to cond.second) } }
        }
        return json
    }
}

/**
 * Represents a location, including its address and geographic bounding boundaries.
 */
data class LocModel(
    val address: String,

    /**
     * The rectangular geographic area that encloses this location, defined by its
     * southernmost, northernmost, westernmost, and easternmost boundaries in latitude and longitude.
     *
     * Format: [southLatitude, northLatitude, westLongitude, eastLongitude]
     */
    val boundingBox: List<Double>,
) {
    fun toJson(): Map<String, Any> {
        val json = mutableMapOf<String, Any>()
        json["address"] = address
        json["boundingBox"] = boundingBox
        return json
    }
}


/**
 * Represents a routing rule for a specific condition type.
 */
data class RoutingModel<T>(
    /** Destination URL when this routing rule matches. */
    val url: String?,

    /** Target condition values (e.g., OS types, device types, time ranges, locations). */
    val targets: List<T>) {
    fun toJson(mapCondition: (T) -> Any) = mapOf("data" to url, "targets" to targets.map(mapCondition))
}

fun String?.isValidHttpUrl(): Boolean {
    if (this.isNullOrBlank()) return false
    return try {
        val url = java.net.URL(this)
        url.protocol.equals("http", true) || url.protocol.equals("https", true)
    } catch (_: Exception) {
        false
    }
}