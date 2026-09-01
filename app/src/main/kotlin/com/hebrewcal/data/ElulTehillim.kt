package com.hebrewcal.data

/**
 * The customary additional Tehillim of the repentance season (Baal Shem Tov):
 * three sequential chapters daily from Rosh Chodesh Elul (day 1 → 1-3, day 2 →
 * 4-6, …) through 9 Tishrei, and the remaining 36 chapters (115-150) on Yom
 * Kippur, completing the book.
 *
 * KosherJava month numbering: ELUL = 6, TISHREI = 7 (Elul always has 29 days).
 */
object ElulTehillim {
    private const val ELUL = 6
    private const val TISHREI = 7

    fun portionFor(jewishMonth: Int, jewishDay: Int): TehillimPortion? {
        val n = when {
            jewishMonth == ELUL -> jewishDay                       // 1..29 → chapters 1-87
            jewishMonth == TISHREI && jewishDay <= 9 -> 29 + jewishDay  // 30..38 → 88-114
            jewishMonth == TISHREI && jewishDay == 10 ->            // Yom Kippur: finish the book
                return TehillimPortion("Psalms.115-150", 115, 150)
            else -> return null
        }
        val first = 3 * n - 2
        return TehillimPortion("Psalms.$first-${first + 2}", first, first + 2)
    }
}
