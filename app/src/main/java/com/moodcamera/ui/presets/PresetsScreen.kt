package com.moodcamera.ui.presets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moodcamera.data.model.PresetEntity
import com.moodcamera.domain.model.EmulationType
import com.moodcamera.domain.model.EmulationCategory
import com.moodcamera.ui.theme.MoodAccent
import com.moodcamera.ui.theme.MoodBlack
import com.moodcamera.ui.theme.MoodOnSurfaceVariant
import com.moodcamera.ui.theme.MoodSurface
import com.moodcamera.ui.theme.MoodSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetsScreen(
    onNavigateBack: () -> Unit,
    viewModel: PresetsViewModel = hiltViewModel()
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
                    text = "Film Emulations",
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

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            // Original
            item {
                CategoryHeader("Original")
            }
            items(listOf(EmulationType.ORIGINAL)) { type ->
                EmulationItem(
                    emulationType = type,
                    isSelected = type == uiState.selectedEmulation,
                    onClick = { viewModel.selectEmulation(type) }
                )
            }

            // Filmic category
            item {
                CategoryHeader("Filmic")
            }
            items(EmulationType.entries.filter { it.category == EmulationCategory.FILMIC }) { type ->
                EmulationItem(
                    emulationType = type,
                    isSelected = type == uiState.selectedEmulation,
                    onClick = { viewModel.selectEmulation(type) }
                )
            }

            // Natural category
            item {
                CategoryHeader("Natural")
            }
            items(EmulationType.entries.filter { it.category == EmulationCategory.NATURAL }) { type ->
                EmulationItem(
                    emulationType = type,
                    isSelected = type == uiState.selectedEmulation,
                    onClick = { viewModel.selectEmulation(type) }
                )
            }

            // Stylistic category
            item {
                CategoryHeader("Stylistic")
            }
            items(EmulationType.entries.filter { it.category == EmulationCategory.STYLISTIC }) { type ->
                EmulationItem(
                    emulationType = type,
                    isSelected = type == uiState.selectedEmulation,
                    onClick = { viewModel.selectEmulation(type) }
                )
            }
        }
    }
}

@Composable
private fun CategoryHeader(name: String) {
    Text(
        text = name,
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
private fun EmulationItem(
    emulationType: EmulationType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isSelected) MoodAccent.copy(alpha = 0.1f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = emulationType.displayName,
                color = if (isSelected) MoodAccent else Color.White,
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = emulationType.description,
                color = MoodOnSurfaceVariant,
                fontSize = 12.sp
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = MoodAccent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
