package ru.homelab.kidguard.feature.parent.children

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.ui.components.GlassBottomSheet

/** Модалки привязки: код для детского устройства и приглашение со-родителя по email. */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CodeSheet(code: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedMsg = stringResource(R.string.child_code_copied)

    GlassBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.child_code_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.child_code_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
            )
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = formatCode(code),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            Text(
                text = stringResource(R.string.child_code_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp, bottom = 16.dp)
            )
            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(code))
                    Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Text(stringResource(R.string.child_code_copy))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CoParentSheet(
    onDismiss: () -> Unit,
    onInvite: (email: String, onResult: (CoParentResult) -> Unit) -> Unit
) {
    var email by remember { mutableStateOf("") }
    val context = LocalContext.current
    val linkedMsg = stringResource(R.string.coparent_linked)
    val pendingMsg = stringResource(R.string.coparent_pending)
    val errorMsg = stringResource(R.string.common_error)

    GlassBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.coparent_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.coparent_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.coparent_email_label)) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    onInvite(email) { result ->
                        val msg = when (result) {
                            CoParentResult.LINKED -> linkedMsg
                            CoParentResult.PENDING -> pendingMsg
                            CoParentResult.ERROR -> errorMsg
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                    onDismiss()
                },
                enabled = isValidEmail(email),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp)
            ) {
                Text(stringResource(R.string.coparent_invite))
            }
        }
    }
}

private fun isValidEmail(email: String): Boolean =
    email.contains("@") && email.contains(".") && email.trim().length >= 5

/** «482915» -> «482 915» для читаемости. */
private fun formatCode(code: String): String =
    if (code.length == 6) "${code.substring(0, 3)} ${code.substring(3)}" else code
