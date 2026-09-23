package com.swiftapp.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.swiftapp.ui.viewmodel.LanguageViewModel

data class BugAttachment(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String?
)

@Composable
fun BugReportDialog(
    versionName: String,
    versionCode: Long,
    languageViewModel: LanguageViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val recipientEmail = "deepaksinghrajput8747@gmail.com"

    val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
    val androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    var issueTitle by remember { mutableStateOf("") }
    var bugDescription by remember { mutableStateOf("") }
    val attachments = remember { mutableStateListOf<BugAttachment>() }

    BackHandler {
        onDismiss()
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            val nameAndSize = getFileNameAndSize(context, uri)
            val mime = context.contentResolver.getType(uri)
            if (attachments.none { it.uri == uri }) {
                attachments.add(
                    BugAttachment(
                        uri = uri,
                        name = nameAndSize.first,
                        sizeBytes = nameAndSize.second,
                        mimeType = mime
                    )
                )
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        BackHandler {
            onDismiss()
        }
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.BugReport,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = languageViewModel.getString("bug_report_title"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Help us fix issues and improve Swift",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Form Fields
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Issue Title
                    OutlinedTextField(
                        value = issueTitle,
                        onValueChange = { issueTitle = it },
                        label = { Text("Issue Title") },
                        placeholder = { Text("e.g., Error while compressing large PDF") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 2. Issue Description / Steps
                    OutlinedTextField(
                        value = bugDescription,
                        onValueChange = { bugDescription = it },
                        label = { Text("What happened / Steps to reproduce") },
                        placeholder = { Text("Please describe what went wrong, what buttons you clicked, or any error messages...") },
                        minLines = 4,
                        maxLines = 6,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 3. Attachments Section (Clean & Simple)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AttachFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Attachments (${attachments.size})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                FilledTonalButton(
                                    onClick = { filePickerLauncher.launch("*/*") },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "Add File/Image", style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            if (attachments.isEmpty()) {
                                Text(
                                    text = "Optional: Add screenshots or screen recordings to help us identify the issue.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    attachments.forEach { item ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = when {
                                                        item.mimeType?.startsWith("image") == true -> Icons.Outlined.Image
                                                        item.mimeType?.startsWith("video") == true -> Icons.Outlined.VideoFile
                                                        item.name.endsWith(".pdf", ignoreCase = true) -> Icons.Outlined.PictureAsPdf
                                                        else -> Icons.AutoMirrored.Filled.InsertDriveFile
                                                    },
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = item.name,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (item.sizeBytes > 0) {
                                                        Text(
                                                            text = formatFileSize(item.sizeBytes),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                IconButton(
                                                    onClick = { attachments.remove(item) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Close,
                                                        contentDescription = "Remove",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Compact Diagnostics Note
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Auto-included: $deviceModel • $androidVersion • v$versionName",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(languageViewModel.getString("btn_cancel"))
                    }

                    Button(
                        onClick = {
                            if (issueTitle.isBlank() && bugDescription.isBlank()) {
                                Toast.makeText(context, "Please enter an issue title or description", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val cleanTitle = if (issueTitle.isNotBlank()) issueTitle.trim() else "Bug Report"
                            val subject = "[Swift PDF Bug] $cleanTitle (v$versionName)"

                            val emailBody = buildString {
                                append("Issue: $cleanTitle\n\n")
                                if (bugDescription.isNotBlank()) {
                                    append("Description & Steps:\n")
                                    append(bugDescription.trim())
                                    append("\n\n")
                                }
                                append("------------------------------\n")
                                append("Diagnostic Information:\n")
                                append("• App Version: v$versionName (Build $versionCode)\n")
                                append("• Device: $deviceModel\n")
                                append("• OS: $androidVersion\n")
                                append("• Package: ${context.packageName}\n")
                                append("------------------------------")
                                if (attachments.isNotEmpty()) {
                                    append("\nAttached Files (${attachments.size}):\n")
                                    attachments.forEach { append("• ${it.name}\n") }
                                }
                            }

                            if (attachments.isEmpty()) {
                                val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:")
                                    putExtra(Intent.EXTRA_EMAIL, arrayOf(recipientEmail))
                                    putExtra(Intent.EXTRA_SUBJECT, subject)
                                    putExtra(Intent.EXTRA_TEXT, emailBody)
                                }
                                try {
                                    context.startActivity(Intent.createChooser(emailIntent, "Send Bug Report"))
                                    onDismiss()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No email client found", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                val sendMultipleIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "*/*"
                                    putExtra(Intent.EXTRA_EMAIL, arrayOf(recipientEmail))
                                    putExtra(Intent.EXTRA_SUBJECT, subject)
                                    putExtra(Intent.EXTRA_TEXT, emailBody)
                                    val uriList = ArrayList<Uri>(attachments.map { it.uri })
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    context.startActivity(Intent.createChooser(sendMultipleIntent, "Send Bug Report with Attachments"))
                                    onDismiss()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot send email with attachments", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (attachments.isEmpty()) "Submit Report" else "Submit (${attachments.size})",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

private fun getFileNameAndSize(context: Context, uri: Uri): Pair<String, Long> {
    var name = "attachment"
    var size = 0L
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) name = cursor.getString(nameIndex) ?: "attachment"
                if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
            }
        }
    } catch (e: Exception) {
        // fallback
    }
    return Pair(name, size)
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}
