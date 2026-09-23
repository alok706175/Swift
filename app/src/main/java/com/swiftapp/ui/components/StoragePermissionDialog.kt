package com.swiftapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.swiftapp.ui.viewmodel.LanguageViewModel
import com.swiftapp.utils.StoragePermissionManager
import com.swiftapp.utils.StoragePermissionState

@Composable
fun StoragePermissionDialog(
    permissionState: StoragePermissionState,
    languageViewModel: LanguageViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isGranted = permissionState == StoragePermissionState.GRANTED

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Status Icon Header
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            if (isGranted) Color(0xFF10B981).copy(alpha = 0.15f)
                            else Color(0xFFF59E0B).copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isGranted) Icons.Filled.CheckCircle else Icons.Filled.Folder,
                        contentDescription = null,
                        tint = if (isGranted) Color(0xFF10B981) else Color(0xFFF59E0B),
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Title
                Text(
                    text = languageViewModel.getString("storage_perm_title"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isGranted) Color(0xFF10B981).copy(alpha = 0.12f)
                    else Color(0xFFF59E0B).copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isGranted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            contentDescription = null,
                            tint = if (isGranted) Color(0xFF10B981) else Color(0xFFD97706),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isGranted) languageViewModel.getString("perm_status_granted")
                            else languageViewModel.getString("perm_status_action_required"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isGranted) Color(0xFF047857) else Color(0xFFB45309)
                        )
                    }
                }

                // Description
                Text(
                    text = if (isGranted) languageViewModel.getString("storage_perm_granted_desc")
                    else languageViewModel.getString("storage_perm_req_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!isGranted) {
                        Button(
                            onClick = {
                                StoragePermissionManager.openStorageSettings(context)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = languageViewModel.getString("btn_open_storage_settings"),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(languageViewModel.getString("btn_done"))
                    }
                }
            }
        }
    }
}
