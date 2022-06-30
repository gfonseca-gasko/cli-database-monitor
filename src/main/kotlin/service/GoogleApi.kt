@file:Suppress("DEPRECATION")

package service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import model.Monitoring
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.security.interfaces.RSAPrivateKey
import java.time.Duration
import java.time.Instant.now
import java.util.*
import kotlin.system.exitProcess

class GoogleApi(private var home: String, private var configFile: String) {

    private var apiURI: String = "https://sheets.googleapis.com/v4/spreadsheets"

    private fun getAuthToken(): String {
        val authFile = File("$home/json", configFile).readText()
        val jsonFile = JSONObject(authFile)
        val credentials: GoogleCredential = GoogleCredential.fromStream(authFile.byteInputStream())
        val algorithm: Algorithm = Algorithm.RSA256(null, credentials.serviceAccountPrivateKey!! as RSAPrivateKey?)
        return JWT.create()
            .withKeyId(jsonFile.getString("private_key_id"))
            .withIssuer(jsonFile.getString("client_email"))
            .withSubject(jsonFile.getString("client_email"))
            .withAudience("https://sheets.googleapis.com/")
            .withIssuedAt(Date.from(now()))
            .withExpiresAt(Date.from(now().plusMillis(300 * 1000L)))
            .sign(algorithm)
    }

    fun getMonitorings(spreadsheetID: String, range: String): MutableList<Monitoring> {

        val monitoringList: MutableList<Monitoring> = ArrayList<Monitoring>()

        try {
            val request = HttpRequest.newBuilder().uri(URI.create("$apiURI/$spreadsheetID/values/$range"))
                .header("Authorization", "Bearer ${getAuthToken()}")
                .timeout(Duration.ofMinutes(5)).GET().build()
            val response = HttpClient.newBuilder().build().send(request, BodyHandlers.ofString())

            if (!(response.statusCode() == 200)) {
                throw Exception("Erro ao obter a planilha - StatusCode ${response.statusCode()}")
            }
            var jsonObject = JSONObject(response.body())
            var monitoringObjects = jsonObject.getJSONArray("values")

            for (i in 0 until monitoringObjects.length()) {
                val monitoringFields = monitoringObjects.getJSONArray(i)
                monitoringList.add(
                    Monitoring(
                        name = monitoringFields.getString(0),
                        title = monitoringFields.getString(1),
                        sqlQuery = monitoringFields.getString(2),
                        enabled = monitoringFields.getBoolean(3),
                        mailNotification = monitoringFields.getBoolean(4),
                        chatNotification = monitoringFields.getBoolean(5),
                        jiraTask = monitoringFields.getBoolean(6),
                        mailTo = monitoringFields.getString(7),
                        documentURL = monitoringFields.getString(8)
                    )
                )
            }

        } catch (e: Exception) {
            e.printStackTrace()
            exitProcess(1)
        }
        return monitoringList
    }
}