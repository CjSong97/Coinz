package com.example.beejceej.coinz

import android.content.Context
import android.location.Location
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineListener
import com.mapbox.android.core.location.LocationEnginePriority
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.CameraMode
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.RenderMode
import kotlinx.android.synthetic.main.activity_main.*
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), OnMapReadyCallback, PermissionsListener, LocationEngineListener {

    //values for downloading json
    private var today:String? = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    //values for parsing json
    private var downloadDate = "" //format: YYYY/MM/DD
    private val preferencesFile = "MyPrefsFile" //for storing preferences


    private var mapView: MapView? = null
    private var map: MapboxMap? = null

    private lateinit var permissionManager: PermissionsManager
    private lateinit var originLocation: Location
    private lateinit var locationEngine: LocationEngine
    //provide location layer to plugin, provides icon representing user location
    private lateinit var locationLayerPlugin: LocationLayerPlugin
    private val TAG = "MainActivity"

    //added downloadFileTask as an inner class

    inner class DownloadFileTask(private val caller: DownloadCompleteListener) :
            AsyncTask<String, Void, String>() {

        override fun doInBackground(vararg urls: String): String = try {
            loadFileFromNetwork(urls[0])
        } catch (e: IOException){
            "Unable to load content. Check your network connection"
        }

        private fun loadFileFromNetwork(urlString: String): String{
            val stream : InputStream = downloadUrl(urlString)
            //Read input from stream, build result as string

            return stream.bufferedReader().use { it.readText() }
        }

        @Throws(IOException::class)
        private fun downloadUrl(urlString: String) : InputStream {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.readTimeout = 10000 //milliseconds
            conn.connectTimeout = 15000 //milliseconds
            conn.requestMethod = "GET"
            conn.doInput = true
            conn.connect() //starts query
            return conn.inputStream
        }

        override fun onPostExecute(result: String) {
            super.onPostExecute(result)

            caller.downloadComplete(result)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        //async task
        val url = "homepages.inf.ed.ac.uk/stg/coinz/$today/coinzmap.geo"
        DownloadFileTask(DownloadCompleteRunner).execute(url)

        Mapbox.getInstance(this , getString(R.string.access_token))
        mapView = findViewById(R.id.mapView)
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync { this
        }
    }

    override fun onMapReady(mapboxMap: MapboxMap?) {
        if(mapboxMap == null){
            Log.d(TAG, "[onMapReady] mapboxMap is null")
        } else {
            map = mapboxMap
            //set user interface options
            map?.uiSettings?.isCompassEnabled = true
            map?.uiSettings?.isZoomControlsEnabled = true

            // make location information available
            enableLocation()
        }

    }

    private fun enableLocation() {
        //check whether permission granted for location
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            Log.d(TAG, "Permissions are granted")
            initializeLocationEngine()
            initializeLocationLayer()
        } else {
            Log.d(TAG, "Permissions NOT granted")
            permissionManager = PermissionsManager(this)
            permissionManager.requestLocationPermissions(this)
        }
    }

    @SuppressWarnings("MissingPermission")
    private fun initializeLocationEngine() {
        locationEngine = LocationEngineProvider(this).obtainBestLocationEngineAvailable()
        locationEngine.apply {
            interval = 5000
            fastestInterval = 1000
            priority = LocationEnginePriority.HIGH_ACCURACY
            activate()
        }

        val lastLocation = locationEngine.lastLocation
        if (lastLocation != null) {
            originLocation = lastLocation
            setCameraPosition(lastLocation)
        } else {
            locationEngine.addLocationEngineListener(this)
        }
    }

    //showing warnings even though its the right constant
    @SuppressWarnings("MissingPermission")
    private fun initializeLocationLayer() {
        if(mapView == null){
            Log.d(TAG, "mapView is null")
        } else {
            if (map == null) {Log.d(TAG, "map is null")
        } else {
                locationLayerPlugin = LocationLayerPlugin(mapView!!,
                        map!!, locationEngine)
                locationLayerPlugin.apply {
                setLocationLayerEnabled(true)
                cameraMode = CameraMode.TRACKING
                renderMode = RenderMode.NORMAL
                }
            }
            }

    }

    private fun setCameraPosition(location: Location) {
        val latlng = LatLng(location.latitude, location.longitude)
        map?.animateCamera(CameraUpdateFactory.newLatLng(latlng))
    }

    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {
        Log.d(TAG, "Permissions: $permissionsToExplain")
    }

    override fun onPermissionResult(granted: Boolean) {
        Log.d(TAG, "[onPermissionResult] granted == $granted")
        if (granted) {
            enableLocation()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        permissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onLocationChanged(location: Location?) {
        if (location == null){
            Log.d(TAG, "[onLocationChanged] is null")
        } else {
            originLocation = location
            setCameraPosition(originLocation)
            }

    }

    @SuppressWarnings("MissingPermission")
    override fun onConnected() {
        Log.d(TAG, "[onConnected] requesting location updates")
        locationEngine.requestLocationUpdates()
    }

    @SuppressWarnings("MissingPermission")
    override fun onStart() {
        super.onStart()

        //Restore preferences
        val settings = getSharedPreferences(preferencesFile, Context.MODE_PRIVATE)

        //use "" as the default value (might be first time app is run)
        downloadDate = settings.getString("lastDownloadDate", "")

        //Write message to logcat
        Log.d(TAG, "[onStart] Recalled lastDownloadDat = '$downloadDate'")
        mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "[onStop] Storing lastDownloadDate of $downloadDate")

        //All objects are from android.context.Context
        val settings = getSharedPreferences(preferencesFile, Context.MODE_PRIVATE)

        //Need Editor object to make preference changes
        val editor = settings.edit()
        editor.putString("lastDownloadDate", downloadDate)
        //Apply edits
        editor.apply()

        mapView?.onStop()
        locationEngine.removeLocationUpdates()
        locationLayerPlugin.onStop()

    }

    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()
        locationEngine.deactivate()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        if (outState != null) {
            mapView?.onSaveInstanceState(outState)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }
}