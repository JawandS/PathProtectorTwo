package com.singhjawand.pathprotectortwo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.math.BigDecimal;
import java.sql.Timestamp;
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
                firstTs = timestamp;
                startingDate = new Timestamp(date.getTime());
                lastPause = System.currentTimeMillis();
                isDriving = true;
            }
        } else if (isDriving && currentSpeed < drivingThreshold && timestamp - lastPause > maxWaitTime) { // done driving
            statusTxt.setText("Status: Done Driving");
            // you are driving, not going fast enough, the wait has been long enough
            isDriving = false; // done driving
            if (timestamp - firstTs > minDriveTime) // check drive is long enough
                promptUser(); // ask the user whether to store drive
        } else {
            statusTxt.setText("Status: Still");
            lastPause = System.currentTimeMillis();
        }
    }

    void promptUser() {
        Log.v("ImportantInfo", "Saving data");
        String tripLength = String.valueOf(round((timestamp - firstTs) / 1000.0, 3) - (minDriveTime / 1000));
        // Timestamp startingDate -> date timestamp of the beginnign of the drive
        // ToDo - calculate: average speed during drive, max speed, day/night amount of driving
        // ToDo - prompt user if they want to save, store in DB
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

/*statusTxt.setText("Update frequency: " + String.valueOf(round((timestamp - firstTs) / 1000 / timestampCounter, 3, BigDecimal.ROUND_HALF_UP)));
        final String TAG = "important info";
        Log.v(TAG, "Critical: " + (timestamp - firstTs));
        Log.v(TAG, "Critical: " + timestampCounter);
        Log.v(TAG, "Critical: " + (timestamp - firstTs) / 1000 / timestampCounter); */