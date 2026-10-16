package com.example.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.LocalAiAgentIcon
import com.example.ui.components.TourElementBounds
import com.example.ui.components.TourShapeType
import com.example.ui.views.MainNavigationTab
import com.example.ui.DocAnizerViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MainBottomBar(
    currentTab: MainNavigationTab,
    isGestureModeEnabled: Boolean,
    batchQueueSize: Int,
    isFabExpanded: Boolean,
    onTabSelected: (MainNavigationTab) -> Unit,
    onOpenAiChatDrawer: () -> Unit,
    onToggleFabExpand: () -> Unit,
    activeTourStep: Int? = null,
    onTourElementClicked: ((Int) -> Unit)? = null,
    onReportElementBounds: ((step: Int, bounds: TourElementBounds) -> Unit)? = null,
    viewModel: DocAnizerViewModel,
    modifier: Modifier = Modifier
) {
    val enableAiAgentFab by viewModel.enableAiAgentFab.collectAsStateWithLifecycle()
    val enableAiAgentAnimation by viewModel.enableAiAgentAnimation.collectAsStateWithLifecycle()
    
    val fabRotation by animateFloatAsState(
        targetValue = if (isFabExpanded) 135f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "fab_rotation"
    )

    val fabScale by animateFloatAsState(
        targetValue = if (isFabExpanded) 1.15f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioHighBouncy),
        label = "fab_scale"
    )

    // Pulsierende AI-Agent Animationen (Leucht-Halo, Atmen-Skalierung, sanftes Neigen)
    val aiInfiniteTransition = rememberInfiniteTransition(label = "ai_agent_pulse_transition")
    val aiPulseScale by aiInfiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ai_pulse_scale"
    )
    val aiPulseAlpha by aiInfiniteTransition.animateFloat(
        initialValue = 0.38f,
        targetValue = 0.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ai_pulse_alpha"
    )
    val aiBreathingScale by aiInfiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ai_breathing_scale"
    )
    val aiGentleTilt by aiInfiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ai_gentle_tilt"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "fab_pulse_transition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fab_pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fab_pulse_alpha"
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
                windowInsets = WindowInsets(0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp)
                    .padding(top = 6.dp)
                    .testTag("main_bottom_nav_bar")
            ) {
                // 1. Dokumente Tab (Tour Schritt 6)
                val isDocanizerSelected = currentTab == MainNavigationTab.DOCANIZER
                val isStepDocActive = activeTourStep == 6
                NavigationBarItem(
                    selected = isDocanizerSelected,
                    onClick = {
                        if (isStepDocActive) onTourElementClicked?.invoke(6)
                        else onTabSelected(MainNavigationTab.DOCANIZER)
                    },
                    icon = {
                        Box(modifier = Modifier.padding(top = 6.dp)) {
                            Icon(
                                imageVector = if (isDocanizerSelected) Icons.Default.FolderSpecial else Icons.Default.Folder,
                                contentDescription = "Dokumente",
                                modifier = Modifier.size(if (isDocanizerSelected) 24.dp else 22.dp)
                            )
                        }
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
                        .padding(horizontal = 12.dp)
                        .onGloballyPositioned { coords ->
                            val bounds = coords.boundsInRoot()
                            val elemBounds = TourElementBounds(
                                centerX = bounds.center.x,
                                centerY = bounds.center.y,
                                widthPx = bounds.width,
                                heightPx = bounds.height,
                                shapeType = TourShapeType.ROUNDED_RECT
                            )
                            onReportElementBounds?.invoke(6, elemBounds)
                        }
                        .testTag("nav_item_docanizer")
                )

                Spacer(modifier = Modifier.weight(0.85f))

                // 2. Dashboard Tab (Tour Schritt 10)
                val isDashboardSelected = currentTab == MainNavigationTab.DASHBOARD
                val isStepDashActive = activeTourStep == 10
                NavigationBarItem(
                    selected = isDashboardSelected,
                    onClick = {
                        if (isStepDashActive) onTourElementClicked?.invoke(10)
                        else onTabSelected(MainNavigationTab.DASHBOARD)
                    },
                    icon = {
                        Box(modifier = Modifier.padding(top = 6.dp)) {
                            Icon(
                                imageVector = if (isDashboardSelected) Icons.Default.SpaceDashboard else Icons.Default.Dashboard,
                                contentDescription = "Dashboard",
                                modifier = Modifier.size(if (isDashboardSelected) 24.dp else 22.dp)
                            )
                        }
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
                        .padding(horizontal = 12.dp)
                        .onGloballyPositioned { coords ->
                            val bounds = coords.boundsInRoot()
                            val elemBounds = TourElementBounds(
                                centerX = bounds.center.x,
                                centerY = bounds.center.y,
                                widthPx = bounds.width,
                                heightPx = bounds.height,
                                shapeType = TourShapeType.ROUNDED_RECT
                            )
                            onReportElementBounds?.invoke(10, elemBounds)
                        }
                        .testTag("nav_item_dashboard")
                )

                Spacer(modifier = Modifier.weight(0.85f))

                // 3. Scan InBox Tab (regulärer Tab, kein Tour-Schritt)
                val isBatchSelected = currentTab == MainNavigationTab.BATCH_INBOX
                NavigationBarItem(
                    selected = isBatchSelected,
                    onClick = {
                        onTabSelected(MainNavigationTab.BATCH_INBOX)
                    },
                    icon = {
                        Box(modifier = Modifier.padding(top = 6.dp)) {
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
                        }
                    },
                    label = {
                        Text(
                            text = "Posteingang",
                            fontWeight = if (isBatchSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            fontSize = 10.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = navItemColors,
                    modifier = Modifier
                        .weight(1.25f)
                        .padding(horizontal = 12.dp)
                        .testTag("nav_item_batch_inbox")
                )
            }

            // 2. Overlapping FABs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.weight(1.25f))

                // Left FAB: Chatbot (Tour Schritt 7)
                val isStepAiActive = activeTourStep == 7
                if (enableAiAgentFab) {
                    Box(
                        modifier = Modifier
                            .weight(0.85f)
                            .offset(y = (-18).dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (enableAiAgentAnimation && !isStepAiActive) {
                            // Leuchtender, pulsierender Halo-Ringeffekt um den KI-Agenten
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = aiPulseAlpha),
                                modifier = Modifier.size((60 * aiPulseScale).dp)
                            ) {}
                        }

                        FloatingActionButton(
                            onClick = {
                                if (isStepAiActive) onTourElementClicked?.invoke(7)
                                else onOpenAiChatDrawer()
                            },
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary,
                            shape = CircleShape,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp, pressedElevation = 10.dp),
                            modifier = Modifier
                                .size(60.dp)
                                .graphicsLayer {
                                    val currentScale = if (isFabExpanded) 0.85f else if (enableAiAgentAnimation) aiBreathingScale else 1f
                                    scaleX = currentScale
                                    scaleY = currentScale
                                    rotationZ = if (isFabExpanded) -135f else if (enableAiAgentAnimation) aiGentleTilt else 0f
                                }
                                .onGloballyPositioned { coords ->
                                    if (isStepAiActive) {
                                        val bounds = coords.boundsInRoot()
                                        val elemBounds = TourElementBounds(
                                            centerX = bounds.center.x,
                                            centerY = bounds.center.y,
                                            widthPx = bounds.width,
                                            heightPx = bounds.height,
                                            shapeType = TourShapeType.CIRCLE
                                        )
                                        onReportElementBounds?.invoke(7, elemBounds)
                                    }
                                }
                                .testTag("main_chatbot_fab")
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                LocalAiAgentIcon(
                                    modifier = Modifier.size(38.dp),
                                    tint = MaterialTheme.colorScheme.onSecondary
                                )
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(0.85f))
                }

                Spacer(modifier = Modifier.weight(1.1f))

                // Right FAB: Scan/Import SpeedDial (Tour Schritt 1)
                val isStep1Active = activeTourStep == 1
                Box(
                    modifier = Modifier
                        .weight(0.85f)
                        .offset(y = (-18).dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isStep1Active) {
                        // Pulsierender Halo Ringeffekt im Normalzustand
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = pulseAlpha),
                            modifier = Modifier.size((60 * pulseScale).dp)
                        ) {}
                    }

                    FloatingActionButton(
                        onClick = {
                            if (isStep1Active) onTourElementClicked?.invoke(1)
                            else onToggleFabExpand()
                        },
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp, pressedElevation = 12.dp),
                        modifier = Modifier
                            .size(60.dp)
                            .graphicsLayer {
                                scaleX = fabScale
                                scaleY = fabScale
                            }
                            .onGloballyPositioned { coords ->
                                val bounds = coords.boundsInRoot()
                                onReportElementBounds?.invoke(
                                    1,
                                    TourElementBounds(
                                        centerX = bounds.center.x,
                                        centerY = bounds.center.y,
                                        widthPx = bounds.width,
                                        heightPx = bounds.height,
                                        shapeType = TourShapeType.CIRCLE
                                    )
                                )
                            }
                            .testTag("main_add_fab")
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_icon_add_in),
                            contentDescription = if (isFabExpanded) "Menü schließen" else "Scan- & Importmenü öffnen",
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier
                                .size(32.dp)
                                .graphicsLayer { rotationZ = fabRotation }
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1.25f))
            }
        }
    }
}
