package com.example.mindshelf.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mindshelf.R
import com.example.mindshelf.data.local.AiPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    sending: Boolean = false,
    placeholder: String = "发消息…",
    isListening: Boolean = false,
    onMicTap: (() -> Unit)? = null,
    onMicPress: (() -> Unit)? = null,
    onMicRelease: (() -> Unit)? = null,
    showBuiltinOptions: Boolean = false,
    webSearchEnabled: Boolean = false,
    onWebSearchToggle: () -> Unit = {},
    builtinModel: String = AiPreferences.MODEL_FLASH,
    onBuiltinModelChange: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(
            1.dp,
            if (isListening) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = if (value.contains('\n')) Alignment.Bottom else Alignment.CenterVertically,
            ) {
                if (onMicTap != null) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .pointerInput(enabled, sending, scope) {
                                if (!enabled || sending) return@pointerInput
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    var holdTriggered = false
                                    val holdJob = scope.launch {
                                        delay(350)
                                        holdTriggered = true
                                        onMicPress?.invoke()
                                    }
                                    val up = waitForUpOrCancellation()
                                    holdJob.cancel()
                                    if (up != null) {
                                        if (holdTriggered) {
                                            onMicRelease?.invoke()
                                        } else {
                                            onMicTap()
                                        }
                                    } else if (holdTriggered) {
                                        onMicRelease?.invoke()
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = if (isListening) {
                                stringResource(R.string.voice_listening)
                            } else {
                                stringResource(R.string.voice_input)
                            },
                            modifier = Modifier.size(22.dp),
                            tint = if (isListening) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 24.dp, max = 120.dp)
                        .padding(vertical = 8.dp),
                    enabled = enabled && !sending,
                    maxLines = 5,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty() && !isListening) {
                                Text(
                                    placeholder,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            } else if (value.isEmpty() && isListening) {
                                Text(
                                    stringResource(R.string.voice_listening),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (showBuiltinOptions) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BuiltinModelChip(
                            model = builtinModel,
                            enabled = enabled && !sending,
                            onModelChange = onBuiltinModelChange,
                        )
                        ChatOptionChip(
                            selected = webSearchEnabled,
                            enabled = enabled && !sending,
                            label = stringResource(R.string.chat_web_search),
                            icon = Icons.Filled.Public,
                            onClick = onWebSearchToggle,
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f))
                FilledIconButton(
                    onClick = onSend,
                    enabled = enabled && !sending && value.isNotBlank(),
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    ),
                ) {
                    if (sending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BuiltinModelChip(
    model: String,
    enabled: Boolean,
    onModelChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ChatOptionChip(
            selected = true,
            enabled = enabled,
            label = AiPreferences.builtinModelLabel(model),
            icon = Icons.Filled.Settings,
            trailingIcon = Icons.Default.ArrowDropDown,
            onClick = { if (enabled) expanded = true },
        )
        MindShelfDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            MindShelfDropdownMenuItem(
                text = AiPreferences.builtinModelLabel(AiPreferences.MODEL_FLASH),
                selected = model == AiPreferences.MODEL_FLASH,
                onClick = {
                    onModelChange(AiPreferences.MODEL_FLASH)
                    expanded = false
                },
            )
            MindShelfDropdownMenuItem(
                text = AiPreferences.builtinModelLabel(AiPreferences.MODEL_V4_PRO),
                selected = model == AiPreferences.MODEL_V4_PRO,
                onClick = {
                    onModelChange(AiPreferences.MODEL_V4_PRO)
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun ChatOptionChip(
    selected: Boolean,
    enabled: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            trailingIcon?.let {
                Icon(
                    it,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
