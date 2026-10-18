package com.mosu.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mosu.app.R
import com.mosu.app.domain.model.getStarRatingColor
import java.util.Locale

@Composable
internal fun GradeBadge(grade: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = gradeBgColor(grade),
        contentColor = gradeTextColor(grade)
    ) {
        Text(
            text = displayGrade(grade).uppercase(Locale.US),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
internal fun StarRatingBadge(stars: Float?) {
    val color = stars?.let { getStarRatingColor(it) } ?: MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (stars == null) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else if (color.luminance() > 0.55f) {
        Color.Black
    } else {
        Color.White
    }

    Surface(
        shape = RoundedCornerShape(5.dp),
        color = color,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stars?.let { "%.1f".format(Locale.US, it) } ?: "--",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = stringResource(R.string.score_cd_star_rating),
                modifier = Modifier.size(10.dp)
            )
        }
    }
}

internal fun gradeBgColor(grade: String): Color {
    return when (grade) {
        "XH", "X" -> Color(0xFFD120A1)
        "SH","S" -> Color(0xFF02B5C3)
        "A" -> Color(0xFF66BB6A)
        "B" -> Color(0xFFEBBD48)
        "C" -> Color(0xFFF18252)
        "D" -> Color(0xFFDE4949)
        else -> Color(0xFF000000)
    }
}

internal fun gradeTextColor(grade: String): Color {
    return when (grade) {
        "XH" -> Color(0xFFD8D8D8)
        "X" -> Color(0xFFFFD15A)
        "SH" -> Color(0xFFD8D8D8)
        "S" -> Color(0xFFFFD15A)
        "A" -> Color(0xFF275227)
        "B" -> Color(0xFF553a2b)
        "C" -> Color(0xFF473625)
        "D" -> Color(0xFF512525)
        else -> Color(0xFFFFFFFF)
    }
}

internal fun displayGrade(rank: String): String {
    return when (rank.uppercase(Locale.US)) {
        "XH" -> "SS"
        "X" -> "SS"
        "SH" -> "S"
        else -> rank.uppercase(Locale.US)
    }
}

internal fun Color.luminance(): Float {
    return (0.299f * red + 0.587f * green + 0.114f * blue)
}

internal fun formatLength(seconds: Int?): String {
    if (seconds == null || seconds <= 0) return "--:--"
    val minutes = seconds / 60
    val remaining = seconds % 60
    return "%d:%02d".format(Locale.US, minutes, remaining)
}
