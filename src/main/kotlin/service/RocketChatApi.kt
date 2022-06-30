package service

import model.Monitoring
import org.apache.http.entity.ContentType
import org.json.JSONObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class RocketChatApi(private var uri: String) {
    fun sendMessage(monitoring: Monitoring, appendable: String) {
        val message = StringBuilder()
        message.append(
            "${monitoring.name.uppercase()} ${
                if (monitoring.status == "OK") "está Normalizado :white_check_mark:"
                else "está Crítico :setonfire:"
            }"
        )
        message.append("\nDocumentação -> ${monitoring.documentURL}")
        if (!monitoring.openTicket.isEmpty()) message.append("\nChamado -> ${monitoring.openTicket}")
        message.append("\nContagem de Disparos -> ${monitoring.errorCount}")
        if (!appendable.isEmpty()) message.append(appendable)

        val json = JSONObject()
        json.put("text", message.toString())

        val request = HttpRequest.newBuilder().uri(URI.create(uri)).timeout(Duration.ofMinutes(5))
            .header("Content-Type", ContentType.APPLICATION_JSON.toString())
            .POST(HttpRequest.BodyPublishers.ofString(json.toString())).build()
        val statusCode = HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.ofString()).statusCode()
        if (statusCode != 200) println("Erro no envio de mensagem para o RocketChat")
    }
}