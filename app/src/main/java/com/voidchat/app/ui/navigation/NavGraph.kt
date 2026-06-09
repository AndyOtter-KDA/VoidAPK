package com.voidchat.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.voidchat.app.ui.screens.*
import com.voidchat.app.viewmodel.*

object Routes {
    const val ONBOARDING = "onboarding"
    const val RECOVERY_PHRASE = "recovery_phrase"
    const val HOME = "home"
    const val CHAT = "chat"
    const val JOIN_CHAT = "join_chat"
    const val CREATE_GROUP = "create_group"
    const val GROUP_CHAT = "group_chat1"
    const val GROUP_INFO = "group_info"
    const val JOIN_GROUP = "join_group"
    const val CREATE_NOTE = "create_note"
    const val READ_NOTE = "read_note"
    const val TRANSFER_OUT = "transfer_out"
    const val TRANSFER_IN = "transfer_in"
    const val BACKUP = "backup"
    const val DONATE = "donate"
    const val SETTINGS = "settings"
}

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Routes.ONBOARDING) {
            val vm: OnboardingViewModel = viewModel()
            OnboardingScreen(
                viewModel = vm,
                onNavigateToRecovery = { id, phrase ->
                    navController.navigate("${Routes.RECOVERY_PHRASE}/${id}/${phrase.joinToString(",")}")
                },
                onNavigateToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = "${Routes.RECOVERY_PHRASE}/{displayId}/{phraseWords}",
            arguments = listOf(
                navArgument("displayId") { type = NavType.StringType },
                navArgument("phraseWords") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val displayId = backStackEntry.arguments?.getString("displayId") ?: ""
            val phraseWordsStr = backStackEntry.arguments?.getString("phraseWords") ?: ""
            val words = phraseWordsStr.split(",")
            val vm: OnboardingViewModel = viewModel()
            
            RecoveryPhraseScreen(
                displayId = displayId,
                phrase = words,
                viewModel = vm,
                onNavigateToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            val vm: HomeViewModel = viewModel()
            HomeScreen(
                viewModel = vm,
                myDisplayId = "VOID-E2E-COMPLY-NODE-LINE",
                onNavigateToChat = { chatId ->
                    navController.navigate("${Routes.CHAT}/${chatId}")
                },
                onNavigateToJoinChat = {
                    navController.navigate(Routes.JOIN_CHAT)
                },
                onNavigateToCreateGroup = {
                    navController.navigate(Routes.CREATE_GROUP)
                },
                onNavigateToCreateNote = {
                    navController.navigate(Routes.CREATE_NOTE)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        composable(
            route = "${Routes.CHAT}/{chatId}",
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
            val vm: ChatViewModel = viewModel()
            ChatScreen(
                chatId = chatId,
                viewModel = vm,
                myDisplayId = "VOID-E2E-COMPLY-NODE-LINE",
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.JOIN_CHAT) {
            val vm: HomeViewModel = viewModel()
            JoinChatScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChat = { chatId ->
                    navController.navigate("${Routes.CHAT}/${chatId}") {
                        popUpTo(Routes.JOIN_CHAT) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.CREATE_GROUP) {
            val vm: GroupChatViewModel = viewModel()
            CreateGroupScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToGroupChat = { groupId ->
                    navController.navigate("${Routes.GROUP_CHAT}/${groupId}") {
                        popUpTo(Routes.CREATE_GROUP) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = "${Routes.GROUP_CHAT}/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            val vm: GroupChatViewModel = viewModel()
            GroupChatScreen(
                groupId = groupId,
                viewModel = vm,
                myDisplayId = "VOID-E2E-COMPLY-PEER",
                onNavigateBack = { navController.popBackStack() },
                onNavigateToGroupInfo = { gid ->
                    navController.navigate("${Routes.GROUP_INFO}/${gid}")
                }
            )
        }

        composable(
            route = "${Routes.GROUP_INFO}/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            val vm: GroupChatViewModel = viewModel()
            GroupInfoScreen(
                groupId = groupId,
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = "${Routes.JOIN_GROUP}/{inviteCode}",
            arguments = listOf(navArgument("inviteCode") { type = NavType.StringType })
        ) { backStackEntry ->
            val code = backStackEntry.arguments?.getString("inviteCode") ?: ""
            val vm: GroupChatViewModel = viewModel()
            JoinGroupScreen(
                inviteCode = code,
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToGroupChat = { groupId ->
                    navController.navigate("${Routes.GROUP_CHAT}/${groupId}")
                }
            )
        }

        composable(Routes.CREATE_NOTE) {
            val vm: NoteViewModel = viewModel()
            CreateNoteScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "${Routes.READ_NOTE}/{shareCode}",
            arguments = listOf(navArgument("shareCode") { type = NavType.StringType })
        ) { backStackEntry ->
            val code = backStackEntry.arguments?.getString("shareCode") ?: ""
            val vm: NoteViewModel = viewModel()
            ReadNoteScreen(
                shareCode = code,
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel()
            SettingsScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToBackup = { navController.navigate(Routes.BACKUP) },
                onNavigateToTransferIn = { navController.navigate(Routes.TRANSFER_IN) },
                onNavigateToTransferOut = { navController.navigate(Routes.TRANSFER_OUT) },
                onNavigateToDonate = { navController.navigate(Routes.DONATE) },
                onLogOut = {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.BACKUP) {
            BackupScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Routes.TRANSFER_IN) {
            TransferInScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.TRANSFER_OUT) {
            TransferOutScreen(
                displayId = "VOID-E2E-COMPLY-NODE-LINE",
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.DONATE) {
            DonateScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
