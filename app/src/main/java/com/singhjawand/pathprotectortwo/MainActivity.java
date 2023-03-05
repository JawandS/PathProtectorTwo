package com.singhjawand.pathprotectortwo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.math.BigDecimal;
import java.sql.Driver;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;

public class MainActivity extends Activity implements GPSCallback {
    private GPSManager gpsManager = null;
    Boolean isGPSEnabled = false;
    LocationManager locationManager;
    double currentSpeed = 0.0;
    double maxSpeed = 0.0;

    // text views
    TextView currentSpeedTxt;
    TextView maxSpeedTxt;
    TextView statusTxt;
    TextView otherInfoTxt;

    double drivingThreshold = 0.4; // default is 2.7 m/s
    //    double movingThreshold = 0.3;
    double firstTs;
    double timestamp;
    int timestampCounter = 0;
    double lastPause;
    Timestamp startingDate;

    // driving information
    boolean isDriving = false;
    double maxWaitTime = 5 * 1000; // default is 4 minutes --> milliseconds
    double minDriveTime = 1000; // default is 1 minute --> milliseconds
    Date date;

    DriverDB tripsDatabase = new DriverDB(this);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // set up
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        date = new Date();
        firstTs = System.currentTimeMillis();

        // get views
        currentSpeedTxt = findViewById(R.id.currentSpeed);
        maxSpeedTxt = findViewById(R.id.maxSpeed);
        statusTxt = findViewById(R.id.status);
        otherInfoTxt = findViewById(R.id.otherInfo);

        // access resources
        try {
            if (ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 101);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // start tracking speed
        getCurrentSpeed();
    }

    public void getCurrentSpeed() {
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        gpsManager = new GPSManager(MainActivity.this);
        isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (isGPSEnabled) {
            gpsManager.startListening(getApplicationContext());
            gpsManager.setGPSCallback(this);
        } else {
            gpsManager.showSettingsAlert();
        }
    }

    ArrayList<Location> trip_locations = new ArrayList<Location>();

    @SuppressLint("SetTextI18n")
    @Override
    public void onGPSUpdate(Location location) {
        currentSpeed = location.getSpeed();
        currentSpeed = round(currentSpeed, 3);
        currentSpeedTxt.setText("Current speed: " + currentSpeed + " m/s");

        if (currentSpeed > maxSpeed) { // update maximum speed
            maxSpeed = currentSpeed;
            maxSpeedTxt.setText("Max speed: " + currentSpeed + " m/s");
        }

        // update timestamp
        timestamp = System.currentTimeMillis();
//        Log.v("timestamp", "" + timestamp);
        // updates status
        if (currentSpeed > drivingThreshold) { // car
            timestampCounter += 1;
            statusTxt.setText("Status: Driving");
            if (!isDriving) { // began driving
                trip_locations = new ArrayList<Location>();
                trip_locations.add(location);
                firstTs = timestamp;
                startingDate = new Timestamp(date.getTime());
                lastPause = System.currentTimeMillis();
                isDriving = true;
            }
        } else if (isDriving && currentSpeed < drivingThreshold && timestamp - lastPause > maxWaitTime) { // done driving
            statusTxt.setText("Status: Done Driving");
            // you are driving, not going fast enough, the wait has been long enough
            isDriving = false; // done driving
            if (timestamp - firstTs > minDriveTime) { // check drive is long enough
                promptUser(); // ask the user whether to store drive
                resetState();
            }
        } else {
            statusTxt.setText("Status: Still");
            lastPause = System.currentTimeMillis();
        }
    }

    void resetState() {
        trip_locations = null;
        maxSpeed = 0;
    }

    void promptUser() {
        Log.v("ImportantInfo", "Saving data");
        float tripLength = (float) round((((timestamp - firstTs) / 1000.0) - (minDriveTime / 1000)), 3);
        DriverDB.Trip currentTrip = new DriverDB.Trip();
        // Timestamp startingDate -> date timestamp of the beginnign of the drive
        // ToDo - calculate: average speed during drive, max speed, day/night amount of driving
        // ToDo - prompt user if they want to save, store in
    }

    @Override
    protected void onDestroy() {
        gpsManager.stopListening();
        gpsManager.setGPSCallback(null);
        gpsManager = null;
        super.onDestroy();
    }

    public static double round(double unrounded, int precision) {
        int roundingMode = BigDecimal.ROUND_HALF_UP;
        BigDecimal bd = new BigDecimal(unrounded);
        BigDecimal rounded = bd.setScale(precision, roundingMode);
        return rounded.doubleValue();
    }
}

class DriverDB{
    SharedPreferences driverDB;
    public DriverDB(Activity main){
        driverDB = main.getSharedPreferences("com.example.myapp.driverDB_File", Context.MODE_PRIVATE);
    }

    public static class Trip{
        public Timestamp startingDate;
        public int tripLength;
        public float averageSpeed;
        public float maxSpeed;
        public float dayNightRatio;

        public Trip(){
            super();
        }
        public Trip(Timestamp startingDate, int tripLength, float averageSpeed, float maxSpeed, float dayNightRatio){
            this.startingDate = startingDate;
            this.tripLength = tripLength;
            this.averageSpeed = averageSpeed;
            this.maxSpeed = maxSpeed;
            this.dayNightRatio = dayNightRatio;
        }
    }

    public void addTrip(Trip trip){
        SharedPreferences.Editor editor = driverDB.edit();
        int numTrips = driverDB.getInt("numTrips", 0);
        editor.putInt("numTrips", numTrips + 1);
        editor.putLong("driver-trip-num-" + numTrips + "-startingDateUnixMillis", trip.startingDate.getTime());
        editor.putInt("driver-trip-num-" + numTrips + "-tripLength", trip.tripLength);
        editor.putFloat("driver-trip-num-" + numTrips + "-averageSpeed", trip.averageSpeed);
        editor.putFloat("driver-trip-num-" + numTrips + "-maxSpeed", trip.maxSpeed);
        editor.putFloat("driver-trip-num-" + numTrips + "-dayNightRatio", trip.dayNightRatio);
        editor.apply();
    }

    public int getNumTrips(){
        return driverDB.getInt("numTrips", 0);
    }

    public Trip getTrip(int n){
        return new Trip(
            new Timestamp(driverDB.getLong("driver-trip-num-" + n + "-startingDateUnixMillis", 0)),
            driverDB.getInt("driver-trip-num-" + n + "-tripLength", 0),
            driverDB.getFloat("driver-trip-num-" + n + "-averageSpeed", 0),
            driverDB.getFloat("driver-trip-num-" + n + "-maxSpeed", 0),
            driverDB.getFloat("driver-trip-num-" + n + "-dayNightRatio", 0)
        );
    }
}