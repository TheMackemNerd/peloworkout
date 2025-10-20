package com.digitalelysium.peloworkout.ui.util

import com.digitalelysium.peloworkout.data.Gender
import com.digitalelysium.peloworkout.data.UserProfile
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Calorie estimate with graceful fallbacks:
 * - If mass & duration present: ACSM (net)
 * - Else: Power-only with mech efficiency
 * - If HR context complete (mass, age, gender, avgHR): blend HR-only (Keytel) in at low weight
 */
fun CalorieCalculation(
    totalKJ: Double,
    heartRateHistory: List<Int?>? = null,
    userProfile: UserProfile? = null,
    mechEff: Double = 0.24,
    hrWeight: Double = 0.15
): Double {
    if (totalKJ <= 0.0) return 0.0

    val durationSec: Int? = heartRateHistory?.size?.takeIf { it > 0 }
    val avgHr: Int? = heartRateHistory
        ?.filterNotNull()
        ?.takeIf { it.isNotEmpty() }
        ?.average()
        ?.roundToInt()

    val avgPowerW: Double? = durationSec?.let { secs ->
        if (secs > 0) (totalKJ * 1000.0) / secs else null
    }

    // Base estimate
    val baseKcal = when {
        userProfile?.massKg != null && avgPowerW != null && durationSec != null ->
            kcalFromACSM(avgPowerW, userProfile.massKg, durationSec, gross = false)
        else ->
            kcalFromPowerOnly(totalKJ, mechEff)
    }

    // Optional HR blend (needs avgHR, mass, age, gender, duration)
    val blended = if (
        avgHr != null &&
        userProfile?.massKg != null &&
        userProfile.age != null &&
        userProfile.gender != Gender.Unspecified &&
        durationSec != null
    ) {
        val hrOnly = kcalFromHeartRateKeytel(
            avgHr = avgHr,
            massKg = userProfile.massKg,
            age = userProfile.age,
            male = userProfile.gender == Gender.Male,
            durationSec = durationSec
        )
        (1.0 - hrWeight) * baseKcal + hrWeight * hrOnly
    } else {
        baseKcal
    }

    return blended.coerceAtLeast(0.0)
}

/** Power-only: human energy ~= mechanical work / efficiency. 1 kcal = 4.184 kJ. */
fun kcalFromPowerOnly(totalKJ: Double, mechEff: Double = 0.24): Double {
    val eff = mechEff.coerceIn(0.15, 0.30)
    return (totalKJ / eff) / 4.184
}

/**
 * ACSM cycling:
 * WR[kg·m/min] = 6.12 * P(W)
 * VO2[ml/kg/min] = 1.8 * WR / mass + (gross? 7 : 0)  // net excludes resting/unloaded cost
 * kcal/min = VO2 * mass * 5 / 1000
 */
fun kcalFromACSM(avgPowerW: Double, massKg: Double, durationSec: Int, gross: Boolean = false): Double {
    if (avgPowerW <= 0.0 || massKg <= 0.0 || durationSec <= 0) return 0.0
    val wr = 6.12 * avgPowerW
    val add = if (gross) 7.0 else 0.0
    val vo2 = 1.8 * (wr / massKg) + add
    val kcalPerMin = vo2 * massKg * 5.0 / 1000.0
    return kcalPerMin * (durationSec / 60.0)
}

/** Keytel et al. 2005, kcal/min from HR, mass, age, sex. */
fun kcalFromHeartRateKeytel(
    avgHr: Int,
    massKg: Double,
    age: Int,
    male: Boolean,
    durationSec: Int
): Double {
    if (avgHr <= 0 || massKg <= 0.0 || age <= 0 || durationSec <= 0) return 0.0
    val perMin = if (male) {
        (-55.0969 + 0.6309 * avgHr + 0.1988 * massKg + 0.2017 * age) / 4.184
    } else {
        (-20.4022 + 0.4472 * avgHr - 0.1263 * massKg + 0.074 * age) / 4.184
    }
    return max(0.0, perMin) * (durationSec / 60.0)
}
