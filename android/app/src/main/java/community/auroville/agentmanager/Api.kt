package community.auroville.agentmanager

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** The phone door of the Mac app: http://<Mac Tailscale address>:8847 with a bearer token. Both are typed in Settings and live only on this phone. */
class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("agentmanager", Context.MODE_PRIVATE)
    var address: String
        get() = sp.getString("address", "") ?: ""
        set(v) { sp.edit().putString("address", v.trim()).apply() }
    var token: String
        get() = sp.getString("token", "") ?: ""
        set(v) { sp.edit().putString("token", v.trim()).apply() }
}

sealed class Result {
    data class Ok(val json: JSONObject) : Result()
    /** kind: "setup" (nothing entered), "tailscale" (unreachable), "token" (401), "other" */
    data class Fail(val kind: String, val text: String) : Result()
}

object Api {
    private fun base(addr: String): String {
        val a = addr.trim().removePrefix("http://").trimEnd('/')
        return "http://" + if (a.contains(":")) a else "$a:8847"
    }

    /** the typed address first, then the Mac's Tailscale name (MagicDNS), so the Mac is found even when its 100.x address changed */
    private fun call(store: Store, path: String, body: String?, readMs: Int = 20000): Result {
        if (store.token.isBlank()) return Result.Fail("setup", "Enter the token in SETTINGS (the Mac is found by its Tailscale name).")
        var last: Result = Result.Fail("tailscale", "Cannot reach the Mac.")
        for (addr in listOf(store.address, "macbook-pro").filter { it.isNotBlank() }.distinct()) {
            val r = one(store, addr, path, body, readMs)
            if (!(r is Result.Fail && r.kind == "tailscale")) return r
            last = r
        }
        return last
    }

    private fun one(store: Store, addr: String, path: String, body: String?, readMs: Int): Result {
        return try {
            val c = URL(base(addr) + path).openConnection() as HttpURLConnection
            c.connectTimeout = 3000; c.readTimeout = readMs
            c.setRequestProperty("Authorization", "Bearer " + store.token)
            if (body != null) {
                c.requestMethod = "POST"; c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            when {
                code == 401 -> Result.Fail("token", "The Mac refused the token.")
                code in 200..299 -> Result.Ok(if (text.trimStart().startsWith("[")) JSONObject().put("items", JSONArray(text)) else JSONObject(text))
                else -> Result.Fail("other", "HTTP $code " + text.take(120))
            }
        } catch (e: java.io.IOException) {
            Result.Fail("tailscale", "Cannot reach the Mac.")
        } catch (e: Exception) {
            Result.Fail("other", e.javaClass.simpleName)
        }
    }

    fun overview(store: Store): Result = call(store, "/overview", null)

    /** long poll: the Mac holds the request up to 20 s and answers the moment a plan, command or session changes */
    fun live(store: Store, since: Int): Result = call(store, "/live?since=$since", null, 30000)

    fun blockers(store: Store): Result = call(store, "/blockers", null)
    fun blocker(store: Store, id: String, action: String): Result = call(store, "/blocker/$action", JSONObject().put("id", id).toString())

    fun command(store: Store, slug: String, agent: String, task: Int, action: String, text: String): Result {
        val j = JSONObject().put("slug", slug).put("agent", agent).put("task", task).put("action", action).put("text", text)
        return call(store, "/command", j.toString())
    }
}

fun JSONArray?.list(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONObject?.obj(k: String): JSONObject = this?.optJSONObject(k) ?: JSONObject()
fun JSONObject?.arr(k: String): List<JSONObject> = this?.optJSONArray(k).list()
fun JSONObject?.str(k: String): String = if (this == null || this.isNull(k)) "" else this.optString(k, "")
