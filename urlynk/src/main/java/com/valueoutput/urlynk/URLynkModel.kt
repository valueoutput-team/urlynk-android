package com.valueoutput.urlynk

import java.util.*

enum class ExpiryType { CLICK_BASED, TIME_BASED }

enum class DeviceType { MOBILE, TABLET, DESKTOP }

enum class OSType { ANDROID, IOS, MACOS, WINDOWS, LINUX }

/**
 * Represents the core link configuration and its validation logic.
 */
data class LinkModel(
    /**
     * The unique link identifier to update. Send `null` when creating a new link.
     * Found at the end of the generated link, for example:
     * https://urlynk.in/<LINK_ID> or https://your.branded.domain/<LINK_ID>.
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
     * Webhook URL for receiving click events.
     * Must be a valid, self-authorized HTTP/HTTPS POST endpoint.
     */
    val webhookURL: String? = null,

    /** List of expiry conditions for the link (click-based or time-based). */
    val expiry: List<ExpiryModel>? = null,

    /** Restrictions applied to the link based on OS, device, time, or location. */
    val restrictions: RestrictionModel? = null,

    /** Smart routing rules that redirect based on OS, device, time, or location. */
    val smartRouting: SmartRoutingModel? = null
) {
    fun toJson(): Map<String, Any?> {
        val tz = TimeZone.getDefault()
        val offsetMillis = tz.getOffset(Calendar.getInstance().timeInMillis)
        val utcOffset = offsetMillis / 3600000.0

        return mapOf(
            "_id" to id?.trim(),
            "data" to url.trim(),
            "password" to password,
            "startTime" to startTime,
            "utcOffset" to utcOffset,
            "domain" to domain?.trim(),
            "webhookURL" to webhookURL?.trim(),
            "expiry" to expiry?.map { it.toJson() },
            "restrictions" to restrictions?.toJson(),
            "smartRouting" to smartRouting?.toJson()
        )
    }

    fun validate(): String? {
        val now = System.currentTimeMillis()

        if (!url.isValidURL) return "Invalid URL"
        if (id != null && id.trim().isEmpty()) return "ID cannot be empty"
        if (domain != null && !domain.isValidDomain) return "Invalid domain"
        if (webhookURL != null && !webhookURL.isValidURL) return "Invalid webhook URL"
        if (password != null && password.trim().isEmpty()) return "Password cannot be empty"
        if (startTime != null && startTime < now) return "Start time cannot be less than current time"

        expiry?.let {
            if(it.isEmpty()) return "Expiry cannot be empty"
            if(it.hasDuplicates) return "Expiry contains duplicates"
            for(e in expiry){
                val err = e.validate()
                if(err != null) return err
            }
        }

        val err = restrictions?.validate()
        if(err != null) return err

        return smartRouting?.validate()
    }
}


/**
 * Expiry configuration for a link.
 */
data class ExpiryModel(
    /** Expiry type: click-based or time-based. */
    val type: ExpiryType,

    /** Expiry threshold value. Meaning depends on [type]:
     * - Click-based: number of allowed clicks
     * - Time-based: UTC timestamp (milliseconds since epoch)
     */
    val value: Long
) {
    override fun hashCode(): Int {
       return type.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExpiryModel) return false
        return type == other.type
    }

    fun toJson() = mapOf("value" to value, "type" to type.ordinal)

    fun validate(): String? {
        val now = System.currentTimeMillis()

        return when (type) {
            ExpiryType.CLICK_BASED -> {
                if (value <= 0) {
                    "Expiry clicks must be greater than 0"
                } else null
            }

            ExpiryType.TIME_BASED -> {
                if (value < now + 24 * 60 * 60 * 1000) {
                    "Expiry time must be more than 24 hours from current time"
                } else null
            }
        }
    }

}

/**
 * Restrictions applied to the link based on OS, device, time, or location.
 *
 * When multiple restrictions are applied, they are evaluated in the following order of priority:
 * 1. Clicks per device
 * 2. OS types
 * 3. Device types
 * 4. Working Hours
 * 5. Excluded locations
 * 6. Included locations
 *
 * Note: If a specific location is included but its parent region is excluded,
 * the exclusion takes precedence and the location will also be blocked.
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

    /** Allowed working hours. Null means 24 hours access. */
    val workingHrs: List<WorkingHrModel>? = null
) {
    fun toJson(): Map<String, Any> {
        val json = mutableMapOf<String, Any>()
        clicksPerDevice?.let { json["clicksPerDevice"] = it }
        os?.let { if (it.isNotEmpty()) json["os"] = it.map { o -> o.ordinal } }
        devices?.let { if (it.isNotEmpty()) json["devices"] = it.map { d -> d.ordinal } }
        inclLoc?.let { if (it.isNotEmpty()) json["inclLoc"] = it.map { b -> b.toJson() } }
        exclLoc?.let { if (it.isNotEmpty()) json["exclLoc"] = it.map { b -> b.toJson() } }
        workingHrs?.let { if (it.isNotEmpty()) json["workingHrs"] = it.map { b -> b.toJson() } }
        return json
    }

    fun validate(): String? {
        if (clicksPerDevice != null && clicksPerDevice <= 0) {
            return "Clicks per device must be greater than 0"
        }

        if (os != null) {
            if (os.isEmpty()) return "Restrictions os cannot be empty"
            if (os.hasDuplicates) return "Restrictions os contains duplicates"
        }

        if (devices != null) {
            if (devices.isEmpty()) return "Restrictions devices cannot be empty"
            if (devices.hasDuplicates) return "Restrictions devices contain duplicates"
        }

        if (workingHrs != null) {
            if (workingHrs.isEmpty()) return "Restrictions workingHrs cannot be empty"
            if (workingHrs.hasDuplicates) return "Restrictions workingHrs contain duplicates"
            for(e in workingHrs){
                val err = e.validate()
                if(err != null) return "$err: restrictions workingHrs"
            }
        }

        if (inclLoc != null) {
            if (inclLoc.isEmpty()) return "Restrictions inclLoc cannot be empty"
            if (inclLoc.hasDuplicates) return "Restrictions inclLoc contain duplicates"
            for(e in inclLoc){
                val err = e.validate()
                if(err != null) return "$err: restrictions inclLoc"
            }
        }

        if (exclLoc != null) {
            if (exclLoc.isEmpty()) return "Restrictions exclLoc cannot be empty"
            if (exclLoc.hasDuplicates) return "Restrictions exclLoc contain duplicates"
            for(e in exclLoc){
                val err = e.validate()
                if(err != null) return "$err: restrictions exclLoc"
            }
        }

        return null
    }
}

/**
 * Smart routing rules that redirect based on OS, device, time, or location.
 *
 * When multiple conditions overlap, they are evaluated in the following order of priority:
 * 1. Time-based routing
 * 2. Location-based routing
 * 3. Device-based routing
 * 4. OS-based routing <br>
 *
 * **Note:** Restrictions are always evaluated first. If a user is blocked by any restriction,
 * smart routing rules will not be applied.
 */
data class SmartRoutingModel(
    /** Routes based on operating system type. */
    val osBased: List<RoutingModel<OSType>>? = null,

    /** Routes based on physical location (address). */
    val locBased: List<RoutingModel<LocModel>>? = null,

    /** Routes based on device type. */
    val deviceBased: List<RoutingModel<DeviceType>>? = null,

    /** Routes based on time ranges. */
    val timeBased: List<RoutingModel<WorkingHrModel>>? = null
) {
    fun toJson(): Map<String, Any> {
        val json = mutableMapOf<String, Any>()
        osBased?.let { if (it.isNotEmpty()) json["osBased"] = it.map { r -> r.toJson { cond -> cond.ordinal } } }
        locBased?.let { if (it.isNotEmpty()) json["locBased"] = it.map { r -> r.toJson { cond -> cond.toJson() } } }
        timeBased?.let { if (it.isNotEmpty()) json["timeBased"] = it.map { r -> r.toJson { cond -> cond.toJson() } } }
        deviceBased?.let { if (it.isNotEmpty()) json["deviceBased"] = it.map { r -> r.toJson { cond -> cond.ordinal } } }
        return json
    }

    fun validate(): String? {
        if(osBased != null){
            for(e in osBased){
                val err = e.validate { _ -> null }
                if(err != null) return "$err: osBased smartRouting"
            }
        }

        if(deviceBased != null){
            for(e in deviceBased){
                val err = e.validate { _ -> null }
                if(err != null) return "$err: deviceBased smartRouting"
            }
        }

        if(timeBased != null){
            for(e in timeBased){
                val err = e.validate { t -> t.validate() }
                if(err != null) return "$err: timeBased smartRouting"
            }
        }

        if(locBased != null){
            for(e in locBased){
                val err = e.validate { t -> t.validate() }
                if(err != null) return "$err: locBased smartRouting"
            }
        }

        return null
    }
}

/**
 * Hour range when the link is accessible (startHr:00:00 - endHr:59:59)
 */
data class WorkingHrModel (
    /**
     * Start hour in 24-hour format, interpreted as startHr:00:00 (inclusive).
     */
    val startHr: Int,

    /**
     * End hour in 24-hour format, interpreted as endHr:59:59 (inclusive).
     */
    val endHr: Int
){
    override fun hashCode(): Int {
        var result = startHr.hashCode()
        result = 31 * result + endHr.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WorkingHrModel) return false
        return startHr == other.startHr && endHr == other.endHr
    }

    fun toJson() = mapOf("startHr" to startHr, "endHr" to endHr)

    fun validate(): String? {
        if (startHr < 0 || startHr > 23) return "Start hr must be between 0 and 23"
        if (endHr < 0 || endHr > 23) return "End hr must be between 0 and 23"
        if (startHr > endHr) return "Start hr cannot be greater than end hr"
        return null
    }
}

/**
 * Represents a location, including its address and geographic bounding boundaries.
 */
data class LocModel(
    /**
     * Address
     */
    val address: String,

    /**
     * The rectangular geographic area that encloses this location, defined by its
     * southernmost, northernmost, westernmost, and easternmost boundaries in latitude and longitude.
     *
     * Required Sequence: [southLatitude, northLatitude, westLongitude, eastLongitude]
     */
    val boundingBox: List<Double>,
) {
    override fun hashCode(): Int {
        var result = address.trim().hashCode()
        result = 31 * result + boundingBox.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LocModel) return false
        return address.trim() == other.address.trim() && boundingBox == other.boundingBox
    }

    fun toJson() = mapOf("address" to address.trim(), "boundingBox" to boundingBox)

    fun validate(): String? {
        if (address.trim().isEmpty()) return "Address cannot be blank"
        if (boundingBox.size != 4) return "Bounding box size must be 4"

        val sLat = boundingBox[0]
        val nLat = boundingBox[1]
        val wLng = boundingBox[2]
        val eLng = boundingBox[3]

        if (sLat < -90 || sLat > 90) return "southLat must be b/w -90 and 90"
        if (nLat < -90 || nLat > 90) return "northLat must be b/w -90 and 90"
        if (wLng < -180 || wLng > 180) return "westLng must be b/w -180 and 180"
        if (eLng < -180 || eLng > 180) return "eastLng must be b/w -180 and 180"
        return null
    }
}


/**
 * Represents a routing rule for a specific condition type.
 */
data class RoutingModel<T>(
    /** Redirect URL */
    val url: String,

    /** Target condition values (e.g., OS types, device types, time ranges, locations). */
    val targets: List<T>) {
    fun toJson(mapCondition: (T) -> Any) = mapOf(
        "data" to url.trim(),
        "targets" to targets.map(mapCondition)
    )

    fun validate(targetValidator: (T) -> String?): String? {
        if (!url.isValidURL) return "Invalid URL"
        if (targets.isEmpty()) return "Targets cannot be empty"
        if (targets.hasDuplicates) return "Targets contain duplicates"
        for (target in targets) {
            val err = targetValidator(target)
            if(err != null) return err
        }

        return null
    }
}

val String.isValidURL: Boolean
    get() {
        return try {
            val url = java.net.URL(trim())
            url.protocol.equals("http", true) || url.protocol.equals("https", true)
        } catch (e: Exception) {
            false
        }
    }

val String.isValidDomain: Boolean
    get() {
        val regex = Regex("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
        return regex.matches(trim())
    }

val <T> List<T>.hasDuplicates: Boolean
    get() {
        val seen = mutableSetOf<T>()
        for (item in this) {
            if (!seen.add(item)) return true
        }
        return false
    }