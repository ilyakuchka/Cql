package me.senkuro.unofficial.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Иконка «компас» (в базовом наборе material-icons её нет): круг и стрелка, контурная. */
val CompassIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Compass",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(22f, 12f)
            arcTo(10f, 10f, 0f, true, true, 2f, 12f)
            arcTo(10f, 10f, 0f, true, true, 22f, 12f)
            close()
            moveTo(16.24f, 7.76f)
            lineTo(14.12f, 14.12f)
            lineTo(7.76f, 16.24f)
            lineTo(9.88f, 9.88f)
            close()
        }
    }.build()
}
