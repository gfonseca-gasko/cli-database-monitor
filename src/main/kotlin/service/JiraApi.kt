package service

import com.mashape.unirest.http.Unirest
import org.apache.http.entity.ContentType
import org.json.JSONObject
import java.io.File
import java.net.http.HttpClient

class JiraApi(
    private var user: String,
    private var token: String,
    private var projectKey: String = "ST"
) {

    private val uri = "https://helpdeskmobly.atlassian.net/rest/api/2"
    private var httpClient: HttpClient = HttpClient.newBuilder().build()

    fun createIssue(title: String, description: String, attachment: File?): String? {
        var fields = JSONObject()
        val obj1 = JSONObject()
        obj1.put("key", "ST")
        fields.put("project",obj1)
        fields.put("summary", title)
        fields.put("description", description)
        val obj2 = JSONObject()
        obj2.put("name", "Task")
        fields.put("issuetype", obj2)
        var payload = JSONObject()
        payload.put("fields",fields)

        var createIssueResponse = Unirest.post("$uri/issue/").basicAuth(user, token)
            .header("Content-Type", ContentType.APPLICATION_JSON.toString())
            .header("Accept", "application/json")
            .body(payload)
            .asJson()

        val createdIssueKey = createIssueResponse.body.`object`.getString("key")

        if (attachment != null && createIssueResponse.status == 201) {
                Unirest.post("$uri/issue/$createdIssueKey/attachments").basicAuth(user, token)
                    .header("Accept", "application/json")
                    .header("X-Atlassian-Token", "no-check")
                    .field("file", attachment)
                    .asJson()
        }
        return createdIssueKey
    }

    fun hasOpenIssue(issueKey: String): Boolean {
        if(issueKey.isEmpty()) return false
            val response =
            Unirest.get("$uri/search?jql=key=$issueKey&fields=status").basicAuth(user, token)
                .header("Content-Type", ContentType.APPLICATION_JSON.toString())
                .header("Accept", "application/json").asJson()
        return try {
            val status = response.body.`object`
                .getJSONArray("issues")
                .getJSONObject(0)
                .getJSONObject("fields")
                .getJSONObject("status")
                .getString("name")
            (status != "Resolved" && status != "Reject" && status != "Rejected" && !status.isNullOrEmpty())
        } catch (e: Exception) {
            false
        }
    }

}