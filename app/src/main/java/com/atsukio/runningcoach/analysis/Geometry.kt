package com.atsukio.runningcoach.analysis

import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

internal data class Vec2(val x: Double, val y: Double) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(k: Double) = Vec2(x * k, y * k)
    val length: Double get() = hypot(x, y)
    val isValid: Boolean get() = !x.isNaN() && !y.isNaN()
}

internal fun midpoint(a: Vec2, b: Vec2) = Vec2((a.x + b.x) / 2, (a.y + b.y) / 2)

/** Interior angle at [vertex] between the segments to [a] and [b], in degrees. */
internal fun angleAt(vertex: Vec2, a: Vec2, b: Vec2): Double {
    val u = a - vertex
    val v = b - vertex
    val cos = (u.x * v.x + u.y * v.y) / (u.length * v.length)
    return Math.toDegrees(acos(cos.coerceIn(-1.0, 1.0)))
}

internal fun List<Double>.median(): Double? = percentile(0.5)

/** Linearly interpolated percentile ([p] in 0..1), ignoring NaN. */
internal fun List<Double>.percentile(p: Double): Double? {
    val sorted = filter { !it.isNaN() }.sorted()
    if (sorted.isEmpty()) return null
    val pos = p * (sorted.size - 1)
    val lo = floor(pos).toInt()
    val hi = ceil(pos).toInt()
    return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo)
}
