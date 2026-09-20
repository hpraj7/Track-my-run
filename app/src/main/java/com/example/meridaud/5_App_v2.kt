package com.example.meridaud

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/* ------------------------------------------------------------------ */
/* Helpers                                                             */
/* ------------------------------------------------------------------ */

fun fmtTime(s: Long): String {
    val h = s / 3600
    val m = (s % 3600) / 60
    val x = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, x)
    else String.format(Locale.US, "%02d:%02d", m, x)
}

fun speedText(meters: Double, sec: Int): String {
    if (meters <= 0.0 || sec <= 0) return "--"
    return String.format(Locale.US, "%.1f", meters / sec * 3.6)
}

// keeps roughly one point every 8 m so saved runs stay small
fun compressRoute(a: List<DoubleArray>): List<DoubleArray> {
    val out = ArrayList<DoubleArray>()
    val res = FloatArray(1)
    var lastKept: DoubleArray? = null
    for (i in a.indices) {
        val q = a[i]
        val lk = lastKept
        var keep = lk == null || i == a.size - 1
        if (!keep && lk != null) {
            Location.distanceBetween(lk[0], lk[1], q[0], q[1], res)
            keep = res[0] >= 8f
        }
        if (keep) {
            lastKept = q
            out.add(doubleArrayOf(Math.round(q[0] * 1e5) / 1e5, Math.round(q[1] * 1e5) / 1e5))
        }
    }
    return out
}

/* ------------------------------------------------------------------ */
/* Live run state (shared between the service and the screen)          */
/* ------------------------------------------------------------------ */

object Tracker {
    const val IDLE = 0
    const val RUNNING = 1
    const val PAUSED = 2

    var state = IDLE
    var meters = 0.0
    var steps = 0
    var hasStepSensor = false
    var accuracy = -1f
    val pts = ArrayList<DoubleArray>()
    var onUpdate: (() -> Unit)? = null

    private var elapsedBefore = 0L
    private var segStart = 0L
    private var last: Location? = null
    private var lastCounter = -1f

    fun elapsedMs(): Long {
        val running = if (state == RUNNING) SystemClock.elapsedRealtime() - segStart else 0L
        return elapsedBefore + running
    }

    fun reset() {
        state = IDLE
        meters = 0.0
        steps = 0
        accuracy = -1f
        pts.clear()
        elapsedBefore = 0L
        last = null
        lastCounter = -1f
        onUpdate?.invoke()
    }

    fun start() {
        reset()
        state = RUNNING
        segStart = SystemClock.elapsedRealtime()
        onUpdate?.invoke()
    }

    fun pause() {
        if (state != RUNNING) return
        elapsedBefore += SystemClock.elapsedRealtime() - segStart
        state = PAUSED
        last = null
        lastCounter = -1f
        onUpdate?.invoke()
    }

    fun resume() {
        if (state != PAUSED) return
        segStart = SystemClock.elapsedRealtime()
        state = RUNNING
        last = null
        lastCounter = -1f
        onUpdate?.invoke()
    }

    // ends the run but keeps the numbers so they can be saved
    fun stop() {
        if (state == RUNNING) elapsedBefore += SystemClock.elapsedRealtime() - segStart
        state = IDLE
        onUpdate?.invoke()
    }

    fun onLocation(l: Location) {
        accuracy = l.accuracy
        if (state == RUNNING && l.accuracy <= 35f) {
            val prev = last
            var accept = true
            if (prev != null) {
                val d = prev.distanceTo(l)
                val dt = (l.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1e9
                if (d < maxOf(3f, l.accuracy * 0.5f)) {
                    accept = false                       // standing still / GPS jitter
                } else if (dt > 0 && d / dt > 12) {
                    accept = false                       // impossible running speed
                } else {
                    meters += d
                }
            }
            if (accept) {
                last = l
                pts.add(doubleArrayOf(l.latitude, l.longitude))
            }
        }
        onUpdate?.invoke()
    }

    fun onStep(v: Float) {
        if (state != RUNNING) return
        if (lastCounter >= 0f) steps += (v - lastCounter).toInt()
        lastCounter = v
        onUpdate?.invoke()
    }
}

/* ------------------------------------------------------------------ */
/* Saved runs                                                          */
/* ------------------------------------------------------------------ */

class Run(
    val id: Long,
    val ts: Long,
    val meters: Int,
    val sec: Int,
    val steps: Int,
    val route: List<DoubleArray>
)

object Store {
    private const val PREF = "runs"
    private const val KEY = "v1"

    fun load(ctx: Context): MutableList<Run> {
        val out = mutableListOf<Run>()
        try {
            val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ra = o.optJSONArray("route") ?: JSONArray()
                val route = ArrayList<DoubleArray>()
                for (j in 0 until ra.length()) {
                    val p = ra.getJSONArray(j)
                    route.add(doubleArrayOf(p.getDouble(0), p.getDouble(1)))
                }
                out.add(Run(o.getLong("id"), o.getLong("ts"), o.getInt("m"), o.getInt("sec"), o.optInt("steps", 0), route))
            }
        } catch (e: Exception) {
            // ignore broken data
        }
        return out
    }

    fun save(ctx: Context, runs: List<Run>) {
        val arr = JSONArray()
        for (r in runs) {
            val o = JSONObject()
            o.put("id", r.id)
            o.put("ts", r.ts)
            o.put("m", r.meters)
            o.put("sec", r.sec)
            o.put("steps", r.steps)
            val ra = JSONArray()
            for (p in r.route) {
                val a = JSONArray()
                a.put(p[0])
                a.put(p[1])
                ra.put(a)
            }
            o.put("route", ra)
            arr.put(o)
        }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }
}

/* ------------------------------------------------------------------ */
/* Route drawing: white box, blue line, north up, east right           */
/* ------------------------------------------------------------------ */

class RouteView(ctx: Context) : View(ctx) {
    var points: List<DoubleArray> = emptyList()
        set(value) {
            field = value
            invalidate()
        }
    var hint: String = ""

    private val dp = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3F7DF0")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 5f * dp
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
        color = Color.parseColor("#0E1A2B")
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5B6678")
        textSize = 15f * dp
        textAlign = Paint.Align.CENTER
    }

    init {
        val bg = GradientDrawable()
        bg.setColor(Color.WHITE)
        bg.cornerRadius = 12f * dp
        bg.setStroke((2f * dp).toInt(), Color.parseColor("#DCE0E6"), 6f * dp, 4f * dp)
        background = bg
    }

    // light 3-point averaging removes small GPS wiggles
    private fun smooth(a: List<DoubleArray>): List<DoubleArray> {
        if (a.size < 3) return a
        val out = ArrayList<DoubleArray>()
        out.add(a[0])
        for (i in 1 until a.size - 1) {
            out.add(
                doubleArrayOf(
                    (a[i - 1][0] + a[i][0] + a[i + 1][0]) / 3.0,
                    (a[i - 1][1] + a[i][1] + a[i + 1][1]) / 3.0
                )
            )
        }
        out.add(a[a.size - 1])
        return out
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toDouble()
        val h = height.toDouble()
        val src = points
        if (src.size < 2) {
            canvas.drawText(hint, (w / 2).toFloat(), (h / 2).toFloat(), hintPaint)
            return
        }

        val lat0 = src[0][0]
        val lon0 = src[0][1]
        val k = Math.cos(Math.toRadians(lat0))
        val xy = smooth(src.map { doubleArrayOf((it[1] - lon0) * k, -(it[0] - lat0)) })

        var minX = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        for (q in xy) {
            if (q[0] < minX) minX = q[0]
            if (q[0] > maxX) maxX = q[0]
            if (q[1] < minY) minY = q[1]
            if (q[1] > maxY) maxY = q[1]
        }
        val minSpan = 0.0003                                   // about 30 m, avoids wild zoom at the start
        val sw = maxOf(maxX - minX, minSpan)
        val sh = maxOf(maxY - minY, minSpan)
        val pad = 26.0 * dp
        val s = minOf((w - 2 * pad) / sw, (h - 2 * pad) / sh)
        val ox = (w - sw * s) / 2 - minX * s
        val oy = (h - sh * s) / 2 - minY * s

        val px = FloatArray(xy.size)
        val py = FloatArray(xy.size)
        for (i in xy.indices) {
            px[i] = (xy[i][0] * s + ox).toFloat()
            py[i] = (xy[i][1] * s + oy).toFloat()
        }

        val path = Path()
        path.moveTo(px[0], py[0])
        for (i in 1 until px.size - 1) {
            path.quadTo(px[i], py[i], (px[i] + px[i + 1]) / 2f, (py[i] + py[i + 1]) / 2f)
        }
        path.lineTo(px[px.size - 1], py[py.size - 1])
        canvas.drawPath(path, line)

        // start ring, then current / end dot
        fill.color = Color.WHITE
        canvas.drawCircle(px[0], py[0], 6f * dp, fill)
        canvas.drawCircle(px[0], py[0], 6f * dp, ring)
        fill.color = Color.parseColor("#0E1A2B")
        canvas.drawCircle(px[px.size - 1], py[py.size - 1], 7f * dp, fill)
    }
}

/* ------------------------------------------------------------------ */
/* Foreground service: keeps GPS and step counting alive, screen off   */
/* ------------------------------------------------------------------ */

class TrackerService : Service() {
    private lateinit var lm: LocationManager
    private lateinit var sm: SensorManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var registered = false

    private val locListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            Tracker.onLocation(location)
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private val stepListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            Tracker.onStep(event.values[0])
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override fun onCreate() {
        super.onCreate()
        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("run", "\u0926\u094c\u0921\u093c", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "run")
            .setContentTitle("\u0926\u094c\u0921\u093c \u091a\u0932 \u0930\u0939\u0940 \u0939\u0948")
            .setContentText("GPS \u0938\u0947 \u0926\u0942\u0930\u0940 \u0914\u0930 \u0930\u093e\u0938\u094d\u0924\u093e \u0928\u093e\u092a\u093e \u091c\u093e \u0930\u0939\u093e \u0939\u0948")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(open)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, n)
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (wakeLock == null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "meridaud:run")
            wakeLock?.acquire(6 * 60 * 60 * 1000L)
        }
        register()
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun register() {
        if (registered) return
        registered = true
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locListener, Looper.getMainLooper())
        } catch (e: Exception) {
            // permission or provider problem; the screen shows the GPS status
        }
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val allowed = Build.VERSION.SDK_INT < 29 ||
            checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        if (sensor != null && allowed) {
            sm.registerListener(stepListener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onDestroy() {
        try {
            lm.removeUpdates(locListener)
            sm.unregisterListener(stepListener)
        } catch (e: Exception) {
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        registered = false
        super.onDestroy()
    }
}

/* ------------------------------------------------------------------ */
/* Screen                                                              */
/* ------------------------------------------------------------------ */

class MainActivity : Activity() {
    private val ink = Color.parseColor("#0E1A2B")
    private val muted = Color.parseColor("#5B6678")
    private val blue = Color.parseColor("#1D46E0")
    private val lineColor = Color.parseColor("#D9DEE7")
    private val ground = Color.parseColor("#F2F4F7")
    private val red = Color.parseColor("#B3261E")
    private val amber = Color.parseColor("#A85A00")

    private val dp by lazy { resources.displayMetrics.density }
    private fun px(v: Int): Int = (v * dp).toInt()

    private lateinit var tvGps: TextView
    private lateinit var tvKm: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvSteps: TextView
    private lateinit var tvMsg: TextView
    private lateinit var routeView: RouteView
    private lateinit var bStart: Button
    private lateinit var bPause: Button
    private lateinit var pausedRow: LinearLayout
    private lateinit var tvTotal: TextView
    private lateinit var tvWeek: TextView
    private lateinit var tvCount: TextView
    private lateinit var historyBox: LinearLayout

    private var runs = mutableListOf<Run>()
    private var pendingStart = false
    private var askedOnce = false
    private var shownPts = -1
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 500)
        }
    }

    /* ---------- small view helpers ---------- */

    private fun tv(t: String, size: Float, color: Int = ink, bold: Boolean = false): TextView {
        val v = TextView(this)
        v.text = t
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        v.setTextColor(color)
        if (bold) v.typeface = Typeface.DEFAULT_BOLD
        return v
    }

    private fun pill(label: String, bg: Int, fg: Int, stroke: Int = 0): Button {
        val b = Button(this)
        b.text = label
        b.setAllCaps(false)
        b.setTextColor(fg)
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        b.typeface = Typeface.DEFAULT_BOLD
        val g = GradientDrawable()
        g.setColor(bg)
        g.cornerRadius = px(34).toFloat()
        if (stroke != 0) g.setStroke(px(2), stroke)
        b.background = g
        b.stateListAnimator = null
        return b
    }

    private fun divider(): View {
        val v = View(this)
        v.setBackgroundColor(lineColor)
        return v
    }

    private fun params(w: Int, h: Int, top: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h)
        p.topMargin = px(top)
        return p
    }

    private fun has(p: String): Boolean = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun stepsOk(): Boolean =
        Tracker.hasStepSensor && (Build.VERSION.SDK_INT < 29 || has(Manifest.permission.ACTIVITY_RECOGNITION))

    /* ---------- lifecycle ---------- */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = ground
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        runs = Store.load(this)
        val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        Tracker.hasStepSensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(ground)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(px(20), px(16), px(20), px(40))
        scroll.addView(root, ViewGroup.LayoutParams(MATCH, WRAP))
        setContentView(scroll)

        // header: title + GPS chip
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.addView(tv("\u092e\u0947\u0930\u0940 \u0926\u094c\u0921\u093c", 20f, ink, true), LinearLayout.LayoutParams(0, WRAP, 1f))
        tvGps = tv("", 13f, ink, true)
        tvGps.setPadding(px(12), px(6), px(12), px(6))
        val chip = GradientDrawable()
        chip.setColor(Color.WHITE)
        chip.cornerRadius = px(20).toFloat()
        chip.setStroke(px(2), lineColor)
        tvGps.background = chip
        tvGps.setOnClickListener { askPermissions() }
        header.addView(tvGps, LinearLayout.LayoutParams(WRAP, WRAP))
        root.addView(header, params(MATCH, WRAP))

        // distance
        val distRow = LinearLayout(this)
        distRow.orientation = LinearLayout.HORIZONTAL
        tvKm = tv("0", 88f, ink, true)
        tvKm.includeFontPadding = false
        distRow.addView(tvKm, LinearLayout.LayoutParams(WRAP, WRAP))
        val unit = tv("\u092e\u0940\u091f\u0930", 22f, muted, true)
        val unitLp = LinearLayout.LayoutParams(WRAP, WRAP)
        unitLp.leftMargin = px(10)
        distRow.addView(unit, unitLp)
        root.addView(distRow, params(MATCH, WRAP, 12))

        // time / pace / steps
        root.addView(divider(), params(MATCH, px(1) + 1, 10))
        val stats = LinearLayout(this)
        stats.orientation = LinearLayout.HORIZONTAL
        fun cell(label: String, first: Boolean): TextView {
            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL
            box.setPadding(if (first) 0 else px(12), px(12), 0, px(10))
            box.addView(tv(label, 12f, muted))
            val v = tv("--", 28f, ink, true)
            box.addView(v)
            stats.addView(box, LinearLayout.LayoutParams(0, WRAP, 1f))
            return v
        }
        tvTime = cell("\u0938\u092e\u092f", true)
        tvSpeed = cell("\u092a\u0947\u0938 (\u0915\u093f\u092e\u0940/\u0918\u0902\u091f\u093e)", false)
        tvSteps = cell("\u0915\u0926\u092e", false)
        tvTime.text = "00:00"
        root.addView(stats, params(MATCH, WRAP))
        root.addView(divider(), params(MATCH, px(1) + 1))

        // route box (below distance, time and pace)
        routeView = RouteView(this)
        routeView.hint = "\u0926\u094c\u0921\u093c \u0936\u0941\u0930\u0942 \u0915\u0930\u0928\u0947 \u092a\u0930 \u0930\u093e\u0938\u094d\u0924\u093e \u092f\u0939\u093e\u0901 \u092c\u0928\u0947\u0917\u093e"
        root.addView(routeView, params(MATCH, px(250), 16))

        tvMsg = tv("", 14f, amber)
        root.addView(tvMsg, params(MATCH, WRAP, 10))

        // buttons
        bStart = pill("\u0936\u0941\u0930\u0942 \u0915\u0930\u0947\u0902", blue, Color.WHITE)
        bStart.setOnClickListener { onStartClicked() }
        root.addView(bStart, params(MATCH, px(68), 8))

        bPause = pill("\u0930\u094b\u0915\u0947\u0902", ink, Color.WHITE)
        bPause.setOnClickListener { Tracker.pause() }
        root.addView(bPause, params(MATCH, px(68), 8))

        pausedRow = LinearLayout(this)
        pausedRow.orientation = LinearLayout.HORIZONTAL
        val bResume = pill("\u091c\u093e\u0930\u0940 \u0930\u0916\u0947\u0902", blue, Color.WHITE)
        bResume.setOnClickListener { Tracker.resume() }
        val bFinish = pill("\u0916\u093c\u0924\u094d\u092e \u0915\u0930\u0947\u0902", Color.WHITE, red, red)
        bFinish.setOnClickListener { finishRun() }
        val half1 = LinearLayout.LayoutParams(0, px(68), 1f)
        half1.rightMargin = px(6)
        val half2 = LinearLayout.LayoutParams(0, px(68), 1f)
        half2.leftMargin = px(6)
        pausedRow.addView(bResume, half1)
        pausedRow.addView(bFinish, half2)
        root.addView(pausedRow, params(MATCH, WRAP, 8))

        // history
        root.addView(tv("\u092a\u093f\u091b\u0932\u0940 \u0926\u094c\u0921\u093c\u0947\u0902", 18f, ink, true), params(MATCH, WRAP, 40))
        root.addView(divider(), params(MATCH, px(1) + 1, 12))
        val totals = LinearLayout(this)
        totals.orientation = LinearLayout.HORIZONTAL
        fun tcell(label: String, first: Boolean): TextView {
            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL
            box.setPadding(if (first) 0 else px(12), px(12), 0, px(10))
            val v = tv("0", 24f, ink, true)
            box.addView(v)
            box.addView(tv(label, 12f, muted))
            totals.addView(box, LinearLayout.LayoutParams(0, WRAP, 1f))
            return v
        }
        tvTotal = tcell("\u0915\u0941\u0932 \u092e\u0940\u091f\u0930", true)
        tvWeek = tcell("\u0907\u0938 \u0939\u092b\u093c\u094d\u0924\u0947 \u092e\u0940\u091f\u0930", false)
        tvCount = tcell("\u0926\u094c\u0921\u093c\u0947\u0902", false)
        root.addView(totals, params(MATCH, WRAP))
        root.addView(divider(), params(MATCH, px(1) + 1))
        historyBox = LinearLayout(this)
        historyBox.orientation = LinearLayout.VERTICAL
        root.addView(historyBox, params(MATCH, WRAP))

        refreshHistory()
        val need = missing()
        if (need.isNotEmpty()) requestPermissions(need, 1)
    }

    override fun onResume() {
        super.onResume()
        Tracker.onUpdate = { render() }
        handler.post(tick)
        render()
    }

    override fun onPause() {
        Tracker.onUpdate = null
        handler.removeCallbacks(tick)
        super.onPause()
    }

    /* ---------- permissions ---------- */

    private fun missing(): Array<String> {
        val l = ArrayList<String>()
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) {
            l.add(Manifest.permission.ACCESS_FINE_LOCATION)
            l.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 29 && !has(Manifest.permission.ACTIVITY_RECOGNITION)) {
            l.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= 33 && !has(Manifest.permission.POST_NOTIFICATIONS)) {
            l.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return l.toTypedArray()
    }

    private fun askPermissions() {
        if (has(Manifest.permission.ACCESS_FINE_LOCATION)) return
        if (askedOnce) {
            // Android stops showing the pop-up after a refusal, so open the app settings
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            startActivity(i)
        } else {
            val need = missing()
            if (need.isNotEmpty()) requestPermissions(need, 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val gotGps = has(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!gotGps) {
            askedOnce = true
            tvMsg.text = "GPS \u0915\u0940 \u0905\u0928\u0941\u092e\u0924\u093f \u0928\u0939\u0940\u0902 \u092e\u093f\u0932\u0940\u0964 \u201c\u0938\u091f\u0940\u0915 (Precise)\u201d \u0932\u094b\u0915\u0947\u0936\u0928 \u091a\u0941\u0928\u0915\u0930 \u0905\u0928\u0941\u092e\u0924\u093f \u0926\u0940\u091c\u093f\u090f\u0964 \u090a\u092a\u0930 GPS \u092c\u091f\u0928 \u0926\u092c\u093e\u0928\u0947 \u092a\u0930 \u0938\u0947\u091f\u093f\u0902\u0917 \u0916\u0941\u0932\u0947\u0917\u0940\u0964"
        } else {
            tvMsg.text = ""
        }
        if (pendingStart) {
            pendingStart = false
            if (gotGps) startRun()
        }
        render()
    }

    /* ---------- run flow ---------- */

    private fun onStartClicked() {
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) {
            pendingStart = true
            val need = missing()
            if (need.isNotEmpty() && !askedOnce) requestPermissions(need, 1) else askPermissions()
            return
        }
        startRun()
    }

    private fun startRun() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            tvMsg.text = "\u092b\u094b\u0928 \u092e\u0947\u0902 \u0932\u094b\u0915\u0947\u0936\u0928 (GPS) \u092c\u0902\u0926 \u0939\u0948\u0964 \u0938\u0947\u091f\u093f\u0902\u0917 \u092e\u0947\u0902 \u091a\u093e\u0932\u0942 \u0915\u0930\u0915\u0947 \u0926\u094b\u092c\u093e\u0930\u093e \u0936\u0941\u0930\u0942 \u0915\u0940\u091c\u093f\u090f\u0964"
            return
        }
        tvMsg.text = ""
        Tracker.start()
        startForegroundService(Intent(this, TrackerService::class.java))
    }

    private fun finishRun() {
        Tracker.stop()
        stopService(Intent(this, TrackerService::class.java))
        val m = Math.round(Tracker.meters).toInt()
        val sec = (Tracker.elapsedMs() / 1000).toInt()
        val steps = Tracker.steps
        if (m <= 0 || sec <= 0) {
            Toast.makeText(this, "\u0926\u0942\u0930\u0940 \u0928\u0939\u0940\u0902 \u092e\u093f\u0932\u0940, \u0907\u0938\u0932\u093f\u090f \u0926\u094c\u0921\u093c \u0938\u0947\u0935 \u0928\u0939\u0940\u0902 \u0939\u0941\u0908", Toast.LENGTH_LONG).show()
            Tracker.reset()
            return
        }
        var msg = "\u0926\u0942\u0930\u0940: $m \u092e\u0940\u091f\u0930\n\u0938\u092e\u092f: ${fmtTime(sec.toLong())}\n\u092a\u0947\u0938: ${speedText(m.toDouble(), sec)} \u0915\u093f\u092e\u0940/\u0918\u0902\u091f\u093e"
        if (stepsOk()) msg += "\n\u0915\u0926\u092e: $steps"
        AlertDialog.Builder(this)
            .setTitle("\u0926\u094c\u0921\u093c \u092a\u0942\u0930\u0940 \u0939\u0941\u0908")
            .setMessage(msg)
            .setCancelable(false)
            .setPositiveButton("\u0938\u0947\u0935 \u0915\u0930\u0947\u0902") { _, _ ->
                val now = System.currentTimeMillis()
                runs.add(0, Run(now, now, m, sec, if (stepsOk()) steps else 0, compressRoute(Tracker.pts)))
                Store.save(this, runs)
                Tracker.reset()
                refreshHistory()
                Toast.makeText(this, "\u0926\u094c\u0921\u093c \u0938\u0947\u0935 \u0939\u094b \u0917\u0908", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("\u0930\u0926\u094d\u0926 \u0915\u0930\u0947\u0902") { _, _ -> Tracker.reset() }
            .show()
    }

    /* ---------- drawing the screen ---------- */

    private fun render() {
        val m = Tracker.meters
        val sec = Tracker.elapsedMs() / 1000
        tvKm.text = Math.round(m).toString()
        tvTime.text = fmtTime(sec)
        tvSpeed.text = if (m >= 50 && sec > 0) speedText(m, sec.toInt()) else "--"
        tvSteps.text = if (stepsOk()) Tracker.steps.toString() else "--"

        if (shownPts != Tracker.pts.size) {
            shownPts = Tracker.pts.size
            routeView.points = ArrayList(Tracker.pts)
        }

        bStart.visibility = if (Tracker.state == Tracker.IDLE) View.VISIBLE else View.GONE
        bPause.visibility = if (Tracker.state == Tracker.RUNNING) View.VISIBLE else View.GONE
        pausedRow.visibility = if (Tracker.state == Tracker.PAUSED) View.VISIBLE else View.GONE

        if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) {
            tvGps.text = "GPS \u0915\u0940 \u0905\u0928\u0941\u092e\u0924\u093f \u0926\u0947\u0902"
            tvGps.setTextColor(red)
        } else if (Tracker.state == Tracker.RUNNING && Tracker.accuracy < 0f) {
            tvGps.text = "GPS \u0916\u094b\u091c \u0930\u0939\u093e \u0939\u0948\u2026"
            tvGps.setTextColor(amber)
        } else if (Tracker.state != Tracker.IDLE && Tracker.accuracy >= 0f) {
            tvGps.text = "GPS (\u00b1" + Tracker.accuracy.toInt() + " \u092e\u0940)"
            tvGps.setTextColor(if (Tracker.accuracy <= 20f) Color.parseColor("#17795A") else amber)
        } else {
            tvGps.text = "GPS \u0924\u0948\u092f\u093e\u0930"
            tvGps.setTextColor(ink)
        }
    }

    private fun refreshHistory() {
        var total = 0L
        var week = 0L
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val weekStart = cal.timeInMillis
        for (r in runs) {
            total += r.meters
            if (r.ts >= weekStart) week += r.meters
        }
        tvTotal.text = total.toString()
        tvWeek.text = week.toString()
        tvCount.text = runs.size.toString()

        historyBox.removeAllViews()
        if (runs.isEmpty()) {
            val e = tv("\u0905\u092d\u0940 \u0915\u094b\u0908 \u0926\u094c\u0921\u093c \u0938\u0947\u0935 \u0928\u0939\u0940\u0902 \u0939\u0948\u0964 \u201c\u0936\u0941\u0930\u0942 \u0915\u0930\u0947\u0902\u201d \u0926\u092c\u093e\u0915\u0930 \u092a\u0939\u0932\u0940 \u0926\u094c\u0921\u093c \u0936\u0941\u0930\u0942 \u0915\u0940\u091c\u093f\u090f\u0964", 15f, muted)
            e.setPadding(0, px(20), 0, px(20))
            historyBox.addView(e, params(MATCH, WRAP))
            return
        }

        val fmt = SimpleDateFormat("d MMM, h:mm a", Locale("hi", "IN"))
        for (r in runs) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(0, px(12), 0, px(6))

            val left = LinearLayout(this)
            left.orientation = LinearLayout.VERTICAL
            left.addView(tv(fmt.format(Date(r.ts)), 15f, ink, true))
            var meta = fmtTime(r.sec.toLong()) + " \u0938\u092e\u092f, " + speedText(r.meters.toDouble(), r.sec) + " \u0915\u093f\u092e\u0940/\u0918\u0902\u091f\u093e"
            if (r.steps > 0) meta += ", " + r.steps + " \u0915\u0926\u092e"
            left.addView(tv(meta, 13f, muted))

            val actions = LinearLayout(this)
            actions.orientation = LinearLayout.HORIZONTAL
            if (r.route.size >= 2) {
                val see = tv("\u0930\u0942\u091f \u0926\u0947\u0916\u0947\u0902", 14f, blue, true)
                see.setPadding(0, px(8), px(20), px(8))
                see.setOnClickListener { showRoute(r) }
                actions.addView(see)
            }
            val del = tv("\u0939\u091f\u093e\u090f\u0901", 14f, muted)
            del.setPadding(0, px(8), px(8), px(8))
            del.setOnClickListener { confirmDelete(r) }
            actions.addView(del)
            left.addView(actions)

            row.addView(left, LinearLayout.LayoutParams(0, WRAP, 1f))
            val right = tv(r.meters.toString() + " \u092e\u0940", 24f, ink, true)
            row.addView(right, LinearLayout.LayoutParams(WRAP, WRAP))
            historyBox.addView(row, params(MATCH, WRAP))
            historyBox.addView(divider(), params(MATCH, px(1) + 1))
        }
    }

    private fun showRoute(r: Run) {
        val rv = RouteView(this)
        rv.points = r.route
        val holder = LinearLayout(this)
        holder.setPadding(px(16), px(8), px(16), 0)
        holder.addView(rv, LinearLayout.LayoutParams(MATCH, px(320)))
        val fmt = SimpleDateFormat("d MMM, h:mm a", Locale("hi", "IN"))
        AlertDialog.Builder(this)
            .setTitle(fmt.format(Date(r.ts)))
            .setView(holder)
            .setPositiveButton("\u092c\u0902\u0926 \u0915\u0930\u0947\u0902", null)
            .show()
    }

    private fun confirmDelete(r: Run) {
        AlertDialog.Builder(this)
            .setMessage("\u092f\u0939 \u0926\u094c\u0921\u093c \u0939\u091f\u093e \u0926\u0947\u0902?")
            .setPositiveButton("\u0939\u091f\u093e\u090f\u0901") { _, _ ->
                runs.remove(r)
                Store.save(this, runs)
                refreshHistory()
            }
            .setNegativeButton("\u0930\u0939\u0928\u0947 \u0926\u0947\u0902", null)
            .show()
    }
}
