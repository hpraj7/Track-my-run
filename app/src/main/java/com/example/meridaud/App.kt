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
