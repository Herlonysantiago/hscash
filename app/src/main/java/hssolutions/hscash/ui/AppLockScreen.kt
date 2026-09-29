package hssolutions.hscash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun AppLockScreen(
    onUnlocked: () -> Unit,
    onBiometricAuth: (() -> Unit, (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val canUseBiometric = AppSettings.isBiometricUnlockEnabled(context) &&
        BiometricAuthHelper.canUseBiometric(context)
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var biometricStarted by remember { mutableStateOf(false) }
    var showRecovery by remember { mutableStateOf(false) }

    fun unlockWithPassword() {
        if (AppSettings.verifyAppLockPassword(context, password)) {
            error = null
            onUnlocked()
        } else {
            error = "Senha incorreta."
        }
    }

    fun unlockWithBiometric() {
        onBiometricAuth(
            {
                error = null
                onUnlocked()
            },
            { message ->
                if (message.isNotBlank()) error = message
            }
        )
    }

    LaunchedEffect(canUseBiometric) {
        if (canUseBiometric && !biometricStarted) {
            biometricStarted = true
            unlockWithBiometric()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF1565C0))
                Text(
                    text = "HS Cash bloqueado",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Digite sua senha para acessar o app.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Senha de acesso") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { unlockWithPassword() }),
                    isError = error != null,
                    colors = hsCashOutlinedTextFieldColors()
                )
                error?.let {
                    Text(it, color = Color(0xFFD32F2F), style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { unlockWithPassword() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Desbloquear")
                }
                if (canUseBiometric) {
                    OutlinedButton(
                        onClick = { unlockWithBiometric() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Usar biometria", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (AppSettings.hasSecurityQuestion(context)) {
                    TextButton(onClick = { showRecovery = true }) {
                        Text("Esqueci minha senha")
                    }
                }
            }
        }
    }

    if (showRecovery) {
        RecoveryDialog(
            onDismiss = { showRecovery = false },
            onRecovered = {
                showRecovery = false
                onUnlocked()
            }
        )
    }
}

@Composable
private fun RecoveryDialog(onDismiss: () -> Unit, onRecovered: () -> Unit) {
    val context = LocalContext.current
    var answer by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recuperar senha", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = AppSettings.securityQuestion(context),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = answer,
                    onValueChange = {
                        answer = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Resposta de seguranca") },
                    singleLine = true,
                    colors = hsCashOutlinedTextFieldColors()
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nova senha") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = hsCashOutlinedTextFieldColors()
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = {
                        confirmation = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Confirmar nova senha") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = error != null,
                    colors = hsCashOutlinedTextFieldColors()
                )
                error?.let {
                    Text(it, color = Color(0xFFD32F2F), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        !AppSettings.verifySecurityAnswer(context, answer) -> error = "Resposta de seguranca incorreta."
                        newPassword.length < 4 -> error = "A senha precisa ter pelo menos 4 caracteres."
                        newPassword != confirmation -> error = "As senhas nao conferem."
                        else -> {
                            AppSettings.setAppLockPassword(context, newPassword)
                            onRecovered()
                        }
                    }
                }
            ) {
                Text("Redefinir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
