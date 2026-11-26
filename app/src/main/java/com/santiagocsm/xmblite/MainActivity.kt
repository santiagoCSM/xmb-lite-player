package com.santiagocsm.xmblite

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.window.OnBackInvokedDispatcher

class MainActivity : Activity() {
    private lateinit var menu: XmbMenuView
    private val appearance by lazy { getSharedPreferences("appearance", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        val background = WaveBackgroundView(this).apply {
            val savedEffect = appearance.getString("background", null)
            effect = WaveBackgroundView.Effect.entries.firstOrNull { it.id == savedEffect }
                ?: WaveBackgroundView.Effect.BLUE
            val savedMotion = appearance.getString("motion", null)
            motion = WaveBackgroundView.Motion.entries.firstOrNull { it.id == savedMotion }
                ?: WaveBackgroundView.Motion.FLUID
        }
        menu = XmbMenuView(this, background.effect, background.motion,
            onEffect = { effect ->
                background.effect = effect
                appearance.edit().putString("background", effect.id).apply()
            },
            onMotion = { motion ->
                background.motion = motion
                appearance.edit().putString("motion", motion.id).apply()
            })
        savedInstanceState?.getBundle("menu")?.let(menu::restoreSelection)
        setContentView(FrameLayout(this).apply {
            addView(background)
            addView(menu)
        })
        menu.requestFocus()

        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                if (!menu.goBack() && !moveTaskToBack(false)) finish()
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!menu.goBack()) super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle("menu", menu.saveSelection())
    }
}
