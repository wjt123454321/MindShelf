package com.example.mindshelf.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.mindshelf.ui.auth.AuthViewModel
import com.example.mindshelf.ui.auth.LoginScreen
import com.example.mindshelf.ui.chat.ChatListScreen
import com.example.mindshelf.ui.chat.ChatScreen
import com.example.mindshelf.ui.knowledge.KnowledgeDetailScreen
import com.example.mindshelf.ui.knowledge.KnowledgeScreen
import com.example.mindshelf.ui.notes.NoteEditScreen
import com.example.mindshelf.ui.notes.NoteVersionsScreen
import com.example.mindshelf.ui.notes.NotesScreen
import com.example.mindshelf.ui.pages.CustomPageScreen
import com.example.mindshelf.ui.pages.PagesListScreen
import com.example.mindshelf.ui.profile.ProfileScreen
import com.example.mindshelf.ui.settings.AiSettingsScreen
import com.example.mindshelf.ui.trash.TrashScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun MindShelfApp(
    authViewModel: AuthViewModel = hiltViewModel(),
    navViewModel: AppNavViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val pinnedPage by navViewModel.pinnedPage.collectAsStateWithLifecycle()

    if (authState.checkingSession) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
        }
        return
    }

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    val hasPinnedTab = pinnedPage != null
    val profileTabIndex = if (hasPinnedTab) 4 else 3
    val pinnedTabIndex = 3

    val pageId = backStack?.arguments?.getString("pageId")
    val isOnCustomPage = pageId != null

    val showBottomBar = route in setOf(
        "chat", "knowledge", "notes", "profile", "chat_list", "pages_list",
    ) || route.startsWith("kb_detail") || isOnCustomPage

    val currentTab = when {
        isOnCustomPage && hasPinnedTab && pageId == pinnedPage?.id -> pinnedTabIndex
        isOnCustomPage -> profileTabIndex
        route.contains("knowledge") || route.startsWith("kb_detail") -> 1
        route.contains("notes") || route.startsWith("note_edit") -> 2
        route.contains("profile") || route == "pages_list" || route == "trash" || route == "ai_settings" -> profileTabIndex
        else -> 0
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(
            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
        ),
        bottomBar = {
            if (showBottomBar && !route.startsWith("note_edit")) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = {
                            if (route == "chat_list") {
                                navController.popBackStack()
                            } else {
                                navController.navigate("chat") { launchSingleTop = true }
                            }
                        },
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        label = { Text("对话", style = MaterialTheme.typography.labelSmall) },
                        colors = navItemColors(),
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { navController.navigate("knowledge") { launchSingleTop = true } },
                        icon = {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        label = { Text("知识库", style = MaterialTheme.typography.labelSmall) },
                        colors = navItemColors(),
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { navController.navigate("notes") { launchSingleTop = true } },
                        icon = {
                            Icon(
                                Icons.Default.Note,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        label = { Text("笔记", style = MaterialTheme.typography.labelSmall) },
                        colors = navItemColors(),
                    )
                    if (hasPinnedTab) {
                        val page = pinnedPage!!
                        NavigationBarItem(
                            selected = currentTab == pinnedTabIndex,
                            onClick = {
                                navController.navigate("page/${page.id}") { launchSingleTop = true }
                            },
                            icon = {
                                Icon(
                                    Icons.Default.Dashboard,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                )
                            },
                            label = {
                                Text(
                                    page.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            colors = navItemColors(),
                        )
                    }
                    NavigationBarItem(
                        selected = currentTab == profileTabIndex,
                        onClick = { navController.navigate("profile") { launchSingleTop = true } },
                        icon = {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        label = { Text("我的", style = MaterialTheme.typography.labelSmall) },
                        colors = navItemColors(),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            NavHost(
                navController,
                startDestination = if (authState.loggedIn) "chat" else "login",
            ) {
                composable("login") {
                    LoginScreen(
                        onLoggedIn = {
                            navController.navigate("chat") {
                                popUpTo("login") { inclusive = true }
                            }
                        },
                        viewModel = authViewModel,
                    )
                }
                composable("chat") {
                    ChatScreen(
                        onOpenList = { navController.navigate("chat_list") },
                    )
                }
                composable("chat_list") {
                    val chatEntry = navController.getBackStackEntry("chat")
                    ChatListScreen(
                        onBack = { navController.popBackStack() },
                        viewModel = hiltViewModel(chatEntry),
                    )
                }
                composable("knowledge") {
                    KnowledgeScreen(
                        onOpenKb = { kb ->
                            val encoded = URLEncoder.encode(kb.name, StandardCharsets.UTF_8.toString())
                            navController.navigate("kb_detail/${kb.id}/$encoded")
                        },
                    )
                }
                composable(
                    "kb_detail/{kbId}/{kbName}",
                    arguments = listOf(
                        navArgument("kbId") { type = NavType.StringType },
                        navArgument("kbName") { type = NavType.StringType },
                    ),
                ) { entry ->
                    val kbId = entry.arguments?.getString("kbId")!!
                    val kbName = URLDecoder.decode(
                        entry.arguments?.getString("kbName")!!,
                        StandardCharsets.UTF_8.toString(),
                    )
                    KnowledgeDetailScreen(
                        kbId = kbId,
                        kbName = kbName,
                        onBack = { navController.popBackStack() },
                        onOpenNote = { note ->
                            navController.navigate("note_edit/${note.id}")
                        },
                    )
                }
                composable("notes") {
                    NotesScreen(onEditNote = { note ->
                        if (note == null) {
                            navController.navigate("note_edit/new")
                        } else {
                            navController.navigate("note_edit/${note.id}")
                        }
                    })
                }
                composable(
                    "note_edit/{noteId}",
                    arguments = listOf(navArgument("noteId") { type = NavType.StringType }),
                ) { entry ->
                    val noteId = entry.arguments?.getString("noteId")
                    NoteEditScreen(
                        noteId = if (noteId == "new") null else noteId,
                        onBack = { navController.popBackStack() },
                        onOpenVersions = { id ->
                            navController.navigate("note_versions/$id")
                        },
                    )
                }
                composable(
                    "note_versions/{noteId}",
                    arguments = listOf(navArgument("noteId") { type = NavType.StringType }),
                ) { entry ->
                    val id = entry.arguments?.getString("noteId")!!
                    NoteVersionsScreen(
                        noteId = id,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    "page/{pageId}",
                    arguments = listOf(navArgument("pageId") { type = NavType.StringType }),
                ) { entry ->
                    val pageId = entry.arguments?.getString("pageId")!!
                    CustomPageScreen(
                        pageId = pageId,
                        onBack = { navController.popBackStack() },
                        onOpenNote = { noteId -> navController.navigate("note_edit/$noteId") },
                    )
                }
                composable("pages_list") {
                    PagesListScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPage = { pageId ->
                            navController.navigate("page/$pageId")
                        },
                    )
                }
                composable("profile") {
                    ProfileScreen(
                        onLogout = {
                            navController.navigate("login") {
                                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                            }
                        },
                        onOpenAiSettings = { navController.navigate("ai_settings") },
                        onOpenTrash = { navController.navigate("trash") },
                        onOpenPages = { navController.navigate("pages_list") },
                    )
                }
                composable("trash") {
                    TrashScreen(onBack = { navController.popBackStack() })
                }
                composable("ai_settings") {
                    AiSettingsScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

@Composable
private fun navItemColors() = androidx.compose.material3.NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
)
