package com.moneybook

import com.moneybook.app.AppRoute
import com.moneybook.app.resolveAppRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class AppRouteTest {
    @Test fun unauthenticatedRoutesToLogin() {
        assertEquals(AppRoute.LOGIN, resolveAppRoute(authenticated = false, hasHousehold = false))
    }

    @Test fun authenticatedWithoutHouseholdRoutesToSetup() {
        assertEquals(AppRoute.HOUSEHOLD_SETUP, resolveAppRoute(authenticated = true, hasHousehold = false))
    }

    @Test fun householdMemberRoutesToHome() {
        assertEquals(AppRoute.HOME, resolveAppRoute(authenticated = true, hasHousehold = true))
    }
}
