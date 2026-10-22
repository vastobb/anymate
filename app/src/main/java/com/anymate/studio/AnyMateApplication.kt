package com.anymate.studio

import android.app.Application
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat

object AppFonts {
    private var regular: Typeface? = null
    private var bold: Typeface? = null

    fun init(context: Application) {
        if (regular != null) return
        regular = ResourcesCompat.getFont(context, R.font.inter) ?: Typeface.DEFAULT
        bold = Typeface.create(regular, Typeface.BOLD)
    }

    fun regular(): Typeface = regular ?: Typeface.DEFAULT
    fun bold(): Typeface = bold ?: Typeface.DEFAULT_BOLD
}

class AnyMateApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppFonts.init(this)
    }
}
