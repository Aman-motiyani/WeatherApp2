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
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.powerhouseassgn.model.WeatherData
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

        sharedPreferences = getSharedPreferences("WeatherData", Context.MODE_PRIVATE)

        val savedData = sharedPreferences.getString("weatherData", null)
        val lastUpdateTime = sharedPreferences.getLong("lastUpdateTime", -1)

        // Restore saved weather data if available
        if (savedData != null) {
            val weatherData = Gson().fromJson(savedData, WeatherData::class.java)
            updateWeatherData(weatherData)
        }

        // Display last update time if available
        if (lastUpdateTime != -1L) {
            val lastUpdatedText = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date(lastUpdateTime))
            lastUpdated.text = "Last Updated on: $lastUpdatedText"
        }

        // Fetch weather data based on current location
        getCurrentLocationWeatherData()

        val search = findViewById<ImageButton>(R.id.searchImage)
        val searchData = findViewById<EditText>(R.id.search_bar)

        search.setOnClickListener {
            val cName = searchData.editableText.toString()
            getCityWeatherData(cName)
        }

        val cLocation = findViewById<ImageButton>(R.id.cLocation)
        cLocation.setOnClickListener {
            getCurrentLocationWeatherData()
            searchData.setText("")
        }
    }

    override fun onResume() {
        super.onResume()
        getCurrentLocationWeatherData();
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(connectivityReceiver)
    }

    // Method to fetch weather data for a specific city
    private fun getCityWeatherData(cityName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiInterface.getCityData(cityName)
                if (response.isSuccessful){
                    val weatherData = response.body()
                    runOnUiThread {
                        updateWeatherData(weatherData)
                        saveWeatherData(weatherData)
                        Toast.makeText(applicationContext, cityName.toUpperCase(), Toast.LENGTH_LONG).show()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            applicationContext,
                            "Failed to retrieve weather data",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(
                        applicationContext,
                        "An error occurred",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        closeKeyboard()
    }

    // Method to fetch weather data based on current location
    private fun getCurrentLocationWeatherData() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val locationListener = object : LocationListener {
            @SuppressLint("SetTextI18n")
            override fun onLocationChanged(location: Location) {
                val latitude = location.latitude.toString()
                val longitude = location.longitude.toString()

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val response = apiInterface.getCurrentWeatherData(latitude, longitude)
                        if (response.isSuccessful) {
                            val weatherData = response.body()

                            runOnUiThread {
                                updateWeatherData(weatherData)
                                saveWeatherData(weatherData)
                                Toast.makeText(applicationContext, "Weather Updated", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            runOnUiThread {
                                Toast.makeText(
                                    applicationContext,
                                    "Failed to retrieve weather data",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        runOnUiThread {
                            Toast.makeText(
                                applicationContext,
                                "Something went wrong",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }

            override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
            }

            override fun onProviderEnabled(provider: String) {}

            override fun onProviderDisabled(provider: String) {}
        }

        // BroadcastReceiver to check network connectivity
        val connectivityReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val connectivityManager =
                    context?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val networkInfo = connectivityManager.activeNetworkInfo
                if (networkInfo != null && networkInfo.isConnected) {
                    // Request location update when network connectivity is available
                    if (ActivityCompat.checkSelfPermission(
                            applicationContext,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                            applicationContext,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        // Request location permission if not granted
                        ActivityCompat.requestPermissions(
                            this@MainActivity,
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ),
                            1
                        )
                    } else {
                        // Request location update
                        locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, locationListener, null)
                    }
                }
            }
        }

        // Register BroadcastReceiver for network connectivity change
        registerReceiver(
            connectivityReceiver,
            IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        )

        // Request location update if location permission is granted
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                1
            )
        } else {
            locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, locationListener, null)
        }
    }

    // Method to update the weather data on the UI
    private fun updateWeatherData(weatherData: WeatherData?) {
        temp.text = weatherData?.main?.temp.toString() + "°C"
        status.text = weatherData?.weather!![0].description.capitalize(Locale.getDefault())
        tempMin.text = "Min. Temp : " + weatherData.main.temp_min.toString() + "°C"
        tempMax.text = "Max. Temp : " + weatherData.main.temp_max.toString() + "°C"
        sunrise.text =
            SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(weatherData.sys.sunrise * 1000L)
        sunset.text =
            SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(weatherData.sys.sunset * 1000L)
        wind.text = weatherData.wind.speed.toString() + " Km/h"
        pressure.text = weatherData.main.pressure.toString() + " hPa"
        humidity.text = weatherData.main.humidity.toString() + "%"
        visibility.text = weatherData.visibility.toString() + " m"

        val currentTime = System.currentTimeMillis()
        val lastUpdatedText =
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date(currentTime))
        lastUpdated.text = "Last Updated on: $lastUpdatedText"
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
