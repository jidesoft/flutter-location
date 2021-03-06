package com.example.flutterlocation

import android.Manifest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GooglePlayServicesUtil
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.PluginRegistry.Registrar
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.app.ActivityCompat
import io.flutter.plugin.common.PluginRegistry
import android.location.Location
import android.location.Criteria
import android.location.LocationListener
import android.location.LocationManager
import android.content.Context
import android.os.Bundle
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener


class Channel {
    companion object {
        const val LOCATION = "javier-elizaga.location"
        const val LOCATION_EVENT = "javier-elizaga.location-event"
    }
}

class Method {
    companion object {
        const val PERMISSION = "permission"
        const val LOCATION = "location"
        const val LOCATION_CHINA = "locationChina"
        const val REQUEST_PERMISSIONS = "requestPermissions"
        const val IS_GOOGLE_PLAY_AVAILABVLE = "isGooglePlayAvailable"
    }
}

class Permission {
    companion object {
        const val NOT_DETERMINED = "NOT_DETERMINED"
        const val DENIED = "DENIED"
        const val AUTHORIZED = "AUTHORIZED"
    }
}

data class Error(val code: String, val desc: String)

class FlutterLocationPlugin(val activity: Activity) : MethodCallHandler, EventChannel.StreamHandler, PluginRegistry.RequestPermissionsResultListener {
    private val permissionNotDeterminedErr = Error("PERMISSION_NOT_DETERMINED", "Location must be determined, call request permission before calling location")
    private val permissionDeniedErr = Error("PERMISSION_DENIED", "You are not allow to access location")
    private val requestCode = 22

    private var locationMode = 0;
    // `Location
    private var fusedLocationClient: FusedLocationProviderClient? = null

    // Android Default Location
    private var locationManager: android.location.LocationManager? = null
    var locationListener: android.location.LocationListener? = null

    // AMap Location
    var amapLocationClient: AMapLocationClient? = null

    private var permission: String = Permission.NOT_DETERMINED
    private var permissionRequested = false

    private var eventSink: EventChannel.EventSink? = null

    private var locationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            eventSink?.success(locationToMap(locationResult.lastLocation))
        }

    }

    inner class MylocationListener : LocationListener {
        constructor() : super() {
        }

        override fun onLocationChanged(location: Location?) {
            eventSink?.success(locationToMap(location))
        }

        override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {}

        override fun onProviderEnabled(p0: String?) {}

        override fun onProviderDisabled(p0: String?) {}
    }

    inner class MyAmapLocationListener : AMapLocationListener {
        constructor() : super() {
        }

        override fun onLocationChanged(location: AMapLocation?) {
            eventSink?.success(amapLocationToMap(location))
        }
    }

    /**
     * Request permission callback
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>?, grantResults: IntArray?): Boolean {
        var result = false
        if (requestCode == requestCode && permissions?.size == 1 && grantResults?.size == 1) {
            if (permissions[0] == Manifest.permission.ACCESS_FINE_LOCATION) {
                permission = when (grantResults?.get(0)) {
                    PackageManager.PERMISSION_GRANTED -> Permission.AUTHORIZED
                    else -> Permission.DENIED
                }
                permissionRequested = false
                result = true
            }
        }
        return result
    }

    companion object {
        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val channel = MethodChannel(registrar.messenger(), Channel.LOCATION)
            val event = EventChannel(registrar.messenger(), Channel.LOCATION_EVENT)
            val instance = FlutterLocationPlugin(registrar.activity())
            channel.setMethodCallHandler(instance)
            event.setStreamHandler(instance)
            registrar.addRequestPermissionsResultListener(instance)
        }
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            Method.PERMISSION -> permission(result)
            Method.LOCATION -> location(result)
            Method.LOCATION_CHINA -> locationChina(result)
            Method.REQUEST_PERMISSIONS -> requestPermissions(result)
            Method.IS_GOOGLE_PLAY_AVAILABVLE -> isGooglePlayAvailable(result)
            else -> {
                result.notImplemented()
            }
        }
    }

    // Stream Handler
    override fun onListen(argument: Any?, eventSink: EventChannel.EventSink?) {
        if (permission == Permission.NOT_DETERMINED) {
            return
        }
        this.eventSink = eventSink
        when (locationMode) {
            0 -> {
                var locationRequest = LocationRequest()
                locationRequest.interval = 10000
                locationRequest.fastestInterval = 10000 / 2
                locationRequest.priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
                if (fusedLocationClient == null) {
                    fusedLocationClient = FusedLocationProviderClient(activity)
                }
                fusedLocationClient?.let{it.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper())}
            }
            1 -> {
                var criteria = android.location.Criteria()
                criteria.setAccuracy(Criteria.ACCURACY_COARSE)
                criteria.setAltitudeRequired(false)
                criteria.setBearingRequired(false)
                criteria.setCostAllowed(false)
                criteria.setSpeedRequired(false)
                if (locationManager == null) {
                    locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                }
                if (locationListener == null) {
                    locationListener = MylocationListener()
                }
                locationManager?.let{it.requestLocationUpdates(it.getBestProvider(criteria, false), 0L, 0f, locationListener)}
            }
            2 -> {
                if (amapLocationClient == null) {
                    amapLocationClient = AMapLocationClient(activity)
                }
                amapLocationClient?.let{it.setLocationListener(MyAmapLocationListener())}
                amapLocationClient?.let{it.startLocation()}
            }
        }


    }

    override fun onCancel(argument: Any?) {
        if (fusedLocationClient != null) fusedLocationClient?.let{it.removeLocationUpdates(locationCallback)}
        if (locationManager != null) locationManager?.let{it.removeUpdates(locationListener)}
        this.eventSink = null
    }

    private fun permission(result: Result) {
        val location = ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        permission = when {
            location == PackageManager.PERMISSION_GRANTED -> Permission.AUTHORIZED
            permissionRequested -> Permission.NOT_DETERMINED
            else -> Permission.DENIED
        }
        result.success(permission)
    }

    private fun requestPermissions(result: Result) {
        var requested = false
        val location = ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        if (location == PackageManager.PERMISSION_DENIED) {
            if (!permissionRequested) {
                permissionRequested = true
                permission = Permission.NOT_DETERMINED
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), requestCode)
                requested = true
            }
        }
        result.success(requested)
    }

//    private fun locationChina(result: Result) {
//    locationMode = 1;
//        when (permission) {
//            Permission.NOT_DETERMINED -> result.error(permissionNotDeterminedErr.code, permissionNotDeterminedErr.desc, null)
//            Permission.DENIED -> result.error(permissionDeniedErr.code, permissionDeniedErr.desc, null)
//            Permission.AUTHORIZED -> locationManager.requestSingleUpdate(criteria, object : android.location.LocationListener {
//                override fun onLocationChanged(location: Location?) {
//                    result.success(locationToMap(location))
//                }
//
//                override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {}
//
//                override fun onProviderEnabled(p0: String?) {}
//
//                override fun onProviderDisabled(p0: String?) {}
//            }, null)
//        }
//    }

    private fun locationChina(result: Result) {
        locationMode = 2;
        when (permission) {
            Permission.NOT_DETERMINED -> result.error(permissionNotDeterminedErr.code, permissionNotDeterminedErr.desc, null)
            Permission.DENIED -> result.error(permissionDeniedErr.code, permissionDeniedErr.desc, null)
            Permission.AUTHORIZED -> {
                if (amapLocationClient == null) {
                    amapLocationClient = AMapLocationClient(activity)
                }

                var amapOption = AMapLocationClientOption()
                amapOption.setLocationPurpose(AMapLocationClientOption.AMapLocationPurpose.Transport)
                amapOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy)
                amapOption.setHttpTimeOut(20000)
                amapOption.setNeedAddress(true)
                amapOption.setInterval(1000)
                amapOption.setLocationCacheEnable(true)
                amapOption.setOnceLocationLatest(true)

                amapLocationClient?.let{it.setLocationOption(amapOption)}
                amapLocationClient?.let{it.setLocationListener(object : AMapLocationListener {
                    override fun onLocationChanged(location: AMapLocation) {
                        result.success(amapLocationToMap(location))
                    }
                })}
                amapLocationClient?.let{it.startLocation()}
            }
        }
    }

    private fun isGooglePlayAvailable(result: Result) {
        val connectionResult = GooglePlayServicesUtil.isGooglePlayServicesAvailable(activity)

        if (connectionResult != ConnectionResult.SUCCESS) {
            // Google Play Services is NOT available. Show appropriate error dialog
            result.success(false)
        } else {
            result.success(true)
        }

    }

    private fun location(result: Result) {
        locationMode = 0;
        when (permission) {
            Permission.NOT_DETERMINED -> result.error(permissionNotDeterminedErr.code, permissionNotDeterminedErr.desc, null)
            Permission.DENIED -> result.error(permissionDeniedErr.code, permissionDeniedErr.desc, null)
            Permission.AUTHORIZED -> {
                if (fusedLocationClient == null) {
                    fusedLocationClient = FusedLocationProviderClient(activity)
                }
                fusedLocationClient?.let{it.lastLocation.addOnSuccessListener { location ->
                    result.success(locationToMap(location))
                }}
            }

        }
    }

    private fun locationToMap(location: Location?) = hashMapOf(
            "latitude" to location?.latitude,
            "longitude" to location?.longitude,
            "accuracy" to location?.accuracy?.toDouble(),
            "altitude" to location?.altitude,
            "speed" to location?.speed?.toDouble()
    )

    private fun amapLocationToMap(location: AMapLocation?) = hashMapOf(
            "latitude" to location?.latitude,
            "longitude" to location?.longitude,
            "accuracy" to location?.accuracy?.toDouble(),
            "altitude" to location?.altitude,
            "district" to location?.district,
            "city" to location?.city,
//            "province" to location?.province,
//            "country" to location?.country,
//            "address" to location?.address,
//            "aoiName" to location?.aoiName,
            "speed" to location?.speed?.toDouble()
    )


}
