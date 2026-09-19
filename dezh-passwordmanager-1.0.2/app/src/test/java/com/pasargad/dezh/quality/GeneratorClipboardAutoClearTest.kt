package com.pasargad.dezh.quality

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.platform.ClipboardManager
import com.pasargad.dezh.generator.PasswordGenerator
import com.pasargad.dezh.presentation.generator.GeneratorScreen
import com.pasargad.dezh.presentation.generator.GeneratorViewModel
import com.pasargad.dezh.vault.StubSettingsRepository
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Deterministic clipboard auto-clear contract for the generator screen.
 *
 * Time is fully controlled via the injected [GeneratorScreen] `suspendDelay`:
 * each delay(ms) call registers a gate keyed by its duration; the test resumes
 * gates in a chosen order. No real time, no looper dependence.
 *
 * Contract under test: hiding the "copied" message must NOT cancel the pending
 * clipboard wipe — the copied password is cleared after the configured timeout
 * even though the message hides earlier.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "fa-rIR-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GeneratorClipboardAutoClearTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val mainDispatcher = UnconfinedTestDispatcher()

    private object Holder {
        var clipboard: ClipboardManager? = null
    }

    /** delay(ms) gates keyed by duration; resumed explicitly by the test. */
    private val gates = ConcurrentHashMap<Long, CompletableDeferred<Unit>>()

    private fun makeDelay(): suspend (Long) -> Unit = { ms ->
        val gate = CompletableDeferred<Unit>()
        gates[ms] = gate
        gate.await()
    }

    private fun resume(ms: Long) {
        val gate = gates.remove(ms)
        check(gate != null) { "no pending delay gate for ${'$'}{ms}ms" }
        gate.complete(Unit)
    }

    /**
     * Robolectric runs a PAUSED looper: LaunchedEffect dispatches and the
     * continuations resumed by [resume] are queued as looper messages. Pump
     * the queue so the composition actually runs them.
     */
    private fun pump() {
        composeRule.waitForIdle()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).runToEndOfTasks()
        composeRule.waitForIdle()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        Holder.clipboard = null
        gates.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun compose(content: @Composable () -> Unit) {
        composeRule.setContent {
            Holder.clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            content()
        }
    }

    private fun clickCopy() {
        composeRule.onNodeWithText("کپی").performClick()
    }

    /** Regression: current screen — hiding the message must NOT kill the wipe. */
    @Test
    fun generator_copy_autoclears_clipboard_after_timeout() {
        val viewModel = GeneratorViewModel(
            generator = PasswordGenerator(SecureRandom()),
            settingsRepository = StubSettingsRepository(),
        )
        val delay = makeDelay()
        compose {
            GeneratorScreen(
                viewModel = viewModel,
                animationsEnabled = true,
                clipboardTimeoutSeconds = 2,
                suspendDelay = delay,
            )
        }
        clickCopy()
        pump() // let the two effects start; gates {1500, 2000} are now pending
        assertTrue(
            "password should be on the clipboard right after copy",
            !Holder.clipboard?.getText()?.text.isNullOrEmpty(),
        )

        resume(1_500) // the copied-message hides…
        pump()
        resume(2_000) // …and afterwards the clipboard wipe must still fire.
        pump()

        assertTrue(
            "clipboard must be wiped after the configured timeout",
            Holder.clipboard?.getText()?.text?.isEmpty() == true,
        )
    }

}

private typealias Composable = androidx.compose.runtime.Composable
