package com.bookorbit.feature.player

import android.app.Application
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ControllerInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The app's own controllers (the UI's and Media3's notification controller) must get the player
 * commands. Without them Media3 posts no media notification and never starts the playback service
 * in the foreground.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ConnectionResultForAppTest {
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        player = ExoPlayer.Builder(context).build()
        session = MediaSession.Builder(context, player).build()
    }

    @After
    fun tearDown() {
        session.release()
        player.release()
    }

    private fun appController() = ControllerInfo.createTestOnlyControllerInfo(
        "com.pranavajk.orbiter", 1, 1000, 0, 0, true, Bundle.EMPTY, true,
    )

    @Test
    fun appControllersGetThePlayerCommands() {
        val result = connectionResultForApp(session, appController())
        assertTrue(result.isAccepted)
        // The notification controller needs these to see the book and offer its controls.
        assertTrue(result.availablePlayerCommands.contains(Player.COMMAND_PLAY_PAUSE))
        assertTrue(result.availablePlayerCommands.contains(Player.COMMAND_GET_TIMELINE))
        assertTrue(result.availablePlayerCommands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
    }

    @Test
    fun appControllersKeepTheDefaultSessionCommandsAndTheQueueCommand() {
        val defaults = MediaSession.ConnectionResult.AcceptedResultBuilder(session, appController()).build()
        val result = connectionResultForApp(session, appController())
        assertTrue(result.availableSessionCommands.contains(BookQueueCommand.command))
        assertTrue(result.availableSessionCommands.commands.containsAll(defaults.availableSessionCommands.commands))
    }
}
