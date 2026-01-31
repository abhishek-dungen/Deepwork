package com.yourname.deepwork

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var startHour: EditText
    private lateinit var startMin: EditText
    private lateinit var startMeridiem: Spinner
    private lateinit var endHour: EditText
    private lateinit var endMin: EditText
    private lateinit var endMeridiem: Spinner
    private lateinit var result: TextView
    private lateinit var todayLabel: TextView
    private lateinit var todayTotal: TextView
    private lateinit var todayList: LinearLayout
    private lateinit var historyList: LinearLayout

    private val prefs by lazy { getSharedPreferences("deepwork", MODE_PRIVATE) }
    private val storageKey = "sessions"
    private val dayKeyFormat = DateTimeFormatter.ISO_LOCAL_DATE
    private val dayLabelFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

    data class Session(
        val date: String,
        val start: String,
        val end: String,
        val minutes: Int,
        val addedAt: Long
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startHour = findViewById(R.id.startHour)
        startMin = findViewById(R.id.startMin)
        startMeridiem = findViewById(R.id.startMeridiem)
        endHour = findViewById(R.id.endHour)
        endMin = findViewById(R.id.endMin)
        endMeridiem = findViewById(R.id.endMeridiem)
        result = findViewById(R.id.result)
        todayLabel = findViewById(R.id.todayLabel)
        todayTotal = findViewById(R.id.todayTotal)
        todayList = findViewById(R.id.todayList)
        historyList = findViewById(R.id.historyList)

        setupSpinners()
        normalizeInputs()

        findViewById<Button>(R.id.calcBtn).setOnClickListener {
            val diff = calculateDiff()
            result.text = if (diff == null) {
                "Difference: Please enter valid times."
            } else {
                "Difference: ${formatDuration(diff)}"
            }
        }

        findViewById<Button>(R.id.addBtn).setOnClickListener {
            val diff = calculateDiff()
            if (diff == null) {
                result.text = "Difference: Please enter valid times."
                return@setOnClickListener
            }
            val session = Session(
                date = todayKey(),
                start = formatTime(startHour.text.toString(), startMin.text.toString(), startMeridiem.selectedItem.toString()),
                end = formatTime(endHour.text.toString(), endMin.text.toString(), endMeridiem.selectedItem.toString()),
                minutes = diff,
                addedAt = System.currentTimeMillis()
            )
            val sessions = loadSessions().toMutableList()
            sessions.add(session)
            saveSessions(sessions)
            result.text = "Added: ${formatDuration(diff)}"
            render()
        }

        findViewById<Button>(R.id.clearBtn).setOnClickListener {
            startHour.text.clear()
            startMin.text.clear()
            endHour.text.clear()
            endMin.text.clear()
            startMeridiem.setSelection(0)
            endMeridiem.setSelection(0)
            result.text = "Difference: —"
        }

        findViewById<Button>(R.id.clearTodayBtn).setOnClickListener {
            val today = todayKey()
            val remaining = loadSessions().filter { it.date != today }
            saveSessions(remaining)
            render()
        }

        render()
    }

    private fun setupSpinners() {
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.meridiem,
            android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        startMeridiem.adapter = adapter
        endMeridiem.adapter = adapter
    }

    private fun normalizeInputs() {
        startHour.inputType = InputType.TYPE_CLASS_NUMBER
        startMin.inputType = InputType.TYPE_CLASS_NUMBER
        endHour.inputType = InputType.TYPE_CLASS_NUMBER
        endMin.inputType = InputType.TYPE_CLASS_NUMBER
    }

    private fun todayKey(): String = LocalDate.now().format(dayKeyFormat)

    private fun formatDateLabel(key: String): String {
        return try {
            val date = LocalDate.parse(key, dayKeyFormat)
            date.format(dayLabelFormat)
        } catch (_: Exception) {
            key
        }
    }

    private fun toMinutes(h: String, m: String, mer: String): Int? {
        val hour = h.toIntOrNull() ?: return null
        val min = m.toIntOrNull() ?: return null
        if (hour !in 1..12) return null
        if (min !in 0..59) return null
        var adjusted = hour
        if (mer == "AM") {
            if (adjusted == 12) adjusted = 0
        } else {
            if (adjusted != 12) adjusted += 12
        }
        return adjusted * 60 + min
    }

    private fun calculateDiff(): Int? {
        val start = toMinutes(startHour.text.toString(), startMin.text.toString(), startMeridiem.selectedItem.toString())
        val end = toMinutes(endHour.text.toString(), endMin.text.toString(), endMeridiem.selectedItem.toString())
        if (start == null || end == null) return null
        var diff = end - start
        if (diff < 0) diff += 24 * 60
        return diff
    }

    private fun formatDuration(totalMinutes: Int): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val hLabel = if (hours == 1) "hour" else "hours"
        val mLabel = if (minutes == 1) "minute" else "minutes"
        return "$hours $hLabel $minutes $mLabel"
    }

    private fun formatTime(h: String, m: String, mer: String): String {
        val hour = h.toIntOrNull() ?: 0
        val min = m.toIntOrNull() ?: 0
        return String.format(Locale.getDefault(), "%d:%02d %s", hour, min, mer)
    }

    private fun loadSessions(): List<Session> {
        val raw = prefs.getString(storageKey, "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            val list = ArrayList<Session>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    Session(
                        date = obj.getString("date"),
                        start = obj.getString("start"),
                        end = obj.getString("end"),
                        minutes = obj.getInt("minutes"),
                        addedAt = obj.optLong("addedAt", 0)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveSessions(sessions: List<Session>) {
        val array = JSONArray()
        sessions.forEach {
            val obj = JSONObject()
            obj.put("date", it.date)
            obj.put("start", it.start)
            obj.put("end", it.end)
            obj.put("minutes", it.minutes)
            obj.put("addedAt", it.addedAt)
            array.put(obj)
        }
        prefs.edit().putString(storageKey, array.toString()).apply()
    }

    private fun render() {
        val sessions = loadSessions()
        val today = todayKey()

        todayLabel.text = "Today: ${formatDateLabel(today)}"

        val todaySessions = sessions.filter { it.date == today }
        val todayMinutes = todaySessions.sumOf { it.minutes }
        todayTotal.text = "Total today: ${formatDuration(todayMinutes)}"

        todayList.removeAllViews()
        if (todaySessions.isEmpty()) {
            todayList.addView(emptyRow("No sessions added yet."))
        } else {
            todayList.addView(listHeader("Session", "Duration", "#"))
            todaySessions.forEachIndexed { idx, session ->
                todayList.addView(listRow("${session.start} - ${session.end}", formatDuration(session.minutes), "${idx + 1}"))
            }
        }

        val totalsByDate = sessions.groupBy { it.date }.mapValues { entry ->
            entry.value.sumOf { it.minutes }
        }

        val sortedDates = totalsByDate.keys.sortedDescending()
        historyList.removeAllViews()
        if (sortedDates.isEmpty()) {
            historyList.addView(emptyRow("No history yet."))
        } else {
            historyList.addView(listHeader("Date", "Total", "Sessions"))
            sortedDates.forEach { dateKey ->
                val count = sessions.count { it.date == dateKey }
                historyList.addView(
                    listRow(
                        formatDateLabel(dateKey),
                        formatDuration(totalsByDate[dateKey] ?: 0),
                        count.toString()
                    )
                )
            }
        }
    }

    private fun listHeader(c1: String, c2: String, c3: String): LinearLayout {
        return listRow(c1, c2, c3, isHeader = true)
    }

    private fun listRow(c1: String, c2: String, c3: String, isHeader: Boolean = false): LinearLayout {
        val row = LinearLayout(this)
        row.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, 8, 0, 8)

        val weight = 1f
        row.addView(makeCell(c1, weight, isHeader))
        row.addView(makeCell(c2, weight, isHeader))
        row.addView(makeCell(c3, weight, isHeader))
        return row
    }

    private fun emptyRow(text: String): LinearLayout {
        val row = LinearLayout(this)
        row.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(getColor(R.color.muted))
        tv.textSize = 13f
        row.addView(tv)
        return row
    }

    private fun makeCell(text: String, weight: Float, isHeader: Boolean): TextView {
        val tv = TextView(this)
        val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.weight = weight
        tv.layoutParams = params
        tv.text = text
        tv.gravity = Gravity.START
        tv.textSize = 13f
        tv.setTextColor(getColor(R.color.ink))
        if (isHeader) {
            tv.setTypeface(tv.typeface, Typeface.BOLD)
        }
        return tv
    }
}
