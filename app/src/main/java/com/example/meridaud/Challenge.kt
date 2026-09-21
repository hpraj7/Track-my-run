package com.example.meridaud

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val C_INK = Color.parseColor("#0E1A2B")
private val C_MUTED = Color.parseColor("#5B6678")
private val C_BLUE = Color.parseColor("#1D46E0")
private val C_LINE = Color.parseColor("#D9DEE7")
private val C_GROUND = Color.parseColor("#F2F4F7")
private val C_GREEN = Color.parseColor("#17795A")
private const val M = ViewGroup.LayoutParams.MATCH_PARENT
private const val W = ViewGroup.LayoutParams.WRAP_CONTENT

/* ------------------------------------------------------------------ */
/* Small view helpers                                                  */
/* ------------------------------------------------------------------ */

private fun px(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

private fun label(c: Context, t: String, size: Float, color: Int = C_INK, bold: Boolean = false): TextView {
    val v = TextView(c)
    v.text = t
    v.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
    v.setTextColor(color)
    if (bold) v.typeface = Typeface.DEFAULT_BOLD
    return v
}

private fun shape(c: Context, color: Int, radiusDp: Int, strokeColor: Int = 0): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(color)
    g.cornerRadius = px(c, radiusDp).toFloat()
    if (strokeColor != 0) g.setStroke(px(c, 2), strokeColor)
    return g
}

private fun pill(c: Context, t: String, bg: Int, fg: Int): Button {
    val b = Button(c)
    b.text = t
    b.setAllCaps(false)
    b.setTextColor(fg)
    b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
    b.typeface = Typeface.DEFAULT_BOLD
    b.background = shape(c, bg, 30)
    b.stateListAnimator = null
    return b
}

private fun lpar(c: Context, w: Int, h: Int, topDp: Int = 0): LinearLayout.LayoutParams {
    val p = LinearLayout.LayoutParams(w, h)
    p.topMargin = px(c, topDp)
    return p
}

private fun divider(c: Context): View {
    val v = View(c)
    v.setBackgroundColor(C_LINE)
    return v
}

/* ------------------------------------------------------------------ */
/* The 10 day plan                                                     */
/* ------------------------------------------------------------------ */

// minSec / minMeters > 0 means: completed automatically by a saved run that day
class PItem(val key: String, val text: String, val minSec: Int = 0, val minMeters: Int = 0) {
    val auto: Boolean get() = minSec > 0 || minMeters > 0
}

class PSection(val title: String, val items: List<PItem>, val notes: List<String> = emptyList())

class DayPlan(val n: Int, val title: String, val notes: List<String>, val sections: List<PSection>)

object Plan {
    private fun reps(prefix: String, count: Int, text: (Int) -> String): List<PItem> {
        val out = ArrayList<PItem>()
        for (i in 1..count) out.add(PItem(prefix + i, text(i)))
        return out
    }

    private fun sets(prefix: String, name: String, count: Int, detail: String): List<PItem> =
        reps(prefix, count) { i -> name + ": \u0938\u0947\u091f " + i + " (" + detail + ")" }

    val days: List<DayPlan> = listOf(
        DayPlan(
            1, "\u0905\u092d\u0940 \u0915\u093e \u091f\u093e\u0907\u092e\u093f\u0902\u0917 \u092a\u0924\u093e \u0915\u0930\u0947\u0902",
            listOf("\u0932\u0915\u094d\u0937\u094d\u092f: \u0905\u092d\u0940 \u0906\u092a\u0915\u0940 \u0935\u093e\u0938\u094d\u0924\u0935\u093f\u0915 \u0915\u094d\u0937\u092e\u0924\u093e \u092a\u0924\u093e \u0915\u0930\u0928\u093e\u0964"),
            listOf(
                PSection(
                    "\u0935\u0949\u0930\u094d\u092e-\u0905\u092a (12-15 \u092e\u093f\u0928\u091f)",
                    listOf(
                        PItem("w1", "5 \u092e\u093f\u0928\u091f \u0939\u0932\u094d\u0915\u0940 \u091c\u0949\u0917\u093f\u0902\u0917"),
                        PItem("w2", "\u0939\u093e\u0908 \u0928\u0940\u091c\u093c 2 \u00d7 20 \u092e\u0940"),
                        PItem("w3", "\u092c\u091f \u0915\u093f\u0915\u094d\u0938 2 \u00d7 20 \u092e\u0940"),
                        PItem("w4", "\u0932\u0947\u0917 \u0938\u094d\u0935\u093f\u0902\u0917\u094d\u0938 10 + 10 \u0939\u0930 \u092a\u0948\u0930"),
                        PItem("w5", "3 \u00d7 50 \u092e\u0940 \u0939\u0932\u094d\u0915\u0940 \u0924\u0947\u091c\u093c \u0938\u094d\u091f\u094d\u0930\u093e\u0907\u0921\u094d\u0938")
                    )
                ),
                PSection(
                    "\u092e\u0941\u0916\u094d\u092f \u0926\u094c\u0921\u093c",
                    listOf(PItem("m1", "1600 \u092e\u0940 \u090f\u0915 \u092c\u093e\u0930 \u092a\u0942\u0930\u093e \u0926\u094c\u0921\u093c\u0947\u0902", 0, 1500)),
                    listOf(
                        "\u0936\u0941\u0930\u0941\u0906\u0924 \u092e\u0947\u0902 \u092c\u0939\u0941\u0924 \u0924\u0947\u091c\u093c \u0928 \u092d\u093e\u0917\u0947\u0902\u0964",
                        "\u0905\u092a\u0928\u093e \u092a\u0942\u0930\u093e \u091f\u093e\u0907\u092e\u093f\u0902\u0917 \u0928\u094b\u091f \u0915\u0930\u0947\u0902\u0964",
                        "\u0939\u0930 400 \u092e\u0940 \u0915\u093e \u0938\u094d\u092a\u094d\u0932\u093f\u091f \u092d\u0940, \u0938\u0902\u092d\u0935 \u0939\u094b \u0924\u094b, \u0928\u094b\u091f \u0915\u0930\u0947\u0902\u0964"
                    )
                ),
                PSection("\u0915\u0942\u0932-\u0921\u093e\u0909\u0928", listOf(PItem("c1", "5-10 \u092e\u093f\u0928\u091f \u0935\u0949\u0915")))
            )
        ),
        DayPlan(
            2, "\u0938\u094d\u092a\u0940\u0921",
            listOf("\u0932\u0915\u094d\u0937\u094d\u092f: \u0924\u0947\u091c\u093c \u092a\u0947\u0938 \u092a\u0930 \u0936\u0930\u0940\u0930 \u0915\u094b \u0905\u0928\u0941\u0915\u0942\u0932 \u0915\u0930\u093e\u0928\u093e\u0964"),
            listOf(
                PSection("\u0935\u0949\u0930\u094d\u092e-\u0905\u092a", listOf(PItem("w1", "10-15 \u092e\u093f\u0928\u091f \u0935\u0949\u0930\u094d\u092e-\u0905\u092a"))),
                PSection(
                    "\u092e\u0941\u0916\u094d\u092f \u0935\u0930\u094d\u0915\u0906\u0909\u091f: 6 \u00d7 200 \u092e\u0940",
                    reps("r", 6) { i -> "\u0930\u0947\u092a " + i + " (200 \u092e\u0940)" },
                    listOf(
                        "\u0939\u0930 200 \u092e\u0940 \u0932\u0917\u092d\u0917 48-52 \u0938\u0947\u0915\u0902\u0921 \u0915\u0947 \u0906\u0938\u092a\u093e\u0938\u0964",
                        "\u0939\u0930 \u0930\u0947\u092a \u0915\u0947 \u092c\u093e\u0926 90-120 \u0938\u0947\u0915\u0902\u0921 \u0935\u0949\u0915 \u092f\u093e \u091c\u0949\u0917 \u0930\u093f\u0915\u0935\u0930\u0940\u0964"
                    )
                ),
                PSection(
                    "\u0905\u0902\u0924 \u092e\u0947\u0902: 4 \u00d7 100 \u092e\u0940",
                    reps("s", 4) { i -> "\u0930\u093f\u0932\u0948\u0915\u094d\u0938\u094d\u0921 \u092b\u093c\u093e\u0938\u094d\u091f \u0938\u094d\u091f\u094d\u0930\u093e\u0907\u0921 " + i + " (100 \u092e\u0940)" },
                    listOf("\u0939\u0930 \u0938\u094d\u091f\u094d\u0930\u093e\u0907\u0921 \u0915\u0947 \u092c\u0940\u091a \u092a\u0942\u0930\u093e \u0906\u0930\u093e\u092e\u0964")
                ),
                PSection("\u0915\u0942\u0932-\u0921\u093e\u0909\u0928", listOf(PItem("c1", "8 \u092e\u093f\u0928\u091f \u0935\u0949\u0915")))
            )
        ),
        DayPlan(
            3, "\u0906\u0938\u093e\u0928 \u0926\u094c\u0921\u093c + \u092b\u093c\u0949\u0930\u094d\u092e",
            listOf("\u0906\u091c \u0936\u0930\u0940\u0930 \u0915\u094b \u0930\u093f\u0915\u0935\u0930\u0940 \u092d\u0940 \u092e\u093f\u0932\u0947\u0917\u0940\u0964"),
            listOf(
                PSection(
                    "\u0906\u0938\u093e\u0928 \u0926\u094c\u0921\u093c",
                    listOf(PItem("m1", "20-25 \u092e\u093f\u0928\u091f \u0906\u0938\u093e\u0928 \u0926\u094c\u0921\u093c", 1200, 0)),
                    listOf("\u0910\u0938\u0940 \u0930\u092b\u093c\u094d\u0924\u093e\u0930 \u0930\u0916\u0947\u0902 \u091c\u093f\u0938 \u092a\u0930 \u0906\u092a \u0928\u093f\u092f\u0902\u0924\u094d\u0930\u093f\u0924 \u0938\u093e\u0901\u0938 \u0932\u0947 \u0938\u0915\u0947\u0902\u0964")
                ),
                PSection(
                    "\u092b\u093c\u0949\u0930\u094d\u092e \u0935\u093e\u0932\u0940 \u0915\u0938\u0930\u0924",
                    sets("k", "\u0915\u093e\u092b\u093c \u0930\u0947\u091c\u093c\u0947\u091c\u093c", 2, "15") +
                        sets("g", "\u0917\u094d\u0932\u0942\u091f \u092c\u094d\u0930\u093f\u091c", 2, "15") +
                        sets("q", "\u092c\u0949\u0921\u0940\u0935\u0947\u091f \u0938\u094d\u0915\u094d\u0935\u0948\u091f\u094d\u0938", 2, "10") +
                        sets("p", "\u092a\u094d\u0932\u0948\u0902\u0915", 2, "30 \u0938\u0947\u0915\u0902\u0921")
                )
            )
        ),
        DayPlan(
            4, "400 \u092e\u0940 \u0907\u0902\u091f\u0930\u0935\u0932",
            listOf(
                "\u092f\u0939 \u092e\u0939\u0924\u094d\u0935\u092a\u0942\u0930\u094d\u0923 \u0926\u093f\u0928 \u0939\u0948\u0964",
                "\u092f\u0939 \u0935\u0930\u094d\u0915\u0906\u0909\u091f \u0906\u092a\u0915\u094b \u0932\u0917\u093e\u0924\u093e\u0930 \u0924\u0947\u091c\u093c \u092a\u0947\u0938 \u092c\u0928\u093e\u090f \u0930\u0916\u0928\u0947 \u092e\u0947\u0902 \u092e\u0926\u0926 \u0915\u0930\u0947\u0917\u093e\u0964"
            ),
            listOf(
                PSection("\u0935\u0949\u0930\u094d\u092e-\u0905\u092a", listOf(PItem("w1", "15 \u092e\u093f\u0928\u091f \u0935\u0949\u0930\u094d\u092e-\u0905\u092a"))),
                PSection(
                    "\u092e\u0941\u0916\u094d\u092f: 4 \u00d7 400 \u092e\u0940",
                    reps("r", 4) { i -> "\u0930\u0947\u092a " + i + " (400 \u092e\u0940)" },
                    listOf(
                        "\u0932\u0915\u094d\u0937\u094d\u092f: 1:40-1:45 \u092a\u094d\u0930\u0924\u093f \u0930\u0947\u092a\u0964",
                        "\u0939\u0930 \u0930\u0947\u092a \u0915\u0947 \u092c\u093e\u0926 2-3 \u092e\u093f\u0928\u091f \u0930\u093f\u0915\u0935\u0930\u0940\u0964"
                    )
                ),
                PSection(
                    "\u092b\u093f\u0930: 2 \u00d7 200 \u092e\u0940",
                    reps("s", 2) { i -> "\u0930\u0947\u092a " + i + " (200 \u092e\u0940)" },
                    listOf("\u0939\u0930 \u0930\u0947\u092a \u0932\u0917\u092d\u0917 48-50 \u0938\u0947\u0915\u0902\u0921\u0964")
                )
            )
        ),
        DayPlan(
            5, "\u0930\u093f\u0915\u0935\u0930\u0940 \u0921\u0947",
            listOf(
                "\u0906\u091c \u0936\u0930\u0940\u0930 \u0915\u094b \u0906\u0930\u093e\u092e \u0926\u0947\u0928\u093e \u091c\u093c\u0930\u0942\u0930\u0940 \u0939\u0948\u0964",
                "\u0915\u094b\u0908 \u0939\u093e\u0930\u094d\u0921 \u0907\u0902\u091f\u0930\u0935\u0932 \u0928\u0939\u0940\u0902\u0964"
            ),
            listOf(
                PSection(
                    "\u0939\u0932\u094d\u0915\u0940 \u0917\u0924\u093f\u0935\u093f\u0927\u093f",
                    listOf(PItem("m1", "15-20 \u092e\u093f\u0928\u091f \u092c\u0939\u0941\u0924 \u0939\u0932\u094d\u0915\u093e \u091c\u0949\u0917 \u092f\u093e \u0924\u0947\u091c\u093c \u0935\u0949\u0915", 900, 0))
                ),
                PSection(
                    "\u092e\u094b\u092c\u093f\u0932\u093f\u091f\u0940",
                    listOf(
                        PItem("b1", "\u090f\u0902\u0915\u0932 \u0938\u0930\u094d\u0915\u0932\u094d\u0938"),
                        PItem("b2", "\u0932\u0947\u0917 \u0938\u094d\u0935\u093f\u0902\u0917\u094d\u0938"),
                        PItem("b3", "\u0939\u093f\u092a \u0938\u0930\u094d\u0915\u0932\u094d\u0938"),
                        PItem("b4", "\u0915\u093e\u092b\u093c \u0938\u094d\u091f\u094d\u0930\u0947\u091a"),
                        PItem("b5", "\u0939\u0948\u092e\u0938\u094d\u091f\u094d\u0930\u093f\u0902\u0917 \u0938\u094d\u091f\u094d\u0930\u0947\u091a")
                    )
                )
            )
        ),
        DayPlan(
            6, "\u0938\u094d\u092a\u0940\u0921 \u090f\u0902\u0921\u094d\u092f\u094b\u0930\u0947\u0902\u0938",
            listOf("\u092f\u0939 \u0926\u093f\u0928 1600 \u092e\u0940 \u0915\u0947 \u0906\u0916\u093c\u093f\u0930\u0940 \u0939\u093f\u0938\u094d\u0938\u0947 \u0915\u0947 \u0932\u093f\u090f \u092e\u0939\u0924\u094d\u0935\u092a\u0942\u0930\u094d\u0923 \u0939\u0948\u0964"),
            listOf(
                PSection("\u0935\u0949\u0930\u094d\u092e-\u0905\u092a", listOf(PItem("w1", "15 \u092e\u093f\u0928\u091f \u0935\u0949\u0930\u094d\u092e-\u0905\u092a"))),
                PSection(
                    "\u092e\u0941\u0916\u094d\u092f: 800 \u092e\u0940 \u2192 600 \u092e\u0940 \u2192 400 \u092e\u0940",
                    listOf(
                        PItem("m1", "800 \u092e\u0940: \u0928\u093f\u092f\u0902\u0924\u094d\u0930\u093f\u0924 \u0924\u0947\u091c\u093c"),
                        PItem("m2", "600 \u092e\u0940: \u0924\u0947\u091c\u093c"),
                        PItem("m3", "400 \u092e\u0940: \u0924\u0947\u091c\u093c")
                    ),
                    listOf(
                        "800 \u092e\u0940 \u0915\u0947 \u092c\u093e\u0926 3-4 \u092e\u093f\u0928\u091f \u0930\u093f\u0915\u0935\u0930\u0940\u0964",
                        "600 \u092e\u0940 \u0915\u0947 \u092c\u093e\u0926 3 \u092e\u093f\u0928\u091f \u0930\u093f\u0915\u0935\u0930\u0940\u0964"
                    )
                ),
                PSection(
                    "\u0907\u0938\u0915\u0947 \u092c\u093e\u0926: 4 \u00d7 100 \u092e\u0940",
                    reps("s", 4) { i -> "\u0938\u094d\u091f\u094d\u0930\u093e\u0907\u0921 " + i + " (100 \u092e\u0940)" }
                )
            )
        ),
        DayPlan(
            7, "\u0906\u0938\u093e\u0928 \u0926\u094c\u0921\u093c + \u0938\u094d\u091f\u094d\u0930\u093e\u0907\u0921\u094d\u0938",
            listOf("\u0906\u091c \u0925\u0915\u093e\u0928\u0947 \u0935\u093e\u0932\u0940 \u091f\u094d\u0930\u0947\u0928\u093f\u0902\u0917 \u0928\u0939\u0940\u0902\u0964"),
            listOf(
                PSection(
                    "\u0906\u0938\u093e\u0928 \u0926\u094c\u0921\u093c",
                    listOf(PItem("m1", "20 \u092e\u093f\u0928\u091f \u0906\u0938\u093e\u0928 \u0926\u094c\u0921\u093c", 1200, 0))
                ),
                PSection(
                    "4 \u00d7 100 \u092e\u0940 \u0938\u094d\u091f\u094d\u0930\u093e\u0907\u0921\u094d\u0938",
                    reps("s", 4) { i -> "\u0938\u094d\u091f\u094d\u0930\u093e\u0907\u0921 " + i + " (100 \u092e\u0940)" },
                    listOf("\u0939\u0930 \u0938\u094d\u091f\u094d\u0930\u093e\u0907\u0921 \u0915\u0947 \u092c\u093e\u0926 1-2 \u092e\u093f\u0928\u091f \u0906\u0930\u093e\u092e\u0964")
                ),
                PSection(
                    "\u0939\u0932\u094d\u0915\u0940 \u092e\u094b\u092c\u093f\u0932\u093f\u091f\u0940",
                    listOf(PItem("b1", "\u090f\u0902\u0915\u0932 \u0938\u0930\u094d\u0915\u0932\u094d\u0938, \u0932\u0947\u0917 \u0938\u094d\u0935\u093f\u0902\u0917\u094d\u0938, \u0939\u093f\u092a \u0938\u0930\u094d\u0915\u0932\u094d\u0938, \u0915\u093e\u092b\u093c \u0914\u0930 \u0939\u0948\u092e\u0938\u094d\u091f\u094d\u0930\u093f\u0902\u0917 \u0938\u094d\u091f\u094d\u0930\u0947\u091a"))
                )
            )
        ),
        DayPlan(
            8, "\u0930\u0947\u0938 \u092a\u0947\u0938 \u092a\u094d\u0930\u0948\u0915\u094d\u091f\u093f\u0938",
            listOf("\u092f\u0939 \u0938\u092c\u0938\u0947 \u092e\u0939\u0924\u094d\u0935\u092a\u0942\u0930\u094d\u0923 \u091f\u094d\u0930\u0947\u0928\u093f\u0902\u0917 \u0938\u0947\u0936\u0928\u094b\u0902 \u092e\u0947\u0902 \u0938\u0947 \u090f\u0915 \u0939\u0948\u0964"),
            listOf(
                PSection("\u0935\u0949\u0930\u094d\u092e-\u0905\u092a", listOf(PItem("w1", "15 \u092e\u093f\u0928\u091f \u0935\u0949\u0930\u094d\u092e-\u0905\u092a"))),
                PSection(
                    "\u092e\u0941\u0916\u094d\u092f: 4 \u00d7 400 \u092e\u0940",
                    reps("r", 4) { i -> "\u0930\u0947\u092a " + i + " (400 \u092e\u0940)" },
                    listOf(
                        "\u0932\u0915\u094d\u0937\u094d\u092f: \u0927\u0940\u0930\u0947-\u0927\u0940\u0930\u0947 6 \u092e\u093f\u0928\u091f \u0915\u0947 \u092a\u0947\u0938 \u0915\u0947 \u0915\u093c\u0930\u0940\u092c, \u092f\u093e\u0928\u0940 1:30-1:35 \u092a\u094d\u0930\u0924\u093f 400 \u092e\u0940\u0964",
                        "\u0905\u0917\u0930 1:30 \u092a\u0930 \u092b\u093c\u0949\u0930\u094d\u092e \u092c\u093f\u0917\u0921\u093c\u0924\u0940 \u0939\u0948, \u0924\u094b \u0909\u0938\u0947 \u091c\u093c\u092c\u0930\u0926\u0938\u094d\u0924\u0940 \u0928 \u0915\u0930\u0947\u0902\u0964",
                        "\u0939\u0930 400 \u092e\u0940 \u0915\u0947 \u092c\u093e\u0926 3-4 \u092e\u093f\u0928\u091f \u0930\u093f\u0915\u0935\u0930\u0940 \u0932\u0947\u0902\u0964"
                    )
                ),
                PSection(
                    "\u0905\u0902\u0924 \u092e\u0947\u0902: 2 \u00d7 100 \u092e\u0940",
                    reps("s", 2) { i -> "\u0930\u093f\u0932\u0948\u0915\u094d\u0938\u094d\u0921 \u0938\u094d\u091f\u094d\u0930\u093e\u0907\u0921 " + i + " (100 \u092e\u0940)" }
                )
            )
        ),
        DayPlan(
            9, "\u091f\u0947\u092a\u0930 / \u092b\u094d\u0930\u0947\u0936\u0928\u0947\u0938",
            listOf(
                "\u0906\u091c \u0936\u0930\u0940\u0930 \u0915\u094b \u092b\u094d\u0930\u0947\u0936 \u0930\u0916\u0928\u093e \u0939\u0948\u0964",
                "\u0915\u094b\u0908 1600 \u092e\u0940 \u091f\u0947\u0938\u094d\u091f \u0928\u0939\u0940\u0902\u0964 \u0915\u094b\u0908 \u0939\u093e\u0930\u094d\u0921 \u0907\u0902\u091f\u0930\u0935\u0932 \u0928\u0939\u0940\u0902\u0964",
                "\u0905\u091a\u094d\u091b\u0940 \u0928\u0940\u0902\u0926 \u0932\u0947\u0902 \u0914\u0930 \u092a\u093e\u0928\u0940 \u092a\u0930\u094d\u092f\u093e\u092a\u094d\u0924 \u092a\u093f\u090f\u0901\u0964"
            ),
            listOf(
                PSection(
                    "\u0939\u0932\u094d\u0915\u093e \u091c\u0949\u0917",
                    listOf(PItem("m1", "10-15 \u092e\u093f\u0928\u091f \u092c\u0939\u0941\u0924 \u0906\u0938\u093e\u0928 \u091c\u0949\u0917", 600, 0))
                ),
                PSection(
                    "3 \u00d7 100 \u092e\u0940 \u0939\u0932\u094d\u0915\u0940 \u0924\u0947\u091c\u093c \u0938\u094d\u091f\u094d\u0930\u093e\u0907\u0921\u094d\u0938",
                    reps("s", 3) { i -> "\u0938\u094d\u091f\u094d\u0930\u093e\u0907\u0921 " + i + " (100 \u092e\u0940)" },
                    listOf("\u092a\u0942\u0930\u0940 \u0930\u093f\u0915\u0935\u0930\u0940\u0964 \u092c\u0938\u0964")
                )
            )
        ),
        DayPlan(
            10, "1600 \u092e\u0940 \u091f\u0947\u0938\u094d\u091f",
            listOf("\u0906\u091c \u092b\u093c\u093e\u0907\u0928\u0932 \u0905\u091f\u0947\u092e\u094d\u092a\u094d\u091f \u0939\u094b\u0917\u093e\u0964"),
            listOf(
                PSection(
                    "\u0935\u0949\u0930\u094d\u092e-\u0905\u092a",
                    listOf(
                        PItem("w1", "5-8 \u092e\u093f\u0928\u091f \u0906\u0938\u093e\u0928 \u091c\u0949\u0917"),
                        PItem("w2", "\u0921\u093e\u092f\u0928\u093e\u092e\u093f\u0915 \u0921\u094d\u0930\u093f\u0932\u094d\u0938")
                    ) + reps("w3", 3) { i -> "\u092a\u094d\u0930\u094b\u0917\u094d\u0930\u0947\u0938\u093f\u0935 \u0938\u094d\u091f\u094d\u0930\u093e\u0907\u0921 " + i + " (60-80 \u092e\u0940)" } +
                        listOf(PItem("w4", "5-7 \u092e\u093f\u0928\u091f \u0906\u0930\u093e\u092e \u0915\u0930\u0915\u0947 \u0938\u094d\u091f\u093e\u0930\u094d\u091f \u0915\u0930\u0947\u0902"))
                ),
                PSection(
                    "6:00 \u0915\u0940 \u0938\u0939\u0940 \u092a\u0947\u0938\u093f\u0902\u0917",
                    emptyList(),
                    listOf(
                        "400 \u092e\u0940: 1:30",
                        "800 \u092e\u0940: 3:00",
                        "1200 \u092e\u0940: 4:30",
                        "1600 \u092e\u0940: 6:00",
                        "\u090f\u0915\u0926\u092e \u0936\u0941\u0930\u0941\u0906\u0924 \u092e\u0947\u0902 \u0938\u094d\u092a\u094d\u0930\u093f\u0902\u091f \u0928\u0939\u0940\u0902 \u0915\u0930\u0928\u093e \u0939\u0948\u0964",
                        "\u092a\u0939\u0932\u0947 400 \u092e\u0940 \u092e\u0947\u0902 \u090f\u0921\u094d\u0930\u0947\u0928\u093e\u0932\u093f\u0928 \u0915\u0947 \u0915\u093e\u0930\u0923 1:20-1:25 \u0928 \u0928\u093f\u0915\u093e\u0932 \u0926\u0947\u0902\u0964 \u0907\u0938\u0938\u0947 \u0906\u0916\u093c\u093f\u0930\u0940 400 \u092e\u0940 \u092c\u0939\u0941\u0924 \u092e\u0941\u0936\u094d\u0915\u093f\u0932 \u0939\u094b \u0938\u0915\u0924\u093e \u0939\u0948\u0964",
                        "\u0906\u0916\u093c\u093f\u0930\u0940 400 \u092e\u0940: \u0905\u0917\u0930 \u0936\u0930\u0940\u0930 \u0938\u093e\u0925 \u0926\u0947 \u0930\u0939\u093e \u0939\u0948 \u0924\u094b \u0927\u0940\u0930\u0947-\u0927\u0940\u0930\u0947 \u092a\u0947\u0938 \u092c\u0922\u093c\u093e\u090f\u0901 \u0914\u0930 \u0906\u0916\u093c\u093f\u0930\u0940 200 \u092e\u0940 \u092e\u0947\u0902 \u092a\u0942\u0930\u0940 \u092e\u0947\u0939\u0928\u0924 \u0915\u0930\u0947\u0902\u0964"
                    )
                ),
                PSection(
                    "1600 \u092e\u0940 \u091f\u0947\u0938\u094d\u091f",
                    listOf(PItem("m1", "1600 \u092e\u0940 \u092a\u0942\u0930\u093e \u0926\u094c\u0921\u093c\u0947\u0902", 0, 1500))
                )
            )
        )
    )
}

/* ------------------------------------------------------------------ */
/* Saved challenge progress                                            */
/* ------------------------------------------------------------------ */

class ChState(var start: Long, val done: MutableSet<Int>, val ticks: MutableSet<String>, var attempt: Int)

object ChStore {
    private const val PREF = "challenge_v1"

    fun load(ctx: Context): ChState {
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val done = mutableSetOf<Int>()
        val ticks = mutableSetOf<String>()
        try {
            val d = JSONArray(sp.getString("done", "[]") ?: "[]")
            for (i in 0 until d.length()) done.add(d.getInt(i))
            val t = JSONArray(sp.getString("ticks", "[]") ?: "[]")
            for (i in 0 until t.length()) ticks.add(t.getString(i))
        } catch (e: Exception) {
            // ignore broken data
        }
        return ChState(sp.getLong("start", -1L), done, ticks, sp.getInt("attempt", 1))
    }

    fun save(ctx: Context, s: ChState) {
        val d = JSONArray()
        for (x in s.done) d.put(x)
        val t = JSONArray()
        for (x in s.ticks) t.put(x)
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putLong("start", s.start)
            .putString("done", d.toString())
            .putString("ticks", t.toString())
            .putInt("attempt", s.attempt)
            .apply()
    }
}

class Status(val kind: Int, val day: Int)

object Challenge {
    const val NOT_STARTED = 0
    const val ACTIVE = 1
    const val COMPLETED = 2
    const val RESET = 3

    fun today(): Long = LocalDate.now().toEpochDay()

    fun epochDayOf(ts: Long): Long =
        Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    fun itemDone(item: PItem, dayNo: Int, s: ChState, runs: List<Run>): Boolean {
        if (!item.auto) return s.ticks.contains("d" + dayNo + "_" + item.key)
        val date = s.start + (dayNo - 1)
        for (r in runs) {
            if (epochDayOf(r.ts) != date) continue
            if (item.minSec > 0 && r.sec >= item.minSec) return true
            if (item.minMeters > 0 && r.meters >= item.minMeters) return true
        }
        return false
    }

    fun dayComplete(plan: DayPlan, s: ChState, runs: List<Run>): Boolean {
        for (sec in plan.sections) {
            for (item in sec.items) {
                if (!itemDone(item, plan.n, s, runs)) return false
            }
        }
        return true
    }

    // marks finished days, and applies the strict rule: a missed date sends the challenge back to day 1
    fun sync(ctx: Context, s: ChState, runs: List<Run>): Status {
        if (s.start < 0) return Status(NOT_STARTED, 0)
        val today = today()
        var changed = false
        for (plan in Plan.days) {
            val date = s.start + (plan.n - 1)
            if (date <= today && !s.done.contains(plan.n) && dayComplete(plan, s, runs)) {
                s.done.add(plan.n)
                changed = true
            }
        }
        for (plan in Plan.days) {
            val date = s.start + (plan.n - 1)
            if (date < today && !s.done.contains(plan.n)) {
                s.start = -1L
                s.done.clear()
                s.ticks.clear()
                s.attempt += 1
                ChStore.save(ctx, s)
                return Status(RESET, plan.n)
            }
        }
        if (changed) ChStore.save(ctx, s)
        if (s.done.size >= Plan.days.size) return Status(COMPLETED, Plan.days.size)
        val cur = (today - s.start).toInt() + 1
        return Status(ACTIVE, minOf(cur, Plan.days.size))
    }
}

/* ------------------------------------------------------------------ */
/* Home screen                                                         */
/* ------------------------------------------------------------------ */

class HomeActivity : Activity() {
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = C_GROUND
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(C_GROUND)
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(px(this, 20), px(this, 12), px(this, 20), px(this, 40))
        scroll.addView(root, ViewGroup.LayoutParams(M, W))
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        val runs = Store.load(this)
        val s = ChStore.load(this)
        val st = Challenge.sync(this, s, runs)
        if (st.kind == Challenge.RESET) {
            AlertDialog.Builder(this)
                .setTitle("\u091a\u0948\u0932\u0947\u0902\u091c \u0926\u094b\u092c\u093e\u0930\u093e \u0936\u0941\u0930\u0942")
                .setMessage("\u0926\u093f\u0928 " + st.day + " \u0915\u093e \u0915\u093e\u092e \u0909\u0938\u0940 \u0924\u093e\u0930\u0940\u0916\u093c \u0915\u094b \u092a\u0942\u0930\u093e \u0928\u0939\u0940\u0902 \u0939\u0941\u0906, \u0907\u0938\u0932\u093f\u090f \u091a\u0948\u0932\u0947\u0902\u091c \u0905\u092c \u0926\u093f\u0928 1 \u0938\u0947 \u0926\u094b\u092c\u093e\u0930\u093e \u0936\u0941\u0930\u0942 \u0939\u094b\u0917\u093e\u0964")
                .setPositiveButton("\u0920\u0940\u0915 \u0939\u0948", null)
                .show()
        }
        val kind = if (st.kind == Challenge.RESET) Challenge.NOT_STARTED else st.kind

        root.removeAllViews()

        // header with logo and name
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val logo = ImageView(this)
        logo.setImageResource(R.drawable.logo_foreground)
        head.addView(logo, LinearLayout.LayoutParams(px(this, 64), px(this, 64)))
        head.addView(label(this, "Raj tracker", 24f, C_INK, true))
        root.addView(head, lpar(this, M, W))

        // challenge card
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = shape(this, C_BLUE, 24)
        card.setPadding(px(this, 20), px(this, 20), px(this, 20), px(this, 20))
        card.addView(label(this, "10 \u0926\u093f\u0928 \u0915\u093e \u0930\u0928\u093f\u0902\u0917 \u091a\u0948\u0932\u0947\u0902\u091c", 14f, Color.parseColor("#C9D4F5"), true))

        val big = when (kind) {
            Challenge.ACTIVE -> "\u0926\u093f\u0928 " + st.day
            Challenge.COMPLETED -> "\u091a\u0948\u0932\u0947\u0902\u091c \u092a\u0942\u0930\u093e!"
            else -> "\u0905\u092d\u0940 \u0936\u0941\u0930\u0942 \u0928\u0939\u0940\u0902 \u0939\u0941\u0906"
        }
        card.addView(label(this, big, 34f, Color.WHITE, true), lpar(this, M, W, 8))

        if (kind == Challenge.ACTIVE) {
            val plan = Plan.days[st.day - 1]
            val sub = if (s.done.contains(st.day)) {
                if (st.day < Plan.days.size) "\u0906\u091c \u0915\u093e \u0926\u093f\u0928 \u092a\u0942\u0930\u093e \u0939\u094b \u0917\u092f\u093e\u0964 \u0905\u0917\u0932\u093e \u0926\u093f\u0928 \u0915\u0932\u0964" else "\u0906\u091c \u0915\u093e \u0926\u093f\u0928 \u092a\u0942\u0930\u093e \u0939\u094b \u0917\u092f\u093e\u0964"
            } else {
                plan.title
            }
            card.addView(label(this, sub, 16f, Color.WHITE), lpar(this, M, W, 2))
        }
        if (s.attempt > 1) {
            card.addView(label(this, "\u0915\u094b\u0936\u093f\u0936 " + s.attempt, 13f, Color.parseColor("#C9D4F5")), lpar(this, M, W, 2))
        }

        val count = label(this, s.done.size.toString() + "/" + Plan.days.size, 15f, Color.WHITE, true)
        count.gravity = Gravity.END
        card.addView(count, lpar(this, M, W, 18))
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.background = shape(this, Color.parseColor("#4A6BE8"), 6)
        val fill = View(this)
        fill.background = shape(this, Color.WHITE, 6)
        bar.addView(fill, LinearLayout.LayoutParams(0, M, s.done.size.toFloat()))
        bar.addView(View(this), LinearLayout.LayoutParams(0, M, (Plan.days.size - s.done.size).toFloat()))
        card.addView(bar, lpar(this, M, px(this, 12), 6))

        val btnText = when (kind) {
            Challenge.ACTIVE -> "\u0906\u091c \u0915\u093e \u0926\u093f\u0928 \u0916\u094b\u0932\u0947\u0902"
            else -> "\u091a\u0948\u0932\u0947\u0902\u091c \u0926\u0947\u0916\u0947\u0902"
        }
        val btn = pill(this, btnText, Color.WHITE, C_BLUE)
        btn.setOnClickListener {
            val i = Intent(this, ChallengeActivity::class.java)
            i.putExtra("day", if (kind == Challenge.ACTIVE) st.day else 0)
            startActivity(i)
        }
        card.addView(btn, lpar(this, M, px(this, 60), 18))
        root.addView(card, lpar(this, M, W, 20))

        // running track card
        val runCard = LinearLayout(this)
        runCard.orientation = LinearLayout.VERTICAL
        runCard.background = shape(this, Color.WHITE, 24, C_LINE)
        runCard.setPadding(px(this, 20), px(this, 20), px(this, 20), px(this, 20))
        runCard.addView(label(this, "\u0930\u0928\u093f\u0902\u0917 \u091f\u094d\u0930\u0948\u0915", 22f, C_INK, true))
        val sub2 = if (Tracker.state != Tracker.IDLE) {
            label(this, "\u0926\u094c\u0921\u093c \u091a\u0932 \u0930\u0939\u0940 \u0939\u0948", 15f, C_GREEN, true)
        } else {
            label(this, "GPS \u0938\u0947 \u0926\u0942\u0930\u0940, \u092a\u0947\u0938 \u0914\u0930 \u0930\u0942\u091f", 15f, C_MUTED)
        }
        runCard.addView(sub2, lpar(this, M, W, 4))
        runCard.setOnClickListener { startActivity(Intent(this, MainActivity::class.java)) }
        root.addView(runCard, lpar(this, M, W, 16))
    }
}

/* ------------------------------------------------------------------ */
/* Challenge screens: list of days, and one day's page                 */
/* ------------------------------------------------------------------ */

class ChallengeActivity : Activity() {
    private lateinit var scrollView: ScrollView
    private lateinit var body: LinearLayout
    private var openDay = 0
    private var s = ChState(-1L, mutableSetOf(), mutableSetOf(), 1)
    private var runs: List<Run> = emptyList()
    private val fmt = DateTimeFormatter.ofPattern("d MMM", Locale("hi", "IN"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = C_GROUND
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        scrollView = ScrollView(this)
        scrollView.setBackgroundColor(C_GROUND)
        body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(px(this, 20), px(this, 16), px(this, 20), px(this, 40))
        scrollView.addView(body, ViewGroup.LayoutParams(M, W))
        setContentView(scrollView)
        openDay = intent.getIntExtra("day", 0)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onBackPressed() {
        if (openDay != 0) {
            openDay = 0
            refresh()
        } else {
            super.onBackPressed()
        }
    }

    private fun refresh() {
        runs = Store.load(this)
        s = ChStore.load(this)
        val st = Challenge.sync(this, s, runs)
        if (st.kind == Challenge.RESET) {
            openDay = 0
            AlertDialog.Builder(this)
                .setTitle("\u091a\u0948\u0932\u0947\u0902\u091c \u0926\u094b\u092c\u093e\u0930\u093e \u0936\u0941\u0930\u0942")
                .setMessage("\u0926\u093f\u0928 " + st.day + " \u0915\u093e \u0915\u093e\u092e \u0909\u0938\u0940 \u0924\u093e\u0930\u0940\u0916\u093c \u0915\u094b \u092a\u0942\u0930\u093e \u0928\u0939\u0940\u0902 \u0939\u0941\u0906, \u0907\u0938\u0932\u093f\u090f \u091a\u0948\u0932\u0947\u0902\u091c \u0905\u092c \u0926\u093f\u0928 1 \u0938\u0947 \u0926\u094b\u092c\u093e\u0930\u093e \u0936\u0941\u0930\u0942 \u0939\u094b\u0917\u093e\u0964")
                .setPositiveButton("\u0920\u0940\u0915 \u0939\u0948", null)
                .show()
        }
        if (openDay == 0) showList(st) else showDay(openDay, st)
    }

    private fun dayLabel(date: Long): String = LocalDate.ofEpochDay(date).format(fmt)

    private fun showList(st: Status) {
        body.removeAllViews()
        scrollView.scrollTo(0, 0)
        val started = s.start >= 0

        body.addView(label(this, "10 \u0926\u093f\u0928 \u0915\u093e \u0930\u0928\u093f\u0902\u0917 \u091a\u0948\u0932\u0947\u0902\u091c", 26f, C_INK, true))
        if (s.attempt > 1) {
            body.addView(label(this, "\u0915\u094b\u0936\u093f\u0936 " + s.attempt, 14f, C_MUTED), lpar(this, M, W, 2))
        }
        val rule = "\u0938\u0916\u093c\u094d\u0924 \u0928\u093f\u092f\u092e: \u0939\u0930 \u0926\u093f\u0928 \u0915\u093e \u0915\u093e\u092e \u0909\u0938\u0940 \u0924\u093e\u0930\u0940\u0916\u093c \u0915\u094b \u0930\u093e\u0924 12 \u092c\u091c\u0947 \u0938\u0947 \u092a\u0939\u0932\u0947 \u092a\u0942\u0930\u093e \u0915\u0930\u0928\u093e \u0939\u0948\u0964 \u090f\u0915 \u0926\u093f\u0928 \u091b\u0942\u091f\u093e \u0924\u094b \u091a\u0948\u0932\u0947\u0902\u091c \u0926\u093f\u0928 1 \u0938\u0947 \u0926\u094b\u092c\u093e\u0930\u093e \u0936\u0941\u0930\u0942 \u0939\u094b\u0917\u093e\u0964"
        body.addView(label(this, rule, 14f, C_MUTED), lpar(this, M, W, 8))

        if (!started) {
            val b = pill(this, "\u091a\u0948\u0932\u0947\u0902\u091c \u0936\u0941\u0930\u0942 \u0915\u0930\u0947\u0902", C_BLUE, Color.WHITE)
            b.setOnClickListener { confirmStart() }
            body.addView(b, lpar(this, M, px(this, 60), 16))
        } else if (st.kind == Challenge.COMPLETED) {
            body.addView(label(this, "\u092c\u0927\u093e\u0908! \u091a\u0948\u0932\u0947\u0902\u091c \u092a\u0942\u0930\u093e \u0939\u0941\u0906\u0964", 18f, C_GREEN, true), lpar(this, M, W, 16))
        }

        body.addView(divider(this), lpar(this, M, px(this, 1), 16))
        for (plan in Plan.days) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, px(this, 14), 0, px(this, 14))

            val left = LinearLayout(this)
            left.orientation = LinearLayout.VERTICAL
            left.addView(label(this, "\u0926\u093f\u0928 " + plan.n, 13f, C_MUTED))
            left.addView(label(this, plan.title, 17f, C_INK, true))
            row.addView(left, LinearLayout.LayoutParams(0, W, 1f))

            var statusText = ""
            var statusColor = C_MUTED
            if (started) {
                val date = s.start + (plan.n - 1)
                val today = Challenge.today()
                if (s.done.contains(plan.n)) {
                    statusText = "\u092a\u0942\u0930\u093e \u2713"
                    statusColor = C_GREEN
                } else if (date == today) {
                    statusText = "\u0906\u091c"
                    statusColor = C_BLUE
                } else if (date > today) {
                    statusText = dayLabel(date)
                }
            }
            row.addView(label(this, statusText, 14f, statusColor, true))
            row.setOnClickListener {
                openDay = plan.n
                refresh()
            }
            body.addView(row, lpar(this, M, W))
            body.addView(divider(this), lpar(this, M, px(this, 1)))
        }
    }

    private fun confirmStart() {
        AlertDialog.Builder(this)
            .setTitle("\u091a\u0948\u0932\u0947\u0902\u091c \u0936\u0941\u0930\u0942 \u0915\u0930\u0947\u0902?")
            .setMessage("\u0906\u091c \u0915\u0940 \u0924\u093e\u0930\u0940\u0916\u093c \u0926\u093f\u0928 1 \u0939\u094b\u0917\u0940\u0964 \u0939\u0930 \u0926\u093f\u0928 \u0915\u093e \u0915\u093e\u092e \u0909\u0938\u0940 \u0924\u093e\u0930\u0940\u0916\u093c \u0915\u094b \u092a\u0942\u0930\u093e \u0915\u0930\u0928\u093e \u0939\u0948\u0964 \u090f\u0915 \u0926\u093f\u0928 \u091b\u0942\u091f\u093e \u0924\u094b \u091a\u0948\u0932\u0947\u0902\u091c \u0926\u093f\u0928 1 \u0938\u0947 \u0926\u094b\u092c\u093e\u0930\u093e \u0936\u0941\u0930\u0942 \u0939\u094b\u0917\u093e\u0964")
            .setPositiveButton("\u0936\u0941\u0930\u0942 \u0915\u0930\u0947\u0902") { _, _ ->
                s.start = Challenge.today()
                s.done.clear()
                s.ticks.clear()
                ChStore.save(this, s)
                refresh()
            }
            .setNegativeButton("\u0905\u092d\u0940 \u0928\u0939\u0940\u0902", null)
            .show()
    }

    private fun showDay(n: Int, st: Status) {
        body.removeAllViews()
        scrollView.scrollTo(0, 0)
        val plan = Plan.days[n - 1]
        val started = s.start >= 0
        val date = s.start + (n - 1)
        val today = Challenge.today()
        val isDone = s.done.contains(n)
        val editable = started && st.kind == Challenge.ACTIVE && date == today && !isDone

        val back = label(this, "\u2039 \u0938\u092d\u0940 \u0926\u093f\u0928", 15f, C_BLUE, true)
        back.setPadding(0, px(this, 4), px(this, 16), px(this, 12))
        back.setOnClickListener {
            openDay = 0
            refresh()
        }
        body.addView(back)
        body.addView(label(this, "\u0926\u093f\u0928 " + n, 14f, C_MUTED))
        body.addView(label(this, plan.title, 26f, C_INK, true))

        var line = ""
        var lineColor = C_MUTED
        if (!started) {
            line = "\u091a\u0948\u0932\u0947\u0902\u091c \u0936\u0941\u0930\u0942 \u0915\u0930\u0928\u0947 \u0915\u0947 \u092c\u093e\u0926 \u0939\u0940 \u0906\u092a \u091f\u093f\u0915 \u0915\u0930 \u092a\u093e\u090f\u0901\u0917\u0947\u0964"
        } else if (isDone) {
            line = "\u092f\u0939 \u0926\u093f\u0928 \u092a\u0942\u0930\u093e \u0939\u094b \u0917\u092f\u093e \u2713"
            lineColor = C_GREEN
        } else if (date == today) {
            line = "\u0906\u091c \u0915\u093e \u0926\u093f\u0928: \u0907\u0938\u0947 \u0906\u091c \u0930\u093e\u0924 12 \u092c\u091c\u0947 \u0938\u0947 \u092a\u0939\u0932\u0947 \u092a\u0942\u0930\u093e \u0915\u0940\u091c\u093f\u090f\u0964"
            lineColor = C_BLUE
        } else if (date > today) {
            line = "\u092f\u0939 \u0926\u093f\u0928 " + dayLabel(date) + " \u0915\u094b \u0916\u0941\u0932\u0947\u0917\u093e\u0964"
        }
        if (line.isNotEmpty()) body.addView(label(this, line, 14f, lineColor, true), lpar(this, M, W, 6))
        for (note in plan.notes) {
            body.addView(label(this, note, 15f, C_MUTED), lpar(this, M, W, 6))
        }

        var hasAuto = false
        for (sec in plan.sections) {
            body.addView(label(this, sec.title, 18f, C_INK, true), lpar(this, M, W, 24))
            for (note in sec.notes) {
                body.addView(label(this, "\u2022 " + note, 14f, C_MUTED), lpar(this, M, W, 4))
            }
            for (item in sec.items) {
                val cb = CheckBox(this)
                cb.text = item.text
                cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                cb.setTextColor(C_INK)
                cb.isChecked = Challenge.itemDone(item, n, s, runs)
                if (item.auto || !editable) {
                    cb.isEnabled = false
                } else {
                    cb.setOnCheckedChangeListener { _, checked -> onTick(plan, item, checked) }
                }
                body.addView(cb, lpar(this, M, W, 2))
                if (item.auto) {
                    hasAuto = true
                    val hint = if (item.minSec > 0) {
                        "\u0938\u0947\u0935 \u0915\u0940 \u0917\u0908 \u0926\u094c\u0921\u093c \u0915\u092e \u0938\u0947 \u0915\u092e " + (item.minSec / 60) + " \u092e\u093f\u0928\u091f \u0915\u0940 \u0939\u094b \u0924\u094b \u092f\u0939 \u0905\u092a\u0928\u0947 \u0906\u092a \u092a\u0942\u0930\u093e \u0939\u094b\u0917\u093e\u0964"
                    } else {
                        "\u0938\u0947\u0935 \u0915\u0940 \u0917\u0908 \u0926\u094c\u0921\u093c \u0915\u092e \u0938\u0947 \u0915\u092e " + item.minMeters + " \u092e\u0940 \u0915\u0940 \u0939\u094b \u0924\u094b \u092f\u0939 \u0905\u092a\u0928\u0947 \u0906\u092a \u092a\u0942\u0930\u093e \u0939\u094b\u0917\u093e (GPS \u092e\u0947\u0902 \u0925\u094b\u0921\u093c\u093e \u092b\u093c\u0930\u094d\u0915\u093c \u091a\u0932\u0947\u0917\u093e)\u0964"
                    }
                    body.addView(label(this, hint, 13f, C_MUTED), lpar(this, M, W, 0))
                }
            }
        }

        if (editable && hasAuto) {
            val b = pill(this, "\u0930\u0928\u093f\u0902\u0917 \u091f\u094d\u0930\u0948\u0915 \u0916\u094b\u0932\u0947\u0902", C_BLUE, Color.WHITE)
            b.setOnClickListener { startActivity(Intent(this, MainActivity::class.java)) }
            body.addView(b, lpar(this, M, px(this, 60), 28))
        }
    }

    private fun onTick(plan: DayPlan, item: PItem, checked: Boolean) {
        val key = "d" + plan.n + "_" + item.key
        if (checked) s.ticks.add(key) else s.ticks.remove(key)
        if (Challenge.dayComplete(plan, s, runs)) {
            s.done.add(plan.n)
            ChStore.save(this, s)
            val msg = if (plan.n >= Plan.days.size) "\u091a\u0948\u0932\u0947\u0902\u091c \u092a\u0942\u0930\u093e \u0939\u094b \u0917\u092f\u093e! \u092c\u0927\u093e\u0908!" else "\u0926\u093f\u0928 " + plan.n + " \u092a\u0942\u0930\u093e! \u0905\u0917\u0932\u093e \u0926\u093f\u0928 \u0915\u0932 \u0916\u0941\u0932\u0947\u0917\u093e\u0964"
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            refresh()
        } else {
            ChStore.save(this, s)
        }
    }
}
