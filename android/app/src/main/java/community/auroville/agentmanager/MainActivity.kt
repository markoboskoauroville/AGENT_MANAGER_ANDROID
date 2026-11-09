package community.auroville.agentmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

object Ink {
    val ground = Color(0xFF121214); val panel = Color(0xFF1C1C20); val line = Color(0x1AFFFFFF)
    val text = Color(0xFFEBEBED); val dim = Color(0xFF9E9EA8); val grey = Color(0xFF85858F)
    val green = Color(0xFF4DD973); val amber = Color(0xFFE8A33D); val red = Color(0xFFF25954); val orange = Color(0xFFFF8A3D)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(this)
        // one-time setup by adb: am start -n community.auroville.agentmanager/.MainActivity --es address 100.x.y.z:8847 --es token ...
        intent?.getStringExtra("address")?.let { store.address = it }
        intent?.getStringExtra("token")?.let { store.token = it }
        setContent { App(store) }
    }
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        val s = Store(this)
        intent.getStringExtra("address")?.let { s.address = it }
        intent.getStringExtra("token")?.let { s.token = it }
    }
}

@Composable
fun Mono(t: String, c: Color = Ink.text, size: Int = 12, bold: Boolean = false, modifier: Modifier = Modifier) {
    Text(t, color = c, fontSize = size.sp, fontFamily = FontFamily.Monospace, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, modifier = modifier, lineHeight = (size + 4).sp)
}

@Composable
fun WordButton(word: String, color: Color = Ink.text, tag: String = "", onClick: () -> Unit) {
    Box(
        Modifier.testTag(tag).clip(RoundedCornerShape(8.dp)).border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) { Mono(word, color, 12, true) }
}

@Composable
fun Panel(title: String, state: String = "", content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ink.panel).border(1.dp, Ink.line, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Mono(title, Ink.text, 13, true)
        if (state.isNotEmpty()) Mono(state, Ink.dim, 11)
        content()
    }
}

fun statusColor(s: String) = when (s) {
    "done" -> Ink.green; "running", "busy" -> Ink.green; "paused", "waiting", "pending" -> Ink.amber
    "failed", "blocked" -> Ink.red; "idle" -> Ink.text; else -> Ink.grey
}

@Composable
fun App(store: Store) {
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var err by remember { mutableStateOf<Result.Fail?>(null) }
    var solo by remember { mutableStateOf("") }
    var settings by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var stamp by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current

    suspend fun refresh() {
        when (val r = withContext(Dispatchers.IO) { Api.overview(store) }) {
            is Result.Ok -> { data = r.json; err = null; stamp = r.json.str("served_at") }
            is Result.Fail -> err = r
        }
    }
    // auto-refresh every 5 s, only while the app is visible
    LaunchedEffect(owner, settings) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { refresh(); delay(5000) }
        }
    }
    fun send(slug: String, agent: String, task: Int, action: String, text: String = "") {
        scope.launch {
            val r = withContext(Dispatchers.IO) { Api.command(store, slug, agent, task, action, text) }
            note = when (r) {
                is Result.Ok -> "sent: $action" + (if (task > 0) " task $task" else "") + " (" + r.json.str("id") + ")"
                is Result.Fail -> "NOT sent: " + r.text
            }
        }
    }

    val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    Column(Modifier.fillMaxSize().background(Ink.ground).windowInsetsPadding(bars.union(WindowInsets.ime)).padding(horizontal = 16.dp)) {
        // header
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Mono("Agent Manager (CEO) (Mantra)", Ink.text, 16, true)
                Mono(if (stamp.isEmpty()) "not connected yet" else "as of $stamp · every 5 s", Ink.grey, 11)
            }
            WordButton(if (settings) "BACK" else "SETTINGS", Ink.dim, "settings") { settings = !settings }
        }
        if (settings) {
            SettingsPane(store) { settings = false }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val e = err
                if (e != null) ErrorBanner(e, data != null)
                if (note.isNotEmpty()) Mono(note, if (note.startsWith("NOT")) Ink.red else Ink.green, 11)
                val d = data
                if (d != null) {
                    Tiles(d, solo) { solo = if (solo == it) "" else it }
                    if (solo.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Mono("SOLO: " + solo.uppercase(), Ink.amber, 11); WordButton("SHOW ALL", Ink.amber, "solo-clear") { solo = "" }
                    }
                    if (solo != "sessions" && solo != "vm") for (p in d.arr("plans")) {
                        if (solo in listOf("done", "running", "paused", "failed", "todo") && p.arr("tasks").none { it.str("status") == solo }) continue
                        PlanCard(p, solo) { a, b, c, dd, e -> send(a, b, c, dd, e) }
                    }
                    if (solo.isEmpty() || solo == "sessions") SessionsPanel(d)
                    if (solo.isEmpty() || solo == "vm") VmPanel(d.obj("vm"))
                    Spacer(Modifier.height(8.dp))
                } else if (e == null) Mono("loading…", Ink.grey, 12)
            }
            data?.let { StatusBar(it) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun ErrorBanner(e: Result.Fail, haveOld: Boolean) {
    val (title, body) = when (e.kind) {
        "tailscale" -> "CONNECT TAILSCALE" to "The Mac cannot be reached. Open Tailscale on this phone and switch it on, and check the Mac is awake with Agent Manager running."
        "token" -> "TOKEN REFUSED" to "The token in SETTINGS is not the one the Mac holds."
        "setup" -> "SET UP" to e.text
        else -> "ERROR" to e.text
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ink.red.copy(alpha = 0.12f)).border(1.dp, Ink.red.copy(alpha = 0.7f), RoundedCornerShape(10.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Mono(title, Ink.red, 14, true, Modifier.testTag("error-title"))
        Mono(body, Ink.text, 12)
        if (haveOld) Mono("What is shown below is the last data received.", Ink.grey, 11)
    }
}

@Composable
fun SettingsPane(store: Store, done: () -> Unit) {
    var a by remember { mutableStateOf(store.address) }
    var t by remember { mutableStateOf(store.token) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Mono("Mac address on Tailscale (100.x.y.z or its name), port 8847 is added if missing", Ink.dim, 11)
        Field(a, "address") { a = it }
        Mono("Token (the file ~/Downloads/API/agent-manager-token.txt on the Mac)", Ink.dim, 11)
        Field(t, "token", true) { t = it }
        WordButton("SAVE", Ink.green, "save") { store.address = a; store.token = t; done() }
        Mono("Stored only on this phone. Tailscale must be on: the Mac answers nobody else.", Ink.grey, 11)
    }
}

@Composable
fun Field(v: String, tag: String, secret: Boolean = false, onChange: (String) -> Unit) {
    BasicTextField(
        value = v, onValueChange = onChange, singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = Ink.text, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
        cursorBrush = SolidColor(Ink.amber),
        visualTransformation = if (secret && v.isNotEmpty()) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.testTag(tag).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Ink.panel).border(1.dp, Ink.line, RoundedCornerShape(8.dp)).padding(12.dp)
    )
}

@Composable
fun Tiles(d: JSONObject, solo: String, pick: (String) -> Unit) {
    val all = d.arr("plans").flatMap { it.arr("tasks") }
    fun n(s: String) = all.count { it.str("status") == s }
    val sess = d.obj("sessions_all").arr("rows").size.let { if (it > 0) it else d.obj("mac").arr("sessions").size }
    val vmUp = d.obj("vm").optBoolean("ok", false)
    val tiles = listOf(
        Triple("done", n("done").toString(), "DONE") to Ink.green, Triple("running", n("running").toString(), "RUNNING") to Ink.amber,
        Triple("paused", n("paused").toString(), "PAUSED") to Ink.dim, Triple("failed", n("failed").toString(), "FAILED") to Ink.red,
        Triple("todo", n("todo").toString(), "TO DO") to Ink.grey, Triple("sessions", sess.toString(), "SESS.") to Ink.text,
        Triple("vm", if (vmUp) "UP" else "DOWN", "VM") to (if (vmUp) Ink.green else Ink.red)
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((x, col) in tiles) {
            val on = solo == x.first
            Column(
                Modifier.weight(1f).testTag("solo-" + x.first).clip(RoundedCornerShape(8.dp)).background(if (on) col.copy(alpha = 0.18f) else Color.Transparent)
                    .border(1.5.dp, if (on) col else Color.Transparent, RoundedCornerShape(8.dp)).clickable { pick(x.first) }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(x.second, color = col, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(x.third, color = if (on) Ink.text else Ink.grey, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
fun PlanCard(p: JSONObject, solo: String, send: (String, String, Int, String, String) -> Unit) {
    val tasks = p.arr("tasks")
    val slug = p.str("slug")
    val done = tasks.count { it.str("status") == "done" }
    val run = tasks.count { it.str("status") == "running" }
    val focus = tasks.firstOrNull { it.optBoolean("focus", false) }
    val shown = if (solo in listOf("done", "running", "paused", "failed", "todo")) tasks.filter { it.str("status") == solo } else tasks
    Panel("Session Manager · " + p.str("session").ifEmpty { p.str("project") }, "$done of ${tasks.size} done · updated ${ago(p.optInt("age", 0))} ago") {
        if (p.str("project").isNotEmpty()) Mono(p.str("project"), Ink.text, 12)
        if (p.str("goal").isNotEmpty()) Mono(p.str("goal"), Ink.dim, 11)
        if (focus != null) Mono("DOING NOW  ${focus.optInt("n")}. ${focus.str("title")}", Ink.amber, 12)
        ProgressBar(done, run, tasks.size)
        for (t in shown) TaskRow(slug, t, send)
    }
}

fun ago(s: Int): String = when { s < 90 -> "${s}s"; s < 5400 -> "${s / 60}m"; s < 172800 -> "${s / 3600}h"; else -> "${s / 86400}d" }

@Composable
fun ProgressBar(done: Int, run: Int, total: Int) {
    if (total == 0) return
    Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.08f))) {
        if (done > 0) Box(Modifier.weight(done.toFloat()).fillMaxHeight().background(Ink.green))
        if (run > 0) Box(Modifier.weight(run.toFloat()).fillMaxHeight().background(Ink.amber))
        val rest = total - done - run
        if (rest > 0) Box(Modifier.weight(rest.toFloat()).fillMaxHeight())
    }
}

@Composable
fun TaskRow(slug: String, t: JSONObject, send: (String, String, Int, String, String) -> Unit) {
    val n = t.optInt("n"); val st = t.str("status").ifEmpty { "todo" }; val agent = t.str("agent")
    var open by remember(slug, n) { mutableStateOf(false) }
    var msg by remember(slug, n) { mutableStateOf(false) }
    var draft by remember(slug, n) { mutableStateOf("") }
    var armed by remember(slug, n) { mutableStateOf(false) }
    val checked = t.optBoolean("checked", false)
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.testTag("task$n"), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Mono(if (checked) "✓" else "○", if (checked) Ink.green else Ink.grey, 13, true)
            Mono("$n.", Ink.dim, 12)
            Column(Modifier.weight(1f)) {
                Mono(t.str("title"), Ink.text, 12)
                if (t.optBoolean("focus", false)) Mono("IN FOCUS", Ink.amber, 10, true)
            }
            Mono(st.uppercase(), statusColor(st), 10, true)
        }
        if (agent.isNotEmpty()) Mono("worker $agent", Ink.dim, 10, modifier = Modifier.padding(start = 40.dp))
        if (st == "running" || st == "paused") Row(Modifier.padding(start = 40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WordButton("PAUSE", Ink.text, "pause$n") { send(slug, agent, n, "pause", "") }
            if (!armed) WordButton("STOP", Ink.red, "stop$n") { armed = true }
            else WordButton("SURE?", Ink.red, "stop-sure$n") { armed = false; send(slug, agent, n, "stop", "") }
            WordButton("MESSAGE", Ink.amber, "msg$n") { msg = !msg }
        }
        if (st == "paused") Column(
            Modifier.padding(start = 40.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Ink.amber.copy(alpha = 0.10f)).border(1.dp, Ink.amber.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Mono("WAITING FOR YOU", Ink.amber, 10, true)
            Mono(t.str("next").ifEmpty { t.str("report") }.ifEmpty { "this agent is paused and has not said what it needs" }, Ink.text, 12)
            Field(draft, "reply$n") { draft = it }
            WordButton("REPLY", Ink.green, "reply-send$n") { if (draft.isNotBlank()) { send(slug, agent, n, "message", draft.trim()); draft = "" } }
        }
        if (msg && st != "paused") Column(Modifier.padding(start = 40.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Field(draft, "message$n") { draft = it }
            WordButton("SEND", Ink.green, "msg-send$n") { if (draft.isNotBlank()) { send(slug, agent, n, "message", draft.trim()); draft = ""; msg = false } }
        }
        if (open) Column(Modifier.padding(start = 40.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Mono(t.str("report").ifEmpty { "no report yet" }, if (t.str("report").isEmpty()) Ink.grey else Ink.text, 11)
            if (t.str("next").isNotEmpty()) Mono("next: " + t.str("next"), Ink.dim, 11)
            if (st != "running" && st != "paused" && agent.isNotEmpty()) WordButton("MESSAGE", Ink.amber, "msg-open$n") { msg = !msg }
        }
    }
}

fun modelOf(mac: JSONObject, name: String): String {
    val s = mac.arr("sessions").firstOrNull { it.str("name") == name } ?: return ""
    val m = s.obj("modes")
    return m.str("current_model").ifEmpty { m.str("model") }
}

@Composable
fun SessionsPanel(d: JSONObject) {
    val rows = d.obj("sessions_all").arr("rows")
    val mac = d.obj("mac")
    val byDevice = rows.groupBy { it.str("device").ifEmpty { "unknown" } }
    Panel("Sessions", "${rows.size} sessions on ${byDevice.size} devices") {
        if (rows.isEmpty()) Mono("the Session Manager has not listed sessions yet", Ink.grey, 11)
        for ((dev, list) in byDevice) {
            Mono(dev.uppercase(), Ink.dim, 10, true, Modifier.padding(top = 6.dp))
            for (r in list) {
                val st = r.str("state"); val nm = r.str("name")
                val model = if (st == "busy" || st == "running") modelOf(mac, nm) else ""
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(color = Ink.text)) { append(nm) }
                    if (model.isNotEmpty()) withStyle(SpanStyle(color = Ink.orange)) { append(" ($model)") }
                    withStyle(SpanStyle(color = Ink.grey)) { append("  " + r.str("kind")) }
                    withStyle(SpanStyle(color = statusColor(st), fontWeight = FontWeight.Bold)) { append("  " + st.uppercase()) }
                }, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp)
            }
        }
    }
}

@Composable
fun VmPanel(vm: JSONObject) {
    val up = vm.optBoolean("ok", false)
    Panel("chromium-vm", if (up) "reachable" else "UNREACHABLE") {
        for (s in vm.arr("services")) {
            val a = s.str("active")
            Mono(s.str("name") + "  " + a, if (a == "active") Ink.green else Ink.red, 11)
        }
        for (w in vm.arr("workers")) Mono(w.str("name") + ": " + w.str("last"), Ink.dim, 10)
    }
}

fun usage(rl: JSONObject, k: String): String {
    val o = rl.obj(k)
    if (!o.has("used_percentage")) return "?"
    val left = o.optLong("resets_at", 0) - System.currentTimeMillis() / 1000
    return "${o.optInt("used_percentage")}%" + if (left > 0) " (resets ${ago(left.toInt())})" else ""
}

fun pctColor(p: Int) = if (p >= 85) Ink.red else if (p >= 60) Ink.amber else Ink.dim

/** the modes/usage strip pinned at the bottom, like the iTerm status bar of the Mac app */
@Composable
fun StatusBar(d: JSONObject) {
    val mac = d.obj("mac"); val set = mac.obj("settings"); val rl = set.obj("usage").obj("rate_limits")
    Column(
        Modifier.fillMaxWidth().heightIn(max = 130.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF0C0C0E)).border(1.dp, Ink.line, RoundedCornerShape(10.dp))
            .verticalScroll(rememberScrollState()).padding(8.dp).testTag("statusbar"),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(buildAnnotatedString {
            withStyle(SpanStyle(color = Ink.amber, fontWeight = FontWeight.Bold)) { append("ALL ") }
            withStyle(SpanStyle(color = Ink.text)) { append(set.str("model") + " · " + set.str("effort_default") + " · RC at start " + (if (set.optBoolean("rc_at_startup")) "on" else "off")) }
            withStyle(SpanStyle(color = Ink.dim)) { append(" · 5h ") }
            withStyle(SpanStyle(color = pctColor(rl.obj("five_hour").optInt("used_percentage")))) { append(usage(rl, "five_hour")) }
            withStyle(SpanStyle(color = Ink.dim)) { append(" · week ") }
            withStyle(SpanStyle(color = pctColor(rl.obj("seven_day").optInt("used_percentage")))) { append(usage(rl, "seven_day")) }
        }, fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 14.sp)
        for (s in mac.arr("sessions")) {
            val m = s.obj("modes"); val busy = s.str("status") == "busy"
            val model = (m.str("current_model").ifEmpty { m.str("model") })
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color = if (busy) Ink.green else Ink.text, fontWeight = FontWeight.Bold)) { append(s.str("name").substringBefore(" (")) }
                withStyle(SpanStyle(color = if (busy) Ink.orange else Ink.dim)) { append(" $model") }
                withStyle(SpanStyle(color = Ink.dim)) { append(" · ${m.str("effort")} · ${m.str("permission")} · RC ${if (m.optBoolean("rc")) "on" else "off"} · router ${m.str("router")}") }
                if (m.has("ctx") && !m.isNull("ctx")) withStyle(SpanStyle(color = pctColor(m.optInt("ctx")))) { append(" · ctx ${m.optInt("ctx")}%") }
            }, fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 14.sp)
        }
    }
}
