package com.jarvis.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val JarvisColorScheme = lightColorScheme(
    primary = JarvisBlue,
    onPrimary = JarvisWhite,
    secondary = JarvisBlueLight,
    background = JarvisWhite,
    onBackground = JarvisText,
    surface = JarvisWhite,
    onSurface = JarvisText,
)

private val JarvisTypography = Typography(
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 17.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
)

@Composable
fun JarvisTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = JarvisColorScheme,
        typography = JarvisTypography,
        content = content,
    )
}
