package com.atsukio.runningcoach.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Line icons for the form metrics, drawn on a 24-unit grid. Each one shows what is measured, the way the
 * overlay draws it: the runner faces right, a dashed line is the vertical reference and an arc is the angle.
 */
object LabIcons {

    /** Foot strike pattern: shoe side-on above the three landing zones (heel, midfoot, forefoot). */
    val FootStrike: ImageVector by lazy {
        vector("FootStrike") {
            strokePath(width = 1.7f) {
                moveTo(3f, 16f)
                lineTo(3f, 10f)
                lineTo(8f, 9f)
                curveTo(9.5f, 11f, 11.5f, 11.5f, 13.5f, 11f)
                lineTo(18.5f, 12.5f)
                curveTo(20.5f, 13f, 21.5f, 14.5f, 21.5f, 16f)
                close()
            }
            fillPath {
                moveTo(3f, 16.8f); lineTo(21.5f, 16.8f); lineTo(21f, 18.8f); lineTo(3.5f, 18.8f); close()
            }
            strokePath(width = 2f) {
                moveTo(3.5f, 21.5f); lineTo(7.5f, 21.5f)
                moveTo(10.5f, 21.5f); lineTo(14f, 21.5f)
                moveTo(17f, 21.5f); lineTo(21f, 21.5f)
            }
        }
    }

    /** At landing: shoe coming down onto the belt. */
    val Landing: ImageVector by lazy {
        vector("Landing") {
            strokePath(width = 1.8f) {
                moveTo(12f, 2.5f); lineTo(12f, 10f)
                moveTo(9f, 7.5f); lineTo(12f, 10.5f); lineTo(15f, 7.5f)
            }
            strokePath(width = 1.7f) {
                moveTo(4.5f, 19f)
                lineTo(4.5f, 14.5f)
                lineTo(8.5f, 13.5f)
                curveTo(9.7f, 15f, 11.5f, 15.4f, 13.2f, 15f)
                lineTo(17f, 16.2f)
                curveTo(18.6f, 16.7f, 19.5f, 17.8f, 19.5f, 19f)
                close()
            }
            strokePath(width = 1.5f) {
                moveTo(2f, 21.5f); lineTo(22f, 21.5f)
            }
        }
    }

    /** Foot ahead of hips: leg reaching out past the line dropped from the hip, with the gap measured. */
    val Overstride: ImageVector by lazy {
        vector("Overstride") {
            fillPath { circle(6f, 4f, 2f) }
            strokePath(width = 1.4f) { dashV(6f, 7f, 18f) }
            strokePath(width = 2f) {
                moveTo(6f, 4f); lineTo(11.5f, 10f); lineTo(15.5f, 17f); lineTo(20.5f, 15.5f)
            }
            strokePath(width = 1.5f) {
                moveTo(3f, 18f); lineTo(22f, 18f)
            }
            strokePath(width = 1.6f) {
                moveTo(6f, 20f); lineTo(6f, 23f)
                moveTo(15.5f, 20f); lineTo(15.5f, 23f)
                moveTo(6f, 21.5f); lineTo(15.5f, 21.5f)
            }
        }
    }

    /** Shin at landing: shin from knee to foot, its angle from vertical marked at the ankle. */
    val ShinAngle: ImageVector by lazy {
        vector("ShinAngle") {
            fillPath { circle(8f, 4f, 2f) }
            strokePath(width = 2f) {
                moveTo(8f, 4f); lineTo(14f, 18f); lineTo(19.5f, 18f)
            }
            strokePath(width = 1.4f) { dashV(14f, 4f, 16f) }
            strokePath(width = 1.5f) {
                moveTo(14f, 10f)
                arcTo(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = false, 10.85f, 10.65f)
            }
            strokePath(width = 1.5f) {
                moveTo(3f, 21f); lineTo(21f, 21f)
            }
        }
    }

    /** Knee bend: hip, knee and ankle with the angle at the knee. */
    val KneeFlexion: ImageVector by lazy {
        vector("KneeFlexion") {
            strokePath(width = 2f) {
                moveTo(5f, 4f); lineTo(15f, 11f); lineTo(9f, 20f); lineTo(13.5f, 20.5f)
            }
            fillPath { circle(5f, 4f, 1.6f) }
            fillPath { circle(15f, 11f, 2f) }
            strokePath(width = 1.5f) {
                moveTo(10.9f, 8.13f)
                arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12.23f, 15.16f)
            }
        }
    }

    /** Bounce: the hips' up-and-down path, with the height of each rise marked. */
    val Bounce: ImageVector by lazy {
        vector("Bounce") {
            strokePath(width = 1.8f) {
                moveTo(2f, 18f)
                curveTo(4.67f, 4.67f, 7.33f, 4.67f, 10f, 18f)
                curveTo(12.67f, 4.67f, 15.33f, 4.67f, 18f, 18f)
            }
            fillPath { circle(14f, 8f, 2f) }
            strokePath(width = 1.6f) {
                moveTo(21.5f, 8.5f); lineTo(21.5f, 17.5f)
                moveTo(19.5f, 10.5f); lineTo(21.5f, 8.5f); lineTo(23f, 10f)
                moveTo(19.5f, 15.5f); lineTo(21.5f, 17.5f); lineTo(23f, 16f)
            }
            strokePath(width = 1.4f) {
                moveTo(2f, 21f); lineTo(18f, 21f)
            }
        }
    }

    /** Cadence: a metronome, since cadence is the step rate and the app's metronome sets it. */
    val Cadence: ImageVector by lazy {
        vector("Cadence") {
            strokePath(width = 1.8f) {
                moveTo(9.5f, 3f); lineTo(14.5f, 3f); lineTo(19.5f, 21f); lineTo(4.5f, 21f); close()
            }
            strokePath(width = 1.5f) {
                moveTo(6.5f, 17f); lineTo(17.5f, 17f)
            }
            strokePath(width = 1.8f) {
                moveTo(12f, 17f); lineTo(16.5f, 6f)
            }
            fillPath { circle(14.2f, 11.6f, 1.7f) }
        }
    }

    /** Posture: torso leaning from the hips, its angle from vertical marked at the hip. */
    val ForwardLean: ImageVector by lazy {
        vector("ForwardLean") {
            fillPath { circle(13.7f, 4.6f, 2.2f) }
            strokePath(width = 2.2f) {
                moveTo(8f, 20f); lineTo(12.5f, 8f)
            }
            fillPath { circle(8f, 20f, 1.8f) }
            strokePath(width = 1.4f) { dashV(8f, 4f, 17f) }
            strokePath(width = 1.5f) {
                moveTo(8f, 12f)
                arcTo(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 10.81f, 12.51f)
            }
        }
    }

    /** Arms: shoulder, elbow and hand with the right angle at the elbow that the coach looks for. */
    val ArmSwing: ImageVector by lazy {
        vector("ArmSwing") {
            fillPath { circle(6f, 4f, 2f) }
            strokePath(width = 2.2f) {
                moveTo(6f, 4f); lineTo(8f, 14f); lineTo(18f, 12f)
            }
            fillPath { circle(18.5f, 11.9f, 2.2f) }
            strokePath(width = 1.4f) {
                moveTo(7.41f, 11.06f); lineTo(10.35f, 10.47f); lineTo(10.94f, 13.41f)
            }
            strokePath(width = 1.5f) {
                moveTo(10.5f, 19.5f)
                curveTo(14f, 20.5f, 18f, 19f, 20.5f, 16f)
                moveTo(18f, 16.2f); lineTo(20.5f, 16f); lineTo(20.8f, 18.4f)
            }
        }
    }

    /** Left/right balance: a left and a right footprint either side of the centre line. */
    val Symmetry: ImageVector by lazy {
        vector("Symmetry") {
            fillPath { ellipse(7f, 8f, 2.6f, 4f) }
            fillPath { ellipse(7.6f, 16.5f, 2.1f, 2.6f) }
            fillPath { ellipse(17f, 8f, 2.6f, 4f) }
            fillPath { ellipse(16.4f, 16.5f, 2.1f, 2.6f) }
            strokePath(width = 1.4f) { dashV(12f, 2.5f, 21.5f) }
        }
    }

    /** Scale for this clip: a ruler beside a standing person, since the scale comes from their height. */
    val ScaleTune: ImageVector by lazy {
        vector("ScaleTune") {
            strokePath(width = 1.6f) {
                moveTo(5f, 3f); lineTo(5f, 21f)
                moveTo(5f, 3f); lineTo(8.5f, 3f)
                moveTo(5f, 12f); lineTo(8.5f, 12f)
                moveTo(5f, 21f); lineTo(8.5f, 21f)
            }
            strokePath(width = 1.2f) {
                moveTo(5f, 5.25f); lineTo(7f, 5.25f)
                moveTo(5f, 7.5f); lineTo(7f, 7.5f)
                moveTo(5f, 9.75f); lineTo(7f, 9.75f)
                moveTo(5f, 14.25f); lineTo(7f, 14.25f)
                moveTo(5f, 16.5f); lineTo(7f, 16.5f)
                moveTo(5f, 18.75f); lineTo(7f, 18.75f)
            }
            fillPath { circle(16f, 5f, 2f) }
            strokePath(width = 1.8f) {
                moveTo(16f, 7.5f); lineTo(16f, 14f)
                moveTo(16f, 14f); lineTo(13.5f, 21f)
                moveTo(16f, 14f); lineTo(18.5f, 21f)
                moveTo(12.8f, 12.5f); lineTo(16f, 9.5f); lineTo(19.2f, 12.5f)
            }
        }
    }

    /** Main focus: bullseye with crosshairs. */
    val Target: ImageVector by lazy {
        vector("Target") {
            strokePath(width = 1.8f) { circle(12f, 12f, 9f) }
            strokePath(width = 1.4f) { circle(12f, 12f, 5f) }
            fillPath { circle(12f, 12f, 2f) }
            strokePath(width = 1.5f) {
                moveTo(12f, 1f); lineTo(12f, 4f)
                moveTo(12f, 20f); lineTo(12f, 23f)
                moveTo(1f, 12f); lineTo(4f, 12f)
                moveTo(20f, 12f); lineTo(23f, 12f)
            }
        }
    }

    /** Coaching cue: a light bulb, the thing to think about while running. */
    val CoachingCue: ImageVector by lazy {
        vector("CoachingCue") {
            strokePath(width = 1.8f) {
                moveTo(9f, 15f)
                curveTo(7f, 13.5f, 5.5f, 11f, 5.5f, 8.5f)
                curveTo(5.5f, 4.9f, 8.4f, 2f, 12f, 2f)
                curveTo(15.6f, 2f, 18.5f, 4.9f, 18.5f, 8.5f)
                curveTo(18.5f, 11f, 17f, 13.5f, 15f, 15f)
                close()
            }
            strokePath(width = 1.6f) {
                moveTo(9.5f, 17.5f); lineTo(14.5f, 17.5f)
                moveTo(10.5f, 20.5f); lineTo(13.5f, 20.5f)
            }
            strokePath(width = 1.2f) {
                moveTo(10f, 12f); lineTo(11f, 8f); lineTo(13f, 8f); lineTo(14f, 12f)
            }
        }
    }

    /** Practice drill: a dumbbell, for the strength and technique work suggested. */
    val Drill: ImageVector by lazy {
        vector("Drill") {
            fillPath { roundRect(2f, 8f, 5f, 16f) }
            fillPath { roundRect(19f, 8f, 22f, 16f) }
            fillPath {
                moveTo(5.5f, 9.5f); lineTo(7f, 9.5f); lineTo(7f, 14.5f); lineTo(5.5f, 14.5f); close()
                moveTo(17f, 9.5f); lineTo(18.5f, 9.5f); lineTo(18.5f, 14.5f); lineTo(17f, 14.5f); close()
            }
            strokePath(width = 2.4f) {
                moveTo(7f, 12f); lineTo(17f, 12f)
            }
        }
    }

    /** Form breakdown: a runner mid-stride. */
    val Biomechanics: ImageVector by lazy {
        vector("Biomechanics") {
            fillPath { circle(12f, 4.5f, 2f) }
            strokePath(width = 2f) {
                moveTo(12f, 7f); lineTo(11f, 13f)
                moveTo(11f, 13f); lineTo(16f, 14f); lineTo(15f, 20f)
                moveTo(11f, 13f); lineTo(6f, 17f); lineTo(4f, 21f)
                moveTo(11.5f, 9f); lineTo(16f, 10.5f); lineTo(17f, 7.5f)
                moveTo(11.5f, 9f); lineTo(7.5f, 10.5f); lineTo(7f, 14f)
            }
        }
    }

    /** Hip drop, seen from behind: the hip line tilted below level (dashed) over the standing leg. */
    val HipDrop: ImageVector by lazy {
        vector("HipDrop") {
            strokePath(width = 1.3f) {
                moveTo(3f, 8f); lineTo(6f, 8f)
                moveTo(9f, 8f); lineTo(12f, 8f)
                moveTo(15f, 8f); lineTo(18f, 8f)
            }
            strokePath(width = 2f) {
                moveTo(6f, 8f); lineTo(18f, 12.5f)
                moveTo(6f, 8f); lineTo(6.5f, 21f)
                moveTo(18f, 12.5f); lineTo(16.5f, 17.5f)
            }
            fillPath { circle(6f, 8f, 2f) }
            fillPath { circle(18f, 12.5f, 2f) }
            strokePath(width = 1.5f) {
                moveTo(21.5f, 7f); lineTo(21.5f, 12f)
                moveTo(20f, 10.5f); lineTo(21.5f, 12f); lineTo(23f, 10.5f)
            }
        }
    }

    /** Knee alignment, seen from behind: the knee sitting inside the straight hip-to-ankle line (dashed). */
    val KneeAlignment: ImageVector by lazy {
        vector("KneeAlignment") {
            strokePath(width = 1.3f) {
                moveTo(8f, 5.5f); lineTo(8f, 8f)
                moveTo(8f, 10.5f); lineTo(8f, 13f)
                moveTo(8f, 15.5f); lineTo(8f, 18f)
            }
            strokePath(width = 2f) {
                moveTo(8f, 3f); lineTo(14f, 12f); lineTo(8f, 21f)
            }
            fillPath { circle(8f, 3f, 1.8f) }
            fillPath { circle(14f, 12f, 2.2f) }
            fillPath { circle(8f, 21f, 1.8f) }
            strokePath(width = 1.5f) {
                moveTo(21.5f, 12f); lineTo(17.5f, 12f)
                moveTo(19f, 10.5f); lineTo(17.5f, 12f); lineTo(19f, 13.5f)
            }
        }
    }

    /** Step width: two footprints either side of the centre line (dashed), with the gap between them marked. */
    val StepWidth: ImageVector by lazy {
        vector("StepWidth") {
            strokePath(width = 1.3f) {
                moveTo(12f, 2f); lineTo(12f, 5f)
                moveTo(12f, 8f); lineTo(12f, 11f)
                moveTo(12f, 19f); lineTo(12f, 22f)
            }
            strokePath(width = 1.8f) {
                moveTo(5f, 3f); curveTo(8f, 3f, 8f, 11f, 5f, 11f); curveTo(2f, 11f, 2f, 3f, 5f, 3f)
                moveTo(19f, 9f); curveTo(22f, 9f, 22f, 17f, 19f, 17f); curveTo(16f, 17f, 16f, 9f, 19f, 9f)
            }
            strokePath(width = 1.5f) {
                moveTo(5f, 15f); lineTo(14f, 15f)
                moveTo(7f, 13.5f); lineTo(5f, 15f); lineTo(7f, 16.5f)
                moveTo(12f, 13.5f); lineTo(14f, 15f); lineTo(12f, 16.5f)
            }
        }
    }

    /** Trunk sway, seen from behind: the trunk tipped to one side of upright (dashed), with the swing marked. */
    val TrunkSway: ImageVector by lazy {
        vector("TrunkSway") {
            strokePath(width = 1.3f) {
                moveTo(12f, 3f); lineTo(12f, 6f)
                moveTo(12f, 9f); lineTo(12f, 12f)
                moveTo(12f, 15f); lineTo(12f, 18f)
            }
            strokePath(width = 2f) {
                moveTo(12f, 20f); lineTo(16.5f, 8f)
                moveTo(8f, 20f); lineTo(16f, 20f)
            }
            fillPath { circle(17.5f, 5f, 2.3f) }
            strokePath(width = 1.5f) {
                moveTo(4f, 8f); curveTo(6f, 5f, 8f, 4.5f, 10f, 5f)
                moveTo(4f, 5.5f); lineTo(4f, 8f); lineTo(6.5f, 8.2f)
            }
        }
    }

    /** The icon for a coaching topic, matching the topic names `Coach` produces. */
    fun forTopic(topic: String): ImageVector = when (topic) {
        "Foot landing", "Overstriding" -> Overstride
        "Shin at landing" -> ShinAngle
        "Knee bend" -> KneeFlexion
        "Bounce" -> Bounce
        "Cadence" -> Cadence
        "Posture" -> ForwardLean
        "Arms" -> ArmSwing
        "Left/right balance" -> Symmetry
        "Foot strike" -> FootStrike
        "Hip drop" -> HipDrop
        "Knee alignment" -> KneeAlignment
        "Step width" -> StepWidth
        "Trunk sway" -> TrunkSway
        else -> Biomechanics
    }
}

private fun vector(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(block).build()

private fun ImageVector.Builder.strokePath(
    width: Float = 2f,
    pathBuilder: PathBuilder.() -> Unit,
) {
    path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = pathBuilder,
    )
}

private fun ImageVector.Builder.fillPath(
    pathBuilder: PathBuilder.() -> Unit,
) {
    path(
        fill = SolidColor(Color.White),
        pathBuilder = pathBuilder,
    )
}

private fun PathBuilder.ellipse(cx: Float, cy: Float, rx: Float, ry: Float) {
    moveTo(cx - rx, cy)
    arcToRelative(rx, ry, 0f, isMoreThanHalf = true, isPositiveArc = true, 2 * rx, 0f)
    arcToRelative(rx, ry, 0f, isMoreThanHalf = true, isPositiveArc = true, -2 * rx, 0f)
    close()
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) = ellipse(cx, cy, r, r)

private fun PathBuilder.roundRect(left: Float, top: Float, right: Float, bottom: Float, r: Float = 1f) {
    moveTo(left + r, top)
    lineTo(right - r, top)
    arcToRelative(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, r, r)
    lineTo(right, bottom - r)
    arcToRelative(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, -r, r)
    lineTo(left + r, bottom)
    arcToRelative(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, -r, -r)
    lineTo(left, top + r)
    arcToRelative(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, r, -r)
    close()
}

/** A dashed vertical reference line; vector paths have no dash effect, so the dashes are separate strokes. */
private fun PathBuilder.dashV(x: Float, top: Float, bottom: Float, dash: Float = 2f, gap: Float = 1.6f) {
    var y = top
    while (y < bottom) {
        moveTo(x, y)
        lineTo(x, minOf(y + dash, bottom))
        y += dash + gap
    }
}
