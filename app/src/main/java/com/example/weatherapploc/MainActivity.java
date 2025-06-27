package com.example.weatherapploc;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import android.os.Looper;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.textfield.TextInputEditText;
import com.squareup.picasso.Picasso;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private RelativeLayout homeRL;
    private ProgressBar loadingPB;
    private TextView cityNameTV, temperatureTV, conditionTV,humidityTV, windTV, pressureTV;;
    private RecyclerView weatherRV;
    private TextInputEditText cityEdt;
    private ImageView backIV, iconIV, searchIV;
    private ArrayList<WeatherRVModal> weatherRVModalArrayList;
    private WeatherRVAdapter weatherRVAdapter;
    private LocationManager locationManager;
    private int PERMISSION_CODE = 1;
    private String cityName;
    private Button mapButton;
    private FusedLocationProviderClient fusedLocationProviderClient;

    private final String API_KEY = "ca442ed05e432b961b6bd2cf02a6549a";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        setContentView(R.layout.activity_main);

        homeRL = findViewById(R.id.idRLHome);
        loadingPB = findViewById(R.id.idPBLoading);
        cityNameTV = findViewById(R.id.idTVCityName);
        temperatureTV = findViewById(R.id.idTVTemperature);
        conditionTV = findViewById(R.id.idTVCondition);
//        weatherRV = findViewById(R.id.idRVWeather);
        cityEdt = findViewById(R.id.idEdtCity);
        backIV = findViewById(R.id.idIVBack);
        iconIV = findViewById(R.id.idIVIcon);
        searchIV = findViewById(R.id.idIVSearch);
        humidityTV = findViewById(R.id.idTVHumidity);
        windTV = findViewById(R.id.idTVWind);
        pressureTV = findViewById(R.id.idTVPressure);
        mapButton = findViewById(R.id.idBtnOpenMap);


        weatherRVModalArrayList = new ArrayList<>();
        weatherRVAdapter = new WeatherRVAdapter(this, weatherRVModalArrayList);
        //weatherRV.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        //weatherRV.setAdapter(weatherRVAdapter);
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    PERMISSION_CODE);
        } else {
            fetchLocationAndWeather();
        }


        searchIV.setOnClickListener(v -> {
            String city = cityEdt.getText().toString().trim();
            if(city.isEmpty()){
                Toast.makeText(MainActivity.this, "Please enter city name", Toast.LENGTH_SHORT).show();
            } else {
                cityNameTV.setText(city);
                getWeatherInfo(city);
            }
        });

        mapButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, MapsActivity.class);
                startActivity(intent);
            }
        });

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == PERMISSION_CODE) {
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                fetchLocationAndWeather();
            } else {
                Toast.makeText(this, "Please provide the permissions", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void fetchLocationAndWeather() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    PERMISSION_CODE);
            return;
        }

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(1000);
        locationRequest.setFastestInterval(500);
        locationRequest.setNumUpdates(1);

        fusedLocationProviderClient.requestLocationUpdates(locationRequest, new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    double longitude = location.getLongitude();
                    double latitude = location.getLatitude();
                    cityName = getCityName(longitude, latitude);
                    getWeatherInfo(cityName);
                } else {
                    Toast.makeText(MainActivity.this, "Using default city: Duliajan", Toast.LENGTH_SHORT).show();
                    getWeatherInfo("Duliajan,India");
                }
            }
        }, Looper.getMainLooper());

    }




    private String getCityName(double longitude, double latitude) {
        String cityName = "Not found";
        Geocoder gcd = new Geocoder(getBaseContext(), Locale.getDefault());
        try {
            List<Address> addresses = gcd.getFromLocation(latitude, longitude, 10);
            for (Address adr : addresses) {
                if (adr != null) {
                    String city = adr.getLocality();
                    if (city != null && !city.equals("")) {
                        cityName = city;
                        break;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return cityName;
    }

    private void getWeatherInfo(String cityName) {
        String url = "https://api.openweathermap.org/data/2.5/weather?q=" + cityName + "&appid=" + API_KEY + "&units=metric";
        cityNameTV.setText(cityName);
        loadingPB.setVisibility(View.VISIBLE);
        homeRL.setVisibility(View.GONE);

        RequestQueue requestQueue = Volley.newRequestQueue(MainActivity.this);
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url, null, response -> {
            loadingPB.setVisibility(View.GONE);
            homeRL.setVisibility(View.VISIBLE);
            weatherRVModalArrayList.clear();

            try {
                String temperature = response.getJSONObject("main").getString("temp");
                String condition = response.getJSONArray("weather").getJSONObject(0).getString("description");
                String iconCode = response.getJSONArray("weather").getJSONObject(0).getString("icon");
                String windSpeed = response.getJSONObject("wind").getString("speed");
                String humidity = response.getJSONObject("main").getString("humidity");
                String pressure = response.getJSONObject("main").getString("pressure");

                temperatureTV.setText(temperature + "°C");
                conditionTV.setText(condition);
                humidityTV.setText("Humidity: " + humidity + "%");
                windTV.setText("Wind Speed: " + windSpeed + " km/h");
                pressureTV.setText("Pressure: " + pressure + " hPa");
                String iconUrl = "https://openweathermap.org/img/wn/" + iconCode + "@2x.png";
                Picasso.get().load(iconUrl).into(iconIV);

                // Simulated forecast data (OpenWeather current API doesn't provide hourly here)
                //weatherRVModalArrayList.clear(); // make sure it's fresh
                //weatherRVModalArrayList.add(new WeatherRVModal("Now", temperature, iconUrl, windSpeed));
                //weatherRVAdapter.notifyDataSetChanged();


            } catch (JSONException e) {
                e.printStackTrace();
            }

        }, error -> {
            Log.e("WEATHER_API", "Error: " + error.toString());
            Toast.makeText(MainActivity.this, "Failed to fetch weather", Toast.LENGTH_SHORT).show();
        });

        requestQueue.add(jsonObjectRequest);
    }
}
