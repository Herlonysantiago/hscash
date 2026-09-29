package hssolutions.hscash.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import hssolutions.hscash.cloud.CloudAuthRepository
import hssolutions.hscash.cloud.CloudSyncRepository
import hssolutions.hscash.cloud.CloudSyncSummary
import hssolutions.hscash.cloud.CloudUser
import hssolutions.hscash.cloud.CloudProfile
import hssolutions.hscash.cloud.SignUpResult
import hssolutions.hscash.cloud.SupabaseProvider
import hssolutions.hscash.cloud.cloudAuthErrorMessage
import hssolutions.hscash.cloud.cloudAuthEmailErrorMessage
import hssolutions.hscash.cloud.cloudSyncErrorMessage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import kotlin.math.ceil

private enum class AuthMode { SIGN_IN, SIGN_UP }
private const val PASSWORD_RESET_COOLDOWN_MS = 60_000L

@Composable
fun CloudAccountScreen(
    onBack: () -> Unit,
    onSessionChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf(CloudAuthRepository.currentUser()) }
    var mode by remember { mutableStateOf(AuthMode.SIGN_IN) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(user?.email.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var syncSummary by remember { mutableStateOf<CloudSyncSummary?>(null) }
    var authChecking by remember {
        mutableStateOf(SupabaseProvider.client?.auth?.sessionStatus?.value is SessionStatus.Initializing)
    }
    var profile by remember { mutableStateOf<CloudProfile?>(null) }
    var lastSyncAt by remember { mutableStateOf(AppSettings.lastCloudSyncAt(context)) }
    var automaticSyncState by remember { mutableStateOf(AppSettings.cloudSyncState(context)) }
    var automaticSyncError by remember { mutableStateOf(AppSettings.cloudSyncError(context)) }
    var passwordResetAvailableAt by remember {
        mutableStateOf(AppSettings.passwordResetRequestedAt(context) + PASSWORD_RESET_COOLDOWN_MS)
    }
    var passwordResetSeconds by remember { mutableStateOf(0) }

    LaunchedEffect(passwordResetAvailableAt) {
        while (true) {
            val remainingMs = passwordResetAvailableAt - System.currentTimeMillis()
            passwordResetSeconds = ceil(remainingMs.coerceAtLeast(0L) / 1000.0).toInt()
            if (remainingMs <= 0L) break
            delay(1_000L)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            automaticSyncState = AppSettings.cloudSyncState(context)
            automaticSyncError = AppSettings.cloudSyncError(context)
            lastSyncAt = AppSettings.lastCloudSyncAt(context)
            delay(3_000L)
        }
    }

    LaunchedEffect(Unit) {
        SupabaseProvider.client?.auth?.sessionStatus?.collect { status ->
            authChecking = status is SessionStatus.Initializing
            user = when (status) {
                is SessionStatus.Authenticated -> status.session.user?.let { sessionUser ->
                    CloudUser(
                        id = sessionUser.id,
                        email = sessionUser.email.orEmpty()
                    )
                }
                is SessionStatus.NotAuthenticated,
                is SessionStatus.RefreshFailure -> null
                SessionStatus.Initializing -> user
            }
        }
    }


    LaunchedEffect(user?.id) {
        if (user == null) {
            profile = null
        } else {
            runCatching { CloudAuthRepository.loadProfile() }
                .onSuccess { profile = it }
        }
    }

    fun validate(): String? = when {
        mode == AuthMode.SIGN_UP && name.isBlank() -> "Informe seu nome."
        !email.contains("@") || !email.contains(".") -> "Informe um e-mail válido."
        password.length < 6 -> "A senha precisa ter pelo menos 6 caracteres."
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Voltar") }
            Column(Modifier.weight(1f)) {
                Text("Conta online", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Sincronização segura com o Supabase", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (!SupabaseProvider.isConfigured) {
            StatusCard(
                icon = Icons.Default.CloudOff,
                title = "Nuvem não configurada",
                body = "O HS Cash continua funcionando normalmente no modo offline."
            )
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Continuar offline") }
            return@Column
        }

        if (authChecking) {
            StatusCard(
                icon = Icons.Default.CloudDone,
                title = "Verificando sua sessão",
                body = "Aguarde enquanto o HS Cash confirma seu acesso."
            )
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            return@Column
        }

        if (user != null) {
            SignedInContent(
                user = user!!,
                profileName = profile?.nome.orEmpty(),
                lastSyncAt = lastSyncAt,
                automaticSyncState = automaticSyncState,
                automaticSyncError = automaticSyncError,
                loading = loading,
                message = message,
                syncSummary = syncSummary,
                onSync = {
                    loading = true
                    error = null
                    message = null
                    scope.launch {
                        AppSettings.setCloudSyncState(context, CloudSyncState.SYNCING)
                        automaticSyncState = CloudSyncState.SYNCING
                        runCatching { CloudSyncRepository.sync(context.applicationContext) }
                            .onSuccess {
                                syncSummary = it
                                lastSyncAt = System.currentTimeMillis()
                                AppSettings.setLastCloudSyncAt(context, lastSyncAt)
                                AppSettings.setCloudSyncState(context, CloudSyncState.SYNCED)
                                automaticSyncState = CloudSyncState.SYNCED
                                automaticSyncError = ""
                                message = "Sincronização concluída com segurança."
                                onSessionChanged()
                            }
                            .onFailure {
                                val friendlyError = cloudSyncErrorMessage(it)
                                AppSettings.setCloudSyncState(context, CloudSyncState.ERROR, friendlyError)
                                automaticSyncState = CloudSyncState.ERROR
                                automaticSyncError = friendlyError
                                error = friendlyError
                            }
                        loading = false
                    }
                },
                onUpdateProfile = { updatedName, updatedEmail, newPassword ->
                    loading = true
                    error = null
                    message = null
                    scope.launch {
                        runCatching {
                            CloudAuthRepository.updateProfile(updatedName, updatedEmail, newPassword)
                        }.onSuccess { result ->
                            profile = runCatching { CloudAuthRepository.loadProfile() }.getOrNull()
                                ?: profile?.copy(nome = updatedName.trim())
                            message = if (result.emailConfirmationRequired) {
                                "Perfil salvo. Confirme o novo e-mail para concluir a alteração."
                            } else {
                                "Perfil online atualizado."
                            }
                            onSessionChanged()
                        }.onFailure { error = cloudAuthErrorMessage(it) }
                        loading = false
                    }
                },
                onSignOut = {
                    loading = true
                    error = null
                    scope.launch {
                        runCatching { CloudAuthRepository.signOut() }
                            .onSuccess {
                                user = null
                                password = ""
                                message = "Sessão encerrada. Os dados locais continuam disponíveis."
                                onSessionChanged()
                            }
                            .onFailure { error = cloudAuthErrorMessage(it) }
                        loading = false
                    }
                }
            )
            error?.let { ErrorCard(it) }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar ao aplicativo") }
            return@Column
        }

        StatusCard(
            icon = Icons.Default.CloudDone,
            title = "Seus dados locais estão protegidos",
            body = "Entrar não apaga nem envia nada automaticamente. A sincronização será confirmada antes do primeiro envio."
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AuthModeButton("Entrar", mode == AuthMode.SIGN_IN, Modifier.weight(1f)) {
                mode = AuthMode.SIGN_IN; error = null; message = null
            }
            AuthModeButton("Criar conta", mode == AuthMode.SIGN_UP, Modifier.weight(1f)) {
                mode = AuthMode.SIGN_UP; error = null; message = null
            }
        }

        if (mode == AuthMode.SIGN_UP) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nome") },
                singleLine = true,
                enabled = !loading,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = hsCashOutlinedTextFieldColors()
            )
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("E-mail") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            singleLine = true,
            enabled = !loading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            colors = hsCashOutlinedTextFieldColors()
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Senha") },
            singleLine = true,
            enabled = !loading,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            supportingText = { Text("Mínimo de 6 caracteres") },
            colors = hsCashOutlinedTextFieldColors()
        )

        error?.let { ErrorCard(it) }
        message?.let { SuccessCard(it) }

        Button(
            onClick = {
                val validationError = validate()
                if (validationError != null) {
                    error = validationError
                    return@Button
                }
                loading = true
                error = null
                message = null
                scope.launch {
                    runCatching {
                        if (mode == AuthMode.SIGN_IN) {
                            CloudAuthRepository.signIn(email, password)
                        } else {
                            when (val result = CloudAuthRepository.signUp(name, email, password)) {
                                is SignUpResult.SignedIn -> result.user
                                SignUpResult.EmailConfirmationRequired -> null
                            }
                        }
                    }.onSuccess { signedIn ->
                        if (signedIn != null) {
                            user = signedIn
                            message = "Conta conectada com sucesso."
                            onSessionChanged()
                        } else {
                            mode = AuthMode.SIGN_IN
                            password = ""
                            message = "Cadastro realizado. Abra o e-mail de confirmação; o link voltará diretamente ao HS Cash."
                        }
                    }.onFailure { error = cloudAuthErrorMessage(it) }
                    loading = false
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) CircularProgressIndicator(modifier = Modifier.padding(end = 10.dp), strokeWidth = 2.dp)
            Text(if (mode == AuthMode.SIGN_IN) "Entrar" else "Criar minha conta")
        }

        if (mode == AuthMode.SIGN_IN) {
            OutlinedButton(
                onClick = {
                    if (!email.contains("@")) {
                        error = "Informe o e-mail usado no cadastro."
                    } else {
                        loading = true
                        error = null
                        message = null
                        scope.launch {
                            runCatching { CloudAuthRepository.resendConfirmation(email) }
                                .onSuccess { message = "Novo e-mail de confirmação enviado. Verifique também a caixa de spam." }
                                .onFailure { error = cloudAuthEmailErrorMessage(it) }
                            loading = false
                        }
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Reenviar e-mail de confirmação") }
            TextButton(
                onClick = {
                    if (!email.contains("@")) {
                        error = "Informe seu e-mail para recuperar a senha."
                    } else {
                        val requestedAt = System.currentTimeMillis()
                        AppSettings.setPasswordResetRequestedAt(context, requestedAt)
                        passwordResetAvailableAt = requestedAt + PASSWORD_RESET_COOLDOWN_MS
                        loading = true
                        error = null
                        message = null
                        scope.launch {
                            runCatching { CloudAuthRepository.sendPasswordReset(email) }
                                .onSuccess {
                                    message = "Enviamos as instruções de recuperação. Verifique a caixa de entrada e o spam. Um novo envio ficará disponível em 60 segundos."
                                }
                                .onFailure { error = cloudAuthEmailErrorMessage(it) }
                            loading = false
                        }
                    }
                },
                enabled = !loading && passwordResetSeconds == 0,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    if (passwordResetSeconds > 0) {
                        "Aguarde ${passwordResetSeconds}s para tentar novamente"
                    } else {
                        "Esqueci minha senha"
                    }
                )
            }
        }

        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Continuar somente offline")
        }
    }
}

@Composable
private fun SignedInContent(
    user: CloudUser,
    profileName: String,
    lastSyncAt: Long,
    automaticSyncState: String,
    automaticSyncError: String,
    loading: Boolean,
    message: String?,
    syncSummary: CloudSyncSummary?,
    onSync: () -> Unit,
    onUpdateProfile: (String, String, String?) -> Unit,
    onSignOut: () -> Unit
) {
    var editingProfile by remember { mutableStateOf(false) }
    var editName by remember(profileName) { mutableStateOf(profileName) }
    var editEmail by remember(user.email) { mutableStateOf(user.email) }
    var newPassword by remember { mutableStateOf("") }
    var passwordConfirmation by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    StatusCard(
        icon = Icons.Default.CheckCircle,
        title = "Conta conectada",
        body = user.email.ifBlank { "Usuário conectado ao Supabase" }
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Perfil online", fontWeight = FontWeight.Bold)
                    Text(
                        profileName.ifBlank { "Nome ainda não informado" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = {
                    editingProfile = !editingProfile
                    localError = null
                    editName = profileName
                    editEmail = user.email
                    newPassword = ""
                    passwordConfirmation = ""
                }, enabled = !loading) {
                    Text(if (editingProfile) "Cancelar" else "Editar")
                }
            }
            if (editingProfile) {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it; localError = null },
                    label = { Text("Nome") },
                    singleLine = true,
                    enabled = !loading,
                    colors = hsCashOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = editEmail,
                    onValueChange = { editEmail = it; localError = null },
                    label = { Text("E-mail") },
                    singleLine = true,
                    enabled = !loading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = hsCashOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; localError = null },
                    label = { Text("Nova senha (opcional)") },
                    singleLine = true,
                    enabled = !loading,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = hsCashOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (newPassword.isNotEmpty()) {
                    OutlinedTextField(
                        value = passwordConfirmation,
                        onValueChange = { passwordConfirmation = it; localError = null },
                        label = { Text("Confirmar nova senha") },
                        singleLine = true,
                        enabled = !loading,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = hsCashOutlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        localError = when {
                            editName.isBlank() -> "Informe seu nome."
                            !editEmail.contains("@") || !editEmail.contains(".") -> "Informe um e-mail válido."
                            newPassword.isNotEmpty() && newPassword.length < 6 -> "A nova senha precisa ter pelo menos 6 caracteres."
                            newPassword != passwordConfirmation -> "As senhas não conferem."
                            else -> null
                        }
                        if (localError == null) {
                            onUpdateProfile(editName, editEmail, newPassword.ifBlank { null })
                            editingProfile = false
                        }
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Salvar perfil") }
            }
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sincronização manual", fontWeight = FontWeight.Bold)
            Text(
                if (lastSyncAt > 0L) {
                    "Última sincronização: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(lastSyncAt))}"
                } else {
                    "Este aparelho ainda não foi sincronizado."
                },
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "O aparelho preserva suas alterações e sincroniza contas, comprovantes e PDFs de boletos no espaço online.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CloudSyncStateLine(automaticSyncState, automaticSyncError)
        }
    }
    Button(onClick = onSync, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
        if (loading) CircularProgressIndicator(modifier = Modifier.padding(end = 10.dp), strokeWidth = 2.dp)
        Text("Sincronizar espaços e contas")
    }
    syncSummary?.let { summary ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Último resultado", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text("Espaços criados: ${summary.createdSpaces}", color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text("Espaços vinculados: ${summary.linkedSpaces}", color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text("Contas novas ou atualizadas enviadas: ${summary.uploadedAccounts}", color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text("Contas recebidas: ${summary.downloadedAccounts}", color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text("Anexos enviados: ${summary.uploadedAttachments}", color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text("Anexos recebidos: ${summary.downloadedAttachments}", color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text("Anexos removidos da nuvem: ${summary.deletedAttachments}", color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text("Histórico enviado: ${summary.uploadedActivities}", color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text("Histórico recebido: ${summary.downloadedActivities}", color = MaterialTheme.colorScheme.onSecondaryContainer)
                if (summary.skippedInvalidAccounts > 0) {
                    Text(
                        "Não enviadas para conferência: ${summary.skippedInvalidAccounts}",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (summary.skippedAttachments > 0) {
                    Text(
                        "Anexos que precisam de nova tentativa: ${summary.skippedAttachments}",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (summary.skippedActivities > 0) {
                    Text(
                        "Históricos que precisam de nova tentativa: ${summary.skippedActivities}",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
    message?.let { SuccessCard(it) }
    OutlinedButton(onClick = onSignOut, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
        Text("Sair da conta online")
    }
}

@Composable
private fun CloudSyncStateLine(state: String, error: String) {
    val (title, body, icon) = when (state) {
        CloudSyncState.WAITING -> Triple(
            "Sincronização automática pendente",
            "Assim que houver conexão, o HS Cash tenta enviar as alterações.",
            Icons.Default.CloudOff
        )
        CloudSyncState.SYNCING -> Triple(
            "Sincronizando agora",
            "As alterações locais estão sendo comparadas com a nuvem.",
            Icons.Default.CloudDone
        )
        CloudSyncState.SYNCED -> Triple(
            "Sincronização automática ativa",
            "As últimas alterações foram processadas com sucesso.",
            Icons.Default.CheckCircle
        )
        CloudSyncState.ERROR -> Triple(
            "A sincronização precisa de atenção",
            error.ifBlank { "Toque em sincronizar para tentar novamente." },
            Icons.Default.CloudOff
        )
        else -> Triple(
            "Pronto para sincronizar",
            "Ao alterar uma conta, o app agenda uma nova tentativa automática.",
            Icons.Default.CloudDone
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (state == CloudSyncState.ERROR) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (state == CloudSyncState.ERROR) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    body,
                    color = if (state == CloudSyncState.ERROR) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun AuthModeButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick, modifier = modifier) { Text(label) }
    else OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
}

@Composable
private fun StatusCard(icon: ImageVector, title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(body, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Text(message, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun SuccessCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Text(message, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
