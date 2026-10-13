package com.webwizards.applywise.di

import android.content.Context
import com.webwizards.applywise.BuildConfig
import com.webwizards.applywise.data.local.LocalStore
import com.webwizards.applywise.data.remote.Network
import com.webwizards.applywise.data.remote.ServerUrlHolder
import com.webwizards.applywise.data.remote.SessionCookieJar
import com.webwizards.applywise.data.remote.StoredCookie
import com.webwizards.applywise.data.remote.UserDto
import com.webwizards.applywise.data.repository.AdminRepository
import com.webwizards.applywise.data.repository.AuthRepository
import com.webwizards.applywise.data.repository.StudentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Values read from disk once at start-up. */
data class StartupState(val serverUrl: String?, val cookies: List<StoredCookie>, val user: UserDto?)

/** Manual dependency injection: one place builds the object graph (no annotation processing). */
class AppContainer(context: Context, startup: StartupState) {
    /** Lives as long as the app process; used for work that must outlive a screen (saving cookies, sign-out). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val localStore = LocalStore(context)
    private val urlHolder = ServerUrlHolder(startup.serverUrl ?: BuildConfig.DEFAULT_SERVER_URL)
    private val cookieJar = SessionCookieJar(startup.cookies) { cookies -> appScope.launch { localStore.saveCookies(cookies) } }

    // The API reports 401s to the auth repository, which is created after it.
    private var sessionLost: () -> Unit = {}
    private val api = Network.createApi(urlHolder, cookieJar) { sessionLost() }

    val authRepository = AuthRepository(
        api, urlHolder, cookieJar, localStore, appScope,
        cachedUser = startup.user.takeIf { cookieJar.hasSessionFor(urlHolder.baseUrl) }
    )
    val studentRepository = StudentRepository(api, urlHolder, localStore) { authRepository.onSessionLost() }
    val adminRepository = AdminRepository(api, urlHolder)

    init {
        sessionLost = authRepository::onSessionLost
        authRepository.verifySavedSession()
    }
}
