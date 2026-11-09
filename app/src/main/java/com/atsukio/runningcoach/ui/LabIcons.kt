package com.atsukio.runningcoach.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Crisp, lightweight lab-styled icons for form metrics, coaching sections and drills. */
object LabIcons {
    val FootStrike: ImageVector by lazy {
        vector("FootStrike") {
            // Running shoe profile
            strokePath {
                moveTo(3f, 15f)
                lineTo(7f, 15f)
                curveTo(9f, 13.5f, 11f, 11.5f, 14.5f, 11.5f)
                lineTo(18.5f, 12.5f)
                curveTo(20.5f, 13f, 21.5f, 14.5f, 21.5f, 16f)
                curveTo(21.5f, 17.5f, 20f, 18.5f, 17.5f, 18.5f)
                lineTo(3f, 18.5f)
                close()
            }
            // Treadmill belt ground contact line
            strokePath {
                moveTo(2f, 21f)
                lineTo(22f, 21f)
            }
            // Impact pulse at heel strike point
            strokePath(width = 1.5f) {
                moveTo(4.5f, 19f)
                lineTo(3f, 21f)
                moveTo(7f, 19f)
                lineTo(6f, 21f)
            }
        }
    }

    val Landing: ImageVector by lazy {
        vector("Landing") {
            // Viewfinder brackets
            strokePath {
                moveTo(4f, 8f); lineTo(4f, 4f); lineTo(8f, 4f)
                moveTo(16f, 4f); lineTo(20f, 4f); lineTo(20f, 8f)
                moveTo(4f, 16f); lineTo(4f, 20f); lineTo(8f, 20f)
                moveTo(16f, 20f); lineTo(20f, 20f); lineTo(20f, 16f)
            }
            // Center contact crosshair
            strokePath(width = 1.5f) {
                moveTo(12f, 8f); lineTo(12f, 16f)
                moveTo(8f, 12f); lineTo(16f, 12f)
            }
        }
    }

    val Overstride: ImageVector by lazy {
        vector("Overstride") {
            // Vertical hip plumb line (reference)
            strokePath(width = 1.5f) {
                moveTo(8f, 3f)
                lineTo(8f, 21f)
            }
            // Leg extending forward ahead of plumb line
            strokePath {
                moveTo(8f, 7f)
                lineTo(13f, 13f)
                lineTo(19f, 20f)
            }
            // Planted foot
            strokePath {
                moveTo(17f, 20f)
                lineTo(22f, 20f)
            }
        }
    }

    val ShinAngle: ImageVector by lazy {
        vector("ShinAngle") {
            // Vertical reference line
            strokePath(width = 1.5f) {
                moveTo(8f, 4f)
                lineTo(8f, 20f)
            }
            // Angled shin bone
            strokePath {
                moveTo(8f, 6f)
                lineTo(18f, 20f)
            }
            // Angle measurement arc
            strokePath(width = 1.5f) {
                moveTo(8f, 13f)
                curveTo(11f, 13f, 12.5f, 14.5f, 13.5f, 16.5f)
            }
            // Foot at belt
            strokePath {
                moveTo(16f, 20f)
                lineTo(21f, 20f)
            }
        }
    }

    val KneeFlexion: ImageVector by lazy {
        vector("KneeFlexion") {
            // Thigh to knee to ankle
            strokePath {
                moveTo(6f, 6f)
                lineTo(17f, 12f)
                lineTo(10f, 20f)
            }
            // Knee bend angle arc
            strokePath(width = 1.5f) {
                moveTo(13.5f, 10.5f)
                curveTo(15f, 12f, 14f, 14f, 12f, 15f)
            }
        }
    }

    val Bounce: ImageVector by lazy {
        vector("Bounce") {
            // Up arrow
            strokePath {
                moveTo(7f, 18f); lineTo(7f, 7f)
                moveTo(4f, 10f); lineTo(7f, 7f); lineTo(10f, 10f)
            }
            // Down arrow
            strokePath {
                moveTo(17f, 6f); lineTo(17f, 17f)
                moveTo(14f, 14f); lineTo(17f, 17f); lineTo(20f, 14f)
            }
            // Ground level line
            strokePath(width = 1.5f) {
                moveTo(2f, 21f); lineTo(22f, 21f)
            }
        }
    }

    val Cadence: ImageVector by lazy {
        vector("Cadence") {
            // Metronome body outline
            strokePath {
                moveTo(7f, 21f)
                lineTo(10f, 5f)
                lineTo(14f, 5f)
                lineTo(17f, 21f)
                close()
            }
            // Swinging pendulum needle
            strokePath(width = 2f) {
                moveTo(12f, 18f)
                lineTo(18.5f, 7f)
            }
            // Weighted slider on pendulum
            fillPath {
                moveTo(16f, 9.5f)
                lineTo(18f, 8f)
                lineTo(19f, 9.5f)
                lineTo(17f, 11f)
                close()
            }
        }
    }

    val ForwardLean: ImageVector by lazy {
        vector("ForwardLean") {
            // Vertical reference line
            strokePath(width = 1.5f) {
                moveTo(7f, 4f)
                lineTo(7f, 20f)
            }
            // Angled torso
            strokePath {
                moveTo(7f, 19f)
                lineTo(17f, 6f)
            }
            // Head
            fillPath {
                moveTo(19f, 4.5f)
                curveTo(19f, 3.4f, 18.1f, 2.5f, 17f, 2.5f)
                curveTo(15.9f, 2.5f, 15f, 3.4f, 15f, 4.5f)
                curveTo(15f, 5.6f, 15.9f, 6.5f, 17f, 6.5f)
                curveTo(18.1f, 6.5f, 19f, 5.6f, 19f, 4.5f)
                close()
            }
            // Lean angle arc
            strokePath(width = 1.5f) {
                moveTo(7f, 12f)
                curveTo(10f, 12f, 12f, 13.5f, 13f, 15f)
            }
        }
    }

    val ArmSwing: ImageVector by lazy {
        vector("ArmSwing") {
            // Shoulder to elbow to hand
            strokePath {
                moveTo(5f, 6f)
                lineTo(12f, 15f)
                lineTo(20f, 9f)
            }
            // Angle arc at elbow
            strokePath(width = 1.5f) {
                moveTo(10f, 12.5f)
                curveTo(12f, 11.5f, 14.5f, 11.5f, 16.5f, 11.5f)
            }
        }
    }

    val Symmetry: ImageVector by lazy {
        vector("Symmetry") {
            // Central scale pillar & base
            strokePath {
                moveTo(12f, 4f); lineTo(12f, 20f)
                moveTo(8f, 20f); lineTo(16f, 20f)
            }
            // Cross beam
            strokePath {
                moveTo(4f, 8f); lineTo(20f, 8f)
            }
            // Left balance pan
            strokePath(width = 1.5f) {
                moveTo(4f, 8f); lineTo(2f, 14f); lineTo(6f, 14f); close()
            }
            // Right balance pan
            strokePath(width = 1.5f) {
                moveTo(20f, 8f); lineTo(18f, 14f); lineTo(22f, 14f); close()
            }
        }
    }

    val ScaleTune: ImageVector by lazy {
        vector("ScaleTune") {
            // Calibration ruler frame
            strokePath {
                moveTo(3f, 7f)
                lineTo(21f, 7f)
                lineTo(21f, 17f)
                lineTo(3f, 17f)
                close()
            }
            // Measurement tick marks
            strokePath(width = 1.5f) {
                moveTo(7f, 7f); lineTo(7f, 12f)
                moveTo(10f, 7f); lineTo(10f, 14f)
                moveTo(13f, 7f); lineTo(13f, 12f)
                moveTo(16f, 7f); lineTo(16f, 14f)
            }
        }
    }

    val Target: ImageVector by lazy {
        vector("Target") {
            // Concentric target rings & crosshairs
            strokePath {
                moveTo(12f, 4f)
                curveTo(16.4f, 4f, 20f, 7.6f, 20f, 12f)
                curveTo(20f, 16.4f, 16.4f, 20f, 12f, 20f)
                curveTo(7.6f, 20f, 4f, 16.4f, 4f, 12f)
                curveTo(4f, 7.6f, 7.6f, 4f, 12f, 4f)
                close()
            }
            strokePath(width = 1.5f) {
                moveTo(12f, 8f)
                curveTo(14.2f, 8f, 16f, 9.8f, 16f, 12f)
                curveTo(16f, 14.2f, 14.2f, 16f, 12f, 16f)
                curveTo(9.8f, 16f, 8f, 14.2f, 8f, 12f)
                curveTo(8f, 9.8f, 9.8f, 8f, 12f, 8f)
                close()
            }
            fillPath {
                moveTo(12f, 10.5f)
                curveTo(12.8f, 10.5f, 13.5f, 11.2f, 13.5f, 12f)
                curveTo(13.5f, 12.8f, 12.8f, 13.5f, 12f, 13.5f)
                curveTo(11.2f, 13.5f, 10.5f, 12.8f, 10.5f, 12f)
                curveTo(10.5f, 11.2f, 11.2f, 10.5f, 12f, 10.5f)
                close()
            }
        }
    }

    val CoachingCue: ImageVector by lazy {
        vector("CoachingCue") {
            // Lightbulb / insight mental cue
            strokePath {
                moveTo(9f, 15f)
                curveTo(7f, 13.5f, 5.5f, 11f, 5.5f, 8.5f)
                curveTo(5.5f, 4.9f, 8.4f, 2f, 12f, 2f)
                curveTo(15.6f, 2f, 18.5f, 4.9f, 18.5f, 8.5f)
                curveTo(18.5f, 11f, 17f, 13.5f, 15f, 15f)
                lineTo(9f, 15f)
                close()
            }
            strokePath(width = 1.5f) {
                moveTo(9.5f, 18f); lineTo(14.5f, 18f)
                moveTo(10.5f, 21f); lineTo(13.5f, 21f)
            }
        }
    }

    val Drill: ImageVector by lazy {
        vector("Drill") {
            // Athletic dumbbell / training drill
            strokePath {
                moveTo(6f, 7f); lineTo(6f, 17f)
                moveTo(4f, 9f); lineTo(4f, 15f)
                moveTo(6f, 12f); lineTo(18f, 12f)
                moveTo(18f, 7f); lineTo(18f, 17f)
                moveTo(20f, 9f); lineTo(20f, 15f)
            }
        }
    }

    val Biomechanics: ImageVector by lazy {
        vector("Biomechanics") {
            // Running stick-figure skeleton node
            strokePath {
                moveTo(14f, 4.5f)
                curveTo(14f, 3.4f, 13.1f, 2.5f, 12f, 2.5f)
                curveTo(10.9f, 2.5f, 10f, 3.4f, 10f, 4.5f)
                curveTo(10f, 5.6f, 10.9f, 6.5f, 12f, 6.5f)
                curveTo(13.1f, 6.5f, 14f, 5.6f, 14f, 4.5f)
                close()
            }
            strokePath {
                moveTo(12f, 7f); lineTo(11f, 12f)
                moveTo(11f, 12f); lineTo(7f, 16f)
                moveTo(11f, 12f); lineTo(16f, 15f); lineTo(14f, 21f)
                moveTo(11f, 8.5f); lineTo(15f, 10.5f)
                moveTo(11f, 8.5f); lineTo(8f, 10.5f)
            }
        }
    }

    /** Returns the corresponding lab icon for a given coaching topic. */
    fun forTopic(topic: String): ImageVector = when (topic) {
        "Foot landing", "Overstriding" -> Overstride
        "Shin at landing" -> ShinAngle
        "Knee bend at landing", "Knees" -> KneeFlexion
        "Bounce" -> Bounce
        "Cadence" -> Cadence
        "Forward lean" -> ForwardLean
        "Arm swing" -> ArmSwing
        "Symmetry" -> Symmetry
        "Foot strike", "Foot strike pattern" -> FootStrike
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
