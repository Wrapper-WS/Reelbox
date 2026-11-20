package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.MediaPermissionHelper
import com.example.data.MediaPermissionState
import com.example.data.MediaType
import com.example.ui.gallery.FavouritesScreen
import com.example.ui.gallery.MediaGridScreen
import com.example.ui.gallery.MediaViewerOverlay
import com.example.ui.gallery.MediaViewerSession
import com.example.ui.home.HomeFeedScreen
import com.example.ui.navigation.ROUTE_FAVOURITES
import com.example.ui.navigation.ROUTE_HOME
import com.example.ui.navigation.ROUTE_PHOTOS
import com.example.ui.navigation.ROUTE_VIDEOS
import com.example.ui.navigation.ReelboxBottomBar
import com.example.ui.navigation.ReelboxNavigationRail
import com.example.ui.navigation.ReelboxTab
import com.example.ui.screens.PermissionScreen
import com.example.ui.theme.ReelboxMotion
import com.example.ui.theme.ReelboxThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReelboxApp(
    viewModel: ReelboxViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    val colors = ReelboxThemeTokens.colors

    var isFullScreenLandscape by rememberSaveable { mutableStateOf(false) }
    var launchSplashFinished by rememberSaveable { mutableStateOf(false) }

    // Keep all non-fullscreen screens locked to portrait; switch to immersive landscape only when requested.
    LaunchedEffect(activity, isFullScreenLandscape, uiState.hasMediaAccess) {
        val hostActivity = activity ?: return@LaunchedEffect
        val immersiveLandscape = isFullScreenLandscape && uiState.hasMediaAccess
        val targetOrientation = if (immersiveLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        if (hostActivity.requestedOrientation != targetOrientation) {
            hostActivity.requestedOrientation = targetOrientation
        }

        val controller = WindowCompat.getInsetsController(
            hostActivity.window,
            hostActivity.window.decorView,
        )
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (immersiveLandscape) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(activity) {
        onDispose {
            activity?.let { hostActivity ->
                val controller = WindowCompat.getInsetsController(
                    hostActivity.window,
                    hostActivity.window.decorView,
                )
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val evaluated = MediaPermissionHelper.evaluatePermissionState(
            context = context,
            hasRequestedBefore = true,
            shouldShowRationale = MediaPermissionHelper.shouldShowAnyRationale(activity),
        )
        viewModel.onPermissionEvaluated(
            newState = evaluated,
            forceReload = evaluated.hasAnyAccess,
        )
    }

    val requestMediaAccess: () -> Unit = {
        viewModel.markPermissionRequested()
        permissionLauncher.launch(MediaPermissionHelper.requiredMediaPermissions())
    }

    // Re-evaluate permissions on initial load and whenever hasRequestedPermission restores from DataStore.
    LaunchedEffect(uiState.hasRequestedPermission) {
        val evaluated = MediaPermissionHelper.evaluatePermissionState(
            context = context,
            hasRequestedBefore = uiState.hasRequestedPermission,
            shouldShowRationale = MediaPermissionHelper.shouldShowAnyRationale(activity),
        )
        viewModel.onPermissionEvaluated(newState = evaluated)
    }

    // Re-evaluate permissions and silently sync library changes when returning from background or Settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val evaluated = MediaPermissionHelper.evaluatePermissionState(
            context = context,
            hasRequestedBefore = uiState.hasRequestedPermission,
            shouldShowRationale = MediaPermissionHelper.shouldShowAnyRationale(activity),
        )
        viewModel.onPermissionEvaluated(
            newState = evaluated,
            forceReload = evaluated.hasAnyAccess,
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        AnimatedContent(
            targetState = uiState.permissionState,
            transitionSpec = {
                fadeIn(ReelboxMotion.standardFade()) togetherWith fadeOut(ReelboxMotion.quickFade())
            },
            label = "root_permission_transition",
        ) { permissionState ->
            when (permissionState) {
                MediaPermissionState.CHECKING -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                    )
                }

                MediaPermissionState.DENIED,
                MediaPermissionState.PERMANENTLY_DENIED,
                -> {
                    isFullScreenLandscape = false
                    PermissionScreen(
                        isPermanentlyDenied = permissionState == MediaPermissionState.PERMANENTLY_DENIED,
                        onAllowAccess = requestMediaAccess,
                        onOpenSettings = { MediaPermissionHelper.openAppSettings(context) },
                    )
                }

                MediaPermissionState.PARTIAL_ACCESS,
                MediaPermissionState.FULL_ACCESS,
                -> {
                    ReelboxMainShell(
                        uiState = uiState,
                        isFullScreenLandscape = isFullScreenLandscape,
                        onSetFullScreenLandscape = { isFullScreenLandscape = it },
                        onChooseMoreMedia = requestMediaAccess,
                        onToggleFavourite = viewModel::toggleFavourite,
                        onSetFavourite = viewModel::setFavourite,
                        onToggleMute = viewModel::toggleMute,
                        onMediaDeleted = viewModel::onMediaDeleted,
                        onSelectPhotoFolder = viewModel::selectPhotoFolder,
                        onSelectVideoFolder = viewModel::selectVideoFolder,
                    )
                }
            }
        }

        // Calm pure-black launch overlay: centred accent play mark fades in gently, then moves into the app.
        if (!launchSplashFinished) {
            ReelboxLaunchOverlay(
                isAppReady = uiState.permissionState != MediaPermissionState.CHECKING,
                onFinished = { launchSplashFinished = true },
            )
        }
    }
}

@Composable
private fun ReelboxLaunchOverlay(
    isAppReady: Boolean,
    onFinished: () -> Unit,
) {
    val markAlpha = remember { Animatable(0f) }
    val markScale = remember { Animatable(0.94f) }
    val backdropAlpha = remember { Animatable(1f) }
    var introCompleted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch {
            markScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 360, easing = ReelboxMotion.EaseOut),
            )
        }
        markAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 360, easing = ReelboxMotion.EaseOut),
        )
        delay(220L)
        introCompleted = true
    }

    LaunchedEffect(introCompleted, isAppReady) {
        if (introCompleted && isAppReady) {
            backdropAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 280, easing = ReelboxMotion.EaseOut),
            )
            onFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(backdropAlpha.value)
            .background(Color.Black)
            .testTag("launch_splash_overlay"),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_splash_play_mark),
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .scale(markScale.value)
                .alpha(markAlpha.value),
        )
    }
}

@Composable
private fun ReelboxMainShell(
    uiState: ReelboxUiState,
    isFullScreenLandscape: Boolean,
    onSetFullScreenLandscape: (Boolean) -> Unit,
    onChooseMoreMedia: () -> Unit,
    onToggleFavourite: (Long) -> Unit,
    onSetFavourite: (Long, Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onMediaDeleted: (Long) -> Unit,
    onSelectPhotoFolder: (String?) -> Unit,
    onSelectVideoFolder: (String?) -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_HOME
    val colors = ReelboxThemeTokens.colors

    var activeViewerSession by remember { mutableStateOf<MediaViewerSession?>(null) }

    // Guarantee all tabs other than Home (unless viewing an opened video item) remain locked to portrait.
    LaunchedEffect(currentRoute, activeViewerSession) {
        val landscapeAllowed = currentRoute == ROUTE_HOME ||
            activeViewerSession?.mediaType == MediaType.VIDEO
        if (!landscapeAllowed && isFullScreenLandscape) {
            onSetFullScreenLandscape(false)
        }
    }

    val navigateToTab: (ReelboxTab) -> Unit = { tab ->
        if (currentRoute != tab.route) {
            activeViewerSession = null
            if (tab != ReelboxTab.HOME && isFullScreenLandscape) {
                onSetFullScreenLandscape(false)
            }
            navController.navigate(tab.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            val useNavigationRail = !isFullScreenLandscape && maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = colors.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (!isFullScreenLandscape && !useNavigationRail) {
                        ReelboxBottomBar(
                            currentRoute = currentRoute,
                            onSelectTab = navigateToTab,
                        )
                    }
                },
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    if (useNavigationRail) {
                        ReelboxNavigationRail(
                            currentRoute = currentRoute,
                            onSelectTab = navigateToTab,
                        )
                    }
                    ReelboxTabNavHost(
                        navController = navController,
                        uiState = uiState,
                        isFullScreenLandscape = isFullScreenLandscape && activeViewerSession == null,
                        onSetFullScreenLandscape = onSetFullScreenLandscape,
                        onChooseMoreMedia = onChooseMoreMedia,
                        onToggleFavourite = onToggleFavourite,
                        onSetFavourite = onSetFavourite,
                        onToggleMute = onToggleMute,
                        onMediaDeleted = onMediaDeleted,
                        onSelectPhotoFolder = onSelectPhotoFolder,
                        onSelectVideoFolder = onSelectVideoFolder,
                        onOpenViewer = { session -> activeViewerSession = session },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }

        // Full-screen viewer rendered above the tab bar while preserving the grid's scroll state underneath.
        AnimatedVisibility(
            visible = activeViewerSession != null,
            enter = fadeIn(ReelboxMotion.quickFade()),
            exit = fadeOut(ReelboxMotion.quickFade()),
            modifier = Modifier.fillMaxSize(),
        ) {
            val session = activeViewerSession
            if (session != null) {
                MediaViewerOverlay(
                    session = session,
                    uiState = uiState,
                    isFullScreenLandscape = isFullScreenLandscape,
                    onSetFullScreenLandscape = onSetFullScreenLandscape,
                    onClose = {
                        onSetFullScreenLandscape(false)
                        activeViewerSession = null
                    },
                    onToggleFavourite = onToggleFavourite,
                    onSetFavourite = onSetFavourite,
                    onToggleMute = onToggleMute,
                    onMediaDeleted = onMediaDeleted,
                )
            }
        }
    }
}

@Composable
private fun ReelboxTabNavHost(
    navController: androidx.navigation.NavHostController,
    uiState: ReelboxUiState,
    isFullScreenLandscape: Boolean,
    onSetFullScreenLandscape: (Boolean) -> Unit,
    onChooseMoreMedia: () -> Unit,
    onToggleFavourite: (Long) -> Unit,
    onSetFavourite: (Long, Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onMediaDeleted: (Long) -> Unit,
    onSelectPhotoFolder: (String?) -> Unit,
    onSelectVideoFolder: (String?) -> Unit,
    onOpenViewer: (MediaViewerSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = ROUTE_HOME,
        modifier = modifier,
        enterTransition = { fadeIn(ReelboxMotion.standardFade()) },
        exitTransition = { fadeOut(ReelboxMotion.quickFade()) },
        popEnterTransition = { fadeIn(ReelboxMotion.standardFade()) },
        popExitTransition = { fadeOut(ReelboxMotion.quickFade()) },
    ) {
        composable(ROUTE_HOME) {
            HomeFeedScreen(
                uiState = uiState,
                isFullScreenLandscape = isFullScreenLandscape,
                onSetFullScreenLandscape = onSetFullScreenLandscape,
                onToggleFavourite = onToggleFavourite,
                onSetFavourite = onSetFavourite,
                onToggleMute = onToggleMute,
                onMediaDeleted = onMediaDeleted,
                onChooseMoreMedia = onChooseMoreMedia,
            )
        }
        composable(ROUTE_FAVOURITES) {
            FavouritesScreen(
                uiState = uiState,
                onOpenItem = { mediaType, index, snapshotIds ->
                    onOpenViewer(
                        MediaViewerSession(
                            mediaType = mediaType,
                            bucketIdFilter = null,
                            initialIndex = index,
                            snapshotItemIds = snapshotIds,
                        ),
                    )
                },
                onChooseMoreMedia = onChooseMoreMedia,
            )
        }
        composable(ROUTE_PHOTOS) {
            MediaGridScreen(
                mediaType = MediaType.PHOTO,
                uiState = uiState,
                onSelectFolder = onSelectPhotoFolder,
                onOpenItem = { index, bucketId ->
                    onOpenViewer(
                        MediaViewerSession(
                            mediaType = MediaType.PHOTO,
                            bucketIdFilter = bucketId,
                            initialIndex = index,
                        ),
                    )
                },
                onChooseMoreMedia = onChooseMoreMedia,
            )
        }
        composable(ROUTE_VIDEOS) {
            MediaGridScreen(
                mediaType = MediaType.VIDEO,
                uiState = uiState,
                onSelectFolder = onSelectVideoFolder,
                onOpenItem = { index, bucketId ->
                    onOpenViewer(
                        MediaViewerSession(
                            mediaType = MediaType.VIDEO,
                            bucketIdFilter = bucketId,
                            initialIndex = index,
                        ),
                    )
                },
                onChooseMoreMedia = onChooseMoreMedia,
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
