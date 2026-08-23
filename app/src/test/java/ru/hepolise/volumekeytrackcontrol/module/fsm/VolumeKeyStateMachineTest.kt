package ru.hepolise.volumekeytrackcontrol.module.fsm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ACTION_DELAY = 300L
private const val BYPASS_DELAY = 2000L

/**
 * Drives the state machine with a virtual clock. `advanceTo` mimics the host:
 * it fires the timeout callback whenever a deadline has come due.
 */
private class Fixture(
    actionDelay: Long = ACTION_DELAY,
    bypassDelay: Long = BYPASS_DELAY,
    boundUp: Boolean = true,
    boundDown: Boolean = true,
    boundBoth: Boolean = true
) {
    val fsm = VolumeKeyStateMachine {
        VolumeKeyStateMachine.Config(actionDelay, bypassDelay, boundUp, boundDown, boundBoth)
    }
    var now = 1_000L
        private set

    val effects = mutableListOf<Effect>()

    fun down(button: VolumeButton): Boolean = key(button, isDown = true)

    fun up(button: VolumeButton): Boolean = key(button, isDown = false)

    private fun key(button: VolumeButton, isDown: Boolean): Boolean {
        val outcome = fsm.onKey(button, isDown, now)
        effects += outcome.effects
        return outcome.consume
    }

    fun advanceTo(time: Long) {
        while (true) {
            val next = fsm.nextTimeoutAt() ?: break
            if (next > time) break
            now = maxOf(now, next)
            effects += fsm.onTimeout(now).effects
        }
        now = maxOf(now, time)
    }

    fun advance(delta: Long) = advanceTo(now + delta)

    fun takeEffects(): List<Effect> = effects.toList().also { effects.clear() }
}

class VolumeKeyStateMachineTest {

    @Test
    fun `short press is swallowed and turned into a volume change`() {
        val f = Fixture()
        assertTrue(f.down(VolumeButton.UP))
        f.advance(100)
        assertTrue(f.up(VolumeButton.UP))

        assertEquals(listOf(Effect.AdjustVolume(VolumeButton.UP)), f.takeEffects())
        assertFalse(f.fsm.isActive)
    }

    @Test
    fun `press held past the action delay runs the action and no volume change`() {
        val f = Fixture()
        f.down(VolumeButton.DOWN)
        f.advance(ACTION_DELAY)

        assertEquals(
            listOf(Effect.RunAction(ActionTrigger.Single(VolumeButton.DOWN))),
            f.takeEffects()
        )

        f.up(VolumeButton.DOWN)
        assertEquals(emptyList<Effect>(), f.takeEffects())
    }

    @Test
    fun `release exactly on the action delay still runs the action`() {
        val f = Fixture()
        f.down(VolumeButton.UP)
        f.advanceTo(f.now + ACTION_DELAY)
        f.up(VolumeButton.UP)

        assertEquals(
            listOf(Effect.RunAction(ActionTrigger.Single(VolumeButton.UP))),
            f.takeEffects()
        )
    }

    @Test
    fun `both buttons within the window collapse into one combo action`() {
        val f = Fixture()
        f.down(VolumeButton.UP)
        f.advance(40)
        f.down(VolumeButton.DOWN)
        f.advance(ACTION_DELAY)

        assertEquals(listOf(Effect.RunAction(ActionTrigger.Both)), f.takeEffects())
    }

    @Test
    fun `second press after the first action fired stays a single action`() {
        val f = Fixture()
        f.down(VolumeButton.UP)
        f.advance(ACTION_DELAY)
        assertEquals(
            listOf(Effect.RunAction(ActionTrigger.Single(VolumeButton.UP))),
            f.takeEffects()
        )

        f.down(VolumeButton.DOWN)
        f.advance(ACTION_DELAY)
        assertEquals(
            listOf(Effect.RunAction(ActionTrigger.Single(VolumeButton.DOWN))),
            f.takeEffects()
        )
    }

    @Test
    fun `releasing one button of a pending combo cancels the gesture actions`() {
        val f = Fixture()
        f.down(VolumeButton.UP)
        f.down(VolumeButton.DOWN)
        f.advance(100)
        f.up(VolumeButton.DOWN)

        assertEquals(listOf(Effect.AdjustVolume(VolumeButton.DOWN)), f.takeEffects())

        // The still-held button must not fire an action of its own afterwards.
        f.advance(ACTION_DELAY * 2)
        assertEquals(emptyList<Effect>(), f.takeEffects())
    }

    @Test
    fun `holding past the bypass delay hands the button to the system`() {
        val f = Fixture()
        f.down(VolumeButton.UP)
        f.advance(BYPASS_DELAY)

        assertEquals(
            listOf(
                Effect.RunAction(ActionTrigger.Single(VolumeButton.UP)),
                Effect.InjectDown(VolumeButton.UP)
            ),
            f.takeEffects()
        )

        // The real release is swallowed and answered with a synthetic one.
        assertTrue(f.up(VolumeButton.UP))
        assertEquals(listOf(Effect.InjectUp(VolumeButton.UP)), f.takeEffects())
        assertFalse(f.fsm.isActive)
    }

    @Test
    fun `each button is handed over on its own deadline`() {
        val f = Fixture()
        f.down(VolumeButton.UP)
        f.advance(40)
        f.down(VolumeButton.DOWN)

        // Combo action first, then the two bypasses 40 ms apart.
        f.advanceTo(f.now + BYPASS_DELAY - 40)
        assertEquals(
            listOf(
                Effect.RunAction(ActionTrigger.Both),
                Effect.InjectDown(VolumeButton.UP)
            ),
            f.takeEffects()
        )

        f.advance(40)
        assertEquals(listOf(Effect.InjectDown(VolumeButton.DOWN)), f.takeEffects())
    }

    @Test
    fun `presses arriving after a bypass are passed straight through`() {
        val f = Fixture()
        f.down(VolumeButton.UP)
        f.advance(BYPASS_DELAY)
        f.takeEffects()

        assertFalse(f.down(VolumeButton.DOWN))
        assertFalse(f.up(VolumeButton.DOWN))
        assertEquals(emptyList<Effect>(), f.takeEffects())

        // Gesture only ends once every physical button is up again.
        assertTrue(f.fsm.isActive)
        f.up(VolumeButton.UP)
        assertFalse(f.fsm.isActive)
    }

    @Test
    fun `pending action is dropped when the button is handed over`() {
        // Bypass shorter than the action delay: the action never gets to run.
        val f = Fixture(actionDelay = 1000, bypassDelay = 500)
        f.down(VolumeButton.UP)
        f.advance(1000)

        assertEquals(listOf(Effect.InjectDown(VolumeButton.UP)), f.takeEffects())
    }

    @Test
    fun `bypass can be disabled`() {
        val f = Fixture(bypassDelay = 0)
        f.down(VolumeButton.UP)
        f.advance(10_000)

        assertEquals(
            listOf(Effect.RunAction(ActionTrigger.Single(VolumeButton.UP))),
            f.takeEffects()
        )
        assertNull(f.fsm.nextTimeoutAt())
    }

    @Test
    fun `auto repeat of a held key changes nothing`() {
        val f = Fixture()
        assertTrue(f.down(VolumeButton.UP))
        f.advance(50)
        assertTrue(f.down(VolumeButton.UP))
        f.advance(50)
        assertTrue(f.up(VolumeButton.UP))

        assertEquals(listOf(Effect.AdjustVolume(VolumeButton.UP)), f.takeEffects())
    }

    @Test
    fun `bypassNow hands every held button over at once`() {
        val f = Fixture()
        f.down(VolumeButton.UP)
        f.advance(40)
        f.down(VolumeButton.DOWN)
        f.advance(50)

        f.effects += f.fsm.bypassNow(f.now).effects
        assertEquals(
            listOf(
                Effect.InjectDown(VolumeButton.UP),
                Effect.InjectDown(VolumeButton.DOWN)
            ),
            f.takeEffects()
        )

        // The pending combo action is dropped: the gesture belongs to the chord now.
        f.advance(BYPASS_DELAY)
        assertEquals(emptyList<Effect>(), f.takeEffects())

        assertTrue(f.up(VolumeButton.UP))
        assertTrue(f.up(VolumeButton.DOWN))
        assertEquals(
            listOf(Effect.InjectUp(VolumeButton.UP), Effect.InjectUp(VolumeButton.DOWN)),
            f.takeEffects()
        )
    }

    @Test
    fun `bypassNow does nothing without a gesture`() {
        val f = Fixture()
        val outcome = f.fsm.bypassNow(f.now)

        assertEquals(emptyList<Effect>(), outcome.effects)
        assertFalse(f.fsm.isActive)
    }

    @Test
    fun `bypassNow after a button was already handed over affects only the rest`() {
        val f = Fixture()
        f.down(VolumeButton.UP)
        f.advance(BYPASS_DELAY)
        f.takeEffects()

        f.down(VolumeButton.DOWN)
        // The gesture was given away, so the second press was never owned.
        f.effects += f.fsm.bypassNow(f.now).effects
        assertEquals(emptyList<Effect>(), f.takeEffects())
    }

    @Test
    fun `a second press with no combo action hands both buttons over at once`() {
        val f = Fixture(boundBoth = false)
        assertTrue(f.down(VolumeButton.UP))
        f.advance(40)
        assertTrue(f.down(VolumeButton.DOWN))

        assertEquals(
            listOf(
                Effect.InjectDown(VolumeButton.UP),
                Effect.InjectDown(VolumeButton.DOWN)
            ),
            f.takeEffects()
        )

        // No action is left pending for either button.
        f.advance(BYPASS_DELAY * 2)
        assertEquals(emptyList<Effect>(), f.takeEffects())
    }

    @Test
    fun `an unbound button is handed over at the action deadline`() {
        val f = Fixture(boundUp = false)
        f.down(VolumeButton.UP)

        f.advanceTo(f.now + ACTION_DELAY - 1)
        assertEquals(emptyList<Effect>(), f.takeEffects())

        f.advance(1)
        assertEquals(listOf(Effect.InjectDown(VolumeButton.UP)), f.takeEffects())
    }

    @Test
    fun `an unbound button still waits for a combo partner`() {
        val f = Fixture(boundUp = false)
        f.down(VolumeButton.UP)
        f.advance(40)
        f.down(VolumeButton.DOWN)

        // The combo is bound, so the gesture is still the module's.
        assertEquals(emptyList<Effect>(), f.takeEffects())
        f.advance(ACTION_DELAY)
        assertEquals(listOf(Effect.RunAction(ActionTrigger.Both)), f.takeEffects())
    }

    @Test
    fun `an unbound button is kept when the bypass is disabled`() {
        val f = Fixture(bypassDelay = 0, boundUp = false)
        f.down(VolumeButton.UP)
        f.advance(BYPASS_DELAY * 2)

        assertEquals(emptyList<Effect>(), f.takeEffects())
        assertTrue(f.up(VolumeButton.UP))
        assertEquals(listOf(Effect.AdjustVolume(VolumeButton.UP)), f.takeEffects())
    }

    @Test
    fun `release without a tracked press is passed through`() {
        val f = Fixture()
        assertFalse(f.up(VolumeButton.DOWN))
        assertFalse(f.fsm.isActive)
    }

    @Test
    fun `buttons released in reverse order end the gesture cleanly`() {
        val f = Fixture()
        f.down(VolumeButton.UP)
        f.down(VolumeButton.DOWN)
        f.advance(ACTION_DELAY)
        f.takeEffects()

        f.up(VolumeButton.UP)
        assertTrue(f.fsm.isActive)
        f.up(VolumeButton.DOWN)
        assertFalse(f.fsm.isActive)
        assertNull(f.fsm.nextTimeoutAt())
        // Combo already fired, so neither release turns into a volume change.
        assertEquals(emptyList<Effect>(), f.takeEffects())
    }
}
