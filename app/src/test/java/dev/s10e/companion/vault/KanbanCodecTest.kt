package dev.s10e.companion.vault

import org.junit.Assert.assertEquals
import org.junit.Test

class KanbanCodecTest {

    @Test
    fun codec_roundTrip() {
        val originalMd = """
            ---
            kanban-plugin: basic
            ---
            ## Todo
            - [ ] Task 1
            - [ ] Task 2

            ## In Progress
            - [ ] Task 3

        """.trimIndent()

        val parsed = KanbanCodec.parse(originalMd)
        val serialized = KanbanCodec.serialize(parsed)
        assertEquals(originalMd, serialized)
    }

    @Test
    fun codec_doneColumnsUseCheckedBoxes() {
        val md = """
            ## erledigt
            - [ ] This should be checked

            ## Done
            - [ ] And this

            ## completed
            - [ ] Also this

        """.trimIndent()

        val parsed = KanbanCodec.parse(md)
        val serialized = KanbanCodec.serialize(parsed)

        val expected = """
            ## erledigt
            - [x] This should be checked

            ## Done
            - [x] And this

            ## completed
            - [x] Also this

        """.trimIndent()

        assertEquals(expected, serialized)
    }

    @Test
    fun codec_keepsUnknownLinesOutsideTasks() {
        val md = """
            ## Todo
            Just some random text
            - [ ] A task
            More text

        """.trimIndent()

        val parsed = KanbanCodec.parse(md)
        val serialized = KanbanCodec.serialize(parsed)
        assertEquals(md, serialized)
    }
}
