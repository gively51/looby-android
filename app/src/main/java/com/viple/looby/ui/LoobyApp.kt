package com.viple.looby.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.viple.looby.core.auth.AuthState
import com.viple.looby.ui.auth.AuthViewModel
import com.viple.looby.ui.auth.SignInScreen
import com.viple.looby.ui.auth.SignInUi
import com.viple.looby.ui.account.*
import com.viple.looby.ui.home.HomeScreen
import com.viple.looby.ui.listings.*
import com.viple.looby.ui.livria.*
import com.viple.looby.ui.messaging.ConversationScreen
import com.viple.looby.ui.messaging.MessagesScreen
import com.viple.looby.ui.navigation.Route
import com.viple.looby.ui.support.*
import kotlin.reflect.KClass

private data class TopLevelDestination(
    val route: Route,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val routeClass: KClass<out Route>
)

private val topLevel = listOf(
    TopLevelDestination(Route.Home, "Accueil", Icons.Outlined.Home, Icons.Filled.Home, Route.Home::class),
    TopLevelDestination(Route.Explore(), "Explorer", Icons.Outlined.Search, Icons.Filled.Search, Route.Explore::class),
    TopLevelDestination(Route.Sell, "Vendre", Icons.Outlined.AddCircleOutline, Icons.Filled.AddCircle, Route.Sell::class),
    TopLevelDestination(Route.Messages, "Messages", Icons.Outlined.ChatBubbleOutline, Icons.Filled.ChatBubble, Route.Messages::class),
    TopLevelDestination(Route.Livria, "Livria", Icons.Outlined.AutoStories, Icons.Filled.AutoStories, Route.Livria::class),
    TopLevelDestination(Route.Account, "Compte", Icons.Outlined.Person, Icons.Filled.Person, Route.Account::class)
)

@Composable
fun LoobyApp(initialIntent: Intent?, authViewModel: AuthViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val signInUi by authViewModel.signIn.collectAsStateWithLifecycle()
    val unread by authViewModel.unreadMessages.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        authViewModel.onAuthResult(result.data)
    }
    LaunchedEffect(signInUi) {
        when (val s = signInUi) {
            is SignInUi.LaunchBrowser -> { authLauncher.launch(s.intent); authViewModel.onBrowserLaunched() }
            is SignInUi.Error -> { snackbar.showSnackbar(s.message); authViewModel.dismissError() }
            else -> Unit
        }
    }

    // Lien profond https://looby.viplenetwork.com/annonces/{id}
    LaunchedEffect(initialIntent) {
        val uri = initialIntent?.data ?: return@LaunchedEffect
        val segments = uri.pathSegments
        val idx = segments.indexOfFirst { it.equals("annonces", true) || it.equals("listings", true) }
        if (idx >= 0 && segments.size > idx + 1) navController.navigate(Route.ListingDetail(segments[idx + 1]))
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination
    val showBottomBar = topLevel.any { d -> currentDestination?.hierarchy?.any { it.hasRoute(d.routeClass) } == true }

    val requireAuth: (() -> Unit) -> Unit = { action ->
        if (authState is AuthState.Authenticated) action() else navController.navigate(Route.SignIn)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            AnimatedVisibility(showBottomBar, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                NavigationBar(tonalElevation = 0.dp) {
                    topLevel.forEach { dest ->
                        val selected = currentDestination?.hierarchy?.any { it.hasRoute(dest.routeClass) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                if (dest.routeClass == Route.Messages::class && unread > 0) {
                                    BadgedBox(badge = { Badge { Text(if (unread > 99) "99+" else unread.toString()) } }) {
                                        Icon(if (selected) dest.selectedIcon else dest.icon, dest.label)
                                    }
                                } else Icon(if (selected) dest.selectedIcon else dest.icon, dest.label)
                            },
                            label = { Text(dest.label) },
                            alwaysShowLabel = false
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        LoobyNavHost(navController, Modifier.padding(padding), authState, authViewModel, requireAuth)
    }
}

@Composable
private fun LoobyNavHost(
    nav: NavHostController,
    modifier: Modifier,
    authState: AuthState,
    authViewModel: AuthViewModel,
    requireAuth: (() -> Unit) -> Unit
) {
    val isAuthed = authState is AuthState.Authenticated
    val signIn = { nav.navigate(Route.SignIn) }

    NavHost(
        navController = nav,
        startDestination = Route.Home,
        modifier = modifier,
        enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 6 } },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { it / 6 } }
    ) {
        composable<Route.Home> {
            HomeScreen(
                onSearch = { q -> nav.navigate(Route.Explore(query = q)) },
                onCategory = { id -> nav.navigate(Route.Explore(categoryId = id)) },
                onDonations = { nav.navigate(Route.Explore(donationsOnly = true)) },
                onListing = { nav.navigate(Route.ListingDetail(it)) },
                onMessages = { requireAuth { nav.navigate(Route.Messages) } },
                onSignIn = signIn
            )
        }
        composable<Route.Explore> { entry ->
            val args = entry.toRoute<Route.Explore>()
            ExploreScreen(initial = args, onListing = { nav.navigate(Route.ListingDetail(it)) })
        }
        composable<Route.Sell> {
            SellHubScreen(
                isAuthenticated = isAuthed,
                onSignIn = signIn,
                onNew = { nav.navigate(Route.ListingWizard()) },
                onMyListings = { nav.navigate(Route.MyListings) },
                onListing = { nav.navigate(Route.ListingDetail(it)) },
                onResume = { nav.navigate(Route.ListingWizard(it)) }
            )
        }
        composable<Route.Messages> {
            MessagesScreen(isAuthenticated = isAuthed, onSignIn = signIn, onOpen = { nav.navigate(Route.Conversation(it)) })
        }
        composable<Route.Livria> {
            LivriaHubScreen(
                isAuthenticated = isAuthed,
                onSignIn = signIn,
                onCatalog = { nav.navigate(Route.LivriaCatalog) },
                onBook = { nav.navigate(Route.BookDetail(it)) },
                onShelf = { nav.navigate(Route.LivriaShelf) },
                onExchanges = { nav.navigate(Route.LivriaExchanges) },
                onLeaderboard = { nav.navigate(Route.LivriaLeaderboard) },
                onOnboarding = { nav.navigate(Route.LivriaOnboarding) },
                onEditProfile = { nav.navigate(Route.LivriaProfileEdit) }
            )
        }
        composable<Route.Account> {
            AccountScreen(
                authState = authState,
                onSignIn = signIn,
                onSignOut = { authViewModel.signOut() },
                onNavigate = { nav.navigate(it) }
            )
        }

        composable<Route.ListingDetail> { entry ->
            val args = entry.toRoute<Route.ListingDetail>()
            ListingDetailScreen(
                listingId = args.listingId,
                onBack = { nav.popBackStack() },
                onSeller = { nav.navigate(Route.SellerProfile(it)) },
                onConversation = { nav.navigate(Route.Conversation(it)) },
                onEdit = { nav.navigate(Route.ListingWizard(it)) },
                requireAuth = requireAuth
            )
        }
        composable<Route.SellerProfile> { entry ->
            SellerProfileScreen(entry.toRoute<Route.SellerProfile>().userId, onBack = { nav.popBackStack() }, onListing = { nav.navigate(Route.ListingDetail(it)) })
        }
        composable<Route.ListingWizard> { entry ->
            ListingWizardScreen(
                listingId = entry.toRoute<Route.ListingWizard>().listingId,
                onClose = { nav.popBackStack() },
                onPublished = { id -> nav.navigate(Route.ListingDetail(id)) { popUpTo<Route.Sell>() } }
            )
        }
        composable<Route.MyListings> {
            MyListingsScreen(onBack = { nav.popBackStack() }, onListing = { nav.navigate(Route.ListingDetail(it)) }, onResume = { nav.navigate(Route.ListingWizard(it)) })
        }
        composable<Route.Conversation> { entry ->
            ConversationScreen(
                conversationId = entry.toRoute<Route.Conversation>().conversationId,
                onBack = { nav.popBackStack() },
                onListing = { nav.navigate(Route.ListingDetail(it)) }
            )
        }

        composable<Route.LivriaCatalog> { LivriaCatalogScreen(onBack = { nav.popBackStack() }, onBook = { nav.navigate(Route.BookDetail(it)) }) }
        composable<Route.BookDetail> { entry -> BookDetailScreen(entry.toRoute<Route.BookDetail>().bookId, onBack = { nav.popBackStack() }, requireAuth = requireAuth) }
        composable<Route.LivriaShelf> { LivriaShelfScreen(onBack = { nav.popBackStack() }, onBook = { nav.navigate(Route.BookDetail(it)) }) }
        composable<Route.LivriaExchanges> { LivriaExchangesScreen(onBack = { nav.popBackStack() }, onConversation = { nav.navigate(Route.Conversation(it)) }) }
        composable<Route.LivriaLeaderboard> { LivriaLeaderboardScreen(onBack = { nav.popBackStack() }) }
        composable<Route.LivriaOnboarding> { LivriaOnboardingScreen(onBack = { nav.popBackStack() }, onDone = { nav.popBackStack() }) }
        composable<Route.LivriaProfileEdit> { LivriaProfileEditScreen(onBack = { nav.popBackStack() }) }

        composable<Route.SignIn> {
            SignInScreen(
                onSignIn = { authViewModel.startSignIn() },
                onBack = { nav.popBackStack() },
                isAuthenticated = isAuthed,
                busy = authViewModel.signIn.collectAsStateWithLifecycle().value.let { it is SignInUi.Launching || it is SignInUi.Exchanging },
                onAuthenticated = { nav.popBackStack() }
            )
        }
        composable<Route.Settings> { SettingsScreen(onBack = { nav.popBackStack() }, onSignOutAll = { authViewModel.signOut(true) }) }
        composable<Route.Addresses> { AddressesScreen(onBack = { nav.popBackStack() }) }
        composable<Route.PaymentMethods> { PaymentMethodsScreen(onBack = { nav.popBackStack() }) }
        composable<Route.Orders> { OrdersScreen(onBack = { nav.popBackStack() }) }
        composable<Route.Sessions> { SessionsScreen(onBack = { nav.popBackStack() }) }
        composable<Route.Privacy> { PrivacyScreen(onBack = { nav.popBackStack() }, onDeleted = { authViewModel.signOut() }) }
        composable<Route.Support> { SupportScreen(onBack = { nav.popBackStack() }, onTicket = { nav.navigate(Route.SupportTicket(it)) }, onChangelog = { nav.navigate(Route.Changelog) }) }
        composable<Route.SupportTicket> { entry -> SupportTicketScreen(entry.toRoute<Route.SupportTicket>().ticketId, onBack = { nav.popBackStack() }) }
        composable<Route.Changelog> { ChangelogScreen(onBack = { nav.popBackStack() }) }
        composable<Route.Legal> { entry -> LegalScreen(entry.toRoute<Route.Legal>().slug, onBack = { nav.popBackStack() }) }
    }
}
