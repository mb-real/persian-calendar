package com.byagowi.persiancalendar.ui.preferences.locationathan.location

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.byagowi.persiancalendar.DEFAULT_CITY
import com.byagowi.persiancalendar.PREF_ALTITUDE
import com.byagowi.persiancalendar.PREF_GEOCODED_CITYNAME
import com.byagowi.persiancalendar.PREF_LATITUDE
import com.byagowi.persiancalendar.PREF_LONGITUDE
import com.byagowi.persiancalendar.PREF_SELECTED_LOCATION
import com.byagowi.persiancalendar.R
import com.byagowi.persiancalendar.ReleaseDebugDifference.logDebug
import com.byagowi.persiancalendar.utils.appPrefs
import com.byagowi.persiancalendar.utils.askForLocationPermission
import com.byagowi.persiancalendar.utils.copyToClipboard
import com.byagowi.persiancalendar.utils.dp
import com.byagowi.persiancalendar.utils.formatCoordinate
import com.byagowi.persiancalendar.utils.formatCoordinateISO6709
import com.byagowi.persiancalendar.utils.language
import com.byagowi.persiancalendar.utils.logException
import com.google.openlocationcode.OpenLocationCode
import io.github.persiancalendar.praytimes.Coordinate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import java.util.*
import java.util.concurrent.TimeUnit

fun Fragment.showGPSLocationDialog(): Boolean {
    runCatching { showGPSLocationDialogMain() }.onFailure(logException)
    return true
}

private fun Fragment.showGPSLocationDialogMain() {
    val activity = activity ?: return

    if (ActivityCompat.checkSelfPermission(
            activity, Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
            activity, Manifest.permission.ACCESS_COARSE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        askForLocationPermission(activity)
        return
    }

    val coordinatesFlow = MutableStateFlow<Coordinate?>(null)
    var cityName: String? = null
    val resultTextView = TextView(activity).also {
        it.setPadding(16.dp)
        it.setText(R.string.pleasewaitgps)
        it.setOnClickListener { _ ->
            copyToClipboard(it, "coords", it.text, showToastInstead = true)
        }
    }
    var isLocationEverShown = false

    fun showResult(coordinates: Coordinate, cityName: String) {
        resultTextView.text = listOf(
            cityName,
            formatCoordinate(activity, coordinates, "\n"),
            formatCoordinateISO6709(
                coordinates.latitude, coordinates.longitude, coordinates.elevation
            ),
            "https://plus.codes/${
                OpenLocationCode.encode(coordinates.latitude, coordinates.longitude)
            }"
        ).joinToString("\n\n")
    }

    // This is preference fragment view lifecycle but ideally we should've used
    // dialog's view lifecycle which resultTextView.findViewTreeLifecycleOwner()
    // won't give it to us probably because AlertDialog isn't fragment based
    val viewLifeCycle = this.viewLifecycleOwner.lifecycleScope

    coordinatesFlow
        .filterNotNull()
        .onEach { coordinates ->
            logDebug("GPSLocationDialog", "A location is received")
            showResult(coordinates, "")
            isLocationEverShown = true
        }
        .flowOn(Dispatchers.Main.immediate)
        .catch { logException(it) }
        .launchIn(viewLifeCycle)

    coordinatesFlow
        .filterNotNull()
        .mapNotNull { coordinates ->
            // Maybe some sort of throttling/debouncing would be nice here to not spam geocoder much.
            // There is an experimental debounce in stdlib itself but it wasn't exactly what was needed
            // as it feels it needed a throttle/leading debounce instead, this should be ok for now as
            // it is no worse that what we are shipping right now already.
            runCatching {
                Geocoder(context, Locale(language))
                    .getFromLocation(coordinates.latitude, coordinates.longitude, 1)
                    .firstOrNull()?.locality?.takeIf { it.isNotEmpty() }
            }.getOrNull()?.let { coordinates to it }
        }
        .flowOn(Dispatchers.IO)
        .onEach { (coordinates, locality) ->
            logDebug("GPSLocationDialog", "A geocoder locality result is received")
            showResult(coordinates, locality)
            cityName = locality
        }
        .flowOn(Dispatchers.Main.immediate)
        .catch { logException(it) }
        .launchIn(viewLifeCycle)

    val locationManager = activity.getSystemService<LocationManager>() ?: return

    fun checkGPSProvider() {
        if (coordinatesFlow.value != null) return

        runCatching {
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                AlertDialog.Builder(activity)
                    .setMessage(R.string.gps_internet_desc)
                    .setPositiveButton(R.string.accept) { _, _ ->
                        runCatching {
                            activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        }.onFailure(logException)
                    }
                    .show()
            }
        }.onFailure(logException)
    }

    val handler = Handler(Looper.getMainLooper())
    val checkGPSProviderCallback = Runnable { checkGPSProvider() }
    var isOneProviderEnabled = false
    val locationListener = object : LocationListener {
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onLocationChanged(location: Location) {
            coordinatesFlow.value =
                Coordinate(location.latitude, location.longitude, location.altitude)
        }

        override fun onProviderEnabled(provider: String) {
            isOneProviderEnabled = true
            if (!isLocationEverShown)
                resultTextView.setText(R.string.pleasewaitgps)
        }

        override fun onProviderDisabled(provider: String) {
            if (!isLocationEverShown && !isOneProviderEnabled)
                resultTextView.setText(R.string.enable_location_services)
        }
    }

    if (LocationManager.GPS_PROVIDER in locationManager.allProviders) {
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER, 0, 0f, locationListener
        )
    }
    if (LocationManager.NETWORK_PROVIDER in locationManager.allProviders) {
        locationManager.requestLocationUpdates(
            LocationManager.NETWORK_PROVIDER, 0, 0f, locationListener
        )
    }

    handler.postDelayed(checkGPSProviderCallback, TimeUnit.SECONDS.toMillis(30))
    val dialog = AlertDialog.Builder(activity)
        .setPositiveButton("", null)
        .setNegativeButton("", null)
        .setView(resultTextView)
        .create()

    val lifeCycleObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_PAUSE) dialog.dismiss()
    }
    // This is fragment's lifecycle
    lifecycle.addObserver(lifeCycleObserver)
    dialog.setOnDismissListener {
        logDebug("GPSLocationDialog", "Dialog is dismissed")
        coordinatesFlow.value?.let { coordinate ->
            activity.appPrefs.edit {
                putString(PREF_LATITUDE, "%f".format(Locale.ENGLISH, coordinate.latitude))
                putString(PREF_LONGITUDE, "%f".format(Locale.ENGLISH, coordinate.longitude))
                putString(PREF_ALTITUDE, "%f".format(Locale.ENGLISH, coordinate.elevation))
                putString(PREF_GEOCODED_CITYNAME, cityName ?: "")
                putString(PREF_SELECTED_LOCATION, DEFAULT_CITY)
            }
        }

        if (ActivityCompat.checkSelfPermission(
                activity, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                activity, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) locationManager.removeUpdates(locationListener)

        handler.removeCallbacks(checkGPSProviderCallback)
        lifecycle.removeObserver(lifeCycleObserver)
    }

    dialog.show()
}
