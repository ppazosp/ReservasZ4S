package latoja.reservasz4s

import android.graphics.Color
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime


class ReservationsAdapter(private val buttons: List<ButtonWithPosition>, private val reservations: MutableList<Reservation>) : RecyclerView.Adapter<ReservationsAdapter.ReservaViewHolder>() {

    init {
        updateButtonsWithReservations()
    }

    //ORANGE_COLOR = Color.rgb(235, 160, 10)
    //RED_COLOR = Color.rgb(235, 50, 50)
    //CYAN_COLOR = Color.rgb(75, 190, 190)
    //GREEN_COLOR = Color.rgb(55, 138, 56)

    private fun updateButtonsWithReservations() {
            var iterator = reservations.iterator()

            buttons.forEach { button ->

                if( button.button.tag.equals("reset")){
                    val resetText = button.button.text.split("\n\n")[0]
                    button.button.text = resetText
                    button.button.setBackgroundColor(Color.rgb(55,138,56))
                    button.button.tag = "free"
                }

                val buttonHour = button.button.text.split("-")[0].toInt()

                while (iterator.hasNext()) {
                    val reserva = iterator.next()
                    val reservaHour = reserva.date.hour

                    if (buttonHour == reservaHour && button.column == reserva.court.ordinal) {
                        button.button.text = buildString {
                            append(button.button.text)
                            append("\n\n")
                            append(reserva.name)
                        }

                        when (reserva.status) {
                            0 -> {
                                button.button.setBackgroundColor(Color.rgb(235, 160, 10))
                                button.button.tag = "notPaid"
                            }

                            1 -> {
                                    button.button.setBackgroundColor(Color.rgb(235, 50, 50))
                                    button.button.tag = "occupied"
                            }

                            2 -> {
                                    button.button.setBackgroundColor(Color.rgb(75, 190, 190))
                                    button.button.tag = "class"
                            }

                            else -> {
                                button.button.setBackgroundColor(Color.TRANSPARENT)
                            }
                        }

                        iterator.remove()
                    }
                }

                iterator = reservations.iterator()
            }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReservaViewHolder {

        throw NotImplementedError("RecyclerView is not used for layout")
    }

    override fun onBindViewHolder(holder: ReservaViewHolder, position: Int) {
    }

    override fun getItemCount(): Int = 0

    class ReservaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
