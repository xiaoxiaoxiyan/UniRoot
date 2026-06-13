package com.uniroot.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uniroot.provider.RootProviderDetector
import com.uniroot.provider.MountMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onRebootClick: () -> Unit
) {
    val rootState = remember { RootProviderDetector.detect() }
    var mountMode by remember { mutableStateOf(rootState.mountMode) }
    var overlayfsEnabled by remember { mutableStateOf(rootState.provider?.supportsOverlayFS == true) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("设置") }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 当前Root方案
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "当前 Root 方案",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = rootState.provider?.displayName ?: "未安装",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (rootState.isInstalled) {
                        Text(
                            text = "版本 ${rootState.version}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 挂载设置
            if (rootState.isInstalled) {
                Text(
                    text = "挂载设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (rootState.provider?.supportsOverlayFS == true) {
                    SettingItem(
                        title = "使用 OverlayFS",
                        subtitle = "使用 OverlayFS 挂载模块（推荐）",
                        checked = overlayfsEnabled,
                        onCheckedChange = { overlayfsEnabled = it }
                    )
                }

                if (rootState.provider?.supportsMagicMount == true) {
                    SettingItem(
                        title = "使用 Magic Mount",
                        subtitle = "使用 Magic Mount 挂载模块",
                        checked = mountMode == MountMode.MAGIC_MOUNT,
                        onCheckedChange = {
                            if (it) mountMode = MountMode.MAGIC_MOUNT
                            else mountMode = MountMode.OVERLAYFS
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // 通用设置
            Text(
                text = "通用",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            SettingItem(
                title = "安全模式",
                subtitle = "禁用所有模块",
                checked = false,
                onCheckedChange = { }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 操作
            Text(
                text = "操作",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onRebootClick,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Filled.RestartAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("重启")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("清除日志")
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (rootState.isInstalled) {
                OutlinedButton(
                    onClick = { },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("卸载 Root")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 关于
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "UniRoot",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "综合 Root 管理器 v1.0.0",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "GPL-3.0 License",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SettingItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
