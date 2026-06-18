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
 * restores the first `doneCount` sets as ticked (and the finished flag once every set is
 * done), along with the per-exercise kg/RIR/reps entries, so a backgrounded / killed app
 * resumes mid-session.
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

        // Steps: a#0, a#1, b#0, b#1 -> doneCount 2 restores a#0/a#1 as ticked, session running.
        assertEquals(false, restored.finished)
        assertEquals(listOf("a", "a"), restored.done.map { it.exerciseId })
        assertEquals(listOf(0, 1), restored.done.map { it.setIndex })
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

        assertEquals(true, restored.finished)
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
