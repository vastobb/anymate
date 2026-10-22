package com.anymate.studio.engine

import com.anymate.studio.model.ShapeKind
import com.anymate.studio.model.Vec2
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Turns clean geometric input into something a person would have drawn:
 * slightly wobbly lines, overshoot at the ends, tapered pressure and one or
 * two lighter "searching" passes on top. The caller supplies a detail level so
 * an agent can block a scene in roughly and refine it afterwards.
 */
data class HumanStrokeSpec(
    val points: List<Vec2>,
    val widthScale: Float = 1f,
    val opacityScale: Float = 1f
)

object HumanStroke {

    private const val TAU = (Math.PI * 2.0).toFloat()

    fun polyline(
        input: List<Vec2>,
        detail: String = "medium",
        seed: Int = 0,
        closed: Boolean = false
    ): List<HumanStrokeSpec> {
        val clean = dedupe(input)
        if (clean.size < 2) return emptyList()
        val level = level(detail)
        val step = when (level) {
            "easy" -> 7f
            "detailed" -> 2.2f
            else -> 3.8f
        }
        val wobble = when (level) {
            "easy" -> 1.7f
            "detailed" -> 0.6f
            else -> 1.05f
        }
        var points = resample(clean, step, closed)
        points = smooth(points, 2, closed)
        points = offsetByNoise(points, wobble, seed, closed)
        if (closed) points = points + points.first() else points = overshoot(points, level, seed)

        val out = ArrayList<HumanStrokeSpec>()
        if (level == "easy") {
            out.add(HumanStrokeSpec(points))
            return out
        }

        // Three overlapping bands: thin at both ends, full weight in the middle.
        val n = points.size
        val cut = (n * 0.2f).toInt().coerceIn(1, max(1, n / 3))
        val startEnd = (cut + 2).coerceAtMost(n)
        out.add(HumanStrokeSpec(points.subList(0, startEnd), 0.5f, 0.9f))
        val midStart = (cut - 1).coerceAtLeast(0)
        val midEnd = (n - cut + 2).coerceIn(midStart + 1, n)
        out.add(HumanStrokeSpec(points.subList(midStart, midEnd), 1f, 1f))
        if (n - cut > midStart) {
            out.add(HumanStrokeSpec(points.subList((n - cut).coerceAtLeast(midStart), n), 0.55f, 0.9f))
        }

        // A lighter second pass that searches for the same line again.
        val ghost = offsetByNoise(smooth(points, 1, false), wobble * 1.9f, seed + 91, false)
        out.add(HumanStrokeSpec(ghost, 0.5f, 0.32f))
        if (level == "detailed") {
            val third = offsetByNoise(smooth(points, 1, false), wobble * 1.2f, seed + 173, false)
            out.add(HumanStrokeSpec(third, 0.35f, 0.26f))
        }
        return out
    }

    fun shape(
        kind: ShapeKind,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        detail: String = "medium",
        seed: Int = 0
    ): List<HumanStrokeSpec> {
        val left = min(x1, x2)
        val right = max(x1, x2)
        val top = min(y1, y2)
        val bottom = max(y1, y2)
        return when (kind) {
            ShapeKind.LINE -> polyline(listOf(Vec2(x1, y1), Vec2(x2, y2)), detail, seed)
            ShapeKind.ARROW -> {
                val out = ArrayList<HumanStrokeSpec>()
                out.addAll(polyline(listOf(Vec2(x1, y1), Vec2(x2, y2)), detail, seed))
                val angle = atan2(y2 - y1, x2 - x1)
                val length = max(10f, hypot(x2 - x1, y2 - y1) * 0.2f)
                for (side in listOf(1f, -1f)) {
                    val headX = x2 - length * cos(angle + side * 0.45f)
                    val headY = y2 - length * sin(angle + side * 0.45f)
                    out.addAll(polyline(listOf(Vec2(x2, y2), Vec2(headX, headY)), detail, seed + side.toInt() * 17))
                }
                out
            }
            ShapeKind.RECT -> {
                val corners = listOf(
                    Vec2(left, top), Vec2(right, top), Vec2(right, bottom), Vec2(left, bottom), Vec2(left, top)
                )
                val out = ArrayList<HumanStrokeSpec>()
                for (i in 0 until 4) {
                    out.addAll(polyline(listOf(corners[i], corners[i + 1]), detail, seed + i * 31))
                }
                out
            }
            ShapeKind.ELLIPSE -> {
                val cx = (left + right) / 2f
                val cy = (top + bottom) / 2f
                val rx = max(1f, (right - left) / 2f)
                val ry = max(1f, (bottom - top) / 2f)
                val samples = when (level(detail)) {
                    "easy" -> 44
                    "detailed" -> 140
                    else -> 84
                }
                val start = (seed % 360) * (Math.PI / 180.0).toFloat()
                val points = ArrayList<Vec2>(samples + 1)
                for (i in 0..samples) {
                    val a = start + TAU * (i / samples.toFloat()) * 1.06f
                    val rwobble = 1f + 0.035f * sin(a * 3f + seed)
                    points.add(Vec2(cx + rx * cos(a) * rwobble, cy + ry * sin(a) * rwobble))
                }
                polyline(points, detail, seed, closed = false)
            }
        }
    }

    // ---- figure drawing ----------------------------------------------------

    private data class Joint(val x: Float, val y: Float)

    /** Blocky mannequin in a 0..1 height box, built from joint chains. */
    private fun pose(name: String): List<List<Joint>> {
        fun chain(vararg points: Float): List<Joint> =
            points.toList().chunked(2).map { Joint(it[0], it[1]) }
        return when (name) {
            "walk" -> listOf(
                chain(0.5f, 0.22f, 0.5f, 0.55f),                       // spine
                chain(0.5f, 0.26f, 0.37f, 0.38f, 0.33f, 0.52f),         // left arm forward
                chain(0.5f, 0.26f, 0.62f, 0.36f, 0.66f, 0.48f),         // right arm back
                chain(0.5f, 0.55f, 0.38f, 0.74f, 0.3f, 0.93f),          // left leg forward
                chain(0.5f, 0.55f, 0.6f, 0.74f, 0.68f, 0.92f)           // right leg back
            )
            "run" -> listOf(
                chain(0.54f, 0.2f, 0.46f, 0.54f),
                chain(0.54f, 0.24f, 0.38f, 0.31f, 0.3f, 0.4f),
                chain(0.54f, 0.24f, 0.68f, 0.36f, 0.74f, 0.3f),
                chain(0.46f, 0.54f, 0.3f, 0.66f, 0.2f, 0.82f),
                chain(0.46f, 0.54f, 0.62f, 0.7f, 0.72f, 0.88f)
            )
            "wave" -> listOf(
                chain(0.5f, 0.22f, 0.5f, 0.55f),
                chain(0.5f, 0.26f, 0.4f, 0.4f, 0.38f, 0.54f),
                chain(0.5f, 0.26f, 0.66f, 0.2f, 0.72f, 0.06f),
                chain(0.5f, 0.55f, 0.43f, 0.75f, 0.43f, 0.95f),
                chain(0.5f, 0.55f, 0.57f, 0.75f, 0.57f, 0.95f)
            )
            "sit" -> listOf(
                chain(0.5f, 0.24f, 0.5f, 0.58f),
                chain(0.5f, 0.28f, 0.4f, 0.44f, 0.42f, 0.58f),
                chain(0.5f, 0.28f, 0.6f, 0.44f, 0.58f, 0.58f),
                chain(0.5f, 0.58f, 0.68f, 0.68f, 0.72f, 0.88f),
                chain(0.5f, 0.58f, 0.66f, 0.72f, 0.7f, 0.92f)
            )
            else -> listOf(
                chain(0.5f, 0.22f, 0.5f, 0.55f),
                chain(0.5f, 0.26f, 0.42f, 0.4f, 0.4f, 0.52f),
                chain(0.5f, 0.26f, 0.58f, 0.4f, 0.6f, 0.52f),
                chain(0.5f, 0.55f, 0.44f, 0.75f, 0.44f, 0.95f),
                chain(0.5f, 0.55f, 0.56f, 0.75f, 0.56f, 0.95f)
            )
        }
    }

    /**
     * A loose figure study: head, spine, both arms and both legs, each joint
     * chain drawn as its own confident stroke.
     */
    fun figure(
        pose: String,
        x: Float,
        y: Float,
        size: Float,
        detail: String = "medium",
        seed: Int = 0
    ): List<HumanStrokeSpec> {
        val chains = pose(pose.lowercase())
        val out = ArrayList<HumanStrokeSpec>()
        val headCx = x + size * 0.5f
        val headCy = y + size * 0.1f
        val headR = size * 0.085f
        out.addAll(shape(ShapeKind.ELLIPSE, headCx - headR, headCy - headR * 1.15f, headCx + headR, headCy + headR * 1.15f, detail, seed + 7))
        // neck
        out.addAll(polyline(listOf(Vec2(headCx, headCy + headR), Vec2(x + size * 0.5f, y + size * 0.22f)), detail, seed + 3))
        chains.forEachIndexed { index, chain ->
            if (index == 0) return@forEachIndexed // spine already connected by the neck
            var previous = chain.first()
            for (joint in chain.drop(1)) {
                out.addAll(
                    polyline(
                        listOf(
                            Vec2(x + previous.x * size, y + previous.y * size),
                            Vec2(x + joint.x * size, y + joint.y * size)
                        ),
                        detail,
                        seed + index * 53 + (joint.x * 100).toInt()
                    )
                )
                previous = joint
            }
        }
        // The spine is one long gesture line from neck to hips.
        val spine = chains[0]
        out.addAll(
            polyline(
                spine.map { Vec2(x + it.x * size, y + it.y * size) },
                detail,
                seed + 11
            )
        )
        return out
    }

    // ---- geometry helpers ---------------------------------------------------

    private fun level(detail: String): String = when (detail.lowercase()) {
        "easy", "rough", "block" -> "easy"
        "detailed", "detail", "final", "clean" -> "detailed"
        else -> "medium"
    }

    private fun dedupe(points: List<Vec2>): List<Vec2> {
        val out = ArrayList<Vec2>(points.size)
        for (point in points) {
            val last = out.lastOrNull()
            if (last == null || abs(last.x - point.x) > 0.01f || abs(last.y - point.y) > 0.01f) {
                out.add(Vec2(point.x, point.y, point.pressure))
            }
        }
        return out
    }

    private fun resample(points: List<Vec2>, step: Float, closed: Boolean): List<Vec2> {
        val path = if (closed) points + points.first() else points
        val out = ArrayList<Vec2>()
        out.add(path.first())
        var carry = 0f
        for (i in 0 until path.size - 1) {
            val a = path[i]
            val b = path[i + 1]
            val segment = hypot(b.x - a.x, b.y - a.y)
            if (segment <= 0.0001f) continue
            var travelled = step - carry
            while (travelled < segment) {
                val t = travelled / segment
                out.add(Vec2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, 1f))
                travelled += step
            }
            carry = (segment - (travelled - step)).coerceAtLeast(0f)
        }
        val last = path.last()
        if (out.last().x != last.x || out.last().y != last.y) out.add(last)
        return out
    }

    private fun smooth(points: List<Vec2>, window: Int, closed: Boolean): List<Vec2> {
        if (points.size < 3) return points
        val out = ArrayList<Vec2>(points.size)
        for (i in points.indices) {
            var sx = 0f
            var sy = 0f
            var count = 0
            for (offset in -window..window) {
                var index = i + offset
                if (closed) {
                    index = ((index % points.size) + points.size) % points.size
                } else if (index < 0 || index > points.lastIndex) {
                    continue
                }
                sx += points[index].x
                sy += points[index].y
                count++
            }
            val base = points[i]
            out.add(Vec2(sx / count, sy / count, base.pressure))
        }
        return out
    }

    private fun offsetByNoise(points: List<Vec2>, amplitude: Float, seed: Int, closed: Boolean): List<Vec2> {
        if (points.size < 3) return points
        val out = ArrayList<Vec2>(points.size)
        val phaseA = (seed % 997) * 0.13f
        val phaseB = (seed % 389) * 0.31f
        for (i in points.indices) {
            val previous = points[(i - 1).coerceAtLeast(0)]
            val next = points[(i + 1).coerceAtMost(points.lastIndex)]
            var nx = -(next.y - previous.y)
            var ny = next.x - previous.x
            val length = hypot(nx, ny)
            if (length < 0.001f) {
                nx = 0f
                ny = 0f
            } else {
                nx /= length
                ny /= length
            }
            val wobble = sin(i * 0.33f + phaseA) * amplitude * 0.55f +
                sin(i * 0.07f + phaseB) * amplitude * 0.8f
            // Fade the wobble at the start and end so joins stay tight.
            val t = i / points.size.toFloat()
            val fade = if (closed) 1f else min(1f, min(t, 1f - t) * 6f)
            out.add(Vec2(points[i].x + nx * wobble * fade, points[i].y + ny * wobble * fade, points[i].pressure))
        }
        return out
    }

    /** Real pens run a little past the corner and flick up at the end. */
    private fun overshoot(points: List<Vec2>, level: String, seed: Int): List<Vec2> {
        if (points.size < 2) return points
        val extra = when (level) {
            "easy" -> 5f
            "detailed" -> 2.5f
            else -> 3.5f
        }
        val out = ArrayList<Vec2>(points.size + 2)
        val first = points.first()
        val second = points[1]
        var dx = first.x - second.x
        var dy = first.y - second.y
        var len = hypot(dx, dy)
        if (len > 0.001f) {
            dx /= len; dy /= len
            val jitter = ((seed % 5) - 2) * 0.4f
            out.add(Vec2(first.x + dx * extra + dy * jitter, first.y + dy * extra - dx * jitter, 0.9f))
        }
        out.addAll(points)
        val last = points.last()
        val before = points[points.size - 2]
        dx = last.x - before.x
        dy = last.y - before.y
        len = hypot(dx, dy)
        if (len > 0.001f) {
            dx /= len; dy /= len
            val flick = ((seed % 7) - 3) * 0.35f
            out.add(Vec2(last.x + dx * extra * 1.2f + dy * flick, last.y + dy * extra * 1.2f - dx * flick, 0.75f))
        }
        return out
    }
}
