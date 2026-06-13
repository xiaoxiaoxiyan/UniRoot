package com.uniroot.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uniroot.patch.AK3Flasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashAK3Screen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var zipPath by remember { mutableStateOf("") }
    var isFlashing by remember { mutableStateOf(false) }
    var flashResult by remember { mutableStateOf<AK3Flasher.FlashResult?>(null) }

    val zipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { zipPath = it.path ?: "" }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("刷入 AnyKernel3") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 说明
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AnyKernel3 刷入",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "选择一个 AnyKernel3 格式的 ZIP 文件进行刷入。支持内核、补丁等刷入操作。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 选择ZIP
            OutlinedButton(
                onClick = { zipLauncher.launch("application/zip") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (zipPath.isEmpty()) "选择 ZIP 文件"
                    else "已选择: ${zipPath.substringAfterLast("/")}"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 刷入进度
            if (isFlashing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "正在刷入…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 刷入结果
            flashResult?.let { result ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.success) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Icon(
                            if (result.success) Icons.Filled.CheckCircle else Icons.Filled.Error,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = result.message)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 刷入按钮
            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        isFlashing = true
                        flashResult = AK3Flasher.flash(zipPath)
                        isFlashing = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                shape = MaterialTheme.shapes.large,
                enabled = !isFlashing && zipPath.isNotEmpty()
            ) {
                Icon(Icons.Filled.FlashOn, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isFlashing) "刷入中…" else "开始刷入", fontWeight = FontWeight.Medium)
            }
        }
    }
}
