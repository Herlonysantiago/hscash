package hssolutions.hscash.cloud

import hssolutions.hscash.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

/**
 * Ponto unico de acesso ao backend.
 *
 * Sem URL e chave publicavel o cliente fica desativado. Isso preserva o modo
 * offline e permite compilar e testar o aplicativo antes de criar o projeto.
 * A service_role key nunca deve ser colocada no aplicativo.
 */
object SupabaseProvider {
    val configuration: SupabaseConfiguration
        get() = SupabaseConfiguration(
            url = BuildConfig.SUPABASE_URL,
            publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        )

    val isConfigured: Boolean
        get() = configuration.isValid

    val client: SupabaseClient? by lazy {
        configuration.takeIf(SupabaseConfiguration::isValid)?.let { config ->
            createSupabaseClient(
                supabaseUrl = config.url,
                supabaseKey = config.publishableKey
            ) {
                install(Auth)
                install(Postgrest)
                install(Realtime)
                install(Storage)
            }
        }
    }
}

data class SupabaseConfiguration(
    val url: String,
    val publishableKey: String
) {
    val isValid: Boolean
        get() = url.startsWith("https://") &&
            url.endsWith(".supabase.co") &&
            publishableKey.isNotBlank() &&
            publishableKey != "YOUR_SUPABASE_PUBLISHABLE_KEY"
}
