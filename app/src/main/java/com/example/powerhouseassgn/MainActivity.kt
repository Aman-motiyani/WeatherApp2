package com.example.powerhouseassgn

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.powerhouseassgn.model.WeatherData
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity() {

    private val apiInterface: ApiInterface = ApiUtilities.getInstance().create(ApiInterface::class.java)
    private lateinit var temp: TextView
    private lateinit var status: TextView
    private lateinit var tempMin: TextView
    private lateinit var tempMax: TextView
    private lateinit var sunrise: TextView
    private lateinit var sunset: TextView
    private lateinit var visibility: TextView
    private lateinit var pressure: TextView
    private lateinit var humidity: TextView
    private lateinit var wind: TextView
    private lateinit var lastUpdated: TextView
    private lateinit var mfusedlocation: FusedLocationProviderClient
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout


    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var connectivityReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        temp = findViewById(R.id.temp)
        status = findViewById(R.id.status)
        tempMin = findViewById(R.id.temp_min)
        tempMax = findViewById(R.id.temp_max)
        sunrise = findViewById(R.id.sunrise)
        sunset = findViewById(R.id.sunsetTime)
        visibility = findViewById(R.id.VisibilityText)
        pressure = findViewById(R.id.pressureText)
        humidity = findViewById(R.id.humidityText)
        wind = findViewById(R.id.windSpeed)
        lastUpdated = findViewById(R.id.lastUpdated)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        sharedPreferences = getSharedPreferences("WeatherData", Context.MODE_PRIVATE)
        mfusedlocation = LocationServices.getFusedLocationProviderClient(this)

        val savedData = sharedPreferences.getString("weatherData", null)
        val lastUpdateTime = sharedPreferences.getLong("lastUpdateTime", -1)

        // Restore and Update Preview data if available
        if (savedData != null) {
            val weatherData = Gson().fromJson(savedData, WeatherData::class.java)
            updateWeatherData(weatherData)
        }

        // Display last update time if available
        if (lastUpdateTime != -1L) {
            val lastUpdatedText = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date(lastUpdateTime))
            lastUpdated.text = "Last Updated on: $lastUpdatedText"
        }

        //calling the method
        getCurrentLocationWeatherData()

        val search = findViewById<ImageButton>(R.id.searchImage)
        val searchData = findViewById<EditText>(R.id.search_bar)

        search.setOnClickListener {
            val cName = searchData.editableText.toString()
            getCityWeatherData(cName)
        }

        //To search from Keyboard
        searchData.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val cName = searchData.editableText.toString()
                getCityWeatherData(cName)
            }
            false
        }

        swipeRefreshLayout.setOnRefreshListener {
            swipeRefreshLayout.isRefreshing = true
            getCurrentLocationWeatherData()
            searchData.setText("")
        }

        // Register BroadcastReceiver for network connectivity change
        connectivityReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val connectivityManager =
                    context?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val networkInfo = connectivityManager.activeNetworkInfo
                if (networkInfo != null && networkInfo.isConnected) {
                    getCurrentLocationWeatherData()
                } else {
                    Toast.makeText(
                        applicationContext,
                        "No internet connection",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        registerReceiver(
            connectivityReceiver,
            IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        )

    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(connectivityReceiver)
    }

    // Method to fetch weather data for a specific city
    private fun getCityWeatherData(cityName: String) {
        lifecycleScope.launch {
            try {
                val response = apiInterface.getCityData(cityName)
                if (response.isSuccessful) {
                    val weatherData = response.body()
                    if (weatherData != null) {
                        updateWeatherData(weatherData)
                        saveWeatherData(weatherData)
                        Toast.makeText(applicationContext, cityName.uppercase(Locale.getDefault()), Toast.LENGTH_LONG)
                            .show()
                    } else {
                        Toast.makeText(applicationContext, "Empty weather data received", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(applicationContext, "Failed to retrieve weather data", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(applicationContext, "An error occurred", Toast.LENGTH_SHORT).show()
            }
            closeKeyboard()
        }
    }

    //Method to fetch the data based on location
    private fun getCurrentLocationWeatherData() {
        swipeRefreshLayout.isRefreshing = false
        if (checkLocationPermission()) {
            if (locationEnable()) {
                mfusedlocation.lastLocation.addOnCompleteListener { task ->
                    val location: Location? = task.result
                    if (location == null) {
                        newLocation()
                    } else {
                        val latitude = location.latitude.toString()
                        val longitude = location.longitude.toString()
                        fetchWeatherData(latitude, longitude)
                    }
                }
            } else {
                Toast.makeText(this, "Please Turn on your GPS location and Refresh Again", Toast.LENGTH_LONG).show()
            }
        } else {
            requestLocationPermission()
        }
    }

    @SuppressLint("MissingPermission")
    private fun newLocation() {
        val locationRequest = LocationRequest()
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        locationRequest.interval = 0
        locationRequest.fastestInterval = 0
        locationRequest.numUpdates = 1
        mfusedlocation = LocationServices.getFusedLocationProviderClient(this)
        mfusedlocation.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper())
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(p0: LocationResult) {
            val lastLocation: Location? = p0.lastLocation
            val latitude = lastLocation?.latitude.toString()
            val longitude = lastLocation?.longitude.toString()
            fetchWeatherData(latitude, longitude)
        }
    }

    private fun locationEnable(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    private fun fetchWeatherData(latitude: String, longitude: String) {
        lifecycleScope.launch {
            try {
                val response = apiInterface.getCurrentWeatherData(latitude, longitude)
                if (response.isSuccessful) {
                    val weatherData = response.body()
                    updateWeatherData(weatherData)
                        saveWeatherData(weatherData)
                        Toast.makeText(applicationContext, "Weather Updated", Toast.LENGTH_SHORT).show()
                } else { Toast.makeText(applicationContext, "Failed to retrieve weather data", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { Toast.makeText(applicationContext,"Something went wrong", Toast.LENGTH_SHORT).show()

            }
        }
    }
    private fun checkLocationPermission(): Boolean {
        val fineLocationPermission = Manifest.permission.ACCESS_FINE_LOCATION
        val coarseLocationPermission = Manifest.permission.ACCESS_COARSE_LOCATION
        return ContextCompat.checkSelfPermission(this, fineLocationPermission) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, coarseLocationPermission) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        val fineLocationPermission = Manifest.permission.ACCESS_FINE_LOCATION
        val coarseLocationPermission = Manifest.permission.ACCESS_COARSE_LOCATION
        val locationCode =1
        ActivityCompat.requestPermissions(
            this,
            arrayOf(fineLocationPermission, coarseLocationPermission),
            locationCode
        )
    }

    // Method to update the weather data on the UI
    private fun updateWeatherData(weatherData: WeatherData?) {
        temp.text = weatherData?.main?.temp.toString() + "°C"
        status.text = weatherData?.weather!![0].description.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        tempMin.text = "Min. Temp : " + weatherData.main.temp_min.toString() + "°C"
        tempMax.text = "Max. Temp : " + weatherData.main.temp_max.toString() + "°C"
        sunrise.text = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(weatherData.sys.sunrise * 1000L)
        sunset.text = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(weatherData.sys.sunset * 1000L)
        wind.text = weatherData.wind.speed.toString() + " Km/h"
        pressure.text = weatherData.main.pressure.toString() + " hPa"
        humidity.text = weatherData.main.humidity.toString() + "%"
        visibility.text = weatherData.visibility.toString() + " m"

        val currentTime = System.currentTimeMillis()
        val lastUpdatedText =
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date(currentTime))
        lastUpdated.text = "Last Updated on:  $lastUpdatedText"
    }

    // Method to save the weather data to SharedPreferences
    private fun saveWeatherData(weatherData: WeatherData?) {
        val editor = sharedPreferences.edit()
        val jsonWeatherData = Gson().toJson(weatherData)
        editor.putString("weatherData", jsonWeatherData)
        editor.putLong("lastUpdateTime", System.currentTimeMillis())
        editor.apply()
    }

    // Method to close the keyboard
    private fun closeKeyboard() {
        val view = this.currentFocus
        if (view != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
}
