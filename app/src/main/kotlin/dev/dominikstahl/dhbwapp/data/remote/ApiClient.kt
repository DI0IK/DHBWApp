package dev.dominikstahl.dhbwapp.data.remote

import dev.dominikstahl.dhbwapp.remote.client.ApiConfiguration
import dev.dominikstahl.dhbwapp.remote.client.CoursesClient
import dev.dominikstahl.dhbwapp.remote.client.MensaClient
import dev.dominikstahl.dhbwapp.remote.client.NetworkError
import dev.dominikstahl.dhbwapp.remote.client.NetworkResult
import dev.dominikstahl.dhbwapp.remote.client.ParkingClient
import dev.dominikstahl.dhbwapp.remote.client.RaplaLecturesClient
import dev.dominikstahl.dhbwapp.remote.client.RaplaRoomsClient
import dev.dominikstahl.dhbwapp.remote.client.SitesClient
import dev.dominikstahl.dhbwapp.remote.models.MensaResponse
import dev.dominikstahl.dhbwapp.remote.models.ParkingLot
import dev.dominikstahl.dhbwapp.remote.models.RaplaLectureEvent
import dev.dominikstahl.dhbwapp.remote.models.RoomAvailabilityResponse
import dev.dominikstahl.dhbwapp.remote.models.SiteDto
import io.ktor.client.HttpClient

class ApiClient(httpClient: HttpClient) {
    private val apiConfig = ApiConfiguration(basePath = "https://api.dhbw.app")
    private val devApiConfig = ApiConfiguration(basePath = "https://api.dhbw.dev")

    private val sitesClient = SitesClient(httpClient)
    private val mensaClient = MensaClient(httpClient)
    private val lecturesClient = RaplaLecturesClient(httpClient)
    private val coursesClient = CoursesClient(httpClient)
    private val parkingClient = ParkingClient(httpClient)
    private val roomsClient = RaplaRoomsClient(httpClient)

    suspend fun getSites(): List<SiteDto> =
        sitesClient.list(apiConfig).unwrap()

    suspend fun getMensaMenu(site: String): List<MensaResponse> =
        mensaClient.list(site, apiConfig).unwrap()

    suspend fun getLectures(site: String, archived: Boolean = false): List<RaplaLectureEvent> =
        lecturesClient.allSiteLectures(site, archived, apiConfig).unwrap()

    suspend fun getLecturesForCourse(course: String, archived: Boolean = false): List<RaplaLectureEvent> =
        lecturesClient.lectures(course, archived, apiConfig).unwrap()

    suspend fun getCoursesForSite(site: String): List<String> =
        coursesClient.coursesOfSite(site, apiConfig).unwrap()

    suspend fun getParking(): List<ParkingLot> =
        parkingClient.getParkingLotUtilizations(apiConfig).unwrap()

    suspend fun getRoomAvailability(site: String, date: String): RoomAvailabilityResponse =
        roomsClient.roomAvailability(site, date, devApiConfig).unwrap()
}

class NetworkException(val error: NetworkError) : Exception(error.toString())

fun <T> NetworkResult<T>.unwrap(): T = when (this) {
    is NetworkResult.Success -> data
    is NetworkResult.Failure -> throw NetworkException(error)
}
