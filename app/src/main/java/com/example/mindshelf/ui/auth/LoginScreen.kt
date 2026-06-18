package com.example.mindshelf.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(uiState.loggedIn) {
        if (uiState.loggedIn) onLoggedIn()
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "MindShelf",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "你的 AI 知识库",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 28.dp),
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(Modifier.padding(20.dp)) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf("密码", "验证码", "注册").forEachIndexed { index, label ->
                            SegmentedButton(
                                selected = tab == index,
                                onClick = { tab = index },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = 3,
                                ),
                                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))

                    when (tab) {
                        0 -> {
                            OutlinedTextField(
                                email,
                                { email = it },
                                label = { Text("邮箱或账号") },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = true,
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                password,
                                { password = it },
                                label = { Text("密码") },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = true,
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = { viewModel.login(email, password) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Text("登录")
                            }
                        }
                        1 -> {
                            OutlinedTextField(
                                email,
                                { email = it },
                                label = { Text("邮箱") },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = true,
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                code,
                                { code = it },
                                label = { Text("验证码") },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = true,
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { viewModel.sendCode(email, "login") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Text("发送验证码")
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.loginWithCode(email, code) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Text("登录")
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "未注册邮箱将自动创建账号",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        2 -> {
                            OutlinedTextField(
                                email,
                                { email = it },
                                label = { Text("邮箱") },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = true,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = code,
                                    onValueChange = { code = it },
                                    label = { Text("验证码") },
                                    modifier = Modifier.weight(1f),
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    singleLine = true,
                                )
                                OutlinedButton(
                                    onClick = { viewModel.sendCode(email, "register") },
                                    modifier = Modifier.align(Alignment.CenterVertically),
                                ) {
                                    Text("获取", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                username,
                                { username = it },
                                label = { Text("用户名（可选）") },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = true,
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                password,
                                { password = it },
                                label = { Text("密码（≥8 位）") },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = true,
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = { viewModel.register(email, password, code, username.ifBlank { null }) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                enabled = code.isNotBlank(),
                            ) {
                                Text("注册")
                            }
                        }
                    }

                    uiState.info?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                    uiState.error?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (uiState.loading) {
                        Spacer(Modifier.height(12.dp))
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}
