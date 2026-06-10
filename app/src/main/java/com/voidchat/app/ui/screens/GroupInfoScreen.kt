package com.voidchat.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.voidchat.app.viewmodel.GroupChatViewModel

@Composable
fun GroupInfoScreen(
    groupId: String,
    viewModel: GroupChatViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    GroupChatHeaderScreen(
        groupId = groupId,
        viewModel = viewModel,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}
