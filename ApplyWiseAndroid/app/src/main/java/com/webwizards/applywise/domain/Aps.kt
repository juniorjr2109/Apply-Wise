package com.webwizards.applywise.domain

import java.time.Year

/**
 * Kotlin port of the web app's shared/aps.js. Keep the two in step: the
 * server re-validates every submission, so any drift shows up as a 422 and
 * the parity tests in ApsTest mirror tests/aps.test.js.
 */
object Aps {
    val SUBJECTS: List<String> = listOf(
        "English Home Language", "English First Additional Language",
        "Afrikaans Home Language", "Afrikaans First Additional Language",
        "IsiZulu Home Language", "IsiXhosa Home Language", "Sesotho Home Language",
        "Setswana Home Language", "Sepedi Home Language", "Xitsonga Home Language",
        "Siswati Home Language", "Tshivenda Home Language", "IsiNdebele Home Language",
        "Mathematics", "Mathematical Literacy", "Technical Mathematics",
        "Physical Sciences", "Technical Sciences", "Life Sciences",
        "Geography", "History", "Accounting", "Business Studies", "Economics",
        "Life Orientation", "Computer Applications Technology", "Information Technology",
        "Tourism", "Consumer Studies", "Hospitality Studies", "Agricultural Sciences",
        "Visual Arts", "Dramatic Arts", "Music", "Civil Technology",
        "Mechanical Technology", "Electrical Technology", "Engineering Graphics & Design"
    )

    val INTERESTS: List<String> = listOf(
        "Engineering & Technology", "Health Sciences", "Business & Commerce", "Law",
        "Education & Teaching", "Arts & Humanities", "Natural Sciences", "Social Sciences",
        "Agriculture & Environment", "Architecture & Design", "Media & Communication",
        "Finance & Accounting"
    )

    val PROVINCES: List<String> = listOf(
        "Gauteng", "Western Cape", "KwaZulu-Natal", "Eastern Cape", "Limpopo",
        "Mpumalanga", "North West", "Free State", "Northern Cape"
    )

    const val EXCLUDED_SUBJECT = "Life Orientation"
    private val MATHS_VARIANTS = listOf("Mathematics", "Mathematical Literacy", "Technical Mathematics")
    const val COUNTED_SUBJECTS = 6
    const val MIN_COUNTED_SUBJECTS = 6
    const val MAX_SUBJECTS = 10
    const val MAX_APS = 42

    val DEFAULT_SUBJECTS = listOf(
        "English Home Language", "Mathematics", "Physical Sciences",
        "Life Sciences", "Geography", "History", "Life Orientation"
    )

    private val subjectSet = SUBJECTS.toHashSet()
    private val nameRegex = Regex("^\\p{L}[\\p{L}\\p{M}' ’.-]{0,49}$")

    fun isSubject(s: String) = s in subjectSet
    fun isProvince(s: String) = s in PROVINCES
    fun isValidName(s: String) = nameRegex.matches(s.trim())

    /** NSC achievement level: 80–100 = 7 … 0–29 = 1. */
    fun gradeToPoints(percentage: Int?): Int = when {
        percentage == null || percentage < 0 -> 0
        percentage >= 80 -> 7
        percentage >= 70 -> 6
        percentage >= 60 -> 5
        percentage >= 50 -> 4
        percentage >= 40 -> 3
        percentage >= 30 -> 2
        else -> 1
    }

    /** Best six subject levels, excluding Life Orientation; duplicates and blanks ignored. */
    fun calculateAps(subjects: List<SubjectGrade>): Int {
        val seen = HashSet<String>()
        return subjects.asSequence()
            .filter { it.name.isNotBlank() && it.name != EXCLUDED_SUBJECT && it.grade != null && seen.add(it.name) }
            .map { gradeToPoints(it.grade) }
            .sortedDescending()
            .take(COUNTED_SUBJECTS)
            .sum()
    }

    /** Names of the subjects that actually count towards the APS. */
    fun countedSubjects(subjects: List<SubjectGrade>): Set<String> =
        subjects.filter { it.name != EXCLUDED_SUBJECT && it.grade != null }
            .distinctBy { it.name }
            .sortedByDescending { gradeToPoints(it.grade) }
            .take(COUNTED_SUBJECTS)
            .map { it.name }
            .toSet()

    /** Same rules as validateSubjects() in shared/aps.js. Empty list = valid. */
    fun validateSubjects(subjects: List<SubjectGrade>): List<String> {
        if (subjects.isEmpty()) return listOf("Add your subjects and grades.")
        val errors = mutableListOf<String>()
        if (subjects.size > MAX_SUBJECTS) errors += "You can enter at most $MAX_SUBJECTS subjects."
        val seen = LinkedHashSet<String>()
        subjects.forEachIndexed { i, s ->
            val row = i + 1
            when {
                s.name.isBlank() -> errors += "Row $row: choose a subject."
                !isSubject(s.name) -> errors += "Row $row: \"${s.name}\" is not a recognised NSC subject."
                s.name in seen -> errors += "${s.name} is listed more than once."
                s.grade == null || s.grade !in 0..100 -> errors += "${s.name}: enter a percentage between 0 and 100."
                else -> seen += s.name
            }
        }
        val maths = MATHS_VARIANTS.filter { it in seen }
        if (maths.size > 1) errors += "Choose only one of: ${maths.joinToString()}."
        if (seen.isNotEmpty() && seen.none { it.endsWith("Home Language") }) errors += "Include your Home Language subject."
        if (errors.isEmpty() && seen.count { it != EXCLUDED_SUBJECT } < MIN_COUNTED_SUBJECTS) {
            errors += "Enter at least $MIN_COUNTED_SUBJECTS subjects besides Life Orientation."
        }
        return errors
    }

    fun matricYears(currentYear: Int = Year.now().value): List<Int> = (currentYear + 1 downTo currentYear - 10).toList()
}

data class SubjectGrade(val name: String, val grade: Int?)
