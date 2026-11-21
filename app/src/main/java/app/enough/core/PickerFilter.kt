package app.enough.core

data class AppInfo(val pkg: String, val label: String)

object PickerFilter {
    fun visible(apps: List<AppInfo>, excluded: Set<String>, query: String): List<AppInfo> {
        val q = query.trim()
        return apps.filter { it.pkg !in excluded && it.label.contains(q, ignoreCase = true) }
            .sortedBy { it.label.lowercase() }
    }
}
