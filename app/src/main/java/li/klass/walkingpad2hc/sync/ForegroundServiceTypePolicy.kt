package li.klass.walkingpad2hc.sync

/**
 * Foreground service type selection for [StepSyncService].
 *
 * On Android 15+ the `dataSync` foreground service type is limited to
 * 6 hours per 24 hours, after which the system stops the service
 * (`onTimeout`). The `health` type (API 34+) has no such limit and fits
 * this app exactly: it exchanges Health Connect data.
 *
 * The system only accepts `health` when the app holds the manifest
 * permission `FOREGROUND_SERVICE_HEALTH` PLUS at least one qualifying
 * runtime permission: `ACTIVITY_RECOGNITION`, `HIGH_SAMPLING_RATE_SENSORS`,
 * or one of the qualifying health READ permissions (heart rate, skin
 * temperature, oxygen saturation, blood pressure, heart-rate variability,
 * respiratory rate, VO2 max). Health WRITE permissions (`WRITE_STEPS`)
 * and `READ_STEPS` do NOT qualify — checking those was the exact mistake
 * that produced `SecurityException: Starting FGS with type health` before
 * this policy existed.
 *
 * Without a qualifying permission the service falls back to `dataSync`
 * (pre-time-limit behavior, valid everywhere), which is why this gate
 * never crashes: an unqualified device simply keeps the previous type.
 */
object ForegroundServiceTypePolicy {
    fun shouldUseHealthType(apiLevel: Int, hasQualifyingPermission: Boolean): Boolean =
        apiLevel >= 34 && hasQualifyingPermission
}
