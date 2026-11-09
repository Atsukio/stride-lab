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
 * Intuitive, recognizable lab-styled icons for running form metrics, calibration and coaching drills.
 * Designed with solid accents and clear anatomical & athletic motifs so runners immediately
 * recognize what each metric represents.
 */
object LabIcons {

    /** Running shoe with ground contact impact shockwave. */
    val FootStrike: ImageVector by lazy {
        vector("FootStrike") {
            // Shoe upper profile
            strokePath(width = 1.8f) {
                moveTo(3.5f, 13f)
                curveTo(5f, 13f, 7f, 12f, 9f, 10.5f)
                curveTo(11f, 9f, 13f, 8.5f, 15f, 9.5f)
                lineTo(19f, 11f)
                curveTo(21f, 12f, 22f, 13.5f, 21.5f, 15f)
                lineTo(3f, 15f)
                close()
            }
            // Shoe sole / cushioned midsole
            fillPath {
                moveTo(3f, 15.5f)
                lineTo(21.5f, 15.5f)
                curveTo(21.5f, 17f, 20.5f, 17.5f, 19f, 17.5f)
                lineTo(3.5f, 17.5f)
                curveTo(2.8f, 17.5f, 2.5f, 16.5f, 3f, 15.5f)
                close()
            }
            // Treadmill ground contact line
            strokePath(width = 1.5f) {
                moveTo(2f, 21f)
                lineTo(22f, 21f)
            }
            // Ground impact energy waves radiating from heel/midfoot strike
            strokePath(width = 1.5f) {
                moveTo(5f, 18.5f); lineTo(3.5f, 20.5f)
                moveTo(8.5f, 18.5f); lineTo(8f, 20.5f)
                moveTo(12f, 18.5f); lineTo(12.5f, 20.5f)
            }
        }
    }

    /** Landing card icon: shoe touching ground with landing crosshairs. */
    val Landing: ImageVector by lazy {
        vector("Landing") {
            // Foot / shoe contacting ground
            strokePath(width = 1.8f) {
                moveTo(4f, 14f)
                curveTo(7f, 14f, 10f, 11f, 14f, 10.5f)
                lineTo(18f, 12f)
                curveTo(20f, 13f, 21f, 14.5f, 20.5f, 16f)
                lineTo(4f, 16f)
                close()
            }
            // Ground line
            strokePath(width = 1.5f) {
                moveTo(2f, 20f)
                lineTo(22f, 20f)
            }
            // Target focus pin pointing to landing spot
            strokePath(width = 1.5f) {
                moveTo(10f, 3f); lineTo(10f, 8f)
                moveTo(6f, 5.5f); lineTo(14f, 5.5f)
            }
            fillPath {
                moveTo(10f, 4f)
                curveTo(10.8f, 4f, 11.5f, 4.7f, 11.5f, 5.5f)
                curveTo(11.5f, 6.3f, 10.8f, 7f, 10f, 7f)
                curveTo(9.2f, 7f, 8.5f, 6.3f, 8.5f, 5.5f)
                curveTo(8.5f, 4.7f, 9.2f, 4f, 10f, 4f)
                close()
            }
        }
    }

    /** Overstride / Foot landing: shoe landing ahead with forward distance measurement arrow. */
    val Overstride: ImageVector by lazy {
        vector("Overstride") {
            // Hip vertical plumb line (reference)
            strokePath(width = 1.5f) {
                moveTo(6f, 3f)
                lineTo(6f, 21f)
            }
            // Running shoe landed forward
            strokePath(width = 1.8f) {
                moveTo(10f, 13f)
                curveTo(12f, 13f, 14f, 11f, 17f, 10.5f)
                lineTo(20f, 12f)
                curveTo(21.5f, 13f, 22f, 14.5f, 21.5f, 15.5f)
                lineTo(10f, 15.5f)
                close()
            }
            // Forward stride distance measurement arrow between plumb line and foot
            strokePath(width = 1.6f) {
                moveTo(6f, 18.5f)
                lineTo(18f, 18.5f)
                // Arrowhead
                moveTo(15f, 16f)
                lineTo(18.5f, 18.5f)
                lineTo(15f, 21f)
            }
        }
    }

    /** Shin angle: lower leg from knee to ankle with vertical plumb line and angle caliper. */
    val ShinAngle: ImageVector by lazy {
        vector("ShinAngle") {
            // Vertical reference line
            strokePath(width = 1.5f) {
                moveTo(7f, 3f)
                lineTo(7f, 21f)
            }
            // Shin & calf bone contour angling forward
            strokePath(width = 2f) {
                // Knee
                moveTo(7f, 5f)
                // Shin line forward
                lineTo(17f, 18f)
                // Foot
                lineTo(21f, 18f)
            }
            // Calf bulge
            strokePath(width = 1.5f) {
                moveTo(8.5f, 6f)
                curveTo(13f, 10f, 14f, 13f, 15.5f, 18f)
            }
            // Angle measurement arc
            strokePath(width = 1.5f) {
                moveTo(7f, 11f)
                curveTo(9.5f, 11f, 11.5f, 12.5f, 12.5f, 14.5f)
            }
            // Ground
            strokePath(width = 1.5f) {
                moveTo(14f, 21f)
                lineTo(22f, 21f)
            }
        }
    }

    /** Knee bend: leg showing thigh, bent knee joint, and calf with joint angle indicator. */
    val KneeFlexion: ImageVector by lazy {
        vector("KneeFlexion") {
            // Thigh (hip to knee)
            strokePath(width = 2.2f) {
                moveTo(4f, 6f)
                lineTo(16f, 11f)
                // Shin (knee to foot)
                lineTo(8f, 20f)
                lineTo(4f, 20f)
            }
            // Highlighted kneecap circle
            fillPath {
                moveTo(16f, 9.5f)
                curveTo(17.4f, 9.5f, 18.5f, 10.6f, 18.5f, 12f)
                curveTo(18.5f, 13.4f, 17.4f, 14.5f, 16f, 14.5f)
                curveTo(14.6f, 14.5f, 13.5f, 13.4f, 13.5f, 12f)
                curveTo(13.5f, 10.6f, 14.6f, 9.5f, 16f, 9.5f)
                close()
            }
            // Angle bend arc in crook of knee
            strokePath(width = 1.5f) {
                moveTo(12f, 9.5f)
                curveTo(13.5f, 11f, 12.5f, 13.5f, 10.5f, 15f)
            }
        }
    }

    /** Bounce: vertical oscillation height meter with top ceiling, bottom floor and opposing arrows. */
    val Bounce: ImageVector by lazy {
        vector("Bounce") {
            // Top peak bar
            strokePath(width = 1.8f) {
                moveTo(4f, 3.5f)
                lineTo(20f, 3.5f)
            }
            // Bottom floor bar
            strokePath(width = 1.8f) {
                moveTo(4f, 20.5f)
                lineTo(20f, 20.5f)
            }
            // Central vertical double-headed arrow
            strokePath(width = 2f) {
                moveTo(12f, 5.5f)
                lineTo(12f, 18.5f)
                // Top arrowhead
                moveTo(8.5f, 8f); lineTo(12f, 4.5f); lineTo(15.5f, 8f)
                // Bottom arrowhead
                moveTo(8.5f, 16f); lineTo(12f, 19.5f); lineTo(15.5f, 16f)
            }
            // Spring bounce oscillation curves
            strokePath(width = 1.4f) {
                moveTo(6.5f, 8.5f)
                curveTo(7.5f, 10.5f, 5.5f, 13.5f, 6.5f, 15.5f)
                moveTo(17.5f, 8.5f)
                curveTo(18.5f, 10.5f, 16.5f, 13.5f, 17.5f, 15.5f)
            }
        }
    }

    /** Cadence: classic athletic stopwatch with tick marks and hand (universal tempo & rate symbol). */
    val Cadence: ImageVector by lazy {
        vector("Cadence") {
            // Top crown pusher
            strokePath(width = 1.8f) {
                moveTo(10f, 2f); lineTo(14f, 2f)
                moveTo(12f, 2f); lineTo(12f, 4.5f)
            }
            // Stopwatch circular dial
            strokePath(width = 1.8f) {
                moveTo(12f, 4.5f)
                curveTo(16.7f, 4.5f, 20.5f, 8.3f, 20.5f, 13f)
                curveTo(20.5f, 17.7f, 16.7f, 21.5f, 12f, 21.5f)
                curveTo(7.3f, 21.5f, 3.5f, 17.7f, 3.5f, 13f)
                curveTo(3.5f, 8.3f, 7.3f, 4.5f, 12f, 4.5f)
                close()
            }
            // Stopwatch hand pointing at cadence angle (~2 o'clock)
            strokePath(width = 1.8f) {
                moveTo(12f, 13f)
                lineTo(16f, 9f)
            }
            // Center pivot dot
            fillPath {
                moveTo(12f, 11.8f)
                curveTo(12.7f, 11.8f, 13.2f, 12.3f, 13.2f, 13f)
                curveTo(13.2f, 13.7f, 12.7f, 14.2f, 12f, 14.2f)
                curveTo(11.3f, 14.2f, 10.8f, 13.7f, 10.8f, 13f)
                curveTo(10.8f, 12.3f, 11.3f, 11.8f, 12f, 11.8f)
                close()
            }
            // Dial tick marks
            strokePath(width = 1.2f) {
                moveTo(12f, 5.5f); lineTo(12f, 7f)
                moveTo(18f, 13f); lineTo(19.5f, 13f)
                moveTo(12f, 19f); lineTo(12f, 20.5f)
                moveTo(4.5f, 13f); lineTo(6f, 13f)
            }
        }
    }

    /** Forward lean: runner torso and head silhouette leaning forward into the stride. */
    val ForwardLean: ImageVector by lazy {
        vector("ForwardLean") {
            // Vertical reference plumb line
            strokePath(width = 1.5f) {
                moveTo(6f, 3f)
                lineTo(6f, 21f)
            }
            // Runner head tilted forward
            fillPath {
                moveTo(18f, 4.5f)
                curveTo(18f, 3.1f, 16.9f, 2f, 15.5f, 2f)
                curveTo(14.1f, 2f, 13f, 3.1f, 13f, 4.5f)
                curveTo(13f, 5.9f, 14.1f, 7f, 15.5f, 7f)
                curveTo(16.9f, 7f, 18f, 5.9f, 18f, 4.5f)
                close()
            }
            // Leaning torso line
            strokePath(width = 2.2f) {
                moveTo(14.5f, 7.5f)
                lineTo(9f, 17f)
            }
            // Driving leg & trailing leg
            strokePath(width = 1.8f) {
                // Front leg
                moveTo(9f, 17f); lineTo(15f, 19f); lineTo(17f, 22f)
                // Back leg
                moveTo(9f, 17f); lineTo(5f, 19f)
            }
            // Forward angle arc
            strokePath(width = 1.5f) {
                moveTo(6f, 11f)
                curveTo(8.5f, 11f, 10.5f, 12f, 11.5f, 13.5f)
            }
        }
    }

    /** Arm swing: muscular bent arm showing shoulder, bicep, 90° elbow, and running fist. */
    val ArmSwing: ImageVector by lazy {
        vector("ArmSwing") {
            // Upper arm and forearm path
            strokePath(width = 2.2f) {
                // Shoulder
                moveTo(5f, 5f)
                // Down to elbow
                lineTo(11f, 16f)
                // Forearm driving forward & up
                lineTo(19f, 9f)
            }
            // Clenched running fist at end
            fillPath {
                moveTo(19f, 7.5f)
                curveTo(20.4f, 7.5f, 21.5f, 8.6f, 21.5f, 10f)
                curveTo(21.5f, 11.4f, 20.4f, 12.5f, 19f, 12.5f)
                curveTo(17.6f, 12.5f, 16.5f, 11.4f, 16.5f, 10f)
                curveTo(16.5f, 8.6f, 17.6f, 7.5f, 19f, 7.5f)
                close()
            }
            // Dynamic swing motion arcs
            strokePath(width = 1.4f) {
                moveTo(9f, 13f)
                curveTo(11f, 12f, 13.5f, 12f, 15.5f, 12.5f)
                moveTo(8f, 19f)
                curveTo(11f, 20.5f, 15f, 19.5f, 18f, 16f)
            }
        }
    }

    /** Symmetry / Balance: balanced scales with horizontal beam and dual hanging pans. */
    val Symmetry: ImageVector by lazy {
        vector("Symmetry") {
            // Center mast & base
            strokePath(width = 1.8f) {
                moveTo(12f, 3f); lineTo(12f, 21f)
                moveTo(7f, 21f); lineTo(17f, 21f)
            }
            // Fulcrum top circle
            fillPath {
                moveTo(12f, 3f)
                curveTo(12.8f, 3f, 13.5f, 3.7f, 13.5f, 4.5f)
                curveTo(13.5f, 5.3f, 12.8f, 6f, 12f, 6f)
                curveTo(11.2f, 6f, 10.5f, 5.3f, 10.5f, 4.5f)
                curveTo(10.5f, 3.7f, 11.2f, 3f, 12f, 3f)
                close()
            }
            // Balanced horizontal cross beam
            strokePath(width = 1.8f) {
                moveTo(3f, 7.5f); lineTo(21f, 7.5f)
            }
            // Left pan (suspension & dish)
            strokePath(width = 1.4f) {
                moveTo(3f, 7.5f); lineTo(1.5f, 13f); lineTo(6.5f, 13f); lineTo(5f, 7.5f)
                moveTo(0.5f, 13.5f); lineTo(7.5f, 13.5f)
            }
            // Right pan (suspension & dish)
            strokePath(width = 1.4f) {
                moveTo(19f, 7.5f); lineTo(17.5f, 13f); lineTo(22.5f, 13f); lineTo(21f, 7.5f)
                moveTo(16.5f, 13.5f); lineTo(23.5f, 13.5f)
            }
        }
    }

    /** Calibration / Scale: three horizontal sliders with adjustable tuning knobs (universal tune icon). */
    val ScaleTune: ImageVector by lazy {
        vector("ScaleTune") {
            // Top track & knob
            strokePath(width = 1.8f) {
                moveTo(3f, 6f); lineTo(21f, 6f)
            }
            fillPath {
                moveTo(8f, 4f); lineTo(12f, 4f); lineTo(12f, 8f); lineTo(8f, 8f); close()
            }
            // Middle track & knob
            strokePath(width = 1.8f) {
                moveTo(3f, 12f); lineTo(21f, 12f)
            }
            fillPath {
                moveTo(14f, 10f); lineTo(18f, 10f); lineTo(18f, 14f); lineTo(14f, 14f); close()
            }
            // Bottom track & knob
            strokePath(width = 1.8f) {
                moveTo(3f, 18f); lineTo(21f, 18f)
            }
            fillPath {
                moveTo(7f, 16f); lineTo(11f, 16f); lineTo(11f, 20f); lineTo(7f, 20f); close()
            }
        }
    }

    /** Main focus: bullseye target with concentric rings and center pip. */
    val Target: ImageVector by lazy {
        vector("Target") {
            // Outer ring
            strokePath(width = 1.8f) {
                moveTo(12f, 3f)
                curveTo(17f, 3f, 21f, 7f, 21f, 12f)
                curveTo(21f, 17f, 17f, 21f, 12f, 21f)
                curveTo(7f, 21f, 3f, 17f, 3f, 12f)
                curveTo(3f, 7f, 7f, 3f, 12f, 3f)
                close()
            }
            // Inner ring
            strokePath(width = 1.4f) {
                moveTo(12f, 7f)
                curveTo(14.8f, 7f, 17f, 9.2f, 17f, 12f)
                curveTo(17f, 14.8f, 14.8f, 17f, 12f, 17f)
                curveTo(9.2f, 17f, 7f, 14.8f, 7f, 12f)
                curveTo(7f, 9.2f, 9.2f, 7f, 12f, 7f)
                close()
            }
            // Bullseye center dot
            fillPath {
                moveTo(12f, 10f)
                curveTo(13.1f, 10f, 14f, 10.9f, 14f, 12f)
                curveTo(14f, 13.1f, 13.1f, 14f, 12f, 14f)
                curveTo(10.9f, 14f, 10f, 13.1f, 10f, 12f)
                curveTo(10f, 10.9f, 10.9f, 10f, 12f, 10f)
                close()
            }
            // Target crosshairs
            strokePath(width = 1.5f) {
                moveTo(12f, 1f); lineTo(12f, 4f)
                moveTo(12f, 20f); lineTo(12f, 23f)
                moveTo(1f, 12f); lineTo(4f, 12f)
                moveTo(20f, 12f); lineTo(23f, 12f)
            }
        }
    }

    /** Coaching mental cue: incandescent lightbulb with glowing filament & idea rays. */
    val CoachingCue: ImageVector by lazy {
        vector("CoachingCue") {
            // Bulb outline
            strokePath(width = 1.8f) {
                moveTo(9f, 15f)
                curveTo(7f, 13.5f, 5.5f, 11f, 5.5f, 8.5f)
                curveTo(5.5f, 4.9f, 8.4f, 2f, 12f, 2f)
                curveTo(15.6f, 2f, 18.5f, 4.9f, 18.5f, 8.5f)
                curveTo(18.5f, 11f, 17f, 13.5f, 15f, 15f)
                lineTo(9f, 15f)
                close()
            }
            // Screw base
            strokePath(width = 1.6f) {
                moveTo(9.5f, 17.5f); lineTo(14.5f, 17.5f)
                moveTo(10.5f, 20.5f); lineTo(13.5f, 20.5f)
            }
            // Filament
            strokePath(width = 1.2f) {
                moveTo(10f, 12f); lineTo(11f, 8f); lineTo(13f, 8f); lineTo(14f, 12f)
            }
        }
    }

    /** Practice drill: fitness dumbbell (athletic exercise / workout). */
    val Drill: ImageVector by lazy {
        vector("Drill") {
            // Left weight plate
            fillPath {
                moveTo(4f, 8f)
                curveTo(4.6f, 8f, 5f, 8.4f, 5f, 9f)
                lineTo(5f, 15f)
                curveTo(5f, 15.6f, 4.6f, 16f, 4f, 16f)
                lineTo(3f, 16f)
                curveTo(2.4f, 16f, 2f, 15.6f, 2f, 15f)
                lineTo(2f, 9f)
                curveTo(2f, 8.4f, 2.4f, 8f, 3f, 8f)
                close()
            }
            // Left inner collar
            fillPath {
                moveTo(7f, 9.5f); lineTo(7f, 14.5f); lineTo(5.5f, 14.5f); lineTo(5.5f, 9.5f); close()
            }
            // Center gripping handle
            strokePath(width = 2.4f) {
                moveTo(7f, 12f); lineTo(17f, 12f)
            }
            // Right inner collar
            fillPath {
                moveTo(18.5f, 9.5f); lineTo(18.5f, 14.5f); lineTo(17f, 14.5f); lineTo(17f, 9.5f); close()
            }
            // Right weight plate
            fillPath {
                moveTo(21f, 8f)
                curveTo(21.6f, 8f, 22f, 8.4f, 22f, 9f)
                lineTo(22f, 15f)
                curveTo(22f, 15.6f, 21.6f, 16f, 21f, 16f)
                lineTo(20f, 16f)
                curveTo(19.4f, 16f, 19f, 15.6f, 19f, 15f)
                lineTo(19f, 9f)
                curveTo(19f, 8.4f, 19.4f, 8f, 20f, 8f)
                close()
            }
        }
    }

    /** Biomechanics / Runner breakdown: athletic runner in stride. */
    val Biomechanics: ImageVector by lazy {
        vector("Biomechanics") {
            // Head
            fillPath {
                moveTo(14f, 4.5f)
                curveTo(14f, 3.4f, 13.1f, 2.5f, 12f, 2.5f)
                curveTo(10.9f, 2.5f, 10f, 3.4f, 10f, 4.5f)
                curveTo(10f, 5.6f, 10.9f, 6.5f, 12f, 6.5f)
                curveTo(13.1f, 6.5f, 14f, 5.6f, 14f, 4.5f)
                close()
            }
            // Spine & legs in full running stride
            strokePath(width = 2f) {
                // Torso
                moveTo(12f, 7f); lineTo(11f, 13f)
                // Front leg high knee driving forward
                moveTo(11f, 13f); lineTo(16f, 14f); lineTo(15f, 20f)
                // Back leg trailing
                moveTo(11f, 13f); lineTo(6f, 17f); lineTo(4f, 21f)
                // Pumping arms
                moveTo(11.5f, 9f); lineTo(16f, 10.5f); lineTo(17f, 7.5f)
                moveTo(11.5f, 9f); lineTo(7.5f, 10.5f); lineTo(7f, 14f)
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
