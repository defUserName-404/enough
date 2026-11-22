package app.enough.common.domain

import java.time.Duration

fun mmss(d: Duration): String {
    val total = maxOf(0L, d.seconds)
    return "%02d:%02d".format(total / 60, total % 60)
}

fun hms(d: Duration): String {
    val total = maxOf(0L, d.seconds)
    return "%02d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
}

fun limitMmss(min: Int): String = mmss(Duration.ofMinutes(min.toLong()))

/** "4H 01M", "12M", "0M" (minutes floored). */
fun dur(d: Duration): String {
    val m = maxOf(0L, d.toMinutes())
    return if (m >= 60) "%dH %02dM".format(m / 60, m % 60) else "${m}M"
}

fun durMin(min: Int): String = dur(Duration.ofMinutes(min.toLong()))

/** "23H 12M LEFT", "12M LEFT", "<1M LEFT". */
fun left(d: Duration): String = if (d.toMinutes() < 1) "<1M LEFT" else dur(d) + " LEFT"

/** Chart axis label, floored like [dur]: 59M stays 59M, 90M becomes 1H. */
fun axisLabel(d: Duration): String {
    val m = maxOf(0L, d.toMinutes())
    return if (m >= 60) "${m / 60}H" else "${m}M"
}
