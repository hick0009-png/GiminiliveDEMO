package com.example.geminimultimodalliveapi.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.geminimultimodalliveapi.data.DatingSkill
import com.example.geminimultimodalliveapi.utils.dpToPx
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class RadialSkillPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnSkillSelectedListener {
        fun onSkillSelected(skill: DatingSkill?)
        fun onDismiss()
    }

    var listener: OnSkillSelectedListener? = null
    private var skills: List<DatingSkill> = emptyList()
    private var selectedIndex = -1

    // Dimensions
    private val outerRadius = context.dpToPx(130f)
    private val innerRadius = context.dpToPx(45f)
    private val centerCircleRadius = context.dpToPx(40f)

    // Paints
    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpToPx(1.5f)
        color = Color.argb(120, 255, 255, 255) // Luxury subtle white border
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = context.dpToPx(14f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(200, 40, 40, 40) // Dark slate center
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(80, 0, 0, 0)
    }

    private val backgroundDimPaint = Paint().apply {
        color = Color.argb(100, 0, 0, 0) // Dim background for focus
    }

    init {
        // Enable hardware acceleration for smooth rendering of translucent arcs
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setSkills(skillsList: List<DatingSkill>) {
        this.skills = skillsList.take(5) // Max 5 skills
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f

        // 1. Draw background dim
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundDimPaint)

        if (skills.isEmpty()) return

        val count = skills.size
        val sweepAngle = 360f / count

        val rectF = RectF(
            centerX - outerRadius,
            centerY - outerRadius,
            centerX + outerRadius,
            centerY + outerRadius
        )

        // Path helper for donut segments
        val path = Path()

        // 2. Draw segments
        for (i in 0 until count) {
            val startAngle = i * sweepAngle - 90f // Start from top (-90 degrees)
            
            // Determine colors for luxury glassmorphism
            val isSelected = i == selectedIndex
            if (isSelected) {
                // Glow active background
                segmentPaint.color = Color.argb(220, 156, 39, 176) // Purple theme highlight
            } else {
                // Glassmorphism translucent gray/white
                segmentPaint.color = Color.argb(150, 45, 45, 45)
            }

            path.reset()
            path.arcTo(rectF, startAngle, sweepAngle)
            
            // Create inner arc to make it a donut slice
            val innerRectF = RectF(
                centerX - innerRadius,
                centerY - innerRadius,
                centerX + innerRadius,
                centerY + innerRadius
            )
            path.arcTo(innerRectF, startAngle + sweepAngle, -sweepAngle)
            path.close()

            canvas.drawPath(path, segmentPaint)
            canvas.drawPath(path, borderPaint)

            // 3. Draw text/label in the center of the arc segment
            val middleAngle = Math.toRadians((startAngle + sweepAngle / 2f).toDouble())
            val textRadius = (outerRadius + innerRadius) / 2f
            val textX = centerX + textRadius * cos(middleAngle).toFloat()
            val textY = centerY + textRadius * sin(middleAngle).toFloat() + context.dpToPx(5f) // Vertical offset adjustment

            val displayName = skills[i].name.substringBefore(" (") // Shorten name if it contains description parentheses
            canvas.drawText(displayName, textX, textY, textPaint)
        }

        // 4. Draw Center Cancel Button
        canvas.drawCircle(centerX, centerY, centerCircleRadius, shadowPaint)
        canvas.drawCircle(centerX, centerY, centerCircleRadius - context.dpToPx(2f), centerPaint)
        canvas.drawCircle(centerX, centerY, centerCircleRadius - context.dpToPx(2f), borderPaint)
        
        // Draw Cancel/Exit text
        val cancelTextPaint = Paint(textPaint).apply {
            textSize = context.dpToPx(11f)
            color = Color.argb(200, 255, 255, 255)
        }
        canvas.drawText("ยกเลิก", centerX, centerY + context.dpToPx(4f), cancelTextPaint)
    }

    /**
     * Intercept and process drag/motion events forwarded from the parent window.
     */
    fun processTouchEvent(event: MotionEvent): Boolean {
        val centerX = width / 2f
        val centerY = height / 2f

        val dx = event.rawX - centerX
        val dy = event.rawY - centerY
        val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                if (distance < centerCircleRadius) {
                    selectedIndex = -1
                } else if (distance <= outerRadius * 1.5f) { // Allow slight overshoot drag
                    // Calculate angle starting from top (-90 degrees)
                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                    if (angle < 0) {
                        angle += 360f
                    }
                    val sweepAngle = 360f / skills.size
                    selectedIndex = (angle / sweepAngle).toInt() % skills.size
                } else {
                    selectedIndex = -1
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (selectedIndex >= 0 && selectedIndex < skills.size) {
                    listener?.onSkillSelected(skills[selectedIndex])
                } else {
                    listener?.onSkillSelected(null) // Cancel
                }
                selectedIndex = -1
                invalidate()
                return true
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Direct tap on background dim to dismiss picker
        if (event.action == MotionEvent.ACTION_DOWN) {
            val centerX = width / 2f
            val centerY = height / 2f
            val dx = event.x - centerX
            val dy = event.y - centerY
            val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            if (distance > outerRadius) {
                listener?.onDismiss()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

}
