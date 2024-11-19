package com.specknet.pdiotapp

import android.app.DatePickerDialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.specknet.pdiotapp.model.ActivityData
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.Month
import java.time.format.DateTimeParseException
import java.util.Calendar

class HistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var dateFilterEditText: EditText
    private lateinit var clearHistoryButton: Button
    private lateinit var filterButton: Button

    private lateinit var activityAdapter: ActivityAdapter
    private var activityDataList: MutableList<ActivityData> = mutableListOf()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        // Initialize RecyclerView and Adapter
        recyclerView = findViewById(R.id.recyclerView)
        dateFilterEditText = findViewById(R.id.dateFilterEditText)
        clearHistoryButton = findViewById(R.id.clearHistoryButton)
        filterButton = findViewById(R.id.filterButton)
        dateFilterEditText = findViewById(R.id.dateFilterEditText)


        recyclerView.layoutManager = LinearLayoutManager(this)

        // Load data from the file
        activityDataList = (loadDataFromFile(this) ?: mutableListOf()).toMutableList()

        // Set up the adapter
        activityAdapter = ActivityAdapter(activityDataList)
        recyclerView.adapter = activityAdapter

        // Clear History Button
        val clearHistoryButton: Button = findViewById(R.id.clearHistoryButton)
        clearHistoryButton.setOnClickListener {
            clearActivityHistory(this)
        }

        dateFilterEditText.setOnClickListener {
            showDatePickerDialog()
        }

        filterButton.setOnClickListener {
            val selectedDate = dateFilterEditText.text.toString()
            if (selectedDate.isNotEmpty()) {
                filterActivitiesByDate(selectedDate)
            } else {
                Toast.makeText(this, "Please select a date to filter", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun filterActivitiesByDate(selectedDate: String) {
        try {
            // Parse the selected date (only the date part)
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val filterDate = LocalDate.parse(selectedDate, formatter) // No need for time part here

            // Filter the activities based on the date comparison
            val filteredList = activityDataList.filter { activity ->
                // Assuming activity.startTime is of type LocalDateTime, so we get the date part (without time)
                val activityDate = activity.startTime.toLocalDate()
                activityDate.isEqual(filterDate)
            }

            // Update the adapter with the filtered list
            activityAdapter = ActivityAdapter(filteredList)
            recyclerView.adapter = activityAdapter

            // Show feedback
            if (filteredList.isEmpty()) {
                Toast.makeText(this, "No activities found for the selected date", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Invalid date format", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            // Format the selected date and set it to the EditText
            val formattedDate = String.format("%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay)
            dateFilterEditText.setText(formattedDate)
        }, year, month, day)

        datePickerDialog.show()
    }

    private fun clearActivityHistory(context: Context) {
        try {
            // Clear the file contents
            val fileOutputStream: FileOutputStream = context.openFileOutput("activities_data.json", Context.MODE_PRIVATE)
            fileOutputStream.write("[]".toByteArray()) // Clear the file by overwriting it with an empty JSON array
            fileOutputStream.close()

            // Clear the list in memory
            activityDataList.clear()

            // Notify the adapter about the data change
            activityAdapter.notifyDataSetChanged()

            // Show feedback to the user
            Toast.makeText(this, "Activity history cleared", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to clear history", Toast.LENGTH_SHORT).show()
        }
    }

    // Save data to file
    @RequiresApi(Build.VERSION_CODES.O)
    fun saveDataToFile(context: Context, data: List<ActivityData>) {
        try {
            // First, try reading the existing data from the file if it exists
            val existingData = StringBuilder()
            try {
                val fileInputStream = context.openFileInput("activities_data.json")
                val inputStreamReader = InputStreamReader(fileInputStream)
                val bufferedReader = BufferedReader(inputStreamReader)

                var line: String?
                while (bufferedReader.readLine().also { line = it } != null) {
                    existingData.append(line)
                }
                fileInputStream.close()
            } catch (e: Exception) {
                // If the file does not exist, we just start with an empty StringBuilder
                // (this block will be reached the first time the file is written)
                e.printStackTrace()
            }

            // Parse the existing JSON data into a JSONArray if it's not empty
            val jsonArray = if (existingData.isNotEmpty()) {
                JSONArray(existingData.toString())
            } else {
                JSONArray()
            }

            val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

            // Append new data to the existing JSON array
            for (activity in data) {
                val jsonObject = JSONObject()
                jsonObject.put("activity", activity.activity)
                jsonObject.put("start_time", activity.startTime.format(formatter))
                jsonObject.put("end_time", activity.endTime.format(formatter))

                jsonArray.put(jsonObject)
            }

            // Convert the updated JSON array to string
            val jsonData = jsonArray.toString()

            // Write the updated data back to the file
            val fileOutputStream: FileOutputStream = context.openFileOutput("activities_data.json", Context.MODE_PRIVATE)
            val outputStreamWriter = OutputStreamWriter(fileOutputStream)
            outputStreamWriter.write(jsonData)
            outputStreamWriter.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Load data from file
    @RequiresApi(Build.VERSION_CODES.O)
    fun loadDataFromFile(context: Context): List<ActivityData>? {
        val activityList = mutableListOf<ActivityData>()
        try {
            val fileInputStream = context.openFileInput("activities_data.json")
            val inputStreamReader = InputStreamReader(fileInputStream)
            val bufferedReader = BufferedReader(inputStreamReader)

            val stringBuilder = StringBuilder()
            var line: String?

            while (bufferedReader.readLine().also { line = it } != null) {
                stringBuilder.append(line)
            }

            val jsonData = stringBuilder.toString()
            val jsonArray = JSONArray(jsonData)

            val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val activity = jsonObject.getString("activity")
                val startTimeString = jsonObject.getString("start_time")
                val endTimeString = jsonObject.getString("end_time")

                // Convert strings back to LocalDateTime
                val startTime = LocalDateTime.parse(startTimeString, formatter)
                val endTime = LocalDateTime.parse(endTimeString, formatter)

                activityList.add(ActivityData(activity, startTime, endTime))
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return activityList
    }

    // Generate and save fake data
    @RequiresApi(Build.VERSION_CODES.O)
    fun generateAndSaveFakeData(context: Context) {
        val fakeData = listOf(
            ActivityData("Running", LocalDateTime.of(2024, Month.NOVEMBER, 19, 7, 0, 0, 0), LocalDateTime.of(2024, Month.NOVEMBER, 19, 8, 0, 0, 0)),
            ActivityData("Cycling", LocalDateTime.of(2024, Month.NOVEMBER, 19, 9, 0, 0, 0), LocalDateTime.of(2024, Month.NOVEMBER, 19, 10, 30, 0, 0)),
            ActivityData("Swimming", LocalDateTime.of(2024, Month.NOVEMBER, 19, 11, 0, 0, 0), LocalDateTime.of(2024, Month.NOVEMBER, 19, 12, 0, 0, 0))
        )

        // Save the generated fake data to file
        Log.d("activity history", fakeData.toString())
        saveDataToFile(context, fakeData)
    }
}
