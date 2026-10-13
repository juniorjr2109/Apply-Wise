package com.webwizards.applywise.ui.apply

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webwizards.applywise.data.local.ApplyDraft
import com.webwizards.applywise.data.local.DraftSubject
import com.webwizards.applywise.data.remote.SubjectDto
import com.webwizards.applywise.data.remote.SubmitRequest
import com.webwizards.applywise.data.remote.UserDto
import com.webwizards.applywise.data.repository.AuthRepository
import com.webwizards.applywise.data.repository.StudentRepository
import com.webwizards.applywise.domain.AppException
import com.webwizards.applywise.domain.Aps
import com.webwizards.applywise.domain.SubjectGrade
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

@Immutable
data class SubjectRow(val id: Long, val name: String = "", val grade: String = "") {
    val gradeInt: Int? get() = grade.toIntOrNull()
}

@Immutable
data class ApplyUiState(
    val loaded: Boolean = false,
    val step: Int = 0,
    val firstName: String = "",
    val lastName: String = "",
    val province: String = "",
    val matricYear: Int? = null,
    val subjects: List<SubjectRow> = emptyList(),
    val interests: Set<String> = emptySet(),
    val careerGoal: String = "",
    val studyLocation: String = "",
    val fieldErrors: Map<String, String> = emptyMap(),
    val subjectErrors: List<String> = emptyList(),
    val submitting: Boolean = false,
    val submitError: String? = null,
    val submitErrorIsNetwork: Boolean = false,
    val submittedRef: String? = null,
    val idempotencyKey: String = UUID.randomUUID().toString()
) {
    val grades: List<SubjectGrade> get() = subjects.map { SubjectGrade(it.name, it.gradeInt) }
    val aps: Int get() = Aps.calculateAps(grades.filter { it.name.isNotBlank() })
    val countedSubjects: Set<String> get() = Aps.countedSubjects(grades.filter { it.name.isNotBlank() })
    val nonLoCount: Int get() = subjects.count { it.name.isNotBlank() && it.name != Aps.EXCLUDED_SUBJECT && it.gradeInt != null }
    val canAddSubject: Boolean get() = subjects.size < Aps.MAX_SUBJECTS

    companion object { const val STEPS = 4 }
}

@OptIn(FlowPreview::class)
class ApplyViewModel(private val repo: StudentRepository, auth: AuthRepository) : ViewModel() {
    /** Signed-in user: their names pre-fill the form and the application is saved to their account. */
    val user: StateFlow<UserDto?> = auth.user
    private val _state = MutableStateFlow(ApplyUiState())
    val state: StateFlow<ApplyUiState> = _state.asStateFlow()
    private var nextId = 0L
    @Volatile private var finished = false // once submitted, the draft must never be re-saved

    init {
        viewModelScope.launch {
            val draft = repo.local.loadDraft()
            val initial = draft?.toState() ?: ApplyUiState(
                loaded = true,
                subjects = Aps.DEFAULT_SUBJECTS.map { SubjectRow(nextId++, it) }
            )
            val u = user.value
            _state.value = if (u != null && initial.firstName.isBlank() && initial.lastName.isBlank())
                initial.copy(firstName = u.firstName, lastName = u.lastName) else initial
            // Persist edits (debounced) so nothing is lost if Android kills the app.
            _state.drop(1).debounce(400).collect { s -> if (!finished) repo.local.saveDraft(s.toDraft()) }
        }
    }

    /* ── Field updates: each clears that field's error ── */
    fun setFirstName(v: String) = edit("firstName") { copy(firstName = v.take(50)) }
    fun setLastName(v: String) = edit("lastName") { copy(lastName = v.take(50)) }
    fun setProvince(v: String) = edit("province") { copy(province = v) }
    fun setMatricYear(v: Int) = edit("matricYear") { copy(matricYear = v) }
    fun setCareerGoal(v: String) = edit(null) { copy(careerGoal = v.take(100)) }
    fun setStudyLocation(v: String) = edit(null) { copy(studyLocation = v) }
    fun toggleInterest(i: String) = edit(null) { copy(interests = if (i in interests) interests - i else interests + i) }

    fun setSubjectName(id: Long, name: String) = editSubject(id) { copy(name = name) }
    fun setSubjectGrade(id: Long, grade: String) {
        // Digits only, max 3 characters; range is checked on validation so the user can finish typing.
        val clean = grade.filter(Char::isDigit).take(3)
        editSubject(id) { copy(grade = clean) }
    }
    fun addSubject() { if (_state.value.canAddSubject) _state.update { it.copy(subjects = it.subjects + SubjectRow(nextId++), subjectErrors = emptyList()) } }
    fun removeSubject(id: Long) = _state.update { it.copy(subjects = it.subjects.filterNot { s -> s.id == id }, subjectErrors = emptyList()) }

    private fun edit(field: String?, change: ApplyUiState.() -> ApplyUiState) =
        _state.update { s -> s.change().copy(fieldErrors = if (field == null) s.fieldErrors else s.fieldErrors - field, submitError = null) }

    private fun editSubject(id: Long, change: SubjectRow.() -> SubjectRow) =
        _state.update { s -> s.copy(subjects = s.subjects.map { if (it.id == id) it.change() else it }, subjectErrors = emptyList(), submitError = null) }

    /* ── Navigation between steps ── */
    fun next() {
        val s = _state.value
        if (!validate(s.step)) return
        if (s.step < ApplyUiState.STEPS - 1) _state.update { it.copy(step = it.step + 1) } else submit()
    }
    fun back(): Boolean {
        if (_state.value.step == 0) return false
        _state.update { it.copy(step = it.step - 1, submitError = null) }
        return true
    }
    fun goTo(step: Int) { if (step < _state.value.step) _state.update { it.copy(step = step) } }

    private fun validate(step: Int): Boolean {
        val s = _state.value
        return when (step) {
            0 -> {
                val errors = buildMap {
                    if (!Aps.isValidName(s.firstName)) put("firstName", "Enter your first name (letters only).")
                    if (!Aps.isValidName(s.lastName)) put("lastName", "Enter your last name (letters only).")
                    if (!Aps.isProvince(s.province)) put("province", "Choose your province.")
                    if (s.matricYear == null || s.matricYear !in Aps.matricYears()) put("matricYear", "Choose your matric year.")
                }
                _state.update { it.copy(fieldErrors = errors) }
                errors.isEmpty()
            }
            1 -> {
                val errors = Aps.validateSubjects(s.grades)
                _state.update { it.copy(subjectErrors = errors) }
                errors.isEmpty()
            }
            else -> true
        }
    }

    /* ── Submit (idempotent: retries after a timeout never create duplicates) ── */
    fun submit() {
        val s = _state.value
        if (s.submitting) return
        if (!validate(0)) { _state.update { it.copy(step = 0) }; return }
        if (!validate(1)) { _state.update { it.copy(step = 1) }; return }
        _state.update { it.copy(submitting = true, submitError = null) }
        viewModelScope.launch {
            try {
                val request = SubmitRequest(
                    firstName = s.firstName.trim(), lastName = s.lastName.trim(), province = s.province,
                    matricYear = s.matricYear!!, subjects = s.subjects.map { SubjectDto(it.name, it.gradeInt!!) },
                    interests = s.interests.toList(), careerGoal = s.careerGoal.trim(), studyLocation = s.studyLocation
                )
                val result = repo.submit(request, s.idempotencyKey)
                finished = true
                repo.local.clearDraft()
                _state.update { it.copy(submitting = false, submittedRef = result.ref) }
            } catch (e: AppException) {
                val firstStepError = e.fieldErrors.keys.any { it in setOf("firstName", "lastName", "province", "matricYear") }
                _state.update {
                    it.copy(
                        submitting = false,
                        submitError = (listOf(e.message) + e.fieldErrors.values.flatten()).distinct().joinToString("\n"),
                        submitErrorIsNetwork = e.isNetwork,
                        step = when { firstStepError -> 0; "subjects" in e.fieldErrors -> 1; else -> it.step },
                        fieldErrors = e.fieldErrors.filterKeys { k -> k != "subjects" }.mapValues { (_, v) -> v.firstOrNull().orEmpty() },
                        subjectErrors = e.fieldErrors["subjects"].orEmpty()
                    )
                }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(submitError = null) }

    fun consumeSubmitted() = _state.update { it.copy(submittedRef = null) }

    /* ── Draft mapping ── */
    private fun ApplyUiState.toDraft() = ApplyDraft(
        firstName, lastName, province, matricYear, subjects.map { DraftSubject(it.name, it.grade) },
        interests.toList(), careerGoal, studyLocation, idempotencyKey
    )

    private fun ApplyDraft.toState() = ApplyUiState(
        loaded = true,
        firstName = firstName, lastName = lastName,
        province = province.takeIf(Aps::isProvince).orEmpty(),
        matricYear = matricYear?.takeIf { it in Aps.matricYears() },
        subjects = subjects.take(Aps.MAX_SUBJECTS).map { SubjectRow(nextId++, it.name.takeIf(Aps::isSubject).orEmpty(), it.grade.filter(Char::isDigit).take(3)) }
            .ifEmpty { Aps.DEFAULT_SUBJECTS.map { SubjectRow(nextId++, it) } },
        interests = interests.filter { it in Aps.INTERESTS }.toSet(),
        careerGoal = careerGoal, studyLocation = studyLocation.takeIf(Aps::isProvince).orEmpty(),
        idempotencyKey = idempotencyKey.ifBlank { UUID.randomUUID().toString() }
    )
}
