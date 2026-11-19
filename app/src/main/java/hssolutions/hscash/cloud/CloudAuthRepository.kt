package hssolutions.hscash.cloud

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class CloudUser(
    val id: String,
    val email: String
)

data class CloudProfileUpdateResult(
    val emailConfirmationRequired: Boolean
)

sealed interface SignUpResult {
    data class SignedIn(val user: CloudUser) : SignUpResult
    data object EmailConfirmationRequired : SignUpResult
}

object CloudAuthRepository {
    fun currentUser(): CloudUser? {
        val user = SupabaseProvider.client?.auth?.currentUserOrNull() ?: return null
        return CloudUser(id = user.id, email = user.email.orEmpty())
    }

    suspend fun signIn(email: String, password: String): CloudUser {
        val client = requireClient()
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        return currentUser() ?: error("Nao foi possivel iniciar a sessao.")
    }

    suspend fun signInWithGoogle(): CloudUser? {
        requireClient().auth.signInWith(Google, redirectUrl = "hscash://auth-callback/")
        return currentUser()
    }

    suspend fun signUp(name: String, email: String, password: String): SignUpResult {
        val client = requireClient()
        client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            data = buildJsonObject { put("name", name.trim()) }
        }
        return currentUser()?.let(SignUpResult::SignedIn)
            ?: SignUpResult.EmailConfirmationRequired
    }

    suspend fun sendPasswordReset(email: String) {
        requireClient().auth.resetPasswordForEmail(email.trim())
    }

    suspend fun resendConfirmation(email: String) {
        requireClient().auth.resendEmail(
            type = OtpType.Email.SIGNUP,
            email = email.trim()
        )
    }

    suspend fun loadProfile(): CloudProfile {
        val user = currentUser() ?: error("Entre na conta online para abrir o perfil.")
        return requireClient().from("profiles").select {
            filter { eq("id", user.id) }
        }.decodeSingle()
    }

    suspend fun updateProfile(
        name: String,
        email: String,
        newPassword: String?
    ): CloudProfileUpdateResult {
        val user = currentUser() ?: error("Entre novamente para editar o perfil.")
        val safeName = name.trim().take(80)
        val safeEmail = email.trim()
        require(safeName.isNotBlank()) { "Informe seu nome." }

        val emailChanged = !safeEmail.equals(user.email, ignoreCase = true)
        require(!emailChanged || (safeEmail.contains("@") && safeEmail.contains("."))) {
            "Informe um e-mail válido."
        }
        require(newPassword.isNullOrBlank() || newPassword.length >= 6) {
            "A nova senha precisa ter pelo menos 6 caracteres."
        }

        val client = requireClient()
        client.auth.updateUser {
            if (emailChanged) this.email = safeEmail
            if (!newPassword.isNullOrBlank()) password = newPassword
            data = buildJsonObject { put("name", safeName) }
        }
        client.from("profiles").update(CloudProfileUpdate(nome = safeName)) {
            filter { eq("id", user.id) }
        }
        return CloudProfileUpdateResult(emailConfirmationRequired = emailChanged)
    }

    suspend fun signOut() {
        requireClient().auth.signOut()
    }

    private fun requireClient() = SupabaseProvider.client
        ?: error("A nuvem ainda nao esta configurada neste aparelho.")
}

internal fun cloudAuthErrorMessage(error: Throwable): String {
    val message = error.message.orEmpty()
    return when {
        message.contains("Invalid login credentials", ignoreCase = true) ->
            "E-mail ou senha incorretos."
        message.contains("Email not confirmed", ignoreCase = true) ->
            "Confirme o e-mail antes de entrar."
        message.contains("User already registered", ignoreCase = true) ->
            "Este e-mail ja possui uma conta."
        message.contains("Password should be", ignoreCase = true) ->
            "A senha precisa ter pelo menos 6 caracteres."
        message.contains("rate limit", ignoreCase = true) ||
            message.contains("too many requests", ignoreCase = true) ->
            "Muitas tentativas de acesso. Aguarde cerca de 5 minutos e tente entrar novamente."
        message.contains("Unable to resolve host", ignoreCase = true) ||
            message.contains("unknown host", ignoreCase = true) ->
            "O servidor do HS Cash nao foi encontrado. Verifique se o projeto Supabase esta ativo. Seus dados offline continuam seguros."
        message.contains("network", ignoreCase = true) ||
            message.contains("timeout", ignoreCase = true) ->
            "Nao foi possivel acessar a nuvem agora. Verifique a conexao e tente novamente. Seus dados offline continuam seguros."
        else -> message.ifBlank { "Nao foi possivel concluir. Tente novamente." }
    }
}

internal fun cloudAuthEmailErrorMessage(error: Throwable): String {
    val message = error.message.orEmpty()
    return if (
        message.contains("rate limit", ignoreCase = true) ||
        message.contains("too many requests", ignoreCase = true)
    ) {
        "O limite temporário de e-mails foi atingido. No ambiente de testes, o Supabase permite somente 2 envios por hora, incluindo confirmação e recuperação. Aguarde até uma hora antes de tentar novamente."
    } else {
        cloudAuthErrorMessage(error)
    }
}

