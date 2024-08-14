package latoja.reservasz4s

import android.Manifest
import android.R.attr.button
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mazenrashed.printooth.Printooth
import com.mazenrashed.printooth.data.printable.Printable
import com.mazenrashed.printooth.data.printable.RawPrintable
import com.mazenrashed.printooth.data.printable.TextPrintable
import com.mazenrashed.printooth.data.printer.DefaultPrinter
import com.mazenrashed.printooth.ui.ScanningActivity
import com.mazenrashed.printooth.utilities.Printing
import com.mazenrashed.printooth.utilities.PrintingCallback
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import java.lang.reflect.Field
import java.time.ZoneId
import java.time.format.DateTimeFormatter


val supabase = createSupabaseClient(
    supabaseUrl = "https://idwluubxdwohroglujkb.supabase.co",
    supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imlkd2x1dWJ4ZHdvaHJvZ2x1amtiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MjM0MDkyNjYsImV4cCI6MjAzODk4NTI2Nn0.9VxfBiCzk7KTM7bKO-wwnMtm61V1IoA1j4l8spQYjDY"
) {
    install(Postgrest)
}

@Serializable
enum class COURTS {
    PADEL_1, PADEL_2, PADEL_3, PADEL_4, TENIS_1, TENIS_2;

    companion object {
        private val map = mapOf(
            0 to PADEL_1,
            1 to PADEL_2,
            2 to PADEL_3,
            3 to PADEL_4,
            4 to TENIS_1,
            5 to TENIS_2
        )

        fun fromInt(value: Int): COURTS? {
            return map[value]
        }
    }
}

@Serializable
data class Reservation (
    val name: String,
    val court: COURTS,
    val date: LocalDateTime,
    val status: Int)

data class ButtonWithPosition(
    val button: Button,
    val row: Int,
    val column: Int
)


class ReservasActivity : AppCompatActivity(), PrintingCallback {

    // ORANGE_COLOR = Color.rgb(235, 160, 10)
    // RED_COLOR = Color.rgb(235, 50, 50)
    // CYAN_COLOR = Color.rgb(75, 190, 190)
    // GREY_COLOR = Color.rgb(175, 190, 190)

    private val REQUEST_CODE_BLUETOOTH_PERMISSIONS = 1001

    private var printing : Printing? = null

    private var buttonCaller : Button? = null

    private val buttons = mutableListOf<ButtonWithPosition>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Printooth.init(this)
        setContentView(R.layout.activity_reservas)


        val selectedDate = intent.getStringExtra("SELECTED_DATE")

        val textView = findViewById<TextView>(R.id.textView0)
        textView.text = selectedDate.toString();

        val gridLayout = findViewById<GridLayout>(R.id.gridLayout)

        val numRows = 14
        var row = 0
        var column = 0

        for (i in 0 until gridLayout.childCount) {
            val view = gridLayout.getChildAt(i)

            row++

            if (row > numRows-1) {
                row = 0
                column++
            }

            if (view is Button) {

                val date = selectedDate.toString()
                val parsedDate = LocalDate.parse(date)

                val hour = view.text.toString().split("-")[0]
                val hourToParse = "$hour:00";
                val parsedHour = LocalTime.parse(hourToParse)

                val dateReserva = LocalDateTime(parsedDate, parsedHour)

                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

                buttons.add(ButtonWithPosition(view, row, column))

                if(dateReserva < now || dateReserva > now.date.plus(2, DateTimeUnit.DAY).atTime(now.time) )
                {
                    view.setBackgroundColor(Color.rgb(130,130,130))                 //GREY
                    view.tag = "finished"
                }else
                    view.tag = "free"

                val c = column

                view.setOnClickListener {

                    buttonCaller = view
                    var status = -1

                    if (view.tag == "notPaid")
                        status = 0
                    else if (view.tag == "occupied")
                        status = 1
                    else if (view.tag == "class")
                        status = 2
                    else if (view.tag == "finished")
                        status = -1

                    if(view.tag == "notPaid" || view.tag == "occupied" || view.tag == "class" ) {

                        val parts = view.text.toString().split("\n\n")

                        val name = parts[1]

                        showCancellationDialog(
                            name,
                            hour,
                            date, c, status)
                    }

                    else {

                        if (view.tag == "finished")
                            Toast.makeText(this, "Esta hora ya/aun no se puede reservar", Toast.LENGTH_SHORT).show()
                        else
                            showReservationDialog(view.text.toString(), selectedDate.toString(), c)
                    }

                }
            }
        }

        fetchReservations(selectedDate.toString())

    }



    private fun showReservationDialog(hour: String, date: String, column: Int) {

        val dialogView = layoutInflater.inflate(R.layout.dialog_reserva, null)
        val editTextName = dialogView.findViewById<EditText>(R.id.editTextName)
        val checkBox = dialogView.findViewById<CheckBox>(R.id.checkBox)

        val court = COURTS.fromInt(column).toString()
        var status = 1;

        AlertDialog.Builder(this)
            .setTitle("Reserva de $hour el $date")
            .setView(dialogView)
            .setPositiveButton("Reservar") { dialog, _ ->

                val name = editTextName.text.toString()

                if(name.equals("C")) {
                    status = 2
                    buttonCaller?.tag  = "class"
                }
                else if(!checkBox.isChecked) {
                    status = 0
                    buttonCaller?.tag  = "notPaid"
                }else {
                    buttonCaller?.tag = "occupied"
                }

                addReservations(name, column, hour.split("-")[0], date, status)

                Toast.makeText(this, "Reserva para \"$name\" realizada con éxito", Toast.LENGTH_SHORT).show()

                if(name!="C") showPrinterDialog(name, court, hour, date)

                dialog.dismiss()
            }

            .setNegativeButton("Cancelar") { dialog, _ ->

                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun showCancellationDialog(name: String, hour: String, date: String, column: Int, status : Int) {

        val dialogView = layoutInflater.inflate(R.layout.dialog_cancelacion, null)
        val cancelButton = dialogView.findViewById<Button>(R.id.button)
        val printButton = dialogView.findViewById<Button>(R.id.button2)
        val checkBox = dialogView.findViewById<CheckBox>(R.id.checkBox2)

        val court = COURTS.fromInt(column).toString()

        if (status == 2) checkBox.isEnabled = false
        else if(status != 0) checkBox.isChecked = true
        else checkBox.isChecked = false

        var dialog = AlertDialog.Builder(this)
            .setTitle("Reserva para $name de $hour el $date")
            .setView(dialogView)
            .setPositiveButton("Aceptar") { dialog, _ ->

                if(checkBox.isChecked && status == 0) {
                    updateReservations(name, column, hour.split("-")[0], date, 1)

                    Toast.makeText(this, "Reserva para \"$name\" actualizada con éxito", Toast.LENGTH_SHORT).show()
                }
                else if (!checkBox.isChecked && status !=0){
                    updateReservations(name, column, hour.split("-")[0], date, 0)

                    Toast.makeText(this, "Reserva para \"$name\" actualizada con éxito", Toast.LENGTH_SHORT).show()
                }

                dialog.dismiss()
            }
            .create()

        printButton.setOnClickListener {
            requestBluetoothPermissions()
            initPrinter()

            tryPrintText(name, court, hour, date)

            dialog.dismiss()

        }

        cancelButton.setOnClickListener {
            buttonCaller?.tag = "reset"
            removeReservations(name, column, hour.split("-")[0], date, status)
            Toast.makeText(this, "Reserva para \"$name\" cancelada con éxito", Toast.LENGTH_SHORT).show()

            dialog.dismiss()
        }

        dialog.show()
    }


    private fun fetchReservations(date: String) {

        val localDate = java.time.LocalDate.parse(date)

        val startOfDay = localDate.atStartOfDay()
        val endOfDay = localDate.plusDays(1).atStartOfDay()

        val zoneId = ZoneId.systemDefault()
        val startZonedDateTime = startOfDay.atZone(zoneId)
        val endZonedDateTime = endOfDay.atZone(zoneId)

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        val startTimestampSql = startZonedDateTime.format(formatter)
        val endTimestampSql = endZonedDateTime.format(formatter)


        CoroutineScope(Dispatchers.IO).launch {
            try {

                val reservations = supabase.from("reservas")
                    .select{
                        filter {
                            lt("date", endTimestampSql)
                            and { gte("date", startTimestampSql) }

                        }

                    }
                    .decodeList<Reservation>()

                withContext(Dispatchers.Main) {
                    ReservationsAdapter(buttons, reservations.toMutableList())
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {

                    Toast.makeText(this@ReservasActivity, "Exception occurred: ${e.message}", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }
        }
    }

    private fun addReservations(name: String, column: Int, hour: String, date: String, status : Int) {

        val hourToParse = "$hour:00";

        val parsedDate = LocalDate.parse(date)
        val parsedHour = LocalTime.parse(hourToParse)

        val dateReserva = LocalDateTime(parsedDate, parsedHour)

        val pista = COURTS.fromInt(column)

        val res = Reservation(name, pista!!, dateReserva, status)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                supabase.from("reservas").insert(res)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {

                    Toast.makeText(
                        this@ReservasActivity,
                        "Exception occurred: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            fetchReservations(date)
        }, 100)
    }

    private fun removeReservations(name: String, column: Int, hour: String, date: String, status : Int) {

        val hourToParse = "$hour:00";

        val parsedDate = LocalDate.parse(date)
        val parsedHour = LocalTime.parse(hourToParse)

        val dateReserva = LocalDateTime(parsedDate, parsedHour)

        val pista = COURTS.fromInt(column)

        val res = Reservation(name, pista!!, dateReserva, status)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                supabase.from("reservas").delete {
                    filter {
                        eq("date", res.date)
                        and { eq("court", res.court) }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {

                    Toast.makeText(
                        this@ReservasActivity,
                        "Exception occurred: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            fetchReservations(date)
        }, 100)
    }

    private fun updateReservations(name: String, column: Int, hour: String, date: String, status : Int) {

        val hourToParse = "$hour:00";

        val parsedDate = LocalDate.parse(date)
        val parsedHour = LocalTime.parse(hourToParse)

        val dateReserva = LocalDateTime(parsedDate, parsedHour)

        val pista = COURTS.fromInt(column)

        val res = Reservation(name, pista!!, dateReserva, status)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                supabase.from("reservas").update(
                    {
                        set("status", res.status)
                    })
                {
                    filter {
                        eq("date", res.date)
                        and { eq("court", res.court) }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {

                    Toast.makeText(
                        this@ReservasActivity,
                        "Exception occurred: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            fetchReservations(date)
        }, 100)
    }

/* ************************ BT-PRINTER FUNCTIONS ************************
               NOT TESTED/NOT DEBUGGED, GOD KNOWS IF THEY WORK*/

    private fun showPrinterDialog(name:String, court:String, hour:String, date:String) {

        val dialogView = layoutInflater.inflate(R.layout.dialog_print, null)

        AlertDialog.Builder(this)
            .setTitle("Reserva para $name de $hour el $date")
            .setView(dialogView)
            .setPositiveButton("Imprimir") { dialog, _ ->

                requestBluetoothPermissions()
                initPrinter()

                tryPrintText(name, court, hour, date)

                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun initPrinter(){
        printing?.printingCallback = this

        if (Printooth.hasPairedPrinter())
            printing = Printooth.printer()
    }

    private fun tryPrintText(name:String, court:String, hour:String, date:String){
        if(!Printooth.hasPairedPrinter()) {
            val intent = Intent(this, ScanningActivity::class.java)
            intent.putExtra("EXTRA_KEY", ScanningActivity.SCANNING_FOR_PRINTER)
            startActivityForResult(intent, ScanningActivity.SCANNING_FOR_PRINTER)
        }
        else{
            printText(name, court, hour, date)
        }
    }

    private fun printText(name:String, court:String, hour:String, date:String){
        val printables = ArrayList<Printable>()

        val text =
            "******** RESERVAS LA TOJA ZONA-4 ********" +
                    "\n\n" +
                    "NOMBRE: $name" +
                    "\n\n" +
                    "PISTA: $court" +
                    "\n\n" +
                    "HORA: $hour" +
                    "\n\n" +
                    "FECHA: $date" +
                    "\n\n" +
            "******************************************"

        printables.add(
            TextPrintable.Builder()
                .setText(text)
                .setCharacterCode(DefaultPrinter.Companion.CHARCODE_PC1252)
                .setEmphasizedMode(DefaultPrinter.Companion.EMPHASIZED_MODE_BOLD)
                .setNewLinesAfter(1)
                .build())

        printables.add(RawPrintable.Builder(byteArrayOf(27, 100, 4)).build())


        printing?.print(printables)

    }

    override fun connectingWithPrinter() {

        Toast.makeText(this, "Connecting to printer...", Toast.LENGTH_SHORT).show()

    }

    override fun connectionFailed(error: String) {

        Toast.makeText(this, "Failed: " + error, Toast.LENGTH_SHORT).show()
    }

    override fun disconnected() {

        Toast.makeText(this, "Disconnecting from printer...", Toast.LENGTH_SHORT).show()
    }

    override fun onError(error: String) {

        Toast.makeText(this, "Error: " + error, Toast.LENGTH_SHORT).show()
    }

    override fun onMessage(message: String) {

        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun printingOrderSentSuccessfully() {

        Toast.makeText(this, "Order sent to printer", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == ScanningActivity.SCANNING_FOR_PRINTER &&
            resultCode == Activity.RESULT_OK)
            initPrinting()
    }

    private fun initPrinting(){
        if(!Printooth.hasPairedPrinter())
            printing = Printooth.printer()
        if (printing != null)
            printing!!.printingCallback = this
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissionsToRequest = mutableListOf<String>()

            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }

            if (permissionsToRequest.isNotEmpty()) {
                requestPermissions(
                    permissionsToRequest.toTypedArray(),
                    REQUEST_CODE_BLUETOOTH_PERMISSIONS
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_BLUETOOTH_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                ///
            } else {
                Toast.makeText(this, "Permissions required for Bluetooth operations", Toast.LENGTH_LONG).show()
            }
        }
    }


}