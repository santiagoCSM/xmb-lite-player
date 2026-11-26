package com.santiagocsm.xmblite

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable

internal enum class XmbIcon(val resource: Int) {
    MUSIC(R.drawable.ic_xmb_category_music),
    VIDEO(R.drawable.ic_xmb_category_video),
    SETTINGS(R.drawable.ic_xmb_category_settings),
    SONGS(R.drawable.ic_xmb_songs),
    ALBUMS(R.drawable.ic_xmb_albums),
    ARTISTS(R.drawable.ic_xmb_artists),
    NOW_PLAYING(R.drawable.ic_xmb_now_playing),
    LOCAL_VIDEOS(R.drawable.ic_xmb_local_videos),
    FOLDERS(R.drawable.ic_xmb_folders),
    APPEARANCE(R.drawable.ic_xmb_appearance),
    BACKGROUND(R.drawable.ic_xmb_animated_background),
    PERFORMANCE(R.drawable.ic_xmb_performance),
    INFORMATION(R.drawable.ic_xmb_information),
    PLAY(R.drawable.ic_xmb_play),
    PAUSE(R.drawable.ic_xmb_pause),
    PREVIOUS(R.drawable.ic_xmb_previous),
    NEXT(R.drawable.ic_xmb_next),
    REWIND(R.drawable.ic_xmb_rewind),
    FAST_FORWARD(R.drawable.ic_xmb_fast_forward),
    BACK(R.drawable.ic_xmb_back),
    APPLIED(R.drawable.ic_xmb_applied),
    ACCESS(R.drawable.ic_xmb_access),
    RETRY(R.drawable.ic_xmb_retry)
}

internal class XmbIcons(context: Context) {
    private companion object {
        const val SIZE = 24
    }

    private val vectors = Array<Drawable>(XmbIcon.entries.size) { index ->
        checkNotNull(context.getDrawable(XmbIcon.entries[index].resource)).mutate().apply {
            setBounds(0, 0, SIZE, SIZE)
        }
    }

    fun draw(canvas: Canvas, icon: XmbIcon, x: Float, y: Float, size: Float, alpha: Int) {
        val vector = vectors[icon.ordinal]
        if (vector.alpha != alpha) vector.alpha = alpha
        val saved = canvas.save()
        canvas.translate(x - size / 2f, y - size / 2f)
        val scale = size / SIZE
        canvas.scale(scale, scale)
        vector.draw(canvas)
        canvas.restoreToCount(saved)
    }
}
