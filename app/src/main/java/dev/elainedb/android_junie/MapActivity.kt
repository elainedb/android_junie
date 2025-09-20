package dev.elainedb.android_junie

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage

// For BottomSheetDialog content using Android Views
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import coil.load
import dev.elainedb.android_junie.data.VideoItem
import dev.elainedb.android_junie.data.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MapActivity : ComponentActivity() {

    private lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Required by osmdroid for proper caching behavior
        Configuration.getInstance().userAgentValue = packageName

        mapView = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 2.0
        }

        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(mapView)
        }

        setContentView(root)

        // Load markers from cached DB
        lifecycleScope.launch {
            val repo = VideoRepository(this@MapActivity)
            val videos = repo.getVideosWithCoordinatesFromCache()
            addMarkers(videos)
            fitToMarkers(videos)
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    private fun addMarkers(videos: List<VideoItem>) {
        for (v in videos) {
            val lat = v.latitude ?: continue
            val lon = v.longitude ?: continue
            val marker = Marker(mapView).apply {
                position = GeoPoint(lat, lon)
                title = v.title
                setOnMarkerClickListener { m, _ ->
                    showVideoBottomSheet(v)
                    true
                }
            }
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
    }

    private fun fitToMarkers(videos: List<VideoItem>) {
        val points = videos.mapNotNull { v ->
            val lat = v.latitude
            val lon = v.longitude
            if (lat != null && lon != null) GeoPoint(lat, lon) else null
        }
        if (points.isEmpty()) return
        val bbox: BoundingBox = BoundingBox.fromGeoPointsSafe(points)
        mapView.post {
            mapView.zoomToBoundingBox(bbox, true, 64)
        }
    }

    private fun showVideoBottomSheet(video: VideoItem) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)

        // Build a simple Bottom Sheet using classic Android Views to avoid Compose lifecycle requirements
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setOnClickListener {
                openYouTube(video.videoId)
                dialog.dismiss()
            }
        }

        val imageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(120)
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            load(video.thumbnailUrl)
        }
        container.addView(imageView)

        fun addText(text: String, topMarginDp: Int = 8, style: Int = android.R.style.TextAppearance_Material_Body1) {
            val tv = TextView(this@MapActivity).apply {
                this.text = text
                setTextAppearance(style)
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(topMarginDp)
            tv.layoutParams = lp
            container.addView(tv)
        }

        addText(video.title, topMarginDp = 8, style = android.R.style.TextAppearance_Material_Title)
        addText(video.channelTitle, topMarginDp = 4, style = android.R.style.TextAppearance_Material_Body1)

        run {
            val pub = video.publishedAt.take(10)
            val rec = video.recordingDate?.take(10)
            val datesLine = if (!rec.isNullOrBlank()) "Published: $pub • Recorded: $rec" else "Published: $pub"
            addText(datesLine, topMarginDp = 4)
        }

        video.tags?.let { tags ->
            if (tags.isNotEmpty()) {
                addText("Tags: ${tags.joinToString()}", topMarginDp = 4)
            }
        }

        run {
            val locParts = listOfNotNull(video.city, video.country).filter { it.isNotBlank() }
            val coords = if (video.latitude != null && video.longitude != null) " (${video.latitude}, ${video.longitude})" else ""
            if (locParts.isNotEmpty() || coords.isNotEmpty()) {
                addText("Location: ${locParts.joinToString(", ")}$coords", topMarginDp = 4)
            }
        }

        val openButton = android.widget.Button(this).apply {
            text = "Open in YouTube"
            setOnClickListener {
                openYouTube(video.videoId)
                dialog.dismiss()
            }
        }
        val btnLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        btnLp.topMargin = dp(8)
        openButton.layoutParams = btnLp
        container.addView(openButton)

        dialog.setContentView(container)
        dialog.behavior.maxHeight = (resources.displayMetrics.heightPixels * 0.25f).toInt()
        dialog.show()
    }

    private fun openYouTube(videoId: String) {
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId"))
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId"))
        try {
            startActivity(appIntent)
        } catch (e: Exception) {
            startActivity(webIntent)
        }
    }
}

@Composable
private fun BottomSheetContent(video: VideoItem, onOpen: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = "Thumbnail",
            modifier = Modifier.fillMaxWidth().height(120.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(text = video.title, style = MaterialTheme.typography.titleMedium)
        Text(text = video.channelTitle, style = MaterialTheme.typography.bodyMedium)
        run {
            val pub = video.publishedAt.take(10)
            val rec = video.recordingDate?.take(10)
            val datesLine = if (!rec.isNullOrBlank()) "Published: $pub • Recorded: $rec" else "Published: $pub"
            Text(text = datesLine, style = MaterialTheme.typography.bodySmall)
        }
        video.tags?.let { tags ->
            if (tags.isNotEmpty()) {
                Text(text = "Tags: ${tags.joinToString()}", style = MaterialTheme.typography.bodySmall)
            }
        }
        val locParts = listOfNotNull(video.city, video.country).filter { it.isNotBlank() }
        val coords = if (video.latitude != null && video.longitude != null) " (${video.latitude}, ${video.longitude})" else ""
        if (locParts.isNotEmpty() || coords.isNotEmpty()) {
            Text(text = "Location: ${locParts.joinToString(", ")}$coords", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
            Text("Open in YouTube")
        }
    }
}
