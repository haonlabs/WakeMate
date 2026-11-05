package id.haonlabs.wakemate.presentation.live

import android.graphics.RectF
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.haonlabs.wakemate.domain.live.NearbyMode
import id.haonlabs.wakemate.domain.live.NearbyStop
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val RING = "radius"
private const val STOPS = "stops"
private const val USER_HALO = "user-halo"
private const val USER = "user"

private const val METERS_PER_DEGREE = 111_194.93

/** Warna penanda per moda: sama dengan blok moda di layar Bangunin dan desain Figma. */
private val MODE_COLORS = mapOf(
    NearbyMode.BUS to 0xFFFF5A4D.toInt(),
    NearbyMode.KRL to 0xFFFF9A3D.toInt(),
    NearbyMode.MRT to 0xFF7C93FF.toInt(),
    NearbyMode.LRT to 0xFFFFD21F.toInt(),
)
/** Warna penanda moda sebagai ARGB, dipakai juga oleh blok moda di daftar. */
internal fun NearbyMode.argb(): Int = MODE_COLORS.getValue(this)

private const val USER_COLOR = 0xFF7C93FF.toInt()

/**
 * Peta "Sekitar Saya": posisi user, lingkaran radius pencarian, dan halte/stasiun terdekat. Mengetuk
 * penanda memanggil [onStopClick]. Kamera dipaskan ke lingkaran radius tiap lokasi/radius/[recenterToken] berubah.
 */
@Composable
fun NearbyMap(
    userLat: Double?,
    userLng: Double?,
    radiusMeters: Int,
    stops: List<NearbyStop>,
    selectedId: String?,
    borderColor: Int,
    onStopClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Ruang di bawah yang tertutup bottom sheet. */
    bottomPadding: Dp = 0.dp,
    recenterToken: Long = 0,
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val padPx = with(density) { 32.dp.roundToPx() }
    val bottomPx = with(density) { bottomPadding.roundToPx() } + padPx
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val currentOnStopClick by rememberUpdatedState(onStopClick)

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView, dark) {
        mapView.getMapAsync { m ->
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isCompassEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            val firstTime = map == null
            style = null
            m.setStyle(Style.Builder().fromUri(if (dark) STYLE_DARK else STYLE_LIGHT)) { loaded ->
                addNearbyLayers(loaded, borderColor)
                map = m
                style = loaded
            }
            if (!firstTime) return@getMapAsync // pendengar klik cukup dipasang sekali
            m.addOnMapClickListener { point ->
                val screen = m.projection.toScreenLocation(point)
                val hit = m.queryRenderedFeatures(RectF(screen.x - 32, screen.y - 32, screen.x + 32, screen.y + 32), STOPS).firstOrNull()
                hit?.getStringProperty("id")?.let(currentOnStopClick)
                hit != null
            }
        }
    }

    LaunchedEffect(style, stops, selectedId) {
        val s = style ?: return@LaunchedEffect
        s.getSourceAs<GeoJsonSource>(STOPS)?.setGeoJson(
            FeatureCollection.fromFeatures(stops.map { stop ->
                Feature.fromGeometry(Point.fromLngLat(stop.halte.lng, stop.halte.lat)).apply {
                    addStringProperty("id", stop.halte.id)
                    addStringProperty("mode", stop.mode.name)
                    addBooleanProperty("sel", stop.halte.id == selectedId)
                }
            })
        )
    }

    LaunchedEffect(style, userLat, userLng, radiusMeters) {
        val s = style ?: return@LaunchedEffect
        if (userLat == null || userLng == null) return@LaunchedEffect
        s.getSourceAs<GeoJsonSource>(USER)?.setGeoJson(Point.fromLngLat(userLng, userLat))
        s.getSourceAs<GeoJsonSource>(USER_HALO)?.setGeoJson(Point.fromLngLat(userLng, userLat))
        s.getSourceAs<GeoJsonSource>(RING)?.setGeoJson(ringAround(userLat, userLng, radiusMeters.toDouble()))
    }

    LaunchedEffect(style, userLat, userLng, radiusMeters, recenterToken, bottomPx) {
        if (style == null || userLat == null || userLng == null) return@LaunchedEffect
        val dLat = radiusMeters / METERS_PER_DEGREE
        val dLng = dLat / cos(userLat * PI / 180.0)
        val bounds = LatLngBounds.Builder()
            .include(LatLng(userLat - dLat, userLng - dLng))
            .include(LatLng(userLat + dLat, userLng + dLng))
            .build()
        map?.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, padPx, padPx * 2, padPx, bottomPx), 600)
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/** Cincin radius sebagai garis tertutup (64 titik) — cukup halus dan tidak butuh lapisan fill. */
private fun ringAround(lat: Double, lng: Double, radiusMeters: Double): LineString {
    val dLat = radiusMeters / METERS_PER_DEGREE
    val dLng = dLat / cos(lat * PI / 180.0)
    val points = (0..64).map { i ->
        val a = 2 * PI * i / 64
        Point.fromLngLat(lng + dLng * cos(a), lat + dLat * sin(a))
    }
    return LineString.fromLngLats(points)
}

private fun addNearbyLayers(style: Style, borderColor: Int) {
    style.addSource(GeoJsonSource(RING))
    style.addSource(GeoJsonSource(STOPS))
    style.addSource(GeoJsonSource(USER_HALO))
    style.addSource(GeoJsonSource(USER))
    style.addLayer(
        LineLayer(RING, RING).withProperties(
            PropertyFactory.lineWidth(2f),
            PropertyFactory.lineColor(borderColor),
            PropertyFactory.lineOpacity(0.45f),
            PropertyFactory.lineDasharray(arrayOf(3f, 3f)),
        )
    )
    style.addLayer(
        CircleLayer(USER_HALO, USER_HALO).withProperties(
            PropertyFactory.circleRadius(26f),
            PropertyFactory.circleColor(USER_COLOR),
            PropertyFactory.circleOpacity(0.3f),
        )
    )
    val byMode = Expression.switchCase(
        Expression.eq(Expression.get("mode"), Expression.literal("KRL")), Expression.color(MODE_COLORS.getValue(NearbyMode.KRL)),
        Expression.eq(Expression.get("mode"), Expression.literal("MRT")), Expression.color(MODE_COLORS.getValue(NearbyMode.MRT)),
        Expression.eq(Expression.get("mode"), Expression.literal("LRT")), Expression.color(MODE_COLORS.getValue(NearbyMode.LRT)),
        Expression.color(MODE_COLORS.getValue(NearbyMode.BUS)),
    )
    style.addLayer(
        CircleLayer(STOPS, STOPS).withProperties(
            PropertyFactory.circleRadius(Expression.switchCase(Expression.get("sel"), Expression.literal(13f), Expression.literal(9f))),
            PropertyFactory.circleColor(byMode),
            PropertyFactory.circleStrokeColor(borderColor),
            PropertyFactory.circleStrokeWidth(Expression.switchCase(Expression.get("sel"), Expression.literal(4f), Expression.literal(2.5f))),
        )
    )
    style.addLayer(
        CircleLayer(USER, USER).withProperties(
            PropertyFactory.circleRadius(9f),
            PropertyFactory.circleColor(USER_COLOR),
            PropertyFactory.circleStrokeColor(0xFFFFFFFF.toInt()),
            PropertyFactory.circleStrokeWidth(4f),
        )
    )
}
