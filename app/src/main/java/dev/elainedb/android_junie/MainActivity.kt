package dev.elainedb.android_junie

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import dev.elainedb.android_junie.data.VideoItem
import dev.elainedb.android_junie.ui.theme.AndroidJunieTheme

class MainActivity : ComponentActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            AndroidJunieTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(12.dp),
                        onLogout = { logout() }
                    )
                }
            }
        }
    }

    private fun logout() {
        googleSignInClient.signOut().addOnCompleteListener {
            navigateToLogin()
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    @Composable
    private fun MainScreen(modifier: Modifier = Modifier, onLogout: () -> Unit) {
        val vm: MainViewModel = viewModel()
        val state by vm.state.collectAsStateWithLifecycle()
        var showFilter by remember { mutableStateOf(false) }
        var showSort by remember { mutableStateOf(false) }

        Column(modifier = modifier) {
            val total = state.allVideos.size
            val visible = state.filteredSorted.size
            val titleSuffix = if (total > 0 && visible != total) " ($visible of $total)" else if (total > 0) " ($total)" else ""
            Text(
                text = "Latest Videos$titleSuffix",
                style = MaterialTheme.typography.titleLarge
            )
            val ctx = LocalContext.current
            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.refresh(true) }) { Text("Refresh") }
                Button(onClick = { showFilter = true }) { Text("Filter") }
                Button(onClick = { showSort = true }) { Text("Sort") }
                Button(onClick = { ctx.startActivity(Intent(ctx, MapActivity::class.java)) }) { Text("Map") }
            }
            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onLogout) { Text("Logout") }
            }

            when {
                state.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Loading...")
                    }
                }
                state.error != null -> {
                    Text(
                        text = "Error: ${state.error}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {
                    val list = state.filteredSorted
                    if (list.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("No videos found.")
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "If this persists, ensure a valid YouTube API key is set in config.properties or YOUTUBE_API_KEY env var.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        LazyColumn {
                            items(list) { video ->
                                VideoRow(video) { openYouTube(video.videoId) }
                                Divider()
                            }
                        }
                    }
                }
            }

            if (showFilter) {
                val channels = state.allVideos.map { it.channelTitle }.distinct().sorted()
                val countries = state.allVideos.mapNotNull { it.country }.distinct().sorted()
                AlertDialog(
                    onDismissRequest = { showFilter = false },
                    title = { Text("Filter Options") },
                    text = {
                        Column {
                            Text("Source Channel:")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { vm.setChannel(null) }) { Text("All") }
                            }
                            channels.forEach { ch ->
                                TextButton(onClick = { vm.setChannel(ch) }) { Text(ch) }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Country:")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { vm.setCountry(null) }) { Text("All") }
                            }
                            countries.forEach { c ->
                                TextButton(onClick = { vm.setCountry(c) }) { Text(c) }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showFilter = false }) { Text("Close") }
                    }
                )
            }

            if (showSort) {
                AlertDialog(
                    onDismissRequest = { showSort = false },
                    title = { Text("Sort Options") },
                    text = {
                        Column {
                            Text("By Publication Date:")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { vm.setSort(SortField.PUBLICATION_DATE, SortDir.DESC) }) { Text("Newest -> Oldest") }
                                TextButton(onClick = { vm.setSort(SortField.PUBLICATION_DATE, SortDir.ASC) }) { Text("Oldest -> Newest") }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("By Recording Date:")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { vm.setSort(SortField.RECORDING_DATE, SortDir.DESC) }) { Text("Newest -> Oldest") }
                                TextButton(onClick = { vm.setSort(SortField.RECORDING_DATE, SortDir.ASC) }) { Text("Oldest -> Newest") }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSort = false }) { Text("Close") }
                    }
                )
            }
        }
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

    @Composable
    private fun VideoRow(video: VideoItem, onClick: () -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { onClick() }
                .padding(vertical = 8.dp)
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = "Thumbnail",
                modifier = Modifier.size(96.dp)
            )
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = video.channelTitle,
                    style = MaterialTheme.typography.bodyMedium,
                )
                run {
                    val pub = video.publishedAt.take(10)
                    val rec = video.recordingDate?.take(10)
                    val datesLine = if (!rec.isNullOrBlank()) "Published: $pub • Recorded: $rec" else "Published: $pub"
                    Text(
                        text = datesLine,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                video.tags?.let { tags ->
                    if (tags.isNotEmpty()) {
                        Text(
                            text = "Tags: ${tags.joinToString()}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                val locParts = listOfNotNull(video.city, video.country).filter { it.isNotBlank() }
                if (locParts.isNotEmpty() || (video.latitude != null && video.longitude != null)) {
                    val coords = if (video.latitude != null && video.longitude != null) " (${video.latitude}, ${video.longitude})" else ""
                    Text(
                        text = "Location: ${locParts.joinToString(", ")}$coords",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainPreview() {
    AndroidJunieTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Latest Videos")
            Button(onClick = { /* preview */ }, modifier = Modifier.padding(top = 16.dp)) {
                Text("Logout")
            }
        }
    }
}