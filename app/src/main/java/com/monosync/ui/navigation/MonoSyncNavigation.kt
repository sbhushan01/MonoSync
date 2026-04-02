package com.monosync.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.monosync.data.remote.YtmTrack
import com.monosync.model.Track
import com.monosync.ui.home.HomeScreen
import com.monosync.ui.library.LibraryScreen
import com.monosync.ui.player.NowPlayingScreenRoot
import com.monosync.ui.player.PlayerViewModel
import com.monosync.ui.search.SearchScreen
import com.monosync.ui.settings.SettingsScreen

data class NavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun MonoSyncNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val playerViewModel: PlayerViewModel = hiltViewModel()
    var selectedTab by remember { mutableIntStateOf(0) }

    val colorScheme = MaterialTheme.colorScheme

    val navItems = listOf(
        NavItem("Home", Icons.Filled.Home, Icons.Outlined.Home),
        NavItem("Search", Icons.Filled.Search, Icons.Outlined.Search),
        NavItem("Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
        NavItem("Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
    )

    // The NowPlayingScreen acts as a full-screen overlay with its own BottomSheetScaffold.
    // The main content (tabs) sits behind the bottom sheet.
    NowPlayingScreenRoot(
        viewModel = playerViewModel,
        mainContent = {
            Column(modifier = modifier.fillMaxSize()) {
                // Tab content
                Box(modifier = Modifier.weight(1f)) {
                    Crossfade(targetState = selectedTab, label = "tab_transition") { tab ->
                        when (tab) {
                            0 -> HomeScreen(onTrackClick = { track ->
                                playerViewModel.loadAndPlayTrack(
                                    ytmTrack = YtmTrack(
                                        videoId = track.id,
                                        title = track.title,
                                        artist = track.artist
                                    ),
                                    trackInfo = track
                                )
                            })
                            1 -> SearchScreen(onResultClick = { track ->
                                playerViewModel.loadAndPlayTrack(
                                    ytmTrack = YtmTrack(
                                        videoId = track.id,
                                        title = track.title,
                                        artist = track.artist
                                    ),
                                    trackInfo = track
                                )
                            })
                            2 -> LibraryScreen()
                            3 -> SettingsScreen()
                        }
                    }
                }

                // Bottom navigation bar
                NavigationBar(
                    containerColor = colorScheme.surface,
                    contentColor = colorScheme.onSurface,
                    tonalElevation = 0.dp
                ) {
                    navItems.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == index) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colorScheme.primary,
                                selectedTextColor = colorScheme.primary,
                                unselectedIconColor = colorScheme.onSurfaceVariant,
                                unselectedTextColor = colorScheme.onSurfaceVariant,
                                indicatorColor = colorScheme.primary.copy(alpha = 0.12f)
                            )
                        )
                    }
                }
            }
        }
    )
}
