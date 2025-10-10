# Peloworkout

A Kotlin/Jetpack Compose Android app for connecting to FTMS-enabled indoor bikes (e.g. Peloton with mods), tracking workout metrics, and uploading sessions to Strava.

## Screenshots

![Bike Selector](app/docs/img1.png)
![Workout screen](app/docs/img2.png)
![Summary screen](app/docs/img3.png)

## Prerequisites

Your bike must broadcast FTMS (Fitness Machine Service) and CSC (Cycling Speed and Cadence) data over Bluetooth LE.

### Peloton setup

Peloton bikes do not do this by default.  
To enable FTMS output:

1. Install the [forked Grupetto app](https://github.com/doudar/grupetto) on your Peloton.
2. Use [OpenPelo](https://github.com/doudar/OpenPelo) to sideload the app.
3. Ensure Grupetto is running during your ride so it can stream live metrics.

### Strava integration

To enable Strava uploads:

1. Create an API app in your Strava account at [strava.com/settings/api](https://www.strava.com/settings/api).
2. Note your **Client ID** and **Client Secret**.
3. In your cloned repo, create a file called `secrets.properties` in the project root and add:

   ```properties
   STRAVA_CLIENT_ID=XXX
   STRAVA_CLIENT_SECRET=XXX

### Features

* Connects to Bluetooth FTMS bikes
* Displays live power, cadence, speed, and resistance
* Workout summary screen
* Uploads workouts to Strava in .tcx format

### Build

* Android Studio (Giraffe or later)
* Gradle
* Kotlin + Jetpack Compose

### License

MIT