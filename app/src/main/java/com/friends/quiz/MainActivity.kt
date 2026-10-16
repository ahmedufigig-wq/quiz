package com.friends.quiz

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

/**
 * كل الواجهات تُبنى بالكود (بدون ملفات XML ولا مكتبات خارجية) ليبقى التطبيق خفيفاً
 * ومتوافقاً مع أندرويد 5.0 فما فوق.
 */
class MainActivity : Activity() {

    private val cBg = Color.parseColor("#14172B")
    private val cCard = Color.parseColor("#1F2447")
    private val cCard2 = Color.parseColor("#2C3363")
    private val cAccent = Color.parseColor("#6C63FF")
    private val cGood = Color.parseColor("#27AE60")
    private val cBad = Color.parseColor("#E74C3C")
    private val cMuted = Color.parseColor("#A9B0D6")

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var prefs: SharedPreferences
    private val ui = Handler(Looper.getMainLooper())

    private var server: GameServer? = null
    private var client: GameClient? = null
    private var connSeq = 0
    private var isHost = false
    private var joined = false
    private var myName = ""
    private var screen = "home"

    private var players: List<String> = emptyList()
    private var playersBox: LinearLayout? = null
    private var playersCount: TextView? = null

    private var qCount = 10
    private var qSecs = 15

    private var curOpts: List<String> = emptyList()
    private var curIdx = -1
    private var mySel = -1
    private val optButtons = ArrayList<Button>()
    private var statusView: TextView? = null
    private var timerRunnable: Runnable? = null

    private var joinStatus: TextView? = null
    private var foundBox: LinearLayout? = null
    private var ipField: EditText? = null
    private var mcLock: WifiManager.MulticastLock? = null

    // ------------------------------------------------------------------ دورة الحياة

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = getSharedPreferences("quiz", Context.MODE_PRIVATE)
        showHome()
    }

    override fun onDestroy() {
        leaveAll()
        releaseMulticast()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (screen == "home") {
            super.onBackPressed()
        } else {
            leaveAll()
            showHome()
        }
    }

    // ------------------------------------------------------------------ أدوات واجهة

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }

    private fun rounded(color: Int, radiusDp: Int = 14): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(radiusDp).toFloat()
        return g
    }

    private fun tv(
        text: String,
        size: Float = 18f,
        color: Int = Color.WHITE,
        bold: Boolean = false,
        gravity: Int = Gravity.CENTER
    ): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        t.gravity = gravity
        if (bold) t.setTypeface(null, Typeface.BOLD)
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.topMargin = dp(8)
        t.layoutParams = lp
        return t
    }

    private fun gap(h: Int): View {
        val v = View(this)
        v.layoutParams = LinearLayout.LayoutParams(MATCH, dp(h))
        return v
    }

    private fun btn(label: String, color: Int, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.setTextColor(Color.WHITE)
        b.textSize = 18f
        b.background = rounded(color)
        b.setPadding(dp(16), dp(14), dp(16), dp(14))
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.topMargin = dp(10)
        b.layoutParams = lp
        b.setOnClickListener { onClick() }
        return b
    }

    private fun edit(hint: String): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setHintTextColor(Color.GRAY)
        e.setTextColor(Color.BLACK)
        e.textSize = 18f
        e.background = rounded(Color.WHITE, 12)
        e.setPadding(dp(14), dp(12), dp(14), dp(12))
        e.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        e.inputType = InputType.TYPE_CLASS_TEXT
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.topMargin = dp(10)
        e.layoutParams = lp
        return e
    }

    private fun show(build: LinearLayout.() -> Unit) {
        val sv = ScrollView(this)
        sv.isFillViewport = true
        sv.setBackgroundColor(cBg)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(dp(20), dp(28), dp(20), dp(28))
        col.build()
        sv.addView(col, ViewGroup.LayoutParams(MATCH, WRAP))
        sv.layoutDirection = View.LAYOUT_DIRECTION_RTL
        setContentView(sv)
    }

    private fun boardRow(rank: Int, name: String, score: Int, gain: Int, me: Boolean): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = rounded(if (me) cAccent else cCard, 12)
        row.setPadding(dp(12), dp(10), dp(12), dp(10))
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.topMargin = dp(6)
        row.layoutParams = lp

        val medal = when (rank) {
            1 -> "🥇"
            2 -> "🥈"
            3 -> "🥉"
            else -> rank.toString()
        }
        val r = TextView(this)
        r.text = medal
        r.textSize = 20f
        r.setTextColor(Color.WHITE)
        r.gravity = Gravity.CENTER
        r.minWidth = dp(40)
        row.addView(r)

        val n = TextView(this)
        n.text = name
        n.textSize = 18f
        n.setTextColor(Color.WHITE)
        if (me) n.setTypeface(null, Typeface.BOLD)
        n.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        row.addView(n)

        if (gain > 0) {
            val g = TextView(this)
            g.text = "+$gain"
            g.textSize = 14f
            g.setTextColor(Color.parseColor("#7CFFB2"))
            g.setPadding(dp(8), 0, dp(8), 0)
            row.addView(g)
        }

        val s = TextView(this)
        s.text = score.toString()
        s.textSize = 20f
        s.setTypeface(null, Typeface.BOLD)
        s.setTextColor(Color.WHITE)
        row.addView(s)
        return row
    }

    // ------------------------------------------------------------------ الشاشة الرئيسية

    private fun readName(f: EditText): String? {
        val n = f.text.toString().trim()
        if (n.isEmpty()) {
            toast("اكتب اسمك أولاً")
            return null
        }
        prefs.edit().putString("name", n).apply()
        return if (n.length > 20) n.substring(0, 20) else n
    }

    private fun showHome() {
        screen = "home"
        val nameField = edit("اسمك في المسابقة")
        nameField.setText(prefs.getString("name", "") ?: "")
        show {
            addView(tv("🎯", 64f))
            addView(tv("مسابقة الأصدقاء", 30f, bold = true))
            addView(tv("منافسة جماعية بدون إنترنت\nعبر هوت سبوت الهاتف", 15f, cMuted))
            addView(gap(18))
            addView(nameField)
            addView(btn("🏠  إنشاء غرفة (أنا المضيف)", cAccent) {
                val n = readName(nameField)
                if (n != null) startHosting(n)
            })
            addView(btn("🔌  الانضمام إلى غرفة", cGood) {
                val n = readName(nameField)
                if (n != null) showJoin(n)
            })
            addView(btn("➕  إضافة أسئلة خاصة بي", cCard2) { showAddQuestions() })
            addView(btn("❓  طريقة الاستخدام", cCard2) { showHowTo() })
        }
    }

    private fun showHowTo() {
        screen = "howto"
        show {
            addView(tv("طريقة اللعب", 26f, bold = true))
            addView(
                tv(
                    "١) أحد الأصدقاء يشغّل «نقطة اتصال / Hotspot» في هاتفه (لا يحتاج إنترنت).\n\n" +
                        "٢) باقي الأصدقاء يتصلون بشبكة الهوت سبوت من إعدادات الواي فاي.\n\n" +
                        "٣) صاحب الهوت سبوت يفتح التطبيق ثم «إنشاء غرفة».\n\n" +
                        "٤) الباقون يفتحون «الانضمام إلى غرفة» ثم «بحث تلقائي»، أو يكتبون العنوان الظاهر عند المضيف.\n\n" +
                        "٥) المضيف يضغط «ابدأ المسابقة».\n\n" +
                        "الإجابة الصحيحة تعطي 500 نقطة، وكل ثانية متبقية تضيف نقاطاً أكثر، فكن سريعاً!\n\n" +
                        "الأسئلة المضافة من «إضافة أسئلة» تُستخدم فقط عندما يكون هاتفك هو المضيف.",
                    17f, Color.WHITE, false, Gravity.START
                )
            )
            addView(btn("رجوع", cCard2) { showHome() })
        }
    }

    // ------------------------------------------------------------------ المضيف

    private fun loadCustom(): List<Question> {
        return try {
            val c = prefs.getString("custom", null)
            if (c == null) emptyList() else parseQuestions(c)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun loadQuestions(): List<Question> {
        val list = ArrayList<Question>()
        try {
            val txt = assets.open("questions.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            list.addAll(parseQuestions(txt))
        } catch (e: Exception) {
        }
        list.addAll(loadCustom())
        return list
    }

    private fun startHosting(name: String) {
        leaveAll()
        unbindNetwork()
        val pool = loadQuestions()
        if (pool.isEmpty()) {
            toast("تعذر تحميل الأسئلة")
            return
        }
        val srv = GameServer(pool, name)
        try {
            srv.start(GAME_PORT) { broadcastTargets() }
        } catch (e: Exception) {
            toast("تعذر إنشاء الغرفة: " + (e.message ?: ""))
            return
        }
        server = srv
        isHost = true
        myName = name
        players = emptyList()
        showLobby()
        connectClient("127.0.0.1")
    }

    private fun connectClient(host: String) {
        val id = connSeq
        val c = GameClient(
            host, GAME_PORT, myName,
            { m -> runOnUiThread { if (id == connSeq) onMessage(m) } },
            { err -> runOnUiThread { if (id == connSeq) onDisconnected(err) } }
        )
        client = c
        c.connect()
    }

    // ------------------------------------------------------------------ الانضمام

    @Suppress("DEPRECATION")
    private fun gatewayIp(): String {
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val gw = wm.dhcpInfo.gateway
            if (gw != 0) {
                return (gw and 0xff).toString() + "." + ((gw shr 8) and 0xff) + "." +
                    ((gw shr 16) and 0xff) + "." + ((gw shr 24) and 0xff)
            }
        } catch (e: Exception) {
        }
        return prefs.getString("ip", "192.168.43.1") ?: "192.168.43.1"
    }

    @Suppress("DEPRECATION")
    private fun bindToWifi() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            for (n in cm.allNetworks) {
                val caps = cm.getNetworkCapabilities(n) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    if (Build.VERSION.SDK_INT >= 23) {
                        cm.bindProcessToNetwork(n)
                    } else {
                        ConnectivityManager.setProcessDefaultNetwork(n)
                    }
                    return
                }
            }
        } catch (e: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun unbindNetwork() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= 23) {
                cm.bindProcessToNetwork(null)
            } else {
                ConnectivityManager.setProcessDefaultNetwork(null)
            }
        } catch (e: Exception) {
        }
    }

    private fun acquireMulticast() {
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val l = wm.createMulticastLock("quiz")
            l.setReferenceCounted(false)
            l.acquire()
            mcLock = l
        } catch (e: Exception) {
        }
    }

    private fun releaseMulticast() {
        try {
            mcLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
        }
        mcLock = null
    }

    private fun showJoin(name: String) {
        screen = "join"
        myName = name
        val ip = edit("عنوان المضيف مثل 192.168.43.1")
        ip.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        ip.layoutDirection = View.LAYOUT_DIRECTION_LTR
        ip.textDirection = View.TEXT_DIRECTION_LTR
        ip.gravity = Gravity.CENTER
        ip.setText(gatewayIp())
        ipField = ip
        val status = tv("", 15f, cMuted)
        joinStatus = status
        val found = LinearLayout(this)
        found.orientation = LinearLayout.VERTICAL
        found.layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        foundBox = found
        show {
            addView(tv("الانضمام إلى غرفة", 26f, bold = true))
            addView(tv("اتصل أولاً بشبكة الهوت سبوت الخاصة بالمضيف من إعدادات الواي فاي", 15f, cMuted))
            addView(gap(8))
            addView(btn("🔍  بحث تلقائي عن الغرف", cAccent) { startDiscovery() })
            addView(found)
            addView(tv("أو اكتب العنوان يدوياً:", 14f, cMuted))
            addView(ip)
            addView(btn("اتصال", cGood) {
                val addr = ip.text.toString().trim()
                if (addr.isEmpty()) toast("اكتب عنوان المضيف") else doJoin(addr)
            })
            addView(status)
            addView(btn("رجوع", cCard2) {
                leaveAll()
                showHome()
            })
        }
    }

    private fun startDiscovery() {
        bindToWifi()
        acquireMulticast()
        foundBox?.removeAllViews()
        joinStatus?.text = "جارٍ البحث عن الغرف..."
        discoverHosts(
            6000,
            { ip, label, _ ->
                runOnUiThread {
                    if (screen == "join") {
                        ipField?.setText(ip)
                        foundBox?.addView(btn("🎮  غرفة $label  ($ip)", cGood) { doJoin(ip) })
                    }
                }
            },
            {
                runOnUiThread {
                    releaseMulticast()
                    if (screen == "join") {
                        val any = (foundBox?.childCount ?: 0) > 0
                        joinStatus?.text =
                            if (any) "اضغط على الغرفة للانضمام" else "لم يتم العثور على غرف. تأكد من الاتصال بالهوت سبوت أو اكتب العنوان يدوياً."
                    }
                }
            }
        )
    }

    private fun doJoin(ip: String) {
        leaveAll()
        bindToWifi()
        isHost = false
        joined = false
        prefs.edit().putString("ip", ip).apply()
        joinStatus?.text = "جارٍ الاتصال بـ $ip ..."
        connectClient(ip)
    }

    // ------------------------------------------------------------------ الرسائل

    private fun onMessage(j: JSONObject) {
        when (j.optString("t")) {
            "joined" -> {
                joined = true
                myName = j.optString("name", myName)
                if (!isHost) showLobby()
            }
            "players" -> {
                readPlayers(j)
                if (screen == "lobby") refreshPlayers()
            }
            "lobby" -> {
                readPlayers(j)
                if (screen == "lobby") refreshPlayers() else showLobby()
            }
            "error" -> {
                val msg = j.optString("msg", "حدث خطأ")
                leaveAll()
                showHome()
                toast(msg)
            }
            "question" -> showQuestion(j)
            "result" -> showResult(j)
            "final" -> showFinal(j)
        }
    }

    private fun readPlayers(j: JSONObject) {
        val arr = j.optJSONArray("list")
        val out = ArrayList<String>()
        if (arr != null) {
            for (i in 0 until arr.length()) out.add(arr.optString(i))
        }
        players = out
    }

    private fun onDisconnected(err: String?) {
        if (!joined && screen == "join") {
            joinStatus?.text = "تعذر الاتصال: " + (err ?: "") +
                "\nتأكد أنك متصل بهوت سبوت المضيف وأن العنوان صحيح"
            return
        }
        if (screen != "home") {
            leaveAll()
            showHome()
            toast("انقطع الاتصال بالغرفة")
        }
    }

    private fun leaveAll() {
        connSeq++
        stopTimer()
        try {
            client?.close()
        } catch (e: Exception) {
        }
        try {
            server?.stop()
        } catch (e: Exception) {
        }
        client = null
        server = null
        joined = false
        isHost = false
    }

    // ------------------------------------------------------------------ غرفة الانتظار

    private fun cycleButton(
        prefix: String,
        suffix: String,
        values: IntArray,
        get: () -> Int,
        set: (Int) -> Unit
    ): Button {
        val b = btn("", cCard2) { }
        val upd = { b.text = prefix + ": " + get() + " " + suffix + "  ⟳" }
        b.setOnClickListener {
            val i = values.indexOf(get())
            set(values[(i + 1) % values.size])
            upd()
        }
        upd()
        return b
    }

    private fun showLobby() {
        screen = "lobby"
        stopTimer()
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.background = rounded(cCard)
        box.setPadding(dp(14), dp(10), dp(14), dp(10))
        box.layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        playersBox = box
        val count = tv("", 16f, cMuted)
        playersCount = count

        show {
            addView(tv(if (isHost) "غرفتك جاهزة 🎉" else "تم الاتصال ✓", 26f, bold = true))
            if (isHost) {
                addView(tv("عنوان الغرفة الذي يكتبه أصدقاؤك (أو يجدونه بالبحث التلقائي):", 14f, cMuted))
                val ips = localIpv4()
                if (ips.isEmpty()) {
                    addView(tv("تأكد من تشغيل الهوت سبوت. عنوانه غالباً 192.168.43.1", 16f, cBad))
                } else {
                    for (a in ips) {
                        val t = tv(a, 28f, Color.parseColor("#7CFFB2"), true)
                        t.setTextIsSelectable(true)
                        t.layoutDirection = View.LAYOUT_DIRECTION_LTR
                        addView(t)
                    }
                }
            } else {
                addView(tv("بانتظار المضيف ليبدأ المسابقة...", 16f, cMuted))
            }
            addView(gap(6))
            addView(count)
            addView(box)
            if (isHost) {
                addView(gap(6))
                addView(cycleButton("عدد الأسئلة", "", intArrayOf(5, 10, 15, 20, 30), { qCount }, { qCount = it }))
                addView(cycleButton("وقت السؤال", "ثانية", intArrayOf(10, 15, 20, 30), { qSecs }, { qSecs = it }))
                addView(btn("▶  ابدأ المسابقة", cGood) {
                    val s = server
                    if (s == null || !s.startGame(qCount, qSecs)) toast("لا يمكن البدء الآن")
                })
            }
            addView(btn("خروج", cBad) {
                leaveAll()
                showHome()
            })
        }
        refreshPlayers()
    }

    private fun refreshPlayers() {
        val box = playersBox ?: return
        box.removeAllViews()
        playersCount?.text = "اللاعبون (" + players.size + ")"
        for (p in players) {
            box.addView(tv("👤  $p", 18f, Color.WHITE, false, Gravity.START))
        }
    }

    // ------------------------------------------------------------------ السؤال

    private fun stopTimer() {
        timerRunnable?.let { ui.removeCallbacks(it) }
        timerRunnable = null
    }

    private fun lockOptions() {
        for (b in optButtons) b.isEnabled = false
    }

    private fun startTimer(secs: Int, bar: ProgressBar, label: TextView) {
        stopTimer()
        val start = SystemClock.elapsedRealtime()
        val total = secs * 1000L
        val r = object : Runnable {
            override fun run() {
                val el = SystemClock.elapsedRealtime() - start
                val left = (total - el).coerceAtLeast(0L)
                bar.progress = left.toInt()
                label.text = ((left + 999) / 1000).toString()
                if (left > 0) {
                    ui.postDelayed(this, 100)
                } else {
                    lockOptions()
                    if (mySel == -1) statusView?.text = "انتهى الوقت ⏰"
                }
            }
        }
        timerRunnable = r
        ui.post(r)
    }

    private fun showQuestion(j: JSONObject) {
        screen = "question"
        val idx = j.optInt("i")
        val n = j.optInt("n")
        val secs = j.optInt("secs", 15)
        val arr = j.getJSONArray("opts")
        val opts = ArrayList<String>()
        for (k in 0 until arr.length()) opts.add(arr.getString(k))
        curOpts = opts
        curIdx = idx
        mySel = -1
        optButtons.clear()

        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = secs * 1000
        bar.progress = secs * 1000
        bar.progressTintList = ColorStateList.valueOf(cAccent)
        bar.layoutParams = LinearLayout.LayoutParams(MATCH, dp(10))
        val timerText = tv(secs.toString(), 40f, cAccent, true)
        val status = tv("", 15f, cMuted)
        statusView = status

        show {
            addView(tv("السؤال " + (idx + 1) + " من " + n, 15f, cMuted))
            addView(timerText)
            addView(bar)
            val q = tv(j.optString("text"), 22f, Color.WHITE, true)
            q.background = rounded(cCard)
            q.setPadding(dp(16), dp(20), dp(16), dp(20))
            val qlp = LinearLayout.LayoutParams(MATCH, WRAP)
            qlp.topMargin = dp(16)
            qlp.bottomMargin = dp(8)
            q.layoutParams = qlp
            addView(q)
            for ((k, text) in opts.withIndex()) {
                val b = btn(text, cCard2) { }
                b.setOnClickListener {
                    if (mySel == -1) {
                        mySel = k
                        for ((x, ob) in optButtons.withIndex()) {
                            ob.isEnabled = false
                            ob.background = rounded(if (x == k) cAccent else cCard, 14)
                        }
                        client?.send(JSONObject().put("t", "answer").put("i", idx).put("c", k))
                        status.text = "تم إرسال إجابتك ✓ بانتظار الآخرين..."
                    }
                }
                optButtons.add(b)
                addView(b)
            }
            addView(status)
        }
        startTimer(secs, bar, timerText)
    }

    // ------------------------------------------------------------------ النتيجة

    private fun showResult(j: JSONObject) {
        screen = "result"
        stopTimer()
        val correct = j.optInt("correct")
        val n = j.optInt("n")
        val idx = j.optInt("i")
        val board = j.getJSONArray("board")
        var myGain = 0
        var myOk = false
        var myAns = -1
        for (k in 0 until board.length()) {
            val e = board.getJSONObject(k)
            if (e.optString("name") == myName) {
                myGain = e.optInt("gain")
                myOk = e.optBoolean("ok")
                myAns = e.optInt("ans", -1)
            }
        }
        val correctText = if (correct in 0 until curOpts.size) curOpts[correct] else ""
        show {
            if (myOk) {
                addView(tv("✅ إجابة صحيحة!", 28f, Color.parseColor("#7CFFB2"), true))
                addView(tv("+$myGain نقطة", 22f, Color.WHITE, true))
            } else if (myAns == -1) {
                addView(tv("⏰ لم تُجب في الوقت", 26f, cBad, true))
            } else {
                addView(tv("❌ إجابة خاطئة", 28f, cBad, true))
            }
            addView(tv("الإجابة الصحيحة: $correctText", 18f, Color.WHITE))
            addView(gap(8))
            addView(tv("الترتيب الحالي", 16f, cMuted))
            for (k in 0 until board.length()) {
                val e = board.getJSONObject(k)
                val nm = e.optString("name")
                addView(boardRow(k + 1, nm, e.optInt("score"), e.optInt("gain"), nm == myName))
            }
            addView(
                tv(
                    if (idx + 1 >= n) "جارٍ حساب النتيجة النهائية..." else "السؤال التالي بعد قليل...",
                    14f, cMuted
                )
            )
        }
    }

    private fun showFinal(j: JSONObject) {
        screen = "final"
        stopTimer()
        val board = j.getJSONArray("board")
        var myRank = 0
        for (k in 0 until board.length()) {
            if (board.getJSONObject(k).optString("name") == myName) myRank = k + 1
        }
        show {
            addView(tv("🏆 النتيجة النهائية", 28f, bold = true))
            if (myRank == 1) {
                addView(tv("مبروك! أنت الفائز 🎉", 22f, Color.parseColor("#FFD54F"), true))
            } else if (myRank > 0) {
                addView(tv("ترتيبك: $myRank", 20f, Color.WHITE, true))
            }
            addView(gap(8))
            for (k in 0 until board.length()) {
                val e = board.getJSONObject(k)
                val nm = e.optString("name")
                addView(boardRow(k + 1, nm, e.optInt("score"), 0, nm == myName))
            }
            if (isHost) {
                addView(btn("🔄  لعبة جديدة بنفس اللاعبين", cGood) { server?.backToLobby() })
            } else {
                addView(tv("بانتظار المضيف لبدء لعبة جديدة، أو اخرج.", 14f, cMuted))
            }
            addView(btn("خروج", cBad) {
                leaveAll()
                showHome()
            })
        }
    }

    // ------------------------------------------------------------------ إضافة أسئلة

    private fun showAddQuestions() {
        screen = "add"
        val q = edit("نص السؤال")
        val opts = Array(4) { edit("الخيار " + (it + 1)) }
        var correct = 0
        val cb = btn("", cCard2) { }
        val updC = { cb.text = "الإجابة الصحيحة: الخيار " + (correct + 1) + "  ⟳" }
        cb.setOnClickListener {
            correct = (correct + 1) % 4
            updC()
        }
        updC()
        val countView = tv("", 14f, cMuted)
        val updCount = { countView.text = "أسئلتك المضافة: " + loadCustom().size }
        updCount()

        show {
            addView(tv("إضافة سؤال جديد", 26f, bold = true))
            addView(tv("تظهر أسئلتك مع أسئلة التطبيق عندما تكون أنت المضيف", 14f, cMuted))
            addView(q)
            for (o in opts) addView(o)
            addView(cb)
            addView(btn("💾  حفظ السؤال", cGood) {
                val text = q.text.toString().trim()
                val list = opts.map { it.text.toString().trim() }
                if (text.isEmpty() || list.any { it.isEmpty() }) {
                    toast("اكتب السؤال والخيارات الأربعة")
                } else if (list.toSet().size < 4) {
                    toast("الخيارات يجب أن تكون مختلفة")
                } else {
                    val all = ArrayList<Question>(loadCustom())
                    all.add(Question(text, list, correct))
                    prefs.edit().putString("custom", questionsToJson(all)).apply()
                    q.setText("")
                    for (o in opts) o.setText("")
                    updCount()
                    toast("تمت الإضافة ✓")
                }
            })
            addView(countView)
            addView(btn("🗑  حذف كل أسئلتي المضافة", cBad) {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage("حذف كل الأسئلة التي أضفتها؟")
                    .setPositiveButton("حذف") { _, _ ->
                        prefs.edit().remove("custom").apply()
                        updCount()
                    }
                    .setNegativeButton("إلغاء", null)
                    .show()
            })
            addView(btn("رجوع", cCard2) { showHome() })
        }
    }
}
