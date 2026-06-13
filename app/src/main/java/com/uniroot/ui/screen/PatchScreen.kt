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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.uniroot.patch.BootPatcher
import com.uniroot.patch.PatchResult
import com.uniroot.provider.RootProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatchScreen(
    providerId: String,
    onBack: () -> Unit,
    onSuccess: () -> Unit
) {
    val provider = RootProvider.fromId(providerId) ?: RootProvider.KERNELSU
    val scope = rememberCoroutineScope()

    var bootImagePath by remember { mutableStateOf("") }
    var superKey by remember { mutableStateOf("") }
    var isPatching by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var patchResult by remember { mutableStateOf<PatchResult?>(null) }
    var kpmModules by remember { mutableStateOf(listOf<String>()) }

    val bootImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { bootImagePath = it.path ?: "" }
    }

    val kpmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { kpmModules = kpmModules + (it.path ?: "") }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("修补 - ${provider.displayName}") },
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

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = provider.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = provider.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { bootImageLauncher.launch("*/*") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Filled.Storage, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (bootImagePath.isEmpty()) "选择 Boot 镜像"
                    else "已选择: ${bootImagePath.substringAfterLast("/")}"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (provider.requiresSuperKey) {
                OutlinedTextField(
                    value = superKey,
                    onValueChange = { superKey = it },
                    label = { Text("超级密钥 (SuperKey)") },
                    placeholder = { Text("请输入强密码") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (provider.supportsKPM) {
                Text(
                    text = "KPM 模块",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { kpmLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("添加 KPM 模块")
                }

                kpmModules.forEach { kpm ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = kpm.substringAfterLast("/"),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { kpmModules = kpmModules - kpm }) {
                            Icon(Icons.Filled.Close, contentDescription = "移除")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (isPatching) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                )
                Text(
                    text = "正在修补… ${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val currentResult = patchResult
            if (currentResult != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (currentResult.success) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Icon(
                            if (currentResult.success) Icons.Filled.CheckCircle else Icons.Filled.Error,
                            contentDescription = null,
                            tint = if (currentResult.success) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (currentResult.success) "修补成功！" else "修补失败: ${currentResult.error}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (currentResult.success && currentResult.outputPath != null) {
                            Text(
                                text = "输出: ${currentResult.outputPath}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        isPatching = true
                        patchResult = null
                        val result = BootPatcher.patch(
                            bootImagePath = bootImagePath,
                            outputPath = "/data/local/tmp/uniroot_patched.img",
                            provider = provider,
                            superKey = superKey.ifBlank { null },
                            kpmModules = kpmModules.ifEmpty { null },
                            onProgress = { progress = it }
                        )
                        patchResult = result
                        isPatching = false
                        if (result.success) {
                            onSuccess()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                shape = MaterialTheme.shapes.large,
                enabled = !isPatching && bootImagePath.isNotEmpty() &&
                        (!provider.requiresSuperKey || superKey.isNotBlank())
            ) {
                Icon(Icons.Filled.Build, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isPatching) "修补中…" else "开始修补",
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
