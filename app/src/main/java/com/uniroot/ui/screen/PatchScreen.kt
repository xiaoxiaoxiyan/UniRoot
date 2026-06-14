package com.uniroot.ui.screen

import android.content.Context
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.uniroot.patch.BootPatcher
import com.uniroot.patch.PatchResult
import com.uniroot.provider.RootCategory
import com.uniroot.provider.RootProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatchScreen(
    providerId: String,
    onBack: () -> Unit,
    onSuccess: () -> Unit
) {
    val provider = RootProvider.fromId(providerId) ?: RootProvider.KERNELSU
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var bootImageUri by remember { mutableStateOf<Uri?>(null) }
    var bootImageName by remember { mutableStateOf("") }
    var superKey by remember { mutableStateOf("") }
    var isPatching by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var patchResult by remember { mutableStateOf<PatchResult?>(null) }
    var kpmModules by remember { mutableStateOf(listOf<Uri>()) }
    var kpmModuleNames by remember { mutableStateOf(listOf<String>()) }
    var patchLogs by remember { mutableStateOf(listOf<String>()) }
    var keepForceEncrypt by remember { mutableStateOf(true) }
    var keepVerity by remember { mutableStateOf(true) }

    // 选择boot/init_boot镜像
    val bootImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            bootImageUri = it
            bootImageName = it.lastPathSegment?.substringAfterLast("/") ?: "boot.img"
        }
    }

    // 选择KPM模块
    val kpmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            kpmModules = kpmModules + it
            kpmModuleNames = kpmModuleNames + (it.lastPathSegment?.substringAfterLast("/") ?: "module.kpm")
        }
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // 方案信息卡片
            item {
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
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (provider.category) {
                                RootCategory.KERNELSU -> "修补方式: 内核模块注入 (init_boot/boot)"
                                RootCategory.MAGISK -> "修补方式: Ramdisk修补 (init_boot/boot)"
                                RootCategory.APATCH -> "修补方式: 内核二进制修补 (boot)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 选择Boot镜像
            item {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { bootImageLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Filled.Storage, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (bootImageUri == null) "选择 Boot / Init_boot 镜像"
                        else bootImageName
                    )
                }
            }

            // APatch超级密钥
            if (provider.requiresSuperKey) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = superKey,
                        onValueChange = { superKey = it },
                        label = { Text("超级密钥 (SuperKey)") },
                        placeholder = { Text("请输入强密码") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            }

            // Magisk选项
            if (provider.category == RootCategory.MAGISK) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Magisk 选项",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = keepForceEncrypt, onCheckedChange = { keepForceEncrypt = it })
                        Text("保持强制加密", modifier = Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = keepVerity, onCheckedChange = { keepVerity = it })
                        Text("保持 dm-verity", modifier = Modifier.weight(1f))
                    }
                }
            }

            // KPM模块
            if (provider.supportsKPM) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
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
                }

                items(kpmModuleNames) { name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            val idx = kpmModuleNames.indexOf(name)
                            if (idx >= 0) {
                                kpmModules = kpmModules.toMutableList().also { it.removeAt(idx) }
                                kpmModuleNames = kpmModuleNames.toMutableList().also { it.removeAt(idx) }
                            }
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "移除")
                        }
                    }
                }
            }

            // 修补进度
            if (isPatching) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "正在修补… ${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 修补日志
            if (patchLogs.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "修补日志",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1E1E1E)
                        )
                    ) {
                        Text(
                            text = patchLogs.joinToString("\n"),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }

            // 修补结果
            val currentResult = patchResult
            if (currentResult != null) {
                item {
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
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "请通过fastboot刷入修补后的镜像",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // 开始修补按钮
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val uri = bootImageUri ?: return@Button
                        scope.launch(Dispatchers.IO) {
                            isPatching = true
                            patchResult = null
                            patchLogs = emptyList()

                            // 将URI内容复制到临时文件
                            val tempBoot = File(context.cacheDir, "input_boot.img")
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                tempBoot.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            } ?: run {
                                patchResult = PatchResult(false, error = "无法读取选择的文件")
                                isPatching = false
                                return@launch
                            }

                            // 复制KPM模块到临时文件
                            val kpmPaths = mutableListOf<String>()
                            kpmModules.forEachIndexed { idx, kpmUri ->
                                val kpmFile = File(context.cacheDir, "kpm_module_$idx.kpm")
                                context.contentResolver.openInputStream(kpmUri)?.use { input ->
                                    kpmFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                kpmPaths.add(kpmFile.absolutePath)
                            }

                            // 输出路径
                            val outputFile = File(
                                context.getExternalFilesDir(null),
                                "uniroot_${provider.id}_patched_${System.currentTimeMillis()}.img"
                            )

                            val result = BootPatcher.patch(
                                bootImagePath = tempBoot.absolutePath,
                                outputPath = outputFile.absolutePath,
                                provider = provider,
                                superKey = superKey.ifBlank { null },
                                kpmModules = kpmPaths.ifEmpty { null },
                                keepForceEncrypt = keepForceEncrypt,
                                keepVerity = keepVerity,
                                onProgress = { progress = it },
                                onLog = { patchLogs = patchLogs + it }
                            )
                            patchResult = result
                            isPatching = false
                            if (result.success) {
                                onSuccess()
                            }

                            // 清理临时文件
                            tempBoot.delete()
                            kpmPaths.forEach { File(it).delete() }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = MaterialTheme.shapes.large,
                    enabled = !isPatching && bootImageUri != null &&
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
}
