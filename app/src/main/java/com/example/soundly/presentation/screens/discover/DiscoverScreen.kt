package com.example.soundly.presentation.screens.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.soundly.presentation.theme.backgroundGradient
import com.example.soundly.presentation.theme.LocalColorPalette

@Composable
fun DiscoverScreen(navController: NavController) {
    val palette = LocalColorPalette.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient())
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Открыть",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item { Spacer(modifier = Modifier.height(60.dp)) }

        // AI Icon with glass effect
        item {
            Surface(
                modifier = Modifier.size(120.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0x40FFFFFF)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }

        // Title
        item {
            Text(
                text = "Персональные рекомендации",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = palette.textPrimary
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        // Subtitle
        item {
            Text(
                text = "Скоро здесь появятся умные рекомендации\nна основе вашей истории прослушивания",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
                textAlign = TextAlign.Center
            )
        }

        item { Spacer(modifier = Modifier.height(40.dp)) }

        // Features list
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FeatureRow(icon = Icons.Default.AutoAwesome, text = "Анализ ваших музыкальных предпочтений", textColor = palette.textPrimary)
                FeatureRow(icon = Icons.Default.AutoAwesome, text = "Подбор похожих треков и артистов", textColor = palette.textPrimary)
                FeatureRow(icon = Icons.Default.AutoAwesome, text = "Персональные плейлисты каждый день", textColor = palette.textPrimary)
            }
        }

        item { Spacer(modifier = Modifier.height(40.dp)) }

        // Coming soon button
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0x30FFFFFF)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🚀", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Скоро", 
                        style = MaterialTheme.typography.labelLarge, 
                        fontWeight = FontWeight.Medium,
                        color = palette.textPrimary
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(140.dp)) }
    }
}

@Composable
fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, textColor: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text, 
            style = MaterialTheme.typography.bodyMedium,
            color = textColor
        )
    }
}
