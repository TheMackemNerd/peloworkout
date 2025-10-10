package com.digitalelysium.peloworkout.ui.util

import java.util.Locale

internal fun fmt(v: Double?, dp: Int): String = if (v == null) "--" else String.format(Locale.UK, "%.${dp}f", v)
internal fun formatElapsed(sec: Int): String {
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}