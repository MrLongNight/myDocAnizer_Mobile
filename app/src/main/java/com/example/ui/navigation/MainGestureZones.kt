package com.example.ui.navigation

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun MainGestureZones(
    isGestureModeEnabled: Boolean,
    onOpenChatbot: () -> Unit,
    onOpenScanImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Wisch-Zone Links (Alignment.CenterStart): Wisch von links nach rechts -> Öffnet Chatbot
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(top = 92.dp, bottom = 100.dp)
                .fillMaxHeight()
                .width(40.dp)
                .systemGestureExclusion()
                .testTag("gesture_zone_left_chatbot")
                .pointerInput(Unit) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onDragEnd = { totalDrag = 0f },
                        onDragCancel = { totalDrag = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            totalDrag += dragAmount
                            if (totalDrag > 32f) {
                                change.consume()
                                totalDrag = 0f
                                onOpenChatbot()
                            }
                        }
                    )
                }
        )

        // Wisch-Zone Rechts (Alignment.CenterEnd): Wisch von rechts nach links -> Öffnet Scan/Import-Panel
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(top = 92.dp, bottom = 100.dp)
                .fillMaxHeight()
                .width(40.dp)
                .systemGestureExclusion()
                .testTag("gesture_zone_right_scan_import")
                .pointerInput(Unit) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onDragEnd = { totalDrag = 0f },
                        onDragCancel = { totalDrag = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            totalDrag += dragAmount
                            if (totalDrag < -32f) {
                                change.consume()
                                totalDrag = 0f
                                onOpenScanImport()
                            }
                        }
                    )
                }
        )
    }
}
