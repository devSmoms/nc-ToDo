package de.devsmoms.nctodo.quickadd

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile ("Neue Aufgabe") in the pull-down menu. Opens the quick add window.
 * On a locked device the user has to unlock first, so nobody can see list names or add
 * tasks from the lock screen.
 */
class QuickAddTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Neue Aufgabe"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = "NC Todo"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { openQuickAdd() } else openQuickAdd()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openQuickAdd() {
        val intent = QuickAddActivity.intent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
