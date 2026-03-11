package com.reink.ui.home

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DateSection<T>(
    val dateHeader: String,
    val items: List<T>,
)

private val dayWithDateFormat = SimpleDateFormat("EEEE, MMM d", Locale.US)
private val dateFormat = SimpleDateFormat("MMM d", Locale.US)

fun <T> groupByDate(
    items: List<T>,
    timestampSelector: (T) -> Long,
): List<DateSection<T>> {
    if (items.isEmpty()) return emptyList()

    val calendar = Calendar.getInstance()
    val today = clearTime(calendar)
    calendar.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = calendar.timeInMillis

    return items.groupBy { item ->
        val ts = timestampSelector(item)
        val itemCal = Calendar.getInstance().apply { timeInMillis = ts }
        val itemDay = clearTime(itemCal)

        when {
            itemDay >= today ->
                "Today, ${dateFormat.format(Date(ts))}"
            itemDay >= yesterday ->
                "Yesterday, ${dateFormat.format(Date(ts))}"
            itemDay >= today - 6 * 24 * 60 * 60 * 1000L ->
                dayWithDateFormat.format(Date(ts))
            else -> dateFormat.format(Date(ts))
        }
    }.map { (header, sectionItems) -> DateSection(dateHeader = header, items = sectionItems) }
}

private fun clearTime(cal: Calendar): Long {
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
