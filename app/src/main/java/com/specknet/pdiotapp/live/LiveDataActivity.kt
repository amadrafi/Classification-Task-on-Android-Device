package com.specknet.pdiotapp.live

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.AssetManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.specknet.pdiotapp.R
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.RESpeckLiveData
import com.specknet.pdiotapp.utils.ThingyLiveData
import kotlin.collections.ArrayList
import org.tensorflow.lite.Interpreter
import com.opencsv.CSVReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel


class LiveDataActivity : AppCompatActivity() {

    // global graph variables
    lateinit var dataSet_res_accel_x: LineDataSet
    lateinit var dataSet_res_accel_y: LineDataSet
    lateinit var dataSet_res_accel_z: LineDataSet

    lateinit var dataSet_res_gyro_x: LineDataSet
    lateinit var dataSet_res_gyro_y: LineDataSet
    lateinit var dataSet_res_gyro_z: LineDataSet

    lateinit var dataSet_thingy_accel_x: LineDataSet
    lateinit var dataSet_thingy_accel_y: LineDataSet
    lateinit var dataSet_thingy_accel_z: LineDataSet

    lateinit var dataSet_thingy_gyro_x: LineDataSet
    lateinit var dataSet_thingy_gyro_y: LineDataSet
    lateinit var dataSet_thingy_gyro_z: LineDataSet

    var time = 0f
    lateinit var allRespeckData: LineData
    lateinit var allRespeckGyroData: LineData

    lateinit var allThingyData: LineData
    lateinit var allThingyGyroData: LineData

    lateinit var respeckChart: LineChart
    lateinit var respeckGyroChart: LineChart

    lateinit var thingyChart: LineChart
    lateinit var thingyGyroChart: LineChart


    val respeckLiveDataBuffer: MutableList<FloatArray> = ArrayList()
    val thingyLiveDataBuffer: MutableList<FloatArray> = ArrayList()

    val activities = mapOf(
        "Ascending Stairs" to 0,
        "Descending Stairs" to 1,
        "Lying Back" to 2,
        "Lying Left" to 3,
        "Lying Right" to 4,
        "Lying Stomach" to 5,
        "Misc Movement" to 6,
        "Normal Walking" to 7,
        "Running" to 8,
        "Shuffle Walking" to 9,
        "Sitting/ Standing" to 10,
    )

    val activitiesSocial = mapOf(
        "Breathing Normally " to 0,
        "Coughing" to 1,
        "Hyperventilation " to 2,
        "Other" to 3,
    )

    // global broadcast receiver so we can unregister it
    lateinit var respeckLiveUpdateReceiver: BroadcastReceiver
    lateinit var thingyLiveUpdateReceiver: BroadcastReceiver
    lateinit var looperRespeck: Looper
    lateinit var looperThingy: Looper
    lateinit var interpreter: Interpreter
    lateinit var interpreter_social: Interpreter

    val filterTestRespeck = IntentFilter(Constants.ACTION_RESPECK_LIVE_BROADCAST)
    val filterTestThingy = IntentFilter(Constants.ACTION_THINGY_BROADCAST)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_data)

        setupCharts()

        val modelSocial = loadModelFile(assets, "model7910.tflite")
        interpreter_social = Interpreter(modelSocial)

        val model = loadModelFile(assets, "model.tflite")
        interpreter = Interpreter(model)

        Log.d("Models status", "Success")

        // set up the broadcast receiver
        respeckLiveUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                Log.i("thread", "I am running on thread = " + Thread.currentThread().name)

                val action = intent.action

                if (action == Constants.ACTION_RESPECK_LIVE_BROADCAST) {

                    val liveData =
                        intent.getSerializableExtra(Constants.RESPECK_LIVE_DATA) as RESpeckLiveData
                    Log.d("Live", "onReceive: liveData = " + liveData)

                    // get all relevant intent contents
                    val x = liveData.accelX
                    val y = liveData.accelY
                    val z = liveData.accelZ

                    val gX = liveData.gyro.x
                    val gY = liveData.gyro.y
                    val gZ = liveData.gyro.z

                    time += 1
                    updateGraph("respeck", x, y, z, gX, gY, gZ)

                    val sample = floatArrayOf(x, y, z, gX, gY, gZ)
                    addLiveData(sample, isRespeck = true)

                    if (respeckLiveDataBuffer.size == 50) {
                        predictActivity(respeckLiveDataBuffer)
                    }
                }
            }
        }

        // register receiver on another thread
        val handlerThreadRespeck = HandlerThread("bgThreadRespeckLive")
        handlerThreadRespeck.start()
        looperRespeck = handlerThreadRespeck.looper
        val handlerRespeck = Handler(looperRespeck)
        this.registerReceiver(respeckLiveUpdateReceiver, filterTestRespeck, null, handlerRespeck)

        // set up the broadcast receiver
        thingyLiveUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                Log.i("thread", "I am running on thread = " + Thread.currentThread().name)

                val action = intent.action

                if (action == Constants.ACTION_THINGY_BROADCAST) {

                    val liveData =
                        intent.getSerializableExtra(Constants.THINGY_LIVE_DATA) as ThingyLiveData
                    Log.d("Live", "onReceive: liveData = " + liveData)

                    // get all relevant intent contents
                    val x = liveData.accelX
                    val y = liveData.accelY
                    val z = liveData.accelZ

                    val gX = liveData.gyro.x
                    val gY = liveData.gyro.y
                    val gZ = liveData.gyro.z

                    time += 1
                    updateGraph("thingy", x, y, z, gX, gY, gZ)


                    val sample = floatArrayOf(x, y, z, gX, gY, gZ)
                    addLiveData(sample, isRespeck = false)

                    if (thingyLiveDataBuffer.size == 50) {
                        predictActivity2(thingyLiveDataBuffer)
                    }
                }
            }
        }
        // register receiver on another thread
        val handlerThreadThingy = HandlerThread("bgThreadThingyLive")
        handlerThreadThingy.start()
        looperThingy = handlerThreadThingy.looper
        val handlerThingy = Handler(looperThingy)
        this.registerReceiver(thingyLiveUpdateReceiver, filterTestThingy, null, handlerThingy)
    }

    fun predictActivity(inputData: List<FloatArray>){

        if (!::interpreter.isInitialized && !::interpreter_social.isInitialized) {
            throw IllegalStateException("TensorFlow Lite interpreters are not initialized.")
        }
        // Run inference
        val result = runBatchInference(interpreter, inputData, 11)
        val resultSocial = runBatchInference(interpreter_social, inputData, 4)

        val maxIndex = result.indices.maxByOrNull { result[it] } ?: -1
        val maxIndexSocial = resultSocial.indices.maxByOrNull { result[it] } ?: -1

        //Get the corresponding activity
        val activity = if (maxIndex != -1) {
            activities.entries.firstOrNull { it.value == maxIndex }?.key ?: "Unknown activity"
        } else {
            "No activity detected"
        }

        val activitySocial = if (maxIndexSocial != -1) {
            activitiesSocial.entries.firstOrNull { it.value == maxIndex }?.key ?: "Unknown activity"
        } else {
            "No activity detected"
        }

        saveActivityPrediction(activity, activitySocial)

        runOnUiThread {
            val activityText: TextView = findViewById(R.id.inference_output_1)
            activityText.text = "Activity Respeck: $activity, $activitySocial"
        }
        Log.d("TFLite Result", "Inference result: ${result.joinToString(", ")}")

    }


    fun predictActivity2(inputData: List<FloatArray>){

        if (!::interpreter.isInitialized) {
            throw IllegalStateException("TensorFlow Lite interpreter is not initialized.")
        }
        // Run inference
        val result = runBatchInference(interpreter, inputData, 11)

        val maxIndex = result.indices.maxByOrNull { result[it] } ?: -1

        //Get the corresponding activity
        //-------------------------------------------------------------------------------------------------------------------------------
        val activity = if (maxIndex != -1) {
            activities.entries.firstOrNull { it.value == maxIndex }?.key ?: "Unknown activity"
        } else {
            "No activity detected"
        }
        runOnUiThread {
            val activityText: TextView = findViewById(R.id.inference_output_2)
            activityText.text = "Activity Thingy: $activity"
        }
        Log.d("TFLite Result", "Inference result: ${result.joinToString(", ")}")

    }

    fun saveActivityPrediction(activity: String, activitySocial: String) {
        val fileName = "activity_predictions.txt"
        val fileContents = "Respeck Activity: $activity, Social Activity: $activitySocial\n"
        try {
            openFileOutput(fileName, Context.MODE_APPEND).use {
                it.write(fileContents.toByteArray())
            }
            Log.d("SaveActivity", "Saved activity prediction: $fileContents")
        } catch (e: Exception) {
            Log.e("SaveActivity", "Failed to save activity prediction", e)
        }
    }

    fun prepareBatchInputData(inputList: List<FloatArray>): ByteBuffer {
        // Check if the list has exactly 50 samples and each sample has 6 float values
        require(inputList.size == 50 && inputList.all { it.size == 6 }) {}

        // Allocate the buffer to hold 50 samples with 6 float values each
        val byteBuffer = ByteBuffer.allocateDirect(50 * 6 * 4) // 50 samples * 6 floats * 4 bytes per float
        byteBuffer.order(ByteOrder.nativeOrder())

        // Add all the float values from the input list into the buffer
        inputList.forEach { floatArray ->
            floatArray.forEach { value ->
                byteBuffer.putFloat(value)
            }
        }

        return byteBuffer
    }

    fun runBatchInference(interpreter: Interpreter, inputList: List<FloatArray>, shape: Int): FloatArray {
        // Prepare the input data as a ByteBuffer
        val inputBuffer = prepareBatchInputData(inputList)

        val outputBuffer = Array(1) { FloatArray(shape) }

        interpreter.run(inputBuffer, outputBuffer)

        return outputBuffer[0]
    }

    private fun loadModelFile(assetManager: AssetManager, modelPath: String): MappedByteBuffer {
        val fileDescriptor = assetManager.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun addLiveData(newSample: FloatArray, isRespeck: Boolean) {

        val buffer = if (isRespeck) respeckLiveDataBuffer else thingyLiveDataBuffer

        if (buffer.size >= 50) {
            // Remove the oldest sample to maintain a size of 50
            buffer.removeAt(0)
        }
        // Add the new sample to the buffer
        buffer.add(newSample)
    }
//
//    // Function to load and preprocess CSV data from assets
//    private fun loadCSVData(assetManager: AssetManager, csvPath: String): List<FloatArray> {
//        val inputStream = assetManager.open(csvPath)
//        val reader = CSVReader(InputStreamReader(inputStream))
//        val inputList = ArrayList<FloatArray>()
//
//        // Read and ignore the header row
//        reader.readNext() // This will skip the header
//
//        reader.forEach { row ->
//            // Check if the row is not empty and has at least 7 elements (counter + 6 values)
//            if (row.isNotEmpty() && row.size >= 7) {
//                try {
//                    // Skip the first element (the counter) and map the rest to Float
//                    val floatValues = row.drop(2).dropLast(1).map {
//                        if (it.isEmpty()) {
//                            throw NumberFormatException("Empty string found")
//                        }
//                        it.toFloat()
//                    }.toFloatArray()
//                    inputList.add(floatValues)
//                } catch (e: NumberFormatException) {
//                    Log.e("CSV Loading Error", "Error parsing row: ${row.joinToString()} - ${e.message}")
//                }
//            } else {
//                Log.w("CSV Warning", "Row skipped due to invalid format: ${row.joinToString()}")
//            }
//        }
//
//        // Log the loaded data
//        Log.d("CSV Data", "Loaded CSV Data: ${inputList.joinToString("\n") { it.contentToString() }}")
//
//        val modifiedList = inputList.take(50)
//
//        val numRows = modifiedList.size
//        val numCol = modifiedList[0].size
//
//        Log.d("Shape of csv", "$numRows x $numCol")
//
//        // Reshape into 50x6 input if needed
//        return modifiedList
//    }

    fun setupCharts() {
        respeckChart = findViewById(R.id.respeck_chart)
        thingyChart = findViewById(R.id.thingy_chart)

        respeckGyroChart = findViewById(R.id.respeck_gyro_chart)
        thingyGyroChart = findViewById(R.id.thingy_gyro_chart)
//--------------------------------------------------------------------------------------------------
        // Respeck

        time = 0f
        val entries_res_accel_x = ArrayList<Entry>()
        val entries_res_accel_y = ArrayList<Entry>()
        val entries_res_accel_z = ArrayList<Entry>()

        dataSet_res_accel_x = LineDataSet(entries_res_accel_x, "Accel X")
        dataSet_res_accel_y = LineDataSet(entries_res_accel_y, "Accel Y")
        dataSet_res_accel_z = LineDataSet(entries_res_accel_z, "Accel Z")

        dataSet_res_accel_x.setDrawCircles(false)
        dataSet_res_accel_y.setDrawCircles(false)
        dataSet_res_accel_z.setDrawCircles(false)

        dataSet_res_accel_x.setColor(
            ContextCompat.getColor(
                this,
                R.color.red
            )
        )
        dataSet_res_accel_y.setColor(
            ContextCompat.getColor(
                this,
                R.color.green
            )
        )
        dataSet_res_accel_z.setColor(
            ContextCompat.getColor(
                this,
                R.color.blue
            )
        )

        val dataSetsRes = ArrayList<ILineDataSet>()
        dataSetsRes.add(dataSet_res_accel_x)
        dataSetsRes.add(dataSet_res_accel_y)
        dataSetsRes.add(dataSet_res_accel_z)

        allRespeckData = LineData(dataSetsRes)
        respeckChart.data = allRespeckData
        respeckChart.invalidate()
//--------------------------------------------------------------------------------------------------
        // Respeck GYRO

        time = 0f
        val entries_res_gyro_x = ArrayList<Entry>()
        val entries_res_gyro_y = ArrayList<Entry>()
        val entries_res_gyro_z = ArrayList<Entry>()

        dataSet_res_gyro_x = LineDataSet(entries_res_gyro_x, "Gyro X")
        dataSet_res_gyro_y = LineDataSet(entries_res_gyro_y, "Gyro Y")
        dataSet_res_gyro_z = LineDataSet(entries_res_gyro_z, "Gyro Z")

        dataSet_res_gyro_x.setDrawCircles(false)
        dataSet_res_gyro_y.setDrawCircles(false)
        dataSet_res_gyro_z.setDrawCircles(false)

        dataSet_res_gyro_x.setColor(
            ContextCompat.getColor(
                this,
                R.color.red
            )
        )
        dataSet_res_gyro_y.setColor(
            ContextCompat.getColor(
                this,
                R.color.green
            )
        )
        dataSet_res_gyro_z.setColor(
            ContextCompat.getColor(
                this,
                R.color.blue
            )
        )

        val dataSetsGyro = ArrayList<ILineDataSet>()
        dataSetsGyro.add(dataSet_res_gyro_x)
        dataSetsGyro.add(dataSet_res_gyro_y)
        dataSetsGyro.add(dataSet_res_gyro_z)

        allRespeckGyroData = LineData(dataSetsGyro)
        respeckGyroChart.data = allRespeckGyroData
        respeckGyroChart.invalidate()
//--------------------------------------------------------------------------------------------------
        // Thingy

        time = 0f
        val entries_thingy_accel_x = ArrayList<Entry>()
        val entries_thingy_accel_y = ArrayList<Entry>()
        val entries_thingy_accel_z = ArrayList<Entry>()

        dataSet_thingy_accel_x = LineDataSet(entries_thingy_accel_x, "Accel X")
        dataSet_thingy_accel_y = LineDataSet(entries_thingy_accel_y, "Accel Y")
        dataSet_thingy_accel_z = LineDataSet(entries_thingy_accel_z, "Accel Z")

        dataSet_thingy_accel_x.setDrawCircles(false)
        dataSet_thingy_accel_y.setDrawCircles(false)
        dataSet_thingy_accel_z.setDrawCircles(false)

        dataSet_thingy_accel_x.setColor(
            ContextCompat.getColor(
                this,
                R.color.red
            )
        )
        dataSet_thingy_accel_y.setColor(
            ContextCompat.getColor(
                this,
                R.color.green
            )
        )
        dataSet_thingy_accel_z.setColor(
            ContextCompat.getColor(
                this,
                R.color.blue
            )
        )

        val dataSetsThingy = ArrayList<ILineDataSet>()
        dataSetsThingy.add(dataSet_thingy_accel_x)
        dataSetsThingy.add(dataSet_thingy_accel_y)
        dataSetsThingy.add(dataSet_thingy_accel_z)

        allThingyData = LineData(dataSetsThingy)
        thingyChart.data = allThingyData
        thingyChart.invalidate()
//--------------------------------------------------------------------------------------------------
        // Thingy GYRO

        time = 0f
        val entries_thingy_gyro_x = ArrayList<Entry>()
        val entries_thingy_gyro_y = ArrayList<Entry>()
        val entries_thingy_gyro_z = ArrayList<Entry>()

        dataSet_thingy_gyro_x = LineDataSet(entries_thingy_gyro_x, "Gyro X")
        dataSet_thingy_gyro_y = LineDataSet(entries_thingy_gyro_y, "Gyro Y")
        dataSet_thingy_gyro_z = LineDataSet(entries_thingy_gyro_z, "Gyro Z")

        dataSet_thingy_gyro_x.setDrawCircles(false)
        dataSet_thingy_gyro_y.setDrawCircles(false)
        dataSet_thingy_gyro_z.setDrawCircles(false)

        dataSet_thingy_gyro_x.setColor(
            ContextCompat.getColor(
                this,
                R.color.red
            )
        )
        dataSet_thingy_gyro_y.setColor(
            ContextCompat.getColor(
                this,
                R.color.green
            )
        )
        dataSet_thingy_gyro_z.setColor(
            ContextCompat.getColor(
                this,
                R.color.blue
            )
        )

        val dataSetsGyroThingy = ArrayList<ILineDataSet>()
        dataSetsGyroThingy.add(dataSet_thingy_gyro_x)
        dataSetsGyroThingy.add(dataSet_thingy_gyro_y)
        dataSetsGyroThingy.add(dataSet_thingy_gyro_z)

        allThingyGyroData = LineData(dataSetsGyroThingy)
        thingyGyroChart.data = allThingyGyroData
        thingyGyroChart.invalidate()
//--------------------------------------------------------------------------------------------------
    }

    fun updateGraph(graph: String, x: Float, y: Float, z: Float,  xG: Float, yG: Float, zG: Float) {
        // take the first element from the queue
        // and update the graph with it
        if (graph == "respeck") {
            // update accelerometer charts
            dataSet_res_accel_x.addEntry(Entry(time, x))
            dataSet_res_accel_y.addEntry(Entry(time, y))
            dataSet_res_accel_z.addEntry(Entry(time, z))

            runOnUiThread {
//                try {
//                    respeckChart.notifyDataSetChanged()
//                    respeckChart.invalidate()
//                } catch (e: Exception) {
//                    Log.e("ChartError", "Error updating chart at respeck1: ${e.message}")
//                }
                allRespeckData.notifyDataChanged()
                respeckChart.notifyDataSetChanged()
                respeckChart.invalidate()
                respeckChart.setVisibleXRangeMaximum(150f)
                respeckChart.moveViewToX(respeckChart.lowestVisibleX + 40)
            }
            // update gyroscope charts
            dataSet_res_gyro_x.addEntry(Entry(time, xG))
            dataSet_res_gyro_y.addEntry(Entry(time, yG))
            dataSet_res_gyro_z.addEntry(Entry(time, zG))

            runOnUiThread {
                allRespeckGyroData.notifyDataChanged()
                respeckGyroChart.notifyDataSetChanged()
                respeckGyroChart.invalidate()
                respeckGyroChart.setVisibleXRangeMaximum(150f)
                respeckGyroChart.moveViewToX(respeckGyroChart.lowestVisibleX + 40)
            }
        } else if (graph == "thingy") {
            // update accelerometer charts
            dataSet_thingy_accel_x.addEntry(Entry(time, x))
            dataSet_thingy_accel_y.addEntry(Entry(time, y))
            dataSet_thingy_accel_z.addEntry(Entry(time, z))

            runOnUiThread {
                allThingyData.notifyDataChanged()
                thingyChart.notifyDataSetChanged()
                thingyChart.invalidate()
                thingyChart.setVisibleXRangeMaximum(150f)
                thingyChart.moveViewToX(thingyChart.lowestVisibleX + 40)
            }
            // update gyroscope charts
            dataSet_thingy_gyro_x.addEntry(Entry(time, xG))
            dataSet_thingy_gyro_y.addEntry(Entry(time, yG))
            dataSet_thingy_gyro_z.addEntry(Entry(time, zG))

            runOnUiThread {

                allThingyGyroData.notifyDataChanged()
                thingyGyroChart.notifyDataSetChanged()
                thingyGyroChart.invalidate()
                thingyGyroChart.setVisibleXRangeMaximum(150f)
                thingyGyroChart.moveViewToX(thingyGyroChart.lowestVisibleX + 40)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        interpreter.close()
        interpreter_social.close()
        unregisterReceiver(respeckLiveUpdateReceiver)
        unregisterReceiver(thingyLiveUpdateReceiver)
        looperRespeck.quit()
        looperThingy.quit()
    }
}
