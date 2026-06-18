package de.rack.app.ui.session

import de.rack.app.data.SessionDraft
import de.rack.app.data.SessionDraftEntries
import de.rack.app.domain.PlanExercise
import de.rack.app.domain.WeightUnit
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Covers the pure resume mapping (issue #60, docs/specs/spec-session-player.md):
 * [restoreSession] rebuilds the deterministic step sequence from the day's exercises and
 * splits it at the cached draft's `doneCount` cursor, restoring the focused step, the
 * ticked sets, and the per-exercise kg/RIR/reps entries so a backgrounded / killed app
 * resumes the same step.
 */
class SessionDraftMappingTest {
    @Test
    fun `restore refocuses the cursor step and keeps the ticked sets and entries`() {
        val exercises =
            listOf(
                exercise("a", target = "2 x 5"),
                exercise("b", target = "2 x 5"),
            )
        val prefilled = prefillEntries(exercises, emptyMap(), WeightUnit.KG)
        val draft =
            SessionDraft(
                doneCount = 2,
                entries = mapOf("a" to SessionDraftEntries(weight = "60", rir = "1", reps = mapOf(0 to "5", 1 to "4"))),
            )

        val restored =
            restoreSession(exercises, prefilled, references = emptyMap(), draft = draft, unit = WeightUnit.KG)

        // Steps: a#0, a#1, b#0, b#1 -> cursor 2 focuses b#0 with a#0/a#1 done.
        assertEquals("b", restored.focused?.exerciseId)
        assertEquals(0, restored.focused?.setIndex)
        assertEquals(listOf("a", "a"), restored.done.map { it.exerciseId })
        // The draft's edited entries override the fresh pre-fill for a.
        assertEquals("60", restored.entriesFor("a").weight)
        assertEquals(mapOf(0 to "5", 1 to "4"), restored.entriesFor("a").reps)
    }

    @Test
    fun `a draft at the last cursor restores a finished session`() {
        val exercises = listOf(exercise("a", target = "2 x 5"))
        val prefilled = prefillEntries(exercises, emptyMap(), WeightUnit.KG)
        val draft = SessionDraft(doneCount = 2, entries = emptyMap())

        val restored =
            restoreSession(exercises, prefilled, references = emptyMap(), draft = draft, unit = WeightUnit.KG)

        assertEquals(null, restored.focused)
        assertEquals(2, restored.done.size)
    }

    private fun exercise(
        id: String,
        target: String?,
    ) = PlanExercise(
        id = id,
        dayId = "d",
        exerciseId = id,
        name = id,
        category = null,
        position = 0,
        target = target,
        rir = null,
        cue = null,
        supersetLabel = null,
    )
}
