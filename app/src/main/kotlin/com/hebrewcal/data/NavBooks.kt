package com.hebrewcal.data

/**
 * The books the reader's navigator can jump into besides the Torah and Tehillim: the Prophets (what
 * a haftarah is read from) and the five parts of Tanya. Chapter counts are Sefaria's, which follow
 * the Jewish division (Joel has 4, Malachi 3).
 */
object NavBooks {
    /** [ref] is the Sefaria book name: chapters are "ref.N" for the Prophets and "ref N" for Tanya. */
    data class Book(val en: String, val he: String, val ref: String, val chapters: Int)
    data class Group(val en: String, val he: String, val books: List<Book>)

    private fun b(en: String, he: String, chapters: Int, ref: String = en) = Book(en, he, ref, chapters)

    val PROPHETS: List<Group> = listOf(
        Group("Early Prophets", "נביאים ראשונים", listOf(
            b("Joshua", "יהושע", 24), b("Judges", "שופטים", 21),
            b("I Samuel", "שמואל א׳", 31), b("II Samuel", "שמואל ב׳", 24),
            b("I Kings", "מלכים א׳", 22), b("II Kings", "מלכים ב׳", 25)
        )),
        Group("Later Prophets", "נביאים אחרונים", listOf(
            b("Isaiah", "ישעיהו", 66), b("Jeremiah", "ירמיהו", 52), b("Ezekiel", "יחזקאל", 48)
        )),
        Group("Twelve", "תרי עשר", listOf(
            b("Hosea", "הושע", 14), b("Joel", "יואל", 4), b("Amos", "עמוס", 9), b("Obadiah", "עובדיה", 1),
            b("Jonah", "יונה", 4), b("Micah", "מיכה", 7), b("Nahum", "נחום", 3), b("Habakkuk", "חבקוק", 3),
            b("Zephaniah", "צפניה", 3), b("Haggai", "חגי", 2), b("Zechariah", "זכריה", 14), b("Malachi", "מלאכי", 3)
        ))
    )

    /** The five parts of Tanya, in order; [Book.en] is the short name shown on the chips. */
    val TANYA: List<Book> = listOf(
        Book("Likkutei Amarim", "ליקוטי אמרים", "Tanya, Part I; Likkutei Amarim", 53),
        Book("Shaar HaYichud", "שער היחוד", "Tanya, Part II; Shaar HaYichud VehaEmunah", 12),
        Book("Iggeret HaTeshuvah", "אגרת התשובה", "Tanya, Part III; Iggeret HaTeshuvah", 12),
        Book("Iggeret HaKodesh", "אגרת הקודש", "Tanya, Part IV; Iggeret HaKodesh", 32),
        Book("Kuntres Acharon", "קונטרס אחרון", "Tanya, Part V; Kuntres Acharon", 9)
    )

    fun prophetRef(book: Book, chapter: Int) = "${book.ref}.$chapter"
    fun tanyaRef(part: Book, chapter: Int) = "${part.ref} $chapter"

    /** Where a Prophets ref ("Obadiah.1.1-1.21", "I_Samuel.1", "Hosea 12:13-14:10") lies: group, book in it, chapter. */
    fun locateProphet(ref: String): Triple<Int, Int, Int>? {
        val r = ref.replace('_', ' ').trim()
        PROPHETS.forEachIndexed { g, group ->
            group.books.forEachIndexed { i, bk ->
                if (r.startsWith(bk.ref) && (r.length == bk.ref.length || r[bk.ref.length] == '.' || r[bk.ref.length] == ' ')) {
                    val ch = r.drop(bk.ref.length + 1).takeWhile { it.isDigit() }.toIntOrNull() ?: 1
                    return Triple(g, i, ch.coerceIn(1, bk.chapters))
                }
            }
        }
        return null
    }

    /**
     * Where a Tanya ref ("Tanya, Part I; Likkutei Amarim 4:35-4:40") lies: part index and chapter. A
     * titled node of a part ("…Likkutei Amarim, Compiler's Foreword 9") is that part's chapter 1.
     */
    fun locateTanya(ref: String): Pair<Int, Int>? {
        val r = ref.trim()
        TANYA.forEachIndexed { i, part ->
            if (r.startsWith(part.ref)) {
                val rest = r.drop(part.ref.length)
                val ch = if (rest.startsWith(" ")) rest.drop(1).takeWhile { it.isDigit() }.toIntOrNull() else null
                return i to (ch ?: 1).coerceIn(1, part.chapters)
            }
        }
        return null
    }
}
