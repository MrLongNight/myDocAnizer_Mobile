package com.example.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.LocalAiAgentIcon
import com.example.ui.views.MainNavigationTab

@Composable
fun MainBottomBar(
    currentTab: MainNavigationTab,
    isGestureModeEnabled: Boolean,
    batchQueueSize: Int,
    isFabExpanded: Boolean,
    onTabSelected: (MainNavigationTab) -> Unit,
    onOpenAiChatDrawer: () -> Unit,
    onToggleFabExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fabRotation by animateFloatAsState(
        targetValue = if (isFabExpanded) 45f else 0f,
        label = "fab_rotation"
    )

    AnimatedVisibility(
        visible = !isGestureModeEnabled && currentTab != MainNavigationTab.SCANNER,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        val navItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter
        ) {
            // 1. NavigationBar
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .testTag("main_bottom_nav_bar")
            ) {
                // 1. Dokumente Tab
                val isDocanizerSelected = currentTab == MainNavigationTab.DOCANIZER
                NavigationBarItem(
                    selected = isDocanizerSelected,
                    onClick = { onTabSelected(MainNavigationTab.DOCANIZER) },
                    icon = {
                        Icon(
                            imageVector = if (isDocanizerSelected) Icons.Default.FolderSpecial else Icons.Default.Folder,
                            contentDescription = "Dokumente",
                            modifier = Modifier.size(if (isDocanizerSelected) 24.dp else 22.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Dokumente",
                            fontWeight = if (isDocanizerSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            fontSize = if (isDocanizerSelected) 11.5.sp else 11.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = navItemColors,
                    modifier = Modifier
                        .weight(1.25f)
                        .testTag("nav_item_docanizer")
                )

                Spacer(modifier = Modifier.weight(0.85f))

                // 2. Dashboard Tab
                val isDashboardSelected = currentTab == MainNavigationTab.DASHBOARD
                NavigationBarItem(
                    selected = isDashboardSelected,
                    onClick = { onTabSelected(MainNavigationTab.DASHBOARD) },
                    icon = {
                        Icon(
                            imageVector = if (isDashboardSelected) Icons.Default.SpaceDashboard else Icons.Default.Dashboard,
                            contentDescription = "Dashboard",
                            modifier = Modifier.size(if (isDashboardSelected) 24.dp else 22.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Dashboard",
                            fontWeight = if (isDashboardSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            fontSize = if (isDashboardSelected) 11.5.sp else 11.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = navItemColors,
                    modifier = Modifier
                        .weight(1.1f)
                        .testTag("nav_item_dashboard")
                )

                Spacer(modifier = Modifier.weight(0.85f))

                // 3. Scan InBox Tab
                val isBatchSelected = currentTab == MainNavigationTab.BATCH_INBOX
                NavigationBarItem(
                    selected = isBatchSelected,
                    onClick = { onTabSelected(MainNavigationTab.BATCH_INBOX) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (batchQueueSize > 0) {
                                    Badge(containerColor = MaterialTheme.colorScheme.tertiary) {
                                        Text("$batchQueueSize")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isBatchSelected) Icons.Default.AllInbox else Icons.Default.Inbox,
                                contentDescription = "Scan Eingang",
                                modifier = Modifier.size(if (isBatchSelected) 24.dp else 22.dp)
                            )
                        }
                    },
                    label = {
                        Text(
                            text = "Scan Eingang",
                            fontWeight = if (isBatchSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            fontSize = if (isBatchSelected) 11.5.sp else 11.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = navItemColors,
                    modifier = Modifier
                        .weight(1.25f)
                        .testTag("nav_item_batch_inbox")
                )
            }

            // 2. Overlapping FABs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.weight(1.25f))

                // Left FAB: Chatbot
                Box(
                    modifier = Modifier
                        .weight(0.85f)
                        .offset(y = (-18).dp),
                    contentAlignment = Alignment.Center
                ) {
                    FloatingActionButton(
                        onClick = onOpenAiChatDrawer,
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp, pressedElevation = 10.dp),
                        modifier = Modifier
                            .size(62.dp)
                            .testTag("main_chatbot_fab")
                    ) {
                        LocalAiAgentIcon(
                            modifier = Modifier.size(46.dp),
                            tint = MaterialTheme.colorScheme.onSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1.1f))

                // Right FAB: Scan/Import SpeedDial
                Box(
                    modifier = Modifier
                        .weight(0.85f)
                        .offset(y = (-18).dp),
                    contentAlignment = Alignment.Center
                ) {
                    FloatingActionButton(
                        onClick = onToggleFabExpand,
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp, pressedElevation = 10.dp),
                        modifier = Modifier
                            .size(58.dp)
                            .testTag("main_add_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = if (isFabExpanded) "Menü schließen" else "Scan- & Importmenü öffnen",
                            modifier = Modifier
                                .size(30.dp)
                                .graphicsLayer { rotationZ = fabRotation }
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1.25f))
            }
        }
    }
}
