package com.example.weatherapploc;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.view.View;
import android.widget.FrameLayout;
import android.os.Handler;
import com.google.android.gms.maps.model.Marker;

import com.example.weatherapploc.MapsActivity.ActivityMarker;


public class RouteHistoryActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private List<String> routeKeys = new ArrayList<>();
    private Map<String, List<LatLng>> routeMap = new HashMap<>();
    private FrameLayout mapContainer;
    private List<LatLng> pendingRoute = null;
    private List<String> displayNames = new ArrayList<>(); // For UI
    private List<String> actualKeys = new ArrayList<>();   // For internal lookup
    private Handler replayHandler = new Handler();
    private Marker replayMarker = null;
    private Runnable replayRunnable = null;
    private int replayIndex = 0;
    private List<LatLng> currentReplayRoute = null;
    private Button btnReplay;
    private List<ActivityMarker> pendingMarkers = null;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_history);

        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.historyMap);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        loadAllRoutes();
        ListView listView = findViewById(R.id.routeListView);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_route_name, R.id.routeNameText, displayNames);
        listView.setAdapter(adapter);

        mapContainer = findViewById(R.id.mapContainer);
        mapContainer.setVisibility(View.GONE); // Hide map initially

        // Show route on item click
        listView.setOnItemClickListener((parent, view, position, id) -> {
            String selectedKey = actualKeys.get(position);
            List<LatLng> route = routeMap.get(selectedKey);

            // Load activity markers
            String activityJson = getSharedPreferences("routes", MODE_PRIVATE)
                    .getString("activity_" + selectedKey.substring(6), null);
            List<ActivityMarker> activityMarkers = new ArrayList<>();
            if (activityJson != null) {
                Type activityType = new TypeToken<List<ActivityMarker>>() {}.getType();
                activityMarkers = new Gson().fromJson(activityJson, activityType);
            }


            mapContainer.setVisibility(View.VISIBLE);
            if (mMap != null) {
                showRouteOnMap(route, activityMarkers);
            } else {
                pendingRoute = route;
                pendingMarkers = activityMarkers;
            }
        });

// Long-press to delete route
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            String selectedKey = actualKeys.get(position);
            String displayName = displayNames.get(position);

            new android.app.AlertDialog.Builder(RouteHistoryActivity.this)
                    .setTitle("Delete Route")
                    .setMessage("Do you want to delete \"" + displayName + "\"?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        deleteRoute(selectedKey);
                        actualKeys.remove(position);
                        displayNames.remove(position);
                        ((ArrayAdapter<?>) parent.getAdapter()).notifyDataSetChanged();
                        mMap.clear();
                        mapContainer.setVisibility(View.GONE);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();

            return true;
        });

        btnReplay = findViewById(R.id.btnReplayRoute);
        btnReplay.setVisibility(View.GONE);  // Hide initially

        btnReplay.setOnClickListener(v -> startRouteReplay());


    }

    private void deleteRoute(String key) {
        SharedPreferences prefs = getSharedPreferences("routes", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.remove(key); // remove route data

        // Also remove route name
        if (key.startsWith("route_")) {
            String timestamp = key.substring(6);
            editor.remove("name_" + timestamp);
        }

        editor.apply();
    }


    private void loadAllRoutes() {
        SharedPreferences prefs = getSharedPreferences("routes", MODE_PRIVATE);
        Gson gson = new Gson();
        Type type = new TypeToken<List<LatLng>>() {}.getType();

        Map<String, ?> allEntries = prefs.getAll();
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            if (entry.getKey().startsWith("route_")) {
                String key = entry.getKey();
                String timestamp = key.substring(6);
                String json = (String) entry.getValue();
                List<LatLng> route = gson.fromJson(json, type);

                // Try getting saved name
                String displayName = prefs.getString("name_" + timestamp, "Route at " + timestamp);

                routeMap.put(key, route);
                actualKeys.add(key);
                displayNames.add(displayName);
            }
        }
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        if (pendingRoute != null) {
            showRouteOnMap(pendingRoute, pendingMarkers);
            pendingRoute = null;
            pendingMarkers = null;
        }
    }
    private void showRouteOnMap(List<LatLng> route, List<ActivityMarker> activityMarkers) {
        if (mMap != null && route != null && !route.isEmpty()) {
            mMap.clear();

            replayHandler.removeCallbacksAndMessages(null);
            if (replayMarker != null) {
                replayMarker.remove();
                replayMarker = null;
            }

            mMap.addPolyline(new PolylineOptions()
                    .addAll(route)
                    .width(10)
                    .color(Color.BLUE));

            mMap.addMarker(new MarkerOptions()
                    .position(route.get(0))
                    .title("Start"));

            mMap.addMarker(new MarkerOptions()
                    .position(route.get(route.size() - 1))
                    .title("Stop"));

            // Activity markers
            if (activityMarkers != null) {
                for (ActivityMarker marker : activityMarkers) {
                    mMap.addMarker(new MarkerOptions()
                            .position(marker.position)
                            .title("Activity: " + marker.activity)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)));
                }
            }

            com.google.android.gms.maps.model.LatLngBounds.Builder builder =
                    new com.google.android.gms.maps.model.LatLngBounds.Builder();
            for (LatLng point : route) builder.include(point);
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
        }

        btnReplay.setVisibility(View.VISIBLE);
        currentReplayRoute = route;
    }
    private void startRouteReplay() {
        if (mMap == null || currentReplayRoute == null || currentReplayRoute.size() < 2) return;

        if (replayMarker != null) {
            replayMarker.remove();
        }

        replayIndex = 0;

        replayRunnable = new Runnable() {
            @Override
            public void run() {
                if (replayIndex >= currentReplayRoute.size()) {
                    return;
                }

                LatLng point = currentReplayRoute.get(replayIndex);

                if (replayMarker == null) {
                    replayMarker = mMap.addMarker(new MarkerOptions()
                            .position(point)
                            .title("Replaying")
                            .icon(com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
                                    com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_ORANGE)));
                } else {
                    replayMarker.setPosition(point);
                }

                mMap.animateCamera(CameraUpdateFactory.newLatLng(point));

                replayIndex++;
                replayHandler.postDelayed(this, 500);  // delay in ms between points
            }
        };

        replayHandler.post(replayRunnable);
    }



}
