package com.example.weatherapploc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import java.util.List;

public class ActivityDetectionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ActivityRecognitionResult.hasResult(intent)) {
            ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);

            if (result != null) {
                List<DetectedActivity> probableActivities = result.getProbableActivities();

                // Debug: Log all detected activities and confidence
                for (DetectedActivity activity : probableActivities) {
                    Log.d("ActivityDetection", "Detected: " + getActivityString(activity.getType()) +
                            " (Confidence: " + activity.getConfidence() + ")");
                }

                // Most probable activity
                DetectedActivity mostProbableActivity = result.getMostProbableActivity();
                int type = mostProbableActivity.getType();
                int confidence = mostProbableActivity.getConfidence();

                String activityName = getActivityString(type);

                // Debug: Log most probable activity
                Log.d("ActivityDetection", "Most Probable: " + activityName + " (" + confidence + "%)");

                // Send to UI via local broadcast (if confidence is reliable)
                Intent localIntent = new Intent("activity_update");
                localIntent.putExtra("activity_type", activityName);
                localIntent.putExtra("confidence", confidence);
                LocalBroadcastManager.getInstance(context).sendBroadcast(localIntent);
            }
        } else {
            Log.w("ActivityDetection", "No ActivityRecognitionResult found.");
        }
    }

    private String getActivityString(int type) {
        switch (type) {
            case DetectedActivity.IN_VEHICLE:
                return "In Vehicle";
            case DetectedActivity.ON_BICYCLE:
                return "On Bicycle";
            case DetectedActivity.ON_FOOT:
                return "On Foot";
            case DetectedActivity.RUNNING:
                return "Running";
            case DetectedActivity.STILL:
                return "Still";
            case DetectedActivity.WALKING:
                return "Walking";
            case DetectedActivity.TILTING:
                return "Tilting";
            case DetectedActivity.UNKNOWN:
            default:
                return "Unknown";
        }
    }
}
