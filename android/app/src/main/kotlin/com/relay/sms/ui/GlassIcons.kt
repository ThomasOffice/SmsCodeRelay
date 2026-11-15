package com.relay.sms.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.unit.dp

/** Lucide 风格线性图标（与 Web 端一致，24x24，1.75px 描边）。 */
object GlassIcons {
    private fun icon(name: String, content: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = PathBuilder().apply(content).nodes,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx + r, cy)
        arcTo(r, r, 0f, false, true, cx - r, cy)
        arcTo(r, r, 0f, false, true, cx + r, cy)
    }

    private fun PathBuilder.rect(x: Float, y: Float, w: Float, h: Float) {
        moveTo(x, y)
        lineTo(x + w, y)
        lineTo(x + w, y + h)
        lineTo(x, y + h)
        close()
    }

    val Copy by lazy {
        icon("copy") {
            rect(9f, 9f, 13f, 13f)
            moveTo(5f, 15f)
            lineTo(4f, 15f)
            arcTo(2f, 2f, 0f, false, true, 2f, 13f)
            lineTo(2f, 4f)
            arcTo(2f, 2f, 0f, false, true, 4f, 2f)
            lineTo(13f, 2f)
            arcTo(2f, 2f, 0f, false, true, 15f, 4f)
            lineTo(15f, 5f)
        }
    }
    val Check by lazy { icon("check") { moveTo(20f, 6f); lineTo(9f, 17f); lineTo(4f, 12f) } }
    val X by lazy { icon("x") { moveTo(18f, 6f); lineTo(6f, 18f); moveTo(6f, 6f); lineTo(18f, 18f) } }
    val Minus by lazy { icon("minus") { moveTo(5f, 12f); lineTo(19f, 12f) } }
    val Stop by lazy { icon("stop") { rect(5f, 5f, 14f, 14f) } }
    val Play by lazy { icon("play") { moveTo(6f, 3f); lineTo(20f, 12f); lineTo(6f, 21f); close() } }
    val Send by lazy {
        icon("send") {
            moveTo(22f, 2f); lineTo(15f, 22f); lineTo(11f, 13f); lineTo(2f, 9f); close()
            moveTo(22f, 2f); lineTo(11f, 13f)
        }
    }
    val Refresh by lazy {
        icon("refresh") {
            moveTo(21f, 12f)
            arcTo(9f, 9f, 0f, true, true, 3f, 12f)
            arcTo(9f, 9f, 0f, false, true, 21f, 12f)
            moveTo(21f, 3f); lineTo(21f, 8f); lineTo(16f, 8f)
        }
    }
    val Settings by lazy {
        icon("settings") {
            moveTo(12.22f, 2f)
            lineTo(11.78f, 2f)
            arcTo(2f, 2f, 0f, false, false, 9.78f, 4f)
            lineTo(9.78f, 4.18f)
            arcTo(2f, 2f, 0f, false, true, 8.78f, 5.91f)
            lineTo(8.35f, 6.16f)
            arcTo(2f, 2f, 0f, false, true, 6.35f, 6.16f)
            lineTo(6.2f, 6.08f)
            arcTo(2f, 2f, 0f, false, false, 3.47f, 6.81f)
            lineTo(3.25f, 7.19f)
            arcTo(2f, 2f, 0f, false, false, 3.98f, 9.92f)
            lineTo(4.13f, 10.02f)
            arcTo(2f, 2f, 0f, false, true, 5.13f, 11.74f)
            lineTo(5.13f, 12.25f)
            arcTo(2f, 2f, 0f, false, true, 4.13f, 13.99f)
            lineTo(3.98f, 14.08f)
            arcTo(2f, 2f, 0f, false, false, 3.25f, 16.81f)
            lineTo(3.47f, 17.19f)
            arcTo(2f, 2f, 0f, false, false, 6.2f, 17.92f)
            lineTo(6.35f, 17.84f)
            arcTo(2f, 2f, 0f, false, true, 8.35f, 17.84f)
            lineTo(8.78f, 18.09f)
            arcTo(2f, 2f, 0f, false, true, 9.78f, 19.82f)
            lineTo(9.78f, 20f)
            arcTo(2f, 2f, 0f, false, false, 11.78f, 22f)
            lineTo(12.22f, 22f)
            arcTo(2f, 2f, 0f, false, false, 14.22f, 20f)
            lineTo(14.22f, 19.82f)
            arcTo(2f, 2f, 0f, false, true, 15.22f, 18.09f)
            lineTo(15.65f, 17.84f)
            arcTo(2f, 2f, 0f, false, true, 17.65f, 17.84f)
            lineTo(17.8f, 17.92f)
            arcTo(2f, 2f, 0f, false, false, 20.53f, 17.19f)
            lineTo(20.75f, 16.8f)
            arcTo(2f, 2f, 0f, false, false, 20.02f, 14.07f)
            lineTo(19.87f, 13.99f)
            arcTo(2f, 2f, 0f, false, true, 18.87f, 12.25f)
            lineTo(18.87f, 11.75f)
            arcTo(2f, 2f, 0f, false, true, 19.87f, 10.01f)
            lineTo(20.02f, 9.92f)
            arcTo(2f, 2f, 0f, false, false, 20.75f, 7.19f)
            lineTo(20.53f, 6.81f)
            arcTo(2f, 2f, 0f, false, false, 17.8f, 6.08f)
            lineTo(17.65f, 6.16f)
            arcTo(2f, 2f, 0f, false, true, 15.65f, 6.16f)
            lineTo(15.22f, 5.91f)
            arcTo(2f, 2f, 0f, false, true, 14.22f, 4.18f)
            lineTo(14.22f, 4f)
            arcTo(2f, 2f, 0f, false, false, 12.22f, 2f)
            circle(12f, 12f, 3f)
        }
    }
    val Sun by lazy {
        icon("sun") {
            circle(12f, 12f, 4f)
            moveTo(12f, 2f); lineTo(12f, 4f)
            moveTo(12f, 20f); lineTo(12f, 22f)
            moveTo(4.93f, 4.93f); lineTo(6.34f, 6.34f)
            moveTo(17.66f, 17.66f); lineTo(19.07f, 19.07f)
            moveTo(2f, 12f); lineTo(4f, 12f)
            moveTo(20f, 12f); lineTo(22f, 12f)
            moveTo(6.34f, 17.66f); lineTo(4.93f, 19.07f)
            moveTo(19.07f, 4.93f); lineTo(17.66f, 6.34f)
        }
    }
    val Moon by lazy {
        icon("moon") {
            moveTo(12f, 3f)
            arcTo(6f, 6f, 0f, false, false, 21f, 12f)
            arcTo(9f, 9f, 0f, true, true, 12f, 3f)
        }
    }
    val Mail by lazy {
        icon("mail") {
            rect(2f, 4f, 20f, 16f)
            moveTo(22f, 7f)
            lineTo(13.03f, 12.7f)
            arcTo(1.94f, 1.94f, 0f, false, true, 10.97f, 12.7f)
            lineTo(2f, 7f)
        }
    }
    val Shield by lazy {
        icon("shield") {
            moveTo(20f, 13f)
            curveTo(20f, 18f, 16.5f, 20.5f, 12.34f, 21.95f)
            arcTo(1f, 1f, 0f, false, true, 11.67f, 21.94f)
            curveTo(7.5f, 20.5f, 4f, 18f, 4f, 13f)
            lineTo(4f, 6f)
            arcTo(1f, 1f, 0f, false, true, 5f, 5f)
            curveTo(7f, 5f, 9.5f, 3.8f, 11.24f, 2.28f)
            arcTo(1f, 1f, 0f, false, true, 12.76f, 2.28f)
            curveTo(14.51f, 3.81f, 17f, 5f, 19f, 5f)
            arcTo(1f, 1f, 0f, false, true, 20f, 6f)
            close()
            moveTo(9f, 12f); lineTo(11f, 14f); lineTo(15f, 10f)
        }
    }
    val History by lazy {
        icon("history") {
            moveTo(3f, 12f)
            arcTo(9f, 9f, 0f, true, false, 12f, 3f)
            arcTo(9.75f, 9.75f, 0f, false, false, 5.26f, 5.74f)
            lineTo(3f, 8f)
            moveTo(3f, 3f); lineTo(3f, 8f); lineTo(8f, 8f)
            moveTo(12f, 7f); lineTo(12f, 12f); lineTo(16f, 14f)
        }
    }
    val Inbox by lazy {
        icon("inbox") {
            moveTo(22f, 12f); lineTo(16f, 12f); lineTo(14f, 15f); lineTo(10f, 15f); lineTo(8f, 12f); lineTo(2f, 12f)
            moveTo(5.45f, 5.11f)
            lineTo(2f, 12f)
            lineTo(2f, 18f)
            arcTo(2f, 2f, 0f, false, false, 4f, 20f)
            lineTo(20f, 20f)
            arcTo(2f, 2f, 0f, false, false, 22f, 18f)
            lineTo(22f, 12f)
            lineTo(18.55f, 5.11f)
            arcTo(2f, 2f, 0f, false, false, 16.76f, 4f)
            lineTo(7.24f, 4f)
            arcTo(2f, 2f, 0f, false, false, 5.45f, 5.11f)
            close()
        }
    }
    val Eye by lazy {
        icon("eye") {
            moveTo(2.06f, 12.35f)
            arcTo(1f, 1f, 0f, false, true, 2.06f, 11.65f)
            arcTo(10.75f, 10.75f, 0f, false, true, 21.94f, 11.65f)
            arcTo(1f, 1f, 0f, false, true, 21.94f, 12.35f)
            arcTo(10.75f, 10.75f, 0f, false, true, 2.06f, 12.35f)
            circle(12f, 12f, 3f)
        }
    }
    val EyeOff by lazy {
        icon("eye-off") {
            moveTo(2f, 2f); lineTo(22f, 22f)
            moveTo(6.71f, 6.71f)
            arcTo(10.75f, 10.75f, 0f, false, false, 2.06f, 12.35f)
            arcTo(1f, 1f, 0f, false, false, 2.06f, 13.05f)
            arcTo(10.75f, 10.75f, 0f, false, false, 16.35f, 18.34f)
            moveTo(10.73f, 10.73f)
            arcTo(3f, 3f, 0f, false, false, 14.97f, 14.97f)
            moveTo(9.5f, 5.5f)
            arcTo(10.75f, 10.75f, 0f, false, true, 21.94f, 11.65f)
            arcTo(1f, 1f, 0f, false, true, 21.94f, 12.35f)
            arcTo(10.75f, 10.75f, 0f, false, true, 20.5f, 14.85f)
        }
    }
    val Save by lazy {
        icon("save") {
            moveTo(19f, 21f)
            lineTo(5f, 21f)
            arcTo(2f, 2f, 0f, false, true, 3f, 19f)
            lineTo(3f, 5f)
            arcTo(2f, 2f, 0f, false, true, 5f, 3f)
            lineTo(16f, 3f)
            lineTo(21f, 8f)
            lineTo(21f, 19f)
            arcTo(2f, 2f, 0f, false, true, 19f, 21f)
            close()
            moveTo(17f, 21f); lineTo(17f, 13f); lineTo(7f, 13f); lineTo(7f, 21f)
            moveTo(7f, 3f); lineTo(7f, 8f); lineTo(15f, 8f)
        }
    }
    val Zap by lazy {
        icon("zap") {
            moveTo(4f, 14f)
            arcTo(1f, 1f, 0f, false, true, 3.22f, 12.37f)
            lineTo(13.12f, 2.17f)
            arcTo(0.5f, 0.5f, 0f, false, true, 13.98f, 2.63f)
            lineTo(12.06f, 8.65f)
            arcTo(1f, 1f, 0f, false, false, 13f, 10f)
            lineTo(20f, 10f)
            arcTo(1f, 1f, 0f, false, true, 20.78f, 11.63f)
            lineTo(10.88f, 21.83f)
            arcTo(0.5f, 0.5f, 0f, false, true, 10.02f, 21.37f)
            lineTo(11.94f, 15.35f)
            arcTo(1f, 1f, 0f, false, false, 11f, 14f)
            close()
        }
    }
    val Bell by lazy {
        icon("bell") {
            moveTo(6f, 8f)
            arcTo(6f, 6f, 0f, false, true, 18f, 8f)
            curveTo(18f, 15f, 21f, 17f, 21f, 17f)
            lineTo(3f, 17f)
            curveTo(3f, 17f, 6f, 15f, 6f, 8f)
            moveTo(10.3f, 21f)
            arcTo(1.94f, 1.94f, 0f, false, false, 13.7f, 21f)
        }
    }
    val List by lazy {
        icon("list") {
            moveTo(8f, 6f); lineTo(21f, 6f)
            moveTo(8f, 12f); lineTo(21f, 12f)
            moveTo(8f, 18f); lineTo(21f, 18f)
            circle(3f, 6f, 0.5f)
            circle(3f, 12f, 0.5f)
            circle(3f, 18f, 0.5f)
        }
    }
    val Trash by lazy {
        icon("trash") {
            moveTo(3f, 6f); lineTo(21f, 6f)
            moveTo(8f, 6f); lineTo(8f, 4f)
            arcTo(2f, 2f, 0f, false, true, 10f, 2f)
            lineTo(14f, 2f)
            arcTo(2f, 2f, 0f, false, true, 16f, 4f)
            lineTo(16f, 6f)
            moveTo(19f, 6f)
            lineTo(18f, 20f)
            arcTo(2f, 2f, 0f, false, true, 16f, 22f)
            lineTo(8f, 22f)
            arcTo(2f, 2f, 0f, false, true, 6f, 20f)
            lineTo(5f, 6f)
        }
    }
    val ChevronDown by lazy { icon("chevron-down") { moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f) } }
    val Monitor by lazy {
        icon("monitor") {
            rect(2f, 3f, 20f, 14f)
            moveTo(8f, 21f); lineTo(16f, 21f)
            moveTo(12f, 17f); lineTo(12f, 21f)
        }
    }
}