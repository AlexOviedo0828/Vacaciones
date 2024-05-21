package com.example.aplicacion_vacaciones

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.example.aplicacion_vacaciones.ui.theme.Aplicacion_VacacionesTheme
import android.content.Intent
import android.net.Uri

data class Lugar(
    val nombre: String,
    var ubicacion: String,
    val pais: Int,
    val costoVisita: String,
    var costoNoche: Int,
    var costoTraslado: Int
) {
    init {
        // Asignación de valores aleatorios para costo por noche y costo del traslado
        costoNoche = (100000..500000).random()
        costoTraslado = (50000..200000).random()
    }
}

data class Seleccion(
    val lugar: Lugar,
    val foto: Bitmap?
)

class ActividadPrincipal : ComponentActivity() {

    private val solicitarPermisoCamara = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            tomarFoto.launch(null)
        } else {
            Log.d("ActividadPrincipal", "Permiso denegado")
        }
    }

    private val tomarFoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let {
            imagenCapturada = it
            navegarADetalleLugar(lugarSeleccionado)
        } ?: run {
            Log.d("ActividadPrincipal", "Error al tomar foto")
        }
    }

    private var imagenCapturada by mutableStateOf<Bitmap?>(null)
    private var lugarSeleccionado by mutableStateOf<Lugar?>(null)
    private lateinit var lugares: List<Lugar>
    private val lugaresSeleccionados = mutableStateListOf<Seleccion>()

    private val solicitarPermisoUbicacion = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            obtenerUbicacionYMostrar()
        } else {
            Log.d("ActividadPrincipal", "Permiso de ubicación denegado")
        }
    }

    private var ubicacionActual by mutableStateOf<Location?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("ActividadPrincipal", "onCreate llamado")
        val mensajeBienvenida = resources.getString(R.string.bienvenida)
        val lugaresArray = resources.getStringArray(R.array.Lugares)
        val sitiosArray = resources.obtainTypedArray(R.array.Lugares_Imagenes)
        lugares = lugaresArray.mapIndexed { index, rawData ->
            val datosLugar = rawData.split("|")
            val nombre = datosLugar[0]
            val ubicacion = datosLugar[1]
            val pais = sitiosArray.getResourceId(index, R.drawable.ic_launcher_foreground)
            Lugar(nombre, ubicacion, pais, obtenerCostoVisita(nombre), 0, 0)
        }
        sitiosArray.recycle()

        setContent {
            Aplicacion_VacacionesTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PantallaPrincipal(this@ActividadPrincipal, lugares, mensajeBienvenida)
                }
            }
        }
    }

    fun navegarADetalleLugar(lugar: Lugar?) {
        lugar?.let {
            setContent {
                PantallaDetalleLugar(
                    it,
                    imagenCapturada,
                    onRegresar = {
                        setContent {
                            PantallaPrincipal(
                                this@ActividadPrincipal,
                                lugares,
                                "Bienvenida"
                            )
                        }
                        lugarSeleccionado = null
                        imagenCapturada = null
                    },
                    onTomarFoto = { tomarFotoLugar(lugar) },
                    onGuardar = { guardarSeleccion(it, imagenCapturada) },
                    onMostrarUbicacion = { mostrarUbicacionUsuario() }
                )
            }
        }
    }

    private fun navegarASeleccionesGuardadas() {
        setContent {
            PantallaSeleccionesGuardadas(
                lugaresSeleccionados,
                onEliminar = { eliminarSeleccion(it) },
                onFinalizar = {
                    setContent {
                        PantallaPrincipal(this@ActividadPrincipal, lugares, "Bienvenida")
                    }
                }
            )
        }
    }

    private fun guardarSeleccion(lugar: Lugar, foto: Bitmap?) {
        lugaresSeleccionados.add(Seleccion(lugar, foto))
        navegarASeleccionesGuardadas()
    }

    fun tomarFotoLugar(lugar: Lugar) {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.d("ActividadPrincipal", "Permiso concedido, tomando foto")
                lugarSeleccionado = lugar
                tomarFoto.launch(null)
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                Log.d("ActividadPrincipal", "Mostrando justificación de permiso")
            }
            else -> {
                Log.d("ActividadPrincipal", "Solicitando permiso")
                solicitarPermisoCamara.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun obtenerCostoVisita(nombre: String): String {
        return when (nombre) {
            "Cascada" -> "500,000"
            "Catarata" -> "450,000"
            "Cristo Redentor" -> "550,000"
            "Cuzco" -> "290,000"
            "Salar" -> "450,000"
            else -> "Desconocido"
        }
    }

    private fun navegarAMapa(lugar: Lugar) {
        val gmmIntentUri = Uri.parse("geo:0,0?q=${lugar.ubicacion}")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        startActivity(mapIntent)
    }

    private fun obtenerUbicacionYMostrar() {
        val locationManager = getSystemService<LocationManager>()
        try {
            locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
                ubicacionActual = it
                val gmmIntentUri = Uri.parse("geo:${it.latitude},${it.longitude}")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.setPackage("com.google.android.apps.maps")
                startActivity(mapIntent)
            } ?: run {
                Log.d("ActividadPrincipal", "Ubicación no encontrada")
            }
        } catch (e: SecurityException) {
            Log.e("ActividadPrincipal", "Error obteniendo ubicación", e)
        }
    }

    fun mostrarUbicacionUsuario() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                obtenerUbicacionYMostrar()
            }
            shouldShowRequestPermissionRationale
                (Manifest.permission.ACCESS_FINE_LOCATION) -> {
                Log.d("ActividadPrincipal", "Mostrando justificación de permiso de ubicación")
            }
            else -> {
                solicitarPermisoUbicacion.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun eliminarSeleccion(seleccion: Seleccion) {
        lugaresSeleccionados.remove(seleccion)
    }
}

@Composable
fun PantallaPrincipal(
    actividad: ActividadPrincipal,
    lugares: List<Lugar>,
    mensajeBienvenida: String
) {
    Column {
        Image(
            painter = painterResource(id = R.drawable.horizontal),
            contentDescription = "Logo",
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        )
        Text(
            text = mensajeBienvenida,
            modifier = Modifier.padding(16.dp)
        )
        Button(
            onClick = { actividad.mostrarUbicacionUsuario() },
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text = "Mostrar Ubicación Local")
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(lugares) { lugar ->
                ItemLugar(lugar) {
                    actividad.navegarADetalleLugar(lugar)
                }
            }
        }
    }
}

@Composable
fun ItemLugar(
    lugar: Lugar,
    navegarADetalle: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(8.dp)
            .clickable { navegarADetalle() }
    ) {
        Image(
            painter = painterResource(id = lugar.pais),
            contentDescription = lugar.nombre,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = lugar.nombre)
            Text(text = "Costo de Visita: ${lugar.costoVisita}")
        }
    }
}

@Composable
fun PantallaDetalleLugar(
    lugar: Lugar,
    imagenCapturada: Bitmap?,
    onRegresar: () -> Unit,
    onTomarFoto: () ->
    Unit,
    onGuardar: () -> Unit,
    onMostrarUbicacion: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = onMostrarUbicacion) {
                Text(text = "Mostrar Ubicación Local")
            }
            Button(onClick = onTomarFoto) {
                Text(text = "Tomar Foto")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Detalles del Lugar")
        Spacer(modifier = Modifier.height(16.dp))
        Image(
            painter = painterResource(id = lugar.pais),
            contentDescription = lugar.nombre,
            modifier = Modifier.fillMaxWidth().height(200.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Nombre: ${lugar.nombre}")
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Costo de Visita: ${lugar.costoVisita}")
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Costo por Noche: ${lugar.costoNoche}")
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Costo de Traslado: ${lugar.costoTraslado}")
        Spacer(modifier = Modifier.height(16.dp))
        imagenCapturada?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Imagen Capturada",
                modifier = Modifier.fillMaxWidth().height(200.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        Button(onClick = onGuardar) {
            Text(text = "Guardar")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRegresar) {
            Text(text = "Volver")
        }
    }
}

@Composable
fun PantallaSeleccionesGuardadas(
    seleccionados: List<Seleccion>,
    onEliminar: (Seleccion) -> Unit,
    onFinalizar: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Lugares Guardados")
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn {
            items(seleccionados) { seleccion ->
                ItemSeleccion(seleccion, onEliminar)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onFinalizar) {
            Text(text = "Finalizar")
        }
    }
}

@Composable
fun ItemSeleccion(
    seleccion: Seleccion,
    onEliminar: (Seleccion) -> Unit
) {
    Column(modifier = Modifier.padding(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(text = "Nombre: ${seleccion.lugar.nombre}")
                Text(text = "Ubicación: ${seleccion.lugar.ubicacion}")
                Text(text = "Costo de Visita: ${seleccion.lugar.costoVisita}")
                Text(text = "Costo por Noche: ${seleccion.lugar.costoNoche}")
                Text(text = "Costo de Traslado: ${seleccion.lugar.costoTraslado}")
            }
            Button(
                onClick = { onEliminar(seleccion) },
                modifier = Modifier.size(40.dp)
            ) {
                Text(text = "Eliminar")
            }
        }
        seleccion.foto?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Imagen Capturada",
                modifier = Modifier.fillMaxWidth().height(200.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun VistaPreviaPorDefecto() {
    val lugaresEjemplo = listOf(
        Lugar("Cascada", "Ubicación 1", R.drawable.ic_launcher_foreground, "500,000", 0, 0),
        Lugar("Catarata", "Ubicación 2", R.drawable.ic_launcher_foreground, "450,000", 0, 0)
    )
    PantallaPrincipal(ActividadPrincipal(), lugaresEjemplo, "Bienvenida")
}
