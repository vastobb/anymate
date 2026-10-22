package com.anymate.studio.engine

import android.graphics.Paint
import com.anymate.studio.AppFonts
import com.anymate.studio.model.Element
import com.anymate.studio.model.PolygonElement
import com.anymate.studio.model.ShapeElement
import com.anymate.studio.model.ShapeKind
import com.anymate.studio.model.TextElement
import com.anymate.studio.model.Vec2
import com.anymate.studio.util.isLight
import com.anymate.studio.util.lerpColor
import com.anymate.studio.util.rotateHue
import com.anymate.studio.util.withAlpha
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class DirectorResult(
    val label: String,
    val startFrame: Int,
    val frameCount: Int,
    val fps: Int
)

/**
 * AnyMate's offline creative director. It paints every frame with the same
 * element primitives an MCP agent uses, one visible frame at a time. This gives
 * a no-API-key, no-network animation experience that is still fully live.
 */
object Director {

    private data class Palette(
        val ink: Long,
        val accent: Long,
        val accent2: Long,
        val warm: Long,
        val glow: Long
    )

    private data class FrameArt(
        val bg: List<Element>,
        val main: List<Element>,
        val fx: List<Element>
    )

    suspend fun animate(
        prompt: String,
        engine: AnimationEngine,
        pace: Boolean = true,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): DirectorResult {
        val kind = chooseKind(prompt)
        parseFps(prompt)?.let { engine.setFps(it) }
        val fps = engine.fps
        val targetCount = (fps * 2).coerceIn(24, 96)
        engine.pause()

        val pristine = engine.frames.size == 1 &&
            engine.frames[0].layers.all { it.elements.isEmpty() }
        val startIndex = if (pristine) 0 else engine.frames.size
        val available = AnimationEngine.MAX_FRAMES - (if (pristine) 0 else engine.frames.size)
        require(available >= 8) { "Not enough frame space. Delete some frames first." }
        val count = min(targetCount, available)

        if (pristine && engine.projectName == "Untitled") engine.renameProject(kind.label)

        val palette = paletteFor(engine.background)
        val bgLayer = 0
        val mainLayer = if (engine.layers.size > 1) 1 else 0
        val fxLayer = if (engine.layers.size > 2) engine.layers.lastIndex else mainLayer
        val delayMs = (1000L / fps).coerceIn(12L, 33L)

        for (i in 0 until count) {
            val t = i.toFloat() / count
            val frameIndex: Int
            if (i == 0 && pristine) {
                frameIndex = 0
            } else {
                frameIndex = engine.frames.size
                engine.addFrame(index = frameIndex, select = false)
            }
            val art = when (kind) {
                Kind.BOUNCE -> bounce(engine, t, palette)
                Kind.WALK -> walk(engine, t, palette)
                Kind.SPIN -> spin(engine, t, palette)
                Kind.PULSE -> pulse(engine, t, palette)
                Kind.FIREWORK -> fireworks(engine, t, palette)
                Kind.MORPH -> morph(engine, t, palette)
                Kind.WAVE -> wave(engine, t, palette)
                Kind.ORBIT -> orbit(engine, t, palette)
                Kind.HEART -> heart(engine, t, palette)
                Kind.TEXT -> typeText(engine, t, palette, prompt)
                Kind.ABSTRACT -> abstract(engine, t, palette)
            }
            if (art.bg.isNotEmpty()) engine.addElements(art.bg, frameIndex, bgLayer, select = false)
            if (art.main.isNotEmpty()) engine.addElements(art.main, frameIndex, mainLayer, select = false)
            if (art.fx.isNotEmpty()) engine.addElements(art.fx, frameIndex, fxLayer, select = false)
            engine.selectFrame(frameIndex)
            onProgress(i + 1, count)
            if (pace) delay(delayMs)
        }

        engine.selectFrame(startIndex)
        if (count > 1) engine.play()
        return DirectorResult(kind.label, startIndex, count, fps)
    }

    // ---- Scene selection --------------------------------------------------
    private enum class Kind(val label: String) {
        BOUNCE("Bouncing Ball"),
        WALK("Walk Cycle"),
        SPIN("Orbital Spin"),
        PULSE("Pulse Rings"),
        FIREWORK("Fireworks"),
        MORPH("Shape Morph"),
        WAVE("Flow Wave"),
        ORBIT("Solar Orbit"),
        HEART("Heart Loop"),
        TEXT("Kinetic Type"),
        ABSTRACT("Liquid Orbit")
    }

    private fun chooseKind(prompt: String): Kind {
        val p = prompt.lowercase()
        return when {
            p.contains("firework") || p.contains("explosion") || p.contains("spark") || p.contains("particle") -> Kind.FIREWORK
            p.contains("walk") || p.contains("run") || p.contains("figure") || p.contains("character") || p.contains("human") -> Kind.WALK
            p.contains("bounce") || p.contains("ball") || p.contains("jump") -> Kind.BOUNCE
            p.contains("heart") || p.contains("love") -> Kind.HEART
            p.contains("orbit") || p.contains("planet") || p.contains("space") || p.contains("galaxy") -> Kind.ORBIT
            p.contains("wave") || p.contains("flow") || p.contains("ribbon") || p.contains("line") -> Kind.WAVE
            p.contains("morph") || p.contains("shape") || p.contains("transform") -> Kind.MORPH
            p.contains("spin") || p.contains("rotate") || p.contains("wheel") -> Kind.SPIN
            p.contains("pulse") || p.contains("beat") || p.contains("breathe") || p.contains("ripple") -> Kind.PULSE
            p.contains("text") || p.contains("type") || p.contains("write") || p.contains("\"") -> Kind.TEXT
            else -> Kind.ABSTRACT
        }
    }

    private fun parseFps(prompt: String): Int? {
        val regex = Regex("(\\d{2})\\s*fps", RegexOption.IGNORE_CASE)
        val value = regex.find(prompt)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
        return if (value in 1..60) value else null
    }

    private fun paletteFor(background: Long): Palette {
        val light = background.isLight()
        return if (light) {
            Palette(
                ink = 0xFF17131F,
                accent = 0xFF7C3AED,
                accent2 = 0xFF0891B2,
                warm = 0xFFF97316,
                glow = 0xFFFFFFFF
            )
        } else {
            Palette(
                ink = 0xFFF7F4FF,
                accent = 0xFFA78BFA,
                accent2 = 0xFF22D3EE,
                warm = 0xFFFB923C,
                glow = 0xFFFFFFFF
            )
        }
    }

    // ---- Element helpers --------------------------------------------------
    private fun line(
        engine: AnimationEngine,
        x1: Float, y1: Float, x2: Float, y2: Float,
        color: Long, width: Float, opacity: Float = 1f
    ) = ShapeElement(
        id = engine.nextElementId(),
        kind = ShapeKind.LINE,
        x1 = x1, y1 = y1, x2 = x2, y2 = y2,
        strokeColor = color, fillColor = null,
        width = width, opacity = opacity
    )

    private fun ellipse(
        engine: AnimationEngine,
        cx: Float, cy: Float, rx: Float, ry: Float,
        fill: Long? = null, stroke: Long? = null, width: Float = 1f,
        opacity: Float = 1f
    ) = ShapeElement(
        id = engine.nextElementId(),
        kind = ShapeKind.ELLIPSE,
        x1 = cx - rx, y1 = cy - ry, x2 = cx + rx, y2 = cy + ry,
        strokeColor = (stroke ?: fill ?: 0xFF000000),
        fillColor = fill,
        width = width, opacity = opacity
    )

    private fun polygon(
        engine: AnimationEngine,
        points: List<Vec2>,
        fill: Long? = null,
        stroke: Long? = null,
        width: Float = 2f,
        opacity: Float = 1f,
        closed: Boolean = true
    ) = PolygonElement(
        id = engine.nextElementId(),
        points = points,
        closed = closed,
        strokeColor = stroke ?: fill ?: 0xFF000000,
        fillColor = fill,
        width = width,
        opacity = opacity
    )

    private fun rect(
        engine: AnimationEngine,
        x1: Float, y1: Float, x2: Float, y2: Float,
        fill: Long? = null, stroke: Long? = null, width: Float = 1f,
        opacity: Float = 1f
    ) = ShapeElement(
        id = engine.nextElementId(),
        kind = ShapeKind.RECT,
        x1 = x1, y1 = y1, x2 = x2, y2 = y2,
        strokeColor = stroke ?: fill ?: 0xFF000000,
        fillColor = fill,
        width = width, opacity = opacity
    )

    private fun text(
        engine: AnimationEngine,
        value: String,
        x: Float, y: Float, size: Float, color: Long,
        opacity: Float = 1f, bold: Boolean = true
    ) = TextElement(
        id = engine.nextElementId(),
        text = value, x = x, y = y, size = size, color = color,
        opacity = opacity, bold = bold
    )

    private fun regularPolygon(
        cx: Float, cy: Float, radius: Float, sides: Int,
        rotation: Float, squash: Float = 1f
    ): List<Vec2> {
        val points = ArrayList<Vec2>(sides)
        for (i in 0 until sides) {
            val a = rotation + i * (2f * PI.toFloat() / sides)
            points.add(Vec2(cx + cos(a) * radius, cy + sin(a) * radius * squash))
        }
        return points
    }

    private fun backgroundRings(engine: AnimationEngine, p: Palette, w: Float, h: Float): List<Element> {
        val out = ArrayList<Element>()
        val cx = w * 0.5f
        val cy = h * 0.5f
        out.add(ellipse(engine, cx, cy, min(w, h) * 0.42f, min(w, h) * 0.42f, stroke = p.accent2.withAlpha(0.12f), width = 2.5f))
        out.add(ellipse(engine, cx, cy, min(w, h) * 0.32f, min(w, h) * 0.32f, stroke = p.accent.withAlpha(0.10f), width = 2f))
        out.add(ellipse(engine, w * 0.82f, h * 0.18f, h * 0.13f, h * 0.13f, stroke = p.warm.withAlpha(0.16f), width = 2f))
        return out
    }

    // ---- Bouncing ball ----------------------------------------------------
    private fun bounce(engine: AnimationEngine, t: Float, p: Palette): FrameArt {
        val w = engine.canvasWidth.toFloat()
        val h = engine.canvasHeight.toFloat()
        val r = min(w, h) * 0.082f
        val ground = h * 0.76f
        val bg = ArrayList<Element>()
        val main = ArrayList<Element>()
        val fx = ArrayList<Element>()
        bg.addAll(backgroundRings(engine, p, w, h))
        bg.add(line(engine, w * 0.09f, ground + 2f, w * 0.91f, ground + 2f, p.ink.withAlpha(0.20f), 2.5f))
        fx.add(ellipse(engine, w * 0.20f, h * 0.22f, 4f, 4f, fill = p.accent2.withAlpha(0.5f)))
        fx.add(ellipse(engine, w * 0.86f, h * 0.60f, 3f, 3f, fill = p.accent.withAlpha(0.45f)))

        fun ballAt(tt: Float): Triple<Float, Float, Float> {
            val wrapped = ((tt % 1f) + 1f) % 1f
            val x = w * 0.5f + sin(wrapped * 2f * PI.toFloat()) * w * 0.18f
            val phase = (wrapped * 2f) % 1f
            val heightNorm = sin(phase * PI.toFloat())
            val squash = 1f - heightNorm
            val sy = 1f - 0.13f * squash.pow(2f)
            return Triple(x, heightNorm, sy)
        }

        val (x, height, sy) = ballAt(t)
        val rx = r * (1f + 0.16f * (1f - height).pow(3f))
        val ry = r * sy
        val cy = ground - ry - height * h * 0.34f

        // Motion echoes after the ball.
        for (k in 1..4) {
            val prev = ballAt(t - k * 0.012f)
            val alpha = 0.075f * (1f - k / 6f)
            main.add(
                ellipse(
                    engine,
                    prev.first,
                    ground - r * 0.9f - prev.second * h * 0.34f,
                    r * (0.25f - k * 0.03f).coerceAtLeast(0.08f),
                    r * (0.25f - k * 0.03f).coerceAtLeast(0.08f),
                    fill = p.accent.withAlpha(alpha), opacity = 1f
                )
            )
        }

        // Ground shadow and impact ring.
        val shadowAlpha = 0.20f * (1f - height * 0.55f)
        fx.add(
            engine.let {
                ellipse(
                    it, x, ground + r * 0.10f,
                    rx * (1.25f + height * 0.55f), r * 0.20f,
                    fill = p.ink.withAlpha(shadowAlpha)
                )
            }
        )
        if (height < 0.22f) {
            val ringT = (0.22f - height) / 0.22f
            bg.add(
                ellipse(
                    engine, x, ground,
                    rx * (1.4f + ringT * 1.1f), r * (0.28f + ringT * 0.2f),
                    stroke = p.accent2.withAlpha(0.35f * (1f - ringT)), width = 3f
                )
            )
            if (height < 0.07f) {
                for (i in 0 until 4) {
                    val dir = if (i % 2 == 0) -1f else 1f
                    val off = (i / 2 + 1) * 8f
                    fx.add(
                        line(
                            engine,
                            x + dir * (rx + off), ground - 5f - i * 3f,
                            x + dir * (rx + off + 16f), ground - 7f - i * 3f,
                            p.warm.withAlpha(0.55f), 2.5f
                        )
                    )
                }
            }
        }

        // Ball body + lighting.
        main.add(ellipse(engine, x, cy, rx, ry, fill = p.accent))
        main.add(
            ellipse(
                engine, x - rx * 0.30f, cy - ry * 0.34f,
                rx * 0.23f, ry * 0.20f,
                fill = p.glow.withAlpha(0.85f)
            )
        )
        main.add(
            ellipse(
                engine, x + rx * 0.32f, cy + ry * 0.30f,
                rx * 0.24f, ry * 0.16f,
                fill = p.accent2.withAlpha(0.35f)
            )
        )
        return FrameArt(bg, main, fx)
    }

    // ---- Walk cycle -------------------------------------------------------
    private fun walk(engine: AnimationEngine, t: Float, p: Palette): FrameArt {
        val w = engine.canvasWidth.toFloat()
        val h = engine.canvasHeight.toFloat()
        val cx = w * 0.5f
        val ground = h * 0.80f
        val phase = t * 2f * PI.toFloat() * 2f
        val bob = abs(sin(phase)) * h * 0.010f
        val hipY = h * 0.615f - bob
        val shoulderY = h * 0.455f - bob
        val headR = w * 0.043f
        val headY = shoulderY - headR * 1.55f
        val bone = max(3f, w * 0.0075f)
        val bg = ArrayList<Element>()
        val main = ArrayList<Element>()
        val fx = ArrayList<Element>()

        bg.addAll(backgroundRings(engine, p, w, h))
        bg.add(line(engine, w * 0.08f, ground + 3f, w * 0.92f, ground + 3f, p.ink.withAlpha(0.18f), 2f))
        bg.add(ellipse(engine, cx, ground + 2f, w * 0.15f, h * 0.013f, fill = p.ink.withAlpha(0.13f)))

        fun leg(legPhase: Float, tint: Long) {
            val footX = cx + sin(legPhase) * w * 0.135f
            val lift = max(0f, sin(legPhase + PI.toFloat() * 0.35f)) * h * 0.055f
            val footY = ground - lift
            val hipX = cx + if (sin(legPhase) >= 0f) w * 0.012f else -w * 0.012f
            val kneeX = (hipX + footX) / 2f + cos(legPhase) * w * 0.028f
            val kneeY = (hipY + footY) / 2f - h * 0.018f
            main.add(line(engine, hipX, hipY, kneeX, kneeY, tint, bone))
            main.add(line(engine, kneeX, kneeY, footX, footY, tint, bone))
            main.add(line(engine, footX - w * 0.018f, footY, footX + w * 0.024f, footY, tint, bone * 0.95f))
        }

        fun arm(armPhase: Float) {
            val shoulderX = cx + w * 0.015f
            val handX = cx - sin(armPhase) * w * 0.125f
            val handY = shoulderY + h * 0.135f + cos(armPhase) * h * 0.018f
            val elbowX = (shoulderX + handX) / 2f - cos(armPhase) * w * 0.022f
            val elbowY = (shoulderY + handY) / 2f + h * 0.010f
            main.add(line(engine, shoulderX, shoulderY + h * 0.015f, elbowX, elbowY, p.ink, bone * 0.9f))
            main.add(line(engine, elbowX, elbowY, handX, handY, p.ink, bone * 0.9f))
            main.add(ellipse(engine, handX, handY, w * 0.014f, w * 0.014f, fill = p.warm))
        }

        leg(phase, p.ink)
        leg(phase + PI.toFloat(), p.accent)
        arm(phase + PI.toFloat())
        main.add(line(engine, cx, hipY, cx, shoulderY, p.accent, bone * 1.65f))
        // Scarf.
        main.add(
            polygon(
                engine,
                listOf(
                    Vec2(cx - w * 0.035f, shoulderY + h * 0.005f),
                    Vec2(cx + w * 0.035f, shoulderY + h * 0.005f),
                    Vec2(cx + w * 0.06f, shoulderY + h * 0.035f),
                    Vec2(cx + w * 0.012f, shoulderY + h * 0.026f)
                ),
                fill = p.accent2, opacity = 0.95f
            )
        )
        main.add(ellipse(engine, cx, headY, headR, headR, fill = p.warm))
        main.add(ellipse(engine, cx - headR * 0.32f, headY - headR * 0.38f, headR * 0.22f, headR * 0.18f, fill = p.glow.withAlpha(0.65f)))
        main.add(line(engine, cx + headR * 0.78f, headY - headR * 0.2f, cx + headR * 1.25f, headY - headR * 0.42f, p.accent, bone * 0.9f))

        // Dust at footfall.
        val contact = abs(cos(phase))
        if (contact < 0.35f) {
            for (i in 0 until 3) {
                fx.add(ellipse(engine, cx + (i - 1) * w * 0.05f, ground - 2f - i * 3f, 3f + i, 3f + i, fill = p.ink.withAlpha(0.14f)))
            }
        }
        return FrameArt(bg, main, fx)
    }

    // ---- Spin -------------------------------------------------------------
    private fun spin(engine: AnimationEngine, t: Float, p: Palette): FrameArt {
        val w = engine.canvasWidth.toFloat()
        val h = engine.canvasHeight.toFloat()
        val cx = w * 0.5f
        val cy = h * 0.5f
        val radius = min(w, h) * 0.24f
        val bg = ArrayList<Element>()
        val main = ArrayList<Element>()
        val fx = ArrayList<Element>()
        bg.addAll(backgroundRings(engine, p, w, h))
        main.add(
            polygon(
                engine,
                regularPolygon(cx, cy, radius, 3, t * 2f * PI.toFloat() - PI.toFloat() / 2f),
                fill = p.accent, stroke = p.accent.withAlpha(0.4f), width = 3f
            )
        )
        main.add(
            polygon(
                engine,
                regularPolygon(cx, cy, radius * 0.72f, 4, -t * 4f * PI.toFloat() + PI.toFloat() / 4f),
                fill = null, stroke = p.accent2, width = max(3f, w * 0.006f)
            )
        )
        main.add(
            polygon(
                engine,
                regularPolygon(cx, cy, radius * 0.34f, 6, t * 6f * PI.toFloat()),
                fill = p.warm.withAlpha(0.9f), stroke = p.ink.withAlpha(0.25f), width = 2f
            )
        )
        main.add(ellipse(engine, cx, cy, radius * 0.10f, radius * 0.10f, fill = p.glow.withAlpha(0.9f)))
        val dotAngle = t * 2f * PI.toFloat() * 2f
        val dotR = radius * 1.5f
        fx.add(ellipse(engine, cx + cos(dotAngle) * dotR, cy + sin(dotAngle) * dotR, w * 0.018f, w * 0.018f, fill = p.warm))
        fx.add(ellipse(engine, cx + cos(dotAngle + PI.toFloat()) * dotR, cy + sin(dotAngle + PI.toFloat()) * dotR, w * 0.011f, w * 0.011f, fill = p.accent2))
        return FrameArt(bg, main, fx)
    }

    // ---- Pulse ------------------------------------------------------------
    private fun pulse(engine: AnimationEngine, t: Float, p: Palette): FrameArt {
        val w = engine.canvasWidth.toFloat()
        val h = engine.canvasHeight.toFloat()
        val cx = w * 0.5f
        val cy = h * 0.5f
        val base = min(w, h) * 0.10f
        val maxR = min(w, h) * 0.42f
        val bg = ArrayList<Element>()
        val main = ArrayList<Element>()
        bg.addAll(backgroundRings(engine, p, w, h))
        for (i in 0 until 5) {
            val phase = (t + i / 5f) % 1f
            val radius = base + (maxR - base) * phase
            val alpha = (1f - phase).pow(1.5f) * 0.65f
            main.add(
                ellipse(
                    engine, cx, cy, radius, radius,
                    stroke = lerpColor(p.accent, p.accent2, i / 4f).withAlpha(alpha),
                    width = 2f + 4f * (1f - phase)
                )
            )
        }
        val heartbeat = 1f + 0.10f * sin(t * 2f * PI.toFloat() * 2f).pow(2f).let { it * it }
        main.add(ellipse(engine, cx, cy, base * heartbeat, base * heartbeat, fill = p.accent))
        main.add(ellipse(engine, cx - base * 0.25f, cy - base * 0.25f, base * 0.32f, base * 0.24f, fill = p.glow.withAlpha(0.55f)))
        for (i in 0 until 8) {
            val a = t * 2f * PI.toFloat() + i * PI.toFloat() / 4f
            val r = base * 1.75f
            main.add(ellipse(engine, cx + cos(a) * r, cy + sin(a) * r, 2.5f, 2.5f, fill = p.warm.withAlpha(0.6f)))
        }
        return FrameArt(bg, main, emptyList())
    }

    // ---- Fireworks --------------------------------------------------------
    private fun fireworks(engine: AnimationEngine, t: Float, p: Palette): FrameArt {
        val w = engine.canvasWidth.toFloat()
        val h = engine.canvasHeight.toFloat()
        val bg = ArrayList<Element>()
        val main = ArrayList<Element>()
        val fx = ArrayList<Element>()
        // A deliberate midnight scene for this preset.
        bg.add(rect(engine, 0f, 0f, w, h, fill = 0xFF07060D, width = 0f))
        for (i in 0 until 26) {
            val sx = ((i * 137.5f) % w)
            val sy = ((i * 79.3f) % (h * 0.72f)) + h * 0.04f
            val twinkle = 0.14f + 0.14f * (sin(t * 2f * PI.toFloat() * 2f + i).toFloat() * 0.5f + 0.5f)
            fx.add(ellipse(engine, sx, sy, 1.5f + (i % 3), 1.5f + (i % 3), fill = 0xFFFFFFFF.withAlpha(twinkle.toDouble().toFloat())))
        }
        val centers = listOf(
            Triple(w * 0.27f, h * 0.34f, 0f),
            Triple(w * 0.73f, h * 0.27f, 0.34f),
            Triple(w * 0.50f, h * 0.58f, 0.66f)
        )
        centers.forEachIndexed { burst, center ->
            val (bx, by, offset) = center
            val age = ((t + offset) % 1f)
            if (age < 0.82f) {
                val normalized = age / 0.82f
                val color = when (burst) {
                    0 -> p.accent
                    1 -> p.accent2
                    else -> p.warm
                }
                val count = 18
                for (i in 0 until count) {
                    val angle = i * (2f * PI.toFloat() / count) + burst * 0.6f
                    val speed = (0.14f + 0.10f * ((i * 7) % 5) / 4f) * min(w, h)
                    val radius = normalized * speed
                    val tail = (radius - speed * 0.08f).coerceAtLeast(0f)
                    val alpha = ((1f - normalized).pow(1.4f) * 0.95f)
                    val c = if (i % 4 == 0) p.glow.withAlpha(alpha * 0.85f) else color.withAlpha(alpha)
                    main.add(
                        line(
                            engine,
                            bx + cos(angle) * tail, by + sin(angle) * tail,
                            bx + cos(angle) * radius, by + sin(angle) * radius,
                            c, 2.2f + 1.4f * (1f - normalized)
                        )
                    )
                }
                if (normalized < 0.25f) {
                    val glowAlpha = (1f - normalized / 0.25f) * 0.25f
                    fx.add(ellipse(engine, bx, by, min(w, h) * 0.09f, min(w, h) * 0.09f, fill = color.withAlpha(glowAlpha)))
                }
            }
        }
        return FrameArt(bg, main, fx)
    }

    // ---- Morph ------------------------------------------------------------
    private fun morph(engine: AnimationEngine, t: Float, p: Palette): FrameArt {
        val w = engine.canvasWidth.toFloat()
        val h = engine.canvasHeight.toFloat()
        val cx = w * 0.5f
        val cy = h * 0.5f
        val base = min(w, h) * 0.25f
        val bg = ArrayList<Element>()
        val main = ArrayList<Element>()
        val fx = ArrayList<Element>()
        bg.addAll(backgroundRings(engine, p, w, h))

        val seg = (floor(t * 3f)).toInt().coerceIn(0, 2)
        val local = (t * 3f) - seg
        val smooth = local * local * (3f - 2f * local)
        fun radiusFor(shape: Int, angle: Float): Float = when (shape) {
            0 -> 1f
            1 -> 0.62f + 0.38f * abs(cos(angle * 2.5f))
            else -> 0.78f + 0.22f * sin(angle * 3f)
        }

        val sides = 48
        val points = ArrayList<Vec2>(sides)
        val rotation = t * 2f * PI.toFloat()
        for (i in 0 until sides) {
            val angle = rotation + i * 2f * PI.toFloat() / sides
            val r1 = radiusFor(seg, angle) * base
            val r2 = radiusFor((seg + 1) % 3, angle) * base
            val r = r1 + (r2 - r1) * smooth
            points.add(Vec2(cx + cos(angle) * r, cy + sin(angle) * r))
        }
        main.add(
            polygon(
                engine, points,
                fill = p.accent.withAlpha(0.92f),
                stroke = p.accent2.withAlpha(0.85f),
                width = max(2f, w * 0.005f)
            )
        )
        val inner = points.map {
            Vec2(cx + (it.x - cx) * 0.55f, cy + (it.y - cy) * 0.55f)
        }
        main.add(
            polygon(
                engine, inner,
                fill = null, stroke = p.warm.withAlpha(0.65f), width = 2f
            )
        )
        for (i in 0 until 3) {
            val a = -t * 4f * PI.toFloat() + i * 2f * PI.toFloat() / 3f
            fx.add(ellipse(engine, cx + cos(a) * base * 1.35f, cy + sin(a) * base * 1.35f, w * 0.012f, w * 0.012f, fill = p.warm))
        }
        return FrameArt(bg, main, fx)
    }

    // ---- Wave -------------------------------------------------------------
    private fun wave(engine: AnimationEngine, t: Float, p: Palette): FrameArt {
        val w = engine.canvasWidth.toFloat()
        val h = engine.canvasHeight.toFloat()
        val bg = ArrayList<Element>()
        val main = ArrayList<Element>()
        val fx = ArrayList<Element>()
        bg.addAll(backgroundRings(engine, p, w, h))
        val segments = 40
        val amplitude = h * 0.10f
        val ribbonY = h * 0.54f
        for (i in 0 until segments) {
            val f1 = i / segments.toFloat()
            val f2 = (i + 1) / segments.toFloat()
            fun yAt(f: Float): Float {
                val a = f * 2f * PI.toFloat() * 1.6f + t * 2f * PI.toFloat()
                return ribbonY + sin(a) * amplitude + sin(a * 0.5f + t * 3f) * amplitude * 0.25f
            }
            val color = lerpColor(p.accent, p.accent2, f1)
            main.add(line(engine, f1 * w, yAt(f1), f2 * w, yAt(f2), color, 7f))
            main.add(
                line(
                    engine, f1 * w, yAt(f1) + h * 0.045f, f2 * w, yAt(f2) + h * 0.045f,
                    p.warm.withAlpha(0.35f), 3f
                )
            )
            if (i % 5 == 0) {
                fx.add(ellipse(engine, f1 * w, yAt(f1) - h * 0.035f, 3.5f, 3.5f, fill = p.glow.withAlpha(0.5f)))
            }
        }
        return FrameArt(bg, main, fx)
    }

    // ---- Orbit ------------------------------------------------------------
    private fun orbit(engine: AnimationEngine, t: Float, p: Palette): FrameArt {
        val w = engine.canvasWidth.toFloat()
        val h = engine.canvasHeight.toFloat()
        val cx = w * 0.5f
        val cy = h * 0.5f
        val base = min(w, h)
        val bg = ArrayList<Element>()
        val main = ArrayList<Element>()
        val fx = ArrayList<Element>()
        bg.addAll(backgroundRings(engine, p, w, h))
        val orbits = listOf(0.18f to p.accent2, 0.29f to p.warm, 0.39f to p.accent)
        orbits.forEachIndexed { i, (radiusFactor, color) ->
            val radius = base * radiusFactor
            bg.add(ellipse(engine, cx, cy, radius, radius * 0.62f, stroke = color.withAlpha(0.20f), width = 2f))
            val speed = 1.1f + i * 0.55f
            val angle = t * 2f * PI.toFloat() * speed + i * 2.1f
            val px = cx + cos(angle) * radius
            val py = cy + sin(angle) * radius * 0.62f
            fx.add(ellipse(engine, px, py, base * (0.016f - i * 0.002f), base * (0.016f - i * 0.002f), fill = color))
        }
        main.add(ellipse(engine, cx, cy, base * 0.095f, base * 0.095f, fill = p.warm))
        main.add(ellipse(engine, cx - base * 0.025f, cy - base * 0.028f, base * 0.045f, base * 0.038f, fill = p.glow.withAlpha(0.55f)))
        // Fixed stars.
        for (i in 0 until 16) {
            val sx = ((i * 149f) % w)
            val sy = ((i * 83f) % h)
            fx.add(ellipse(engine, sx, sy, 1.5f + (i % 2), 1.5f + (i % 2), fill = p.ink.withAlpha(0.18f)))
        }
        return FrameArt(bg, main, fx)
    }

    // ---- Heart ------------------------------------------------------------
    private fun heart(engine: AnimationEngine, t: Float, p: Palette): FrameArt {
        val w = engine.canvasWidth.toFloat()
        val h = engine.canvasHeight.toFloat()
        val cx = w * 0.5f
        val cy = h * 0.46f
        val scale = min(w, h) * 0.018f
        val pulseScale = 1f + 0.06f * sin(t * 2f * PI.toFloat() * 2f)
        val bg = ArrayList<Element>()
        val main = ArrayList<Element>()
        val fx = ArrayList<Element>()
        bg.addAll(backgroundRings(engine, p, w, h))
        val points = ArrayList<Vec2>(48)
        for (i in 0 until 48) {
            val a = i * 2f * PI.toFloat() / 48f
            val hx = 16f * sin(a).pow(3f)
            val hy = 13f * cos(a) - 5f * cos(2f * a) - 2f * cos(3f * a) - cos(4f * a)
            points.add(Vec2(cx + hx * scale * pulseScale, cy - hy * scale * pulseScale))
        }
        main.add(polygon(engine, points, fill = 0xFFF43F5E, stroke = 0xFFFB7185, width = 3f))
        main.add(ellipse(engine, cx - w * 0.055f, cy - h * 0.055f, w * 0.035f, h * 0.022f, fill = 0xFFFFFFFF.withAlpha(0.30f)))
        for (i in 0 until 5) {
            val a = t * 2f * PI.toFloat() + i * 2f * PI.toFloat() / 5f
            val r = min(w, h) * (0.28f + 0.02f * sin(t * 4f + i))
            fx.add(ellipse(engine, cx + cos(a) * r, cy + sin(a) * r, w * 0.010f, w * 0.010f, fill = p.warm.withAlpha(0.65f)))
        }
        return FrameArt(bg, main, fx)
    }

    // ---- Kinetic type -----------------------------------------------------
    private fun typeText(engine: AnimationEngine, t: Float, p: Palette, prompt: String): FrameArt {
        val w = engine.canvasWidth.toFloat()
        val h = engine.canvasHeight.toFloat()
        val quoted = Regex("\"([^\"]+)\"").find(prompt)?.groupValues?.getOrNull(1)
        val cleaned = quoted ?: prompt
            .replace(Regex("(?i)animate|make|create|text|type|write|please"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "ANYMATE" }
        val full = cleaned.uppercase().take(20)
        val visibleCount = ((t * 1.25f + 0.05f) * full.length).toInt().coerceIn(1, full.length)
        val typed = full.take(visibleCount)
        val cursorOn = ((t * 4f).toInt() % 2 == 0) || visibleCount < full.length
        val size = min(h * 0.15f, w * 0.90f / (full.length * 0.62f)).coerceAtLeast(18f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = AppFonts.bold()
        }
        val fullWidth = paint.measureText(full)
        val x = (w - fullWidth) / 2f
        val baseline = h * 0.53f
        val bg = ArrayList<Element>()
        val main = ArrayList<Element>()
        val fx = ArrayList<Element>()
        bg.addAll(backgroundRings(engine, p, w, h))
        main.add(rect(engine, x - w * 0.02f, baseline - size * 0.82f, x + fullWidth + w * 0.02f, baseline + size * 0.28f, fill = p.accent.withAlpha(0.10f), width = 0f))
        main.add(text(engine, if (cursorOn) "$typed|" else typed, x, baseline, size, p.ink))
        val progress = visibleCount.toFloat() / full.length
        main.add(line(engine, x, baseline + size * 0.22f, x + fullWidth * progress, baseline + size * 0.22f, p.accent2, 4f))
        val caption = "MADE WITH ANYMATE"
        val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size * 0.22f
            typeface = AppFonts.regular()
        }
        val captionX = (w - captionPaint.measureText(caption)) / 2f
        fx.add(text(engine, caption, captionX, h * 0.72f, size * 0.22f, p.ink.withAlpha(0.35f), bold = false))
        return FrameArt(bg, main, fx)
    }

    // ---- Abstract fallback ------------------------------------------------
    private fun abstract(engine: AnimationEngine, t: Float, p: Palette): FrameArt {
        val w = engine.canvasWidth.toFloat()
        val h = engine.canvasHeight.toFloat()
        val cx = w * 0.5f
        val cy = h * 0.5f
        val base = min(w, h) * 0.22f
        val bg = ArrayList<Element>()
        val main = ArrayList<Element>()
        val fx = ArrayList<Element>()
        bg.addAll(backgroundRings(engine, p, w, h))
        for (i in 0 until 3) {
            val a = t * 2f * PI.toFloat() * (1f + i * 0.4f) + i * 2.1f
            val r = base * (0.95f + 0.25f * i)
            main.add(
                earringBlob(
                    engine,
                    cx + cos(a) * r * 0.45f,
                    cy + sin(a) * r * 0.45f,
                    base * (0.55f - i * 0.10f),
                    listOf(p.accent, p.accent2, p.warm)[i].withAlpha(0.70f)
                )
            )
        }
        main.add(ellipse(engine, cx, cy, base * 0.09f, base * 0.09f, fill = p.glow.withAlpha(0.85f)))
        for (i in 0 until 12) {
            val a = -t * 2f * PI.toFloat() * 1.5f + i * 2f * PI.toFloat() / 12f
            fx.add(ellipse(engine, cx + cos(a) * base * 1.55f, cy + sin(a) * base * 1.55f, 2.5f + (i % 3), 2.5f + (i % 3), fill = p.ink.withAlpha(0.35f)))
        }
        return FrameArt(bg, main, fx)
    }

    private fun earringBlob(engine: AnimationEngine, cx: Float, cy: Float, r: Float, color: Long): PolygonElement {
        val points = ArrayList<Vec2>(32)
        for (i in 0 until 32) {
            val a = i * 2f * PI.toFloat() / 32f
            val wobble = 0.85f + 0.15f * sin(a * 3f + PI.toFloat() / 2f)
            points.add(Vec2(cx + cos(a) * r * wobble, cy + sin(a) * r * wobble))
        }
        return polygon(engine, points, fill = color, stroke = color.withAlpha(0.9f), width = 2f)
    }
}
