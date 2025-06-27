package com.example.weatherapploc;

public class KalmanLatLong {
    private static final float MIN_ACCURACY = 1f;

    private float qMetersPerSecond;
    private long timestamp;
    private double lat;
    private double lng;
    private float variance;

    public KalmanLatLong(float qMetersPerSecond) {
        this.qMetersPerSecond = qMetersPerSecond;
        this.variance = -1;
    }

    public void process(double latMeasurement, double lngMeasurement, float accuracy, long timeMillis) {
        if (accuracy < MIN_ACCURACY) accuracy = MIN_ACCURACY;

        if (variance < 0) {
            this.lat = latMeasurement;
            this.lng = lngMeasurement;
            this.timestamp = timeMillis;
            this.variance = accuracy * accuracy;
        } else {
            long timeDelta = timeMillis - this.timestamp;
            if (timeDelta > 0) {
                this.variance += timeDelta * qMetersPerSecond * qMetersPerSecond / 1000;
                this.timestamp = timeMillis;
            }

            float k = variance / (variance + accuracy * accuracy);
            this.lat += k * (latMeasurement - lat);
            this.lng += k * (lngMeasurement - lng);
            this.variance *= (1 - k);
        }
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public void reset() {
        this.variance = -1;
    }

    public void setProcessNoise(float q) {
        qMetersPerSecond = q;
    }

}
