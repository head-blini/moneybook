package com.moneybook

import com.moneybook.data.repository.hasUsableSession
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import org.junit.Assert.assertFalse
import org.junit.Test

class SupabaseAuthStatusTest {
    @Test
    fun refreshFailureDoesNotRestoreExpiredSession() {
        val status = SessionStatus.RefreshFailure(
            RefreshFailureCause.NetworkError(IllegalStateException("refresh failed")),
        )

        assertFalse(status.hasUsableSession())
    }
}
