package com.digitalelysium.peloworkout.ui.util

import com.digitalelysium.peloworkout.data.Gender
import com.digitalelysium.peloworkout.data.UserProfile
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Primary entry point.
 * totalKJ: total mechanical work (kJ) accumulated this session.
 * heartRateHistory: per-second HR samples (nullable Int). Length is used as duration.
 * profile: optional mass/age/gender.
 *
 * Fallback order:
 *  - ACSM (needs mass + duration) or Power-only (24% mech. efficiency) as the base
 *  - If HR + mass + age + gender exist, blend in a Heart-Rate estimate (Keytel) with small weight
 */
fun CalorieCalculation(
    totalKJ: Double,
    heartRateHistory: List<Int?>? = null,
    profile: UserProfile? = null,
    mechEff: Double = 0.24,     // keep your current behaviour by default
    hrWeight: Double = 0.25     // modest influence from HR to correct day-to-day drift
): Double {
    if (totalKJ <= 0.0) return 0.0

    // Derive duration and averages from inputs we actually have
    val durationSec: Int? = heartRateHistory?.size?.takeIf { it > 0 }
    val avgHr: Int? = heartRateHistory
        ?.filterNotNull()
        ?.average()
        ?.takeIf { !it.isNaN() }
        ?.roundToInt()

    val avgPowerW: Double? = durationSec?.let { secs ->
        if (secs > 0) (totalKJ * 1000.0) / secs else null
    }

    // Base estimate
    val baseKcal = when {
        profile?.massKg != null && avgPowerW != null && durationSec != null ->
            kcalFromACSM(avgPowerW, profile.massKg, durationSec)
        else ->
            kcalFromPowerOnly(totalKJ, mechEff)
    }

    // Optional HR correction (requires enough context)
    val hrAdjusted = if (
        avgHr != null &&
        profile?.massKg != null &&
        profile.age != null &&
        profile.gender != Gender.Unspecified &&
        durationSec != null
    ) {
        val hrOnly = kcalFromHeartRateKeytel(
            avgHr = avgHr,
            massKg = profile.massKg,
            age = profile.age,
            male = profile.gender == Gender.Male,
            durationSec = durationSec
        )
        // Blend, keeping power-based as the anchor
        (1.0 - hrWeight) * baseKcal + hrWeight * hrOnly
    } else {
        baseKcal
    }

    return hrAdjusted.coerceAtLeast(0.0)
}

/** Power-only fallback. Human energy ~= mechanical work / efficiency. 1 kcal = 4.184 kJ. */
fun kcalFromPowerOnly(totalKJ: Double, mechEff: Double = 0.24): Double {
    val eff = mechEff.coerceIn(0.15, 0.30) // plausible bounds
    return (totalKJ / eff) / 4.184
}

/** ACSM cycling equation: WR[kg·m/min] = 6.12*P; VO2[ml/kg/min] = 1.8*WR/M + 7; kcal/min = VO2*M*5/1000 */
fun kcalFromACSM(avgPowerW: Double, massKg: Double, durationSec: Int): Double {
    if (avgPowerW <= 0.0 || massKg <= 0.0 || durationSec <= 0) return 0.0
    val wr = 6.12 * avgPowerW                     // kg·m/min
    val vo2 = 1.8 * (wr / massKg) + 7.0           // ml/kg/min (gross, includes resting)
    val kcalPerMin = vo2 * massKg * 5.0 / 1000.0
    return kcalPerMin * (durationSec / 60.0)
}

/** Keytel et al. 2005 regression, kcal/min from HR, mass, age, sex. */
fun kcalFromHeartRateKeytel(
    avgHr: Int,
    massKg: Double,
    age: Int,
    male: Boolean,
    durationSec: Int
): Double {
    if (avgHr <= 0 || massKg <= 0.0 || age <= 0 || durationSec <= 0) return 0.0
    val perMin = if (male) {
        // kcal/min (already energy units), convert from joules-based coefficients by dividing by 4.184
        (-55.0969 + 0.6309 * avgHr + 0.1988 * massKg + 0.2017 * age) / 4.184
    } else {
        (-20.4022 + 0.4472 * avgHr - 0.1263 * massKg + 0.074 * age) / 4.184
    }
    return max(0.0, perMin) * (durationSec / 60.0)
}


