package com.webwizards.applywise.ui.admin

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webwizards.applywise.data.remote.ApplicationDetail
import com.webwizards.applywise.data.remote.ApplicationSummary
import com.webwizards.applywise.data.remote.StatsDto
import com.webwizards.applywise.data.repository.AdminRepository
import com.webwizards.applywise.data.repository.AuthRepository
import com.webwizards.applywise.domain.AppException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class DashboardUiState(
    val username: String = "",
    val stats: StatsDto? = null,
    val statsError: String? = null,
    val items: List<ApplicationSummary> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pages: Int = 1,
    val query: String = "",
    val province: String? = null,
    val listLoading: Boolean = true,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val listError: String? = null,
    val detail: ApplicationDetail? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
    val pendingDelete: ApplicationSummary? = null,
    val message: String? = null,
    /** "expired" (signed out) or "admin" (signed in, but not an administrator). */
    val signedOutReason: String? = null,
    val signedOut: Boolean = false
) {
    val canLoadMore get() = page < pages && !loadingMore && !listLoading
}

private data class Filters(val query: String, val province: String?, val reload: Int)

@OptIn(FlowPreview::class)
class DashboardViewModel(private val repo: AdminRepository, private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private val query = MutableStateFlow("")
    private val province = MutableStateFlow<String?>(null)
    private val reloadTrigger = MutableStateFlow(0)
    private val pageSize = 20

    init {
        val user = auth.user.value
        when {
            user == null -> _state.update { it.copy(signedOutReason = "expired") }
            !user.isAdmin -> _state.update { it.copy(signedOutReason = "admin") }
            else -> {
                _state.update { it.copy(username = user.username) }
                viewModelScope.launch { loadStats() }
            }
        }
        // Search is debounced; collectLatest cancels the previous request so an older,
        // slower response can never overwrite newer results.
        viewModelScope.launch {
            combine(query.debounce(300).map { it.trim() }.distinctUntilChanged(), province, reloadTrigger) { q, p, r -> Filters(q, p, r) }
                .collectLatest { f -> loadFirstPage(f) }
        }
    }

    fun onQueryChange(value: String) { val v = value.take(60); _state.update { it.copy(query = v) }; query.value = v }
    fun onProvinceChange(value: String?) { _state.update { it.copy(province = value) }; province.value = value }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        reloadTrigger.update { it + 1 }
        viewModelScope.launch { loadStats() }
    }

    private suspend fun loadStats() {
        try {
            val stats = repo.stats()
            _state.update { it.copy(stats = stats, statsError = null) }
        } catch (e: AppException) {
            if (!handle(e)) _state.update { it.copy(statsError = e.message) }
        }
    }

    private suspend fun loadFirstPage(f: Filters) {
        _state.update { it.copy(listLoading = it.items.isEmpty() || !it.refreshing, listError = null) }
        try {
            val page = repo.applications(1, pageSize, f.query, f.province)
            _state.update { it.copy(items = page.items, total = page.total, page = 1, pages = page.pages, listLoading = false, refreshing = false) }
        } catch (e: AppException) {
            if (!handle(e)) _state.update { it.copy(listLoading = false, refreshing = false, listError = e.message) }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (!s.canLoadMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val next = repo.applications(s.page + 1, pageSize, query.value.trim(), province.value)
                _state.update { cur ->
                    val known = cur.items.mapTo(HashSet()) { it.id }  // rows can shift between pages after deletes
                    cur.copy(items = cur.items + next.items.filter { it.id !in known }, page = next.page, pages = next.pages, total = next.total, loadingMore = false)
                }
            } catch (e: AppException) {
                if (!handle(e)) _state.update { it.copy(loadingMore = false, message = e.message) }
            }
        }
    }

    fun openDetail(id: Long) {
        _state.update { it.copy(detailLoading = true, detail = null, detailError = null) }
        viewModelScope.launch {
            try {
                val d = repo.detail(id)
                _state.update { it.copy(detail = d, detailLoading = false) }
            } catch (e: AppException) {
                if (!handle(e)) _state.update { it.copy(detailLoading = false, detailError = e.message) }
            }
        }
    }
    fun closeDetail() = _state.update { it.copy(detail = null, detailLoading = false, detailError = null) }

    fun requestDelete(item: ApplicationSummary) = _state.update { it.copy(pendingDelete = item) }
    fun cancelDelete() = _state.update { it.copy(pendingDelete = null) }
    fun confirmDelete() {
        val item = _state.value.pendingDelete ?: return
        _state.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            val name = "${item.firstName} ${item.lastName}"
            try {
                repo.delete(item.id)
                _state.update { s -> s.copy(items = s.items.filterNot { it.id == item.id }, total = (s.total - 1).coerceAtLeast(0), detail = null, message = "Deleted application from $name.") }
            } catch (e: AppException) {
                if (handle(e)) return@launch
                if (e.status == 404) _state.update { s -> s.copy(items = s.items.filterNot { it.id == item.id }, message = "That application was already removed.") }
                else _state.update { it.copy(message = e.message) }
            }
            loadStats()
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    fun logout() {
        viewModelScope.launch {
            auth.signOut()
            _state.update { it.copy(signedOut = true) }
        }
    }

    /** Returns true if the error ended access to the dashboard (caller should stop). */
    private fun handle(e: AppException): Boolean = when (e.status) {
        401 -> { _state.update { it.copy(signedOutReason = "expired") }; true }
        403 -> { _state.update { it.copy(signedOutReason = "admin") }; true }
        else -> false
    }
}
