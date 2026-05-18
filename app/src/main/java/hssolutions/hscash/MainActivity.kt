package hssolutions.hscash

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import hssolutions.hscash.ui.AppLockScreen
import hssolutions.hscash.ui.BoletoDetalheScreen
import hssolutions.hscash.ui.BoletosScreen
import hssolutions.hscash.ui.CalendarioScreen
import hssolutions.hscash.ui.CobrancasScreen
import hssolutions.hscash.ui.ContaDetalheScreen
import hssolutions.hscash.ui.ContaDirecao
import hssolutions.hscash.ui.ContaManualScreen
import hssolutions.hscash.ui.DashboardScreen
import hssolutions.hscash.ui.ContasScreen
import hssolutions.hscash.ui.ClientesScreen
import hssolutions.hscash.ui.ConfiguracoesScreen
import hssolutions.hscash.ui.HistoricoScreen
import hssolutions.hscash.ui.LixeiraScreen
import hssolutions.hscash.ui.MaisScreen
import hssolutions.hscash.ui.NotificationScheduler
import hssolutions.hscash.ui.OnboardingScreen
import hssolutions.hscash.ui.AppSettings
import hssolutions.hscash.ui.RelatorioScreen
import hssolutions.hscash.ui.theme.HsCashTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationScheduler.scheduleDailySummaries(this)
        NotificationScheduler.showTodaySummaryOnceIfDue(this)
        NotificationScheduler.showTomorrowSummaryOnceIfDue(this)
        setContent {
            HsCashTheme {
                MainScreen(
                    onBiometricAuth = { onSuccess, onError ->
                        showBiometricPrompt(onSuccess, onError)
                    }
                )
            }
        }
    }

    private fun showBiometricPrompt(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Biometria nao reconhecida.")
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Desbloquear HS Cash")
            .setSubtitle("Confirme sua identidade para acessar o app.")
            .setNegativeButtonText("Usar senha")
            .build()
        prompt.authenticate(promptInfo)
    }
}

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Inicio", Icons.Default.Home)
    object Contas : Screen("contas", "Contas", Icons.Default.Inventory)
    object Boletos : Screen("boletos", "Boletos", Icons.Default.QrCodeScanner)
    object Historico : Screen("historico", "Historico", Icons.Default.History)
    object Mais : Screen("mais", "Mais", Icons.Default.MoreHoriz)
    object Calendario : Screen("calendario", "Calendario", Icons.Default.Inventory)
    object Relatorio : Screen("relatorio", "Relatorio", Icons.Default.Inventory)
    object Clientes : Screen("clientes", "Clientes", Icons.Default.Inventory)
    object Cobrancas : Screen("cobrancas", "Cobrancas", Icons.Default.Inventory)
    object Lixeira : Screen("lixeira", "Lixeira", Icons.Default.Inventory)
    object Configuracoes : Screen("configuracoes", "Configuracoes", Icons.Default.Inventory)
    object Onboarding : Screen("onboarding", "Ajuda", Icons.Default.Inventory)
    object NovaConta : Screen("novaConta/{direcao}", "Nova conta", Icons.Default.Inventory) {
        fun createRoute(direcao: String) = "novaConta/${Uri.encode(direcao)}"
    }
    object BoletoDetalhe : Screen("boletoDetalhe/{codigo}", "Detalhes", Icons.Default.QrCodeScanner) {
        fun createRoute(codigo: String) = "boletoDetalhe/${Uri.encode(codigo)}"
    }
    object ContaDetalhe : Screen("contaDetalhe/{id}", "Conta", Icons.Default.Inventory) {
        fun createRoute(id: String) = "contaDetalhe/${Uri.encode(id)}"
    }
}

@Composable
fun MainScreen(
    onBiometricAuth: (() -> Unit, (String) -> Unit) -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var onboardingDone by remember { mutableStateOf(AppSettings.isOnboardingDone(context)) }
    var unlocked by remember { mutableStateOf(!AppSettings.isAppLockEnabled(context)) }
    val items = listOf(Screen.Dashboard, Screen.Contas, Screen.Boletos, Screen.Historico, Screen.Mais)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val hideBottomBar = currentDestination?.route == Screen.Boletos.route ||
        currentDestination?.route == Screen.BoletoDetalhe.route ||
        currentDestination?.route == Screen.ContaDetalhe.route ||
        currentDestination?.route == Screen.NovaConta.route ||
        currentDestination?.route == Screen.Clientes.route ||
        currentDestination?.route == Screen.Cobrancas.route ||
        currentDestination?.route == Screen.Lixeira.route ||
        currentDestination?.route == Screen.Configuracoes.route ||
        currentDestination?.route == Screen.Onboarding.route

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && AppSettings.isAppLockEnabled(context)) {
                unlocked = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (AppSettings.isAppLockEnabled(context) && !unlocked) {
        AppLockScreen(
            onUnlocked = { unlocked = true },
            onBiometricAuth = onBiometricAuth
        )
        return
    }

    if (!onboardingDone) {
        OnboardingScreen(
            onFinish = {
                AppSettings.setOnboardingDone(context, true)
                onboardingDone = true
            }
        )
        return
    }

    Scaffold(
        bottomBar = {
            if (!hideBottomBar) {
                NavigationBar {
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) { DashboardScreen() }
            composable(Screen.Contas.route) {
                ContasScreen(
                    onContaClick = { id ->
                        navController.navigate(Screen.ContaDetalhe.createRoute(id))
                    },
                    onAddConta = { direcao ->
                        navController.navigate(Screen.NovaConta.createRoute(direcao))
                    }
                )
            }
            composable(Screen.Boletos.route) {
                BoletosScreen(
                    onBack = { navController.popBackStack() },
                    onBoletoFound = { codigo ->
                        navController.navigate(Screen.BoletoDetalhe.createRoute(codigo))
                    }
                )
            }
            composable(Screen.Historico.route) {
                HistoricoScreen(
                    onContaClick = { id ->
                        navController.navigate(Screen.ContaDetalhe.createRoute(id))
                    }
                )
            }
            composable(Screen.Mais.route) {
                MaisScreen(
                    onOpenCalendar = { navController.navigate(Screen.Calendario.route) },
                    onOpenReport = { navController.navigate(Screen.Relatorio.route) },
                    onOpenOnboarding = { navController.navigate(Screen.Onboarding.route) },
                    onOpenClients = { navController.navigate(Screen.Clientes.route) },
                    onOpenCharges = { navController.navigate(Screen.Cobrancas.route) },
                    onOpenTrash = { navController.navigate(Screen.Lixeira.route) },
                    onOpenSettings = { navController.navigate(Screen.Configuracoes.route) }
                )
            }
            composable(Screen.Calendario.route) { CalendarioScreen(onBack = { navController.popBackStack() }) }
            composable(Screen.Relatorio.route) { RelatorioScreen(onBack = { navController.popBackStack() }) }
            composable(Screen.Clientes.route) { ClientesScreen(onBack = { navController.popBackStack() }) }
            composable(Screen.Cobrancas.route) { CobrancasScreen(onBack = { navController.popBackStack() }) }
            composable(Screen.Lixeira.route) { LixeiraScreen(onBack = { navController.popBackStack() }) }
            composable(Screen.Configuracoes.route) { ConfiguracoesScreen(onBack = { navController.popBackStack() }) }
            composable(Screen.Onboarding.route) {
                OnboardingScreen(onFinish = { navController.popBackStack() })
            }
            composable(
                route = Screen.NovaConta.route,
                arguments = listOf(navArgument("direcao") { type = NavType.StringType })
            ) { backStackEntry ->
                ContaManualScreen(
                    direcao = backStackEntry.arguments?.getString("direcao") ?: ContaDirecao.PAGAR,
                    onBack = { navController.popBackStack() },
                    onSaved = {
                        navController.navigate(Screen.Contas.route) {
                            popUpTo(Screen.Dashboard.route)
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(
                route = Screen.BoletoDetalhe.route,
                arguments = listOf(navArgument("codigo") { type = NavType.StringType })
            ) { backStackEntry ->
                BoletoDetalheScreen(
                    codigo = backStackEntry.arguments?.getString("codigo").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onSaved = {
                        navController.navigate(Screen.Contas.route) {
                            popUpTo(Screen.Dashboard.route)
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(
                route = Screen.ContaDetalhe.route,
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { backStackEntry ->
                ContaDetalheScreen(
                    contaId = backStackEntry.arguments?.getString("id").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onDeleted = {
                        navController.navigate(Screen.Contas.route) {
                            popUpTo(Screen.Dashboard.route)
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    }
}
