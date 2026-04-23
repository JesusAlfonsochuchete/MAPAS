package com.example.maps.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.maps.data.remote.RetrofitClient
import com.example.maps.location.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale

import androidx.compose.foundation.background
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment

@SuppressLint("MissingPermission", "UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val locationHelper = remember { LocationHelper(context) }
    
    // Configuración de osmdroid (Esencial para que funcionen los tiles)
    Configuration.getInstance().userAgentValue = context.packageName

    var targetAddress by remember { mutableStateOf("") }
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var targetLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var routePolyline by remember { mutableStateOf<Polyline?>(null) }

    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(19.4326, -99.1332)) // Default CDMX
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mapView.onResume()
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                mapView.onPause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineLocationGranted || coarseLocationGranted) {
            coroutineScope.launch {
                val loc = locationHelper.getCurrentLocation()
                if (loc != null) {
                    val gp = GeoPoint(loc.latitude, loc.longitude)
                    userLocation = gp
                    mapView.controller.animateTo(gp)
                    
                    val userMarker = Marker(mapView)
                    userMarker.position = gp
                    userMarker.title = "Mi Ubicación"
                    mapView.overlays.add(userMarker)
                    mapView.invalidate()
                } else {
                    Toast.makeText(context, "Ubicación Nula. Si estás en emulador, envía una mock location.", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            Toast.makeText(context, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            val loc = locationHelper.getCurrentLocation()
            if (loc != null) {
                userLocation = GeoPoint(loc.latitude, loc.longitude)
                mapView.controller.setCenter(userLocation)
                
                val userMarker = Marker(mapView)
                userMarker.position = userLocation
                userMarker.title = "Mi Ubicación"
                mapView.overlays.add(userMarker)
                mapView.invalidate()
            } else {
                Toast.makeText(context, "Buscando ubicación GPS... Recuerda activar el GPS o enviar ubicación en el emulador.", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Surface(
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = targetAddress,
                        onValueChange = { targetAddress = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Dirección (ej. Reforma 222)") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val geocoder = Geocoder(context, Locale.getDefault())
                                try {
                                    @Suppress("DEPRECATION")
                                    val results = withContext(Dispatchers.IO) {
                                        geocoder.getFromLocationName(targetAddress, 1)
                                    }
                                    if (!results.isNullOrEmpty()) {
                                        val address = results[0]
                                        targetLocation = GeoPoint(address.latitude, address.longitude)
                                        
                                        val targetMarker = Marker(mapView)
                                        targetMarker.position = targetLocation
                                        targetMarker.title = "Destino (Casa)"
                                        mapView.overlays.add(targetMarker)
                                        mapView.invalidate()

                                        if (userLocation != null && targetLocation != null) {
                                            val startAndEnd = "${userLocation!!.longitude},${userLocation!!.latitude};${targetLocation!!.longitude},${targetLocation!!.latitude}"
                                            
                                            val response = withContext(Dispatchers.IO) {
                                                RetrofitClient.api.getDirections(startAndEnd)
                                            }
                                            
                                            response.routes?.firstOrNull()?.geometry?.coordinates?.let { coords ->
                                                routePolyline?.let { mapView.overlays.remove(it) }
                                                
                                                val geoPoints = coords.map { GeoPoint(it[1], it[0]) } // Geojson da [lon, lat]
                                                routePolyline = Polyline().apply {
                                                    setPoints(geoPoints)
                                                }
                                                mapView.overlays.add(routePolyline)
                                                mapView.invalidate()
                                                
                                                try {
                                                    mapView.zoomToBoundingBox(routePolyline!!.bounds, true)
                                                } catch (e: Exception) {
                                                    Log.e("MapScreen", "Error zooming to bounds: ${e.message}")
                                                }
                                            }
                                        } else {
                                            Toast.makeText(context, "No se tiene ubicación actual. Envía GPS desde el emulador.", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "No se encontró la dirección", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Log.e("MapScreen", "Error: ${e.message}")
                                    Toast.makeText(context, "Error al procesar dirección", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Text("Trazar")
                    }
                }
            }
            
            AndroidView(
                factory = { mapView },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
}
