package com.swiftapp.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.swiftapp.ui.viewmodel.LanguageViewModel

enum class FeedbackTopic(val label: String, val icon: ImageVector, val subjectPrefix: String) {
    FEATURE("Feature Request", Icons.Outlined.Lightbulb, "Feature Request"),
    FEEDBACK("General Feedback", Icons.Outlined.ChatBubbleOutline, "Feedback"),
    QUESTION("Question / Help", Icons.Outlined.HelpOutline, "Question"),
    OTHER("Other", Icons.Outlined.MoreHoriz, "Inquiry")
}

@Composable
fun ContactDeveloperDialog(
    versionName: String,
    languageViewModel: LanguageViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val recipientEmail = "deepaksinghrajput8747@gmail.com"

    var selectedTopic by remember { mutableStateOf(FeedbackTopic.FEATURE) }
    var userNameOrEmail by remember { mutableStateOf("") }
    var userMessage by remember { mutableStateOf("") }

    BackHandler {
        onDismiss()
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
                .wrapContentHeight()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Mail,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = languageViewModel.getString("settings_contact_dev"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "We'd love to hear your thoughts",
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
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Topic Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Select Topic",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FeedbackTopic.values().take(2).forEach { topic ->
                                val isSelected = selectedTopic == topic
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedTopic = topic }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = topic.icon,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = topic.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FeedbackTopic.values().drop(2).forEach { topic ->
                                val isSelected = selectedTopic == topic
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedTopic = topic }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = topic.icon,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = topic.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Name or Email (Optional)
                    OutlinedTextField(
                        value = userNameOrEmail,
                        onValueChange = { userNameOrEmail = it },
                        label = { Text("Your Name or Email (Optional)") },
                        placeholder = { Text("How should we reply to you?") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 3. Message Body (Required)
                    OutlinedTextField(
                        value = userMessage,
                        onValueChange = { userMessage = it },
                        label = { Text("Message") },
                        placeholder = { Text("Write your suggestions, feature request, or feedback here...") },
                        minLines = 4,
                        maxLines = 6,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

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
                            if (userMessage.isBlank()) {
                                Toast.makeText(context, "Please enter your message first", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val subject = "[Swift PDF] ${selectedTopic.subjectPrefix} (v$versionName)"
                            val emailBody = buildString {
                                if (userNameOrEmail.isNotBlank()) {
                                    append("From: ${userNameOrEmail.trim()}\n")
                                }
                                append("Topic: ${selectedTopic.label}\n\n")
                                append("Message:\n")
                                append(userMessage.trim())
                                append("\n\n------------------------------\n")
                                append("App Version: Swift PDF v$versionName\n")
                                append("------------------------------")
                            }

                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:")
                                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipientEmail))
                                putExtra(Intent.EXTRA_SUBJECT, subject)
                                putExtra(Intent.EXTRA_TEXT, emailBody)
                            }

                            try {
                                context.startActivity(Intent.createChooser(intent, "Send Feedback"))
                                onDismiss()
                            } catch (e: Exception) {
                                Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
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
                        Text(text = "Send Message", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
