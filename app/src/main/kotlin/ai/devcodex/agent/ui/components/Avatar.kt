package ai.devcodex.agent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import java.io.File

/** 头像渲染：自定义图片（filesDir/avatars/）→ 回退首字圆标 */
@Composable
fun Avatar(
    name: String,
    avatarFile: String,
    defaultColor: androidx.compose.ui.graphics.Color,
    defaultIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    size: Dp = 32.dp,
) {
    val ctx = LocalContext.current
    val file = if (avatarFile.isNotBlank()) File(File(ctx.filesDir, "avatars"), avatarFile) else null
    if (file != null && file.exists()) {
        AsyncImage(
            model = file,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).background(androidx.compose.ui.graphics.Color.Transparent, CircleShape),
        )
    } else {
        Box(
            Modifier.size(size).background(defaultColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (defaultIcon != null) {
                Icon(defaultIcon, name, Modifier.size(size / 1.8f), tint = androidx.compose.ui.graphics.Color.White)
            } else {
                Text(
                    name.firstOrNull()?.toString() ?: "?",
                    style = androidx.compose.ui.text.TextStyle(fontSize = androidx.compose.ui.unit.TextUnit(size.value * 0.45f, androidx.compose.ui.unit.TextUnitType.Sp)),
                    color = androidx.compose.ui.graphics.Color.White,
                )
            }
        }
    }
}
