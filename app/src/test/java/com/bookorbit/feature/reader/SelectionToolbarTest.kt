package com.bookorbit.feature.reader

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The selection toolbar takes the room its buttons need, even on a tablet-wide screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w1280dp-h800dp-land-xhdpi")
class SelectionToolbarTest {
    @get:Rule
    val compose = createComposeRule()

    private fun toolbarWidth(onDelete: (() -> Unit)?): Dp {
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                SelectionToolbar(
                    currentColor = null,
                    onColor = {},
                    onDelete = onDelete,
                    onCopy = {}, onNote = {}, onDefine = {}, onTranslate = {}, onSearch = {}, onReadAloud = {},
                    modifier = Modifier.testTag("toolbar"),
                )
            }
        }
        return compose.onNodeWithTag("toolbar").getUnclippedBoundsInRoot().width
    }

    @Test
    fun highlightToolbarWrapsItsButtons() {
        // Six 44dp actions plus padding; the screen is 1280dp wide.
        val width = toolbarWidth(onDelete = {})
        assertTrue("toolbar was $width wide", width < 320.dp)
    }

    @Test
    fun selectionToolbarWrapsItsButtons() {
        val width = toolbarWidth(onDelete = null)
        assertTrue("toolbar was $width wide", width < 320.dp)
    }
}
