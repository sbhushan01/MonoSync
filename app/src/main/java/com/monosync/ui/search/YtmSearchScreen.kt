package com.monosync.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun YtmSearchScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val tracks by viewModel.ytmTracks.collectAsState()
    val query by viewModel.searchQuery.collectAsState()

    Column(modifier.padding(16.dp)) {
        Row {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::updateSearchQuery,
                label = { Text("YouTube Music") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = viewModel::searchYtmMusic, modifier = Modifier.align(Alignment.CenterVertically)) {
                Text("Search")
            }
        }
        
        Spacer(modifier = Modifier.padding(8.dp))

        LazyColumn {
            items(tracks) { track ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { /* play track */ }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = track.thumbnail,
                            contentDescription = track.title,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(track.title, style = MaterialTheme.typography.titleMedium)
                            Text(track.artist, style = MaterialTheme.typography.bodyMedium)
                            Text(track.duration, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
