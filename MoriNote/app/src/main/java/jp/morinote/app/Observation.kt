package jp.morinote.app

import java.util.Calendar

enum class Season(val label: String, val range: String) {
    SPRING("春", "3–5月"), SUMMER("夏", "6–8月"), AUTUMN("秋", "9–11月"), WINTER("冬", "12–2月");

    companion object {
        fun fromDate(timeMillis: Long): Season {
            val month = Calendar.getInstance().apply { timeInMillis = timeMillis }.get(Calendar.MONTH) + 1
            return when (month) {
                in 3..5 -> SPRING
                in 6..8 -> SUMMER
                in 9..11 -> AUTUMN
                else -> WINTER
            }
        }
    }
}

data class Observation(
    val id: String,
    val name: String,
    val photoPath: String,
    val dateMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val place: String = "",
) {
    val season: Season get() = Season.fromDate(dateMillis)
}
