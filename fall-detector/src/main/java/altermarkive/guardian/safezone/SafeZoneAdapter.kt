package altermarkive.guardian.safezone
import altermarkive.guardian.R
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat

class SafeZoneAdapter(context: Context) : ArrayAdapter<SafeZone>(context, 0) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var view = convertView
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.item_safe_zone, parent, false)
        }

        val safeZone = getItem(position)

        val nameTextView = view!!.findViewById<TextView>(R.id.safeZoneName)
        val detailsTextView = view.findViewById<TextView>(R.id.safeZoneDetails)
        val statusView = view.findViewById<View>(R.id.statusIndicator)

        nameTextView.text = safeZone!!.name
        detailsTextView.text = "Radio: ${safeZone.radius} metros"

        // Indicador de estado
        if (safeZone.enabled) {
            statusView.setBackgroundResource(android.R.color.holo_green_light)
        } else {
            statusView.setBackgroundResource(android.R.color.darker_gray)
        }

        return view
    }
}

