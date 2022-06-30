package model

class Monitoring(
    var name: String,
    var title: String,
    var sqlQuery: String,
    var enabled: Boolean,
    var mailNotification: Boolean,
    var chatNotification: Boolean,
    var jiraTask: Boolean,
    var mailTo: String = "",
    var documentURL: String = "",
    var status: String = "",
    var openTicket: String = "",
    var errorCount: Int = 0
)