package com.friends.quiz

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/*
 * طبقة الشبكة والمنطق (لا تعتمد على أي شيء من واجهة أندرويد).
 *
 * البروتوكول: رسائل JSON، كل رسالة في سطر، عبر TCP على المنفذ 8080.
 *   لاعب -> خادم : {"t":"join","name":"..."}   {"t":"answer","i":رقم_السؤال,"c":رقم_الخيار}
 *   خادم -> لاعب : joined / players / lobby / question / result / final / error
 * المضيف نفسه يتصل بخادمه عبر 127.0.0.1 كأنه لاعب عادي، فتكون الواجهة واحدة للجميع.
 * اكتشاف الغرف تلقائياً: المضيف يبث رسالة UDP كل ثانية على المنفذ 8081.
 */

const val GAME_PORT = 8080
const val DISCOVERY_PORT = 8081

class Question(val text: String, val options: List<String>, val answer: Int)

fun parseQuestions(json: String): List<Question> {
    val out = ArrayList<Question>()
    val arr = JSONArray(json)
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        val opts = o.getJSONArray("o")
        val list = ArrayList<String>()
        for (k in 0 until opts.length()) list.add(opts.getString(k))
        val ans = o.getInt("a")
        if (list.size >= 2 && ans in 0 until list.size) {
            out.add(Question(o.getString("q"), list, ans))
        }
    }
    return out
}

fun questionsToJson(list: List<Question>): String {
    val arr = JSONArray()
    for (q in list) {
        val opts = JSONArray()
        for (o in q.options) opts.put(o)
        arr.put(JSONObject().put("q", q.text).put("o", opts).put("a", q.answer))
    }
    return arr.toString()
}

/** عناوين IPv4 الخاصة بهذا الهاتف (عنوان الهوت سبوت يكون غالباً 192.168.43.1 أو شبيهه). */
fun localIpv4(): List<String> {
    val out = ArrayList<String>()
    try {
        val en = NetworkInterface.getNetworkInterfaces() ?: return out
        for (ni in Collections.list(en)) {
            if (!ni.isUp || ni.isLoopback) continue
            for (addr in Collections.list(ni.inetAddresses)) {
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    val h = addr.hostAddress
                    if (h != null) out.add(h)
                }
            }
        }
    } catch (e: Exception) {
    }
    return out
}

fun broadcastTargets(): List<InetAddress> {
    val out = ArrayList<InetAddress>()
    try {
        val en = NetworkInterface.getNetworkInterfaces()
        if (en != null) {
            for (ni in Collections.list(en)) {
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.interfaceAddresses) {
                    val b = ia.broadcast
                    if (b != null) out.add(b)
                }
            }
        }
    } catch (e: Exception) {
    }
    try {
        out.add(InetAddress.getByName("255.255.255.255"))
    } catch (e: Exception) {
    }
    return out
}

private fun nowMs(): Long = System.nanoTime() / 1_000_000L

class Player(val id: Int, val name: String, private val socket: Socket) {
    private val out = PrintWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8), true)

    @Volatile var score = 0
    @Volatile var answered = -1
    @Volatile var gain = 0
    @Volatile var correct = false

    fun send(j: JSONObject) {
        try {
            synchronized(out) { out.println(j.toString()) }
        } catch (e: Exception) {
        }
    }

    fun close() {
        try {
            socket.close()
        } catch (e: Exception) {
        }
    }
}

class GameServer(private val pool: List<Question>, hostLabel: String) {
    private val label = hostLabel.replace("|", " ")
    private val players = CopyOnWriteArrayList<Player>()
    private var serverSocket: ServerSocket? = null
    private var beacon: DatagramSocket? = null
    private var gameThread: Thread? = null
    private val lock = Any()
    private var nextId = 1

    @Volatile private var running = false
    @Volatile var inGame = false
        private set
    @Volatile private var curIndex = -1
    @Volatile private var curCorrect = -1
    @Volatile private var curStart = 0L
    @Volatile private var curLimitMs = 1L

    fun start(port: Int, targets: () -> List<InetAddress>) {
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(port))
        serverSocket = ss
        running = true
        thread(isDaemon = true) { acceptLoop(ss) }
        thread(isDaemon = true) { beaconLoop(port, targets) }
    }

    fun playerCount(): Int = players.size

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
        }
        try {
            beacon?.close()
        } catch (e: Exception) {
        }
        for (p in players) p.close()
        players.clear()
        gameThread?.interrupt()
    }

    // ---------------------------------------------------------------- الاتصالات

    private fun acceptLoop(ss: ServerSocket) {
        while (running) {
            try {
                val s = ss.accept()
                s.tcpNoDelay = true
                s.keepAlive = true
                thread(isDaemon = true) { handleClient(s) }
            } catch (e: Exception) {
                if (!running) break
                try {
                    Thread.sleep(50)
                } catch (ie: InterruptedException) {
                    break
                }
            }
        }
    }

    private fun beaconLoop(port: Int, targets: () -> List<InetAddress>) {
        val sock = try {
            DatagramSocket()
        } catch (e: Exception) {
            return
        }
        beacon = sock
        try {
            sock.broadcast = true
            val data = ("ASQ1|" + label + "|" + port).toByteArray(Charsets.UTF_8)
            while (running) {
                for (addr in targets()) {
                    try {
                        sock.send(DatagramPacket(data, data.size, addr, DISCOVERY_PORT))
                    } catch (e: Exception) {
                    }
                }
                Thread.sleep(1000)
            }
        } catch (e: Exception) {
        } finally {
            sock.close()
        }
    }

    private fun rawSend(s: Socket, j: JSONObject) {
        try {
            val pw = PrintWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8), true)
            pw.println(j.toString())
        } catch (e: Exception) {
        }
    }

    private fun uniqueName(base: String): String {
        var n = base
        var k = 2
        while (players.any { it.name == n }) {
            n = "$base $k"
            k++
        }
        return n
    }

    private fun tryAdd(s: Socket, wanted: String): Player? {
        return synchronized(lock) {
            if (inGame) {
                null
            } else {
                val p = Player(nextId++, uniqueName(wanted), s)
                players.add(p)
                p
            }
        }
    }

    private fun handleClient(s: Socket) {
        var me: Player? = null
        try {
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            s.soTimeout = 10000
            val first = reader.readLine() ?: return
            val j = JSONObject(first)
            if (j.optString("t") != "join") return
            var name = j.optString("name").trim()
            if (name.length > 20) name = name.substring(0, 20)
            if (name.isEmpty()) name = "لاعب"
            val p = tryAdd(s, name)
            if (p == null) {
                rawSend(s, JSONObject().put("t", "error").put("msg", "المسابقة بدأت بالفعل، انتظر الجولة القادمة"))
                return
            }
            me = p
            s.soTimeout = 0
            p.send(JSONObject().put("t", "joined").put("id", p.id).put("name", p.name))
            broadcastPlayers()
            while (true) {
                val line = reader.readLine() ?: break
                val m = try {
                    JSONObject(line)
                } catch (e: Exception) {
                    continue
                }
                if (m.optString("t") == "answer") {
                    onAnswer(p, m.optInt("i", -1), m.optInt("c", -1))
                }
            }
        } catch (e: Exception) {
        } finally {
            val p = me
            if (p != null) {
                players.remove(p)
                broadcastPlayers()
            }
            try {
                s.close()
            } catch (e: Exception) {
            }
        }
    }

    private fun playersJson(): JSONArray {
        val a = JSONArray()
        for (p in players) a.put(p.name)
        return a
    }

    private fun broadcast(m: JSONObject) {
        for (p in players) p.send(m)
    }

    private fun broadcastPlayers() {
        if (!inGame) broadcast(JSONObject().put("t", "players").put("list", playersJson()))
    }

    // ---------------------------------------------------------------- اللعبة

    private fun onAnswer(p: Player, qi: Int, choice: Int) {
        synchronized(lock) {
            if (qi != curIndex || p.answered != -1) return
            val elapsed = nowMs() - curStart
            if (elapsed > curLimitMs + 700) return
            p.answered = choice
            if (choice == curCorrect) {
                val left = (curLimitMs - elapsed).coerceIn(0L, curLimitMs)
                // 500 نقطة للإجابة الصحيحة + حتى 500 نقطة إضافية حسب السرعة
                p.gain = (500 + 500 * left / curLimitMs).toInt()
                p.score += p.gain
                p.correct = true
            }
        }
    }

    fun startGame(count: Int, secs: Int): Boolean {
        synchronized(lock) {
            if (inGame || players.isEmpty()) return false
            inGame = true
        }
        gameThread = thread(isDaemon = true) { runGame(count, secs) }
        return true
    }

    private fun shuffleOptions(q: Question): Question {
        val order = q.options.indices.shuffled()
        val opts = order.map { q.options[it] }
        return Question(q.text, opts, order.indexOf(q.answer))
    }

    private fun buildBoard(): JSONArray {
        val a = JSONArray()
        for (p in players.sortedByDescending { it.score }) {
            a.put(
                JSONObject()
                    .put("name", p.name)
                    .put("score", p.score)
                    .put("gain", p.gain)
                    .put("ok", p.correct)
                    .put("ans", p.answered)
            )
        }
        return a
    }

    private fun runGame(count: Int, secs: Int) {
        try {
            val picked = pool.shuffled().take(count).map { shuffleOptions(it) }
            for (p in players) {
                p.score = 0
            }
            for ((idx, q) in picked.withIndex()) {
                if (!running) return
                synchronized(lock) {
                    for (p in players) {
                        p.answered = -1
                        p.gain = 0
                        p.correct = false
                    }
                    curCorrect = q.answer
                    curLimitMs = secs * 1000L
                    curStart = nowMs()
                    curIndex = idx
                }
                val opts = JSONArray()
                for (o in q.options) opts.put(o)
                broadcast(
                    JSONObject().put("t", "question").put("i", idx).put("n", picked.size)
                        .put("text", q.text).put("opts", opts).put("secs", secs)
                )
                val deadline = curStart + curLimitMs
                while (running && nowMs() < deadline && players.any { it.answered == -1 }) {
                    Thread.sleep(100)
                }
                synchronized(lock) { curIndex = -1 }
                broadcast(
                    JSONObject().put("t", "result").put("i", idx).put("n", picked.size)
                        .put("correct", q.answer).put("board", buildBoard())
                )
                Thread.sleep(if (idx == picked.size - 1) 4000L else 6000L)
            }
            broadcast(JSONObject().put("t", "final").put("board", buildBoard()))
        } catch (e: InterruptedException) {
        }
    }

    /** يعيد الجميع إلى شاشة الانتظار لبدء مسابقة جديدة بنفس اللاعبين. */
    fun backToLobby() {
        thread(isDaemon = true) {
            synchronized(lock) {
                inGame = false
                curIndex = -1
                for (p in players) {
                    p.score = 0
                    p.gain = 0
                    p.answered = -1
                    p.correct = false
                }
            }
            broadcast(JSONObject().put("t", "lobby").put("list", playersJson()))
        }
    }
}

// -------------------------------------------------------------------- العميل

class GameClient(
    private val host: String,
    private val port: Int,
    private val name: String,
    private val onMessage: (JSONObject) -> Unit,
    private val onClosed: (String?) -> Unit
) {
    @Volatile private var socket: Socket? = null
    @Volatile private var out: PrintWriter? = null
    @Volatile private var closedByUs = false
    private val writer = Executors.newSingleThreadExecutor()

    fun connect() {
        thread(isDaemon = true) {
            var err: String? = null
            try {
                val s = Socket()
                s.connect(InetSocketAddress(host, port), 6000)
                s.tcpNoDelay = true
                s.keepAlive = true
                socket = s
                val pw = PrintWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8), true)
                out = pw
                pw.println(JSONObject().put("t", "join").put("name", name).toString())
                val r = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                while (true) {
                    val line = r.readLine() ?: break
                    val j = try {
                        JSONObject(line)
                    } catch (e: Exception) {
                        continue
                    }
                    onMessage(j)
                }
            } catch (e: Exception) {
                err = e.message ?: "خطأ في الاتصال"
            }
            if (!closedByUs) onClosed(err)
        }
    }

    fun send(j: JSONObject) {
        try {
            writer.execute {
                try {
                    out?.println(j.toString())
                } catch (e: Exception) {
                }
            }
        } catch (e: Exception) {
        }
    }

    fun close() {
        closedByUs = true
        try {
            socket?.close()
        } catch (e: Exception) {
        }
        try {
            writer.shutdown()
        } catch (e: Exception) {
        }
    }
}

/** يستمع لبث المضيفين ويستدعي onFound(عنوان_IP, اسم_المضيف, المنفذ) عند العثور على غرفة. */
fun discoverHosts(timeoutMs: Long, onFound: (String, String, Int) -> Unit, onDone: () -> Unit): Thread {
    return thread(isDaemon = true) {
        var sock: DatagramSocket? = null
        try {
            val s = DatagramSocket(null)
            sock = s
            s.reuseAddress = true
            s.bind(InetSocketAddress(DISCOVERY_PORT))
            s.soTimeout = 500
            val end = System.currentTimeMillis() + timeoutMs
            val buf = ByteArray(256)
            val seen = HashSet<String>()
            while (System.currentTimeMillis() < end) {
                try {
                    val p = DatagramPacket(buf, buf.size)
                    s.receive(p)
                    val text = String(p.data, 0, p.length, Charsets.UTF_8)
                    val parts = text.split("|")
                    if (parts.size >= 3 && parts[0] == "ASQ1") {
                        val ip = p.address.hostAddress
                        if (ip != null && seen.add(ip)) {
                            onFound(ip, parts[1], parts[2].toIntOrNull() ?: GAME_PORT)
                        }
                    }
                } catch (e: SocketTimeoutException) {
                }
            }
        } catch (e: Exception) {
        } finally {
            try {
                sock?.close()
            } catch (e: Exception) {
            }
        }
        onDone()
    }
}
