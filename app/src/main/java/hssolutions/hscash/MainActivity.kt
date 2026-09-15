package hssolutions.hscash

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.lifecycleScope
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
import hssolutions.hscash.ui.NotificationScheduler.EXTRA_DASHBOARD_FILTER
import hssolutions.hscash.ui.OnboardingScreen
import hssolutions.hscash.ui.AppSettings
import hssolutions.hscash.ui.RelatorioScreen
import hssolutions.hscash.ui.theme.HsCashTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch(Dispatchers.IO) {
            NotificationScheduler.scheduleDailySummaries(this@MainActivity)
            NotificationScheduler.showOverdueSummaryOnceIfDue(this@MainActivity)
            NotificationScheduler.showTodaySummaryOnceIfDue(this@MainActivity)
            NotificationScheduler.showTomorrowSummaryOnceIfDue(this@MainActivity)
        }
        setContent {
            HsCashTheme {
                MainScreen(
                    initialContaId = intent.getStringExtra("contaId"),
                    initialDashboardFilter = intent.getStringExtra(EXTRA_DASHBOARD_FILTER),
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
    object Contas : Screen("contas", "Contas", Icons.Default.Payments)
    object Boletos : Screen("boletos", "Boletos", Icons.Default.QrCodeScanner)
    object Historico : Screen("historico", "Historico", Icons.Default.History)
    object Mais : Screen("mais", "Mais", Icons.Default.MoreHoriz)
    object Calendario : Screen("calendario", "Calendario", Icons.Default.CalendarMonth)
    object Relatorio : Screen("relatorio", "Relatorio", Icons.Default.Description)
    object Clientes : Screen("clientes", "Clientes", Icons.Default.Groups)
    object Cobrancas : Screen("cobrancas", "Cobrancas", Icons.Default.Send)
    object Lixeira : Screen("lixeira", "Lixeira", Icons.Default.DeleteOutline)
    object Configuracoes : Screen("configuracoes", "Configuracoes", Icons.Default.Settings)
    object Onboarding : Screen("onboarding", "Ajuda", Icons.Default.HelpOutline)
    object NovaConta : Screen("novaConta/{direcao}?codigo={codigo}", "Nova conta", Icons.Default.Payments) {
        fun createRoute(direcao: String) = "novaConta/${Uri.encode(direcao)}"
        fun createRoute(direcao: String, codigo: String) = "novaConta/${Uri.encode(direcao)}?codigo=${Uri.encode(codigo)}"
    }
    object ScannerManual : Screen("scannerManual/{direcao}", "Escanear código", Icons.Default.QrCodeScanner) {
        fun createRoute(direcao: String) = "scannerManual/${Uri.encode(direcao)}"
    }
    object BoletoDetalhe : Screen("boletoDetalhe/{codigo}", "Detalhes", Icons.Default.QrCodeScanner) {
        fun createRoute(codigo: String) = "boletoDetalhe/${Uri.encode(codigo)}"
    }
    object ContaDetalhe : Screen("contaDetalhe/{id}", "Conta", Icons.Default.Payments) {
        fun createRoute(id: String) = "contaDetalhe/${Uri.encode(id)}"
    }
}

@Composable
fun MainScreen(
    initialContaId: String? = null,
    initialDashboardFilter: String? = null,
    onBiometricAuth: (() -> Unit, (String) -> Unit) -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var onboardingDone by remember { mutableStateOf(AppSettings.isOnboardingDone(context)) }
    var unlocked by remember { mutableStateOf(!AppSettings.isAppLockEnabled(context)) }
    var pendingContaId by remember { mutableStateOf(initialContaId) }
    var pendingDashboardFilter by remember { mutableStateOf(initialDashboardFilter) }
    val items = listOf(Screen.Dashboard, Screen.Contas, Screen.Boletos, Screen.Historico, Screen.Mais)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val hideBottomBar = currentDestination?.route == Screen.Boletos.route ||
        currentDestination?.route == Screen.BoletoDetalhe.route ||
        currentDestination?.route == Screen.ContaDetalhe.route ||
        currentDestination?.route == Screen.NovaConta.route ||
        currentDestination?.route == Screen.ScannerManual.route ||
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
                NavigationBar(
                    containerColor = androidx.compose.ui.graphics.Color.White,
                    contentColor = androidx.compose.ui.graphics.Color(0xFF102A43)
                ) {
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = androidx.compose.ui.graphics.Color(0xFF0B3D6E),
                                selectedTextColor = androidx.compose.ui.graphics.Color(0xFF0B3D6E),
                                indicatorColor = androidx.compose.ui.graphics.Color(0xFFB9DCFF),
                                unselectedIconColor = androidx.compose.ui.graphics.Color(0xFF334155),
                                unselectedTextColor = androidx.compose.ui.graphics.Color(0xFF334155)
                            ),
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
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 12 }
            },
            exitTransition = {
                fadeOut(tween(120)) + slideOutHorizontally(tween(180)) { -it / 16 }
            },
            popEnterTransition = {
                fadeIn(tween(180)) + slideInHorizontally(tween(220)) { -it / 12 }
            },
            popExitTransition = {
                fadeOut(tween(120)) + slideOutHorizontally(tween(180)) { it / 16 }
            }
        ) {
            composable(Screen.Dashboard.route) { DashboardScreen(initialFilter = pendingDashboardFilter) }
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
                arguments = listOf(
                    navArgument("direcao") { type = NavType.StringType },
                    navArgument("codigo") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) { backStackEntry ->
                ContaManualScreen(
                    direcao = backStackEntry.arguments?.getString("direcao") ?: ContaDirecao.PAGAR,
                    initialBarcode = backStackEntry.arguments?.getString("codigo").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onScanBarcode = {
                        navController.navigate(Screen.ScannerManual.createRoute(
                            backStackEntry.arguments?.getString("direcao") ?: ContaDirecao.PAGAR
                        ))
                    },
                    onSaved = {
                        navController.navigate(Screen.Contas.route) {
                            popUpTo(Screen.Dashboard.route)
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(
                route = Screen.ScannerManual.route,
                arguments = listOf(navArgument("direcao") { type = NavType.StringType })
            ) { backStackEntry ->
                val direcao = backStackEntry.arguments?.getString("direcao") ?: ContaDirecao.PAGAR
                BoletosScreen(
                    onBack = { navController.popBackStack() },
                    onBoletoFound = { codigo ->
                        navController.navigate(Screen.NovaConta.createRoute(direcao, codigo)) {
                            popUpTo(Screen.NovaConta.route) { inclusive = true }
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

        LaunchedEffect(pendingContaId, unlocked, onboardingDone) {
            val contaId = pendingContaId
            if (!contaId.isNullOrBlank() && unlocked && onboardingDone) {
                navController.navigate(Screen.ContaDetalhe.createRoute(contaId)) {
                    launchSingleTop = true
                }
                pendingContaId = null
            }
        }

        LaunchedEffect(pendingDashboardFilter, unlocked, onboardingDone) {
            val filter = pendingDashboardFilter
            if (!filter.isNullOrBlank() && unlocked && onboardingDone) {
                navController.navigate(Screen.Dashboard.route) {
                    launchSingleTop = true
                }
            }
        }
    }
}
