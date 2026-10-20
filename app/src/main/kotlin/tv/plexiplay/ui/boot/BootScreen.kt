package tv.plexiplay.ui.boot

import tv.plexiplay.ui.theme.screenBackground
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.plexiplay.R
import tv.plexiplay.ui.theme.Ink
import tv.plexiplay.ui.theme.Paper

@Composable
fun BootScreen(progress: Float, versionName: String) {
    val fraction = progress.coerceIn(0f, 1f)
    val percent = (fraction * 100).toInt()
    Box(Modifier.fillMaxSize().screenBackground(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(R.drawable.logo),
                contentDescription = "Plexiplay",
                modifier = Modifier.size(240.dp).clip(RoundedCornerShape(36.dp)),
                contentScale = ContentScale.Crop,
            )
            Text(
                "Version $versionName",
                color = Color(0xFF9AA3AD),
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 22.dp),
            )
            Box(
                Modifier
                    .padding(top = 22.dp)
                    .width(280.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.16f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .background(Paper),
                )
            }
            Text(
                "$percent %",
                color = Paper,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
