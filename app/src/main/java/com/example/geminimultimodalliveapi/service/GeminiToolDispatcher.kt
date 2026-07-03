package com.example.geminimultimodalliveapi.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.geminimultimodalliveapi.MainActivity
import com.example.geminimultimodalliveapi.memory.MemoryManager
import com.example.geminimultimodalliveapi.network.GeminiLiveClient
import com.example.geminimultimodalliveapi.utils.DocumentParser
import com.example.geminimultimodalliveapi.utils.GoogleCalendarServiceHelper
import com.example.geminimultimodalliveapi.utils.LocalVehicleDbHelper
import com.example.geminimultimodalliveapi.error.AppError
import com.example.geminimultimodalliveapi.session.SessionStateHolder
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

class GeminiToolDispatcher(
    private val context: Context,
    private val dbHelper: LocalVehicleDbHelper,
    private val memoryManager: MemoryManager,
    private val scope: CoroutineScope,
    private val logger: Logger
) {

    interface Logger {
        fun log(message: String)
    }

    private fun showCameraLaunchNotification(intent: Intent) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "camera_launch_channel"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Camera Launch Channel",
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntentFlags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            1001,
            intent,
            pendingIntentFlags
        )

        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setContentTitle("เปิดกล้องถ่ายภาพ")
            .setContentText("แตะเพื่อเริ่มถ่ายภาพและส่งข้อมูลให้ AI")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(1001, notification)
    }

    private fun readTextWithTimeout(urlStr: String, connectTimeout: Int = 10000, readTimeout: Int = 10000): String {
        val url = java.net.URL(urlStr)
        val connection = url.openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = connectTimeout
        connection.readTimeout = readTimeout
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun sendErrorToolResponse(callId: String, liveClient: GeminiLiveClient?, message: String?) {
        try {
            val output = JSONObject().apply {
                put("success", false)
                put("error", message ?: "Unknown error occurred")
            }
            liveClient?.sendToolResponse(callId, output)
        } catch (e: Exception) {
            Log.e("GeminiToolDispatcher", "Failed to send error tool response", e)
        }
    }

    fun handleCameraOpen(callId: String, liveClient: GeminiLiveClient?) {
        Log.i("GeminiToolDispatcher", "Tool call open_camera requested (id=$callId)")
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("START_CAMERA", true)
            putExtra("CAMERA_CALL_ID", callId)
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && !android.provider.Settings.canDrawOverlays(context)) {
                showCameraLaunchNotification(intent)
            } else {
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e("GeminiToolDispatcher", "Failed to start MainActivity from background", e)
            liveClient?.sendToolResponse(callId, false)
        }
    }

    fun handleCameraClose(callId: String, liveClient: GeminiLiveClient?) {
        Log.i("GeminiToolDispatcher", "Tool call close_camera requested (id=$callId)")
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("STOP_CAMERA", true)
            putExtra("CAMERA_CALL_ID", callId)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("GeminiToolDispatcher", "Failed to send STOP_CAMERA to MainActivity", e)
            liveClient?.sendToolResponse(callId, false)
        }
    }

    fun handleSaveVehicleInfo(
        callId: String,
        category: String,
        keyName: String,
        infoValue: String,
        liveClient: GeminiLiveClient?
    ) {
        scope.launch {
            try {
                Log.i("GeminiToolDispatcher", "Tool call save_vehicle_info: cat=$category, key=$keyName, val=$infoValue")
                val success = withContext(Dispatchers.IO) {
                    dbHelper.saveInfo(category, keyName, infoValue)
                }
                liveClient?.sendToolResponse(callId, success)
                if (success) {
                    logger.log("SYSTEM: Saved $category - $keyName to local memory")
                    val memoryId = "vehicle_${category}_${keyName}"
                    memoryManager.addFact(memoryId, "ข้อมูลรถหมวดหมู่ $category ($keyName): $infoValue", isPinned = false, category = category)
                } else {
                    logger.log("SYSTEM: Failed to save to local memory")
                }
            } catch (e: Exception) {
                Log.e("GeminiToolDispatcher", "Error in handleSaveVehicleInfo", e)
                liveClient?.sendToolResponse(callId, false)
            }
        }
    }

    fun handleQueryVehicleInfo(callId: String, category: String?, liveClient: GeminiLiveClient?) {
        scope.launch {
            try {
                Log.i("GeminiToolDispatcher", "Tool call query_vehicle_info: cat=$category")
                val records = withContext(Dispatchers.IO) {
                    dbHelper.queryInfo(category)
                }
                
                val output = JSONObject()
                val resultsArray = JSONArray()
                for (record in records) {
                    val recJson = JSONObject()
                    recJson.put("category", record["category"])
                    recJson.put("key_name", record["key_name"])
                    recJson.put("info_value", record["info_value"])
                    recJson.put("updated_at", record["updated_at"])
                    resultsArray.put(recJson)
                }
                output.put("results", resultsArray)
                
                liveClient?.sendToolResponse(callId, output)
                logger.log("SYSTEM: Queried local memory (found ${records.size} items)")
            } catch (e: Exception) {
                Log.e("GeminiToolDispatcher", "Error in handleQueryVehicleInfo", e)
                sendErrorToolResponse(callId, liveClient, e.message)
            }
        }
    }

    fun handleGetCurrentTime(callId: String, liveClient: GeminiLiveClient?) {
        try {
            Log.i("GeminiToolDispatcher", "Tool call get_current_time")
            val tz = java.util.TimeZone.getDefault()
            val sdfDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = tz }
            val sdfTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).apply { timeZone = tz }
            val sdfIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).apply { timeZone = tz }
            val dayOfWeekEng = java.text.SimpleDateFormat("EEEE", java.util.Locale.US).apply { timeZone = tz }.format(java.util.Date())
            
            val dayOfWeekThai = when (dayOfWeekEng.lowercase()) {
                "sunday" -> "วันอาทิตย์"
                "monday" -> "วันจันทร์"
                "tuesday" -> "วันอังคาร"
                "wednesday" -> "วันพุธ"
                "thursday" -> "วันพฤหัสบดี"
                "friday" -> "วันศุกร์"
                "saturday" -> "วันเสาร์"
                else -> dayOfWeekEng
            }
            
            val dateStr = sdfDate.format(java.util.Date())
            val timeStr = sdfTime.format(java.util.Date())
            val isoStr = sdfIso.format(java.util.Date())
            val timezoneStr = "${tz.id} (${tz.getDisplayName(false, java.util.TimeZone.SHORT, java.util.Locale.US)})"
            
            val output = JSONObject()
            output.put("current_date", dateStr)
            output.put("current_time", timeStr)
            output.put("iso_datetime", isoStr)
            output.put("day_of_week", dayOfWeekThai)
            output.put("timezone", timezoneStr)
            output.put("note", "นี่คือเวลาปัจจุบันของระบบบนเครื่องโทรศัพท์มือถือของผู้ใช้ เป็นเวลาท้องถิ่นประเทศไทยเรียบร้อยแล้ว ห้ามคุณคำนวณหรือปรับค่าโซนเวลาใดๆ เพิ่มเติมอีก ให้ใช้เวลา $timeStr และวันที่ $dateStr (วัน$dayOfWeekThai) ในการตอบได้ทันที")
            
            liveClient?.sendToolResponse(callId, output)
            logger.log("SYSTEM: Provided device current time ($timeStr, $timezoneStr)")
        } catch (e: Exception) {
            Log.e("GeminiToolDispatcher", "Error in handleGetCurrentTime", e)
            sendErrorToolResponse(callId, liveClient, e.message)
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        return locationManager?.let {
            it.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
            it.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        } ?: false
    }

    private fun getUserLocation(onLocationReceived: (android.location.Location?) -> Unit) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                if (lastLoc != null) {
                    val ageMs = System.currentTimeMillis() - lastLoc.time
                    if (ageMs < 120000) { // 2 minutes
                        Log.i("GeminiToolDispatcher", "getUserLocation: Using fresh lastLocation (age: ${ageMs / 1000}s)")
                        onLocationReceived(lastLoc)
                    } else {
                        Log.i("GeminiToolDispatcher", "getUserLocation: lastLocation is old (${ageMs / 1000}s), requesting fresh location")
                        val cancellationTokenSource = com.google.android.gms.tasks.CancellationTokenSource()
                        var delivered = false
                        
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
                            .addOnSuccessListener { location ->
                                if (!delivered) {
                                    delivered = true
                                    cancellationTokenSource.cancel()
                                    if (location != null) {
                                        Log.i("GeminiToolDispatcher", "getUserLocation: getCurrentLocation success (fresh fix)")
                                        onLocationReceived(location)
                                    } else {
                                        Log.w("GeminiToolDispatcher", "getUserLocation: getCurrentLocation was null, falling back to old lastLocation")
                                        onLocationReceived(lastLoc)
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("GeminiToolDispatcher", "getUserLocation: getCurrentLocation failed, falling back to old lastLocation", e)
                                if (!delivered) {
                                    delivered = true
                                    cancellationTokenSource.cancel()
                                    onLocationReceived(lastLoc)
                                }
                            }
                        
                        scope.launch {
                            delay(4000) // 4 seconds timeout
                            if (!delivered) {
                                delivered = true
                                Log.w("GeminiToolDispatcher", "getUserLocation: getCurrentLocation timed out, cancelling and falling back to lastLocation")
                                cancellationTokenSource.cancel()
                                withContext(Dispatchers.Main) {
                                    onLocationReceived(lastLoc)
                                }
                            }
                        }
                    }
                } else {
                    Log.i("GeminiToolDispatcher", "getUserLocation: lastLocation is null, requesting getCurrentLocation")
                    val cancellationTokenSource = com.google.android.gms.tasks.CancellationTokenSource()
                    var delivered = false
                    
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
                        .addOnSuccessListener { location ->
                            if (!delivered) {
                                delivered = true
                                cancellationTokenSource.cancel()
                                onLocationReceived(location)
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("GeminiToolDispatcher", "getUserLocation: getCurrentLocation failed", e)
                            if (!delivered) {
                                delivered = true
                                cancellationTokenSource.cancel()
                                onLocationReceived(null)
                            }
                        }
                    
                    scope.launch {
                        delay(5000) // 5 seconds timeout
                        if (!delivered) {
                            delivered = true
                            Log.w("GeminiToolDispatcher", "getUserLocation: getCurrentLocation timed out and no lastLocation, cancelling")
                            cancellationTokenSource.cancel()
                            withContext(Dispatchers.Main) {
                                onLocationReceived(null)
                            }
                        }
                    }
                }
            }.addOnFailureListener { e ->
                Log.e("GeminiToolDispatcher", "getUserLocation: lastLocation failed", e)
                val cancellationTokenSource = com.google.android.gms.tasks.CancellationTokenSource()
                var delivered = false
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
                    .addOnSuccessListener { location ->
                        if (!delivered) {
                            delivered = true
                            cancellationTokenSource.cancel()
                            onLocationReceived(location)
                        }
                    }
                    .addOnFailureListener {
                        if (!delivered) {
                            delivered = true
                            cancellationTokenSource.cancel()
                            onLocationReceived(null)
                        }
                    }
                
                scope.launch {
                    delay(5000)
                    if (!delivered) {
                        delivered = true
                        cancellationTokenSource.cancel()
                        withContext(Dispatchers.Main) {
                            onLocationReceived(null)
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e("GeminiToolDispatcher", "getUserLocation: SecurityException", e)
            onLocationReceived(null)
        }
    }

    fun handleGetCurrentWeather(callId: String, liveClient: GeminiLiveClient?) {
        Log.i("GeminiToolDispatcher", "Tool call get_current_weather requested (id=$callId)")
        
        // 1. Check location permissions
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("GeminiToolDispatcher", "Cannot fetch location: missing permissions")
            val output = JSONObject().apply {
                put("success", false)
                put("error", "แอปพลิเคชันยังไม่ได้รับสิทธิ์เข้าถึงพิกัดตำแหน่ง (Location permissions missing)")
            }
            liveClient?.sendToolResponse(callId, output)
            logger.log("SYSTEM: Weather request failed (Permissions missing)")
            
            // Request permission via MainActivity
            val permIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("REQUEST_LOCATION_PERMISSION", true)
            }
            try {
                context.startActivity(permIntent)
            } catch (e: Exception) {
                Log.e("GeminiToolDispatcher", "Failed to request location permission from background", e)
            }
            return
        }

        // 2. Fetch location
        getUserLocation { location ->
            if (location != null) {
                val lat = location.latitude
                val lon = location.longitude
                scope.launch {
                    val weatherData = fetchWeatherFromApi(lat, lon)
                    liveClient?.sendToolResponse(callId, weatherData)
                    val currentTemp = weatherData.optJSONObject("current_weather")?.optDouble("temperature")
                    if (currentTemp != null) {
                        logger.log("SYSTEM: Provided weather data (Temp: $currentTemp C)")
                    } else {
                        logger.log("SYSTEM: Provided weather data (successful fetch)")
                    }
                }
            } else {
                val errorMsg = if (!isLocationEnabled()) {
                    "ระบบระบุตำแหน่ง (GPS) บนโทรศัพท์ปิดอยู่ กรุณาเปิดระบบระบุตำแหน่งในการตั้งค่า"
                } else {
                    "ไม่สามารถดึงตำแหน่งพิกัด GPS ได้ในขณะนี้ (สัญญาณอ่อนหรือขัดข้อง)"
                }
                val output = JSONObject().apply {
                    put("success", false)
                    put("error", errorMsg)
                }
                liveClient?.sendToolResponse(callId, output)
                logger.log("SYSTEM: Failed to fetch location coordinates for weather")
            }
        }
    }

    private suspend fun fetchWeatherFromApi(lat: Double, lon: Double): JSONObject = withContext(Dispatchers.IO) {
        try {
            val urlString = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true&timezone=auto"
            val response = readTextWithTimeout(urlString)
            JSONObject(response).apply {
                put("success", true)
            }
        } catch (e: Exception) {
            Log.e("GeminiToolDispatcher", "Failed to query weather API", e)
            JSONObject().apply {
                put("success", false)
                put("error", "Weather API query failed: ${e.message}")
            }
        }
    }

    fun handleFindNearbyPlaces(callId: String, placeType: String, liveClient: GeminiLiveClient?) {
        Log.i("GeminiToolDispatcher", "Tool call find_nearby_places requested: type=$placeType (id=$callId)")
        
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val output = JSONObject().apply {
                put("success", false)
                put("error", "แอปพลิเคชันยังไม่ได้รับสิทธิ์เข้าถึงพิกัดตำแหน่ง (Location permissions missing)")
            }
            liveClient?.sendToolResponse(callId, output)
            return
        }

        getUserLocation { location ->
            if (location != null) {
                scope.launch {
                    val results = fetchNearbyFromOsm(location.latitude, location.longitude, placeType)
                    liveClient?.sendToolResponse(callId, results)
                }
            } else {
                val errorMsg = if (!isLocationEnabled()) {
                    "ระบบระบุตำแหน่ง (GPS) บนโทรศัพท์ปิดอยู่ กรุณาเปิดระบบระบุตำแหน่งในการตั้งค่าเพื่อค้นหาสถานที่"
                } else {
                    "ไม่สามารถดึงตำแหน่งพิกัดเพื่อค้นหาสถานที่ได้ในขณะนี้ (สัญญาณอ่อนหรือขัดข้อง)"
                }
                val output = JSONObject().apply {
                    put("success", false)
                    put("error", errorMsg)
                }
                liveClient?.sendToolResponse(callId, output)
            }
        }
    }

    private suspend fun fetchNearbyFromOsm(userLat: Double, userLon: Double, placeType: String): JSONObject = withContext(Dispatchers.IO) {
        try {
            val osmFilter = when (placeType.lowercase().trim()) {
                "fuel", "ปั๊มน้ำมัน", "ปั๊ม", "gas station" -> "[amenity=fuel]"
                "repair", "ร้านซ่อมรถ", "ร้านซ่อมมอเตอร์ไซค์", "ร้านซ่อม", "repair shop" -> "[shop~\"motorcycle|car_repair\"]"
                "hospital", "โรงพยาบาล", "คลินิก" -> "[amenity=hospital]"
                "restaurant", "ร้านอาหาร", "ร้านขายอาหาร", "ร้านก๋วยเตี๋ยว", "ของกิน" -> "[amenity=restaurant]"
                "cafe", "ร้านกาแฟ", "คาเฟ่" -> "[amenity=cafe]"
                "atm", "ตู้เอทีเอ็ม", "เอทีเอ็ม", "ตู้กดเงิน" -> "[amenity=atm]"
                else -> "[amenity=${placeType}]"
            }

            val osmQuery = "[out:json];node(around:5000,$userLat,$userLon)$osmFilter;out 15;"
            val urlString = "https://overpass-api.de/api/interpreter?data=${Uri.encode(osmQuery)}"
            val response = readTextWithTimeout(urlString)
            val rawJson = JSONObject(response)
            
            val elements = rawJson.optJSONArray("elements")
            val formattedResults = JSONArray()

            if (elements != null) {
                val tempList = mutableListOf<JSONObject>()
                for (i in 0 until elements.length()) {
                    val elem = elements.getJSONObject(i)
                    val lat = elem.optDouble("lat")
                    val lon = elem.optDouble("lon")
                    val tags = elem.optJSONObject("tags")
                    
                    val name = tags?.optString("name") ?: tags?.optString("brand") ?: when (placeType) {
                        "fuel" -> "ปั๊มน้ำมันไม่มีชื่อ"
                        "repair" -> "ร้านซ่อมรถไม่มีชื่อ"
                        "hospital" -> "โรงพยาบาล/คลินิก"
                        "restaurant" -> "ร้านอาหารไม่มีชื่อ"
                        "cafe" -> "ร้านกาแฟไม่มีชื่อ"
                        else -> "สถานที่ใกล้ตัว"
                    }

                    val distanceResults = FloatArray(1)
                    android.location.Location.distanceBetween(userLat, userLon, lat, lon, distanceResults)
                    val distanceMeters = distanceResults[0].toInt()

                    val placeObj = JSONObject().apply {
                        put("name", name)
                        put("distance_meters", distanceMeters)
                        put("latitude", lat)
                        put("longitude", lon)
                    }
                    tempList.add(placeObj)
                }

                // Sort by distance ascending
                tempList.sortBy { it.getInt("distance_meters") }
                for (place in tempList) {
                    formattedResults.put(place)
                }
            }

            JSONObject().apply {
                put("success", true)
                put("place_type", placeType)
                put("user_latitude", userLat)
                put("user_longitude", userLon)
                put("places", formattedResults)
            }
        } catch (e: Exception) {
            Log.e("GeminiToolDispatcher", "OSM fetch failed", e)
            JSONObject().apply {
                put("success", false)
                put("error", "Failed to query OSM: ${e.message}")
            }
        }
    }

    fun handleLaunchNavigation(callId: String, destination: String, liveClient: GeminiLiveClient?) {
        Log.i("GeminiToolDispatcher", "Tool call launch_navigation requested: dest=$destination (id=$callId)")
        
        try {
            // Start navigation Intent using Google Maps
            val gmmIntentUri = Uri.parse("google.navigation:q=${Uri.encode(destination)}")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            // Fallback if Google Maps is not installed
            if (mapIntent.resolveActivity(context.packageManager) == null) {
                mapIntent.setPackage(null)
            }

            context.startActivity(mapIntent)

            val output = JSONObject().apply {
                put("success", true)
                put("message", "กำลังเปิดแอปพลิเคชันนำทางเพื่อไปยัง $destination")
            }
            liveClient?.sendToolResponse(callId, output)
            logger.log("SYSTEM: Launched Google Maps navigation to '$destination'")
        } catch (e: Exception) {
            Log.e("GeminiToolDispatcher", "Failed to launch navigation", e)
            val output = JSONObject().apply {
                put("success", false)
                put("error", e.message)
            }
            liveClient?.sendToolResponse(callId, output)
            logger.log("SYSTEM: Failed to launch navigation: ${e.message}")
        }
    }

    fun handleDeleteVehicleInfo(callId: String, category: String, keyName: String?, liveClient: GeminiLiveClient?) {
        scope.launch {
            try {
                Log.i("GeminiToolDispatcher", "Tool call delete_vehicle_info: cat=$category, key=$keyName")
                val success = withContext(Dispatchers.IO) {
                    dbHelper.deleteInfo(category, keyName)
                }
                liveClient?.sendToolResponse(callId, success)
                if (success) {
                    logger.log("SYSTEM: Deleted $category memory log")
                } else {
                    logger.log("SYSTEM: Failed to delete memory log or no matching record")
                }
            } catch (e: Exception) {
                Log.e("GeminiToolDispatcher", "Error in handleDeleteVehicleInfo", e)
                liveClient?.sendToolResponse(callId, false)
            }
        }
    }

    fun handleQueryPolicyDocument(callId: String, query: String, liveClient: GeminiLiveClient?) {
        Log.i("GeminiToolDispatcher", "Tool call query_policy_document: $query")
        val results = DocumentParser.queryDocuments(context, query)
        
        val output = JSONObject()
        output.put("context", results)
        
        liveClient?.sendToolResponse(callId, output)
        logger.log("SYSTEM: Queried policy docs for '$query'")
    }

    fun handleMakePhoneCall(callId: String, phoneNumber: String, liveClient: GeminiLiveClient?) {
        Log.i("GeminiToolDispatcher", "Tool call make_phone_call: $phoneNumber")
        val sanitizedNumber = phoneNumber.replace(Regex("[^0-9+*#]"), "")
        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.CALL_PHONE
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                val permIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("REQUEST_CALL_PERMISSION", true)
                }
                context.startActivity(permIntent)
                throw SecurityException("Missing CALL_PHONE permission. Requesting permission from user.")
            }

            // Respond success to tool call immediately so model session can terminate gracefully
            val output = JSONObject()
            output.put("success", true)
            liveClient?.sendToolResponse(callId, output)
            logger.log("SYSTEM: Triggered automatic call for $sanitizedNumber")

            // Wait for audio player to finish speaking before disconnecting and starting call
            scope.launch {
                var checkCount = 0
                // Wait up to 10 seconds (100 * 100ms)
                while (checkCount < 100) {
                    val isPlaying = com.example.geminimultimodalliveapi.FloatingWidgetService.instance?.isAudioPlaying() ?: false
                    if (!isPlaying) {
                        // Double check after 200ms to ensure it's not a temporary gap between packets
                        delay(200)
                        if (com.example.geminimultimodalliveapi.FloatingWidgetService.instance?.isAudioPlaying() != true) {
                            break
                        }
                    }
                    delay(100)
                    checkCount++
                }

                // Disconnect to release mic and audio resources
                com.example.geminimultimodalliveapi.FloatingWidgetService.instance?.disconnect()
                
                // Allow some time (1500ms) for audio focus / hardware mic release to fully settle
                delay(1500)

                // Start call intent

                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$sanitizedNumber")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e("GeminiToolDispatcher", "Failed to start automatic call", e)
            val output = JSONObject()
            output.put("success", false)
            output.put("error", e.message)
            liveClient?.sendToolResponse(callId, output)
            logger.log("SYSTEM: Failed to trigger automatic call")
        }
    }


    fun handleEndPhoneCall(callId: String, liveClient: GeminiLiveClient?) {
        Log.i("GeminiToolDispatcher", "Tool call end_phone_call requested (id=$callId)")
        try {
            val success = endPhoneCall()
            val output = JSONObject()
            output.put("success", success)
            liveClient?.sendToolResponse(callId, output)
            if (success) {
                logger.log("SYSTEM: Ended phone call successfully")
            } else {
                logger.log("SYSTEM: Failed to end phone call (no active call or unsupported version)")
            }
        } catch (e: Exception) {
            Log.e("GeminiToolDispatcher", "Failed to end call", e)
            val output = JSONObject()
            output.put("success", false)
            output.put("error", e.message)
            liveClient?.sendToolResponse(callId, output)
            logger.log("SYSTEM: Failed to end phone call: ${e.message}")
        }
    }

    private fun endPhoneCall(): Boolean {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ANSWER_PHONE_CALLS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            val permIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("REQUEST_ANSWER_CALL_PERMISSION", true)
            }
            context.startActivity(permIntent)
            throw SecurityException("Missing ANSWER_PHONE_CALLS permission. Requesting permission from user.")
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
            if (telecomManager != null) {
                try {
                    return telecomManager.endCall()
                } catch (e: Exception) {
                    Log.e("GeminiToolDispatcher", "TelecomManager endCall failed", e)
                }
            }
            return false // Do NOT fallback to reflection on P+
        }

        // Reflection fallback for older versions (pre-P)
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
            if (telephonyManager != null) {
                val getITelephony = telephonyManager.javaClass.getDeclaredMethod("getITelephony")
                getITelephony.isAccessible = true
                val iTelephony = getITelephony.invoke(telephonyManager)
                val endCall = iTelephony.javaClass.getDeclaredMethod("endCall")
                endCall.invoke(iTelephony)
                return true
            }
        } catch (e: Exception) {
            Log.e("GeminiToolDispatcher", "Reflection endCall failed", e)
        }

        return false
    }

    fun handleCreateCalendarEvent(
        callId: String,
        title: String,
        description: String,
        startTimeIso: String,
        durationMinutes: Int,
        liveClient: GeminiLiveClient?
    ) {
        Log.i("GeminiToolDispatcher", "Tool call create_calendar_event: title=$title, start=$startTimeIso, duration=$durationMinutes")
        
        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
        if (lastAccount == null) {
            Log.w("GeminiToolDispatcher", "Cannot create calendar event: User is not signed in to Google Account.")
            val output = JSONObject()
            output.put("success", false)
            output.put("error", "User is not signed in to Google Account")
            liveClient?.sendToolResponse(callId, output)
            logger.log("SYSTEM: Failed to create calendar event (Not signed in)")
            return
        }

        scope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val credential = GoogleAccountCredential.usingOAuth2(
                        context,
                        listOf(DriveScopes.DRIVE_FILE, "https://www.googleapis.com/auth/calendar")
                    ).apply {
                        selectedAccount = lastAccount.account
                    }

                    val calendarService = com.google.api.services.calendar.Calendar.Builder(
                        com.google.api.client.http.javanet.NetHttpTransport(),
                        com.google.api.client.json.gson.GsonFactory.getDefaultInstance(),
                        credential
                    )
                    .setApplicationName("Gemini Live Demo")
                    .build()

                    val helper = GoogleCalendarServiceHelper(calendarService)
                    
                    val startMs = parseIsoDateTime(startTimeIso)
                    val endMs = startMs + (durationMinutes * 60 * 1000)
                    val reminderMin = com.example.geminimultimodalliveapi.data.AppPreferences.getInstance(context).calendarReminderMinutes
                    
                    helper.insertEvent(title, startMs, endMs, description, reminderMin)
                } catch (e: Exception) {
                    Log.e("GeminiToolDispatcher", "Failed to insert event via voice tool", e)
                    SessionStateHolder.postError(AppError.fromThrowable(e))
                    false
                }
            }

            val output = JSONObject()
            output.put("success", success)
            liveClient?.sendToolResponse(callId, output)

            if (success) {
                logger.log("SYSTEM: Created calendar event: '$title'")
                val memoryId = "cal_${System.currentTimeMillis()}"
                memoryManager.addFact(memoryId, "ผู้ใช้มีบันทึกนัดหมายเรื่อง '$title' รายละเอียด: '$description' เริ่มเวลา: $startTimeIso", isPinned = false)
                context.sendBroadcast(Intent("com.example.geminimultimodalliveapi.CALENDAR_UPDATED"))
            } else {
                logger.log("SYSTEM: Failed to create calendar event")
            }
        }
    }

    fun handleListCalendarEvents(callId: String, liveClient: GeminiLiveClient?) {
        Log.i("GeminiToolDispatcher", "Tool call list_calendar_events requested (id=$callId)")
        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
        if (lastAccount == null) {
            Log.w("GeminiToolDispatcher", "Cannot list calendar events: User is not signed in to Google Account.")
            val output = JSONObject()
            output.put("success", false)
            output.put("error", "User is not signed in to Google Account")
            liveClient?.sendToolResponse(callId, output)
            logger.log("SYSTEM: Failed to list calendar events (Not signed in)")
            return
        }

        scope.launch {
            val output = withContext(Dispatchers.IO) {
                try {
                    val credential = GoogleAccountCredential.usingOAuth2(
                        context,
                        listOf(DriveScopes.DRIVE_FILE, "https://www.googleapis.com/auth/calendar")
                    ).apply {
                        selectedAccount = lastAccount.account
                    }

                    val calendarService = com.google.api.services.calendar.Calendar.Builder(
                        com.google.api.client.http.javanet.NetHttpTransport(),
                        com.google.api.client.json.gson.GsonFactory.getDefaultInstance(),
                        credential
                    )
                    .setApplicationName("Gemini Live Demo")
                    .build()

                    val helper = GoogleCalendarServiceHelper(calendarService)
                    val now = System.currentTimeMillis()
                    val oneWeekLater = now + (7L * 24L * 60L * 60L * 1000L)
                    
                    val eventsList = helper.fetchEventsForRange(now, oneWeekLater)
                    val out = JSONObject()
                    val eventsArr = JSONArray()
                    for (e in eventsList) {
                        val eJson = JSONObject().apply {
                            put("id", e.id)
                            put("title", e.title)
                            put("description", e.description ?: "")
                            put("start_time", formatMsToIso(e.startTime))
                            put("end_time", formatMsToIso(e.endTime))
                        }
                        eventsArr.put(eJson)
                    }
                    out.put("success", true)
                    out.put("events", eventsArr)
                    out
                } catch (e: Exception) {
                    Log.e("GeminiToolDispatcher", "Failed to list calendar events", e)
                    SessionStateHolder.postError(AppError.fromThrowable(e))
                    val out = JSONObject()
                    out.put("success", false)
                    out.put("error", e.message ?: "Unknown error")
                    out
                }
            }
            liveClient?.sendToolResponse(callId, output)
            if (output.optBoolean("success", false)) {
                val count = output.optJSONArray("events")?.length() ?: 0
                logger.log("SYSTEM: Listed calendar events (found $count items)")
            } else {
                logger.log("SYSTEM: Failed to list calendar events")
            }
        }
    }

    fun handleRememberPersonalFact(
        callId: String,
        factContent: String,
        importance: Int,
        category: String,
        liveClient: GeminiLiveClient?
    ) {
        Log.i("GeminiToolDispatcher", "Tool call remember_personal_fact: $factContent (importance=$importance, category=$category)")
        
        scope.launch(Dispatchers.IO) {
            val memoryId = "fact_${System.currentTimeMillis()}"
            val isPinned = (importance == 5)
            memoryManager.addFact(
                id = memoryId,
                content = factContent,
                isPinned = isPinned,
                baseImportance = importance,
                category = category
            )
            
            val output = JSONObject()
            output.put("success", true)
            liveClient?.sendToolResponse(callId, output)
            
            // Push updated memory context mid-session on IO thread
            liveClient?.updateMemoryContext(memoryManager.getFormattedContextPrompt())
            
            logger.log("SYSTEM: 🧠 [ความจำ] บันทึกสำเร็จ: \"$factContent\" (ความสำคัญ: $importance, หมวดหมู่: $category)")
            scope.launch(Dispatchers.Main) {
                android.widget.Toast.makeText(context, "🧠 [ระบบความจำ] บันทึกสำเร็จ: \"$factContent\"", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun handleForgetPersonalFact(callId: String, query: String, liveClient: GeminiLiveClient?) {
        Log.i("GeminiToolDispatcher", "Tool call forget_personal_fact: query=$query")
        
        scope.launch(Dispatchers.IO) {
            // High-performance search using SQL LIKE
            val matching = memoryManager.searchMemories(query)
            
            for (m in matching) {
                memoryManager.deleteMemory(m.id)
            }
            
            val deletedCount = matching.size
            val success = deletedCount > 0
            val output = JSONObject()
            output.put("success", success)
            output.put("deleted_count", deletedCount)
            liveClient?.sendToolResponse(callId, output)
            
            if (success) {
                // Push updated memory context mid-session on IO thread
                liveClient?.updateMemoryContext(memoryManager.getFormattedContextPrompt())
                
                logger.log("SYSTEM: 🗑️ [ความจำ] ลบความจำสำหรับคำค้นหา \"$query\" สำเร็จ ($deletedCount รายการ)")
                scope.launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "🗑️ [ระบบความจำ] ลบสำเร็จ ($deletedCount รายการ)", android.widget.Toast.LENGTH_LONG).show()
                }
            } else {
                logger.log("SYSTEM: 🗑️ [ความจำ] ไม่พบข้อมูลที่ต้องการลบสำหรับคำค้นหา \"$query\"")
                scope.launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "🗑️ [ระบบความจำ] ไม่พบข้อมูลสำหรับ \"$query\"", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun handleQueryRelevantMemories(callId: String, searchQuery: String, liveClient: GeminiLiveClient?) {
        Log.i("GeminiToolDispatcher", "Tool call query_relevant_memories: searchQuery=$searchQuery")
        
        scope.launch(Dispatchers.IO) {
            // High-performance search using SQL LIKE
            val matching = memoryManager.searchMemories(searchQuery)
            
            // Record access to boost matching memories' utility score
            for (m in matching) {
                memoryManager.recordAccess(m.id)
            }
            
            val memoriesArr = org.json.JSONArray()
            for (m in matching) {
                memoriesArr.put(m.content)
            }
            
            val output = JSONObject()
            output.put("success", true)
            output.put("memories", memoriesArr)
            liveClient?.sendToolResponse(callId, output)
            
            logger.log("SYSTEM: 🔍 [ค้นหาความจำ] คำค้นหา \"$searchQuery\" (พบ ${matching.size} รายการ)")
        }
    }

    fun handleSaveSystemRule(
        callId: String,
        conditionType: String?,
        conditionValue: String?,
        instruction: String?,
        action: String,
        liveClient: GeminiLiveClient?
    ) {
        Log.i("GeminiToolDispatcher", "Tool call save_system_rule: action=$action, type=$conditionType, val=$conditionValue, inst=$instruction")
        
        scope.launch(Dispatchers.IO) {
            val service = com.example.geminimultimodalliveapi.FloatingWidgetService.instance
            if (service == null) {
                val output = JSONObject().apply {
                    put("success", false)
                    put("error", "FloatingWidgetService is not running")
                }
                liveClient?.sendToolResponse(callId, output)
                return@launch
            }

            try {
                if (action.equals("CLEAR_ALL", ignoreCase = true)) {
                    service.dynamicRulesManager.clearAllRules()
                    val output = JSONObject().apply {
                        put("success", true)
                        put("message", "Cleared all system rules successfully")
                    }
                    liveClient?.sendToolResponse(callId, output)
                    
                    // Trigger dynamic prompt reload in the WebSocket
                    service.refreshDynamicPrompt()
                    logger.log("SYSTEM: ⚙️ [กฎการจูนสด] รีเซ็ตและลบกฎพฤติกรรมทั้งหมดแล้ว")
                } else {
                    if (conditionType == null || conditionValue == null || instruction == null) {
                        val output = JSONObject().apply {
                            put("success", false)
                            put("error", "Missing required parameters for SAVE action")
                        }
                        liveClient?.sendToolResponse(callId, output)
                        return@launch
                    }

                    val enumType = try {
                        com.example.geminimultimodalliveapi.architecture.ConditionType.valueOf(conditionType.uppercase())
                    } catch (e: Exception) {
                        com.example.geminimultimodalliveapi.architecture.ConditionType.GENERAL
                    }

                    val rule = com.example.geminimultimodalliveapi.architecture.DynamicSystemRule(
                        conditionType = enumType,
                        conditionValue = conditionValue,
                        instructionToInject = instruction
                    )
                    service.dynamicRulesManager.saveRule(rule)

                    val output = JSONObject().apply {
                        put("success", true)
                        put("message", "System rule saved successfully")
                    }
                    liveClient?.sendToolResponse(callId, output)
                    
                    // Trigger dynamic prompt reload in the WebSocket
                    service.refreshDynamicPrompt()
                    logger.log("SYSTEM: ⚙️ [กฎการจูนสด] บันทึกสำเร็จ: เมื่อ $conditionValue -> \"$instruction\"")
                }
            } catch (e: Exception) {
                Log.e("GeminiToolDispatcher", "Failed to process system rule", e)
                val output = JSONObject().apply {
                    put("success", false)
                    put("error", e.message)
                }
                liveClient?.sendToolResponse(callId, output)
            }
        }
    }

    fun handleGetSituationalContext(callId: String, liveClient: GeminiLiveClient?) {
        Log.i("GeminiToolDispatcher", "Tool call get_situational_context requested")
        val service = com.example.geminimultimodalliveapi.FloatingWidgetService.instance
        if (service == null) {
            val output = JSONObject().apply {
                put("success", false)
                put("error", "FloatingWidgetService is not running")
            }
            liveClient?.sendToolResponse(callId, output)
            return
        }

        val snapshot = service.contextManager.getCurrentSnapshot()
        val customRules = service.dynamicRulesManager.getActiveInstructionsForContext(
            currentMotion = snapshot.motion,
            currentLocation = snapshot.location,
            currentTopic = snapshot.currentTopic
        )

        val output = JSONObject().apply {
            put("success", true)
            put("user", snapshot.user)
            put("location", snapshot.location)
            put("motion", snapshot.motion)
            put("attention", snapshot.attentionSession)
            put("topic", snapshot.currentTopic)
            put("custom_rules", customRules)
        }
        
        liveClient?.sendToolResponse(callId, output)
        logger.log("SYSTEM: ⚙️ [บริบทเซ็นเซอร์] ดึงบริบทสำเร็จ (Motion:${snapshot.motion}, Topic:${snapshot.currentTopic})")
    }
 
    companion object {
        internal fun formatMsToIso(ms: Long): String {
            val instant = java.time.Instant.ofEpochMilli(ms)
            return java.time.OffsetDateTime.ofInstant(instant, java.time.ZoneId.systemDefault()).toString()
        }

        internal fun parseIsoDateTime(isoStr: String): Long {
            try {
                return java.time.OffsetDateTime.parse(isoStr).toInstant().toEpochMilli()
            } catch (e: Exception) {
                try {
                    return java.time.Instant.parse(isoStr).toEpochMilli()
                } catch (e2: Exception) {
                    return java.time.LocalDateTime.parse(isoStr)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                }
            }
        }
    }
}
