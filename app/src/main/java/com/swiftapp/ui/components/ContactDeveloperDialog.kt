package com.swiftapp.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Patterns
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.swiftapp.ui.viewmodel.LanguageViewModel
import com.swiftapp.utils.HapticManager

enum class FeedbackTopic(val label: String, val icon: ImageVector, val subjectPrefix: String) {
    FEATURE("Feature Request", Icons.Outlined.Lightbulb, "Feature Request"),
    FEEDBACK("General Feedback", Icons.Outlined.ChatBubbleOutline, "Feedback"),
    QUESTION("Question / Help", Icons.AutoMirrored.Outlined.HelpOutline, "Question"),
    OTHER("Other", Icons.Outlined.MoreHoriz, "Inquiry")
}

@Composable
fun ContactDeveloperDialog(
    versionName: String,
    languageViewModel: LanguageViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val recipientEmail = "deepaksinghrajput8747@gmail.com"

    var selectedTopic by remember { mutableStateOf(FeedbackTopic.FEATURE) }
    var userName by remember { mutableStateOf("") }
    var userEmail by remember { mutableStateOf("") }
    var userMessage by remember { mutableStateOf("") }

    var isNameFocused by remember { mutableStateOf(false) }
    var isEmailFocused by remember { mutableStateOf(false) }
    var isMessageFocused by remember { mutableStateOf(false) }
    val isAnyFocused = isNameFocused || isEmailFocused || isMessageFocused

    var nameError by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var messageError by remember { mutableStateOf(false) }

    fun hideKeyboardAndClearFocus() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        } catch (_: Exception) {}
    }

    // Ensure keyboard is closed when dialog is dismissed/disposed
    DisposableEffect(Unit) {
        onDispose {
            hideKeyboardAndClearFocus()
        }
    }

    fun handleDismiss() {
        hideKeyboardAndClearFocus()
        onDismiss()
    }

    fun validate(): Boolean {
        var isValid = true

        if (userName.trim().isBlank()) {
            nameError = true
            isValid = false
        } else {
            nameError = false
        }

        val trimmedEmail = userEmail.trim()
        if (trimmedEmail.isBlank()) {
            emailError = "This is compulsory"
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            emailError = "Please enter a valid email address"
            isValid = false
        } else {
            emailError = null
        }

        if (userMessage.trim().isBlank()) {
            messageError = true
            isValid = false
        } else {
            messageError = false
        }

        return isValid
    }

    Dialog(
        onDismissRequest = { handleDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        // Intercept Back button: if keyboard/field is active, hide keyboard first; otherwise dismiss dialog
        BackHandler {
            if (isAnyFocused) {
                hideKeyboardAndClearFocus()
            } else {
                handleDismiss()
            }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
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
                        onClick = { handleDismiss() },
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
                                        .clickable {
                                            HapticManager.light()
                                            selectedTopic = topic
                                        }
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
                                        .clickable {
                                            HapticManager.light()
                                            selectedTopic = topic
                                        }
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

                    // 2. Your Name Field (Compulsory without *)
                    OutlinedTextField(
                        value = userName,
                        onValueChange = {
                            userName = it
                            if (nameError && it.trim().isNotBlank()) {
                                nameError = false
                            }
                        },
                        label = { Text("Your Name") },
                        placeholder = { Text("Enter your full name") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                tint = if (nameError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        isError = nameError,
                        supportingText = if (nameError) {
                            { Text("This is compulsory", color = MaterialTheme.colorScheme.error) }
                        } else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isNameFocused = it.isFocused }
                    )

                    // 3. Your Email ID Field (Compulsory without *)
                    OutlinedTextField(
                        value = userEmail,
                        onValueChange = {
                            userEmail = it
                            if (emailError != null && it.trim().isNotBlank()) {
                                emailError = null
                            }
                        },
                        label = { Text("Your Email ID") },
                        placeholder = { Text("example@gmail.com") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Email,
                                contentDescription = null,
                                tint = if (emailError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        isError = emailError != null,
                        supportingText = if (emailError != null) {
                            { Text(emailError!!, color = MaterialTheme.colorScheme.error) }
                        } else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isEmailFocused = it.isFocused }
                    )

                    // 4. Message Body (Compulsory without *)
                    OutlinedTextField(
                        value = userMessage,
                        onValueChange = {
                            userMessage = it
                            if (messageError && it.trim().isNotBlank()) {
                                messageError = false
                            }
                        },
                        label = { Text("Message") },
                        placeholder = { Text("Write your suggestions, feature request, or feedback here...") },
                        isError = messageError,
                        supportingText = if (messageError) {
                            { Text("This is compulsory", color = MaterialTheme.colorScheme.error) }
                        } else null,
                        minLines = 4,
                        maxLines = 6,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isMessageFocused = it.isFocused }
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { handleDismiss() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(languageViewModel.getString("btn_cancel"))
                    }

                    Button(
                        onClick = {
                            if (!validate()) {
                                HapticManager.error()
                                if (nameError) {
                                    Toast.makeText(context, "Name: This is compulsory", Toast.LENGTH_SHORT).show()
                                } else if (emailError != null) {
                                    Toast.makeText(context, if (emailError == "This is compulsory") "Email ID: This is compulsory" else emailError!!, Toast.LENGTH_SHORT).show()
                                } else if (messageError) {
                                    Toast.makeText(context, "Message: This is compulsory", Toast.LENGTH_SHORT).show()
                                }
                                return@Button
                            }

                            HapticManager.performHaptic()
                            val subject = "[Swift] ${selectedTopic.subjectPrefix}: ${selectedTopic.label} (v$versionName)"
                            val emailBody = buildString {
                                append("Name: ${userName.trim()}\n")
                                append("Email: ${userEmail.trim()}\n")
                                append("Topic: ${selectedTopic.label}\n\n")
                                append("Message:\n")
                                append(userMessage.trim())
                                append("\n\n------------------------------\n")
                                append("App Version: Swift v$versionName\n")
                                append("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
                                append("OS: Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n")
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
                                handleDismiss()
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
