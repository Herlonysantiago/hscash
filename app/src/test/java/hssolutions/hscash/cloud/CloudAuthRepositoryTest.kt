package hssolutions.hscash.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudAuthRepositoryTest {
    @Test
    fun translatesInvalidCredentials() {
        assertEquals(
            "E-mail ou senha incorretos.",
            cloudAuthErrorMessage(IllegalStateException("Invalid login credentials"))
        )
    }

    @Test
    fun identifiesUnavailableSupabaseHostWithoutBlamingInternet() {
        assertEquals(
            "O servidor do HS Cash nao foi encontrado. Verifique se o projeto Supabase esta ativo. Seus dados offline continuam seguros.",
            cloudAuthErrorMessage(IllegalStateException("Unable to resolve host"))
        )
    }

    @Test
    fun translatesGenericNetworkFailureWithoutHidingLocalData() {
        assertEquals(
            "Nao foi possivel acessar a nuvem agora. Verifique a conexao e tente novamente. Seus dados offline continuam seguros.",
            cloudAuthErrorMessage(IllegalStateException("Network timeout"))
        )
    }

    @Test
    fun distinguishesLoginRateLimitFromEmailRateLimit() {
        val failure = IllegalStateException("rate limit exceeded")

        assertEquals(
            "Muitas tentativas de acesso. Aguarde cerca de 5 minutos e tente entrar novamente.",
            cloudAuthErrorMessage(failure)
        )
        assertEquals(
            "O limite temporário de e-mails foi atingido. No ambiente de testes, o Supabase permite somente 2 envios por hora, incluindo confirmação e recuperação. Aguarde até uma hora antes de tentar novamente.",
            cloudAuthEmailErrorMessage(failure)
        )
    }
}
