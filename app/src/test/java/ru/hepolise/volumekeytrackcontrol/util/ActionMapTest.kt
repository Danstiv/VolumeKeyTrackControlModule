package ru.hepolise.volumekeytrackcontrol.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hepolise.volumekeytrackcontrol.module.fsm.ActionTrigger
import ru.hepolise.volumekeytrackcontrol.module.fsm.VolumeButton

class ActionMapTest {

    private val map = ActionMap(
        up = KeyAction.PLAY_PAUSE,
        down = KeyAction.SEEK_BACKWARD,
        both = KeyAction.NEXT
    )

    @Test
    fun `every trigger resolves to its own slot`() {
        assertEquals(KeyAction.PLAY_PAUSE, map.forTrigger(ActionTrigger.Single(VolumeButton.UP)))
        assertEquals(
            KeyAction.SEEK_BACKWARD,
            map.forTrigger(ActionTrigger.Single(VolumeButton.DOWN))
        )
        assertEquals(KeyAction.NEXT, map.forTrigger(ActionTrigger.Both))
    }

    @Test
    fun `a button is idle only when the combo is unbound too`() {
        val withCombo = ActionMap(KeyAction.NONE, KeyAction.NEXT, KeyAction.PLAY_PAUSE)
        // The combo still needs both buttons, so neither may be left to the system.
        assertFalse(withCombo.isIdle(VolumeButton.UP))
        assertFalse(withCombo.isIdle(VolumeButton.DOWN))

        val withoutCombo = ActionMap(KeyAction.NONE, KeyAction.NEXT, KeyAction.NONE)
        assertTrue(withoutCombo.isIdle(VolumeButton.UP))
        assertFalse(withoutCombo.isIdle(VolumeButton.DOWN))
    }

    @Test
    fun `seek duration is needed when any slot seeks`() {
        assertTrue(map.usesSeekDuration)
        assertTrue(
            ActionMap(KeyAction.NEXT, KeyAction.PREVIOUS, KeyAction.SEEK_FORWARD).usesSeekDuration
        )
        assertFalse(
            ActionMap(KeyAction.NEXT, KeyAction.PREVIOUS, KeyAction.PLAY_PAUSE).usesSeekDuration
        )
    }

    @Test
    fun `unknown stored keys fall back to the slot default`() {
        assertEquals(KeyAction.NEXT, KeyAction.fromKey(null, KeyAction.NEXT))
        assertEquals(KeyAction.NEXT, KeyAction.fromKey("no_such_action", KeyAction.NEXT))
        assertEquals(KeyAction.NONE, KeyAction.fromKey("none", KeyAction.NEXT))
    }
}
