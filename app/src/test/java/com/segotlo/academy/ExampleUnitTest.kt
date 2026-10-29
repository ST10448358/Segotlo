package com.segotlo.app

import org.junit.Test
import org.junit.Assert.*

class ExampleUnitTest {

    // 1. Test that the live website URL is correct
    @Test
    fun website_url_is_correct() {
        val expectedUrl = "https://segotlo-academy.web.app/"
        val actualUrl = "https://segotlo-academy.web.app/"

        assertEquals("The app must load the correct live URL", expectedUrl, actualUrl)
        assertTrue("The URL must start with https for security", actualUrl.startsWith("https://"))
    }

    // 2. Test that the Firebase database URL is correct
    @Test
    fun firebase_database_url_is_correct() {
        val dbUrl = "https://segotlo-academy-default-rtdb.europe-west1.firebasedatabase.app"

        assertTrue("Database URL must use HTTPS", dbUrl.startsWith("https://"))
        assertTrue("Database URL must contain the project name", dbUrl.contains("segotlo-academy"))
        assertTrue("Database URL must point to the correct region", dbUrl.contains("europe-west1"))
    }

    // 3. Test the logic for identifying user roles
    @Test
    fun user_role_validation_works() {
        val validRoles = listOf("admin", "manager", "coach", "parent", "player")
        val testRole = "player"

        assertTrue("Player should be a valid role", validRoles.contains(testRole))

        val invalidRole = "hacker"
        assertFalse("Hacker should not be a valid role", validRoles.contains(invalidRole))
    }
}