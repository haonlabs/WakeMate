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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import id.haonlabs.wakemate.domain.live.Halte
import id.haonlabs.wakemate.domain.live.LiveBus
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
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Style gratis tanpa API key, berbasis data OpenStreetMap; gelap/terang mengikuti tema. */
private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"
private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/positron"

/** Permintaan memindah kamera; [id] baru memicu ulang walau koordinatnya sama (tombol lokasiku). */
data class CameraTarget(val lat: Double, val lng: Double, val zoom: Double = 15.0, val id: Long = 0)

private const val ROUTE = "route"
private const val HALTE = "halte"
private const val BUSES = "buses"

/**
 * Peta satu rute: garis rute arah terpilih, halte, dan bus live. Bus di arah lain digambar redup
 * supaya tetap terlihat tanpa mengaburkan arah yang sedang dilihat.
 */
@Composable
fun LiveMap(
    shape: String,
    halte: List<Halte>,
    buses: List<LiveBus>,
    activeTripIds: Set<String>,
    selectedBusNo: String?,
    routeColor: Int,
    onBusClick: (String?) -> Unit,
    modifier: Modifier = Modifier,
    /** Ruang di bawah yang tertutup bottom sheet; rute dipaskan di atasnya. */
    bottomPadding: Dp = 0.dp,
    target: CameraTarget? = null,
    interactive: Boolean = true,
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val padPx = with(density) { 48.dp.roundToPx() }
    val bottomPx = with(density) { bottomPadding.roundToPx() } + padPx
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val currentOnBusClick by rememberUpdatedState(onBusClick)

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
            m.uiSettings.setAllGesturesEnabled(interactive)
            style = null
            m.setStyle(Style.Builder().fromUri(if (dark) STYLE_DARK else STYLE_LIGHT)) { loaded ->
                addLayers(loaded)
                map = m
                style = loaded
            }
            if (map != null) return@getMapAsync // pendengar klik cukup dipasang sekali
            m.addOnMapClickListener { point ->
                val screen = m.projection.toScreenLocation(point)
                val hit = m.queryRenderedFeatures(
                    RectF(screen.x - 28, screen.y - 28, screen.x + 28, screen.y + 28),
                    BUSES,
                ).firstOrNull()
                currentOnBusClick(hit?.getStringProperty("no"))
                true
            }
        }
    }

    LaunchedEffect(style, shape, halte, routeColor, bottomPx) {
        val s = style ?: return@LaunchedEffect
        val line = shape.takeIf { it.isNotEmpty() }?.let { LineString.fromPolyline(it, 5) }
        s.getSourceAs<GeoJsonSource>(ROUTE)?.setGeoJson(
            FeatureCollection.fromFeatures(listOfNotNull(line?.let(Feature::fromGeometry)))
        )
        s.getSourceAs<GeoJsonSource>(HALTE)?.setGeoJson(
            FeatureCollection.fromFeatures(halte.map { Feature.fromGeometry(Point.fromLngLat(it.lng, it.lat)) })
        )
        s.getLayer(ROUTE)?.setProperties(PropertyFactory.lineColor(routeColor))
        s.getLayer(HALTE)?.setProperties(PropertyFactory.circleStrokeColor(routeColor))
        s.getLayer(BUSES)?.setProperties(PropertyFactory.circleColor(routeColor))

        val points = line?.coordinates().orEmpty().map { LatLng(it.latitude(), it.longitude()) }
            .ifEmpty { halte.map { LatLng(it.lat, it.lng) } }
        if (target == null && points.size >= 2) {
            map?.easeCamera(
                CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points).build(), padPx, padPx * 2, padPx, bottomPx),
                600,
            )
        }
    }

    LaunchedEffect(style, target) {
        val t = target ?: return@LaunchedEffect
        if (style == null) return@LaunchedEffect
        map?.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(t.lat, t.lng), t.zoom), 500)
    }

    LaunchedEffect(style, buses, selectedBusNo, activeTripIds) {
        val s = style ?: return@LaunchedEffect
        s.getSourceAs<GeoJsonSource>(BUSES)?.setGeoJson(
            FeatureCollection.fromFeatures(
                buses.map { bus ->
                    Feature.fromGeometry(Point.fromLngLat(bus.lng, bus.lat)).apply {
                        addStringProperty("no", bus.busNo)
                        addBooleanProperty("sel", bus.busNo == selectedBusNo)
                        addBooleanProperty("dim", activeTripIds.isNotEmpty() && bus.tripId !in activeTripIds)
                    }
                }
            )
        )
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun addLayers(style: Style) {
    style.addSource(GeoJsonSource(ROUTE))
    style.addSource(GeoJsonSource(HALTE))
    style.addSource(GeoJsonSource(BUSES))
    style.addLayer(
        LineLayer(ROUTE, ROUTE).withProperties(
            PropertyFactory.lineWidth(4f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineOpacity(0.85f),
        )
    )
    style.addLayer(
        CircleLayer(HALTE, HALTE).withProperties(
            PropertyFactory.circleRadius(3.5f),
            PropertyFactory.circleColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(1.5f),
        )
    )
    style.addLayer(
        CircleLayer(BUSES, BUSES).withProperties(
            PropertyFactory.circleRadius(
                Expression.switchCase(Expression.get("sel"), Expression.literal(10f), Expression.literal(7f))
            ),
            PropertyFactory.circleOpacity(
                Expression.switchCase(Expression.get("dim"), Expression.literal(0.35f), Expression.literal(1f))
            ),
            PropertyFactory.circleStrokeColor(
                Expression.switchCase(Expression.get("sel"), Expression.color(0xFFFFB95F.toInt()), Expression.color(0xFFFFFFFF.toInt()))
            ),
            PropertyFactory.circleStrokeWidth(
                Expression.switchCase(Expression.get("sel"), Expression.literal(3f), Expression.literal(2f))
            ),
        )
    )
}
