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
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

import kotlin.Triple;

public class MainActivity extends Activity implements GPSCallback {
    private GPSManager gpsManager = null;
    Boolean isGPSEnabled = false;
    LocationManager locationManager;
    double currentSpeed = 0.0;

    // text views
    TextView currentSpeedTxt;
    TextView maxSpeedTxt;
    TextView statusTxt;
    TextView otherInfoTxt;


    TripInProgress currentTrip = new TripInProgress();

    double drivingThreshold = 0.4; // default is 2.7 m/s
    //    double movingThreshold = 0.3;
    double firstTs;
    double timestamp;
    double lastPause;

    // driving information
    boolean isDriving = false;
    double maxWaitTime = 5 * 1000; // default is 4 minutes --> milliseconds
    double minDriveTime = 1000; // default is 1 minute --> milliseconds
    Date date;
    DriverDB tripsDatabase;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // set up
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        tripsDatabase = new DriverDB(MainActivity.this);

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

    public void switchToLogs(View view) {
        setContentView(R.layout.activity_log);
        TableLayout spreadsheet = findViewById(R.id.mainTable);
        for(int i = 0; i < tripsDatabase.getNumTrips(); i++){
            DriverDB.Trip trip = tripsDatabase.getTrip(i);
            TableRow dataRow = (TableRow)(LayoutInflater.from(this).inflate(R.layout.activity_log_row, null, false));

            TextView date = (TextView)dataRow.getChildAt(0);
            date.setText(trip.startingDate.toString());

            TextView drivingTime = (TextView)dataRow.getChildAt(1);
            drivingTime.setText(((int)(trip.drivingTime / 3600)) + " hours " + ((int)(trip.drivingTime % 3600) / 60) + " minutes");

            TextView nightDrivingTime = (TextView)dataRow.getChildAt(2);
            nightDrivingTime.setText(((int)(trip.nightDrivingTime / 3600)) + " hours " + ((int)(trip.nightDrivingTime % 3600) / 60) + " minutes");

            TextView maxSpeed = (TextView)dataRow.getChildAt(3);
            maxSpeed.setText(trip.maxSpeed + " m/s");

            TextView violations = (TextView)dataRow.getChildAt(4);
            Set<String> violationsSet = trip.violations;
            String violationsString = "";
            if(violationsSet.size() == 0) violationsString = "no violations!";
            for(String violation : violationsSet){
                violationsString += violation + "\n";
            }
            violations.setText(violationsString);

            spreadsheet.addView(dataRow);
        }
    }

    public void goToHome(View view) {
        setContentView(R.layout.activity_home);
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
        //Log.v("currentSpeed", currentSpeed+"");
        //Create a Toast with the text "Hello World"
        //Toast.makeText(getApplicationContext(), "Hello World", Toast.LENGTH_LONG).show();

        currentSpeedTxt.setText("Speed: " + currentSpeed + " m/s");
        Log.v("status of speed", "Speed: " + currentSpeed + " m/s");

        // update timestamp
        timestamp = System.currentTimeMillis();
        // updates status
        if (currentSpeed > drivingThreshold) { // car
            isDriving = true;
            statusTxt.setText("Status: Driving");
            Log.v("stats", "driving");
            currentTrip.addLocation(location, new Timestamp(System.currentTimeMillis()));
        } else if (isDriving && currentSpeed < drivingThreshold && timestamp - lastPause > maxWaitTime) { // done driving
            statusTxt.setText("Status: Done Driving");
            Log.v("stats", "done driving");
            // you are driving, not going fast enough, the wait has been long enough
            isDriving = false; // done driving
            if (timestamp - firstTs > minDriveTime) { // check drive is long enough
                promptUser(); // ask the user whether to store drive
                currentTrip = new TripInProgress();
            }
        } else {
            statusTxt.setText("Status: Still");
            Log.v("stats", "still");
            lastPause = System.currentTimeMillis();
        }
    }

    @SuppressLint("SetTextI18n")
    void promptUser() {
        //Log.v("ImportantInfo", "Saving data");
        DriverDB.Trip dbTrip = currentTrip.finalizeTrip();
        tripsDatabase.addTrip(dbTrip);
        otherInfoTxt.setText((dbTrip.endingDate.getTime() - dbTrip.startingDate.getTime()) / 1000.f + " s");

        //Toast.makeText(getApplicationContext(), dbTrip.tripLength + " m/s", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        gpsManager.stopListening();
        gpsManager.setGPSCallback(null);
        gpsManager = null;
        super.onDestroy();
    }
}

class TripInProgress {
    ArrayList<Location> gpsLocations;
    ArrayList<Float> speedsInMetersPerSecond;
    ArrayList<Timestamp> timestamps;
    Location startLocation;
    Location endLocation;
    Timestamp startTime;
    Timestamp endTime;
    float numDaySeconds;
    float numNightSeconds;
    int numLocations = 0;
    boolean droveDuringBadHours = false;
    ArrayList<Triple<String, Integer, Timestamp>> violations = new ArrayList<>();

    public TripInProgress() {
        gpsLocations = new ArrayList<>();
        speedsInMetersPerSecond = new ArrayList<>();
        numDaySeconds = 0;
        numNightSeconds = 0;
    }

    public void addLocation(Location loc, Timestamp timestamp) {
        if (numLocations == 0) {
            startLocation = loc;
            endLocation = startLocation;
            startTime = timestamp;
            endTime = startTime;
        }
        long timeDiff = timestamp.getTime() - endTime.getTime();
        endLocation = loc;
        endTime = timestamp;
        gpsLocations.add(loc);
        speedsInMetersPerSecond.add(loc.getSpeed());
        if (isDark(String.valueOf(loc.getLatitude()), String.valueOf(loc.getLongitude()), timestamp.getTime())) {
            numNightSeconds += timeDiff / 1000.f;
        } else {
            numDaySeconds += timeDiff / 1000.f;
        }
        //between 12 AM and 4 AM, don't use getHours
        if (timestamp.getHours() < 4 && timestamp.getHours() >= 0) {
            droveDuringBadHours = true;
            violations.add(new Triple<>("It is not legal to drive between the hours of 12 AM and 4 AM without a driver's permit!", numLocations, timestamp));
        }
        // /*Log.v("ImportantInfo", startLocation.getLatitude() + " " + startLocation.getLongitude() + " " + startLocation.getTime());
        // Log.v("ImportantInfo", endLocation.getLatitude() + " " + endLocation.getLongitude() + " " + endLocation.getTime()); */
        numLocations++; //this has to be the last thing that happens
    }

    public boolean isDark(String latVal, String longVal, long timestamp) {
        TimeZone tz = TimeZone.getDefault();
        com.luckycatlabs.sunrisesunset.dto.Location location = new com.luckycatlabs.sunrisesunset.dto.Location(latVal, longVal);
        SunriseSunsetCalculator calculator = new SunriseSunsetCalculator(location, tz.getID());
        long officialSunset = calculator.getOfficialSunsetCalendarForDate(Calendar.getInstance()).getTimeInMillis();
        long officialSunrise = calculator.getOfficialSunriseCalendarForDate(Calendar.getInstance()).getTimeInMillis();
        return timestamp > officialSunset || timestamp < officialSunrise;
    }

    public DriverDB.Trip finalizeTrip() {
        DriverDB.Trip trip = new DriverDB.Trip();
        trip.startingDate = startTime;
        trip.endingDate = endTime;
        float averageSpeed = 0;
        for (int i = 0; i < speedsInMetersPerSecond.size(); i++) {
            averageSpeed += speedsInMetersPerSecond.get(i);
            //Log.v("averageSpeed", averageSpeed+"");
        }
        averageSpeed /= speedsInMetersPerSecond.size();
        trip.averageSpeed = averageSpeed;
        trip.maxSpeed = Collections.max(speedsInMetersPerSecond);
        trip.drivingTime = numDaySeconds + numNightSeconds;
        trip.nightDrivingTime = numNightSeconds;
        trip.violations = new HashSet<String>();
        for (int i = 0; i < violations.size(); i++) {
            trip.violations.add(violations.get(i).getThird().toString() + ": " + violations.get(i).getFirst());
        }
        return trip;
    }
}

class DriverDB {
    SharedPreferences driverDB;

    public DriverDB(Activity main) {
        driverDB = main.getSharedPreferences("com.singhjawand.PathProtectorTwo.driverDB_File", Context.MODE_PRIVATE);
    }

    public int getNumTrips() {
        return driverDB.getInt("numTrips", 0);
    }

    public float getTotalTime() {
        return driverDB.getFloat("totalTime", 0);
    }

    public float getTotalNightTime() {
        return driverDB.getFloat("totalNightTime", 0);
    }

    public int getNumViolations() {
        return driverDB.getInt("numViolations", 0);
    }


    public static class Trip {
        public Timestamp startingDate;
        public Timestamp endingDate;
        public float averageSpeed;
        public float maxSpeed;
        public float drivingTime;
        public float nightDrivingTime;
        public Set<String> violations;

        public Trip() {
            super();
        }

        public Trip(Timestamp startingDate, Timestamp endingDate, float averageSpeed, float maxSpeed, float drivingTime, float nightDrivingTime, Set<String> violations) {
            this.startingDate = startingDate;
            this.endingDate = endingDate;
            this.averageSpeed = averageSpeed;
            this.maxSpeed = maxSpeed;
            this.drivingTime = drivingTime;
            this.nightDrivingTime = nightDrivingTime;
            this.violations = violations;
        }
    }

    public void addTrip(Trip trip) {
        SharedPreferences.Editor editor = driverDB.edit();
        int numTrips = driverDB.getInt("numTrips", 0);
        editor.putInt("numTrips", numTrips + 1);
        editor.putLong("driver-trip-num-" + numTrips + "-startingDateUnixMillis", trip.startingDate.getTime());
        editor.putLong("driver-trip-num-" + numTrips + "-endingDateUnixMillis", trip.endingDate.getTime());
        editor.putFloat("driver-trip-num-" + numTrips + "-averageSpeed", trip.averageSpeed);
        editor.putFloat("driver-trip-num-" + numTrips + "-maxSpeed", trip.maxSpeed);
        editor.putFloat("driver-trip-num-" + numTrips + "-drivingTime", trip.drivingTime);
        editor.putFloat("totalTime", driverDB.getFloat("totalTime", 0) + trip.drivingTime);
        editor.putFloat("driver-trip-num-" + numTrips + "-nightDrivingTime", trip.nightDrivingTime);
        editor.putFloat("totalNightTime", driverDB.getFloat("totalNightTime", 0) + trip.nightDrivingTime);
        editor.putStringSet("driver-trip-num-" + numTrips + "-violations", trip.violations);
        editor.apply();
    }

    public Trip getTrip(int n) {
        return new Trip(
                new Timestamp(driverDB.getLong("driver-trip-num-" + n + "-startingDateUnixMillis", 0)),
                new Timestamp(driverDB.getLong("driver-trip-num-" + n + "-endingDateUnixMillis", 0)),
                driverDB.getFloat("driver-trip-num-" + n + "-averageSpeed", 0),
                driverDB.getFloat("driver-trip-num-" + n + "-maxSpeed", 0),
                driverDB.getFloat("driver-trip-num-" + n + "-drivingTime", 0),
                driverDB.getFloat("driver-trip-num-" + n + "-nightDrivingTime", 0),
                driverDB.getStringSet("driver-trip-num-" + n + "-violations", new HashSet<String>())
        );
    }
}