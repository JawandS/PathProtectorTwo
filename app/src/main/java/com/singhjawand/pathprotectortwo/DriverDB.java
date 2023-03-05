package com.singhjawand.pathprotectortwo;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;

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
        editor.putFloat("driver-trip-num-" + numTrips + "-endingDateUnixMillis", trip.endingDate.getTime());
        editor.putFloat("driver-trip-num-" + numTrips + "-averageSpeed", trip.averageSpeed);
        editor.putFloat("driver-trip-num-" + numTrips + "-maxSpeed", trip.maxSpeed);
        editor.putFloat("driver-trip-num-" + numTrips + "-drivingTime", trip.drivingTime);
        editor.putFloat("totalTime", driverDB.getFloat("totalTime", 0) + trip.drivingTime);
        editor.putFloat("driver-trip-num-" + numTrips + "-nightDrivingTime", trip.nightDrivingTime);
        editor.putFloat("totalNightTime", driverDB.getFloat("totalNightTime", 0) + trip.nightDrivingTime);
        editor.putStringSet("driver-trip-num-" + numTrips + "-violations", trip.violations);
        editor.putInt("numViolations", driverDB.getInt("numViolations", 0) + trip.violations.size());
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