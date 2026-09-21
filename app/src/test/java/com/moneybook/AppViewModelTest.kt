package com.moneybook

import com.moneybook.app.AppUiState
import com.moneybook.app.AppViewModel
import com.moneybook.core.result.AppResult
import com.moneybook.domain.model.Household
import com.moneybook.domain.model.HouseholdMember
import com.moneybook.domain.repository.AuthRepository
import com.moneybook.domain.repository.HouseholdRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun firstLaunchWithoutSessionRoutesToLogin() = runTest(dispatcher) {
        val viewModel = viewModel(session = completedSession(false))

        advanceUntilIdle()

        assertEquals(AppUiState.Unauthenticated, viewModel.state.value)
    }

    @Test
    fun restoredSessionRoutesToHomeWithExistingHousehold() = runTest(dispatcher) {
        val household = existingHousehold()
        val viewModel = viewModel(session = completedSession(true), household = household)

        advanceUntilIdle()

        assertEquals(AppUiState.Ready(household), viewModel.state.value)
    }

    @Test
    fun explicitLogoutIsNotRestoredOnNextAppStart() = runTest(dispatcher) {
        val authState = FakeAuthState(authenticated = true)
        val firstViewModel = viewModel(authState = authState, household = existingHousehold())
        advanceUntilIdle()

        firstViewModel.signOut()
        advanceUntilIdle()
        val restartedViewModel = viewModel(authState = authState, household = existingHousehold())
        advanceUntilIdle()

        assertEquals(AppUiState.Unauthenticated, restartedViewModel.state.value)
    }

    @Test
    fun expiredSessionRefreshFailureRoutesToLogin() = runTest(dispatcher) {
        val viewModel = viewModel(session = completedSession(false))

        advanceUntilIdle()

        assertEquals(AppUiState.Unauthenticated, viewModel.state.value)
    }

    @Test
    fun loginIsNotShownWhileSessionRestorationIsPending() = runTest(dispatcher) {
        val restoration = CompletableDeferred<Boolean>()
        val viewModel = viewModel(session = restoration)

        advanceUntilIdle()
        assertEquals(AppUiState.Loading, viewModel.state.value)

        restoration.complete(false)
        advanceUntilIdle()
        assertEquals(AppUiState.Unauthenticated, viewModel.state.value)
    }

    private fun viewModel(
        session: CompletableDeferred<Boolean>? = null,
        authState: FakeAuthState = FakeAuthState(),
        household: Household? = null,
    ) = AppViewModel(
        authRepository = FakeAuthRepository(authState, session),
        householdRepository = FakeHouseholdRepository(household),
    )

    private fun completedSession(authenticated: Boolean) =
        CompletableDeferred(authenticated)

    private fun existingHousehold() = Household(
        id = "household-id",
        name = "우리집",
        members = listOf(HouseholdMember("owner-id", "OWNER")),
    )
}

private data class FakeAuthState(var authenticated: Boolean = false)

private class FakeAuthRepository(
    private val authState: FakeAuthState,
    private val restoration: CompletableDeferred<Boolean>? = null,
) : AuthRepository {
    override val isConfigured = true

    override suspend fun hasSession(): Boolean =
        restoration?.await()?.also { authState.authenticated = it } ?: authState.authenticated

    override suspend fun signUp(email: String, password: String) = AppResult.Success(Unit)

    override suspend fun signIn(email: String, password: String): AppResult<Unit> {
        authState.authenticated = true
        return AppResult.Success(Unit)
    }

    override suspend fun signOut(): AppResult<Unit> {
        authState.authenticated = false
        return AppResult.Success(Unit)
    }

    override fun currentUserId(): String? = authState.authenticated.takeIf { it }?.let { "owner-id" }
}

private class FakeHouseholdRepository(
    private val household: Household?,
) : HouseholdRepository {
    override suspend fun currentHousehold() = AppResult.Success(household)

    override suspend fun createHousehold(name: String) = error("Not used")

    override suspend fun createInvitation() = error("Not used")

    override suspend fun joinHousehold(code: String) = error("Not used")
}
