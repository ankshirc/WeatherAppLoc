package com.example.weatherapploc;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.fragment.app.FragmentActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.os.Bundle;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationListener;

import android.location.Location;
import android.location.Address;
import android.location.Geocoder;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.DetectedActivity;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import android.content.SharedPreferences;
import android.graphics.Color;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.Polyline;
import java.lang.reflect.Type;


public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LatLng lastLocationLatLng = null;
    private float totalDistance = 0;
    private TextView distanceTextView;
    private List<LatLng> routePoints = new ArrayList<>();
    private ActivityRecognitionClient activityRecognitionClient;
    private BroadcastReceiver activityReceiver;
    private TextView activityTextView;
    private KalmanLatLong kalmanFilter;
    private String currentActivity = "unknown";
    private final List<ActivityMarker> activityMarkers = new ArrayList<>();
    private boolean isViewingHistory = false;
    private Button btnStartStop;
    private boolean isTracking = false;
    private LocationRequest locationRequest;
    private PendingIntent activityPendingIntent;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        activityTextView = findViewById(R.id.activityTypeText);
        distanceTextView = findViewById(R.id.idTVDistance);
        btnStartStop = findViewById(R.id.btnStartStop);
        // Kalman filter initialization
        kalmanFilter = new KalmanLatLong(3);

        isViewingHistory = getIntent().getBooleanExtra("loadFromHistory", false) || getIntent().getBooleanExtra("loadLastRoute", false);

        if (isViewingHistory) {
            String routeJson = getIntent().getStringExtra("routeData");
            if (routeJson != null) {
                Type type = new TypeToken<List<LatLng>>() {}.getType();
                routePoints = new Gson().fromJson(routeJson, type);
            }

            String activityJson = getIntent().getStringExtra("activityData");
            if (activityJson != null) {
                Type activityType = new TypeToken<List<ActivityMarker>>() {}.getType();
                activityMarkers.clear();
                activityMarkers.addAll(new Gson().fromJson(activityJson, activityType));
            }

            if (!routePoints.isEmpty()) {
                lastLocationLatLng = routePoints.get(routePoints.size() - 1);
            }
        }

        activityReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String activity = intent.getStringExtra("activity_type");
                int confidence = intent.getIntExtra("confidence", 0);

                if (confidence >= 50 && !activity.equals(currentActivity)) {
                    currentActivity = activity;
                    activityTextView.setText("Activity: " + activity);

                    if (lastLocationLatLng != null && mMap != null) {
                        activityMarkers.add(new ActivityMarker(lastLocationLatLng, activity));

                        mMap.addMarker(new MarkerOptions()
                                .position(lastLocationLatLng)
                                .title("Activity changed to: " + activity)
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)));
                    }
                }
                else {
                    Log.d("ActivityDetection", "Low confidence: " + confidence);
                }
            }
        };


        LocalBroadcastManager.getInstance(this).registerReceiver(
                activityReceiver, new android.content.IntentFilter("activity_update")
        );

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        activityRecognitionClient = ActivityRecognition.getClient(this);

        // Request permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ requires FOREGROUND_SERVICE_LOCATION permission
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACTIVITY_RECOGNITION,
                            Manifest.permission.FOREGROUND_SERVICE_LOCATION
                    },
                    1001);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACTIVITY_RECOGNITION
                    },
                    1001);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1002);
            }
        }


        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        Button btnViewHistory = findViewById(R.id.btnViewHistory);
        btnViewHistory.setOnClickListener(view -> {
            Intent intent = new Intent(MapsActivity.this, RouteHistoryActivity.class);
            startActivity(intent);
        });

        btnStartStop.setOnClickListener(v -> {
            if (!isTracking) {
                startTracking();
            } else {
                stopTracking();
            }
        });

    }


    private void startTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                        ActivityCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(this, "Required permissions not granted", Toast.LENGTH_SHORT).show();
            return;
        }

        locationRequest = LocationRequest.create();
        locationRequest.setInterval(5000);
        locationRequest.setFastestInterval(2000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        Intent serviceIntent = new Intent(this, LocationForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        Intent intent = new Intent(this, ActivityDetectionReceiver.class);
        activityPendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
            activityRecognitionClient.requestActivityUpdates(5000, activityPendingIntent);
        }

        isTracking = true;
        btnStartStop.setText("Stop Tracking");

        routePoints.clear();
        activityMarkers.clear();
        totalDistance = 0;
        distanceTextView.setText("0 km");

        Toast.makeText(this, "Tracking started", Toast.LENGTH_SHORT).show();
    }

    private void stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback);

        if (activityRecognitionClient != null && activityPendingIntent != null) {
            activityRecognitionClient.removeActivityUpdates(activityPendingIntent);
        }

        saveRouteToPrefs();

        isTracking = false;
        btnStartStop.setText("Start Tracking");

        Toast.makeText(this, "Tracking stopped", Toast.LENGTH_SHORT).show();
        stopService(new Intent(this, LocationForegroundService.class));
    }

    @Override
    public void onMapReady(GoogleMap map) {
        mMap = map;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setAllGesturesEnabled(true);

        if (isViewingHistory) {
            drawRoutePolyline();
            return;
        }

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(5000);
        locationRequest.setFastestInterval(2000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;

                for (Location location : locationResult.getLocations()) {
                    float accuracy = location.getAccuracy();
                    if (accuracy <= 0 || accuracy > 100) continue;

                    long time = System.currentTimeMillis();
                    kalmanFilter.process(location.getLatitude(), location.getLongitude(), accuracy, time);
                    LatLng filteredLatLng = new LatLng(kalmanFilter.getLat(), kalmanFilter.getLng());

                    lastLocationLatLng = filteredLatLng; // Always update current location

                    if(isTracking) {
                        Location filteredLoc = new Location("");
                        filteredLoc.setLatitude(filteredLatLng.latitude);
                        filteredLoc.setLongitude(filteredLatLng.longitude);

                        if (!routePoints.isEmpty()) {
                            Location lastLoc = new Location("");
                            lastLoc.setLatitude(routePoints.get(routePoints.size() - 1).latitude);
                            lastLoc.setLongitude(routePoints.get(routePoints.size() - 1).longitude);

                            float dist = lastLoc.distanceTo(filteredLoc);

                            if (activityTextView.getText().toString().contains("Still") && dist > 20) {
                                Log.d("KalmanJump", "Ignored jump of " + dist + "m while still");
                                return;
                            }

                            totalDistance += dist;

                        }

                        routePoints.add(filteredLatLng);

                        if (distanceTextView != null) {
                            String formattedKm = String.format(Locale.getDefault(), "%.2f", totalDistance / 1000.0);  // meters to km
                            distanceTextView.setText(formattedKm + " km");
                            Log.d("DistanceUpdate", "Total distance: " + totalDistance);
                        }
                        //lastLocationLatLng = filteredLatLng;

                        drawRoutePolyline();
                    }
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(filteredLatLng, 17));

                    try {
                        Geocoder geocoder = new Geocoder(MapsActivity.this, Locale.getDefault());
                        List<Address> addresses = geocoder.getFromLocation(
                                location.getLatitude(), location.getLongitude(), 1);
                        if (!addresses.isEmpty()) {
                            String fullAddress = addresses.get(0).getAddressLine(0);
                            Toast.makeText(MapsActivity.this, "You're at: " + fullAddress, Toast.LENGTH_SHORT).show();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }
    }



    private void drawRoutePolyline() {
        mMap.clear();

        // Draw all segments
        if (routePoints.size() > 1) {
            PolylineOptions polylineOptions = new PolylineOptions()
                    .addAll(routePoints)
                    .color(Color.BLUE)  // Fixed color for entire route
                    .width(10);
            mMap.addPolyline(polylineOptions);
        }


        // Draw Start Marker
        if (!routePoints.isEmpty()) {
            mMap.addMarker(new MarkerOptions()
                    .position(routePoints.get(0))
                    .title("Start")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
        }

        // Draw Stop Marker (if more than one point)
        if (routePoints.size() > 1) {
            mMap.addMarker(new MarkerOptions()
                    .position(routePoints.get(routePoints.size() - 1))
                    .title("Stop")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
        }

        // Draw "You are here" marker
        if (lastLocationLatLng != null) {
            mMap.addMarker(new MarkerOptions()
                    .position(lastLocationLatLng)
                    .title("You are here"));
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lastLocationLatLng, 17));
        }
        // Re-draw activity change markers
        if (isViewingHistory) {
            for (ActivityMarker marker : activityMarkers) {
                mMap.addMarker(new MarkerOptions()
                        .position(marker.position)
                        .title("Activity changed to: " + marker.activity)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)));
            }
        }

    }


    private void saveRouteToPrefs() {
        SharedPreferences prefs = getSharedPreferences("routes", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        long timestamp = System.currentTimeMillis();
        String routeJson = new Gson().toJson(routePoints);
        String activityJson = new Gson().toJson(activityMarkers);

        // Save route
        editor.putString("route_" + timestamp, routeJson);
        editor.putString("activity_" + timestamp, activityJson);

        // Save the latest for quick loading
        editor.putString("lastRoute", routeJson);
        editor.putString("lastRouteActivity", activityJson);

        // Optional: name for UI
        SimpleDateFormat formatter = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
        String routeName = "Route on " + formatter.format(new Date(timestamp));
        editor.putString("name_" + timestamp, routeName);

        editor.apply();
    }



    private void loadRouteFromPrefs() {
        SharedPreferences prefs = getSharedPreferences("routes", MODE_PRIVATE);

        // Load route points
        String json = prefs.getString("lastRoute", null);
        if (json != null) {
            Type type = new TypeToken<List<LatLng>>() {}.getType();
            routePoints = new Gson().fromJson(json, type);
        }

        // Load activity markers
        String activityJson = prefs.getString("lastRouteActivity", null);
        if (activityJson != null) {
            Type activityType = new TypeToken<List<ActivityMarker>>() {}.getType();
            activityMarkers.clear();
            activityMarkers.addAll(new Gson().fromJson(activityJson, activityType));
        }

        // Redraw everything
        drawRoutePolyline();
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (activityReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(activityReceiver);
        }

        kalmanFilter.reset();

        if (!routePoints.isEmpty()) {
            LatLng stopPoint = routePoints.get(routePoints.size() - 1);
            mMap.addMarker(new MarkerOptions()
                    .position(stopPoint)
                    .title("Stop")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

            saveRouteToPrefs();
        }

    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 1001) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                startActivityUpdates();
            } else {
                Toast.makeText(this, "All permissions not granted!", Toast.LENGTH_SHORT).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void startActivityUpdates() {
        Intent intent = new Intent(this, ActivityDetectionReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED) {
            activityRecognitionClient.requestActivityUpdates(5000, pendingIntent);
        }
    }
    public class ActivityMarker {
        public LatLng position;
        public String activity;

        public ActivityMarker(LatLng position, String activity) {
            this.position = position;
            this.activity = activity;
        }
    }

}





