package com.webwizards.applywise.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors the web project's tests/aps.test.js so both platforms apply identical rules. */
class ApsTest {
    private fun s(name: String, grade: Int?) = SubjectGrade(name, grade)
    private val valid = listOf(
        s("English Home Language", 60), s("Mathematics", 60), s("Physical Sciences", 60),
        s("Life Sciences", 60), s("Geography", 60), s("History", 60)
    )

    @Test fun nscLevels() {
        assertEquals(7, Aps.gradeToPoints(100)); assertEquals(7, Aps.gradeToPoints(80))
        assertEquals(6, Aps.gradeToPoints(79)); assertEquals(4, Aps.gradeToPoints(50))
        assertEquals(2, Aps.gradeToPoints(30)); assertEquals(1, Aps.gradeToPoints(29))
        assertEquals(1, Aps.gradeToPoints(0)); assertEquals(0, Aps.gradeToPoints(-5)); assertEquals(0, Aps.gradeToPoints(null))
    }

    @Test fun bestSixExcludingLifeOrientation() {
        val subjects = listOf("A", "B", "C", "D", "E", "F", "G").mapIndexed { i, n -> s(n, 85 - i * 10) }
        assertEquals(27, Aps.calculateAps(subjects))
        assertEquals(27, Aps.calculateAps(subjects + s("Life Orientation", 99)))
    }

    @Test fun duplicatesBlanksAndMaximum() {
        assertEquals(7, Aps.calculateAps(listOf(s("Mathematics", 90), s("Mathematics", 90))))
        assertEquals(0, Aps.calculateAps(listOf(s("History", null))))
        assertEquals(42, Aps.calculateAps(Aps.SUBJECTS.map { s(it, 100) }))
    }

    @Test fun validationRules() {
        assertTrue(Aps.validateSubjects(valid).isEmpty())
        assertTrue(Aps.validateSubjects(emptyList()).isNotEmpty())
        assertTrue("needs 6 non-LO", Aps.validateSubjects(valid.take(5)).isNotEmpty())
        assertTrue("duplicate", Aps.validateSubjects(valid + s("Mathematics", 70)).isNotEmpty())
        assertTrue("maths + maths lit", Aps.validateSubjects(valid + s("Mathematical Literacy", 70)).isNotEmpty())
        assertTrue("no home language", Aps.validateSubjects(valid.drop(1) + s("Economics", 60)).isNotEmpty())
        assertTrue("grade > 100", Aps.validateSubjects(valid.take(5) + s("History", 101)).isNotEmpty())
        assertTrue("unknown subject", Aps.validateSubjects(valid.take(5) + s("Basket Weaving", 60)).isNotEmpty())
    }

    @Test fun countedSubjectsShowsBestSix() {
        val subjects = valid + s("Economics", 95) + s("Life Orientation", 99)
        val counted = Aps.countedSubjects(subjects)
        assertEquals(6, counted.size)
        assertTrue("Economics" in counted)
        assertTrue("Life Orientation" !in counted)
    }

    @Test fun nameValidation() {
        assertTrue(Aps.isValidName("Naledi")); assertTrue(Aps.isValidName("O'Neil-Khumalo")); assertTrue(Aps.isValidName("Zoë"))
        assertTrue(!Aps.isValidName("<img src=x>")); assertTrue(!Aps.isValidName("")); assertTrue(!Aps.isValidName("1abc"))
    }
}
