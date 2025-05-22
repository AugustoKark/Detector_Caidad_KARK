package altermarkive.guardian.safezone

import altermarkive.guardian.R
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat

class ExceptionScheduleAdapter(context: Context) : android.widget.ArrayAdapter<ExceptionSchedule>(context, 0) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var view = convertView
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.item_exception_schedule, parent, false)
        }

        val schedule = getItem(position)

        val nameTextView = view!!.findViewById<TextView>(R.id.scheduleName)
        val detailsTextView = view.findViewById<TextView>(R.id.scheduleDetails)
        val daysTextView = view.findViewById<TextView>(R.id.scheduleDays)
        val statusView = view.findViewById<View>(R.id.statusIndicator)

        nameTextView.text = schedule!!.name
        detailsTextView.text = schedule.getTimeRangeString()
        daysTextView.text = schedule.getDaysOfWeekString()

        // Indicador de estado
        if (schedule.enabled) {
            statusView.setBackgroundResource(android.R.color.holo_green_light)

            // Verificar si el horario está activo ahora
            if (schedule.isCurrentTimeInSchedule()) {
                nameTextView.setTextColor(ContextCompat.getColor(context, android.R.color.holo_blue_dark))
            } else {
                nameTextView.setTextColor(ContextCompat.getColor(context, android.R.color.black))
            }
        } else {
            statusView.setBackgroundResource(android.R.color.darker_gray)
            nameTextView.setTextColor(ContextCompat.getColor(context, android.R.color.black))
        }

        return view
    }
}
