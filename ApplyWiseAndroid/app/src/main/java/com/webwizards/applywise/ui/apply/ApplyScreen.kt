package com.webwizards.applywise.ui.apply

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.webwizards.applywise.domain.Aps
import com.webwizards.applywise.ui.components.DropdownField
import com.webwizards.applywise.ui.components.LoadingState
import com.webwizards.applywise.ui.navigation.appContainer

private val STEP_TITLES = listOf("About you", "Subjects & grades", "Interests", "Review")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplyScreen(onBack: () -> Unit, onSubmitted: (String) -> Unit, onSettings: () -> Unit, onSignIn: () -> Unit) {
    val container = appContainer()
    val vm: ApplyViewModel = viewModel(factory = viewModelFactory {
        initializer { ApplyViewModel(container.studentRepository, container.authRepository) }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()

    // One-shot navigation once the server has accepted the application.
    LaunchedEffect(state.submittedRef) {
        state.submittedRef?.let { ref -> vm.consumeSubmitted(); onSubmitted(ref) }
    }
    // System back moves to the previous step before leaving the form.
    BackHandler(enabled = state.step > 0 && !state.submitting) { vm.back() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(STEP_TITLES[state.step], style = MaterialTheme.typography.titleLarge)
                            Text("Step ${state.step + 1} of ${ApplyUiState.STEPS}", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { if (!vm.back()) onBack() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
                LinearProgressIndicator(progress = { (state.step + 1) / ApplyUiState.STEPS.toFloat() }, modifier = Modifier.fillMaxWidth())
            }
        },
        bottomBar = { WizardBar(state, onBack = { if (!vm.back()) onBack() }, onNext = vm::next) }
    ) { padding ->
        if (!state.loaded) { LoadingState("Loading your saved progress…", Modifier.padding(padding)); return@Scaffold }
        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { it * dir / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir / 4 } + fadeOut())
            },
            label = "wizard-step",
            modifier = Modifier.padding(padding).fillMaxSize()
        ) { step ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (step) {
                    0 -> PersonalStep(state, vm)
                    1 -> SubjectsStep(state, vm)
                    2 -> InterestsStep(state, vm)
                    else -> { AccountNote(user, onSignIn); ReviewStep(state, onEdit = vm::goTo) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    state.submitError?.let { message ->
        AlertDialog(
            onDismissRequest = vm::dismissError,
            title = { Text(if (state.submitErrorIsNetwork) "Can't reach the server" else "Please check your details") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = vm::dismissError) { Text("OK") } },
            dismissButton = if (state.submitErrorIsNetwork) {
                { TextButton(onClick = { vm.dismissError(); onSettings() }) { Text("Server settings") } }
            } else null
        )
    }
}

@Composable
private fun WizardBar(state: ApplyUiState, onBack: () -> Unit, onNext: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack, enabled = !state.submitting) { Text(if (state.step == 0) "Cancel" else "Back") }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Text("APS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${state.aps}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }
            Button(onClick = onNext, enabled = !state.submitting) {
                if (state.submitting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp)); Text("Matching…")
                } else Text(if (state.step == ApplyUiState.STEPS - 1) "Find my matches" else "Next")
            }
        }
    }
}

@Composable
private fun PersonalStep(state: ApplyUiState, vm: ApplyViewModel) {
    Text("Basic details to personalise your recommendations.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
        value = state.firstName, onValueChange = vm::setFirstName, label = { Text("First name") }, singleLine = true,
        isError = "firstName" in state.fieldErrors, supportingText = state.fieldErrors["firstName"]?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = state.lastName, onValueChange = vm::setLastName, label = { Text("Last name") }, singleLine = true,
        isError = "lastName" in state.fieldErrors, supportingText = state.fieldErrors["lastName"]?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth()
    )
    DropdownField("Province", state.province.ifBlank { null }, Aps.PROVINCES, vm::setProvince,
        placeholder = "Select your province", error = state.fieldErrors["province"])
    DropdownField("Matric year", state.matricYear, Aps.matricYears(), vm::setMatricYear,
        placeholder = "Select year", error = state.fieldErrors["matricYear"])
}

@Composable
private fun SubjectsStep(state: ApplyUiState, vm: ApplyViewModel) {
    Text("Enter each subject and your percentage. Your APS uses your best ${Aps.COUNTED_SUBJECTS} subjects, excluding Life Orientation.",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    ApsSummary(state)
    state.subjects.forEach { row ->
        val gradeInvalid = row.gradeInt?.let { it > 100 } == true
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DropdownField(
                label = "Subject", selected = row.name.ifBlank { null }, options = Aps.SUBJECTS,
                onSelect = { vm.setSubjectName(row.id, it) }, placeholder = "Choose", modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = row.grade, onValueChange = { vm.setSubjectGrade(row.id, it) }, label = { Text("%") }, singleLine = true,
                isError = gradeInvalid, supportingText = if (gradeInvalid) { { Text("0–100") } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.width(84.dp)
            )
            IconButton(onClick = { vm.removeSubject(row.id) }, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = "Remove ${row.name.ifBlank { "subject" }}")
            }
        }
    }
    OutlinedButton(onClick = vm::addSubject, enabled = state.canAddSubject, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Add, contentDescription = null); Spacer(Modifier.width(8.dp))
        Text(if (state.canAddSubject) "Add another subject" else "Maximum ${Aps.MAX_SUBJECTS} subjects")
    }
    if (state.subjectErrors.isNotEmpty()) ErrorCard(state.subjectErrors)
}

@Composable
private fun ApsSummary(state: ApplyUiState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your APS score", style = MaterialTheme.typography.labelLarge)
                Text(
                    if (state.nonLoCount > Aps.COUNTED_SUBJECTS) "Best ${Aps.COUNTED_SUBJECTS} of your ${state.nonLoCount} subjects counted"
                    else "Updates as you type · max ${Aps.MAX_APS}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text("${state.aps}", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterestsStep(state: ApplyUiState, vm: ApplyViewModel) {
    Text("Select every field you're curious about. This ranks your matches.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Aps.INTERESTS.forEach { interest ->
            val selected = interest in state.interests
            FilterChip(
                selected = selected, onClick = { vm.toggleInterest(interest) }, label = { Text(interest) },
                leadingIcon = if (selected) { { Icon(Icons.Outlined.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) } } else null
            )
        }
    }
    OutlinedTextField(
        value = state.careerGoal, onValueChange = vm::setCareerGoal, label = { Text("Career in mind (optional)") },
        placeholder = { Text("e.g. Software developer, Doctor, Teacher") }, singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth()
    )
    DropdownField(
        label = "Preferred study location (optional)", selected = state.studyLocation.ifBlank { null }, options = Aps.PROVINCES,
        onSelect = vm::setStudyLocation, placeholder = "No preference",
        leadingOption = "No preference" to { vm.setStudyLocation("") }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewStep(state: ApplyUiState, onEdit: (Int) -> Unit) {
    Text("Double-check everything before we find your matches.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    ReviewCard("Personal", onEdit = { onEdit(0) }) {
        Text("${state.firstName} ${state.lastName}", style = MaterialTheme.typography.titleMedium)
        Text("${state.province} · Matric ${state.matricYear}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    ReviewCard("Subjects & grades", onEdit = { onEdit(1) }) {
        val counted = state.countedSubjects
        state.subjects.forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider()
            val note = when {
                s.name == Aps.EXCLUDED_SUBJECT -> "not counted"
                s.name !in counted -> "not in best ${Aps.COUNTED_SUBJECTS}"
                else -> "${Aps.gradeToPoints(s.gradeInt)} pts"
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text("${s.grade}%  ", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text("Total APS", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text("${state.aps}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
    ReviewCard("Interests", onEdit = { onEdit(2) }) {
        if (state.interests.isEmpty()) Text("None selected — results will be ranked by APS only.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.interests.forEach { com.webwizards.applywise.ui.components.InfoChip(it) }
        }
        if (state.careerGoal.isNotBlank()) Text("Career goal: ${state.careerGoal}", Modifier.padding(top = 8.dp))
        if (state.studyLocation.isNotBlank()) Text("Preferred location: ${state.studyLocation}")
    }
}

/** Tells the student whether this application will be saved to an account. */
@Composable
private fun AccountNote(user: com.webwizards.applywise.data.remote.UserDto?, onSignIn: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (user != null) "Signed in as ${user.username} — this application will be saved to your account."
                else "Not signed in — sign in to keep this application in your account.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)
            )
            if (user == null) TextButton(onClick = onSignIn) { Text("Sign in") }
        }
    }
}

@Composable
private fun ReviewCard(title: String, onEdit: () -> Unit, content: @Composable () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Edit") }
            }
            content()
        }
    }
}

@Composable
private fun ErrorCard(errors: List<String>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp).semantics { liveRegion = LiveRegionMode.Assertive }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            errors.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
