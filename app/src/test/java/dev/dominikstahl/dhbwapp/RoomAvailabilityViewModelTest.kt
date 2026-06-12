package dev.dominikstahl.dhbwapp

import dev.dominikstahl.dhbwapp.remote.models.RoomInfo
import dev.dominikstahl.dhbwapp.remote.models.RoomOccupancy
import dev.dominikstahl.dhbwapp.ui.rooms.RoomAvailabilityViewModel
import dev.dominikstahl.dhbwapp.data.remote.ApiClient
import io.ktor.client.HttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomAvailabilityViewModelTest {

    @Test
    fun testRoomDeduplication() {
        val dummyClient = ApiClient(HttpClient())
        val viewModel = RoomAvailabilityViewModel(dummyClient)

        val occ1 = RoomOccupancy(
            courseName = "INF20A",
            lectureName = "Math",
            isPublic = true,
            startTime = "08:00",
            endTime = "09:30"
        )
        val occ2 = RoomOccupancy(
            courseName = "INF20A",
            lectureName = "Physics",
            isPublic = true,
            startTime = "10:00",
            endTime = "11:30"
        )
        val occ3 = RoomOccupancy(
            courseName = "INF20A",
            lectureName = "Chemistry",
            isPublic = true,
            startTime = "12:00",
            endTime = "13:30"
        )
        val occ4 = RoomOccupancy(
            courseName = "INF20A",
            lectureName = "Biology",
            isPublic = true,
            startTime = "14:00",
            endTime = "15:30"
        )

        val inputRooms = listOf(
            RoomInfo(name = "Raum 101", status = "free", occupancies = null),
            RoomInfo(name = "Raum 101 (suffix)", status = "occupied", occupancies = listOf(occ1)),
            RoomInfo(name = "Raum 102 - Labor", status = "occupied", occupancies = listOf(occ2)),
            RoomInfo(name = "Raum 102", status = "free", occupancies = null),
            RoomInfo(name = "Raum 103/Seminar", status = "occupied", occupancies = listOf(occ3)),
            RoomInfo(name = "Raum 103", status = "free", occupancies = null),
            RoomInfo(name = "Raum 104_Extra", status = "occupied", occupancies = listOf(occ4)),
            RoomInfo(name = "Raum 104", status = "free", occupancies = null),
            RoomInfo(name = "Raum 105 Suffix", status = "occupied", occupancies = null),
            RoomInfo(name = "Raum 105", status = "free", occupancies = null),
            RoomInfo(name = "Raum 1067", status = "free", occupancies = null),
            RoomInfo(name = "Raum 106", status = "free", occupancies = null) // 1067 should not deduplicate with 106
        )

        val result = viewModel.deduplicateRooms(inputRooms)

        assertEquals(7, result.size)

        val room101 = result.first { it.name == "Raum 101" }
        assertEquals("occupied", room101.status)
        assertEquals("Math", room101.occupancies?.first()?.lectureName)

        val room102 = result.first { it.name == "Raum 102" }
        assertEquals("occupied", room102.status)
        assertEquals("Physics", room102.occupancies?.first()?.lectureName)

        val room103 = result.first { it.name == "Raum 103" }
        assertEquals("occupied", room103.status)
        assertEquals("Chemistry", room103.occupancies?.first()?.lectureName)

        val room104 = result.first { it.name == "Raum 104" }
        assertEquals("occupied", room104.status)
        assertEquals("Biology", room104.occupancies?.first()?.lectureName)

        val room105 = result.first { it.name == "Raum 105" }
        assertEquals("occupied", room105.status)

        // Make sure Raum 1067 is kept separate from Raum 106
        val room106 = result.first { it.name == "Raum 106" }
        val room1067 = result.first { it.name == "Raum 1067" }
        assertEquals("free", room106.status)
        assertEquals("free", room1067.status)
    }
}
