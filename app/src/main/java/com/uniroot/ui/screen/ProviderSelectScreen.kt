package com.uniroot.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uniroot.R
import com.uniroot.provider.RootCategory
import com.uniroot.provider.RootProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSelectScreen(
    category: String,
    onProviderSelect: (String) -> Unit,
    onBack: () -> Unit
) {
    val rootCategory = try {
        RootCategory.valueOf(category)
    } catch (e: Exception) {
        RootCategory.KERNELSU
    }

    val providers = RootProvider.getByCategory(rootCategory)

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Text(
                    when (rootCategory) {
                        RootCategory.KERNELSU -> "KernelSU 类"
                        RootCategory.MAGISK -> "Magisk 类"
                        RootCategory.APATCH -> "APatch 类"
                    }
                )
            },
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
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.install_select_provider),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(providers) { provider ->
                    ProviderCard(
                        provider = provider,
                        onClick = { onProviderSelect(provider.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun ProviderCard(
    provider: RootProvider,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = provider.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // 功能标签
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (provider.supportsKPM) {
                        AssistChip(
                            onClick = { },
                            label = { Text("KPM", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    if (provider.supportsSuList) {
                        AssistChip(
                            onClick = { },
                            label = { Text("SuList", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    if (provider.requiresSuperKey) {
                        AssistChip(
                            onClick = { },
                            label = { Text("SuperKey", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = provider.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 支持的功能
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FeatureTag("Boot修补", provider.supportsBootPatch)
                FeatureTag("AK3刷入", provider.supportsAK3)
                FeatureTag("KPM模块", provider.supportsKPM)
                FeatureTag("OverlayFS", provider.supportsOverlayFS)
                FeatureTag("Magic Mount", provider.supportsMagicMount)
            }
        }
    }
}

@Composable
fun FeatureTag(label: String, enabled: Boolean) {
    if (enabled) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
