package com.moodcamera.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moodcamera.ui.theme.MoodAccent
import com.moodcamera.ui.theme.MoodBlack
import com.moodcamera.ui.theme.MoodOnSurfaceVariant
import com.moodcamera.ui.theme.MoodSurface
import com.moodcamera.ui.theme.MoodSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MoodBlack)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Settings",
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MoodSurface
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Image Settings
            SettingsSectionHeader("Image Settings")

            SettingsSwitchItem(
                title = "Film Grain",
                subtitle = "Add realistic film grain texture",
                checked = uiState.isGrainEnabled,
                onCheckedChange = { viewModel.toggleGrain() }
            )

            SettingsSwitchItem(
                title = "Halation",
                subtitle = "Glow effect on bright highlights",
                checked = uiState.isHalationEnabled,
                onCheckedChange = { viewModel.toggleHalation() }
            )

            SettingsSwitchItem(
                title = "Custom Frames",
                subtitle = "Auto-add decorative frames",
                checked = uiState.isFrameEnabled,
                onCheckedChange = { viewModel.toggleFrame() }
            )

            HorizontalDivider(color = MoodSurfaceVariant)

            // Camera Settings
            SettingsSectionHeader("Camera")

            SettingsSwitchItem(
                title = "Grid Overlay",
                subtitle = "Rule of thirds grid",
                checked = uiState.isGridEnabled,
                onCheckedChange = { viewModel.toggleGrid() }
            )

            SettingsSwitchItem(
                title = "Auto Filter",
                subtitle = "AI recommends best filter for scene",
                checked = uiState.isAutoFilterEnabled,
                onCheckedChange = { viewModel.toggleAutoFilter() }
            )

            HorizontalDivider(color = MoodSurfaceVariant)

            // About
            SettingsSectionHeader("About")

            SettingsTextItem(
                title = "Version",
                subtitle = "1.0.0"
            )

            SettingsTextItem(
                title = "Film Emulations",
                subtitle = "12 unique film stocks"
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        color = MoodAccent,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .background(MoodSurfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MoodSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp
        )
        Text(
            text = subtitle,
            color = MoodOnSurfaceVariant,
            fontSize = 12.sp
        )
    }

    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.padding(horizontal = 16.dp),
        colors = SwitchDefaults.colors(
            checkedThumbColor = MoodBlack,
            checkedTrackColor = MoodAccent,
            uncheckedThumbColor = MoodOnSurfaceVariant,
            uncheckedTrackColor = MoodSurfaceVariant
        )
    )

    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun SettingsTextItem(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MoodSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp
        )
        Text(
            text = subtitle,
            color = MoodOnSurfaceVariant,
            fontSize = 12.sp
        )
    }
}
