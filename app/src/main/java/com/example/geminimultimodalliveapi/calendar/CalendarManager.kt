package com.example.geminimultimodalliveapi.calendar

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.example.geminimultimodalliveapi.R
import com.example.geminimultimodalliveapi.utils.CalendarEvent
import com.example.geminimultimodalliveapi.utils.GoogleCalendarServiceHelper
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

class CalendarManager(
    private val activity: FragmentActivity,
    private val parentView: View,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun launchGoogleSignIn(intent: Intent)
    }
    // Coroutine Scope for background calendar queries
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Calendar UI elements
    private val calendarCard: View = parentView.findViewById(R.id.calendarCard)
    private val calendarGridView: GridView = parentView.findViewById(R.id.calendarGridView)
    private val txtMonthYear: TextView = parentView.findViewById(R.id.txtMonthYear)
    private val btnPrevMonth: ImageButton = parentView.findViewById(R.id.btnPrevMonth)
    private val btnNextMonth: ImageButton = parentView.findViewById(R.id.btnNextMonth)
    private val btnAddEvent: View = parentView.findViewById(R.id.btnAddEvent)
    private val selectedDateText: TextView = parentView.findViewById(R.id.selectedDateText)
    private val eventsListContainer: LinearLayout = parentView.findViewById(R.id.eventsListContainer)
    private val calendarLoginLayout: View = parentView.findViewById(R.id.calendarLoginLayout)
    private val btnCalendarSignIn: Button = parentView.findViewById(R.id.btnCalendarSignIn)
    private val customCalendarLayout: View = parentView.findViewById(R.id.customCalendarLayout)

    // Data structures
    data class CalendarDay(
        val dayNumber: Int,
        val isSelected: Boolean,
        val isToday: Boolean,
        val year: Int,
        val month: Int,
        var hasUserEvent: Boolean = false,
        var hasHoliday: Boolean = false
    )

    private val calendarDays = ArrayList<CalendarDay>()
    private val calendarAdapter = CalendarAdapter()
    
    private var calendarServiceHelper: GoogleCalendarServiceHelper? = null

    private var selectedYear = Calendar.getInstance().get(Calendar.YEAR)
    private var selectedMonth = Calendar.getInstance().get(Calendar.MONTH)
    private var selectedDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

    private var viewedYear = Calendar.getInstance().get(Calendar.YEAR)
    private var viewedMonth = Calendar.getInstance().get(Calendar.MONTH)

    // Broadcast receiver for calendar changes (e.g. from background assistant widget)
    private val calendarUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadEventsForSelectedDate()
            rebuildCalendarGrid()
        }
    }

    init {
        calendarGridView.adapter = calendarAdapter
        calendarGridView.setOnItemClickListener { _, _, position, _ ->
            val day = calendarDays[position]
            if (day.dayNumber > 0) {
                selectedDay = day.dayNumber
                selectedMonth = day.month
                selectedYear = day.year
                
                for (i in calendarDays.indices) {
                    val d = calendarDays[i]
                    val nowSelected = (d.dayNumber == selectedDay && d.month == selectedMonth && d.year == selectedYear)
                    if (d.isSelected != nowSelected) {
                        calendarDays[i] = d.copy(isSelected = nowSelected)
                    }
                }
                calendarAdapter.notifyDataSetChanged()
                loadEventsForDate(selectedYear, selectedMonth, selectedDay)
            }
        }

        btnPrevMonth.setOnClickListener {
            changeMonth(-1)
        }
        btnNextMonth.setOnClickListener {
            changeMonth(1)
        }
        rebuildCalendarGrid()

        btnAddEvent.setOnClickListener {
            if (calendarServiceHelper == null) {
                Toast.makeText(activity, "กรุณาเชื่อมต่อ Google ก่อนใช้งานปฏิทิน", Toast.LENGTH_SHORT).show()
            } else {
                showAddEventDialog()
            }
        }

        btnCalendarSignIn.setOnClickListener {
            val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(
                    com.google.android.gms.common.api.Scope(com.google.api.services.drive.DriveScopes.DRIVE_FILE),
                    com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/calendar"),
                    com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/calendar.events")
                )
                .build()
            val googleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(activity, gso)
            callbacks.launchGoogleSignIn(googleSignInClient.signInIntent)
        }
    }

    fun onSignedIn(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            activity,
            listOf(DriveScopes.DRIVE_FILE, "https://www.googleapis.com/auth/calendar", "https://www.googleapis.com/auth/calendar.events")
        ).apply {
            selectedAccount = account.account
        }
        val calendarService = com.google.api.services.calendar.Calendar.Builder(
            com.google.api.client.http.javanet.NetHttpTransport(),
            com.google.api.client.json.gson.GsonFactory.getDefaultInstance(),
            credential
        )
        .setApplicationName("Gemini Live Demo")
        .build()
        calendarServiceHelper = GoogleCalendarServiceHelper(calendarService)
        
        calendarLoginLayout.visibility = View.GONE
        customCalendarLayout.visibility = View.VISIBLE
        
        fetchMonthEventsAndHolidays()
        loadEventsForSelectedDate()
    }

    fun onSignedOut() {
        calendarServiceHelper = null
        calendarLoginLayout.visibility = View.VISIBLE
        customCalendarLayout.visibility = View.GONE
    }

    fun onResume() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(calendarUpdateReceiver, IntentFilter("com.example.geminimultimodalliveapi.CALENDAR_UPDATED"), Context.RECEIVER_NOT_EXPORTED)
        } else {
            activity.registerReceiver(calendarUpdateReceiver, IntentFilter("com.example.geminimultimodalliveapi.CALENDAR_UPDATED"))
        }
        loadEventsForSelectedDate()
    }

    fun onPause() {
        try {
            activity.unregisterReceiver(calendarUpdateReceiver)
        } catch (e: Exception) {
            Log.e("CalendarManager", "Failed to unregister calendar receiver", e)
        }
    }

    fun onDestroy() {
        managerScope.cancel()
    }

    fun loadEventsForSelectedDate() {
        loadEventsForDate(selectedYear, selectedMonth, selectedDay)
    }

    private fun loadEventsForDate(year: Int, month: Int, day: Int) {
        val helper = calendarServiceHelper ?: return
        val dateStr = String.format(Locale.getDefault(), "%02d/%02d/%04d", day, month + 1, year)
        selectedDateText.text = "รายการบันทึกประจำวัน ($dateStr)"
        
        val startCal = Calendar.getInstance().apply {
            set(year, month, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            set(year, month, day, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }

        val loadingTv = TextView(activity).apply {
            text = "กำลังโหลดข้อมูล..."
            setTextColor(Color.parseColor("#80FFFFFF"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }
        eventsListContainer.removeAllViews()
        eventsListContainer.addView(loadingTv)

        managerScope.launch {
            try {
                val userEvents = helper.fetchEventsForRange(startCal.timeInMillis, endCal.timeInMillis)
                val holidays = helper.fetchThaiHolidays(startCal.timeInMillis, endCal.timeInMillis)
                val combined = (userEvents + holidays).sortedBy { it.startTime }
                refreshEventsUI(combined)
            } catch (e: Exception) {
                Log.e("CalendarManager", "Error fetching events for selected date", e)
                val errorTv = TextView(activity).apply {
                    text = "ไม่สามารถโหลดข้อมูลปฏิทินได้: ${e.message}"
                    setTextColor(Color.parseColor("#FF5252"))
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setPadding(0, 24, 0, 24)
                }
                eventsListContainer.removeAllViews()
                eventsListContainer.addView(errorTv)
            }
        }
    }

    private fun deleteCalendarEvent(eventId: String) {
        val helper = calendarServiceHelper ?: return
        managerScope.launch {
            val success = helper.deleteEvent(eventId)
            if (success) {
                Toast.makeText(activity, "ลบรายการบันทึกสำเร็จ!", Toast.LENGTH_SHORT).show()
                loadEventsForDate(selectedYear, selectedMonth, selectedDay)
                rebuildCalendarGrid()
            } else {
                Toast.makeText(activity, "ไม่สามารถลบรายการบันทึกได้", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshEventsUI(events: List<CalendarEvent>) {
        activity.runOnUiThread {
            eventsListContainer.removeAllViews()
            if (events.isEmpty()) {
                val emptyTv = TextView(activity).apply {
                    text = "ไม่มีบันทึกหรือวันสำคัญในวันนี้"
                    setTextColor(Color.parseColor("#80FFFFFF"))
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setPadding(0, 24, 0, 24)
                }
                eventsListContainer.addView(emptyTv)
                return@runOnUiThread
            }

            val tf = SimpleDateFormat("HH:mm", Locale.getDefault())
            for (event in events) {
                val itemCard = com.google.android.material.card.MaterialCardView(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 8, 0, 8)
                    }
                    radius = 12f
                    setCardBackgroundColor(Color.parseColor("#1E1E1E"))
                    strokeWidth = 1
                    strokeColor = Color.parseColor("#1AFFFFFF")
                }

                val cardContentLayout = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                // Left color bar indicator for modern look
                val colorBar = View(activity).apply {
                    val barWidth = (4 * activity.resources.displayMetrics.density).toInt()
                    layoutParams = LinearLayout.LayoutParams(
                        barWidth,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(Color.parseColor(if (event.isHoliday) "#FF5722" else "#1A73E8"))
                }

                val layout = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                    setPadding(16, 12, 16, 12)
                }

                val headerLayout = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val titleTv = TextView(activity).apply {
                    text = event.title
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    paintFlags = paintFlags or Paint.FAKE_BOLD_TEXT_FLAG
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }

                val timeStr = if (event.startTime == event.endTime) "ทั้งวัน" else {
                    val startStr = tf.format(Date(event.startTime))
                    val endStr = tf.format(Date(event.endTime))
                    "$startStr - $endStr"
                }
                val timeTv = TextView(activity).apply {
                    text = timeStr
                    setTextColor(Color.parseColor("#B3FFFFFF"))
                    textSize = 12f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                headerLayout.addView(titleTv)
                headerLayout.addView(timeTv)

                if (!event.isHoliday) {
                    val editBtn = ImageButton(activity).apply {
                        setImageResource(com.example.geminimultimodalliveapi.R.drawable.ic_edit_24)
                        setBackgroundColor(Color.TRANSPARENT)
                        setColorFilter(Color.parseColor("#80FFFFFF"))
                        layoutParams = LinearLayout.LayoutParams(
                            (32 * activity.resources.displayMetrics.density).toInt(),
                            (32 * activity.resources.displayMetrics.density).toInt()
                        ).apply {
                            setMargins(8, 0, 0, 0)
                        }
                        setOnClickListener {
                            showAddEventDialog(event)
                        }
                    }
                    headerLayout.addView(editBtn)

                    val deleteBtn = ImageButton(activity).apply {
                        setImageResource(com.example.geminimultimodalliveapi.R.drawable.ic_delete_24)
                        setBackgroundColor(Color.TRANSPARENT)
                        setColorFilter(Color.parseColor("#FF5252"))
                        layoutParams = LinearLayout.LayoutParams(
                            (32 * activity.resources.displayMetrics.density).toInt(),
                            (32 * activity.resources.displayMetrics.density).toInt()
                        ).apply {
                            setMargins(8, 0, 0, 0)
                        }
                        setOnClickListener {
                            AlertDialog.Builder(activity)
                                .setTitle("ยืนยันการลบ")
                                .setMessage("คุณต้องการลบรายการบันทึกนี้ใช่หรือไม่?")
                                .setPositiveButton("ลบ") { _, _ ->
                                    deleteCalendarEvent(event.id)
                                }
                                .setNegativeButton("ยกเลิก", null)
                                .show()
                        }
                    }
                    headerLayout.addView(deleteBtn)
                }

                layout.addView(headerLayout)

                if (event.isHoliday) {
                    val badgeTv = TextView(activity).apply {
                        text = "วันหยุดราชการ"
                        setTextColor(Color.parseColor("#FF5722"))
                        textSize = 10f
                        paintFlags = paintFlags or Paint.FAKE_BOLD_TEXT_FLAG
                        setPadding(0, 4, 0, 0)
                    }
                    layout.addView(badgeTv)
                }

                if (!event.description.isNullOrEmpty()) {
                    val descTv = TextView(activity).apply {
                        text = event.description
                        setTextColor(Color.parseColor("#80FFFFFF"))
                        textSize = 12f
                        setPadding(0, 6, 0, 0)
                    }
                    layout.addView(descTv)
                }

                cardContentLayout.addView(colorBar)
                cardContentLayout.addView(layout)
                itemCard.addView(cardContentLayout)
                eventsListContainer.addView(itemCard)
            }
        }
    }

    private fun showAddEventDialog(existingEvent: CalendarEvent? = null) {
        try {
            val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_add_event, null)
            val titleInput = dialogView.findViewById<EditText>(R.id.eventTitleInput)
            val descInput = dialogView.findViewById<EditText>(R.id.eventDescInput)
            val btnStartTime = dialogView.findViewById<Button>(R.id.btnStartTime)
            val btnEndTime = dialogView.findViewById<Button>(R.id.btnEndTime)
            val reminderSpinner = dialogView.findViewById<Spinner>(R.id.reminderSpinner)
            val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelEvent)
            val btnSave = dialogView.findViewById<Button>(R.id.btnSaveEvent)

            val reminderOptions = arrayOf(
                "ไม่มีการแจ้งเตือน",
                "เมื่อถึงเวลา",
                "10 นาทีล่วงหน้า",
                "30 นาทีล่วงหน้า",
                "1 ชั่วโมงล่วงหน้า",
                "1 วันล่วงหน้า"
            )
            val reminderMinutesMap = arrayOf(-1, 0, 10, 30, 60, 1440)
            
            val adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, reminderOptions)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            reminderSpinner.adapter = adapter
            reminderSpinner.setSelection(2) // Default to 10 mins before

            val startCal = Calendar.getInstance().apply {
                if (existingEvent != null) {
                    timeInMillis = existingEvent.startTime
                } else {
                    set(selectedYear, selectedMonth, selectedDay)
                }
            }
            val endCal = Calendar.getInstance().apply {
                if (existingEvent != null) {
                    timeInMillis = existingEvent.endTime
                } else {
                    set(selectedYear, selectedMonth, selectedDay)
                    add(Calendar.HOUR_OF_DAY, 1)
                }
            }

            if (existingEvent != null) {
                titleInput.setText(existingEvent.title)
                descInput.setText(existingEvent.description ?: "")
                btnSave.text = "แก้ไข"
            }

            val dateTimeFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("th", "TH"))
            
            btnStartTime.text = dateTimeFormat.format(startCal.time)
            btnEndTime.text = dateTimeFormat.format(endCal.time)

            btnStartTime.setOnClickListener {
                DatePickerDialog(activity, { _, y, m, d ->
                    startCal.set(Calendar.YEAR, y)
                    startCal.set(Calendar.MONTH, m)
                    startCal.set(Calendar.DAY_OF_MONTH, d)
                    
                    TimePickerDialog(activity, { _, h, min ->
                        startCal.set(Calendar.HOUR_OF_DAY, h)
                        startCal.set(Calendar.MINUTE, min)
                        btnStartTime.text = dateTimeFormat.format(startCal.time)
                        
                        if (endCal.before(startCal)) {
                            endCal.time = startCal.time
                            endCal.add(Calendar.HOUR_OF_DAY, 1)
                            btnEndTime.text = dateTimeFormat.format(endCal.time)
                        }
                    }, startCal.get(Calendar.HOUR_OF_DAY), startCal.get(Calendar.MINUTE), true).show()
                }, startCal.get(Calendar.YEAR), startCal.get(Calendar.MONTH), startCal.get(Calendar.DAY_OF_MONTH)).show()
            }

            btnEndTime.setOnClickListener {
                DatePickerDialog(activity, { _, y, m, d ->
                    endCal.set(Calendar.YEAR, y)
                    endCal.set(Calendar.MONTH, m)
                    endCal.set(Calendar.DAY_OF_MONTH, d)
                    
                    TimePickerDialog(activity, { _, h, min ->
                        endCal.set(Calendar.HOUR_OF_DAY, h)
                        endCal.set(Calendar.MINUTE, min)
                        btnEndTime.text = dateTimeFormat.format(endCal.time)
                    }, endCal.get(Calendar.HOUR_OF_DAY), endCal.get(Calendar.MINUTE), true).show()
                }, endCal.get(Calendar.YEAR), endCal.get(Calendar.MONTH), endCal.get(Calendar.DAY_OF_MONTH)).show()
            }

            val dialog = AlertDialog.Builder(activity)
                .setView(dialogView)
                .create()
                
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            btnCancel.setOnClickListener {
                dialog.dismiss()
            }

            btnSave.setOnClickListener {
                val title = titleInput.text.toString().trim()
                val desc = descInput.text.toString().trim()
                if (title.isEmpty()) {
                    Toast.makeText(activity, "กรุณากรอกหัวข้อหลัก", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val reminderIdx = reminderSpinner.selectedItemPosition
                val reminderMinutes = if (reminderIdx in reminderMinutesMap.indices) reminderMinutesMap[reminderIdx] else -1

                btnSave.isEnabled = false
                btnSave.text = "กำลังบันทึก..."

                managerScope.launch {
                    val success = if (existingEvent != null) {
                        calendarServiceHelper?.updateEvent(
                            existingEvent.id,
                            title,
                            startCal.timeInMillis,
                            endCal.timeInMillis,
                            desc,
                            reminderMinutes
                        ) ?: false
                    } else {
                        calendarServiceHelper?.insertEvent(
                            title,
                            startCal.timeInMillis,
                            endCal.timeInMillis,
                            desc,
                            reminderMinutes
                        ) ?: false
                    }

                    if (success) {
                        val msg = if (existingEvent != null) "แก้ไขใน Google Calendar สำเร็จ!" else "บันทึกใน Google Calendar สำเร็จ!"
                        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                        loadEventsForDate(selectedYear, selectedMonth, selectedDay)
                        rebuildCalendarGrid()
                        dialog.dismiss()
                    } else {
                        Toast.makeText(activity, "บันทึกข้อมูลล้มเหลว", Toast.LENGTH_SHORT).show()
                        btnSave.isEnabled = true
                        btnSave.text = if (existingEvent != null) "แก้ไข" else "บันทึก"
                    }
                }
            }

            dialog.show()
        } catch (e: Exception) {
            Log.e("AddEvent", "Failed to show add event dialog", e)
            Toast.makeText(activity, "ไม่สามารถเปิดหน้าต่างเพิ่มบันทึกได้", Toast.LENGTH_SHORT).show()
        }
    }

    private fun rebuildCalendarGrid() {
        calendarDays.clear()
        val cal = Calendar.getInstance().apply {
            set(viewedYear, viewedMonth, 1)
        }
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        
        val emptyCellsCount = firstDayOfWeek - 1
        for (i in 0 until emptyCellsCount) {
            calendarDays.add(CalendarDay(0, false, false, viewedYear, viewedMonth))
        }
        
        val todayCal = Calendar.getInstance()
        val todayYear = todayCal.get(Calendar.YEAR)
        val todayMonth = todayCal.get(Calendar.MONTH)
        val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)
        
        for (day in 1..maxDays) {
            val isSelected = (day == selectedDay && viewedMonth == selectedMonth && viewedYear == selectedYear)
            val isToday = (day == todayDay && viewedMonth == todayMonth && viewedYear == todayYear)
            calendarDays.add(CalendarDay(day, isSelected, isToday, viewedYear, viewedMonth))
        }
        
        calendarAdapter.notifyDataSetChanged()
        
        val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale("th", "TH"))
        val displayCal = Calendar.getInstance().apply {
            set(viewedYear, viewedMonth, 1)
        }
        txtMonthYear.text = monthYearFormat.format(displayCal.time)
        
        fetchMonthEventsAndHolidays()
    }

    private fun changeMonth(offset: Int) {
        val cal = Calendar.getInstance().apply {
            set(viewedYear, viewedMonth, 1)
            add(Calendar.MONTH, offset)
        }
        viewedYear = cal.get(Calendar.YEAR)
        viewedMonth = cal.get(Calendar.MONTH)
        rebuildCalendarGrid()
    }

    private fun fetchMonthEventsAndHolidays() {
        val helper = calendarServiceHelper ?: return
        
        val startCal = Calendar.getInstance().apply {
            set(viewedYear, viewedMonth, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            set(viewedYear, viewedMonth, 1, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        }
        
        val startMs = startCal.timeInMillis
        val endMs = endCal.timeInMillis
        
        managerScope.launch {
            try {
                val userEvents = helper.fetchEventsForRange(startMs, endMs)
                val holidays = helper.fetchThaiHolidays(startMs, endMs)
                
                val userEventDays = HashSet<Int>()
                val holidayDays = HashSet<Int>()
                
                val cal = Calendar.getInstance()
                
                for (event in userEvents) {
                    cal.timeInMillis = event.startTime
                    if (cal.get(Calendar.YEAR) == viewedYear && cal.get(Calendar.MONTH) == viewedMonth) {
                        userEventDays.add(cal.get(Calendar.DAY_OF_MONTH))
                    }
                }
                
                for (holiday in holidays) {
                    cal.timeInMillis = holiday.startTime
                    if (cal.get(Calendar.YEAR) == viewedYear && cal.get(Calendar.MONTH) == viewedMonth) {
                        holidayDays.add(cal.get(Calendar.DAY_OF_MONTH))
                    }
                }
                
                var updated = false
                for (i in calendarDays.indices) {
                    val day = calendarDays[i]
                    if (day.dayNumber > 0) {
                        val hasUser = userEventDays.contains(day.dayNumber)
                        val hasHol = holidayDays.contains(day.dayNumber)
                        if (day.hasUserEvent != hasUser || day.hasHoliday != hasHol) {
                            calendarDays[i] = day.copy(hasUserEvent = hasUser, hasHoliday = hasHol)
                            updated = true
                        }
                    }
                }
                
                if (updated) {
                    calendarAdapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                Log.e("CalendarManager", "Failed to fetch month events/holidays", e)
            }
        }
    }

    private inner class CalendarAdapter : BaseAdapter() {
        override fun getCount(): Int = calendarDays.size
        override fun getItem(position: Int): Any = calendarDays[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(activity)
                .inflate(R.layout.item_calendar_day, parent, false)

            val day = calendarDays[position]
            val dayBg = view.findViewById<View>(R.id.dayBg)
            val dayText = view.findViewById<TextView>(R.id.dayText)
            val dotHoliday = view.findViewById<View>(R.id.dotHoliday)
            val dotUserEvent = view.findViewById<View>(R.id.dotUserEvent)

            if (day.dayNumber <= 0) {
                dayText.text = ""
                dayBg.background = null
                dotHoliday.visibility = View.GONE
                dotUserEvent.visibility = View.GONE
            } else {
                dayText.text = day.dayNumber.toString()

                val bgColor: Int
                val textColor: Int

                when {
                    day.isToday -> {
                        bgColor = Color.parseColor("#1A73E8") // Google Blue for today
                        textColor = Color.WHITE
                    }
                    else -> {
                        bgColor = Color.TRANSPARENT
                        textColor = if (day.hasHoliday) Color.parseColor("#FF5722") else Color.WHITE
                    }
                }

                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(bgColor)
                    if (day.isSelected) {
                        setStroke(3, Color.WHITE)
                    }
                }
                dayBg.background = drawable
                dayText.setTextColor(textColor)

                dotHoliday.visibility = if (day.hasHoliday) View.VISIBLE else View.GONE
                dotUserEvent.visibility = if (day.hasUserEvent) View.VISIBLE else View.GONE
            }

            return view
        }
    }
}
