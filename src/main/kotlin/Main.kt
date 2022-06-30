import model.Monitoring
import org.json.JSONException
import org.json.JSONObject
import org.slf4j.LoggerFactory
import service.GoogleApi
import service.JiraApi
import service.Mailer
import service.RocketChatApi
import java.io.File
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.Instant.now
import java.util.*
import kotlin.system.exitProcess

class Main {
    companion object {
        private var SHEET_ID: String = ""
        private var SHEET_RANGE: String = ""
        private var DATABASE_ENDPOINT: String = ""
        private var DATABASE_USER: String = ""
        private var DATABASE_KEY: String = ""
        private var MAIL_USER: String = ""
        private var MAIL_KEY: String = ""
        private var GOOGLE_CONFIG_FILE: String = ""
        private var JIRA_USER: String = ""
        private var JIRA_API_KEY: String = ""
        private var ROCKETCHAT_WEBHOOK: String = ""
        private const val DATABASE_PARAMETERS = "useTimezone=true&serverTimezone=UTC&verifyServerCertificate=false&useSSL=false&allowMultiQueries=true"
        private const val HOME_DIR = "/dados"
        private var mailer: Mailer? = null
        private var rocketChatApi: RocketChatApi? = null
        private var jiraApi: JiraApi? = null
        private lateinit var monitoringDetailsFile: File

        @JvmStatic
        fun main(args: Array<String>) {

            val root =
                LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
            root.setLevel(ch.qos.logback.classic.Level.OFF)

            try {
                args.forEach {
                    when (it) {
                        "-sheetid" -> SHEET_ID = args[args.indexOf(it) + 1]
                        "-sheetrange" -> SHEET_RANGE = args[args.indexOf(it) + 1]
                        "-dbhost" -> DATABASE_ENDPOINT =
                            "jdbc:mysql://${args[args.indexOf(it) + 1]}:3306/?$DATABASE_PARAMETERS"
                        "-dbuser" -> DATABASE_USER = args[args.indexOf(it) + 1]
                        "-dbkey" -> DATABASE_KEY = args[args.indexOf(it) + 1]
                        "-mailuser" -> MAIL_USER = args[args.indexOf(it) + 1]
                        "-mailkey" -> MAIL_KEY = args[args.indexOf(it) + 1]
                        "-googlekey" -> GOOGLE_CONFIG_FILE = args[args.indexOf(it) + 1]
                        "-jirauser" -> JIRA_USER = args[args.indexOf(it) + 1]
                        "-jirakey" -> JIRA_API_KEY = args[args.indexOf(it) + 1]
                        "-rocketchat" -> ROCKETCHAT_WEBHOOK = args[args.indexOf(it) + 1]
                    }
                }
                if (SHEET_ID.isEmpty() || SHEET_RANGE.isEmpty() || DATABASE_ENDPOINT.isEmpty() || DATABASE_USER.isEmpty() || DATABASE_KEY.isEmpty() || GOOGLE_CONFIG_FILE.isEmpty()) {
                    throw Exception()
                } else {
                    if (!MAIL_USER.isEmpty() || !MAIL_KEY.isEmpty()) mailer = Mailer(MAIL_USER, MAIL_KEY)
                    if (!JIRA_USER.isEmpty() && !JIRA_API_KEY.isEmpty()) jiraApi = JiraApi(JIRA_USER, JIRA_API_KEY)
                    if (!ROCKETCHAT_WEBHOOK.isEmpty()) rocketChatApi = RocketChatApi(ROCKETCHAT_WEBHOOK)
                }
            } catch (e: Exception) {
                val errorMessage = StringBuilder()
                errorMessage.append("Invalid Parameters")
                errorMessage.append("Expected parameters")
                errorMessage.append("-sheetid (ID do google sheets)")
                errorMessage.append("-sheetrange (Range da planilha, ex: PLAN!A1:D5)")
                errorMessage.append("-dbhost (Endpoint do banco de dados)")
                errorMessage.append("-dbuser (Usuário do banco de dados)")
                errorMessage.append("-dbkey (Senha do banco de dados)")
                errorMessage.append("-mailuser (Endereço de mail (origem) para envio de notificações)")
                errorMessage.append("-mailkey (Senha do e-mail fornecido)")
                errorMessage.append("-googlekey (Token JSON da API do google)")
                errorMessage.append("-jirauser (Usuário do jira para abertura de chamados) OPCIONAL")
                errorMessage.append("-jirakey (Api token do usuário em base64 user+token) OPCIONAL")
                errorMessage.append("-rocketchat (Webhook do Rockechat) OPCIONAL")
                errorMessage.append(e.message)
                exitProcess(1)
            }

            try {
                // Get Monitoring List from Google Spreadsheets
                val googleApi = GoogleApi(HOME_DIR, GOOGLE_CONFIG_FILE)
                val monitoringList = googleApi.getMonitorings(SHEET_ID, SHEET_RANGE)
                // Read MonitoringDetails JSON File

                monitoringList.forEach { monitoring ->
                    monitoringDetailsFile = File("$HOME_DIR/json", "${monitoring.name}.json")
                    if (monitoringDetailsFile.exists() && monitoringDetailsFile.readText(Charsets.UTF_8).isNotEmpty()) {
                        val monitoringDetails = JSONObject(monitoringDetailsFile.readText(Charsets.UTF_8))
                        try {
                            monitoring.status = monitoringDetails.getString("status")
                            monitoring.openTicket = monitoringDetails.getString("openTicket")
                            monitoring.errorCount = monitoringDetails.getInt("errorCount")
                        } catch (e: JSONException) {
                            monitoring.status = ""
                            monitoring.openTicket = ""
                            monitoring.errorCount = 0
                        }
                    } else {
                        monitoringDetailsFile.createNewFile()
                        monitoringDetailsFile.writer().append(JSONObject().toString()).flush()
                    }
                }
                execute(monitoringList)
            } catch (e: Exception) {
                e.printStackTrace()
                exitProcess(1)
            }
        }

        private fun execute(monitoringList: MutableList<Monitoring>) {
            monitoringList.forEach { monitoring ->

                if (!monitoring.enabled) return@forEach

                println("Executando -> ${monitoring.name}\n${monitoring.sqlQuery}")
                val startTimestamp = now()
                var result = executeSql(monitoring.sqlQuery)
                println("Tempo decorrido -> ${((now().toEpochMilli() - startTimestamp.toEpochMilli()))} ms")

                val attachment = File(HOME_DIR, "$SHEET_ID-${monitoring.name}-result_log.csv")
                if (attachment.exists()) attachment.delete().and(attachment.createNewFile())
                attachment.writer().append(result).flush()
                attachment.deleteOnExit()

                var mailMessage =
                    "Alerta - ${monitoring.title}\nDocumentação - ${monitoring.documentURL}\nSQL\n${monitoring.sqlQuery}"
                var mailSubject: String

                if (result.isNotEmpty()) {
                    monitoring.status = "NOK"
                    monitoring.errorCount++
                    mailSubject = "${monitoring.name} está Critíco"
                    if (monitoring.mailNotification && mailer != null) mailer!!.send(
                        monitoring.mailTo,
                        mailSubject,
                        mailMessage,
                        attachment
                    )
                    if (monitoring.jiraTask && jiraApi != null) {
                        if (!jiraApi!!.hasOpenIssue(monitoring.openTicket)) {
                            monitoring.openTicket = jiraApi!!.createIssue(
                                title = "Monitoramento: ${monitoring.name}",
                                description = "${monitoring.title}\nDocumento -> ${monitoring.documentURL}\n${monitoring.sqlQuery}",
                                attachment).toString()
                        }
                    }
                    if (monitoring.chatNotification && rocketChatApi != null) rocketChatApi!!.sendMessage(
                        monitoring,"")
                } else if (monitoring.status == "NOK") {
                    monitoring.status = "OK"
                    monitoring.openTicket = ""
                    monitoring.errorCount = 0
                    mailSubject = "${monitoring.name} está Normalizado"
                    if (monitoring.mailNotification && mailer != null) mailer!!.send(
                        monitoring.mailTo,
                        mailSubject,
                        mailMessage
                    )
                    if (monitoring.chatNotification && rocketChatApi != null) rocketChatApi!!.sendMessage(monitoring,"")
                }
                saveMonitoringDetails(monitoring)
            }
        }

        private fun saveMonitoringDetails(monitoring: Monitoring) {
            monitoringDetailsFile = File("$HOME_DIR/json", "${monitoring.name}.json")
            var monitoringDetails: JSONObject
            if (monitoringDetailsFile.readText().isNotEmpty()) {
                monitoringDetails = JSONObject(monitoringDetailsFile.readText(Charsets.UTF_8))
                monitoringDetails.put("status", monitoring.status)
                monitoringDetails.put("openTicket", monitoring.openTicket)
                monitoringDetails.put("errorCount", monitoring.errorCount)
            } else {
                monitoringDetails = JSONObject()
                monitoringDetails.put("status", monitoring.status).put("openTicket", monitoring.openTicket).put("errorCount", monitoring.errorCount)
            }
            monitoringDetailsFile.delete()
            monitoringDetailsFile.createNewFile()
            monitoringDetailsFile.writer().append(monitoringDetails.toString()).flush()
        }

        private fun executeSql(sql: String): String {
            val connection = DriverManager.getConnection(this.DATABASE_ENDPOINT, this.DATABASE_USER, this.DATABASE_KEY)
            val transact = connection.prepareStatement(sql)
            transact.queryTimeout = 300

            var resultSet: ResultSet
            try {
                resultSet = transact.executeQuery()
            } catch (e: Exception) {
                transact.close()
                connection.close()
                throw e
            }

            var csv = buildCsv(resultSet)
            resultSet.close()
            transact.close()
            connection.close()
            return csv
        }

        private fun buildCsv(rs: ResultSet): String {

            val objList: ArrayList<GenericDto> = ArrayList()
            val csv = StringBuilder()

            while (rs.next()) {
                val obj = GenericDto()
                for (i in 1..rs.metaData.columnCount) {
                    try {
                        if (rs.getObject(i) != null && rs.getObject(i).toString().isNotEmpty()) {
                            obj.props.setProperty(rs.metaData.getColumnLabel(i), rs.getObject(i).toString())
                        } else {
                            obj.props.setProperty(rs.metaData.getColumnLabel(i), " ")
                        }
                    } catch (e: Exception) {
                        obj.props.setProperty(rs.metaData.getColumnLabel(i), " ")
                    }
                }
                objList.add(obj)
            }

            if (objList.isNotEmpty()) {
                csv.appendLine(objList.first().props.stringPropertyNames().toString())
                objList.indices.forEach { i ->
                    csv.appendLine(objList[i].props.values)
                }
                csv.chars().forEach { _ ->
                    if (csv.contains("[")) csv.deleteAt(csv.indexOf("["))
                    if (csv.contains("]")) csv.deleteAt(csv.indexOf("]"))
                }
            }

            return csv.toString()

        }

        private class GenericDto(var props: Properties = Properties())

    }

}