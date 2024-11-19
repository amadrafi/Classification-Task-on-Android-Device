package com.specknet.pdiotapp

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.specknet.pdiotapp.model.ActivityData
import kotlinx.android.synthetic.main.activity_item.view.*
import java.time.format.DateTimeFormatter

class ActivityAdapter(private val activityList: List<ActivityData>) : RecyclerView.Adapter<ActivityAdapter.ActivityViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.activity_item, parent, false)
        return ActivityViewHolder(view)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBindViewHolder(holder: ActivityViewHolder, position: Int) {
//        val activity = activityList[position]
//
//        // Set the activity name, start time, and end time dynamically
//        holder.itemView.activityName.text = activity.activity
//        holder.itemView.startTime.text = activity.startTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
//        holder.itemView.endTime.text = activity.endTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))

        val activity = activityList[position]

        // Bind the data to the TextViews
        holder.itemView.findViewById<TextView>(R.id.activityName).text = activity.activity
        holder.itemView.findViewById<TextView>(R.id.startTime).text = activity.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        holder.itemView.findViewById<TextView>(R.id.endTime).text = activity.endTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    override fun getItemCount(): Int {
        return activityList.size
    }

    class ActivityViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
