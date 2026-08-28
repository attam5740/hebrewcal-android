package com.hebrewcal.data

data class TehillimPortion(
    val sefariaRef: String,
    val firstChapter: Int,
    val lastChapter: Int
)

/** Standard siddur day-of-month division of Tehillim. */
object TehillimSchedule {
    // day -> (firstChapter, lastChapter) for a full 30-day month.
    private val ranges: Map<Int, Pair<Int, Int>> = mapOf(
        1 to (1 to 9), 2 to (10 to 17), 3 to (18 to 22), 4 to (23 to 28), 5 to (29 to 34),
        6 to (35 to 38), 7 to (39 to 43), 8 to (44 to 48), 9 to (49 to 54), 10 to (55 to 59),
        11 to (60 to 65), 12 to (66 to 68), 13 to (69 to 71), 14 to (72 to 76), 15 to (77 to 78),
        16 to (79 to 82), 17 to (83 to 87), 18 to (88 to 89), 19 to (90 to 96), 20 to (97 to 103),
        21 to (104 to 105), 22 to (106 to 107), 23 to (108 to 112), 24 to (113 to 118),
        // 25 & 26 are verse-spans within chapter 119 — handled specially below.
        27 to (120 to 134), 28 to (135 to 139), 29 to (140 to 144), 30 to (145 to 150)
    )

    fun portionFor(hebrewDay: Int, daysInMonth: Int): TehillimPortion {
        when (hebrewDay) {
            25 -> return TehillimPortion("Psalms.119.1-96", 119, 119)
            26 -> return TehillimPortion("Psalms.119.97-176", 119, 119)
            29 -> if (daysInMonth == 29) return TehillimPortion("Psalms.140-150", 140, 150)
        }
        val (first, last) = ranges[hebrewDay] ?: ranges.getValue(30)
        return TehillimPortion("Psalms.$first-$last", first, last)
    }
}
