package com.uniroot.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class ModuleInfo(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val enabled: Boolean,
    val hasWebUI: Boolean = false,
    val hasAction: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleScreen(
    onFlashAK3: () -> Unit
) {
    var modules by remember {
        mutableStateOf(
            listOf(
                ModuleInfo("zygisk_next", "Zygisk Next", "v4.2.0", "Dr-TSNG", "Zygisk API实现", true),
                ModuleInfo("shamiko", "Shamiko", "v1.1.1", "LSPosed", "隐藏Root", true, hasWebUI = true),
            )
        )
    }

    val moduleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { _: Uri? ->
        // 安装模块
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("模块") },
            actions = {
                IconButton(onClick = { moduleLauncher.launch("application/zip") }) {
                    Icon(Icons.Filled.Add, contentDescription = "安装模块")
                }
            }
        )

        if (modules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Extension,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "没有已安装的模块",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(modules) { module ->
                    ModuleItem(
                        module = module,
                        onToggle = { enabled ->
                            modules = modules.map {
                                if (it.id == module.id) it.copy(enabled = enabled) else it
                            }
                        },
                        onUninstall = {
                            modules = modules.filterNot { it.id == module.id }
                        }
                    )
                }

                // AK3刷入入口
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onFlashAK3,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(Icons.Filled.FlashOn, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("刷入 AnyKernel3 ZIP", fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun ModuleItem(
    module: ModuleInfo,
    onToggle: (Boolean) -> Unit,
    onUninstall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = module.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${module.version} by ${module.author}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = module.enabled,
                    onCheckedChange = onToggle
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = module.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (module.hasWebUI || module.hasAction) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (module.hasWebUI) {
                        AssistChip(
                            onClick = { },
                            label = { Text("WebUI") },
                            leadingIcon = { Icon(Icons.Filled.Language, null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                    if (module.hasAction) {
                        AssistChip(
                            onClick = { },
                            label = { Text("操作") },
                            leadingIcon = { Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onUninstall) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("卸载")
                }
            }
        }
    }
}
