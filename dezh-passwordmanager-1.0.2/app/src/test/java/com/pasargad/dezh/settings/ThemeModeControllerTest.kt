package com.pasargad.dezh.settings

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Theme mode controller over DataStore — cycle + cross-instance persistence. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeModeControllerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newScope() = CoroutineScope(Job() + UnconfinedTestDispatcher())

    private fun newRepository() = DataStoreSettingsRepository.forFile(
        file = File(tmp.root, "theme-${System.nanoTime()}.preferences_pb"),
        scope = newScope(),
    )

    @Test
    fun `cycle walks SYSTEM - LIGHT - DARK - SYSTEM`() = runBlocking {
        val controller = ThemeModeController(newRepository(), newScope())
        withTimeout(5_000) { controller.mode.first { it == ThemeMode.SYSTEM } }
        controller.cycle()
        assertEquals(ThemeMode.LIGHT, controller.mode.value)
        controller.cycle()
        assertEquals(ThemeMode.DARK, controller.mode.value)
        controller.cycle()
        assertEquals(ThemeMode.SYSTEM, controller.mode.value)
    }

    @Test
    fun `set persists to the settings repository`() = runBlocking {
        val repo = newRepository()
        val controller = ThemeModeController(repo, newScope())
        controller.set(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repo.current().themeMode)
        assertEquals(ThemeMode.DARK, controller.mode.value)
    }

    @Test
    fun `a new controller converges on the persisted value`() = runBlocking {
        val repo = newRepository()
        ThemeModeController(repo, newScope()).set(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, repo.current().themeMode)

        val second = ThemeModeController(repo, newScope())
        withTimeout(5_000) { second.mode.first { it == ThemeMode.LIGHT } }
        assertEquals(ThemeMode.LIGHT, second.mode.value)
    }

    @Test
    fun `repository reset propagates back to the controller`() = runBlocking {
        val repo = newRepository()
        val controller = ThemeModeController(repo, newScope())
        controller.set(ThemeMode.DARK)
        repo.resetToDefaults()
        withTimeout(5_000) { controller.mode.first { it == ThemeMode.SYSTEM } }
        assertEquals(ThemeMode.SYSTEM, controller.mode.value)
    }
}
