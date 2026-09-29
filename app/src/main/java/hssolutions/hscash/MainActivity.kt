package hssolutions.hscash

import android.content.Intent
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import hssolutions.hscash.ui.AlertCenterScreen
import hssolutions.hscash.ui.BoletoDetalheScreen
import hssolutions.hscash.ui.BoletosScreen
import hssolutions.hscash.ui.CalendarioScreen
import hssolutions.hscash.ui.CobrancasScreen
import hssolutions.hscash.ui.CloudAccountScreen
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
import hssolutions.hscash.ui.ThemeMode
import hssolutions.hscash.ui.RelatorioScreen
import hssolutions.hscash.ui.ReportSection
import hssolutions.hscash.ui.RecorrentesScreen
import hssolutions.hscash.ui.SpacesScreen
import hssolutions.hscash.ui.SharedExternalImportScreen
import hssolutions.hscash.ui.SpaceSharingScreen
import hssolutions.hscash.ui.HojeScreen
import hssolutions.hscash.ui.HsCashDrawerContent
import hssolutions.hscash.ui.PaymentScanMode
import hssolutions.hscash.ui.defaultDrawerDestinations
import hssolutions.hscash.ui.theme.HsCashTheme
import hssolutions.hscash.cloud.SupabaseProvider
import hssolutions.hscash.cloud.CloudAuthRepository
import hssolutions.hscash.cloud.CloudSyncScheduler
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private var sharedUris by mutableStateOf<List<Uri>>(emptyList())
    private var authCallbackPending by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAuthDeeplink(intent)
        sharedUris = extractSharedUris(intent)
        lifecycleScope.launch(Dispatchers.IO) {
            CloudSyncScheduler.schedule(this@MainActivity)
            CloudSyncScheduler.enqueue(this@MainActivity)
            NotificationScheduler.scheduleDailySummaries(this@MainActivity)
            NotificationScheduler.showOverdueSummaryOnceIfDue(this@MainActivity)
            NotificationScheduler.showTodaySummaryOnceIfDue(this@MainActivity)
            NotificationScheduler.showTomorrowSummaryOnceIfDue(this@MainActivity)
        }
        setContent {
            var themeMode by remember { mutableStateOf(AppSettings.themeMode(this@MainActivity)) }
            HsCashTheme(themeMode = themeMode) {
                MainScreen(
                    initialContaId = intent.getStringExtra("contaId"),
                    initialDashboardFilter = intent.getStringExtra(EXTRA_DASHBOARD_FILTER),
                    sharedUris = sharedUris,
                    onSharedUrisConsumed = { sharedUris = emptyList() },
                    authCallbackPending = authCallbackPending,
                    onAuthCallbackConsumed = { authCallbackPending = false },
                    themeMode = themeMode,
                    onThemeModeChanged = { newMode ->
                        AppSettings.setThemeMode(this@MainActivity, newMode)
                        themeMode = newMode
                    },
                    onBiometricAuth = { onSuccess, onError ->
                        showBiometricPrompt(onSuccess, onError)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthDeeplink(intent)
        extractSharedUris(intent).takeIf { it.isNotEmpty() }?.let { sharedUris = it }
    }

    private fun handleAuthDeeplink(intent: Intent?) {
        if (intent?.data?.scheme != "hscash" || intent.data?.host != "auth-callback") return
        authCallbackPending = true
        SupabaseProvider.client?.handleDeeplinks(intent)
    }

    @Suppress("DEPRECATION")
    private fun extractSharedUris(intent: Intent?): List<Uri> {
        if (intent?.action != Intent.ACTION_SEND && intent?.action != Intent.ACTION_SEND_MULTIPLE) {
            return emptyList()
        }
        val extras = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
            Intent.ACTION_SEND_MULTIPLE ->
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            else -> emptyList()
        }
        val clipData = intent.clipData
        val clipped = (0 until (clipData?.itemCount ?: 0)).mapNotNull { index ->
            clipData?.getItemAt(index)?.uri
        }
        return (extras + clipped).distinct()
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
    object Hoje : Screen("hoje", "Hoje", Icons.Default.CalendarMonth)
    object Contas : Screen("contas", "Contas", Icons.Default.Payments)
    object Boletos : Screen("boletos", "Boletos", Icons.Default.QrCodeScanner)
    object Historico : Screen("historico", "Historico", Icons.Default.History)
    object Mais : Screen("mais", "Mais", Icons.Default.MoreHoriz)
    object Calendario : Screen("calendario", "Calendario", Icons.Default.CalendarMonth)
    object Relatorio : Screen("relatorio", "Relatorio", Icons.Default.Description)
    object RelatorioCategorias : Screen("relatorioCategorias", "Evolução por categoria", Icons.Default.Description)
    object Recorrentes : Screen("recorrentes", "Recorrentes", Icons.Default.History)
    object Alertas : Screen("alertas", "Alertas", Icons.Default.HelpOutline)
    object Clientes : Screen("clientes", "Clientes", Icons.Default.Groups)
    object Cobrancas : Screen("cobrancas", "Cobrancas", Icons.Default.Send)
    object Lixeira : Screen("lixeira", "Lixeira", Icons.Default.DeleteOutline)
    object Configuracoes : Screen("configuracoes", "Configuracoes", Icons.Default.Settings)
    object Espacos : Screen("espacos", "Espaços", Icons.Default.Groups)
    object ContaOnline : Screen("contaOnline", "Conta online", Icons.Default.Groups)
    object Compartilhamento : Screen("compartilhamento?spaceId={spaceId}", "Compartilhamento", Icons.Default.Groups) {
        fun createRoute(spaceId: String? = null) = "compartilhamento?spaceId=${Uri.encode(spaceId.orEmpty())}"
    }
    object Onboarding : Screen("onboarding", "Ajuda", Icons.Default.HelpOutline)
    object SharedImport : Screen("importarCompartilhado", "Conferir importacao", Icons.Default.Description)
    object NovaConta : Screen("novaConta/{direcao}?codigo={codigo}", "Nova conta", Icons.Default.Payments) {
        fun createRoute(direcao: String) = "novaConta/${Uri.encode(direcao)}"
        fun createRoute(direcao: String, codigo: String) = "novaConta/${Uri.encode(direcao)}?codigo=${Uri.encode(codigo)}"
    }
    object ScannerManual : Screen("scannerManual/{direcao}", "Escanear código", Icons.Default.QrCodeScanner) {
        fun createRoute(direcao: String) = "scannerManual/${Uri.encode(direcao)}"
    }
    object ScannerPix : Screen("scannerPix", "Escanear Pix", Icons.Default.QrCodeScanner)
    object BoletoDetalhe : Screen("boletoDetalhe/{codigo}", "Detalhes", Icons.Default.QrCodeScanner) {
        fun createRoute(codigo: String) = "boletoDetalhe/${Uri.encode(codigo)}"
    }
    object ContaDetalhe : Screen("contaDetalhe/{id}?acao={acao}", "Conta", Icons.Default.Payments) {
        fun createRoute(id: String, acao: String? = null): String {
            val base = "contaDetalhe/${Uri.encode(id)}"
            return acao?.let { "$base?acao=${Uri.encode(it)}" } ?: base
        }
    }
}

@Composable
fun MainScreen(
    initialContaId: String? = null,
    initialDashboardFilter: String? = null,
    sharedUris: List<Uri> = emptyList(),
    onSharedUrisConsumed: () -> Unit = {},
    authCallbackPending: Boolean = false,
    onAuthCallbackConsumed: () -> Unit = {},
    themeMode: String = ThemeMode.SYSTEM,
    onThemeModeChanged: (String) -> Unit = {},
    onBiometricAuth: (() -> Unit, (String) -> Unit) -> Unit
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var onboardingDone by remember { mutableStateOf(AppSettings.isOnboardingDone(context)) }
    var unlocked by remember { mutableStateOf(!AppSettings.isAppLockEnabled(context)) }
    var pendingContaId by remember { mutableStateOf(initialContaId) }
    var pendingDashboardFilter by remember { mutableStateOf(initialDashboardFilter) }
    var drawerRefreshKey by remember { mutableStateOf(0) }
    val items = listOf(Screen.Dashboard, Screen.Hoje, Screen.Contas, Screen.Boletos, Screen.Mais)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val scannerRouteActive = currentDestination?.route in setOf(
        Screen.Boletos.route,
        Screen.ScannerManual.route,
        Screen.ScannerPix.route
    )
    val hideBottomBar = currentDestination?.route == Screen.BoletoDetalhe.route ||
        currentDestination?.route == Screen.ContaDetalhe.route ||
        currentDestination?.route == Screen.NovaConta.route ||
        currentDestination?.route == Screen.ScannerManual.route ||
        currentDestination?.route == Screen.ScannerPix.route ||
        currentDestination?.route == Screen.Clientes.route ||
        currentDestination?.route == Screen.Cobrancas.route ||
        currentDestination?.route == Screen.Lixeira.route ||
        currentDestination?.route == Screen.Configuracoes.route ||
        currentDestination?.route == Screen.Espacos.route ||
        currentDestination?.route == Screen.ContaOnline.route ||
        currentDestination?.route == Screen.Compartilhamento.route ||
        currentDestination?.route == Screen.Recorrentes.route ||
        currentDestination?.route == Screen.Onboarding.route ||
        currentDestination?.route == Screen.SharedImport.route ||
        currentDestination?.route == Screen.Alertas.route

    LaunchedEffect(scannerRouteActive, hideBottomBar, drawerState.targetValue) {
        // A troca paisagem/retrato do scanner recalcula as âncoras do drawer.
        // Mantenha-o fechado também na tela de cadastro que recebe o resultado.
        if ((scannerRouteActive || hideBottomBar) && drawerState.targetValue != DrawerValue.Closed) {
            drawerState.snapTo(DrawerValue.Closed)
        }
    }

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

    fun openDrawer() {
        drawerRefreshKey++
        drawerScope.launch { drawerState.open() }
    }

    fun navigateFromDrawer(route: String) {
        drawerScope.launch {
            drawerState.close()
            navController.navigate(route) { launchSingleTop = true }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !hideBottomBar && !scannerRouteActive,
        drawerContent = {
            if (!hideBottomBar && !scannerRouteActive) {
            HsCashDrawerContent(
                refreshKey = drawerRefreshKey,
                destinations = defaultDrawerDestinations(),
                onNavigate = ::navigateFromDrawer,
                onSelectSpace = { spaceId ->
                    if (hssolutions.hscash.ui.SpaceStorage.select(context, spaceId)) {
                        drawerScope.launch { drawerState.close() }
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Dashboard.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                onManageSpaces = { navigateFromDrawer(Screen.Espacos.route) },
                onOpenAccount = { navigateFromDrawer(Screen.ContaOnline.route) },
                onSignOut = {
                    drawerScope.launch {
                        runCatching { CloudAuthRepository.signOut() }
                        drawerRefreshKey++
                    }
                }
            )
            }
        }
    ) {
    Scaffold(
        bottomBar = {
            if (!hideBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = { Text(screen.title) },
                            selected = if (screen == Screen.Mais) {
                                drawerState.isOpen
                            } else {
                                currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            onClick = {
                                if (screen == Screen.Mais) {
                                    openDrawer()
                                } else {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            // Telas abertas pelo menu lateral (como Histórico)
                                            // não devem reaparecer ao trocar de aba inferior.
                                            saveState = false
                                        }
                                        launchSingleTop = true
                                        restoreState = false
                                    }
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
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    initialFilter = pendingDashboardFilter,
                    onOpenToday = { navController.navigate(Screen.Hoje.route) },
                    onOpenSpaces = { navController.navigate(Screen.Espacos.route) },
                    onOpenAlerts = { navController.navigate(Screen.Alertas.route) },
                    onOpenDrawer = ::openDrawer
                )
            }
            composable(Screen.Hoje.route) {
                HojeScreen(
                    onBack = { navController.popBackStack() },
                    onContaClick = { id -> navController.navigate(Screen.ContaDetalhe.createRoute(id)) },
                    onPayClick = { id -> navController.navigate(Screen.ContaDetalhe.createRoute(id, "pagar")) }
                )
            }
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
                    },
                    onOpenCategoryEvolution = { navController.navigate(Screen.RelatorioCategorias.route) }
                )
            }
            composable(Screen.Mais.route) {
                MaisScreen(
                    onOpenSpaces = { navController.navigate(Screen.Espacos.route) },
                    onOpenOnlineAccount = { navController.navigate(Screen.ContaOnline.route) },
                    onOpenHistory = { navController.navigate(Screen.Historico.route) },
                    onOpenCalendar = { navController.navigate(Screen.Calendario.route) },
                    onOpenReport = { navController.navigate(Screen.Relatorio.route) },
                    onOpenOnboarding = { navController.navigate(Screen.Onboarding.route) },
                    onOpenClients = { navController.navigate(Screen.Clientes.route) },
                    onOpenCharges = { navController.navigate(Screen.Cobrancas.route) },
                    onOpenAlerts = { navController.navigate(Screen.Alertas.route) },
                    onOpenTrash = { navController.navigate(Screen.Lixeira.route) },
                    onOpenSettings = { navController.navigate(Screen.Configuracoes.route) }
                )
            }
            composable(Screen.Espacos.route) {
                SpacesScreen(
                    onBack = { navController.popBackStack() },
                    onSpaceSelected = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Dashboard.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onOpenOnlineAccount = { navController.navigate(Screen.ContaOnline.route) },
                    onOpenSharing = { spaceId -> navController.navigate(Screen.Compartilhamento.createRoute(spaceId)) }
                )
            }
            composable(Screen.ContaOnline.route) {
                CloudAccountScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Screen.Compartilhamento.route,
                arguments = listOf(navArgument("spaceId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                })
            ) { backStackEntry ->
                SpaceSharingScreen(
                    localSpaceId = backStackEntry.arguments?.getString("spaceId")?.takeIf(String::isNotBlank),
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Calendario.route) { CalendarioScreen(onBack = { navController.popBackStack() }) }
            composable(Screen.Relatorio.route) { RelatorioScreen(onBack = { navController.popBackStack() }) }
            composable(Screen.RelatorioCategorias.route) {
                RelatorioScreen(
                    onBack = { navController.popBackStack() },
                    initialSection = ReportSection.CATEGORY_EVOLUTION
                )
            }
            composable(Screen.Recorrentes.route) {
                RecorrentesScreen(
                    onBack = { navController.popBackStack() },
                    onAdd = { navController.navigate(Screen.NovaConta.createRoute(ContaDirecao.PAGAR)) },
                    onOpenAccount = { id -> navController.navigate(Screen.ContaDetalhe.createRoute(id)) }
                )
            }
            composable(Screen.Alertas.route) {
                AlertCenterScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAccount = { id -> navController.navigate(Screen.ContaDetalhe.createRoute(id)) },
                    onPayAccount = { id -> navController.navigate(Screen.ContaDetalhe.createRoute(id, "pagar")) }
                )
            }
            composable(Screen.Clientes.route) { ClientesScreen(onBack = { navController.popBackStack() }) }
            composable(Screen.Cobrancas.route) { CobrancasScreen(onBack = { navController.popBackStack() }) }
            composable(Screen.Lixeira.route) { LixeiraScreen(onBack = { navController.popBackStack() }) }
            composable(Screen.Configuracoes.route) {
                ConfiguracoesScreen(
                    onBack = { navController.popBackStack() },
                    themeMode = themeMode,
                    onThemeModeChanged = onThemeModeChanged
                )
            }
            composable(Screen.Onboarding.route) {
                OnboardingScreen(onFinish = { navController.popBackStack() })
            }
            composable(Screen.SharedImport.route) {
                SharedExternalImportScreen(
                    uris = sharedUris,
                    onBack = {
                        onSharedUrisConsumed()
                        navController.popBackStack()
                    },
                    onFinished = {
                        onSharedUrisConsumed()
                        navController.navigate(Screen.Contas.route) {
                            popUpTo(Screen.Dashboard.route)
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(
                route = Screen.NovaConta.route,
                arguments = listOf(
                    navArgument("direcao") { type = NavType.StringType },
                    navArgument("codigo") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) { backStackEntry ->
                val scannedPix by backStackEntry.savedStateHandle
                    .getStateFlow("pixCode", "")
                    .collectAsState()
                ContaManualScreen(
                    direcao = backStackEntry.arguments?.getString("direcao") ?: ContaDirecao.PAGAR,
                    initialBarcode = backStackEntry.arguments?.getString("codigo").orEmpty(),
                    initialPix = scannedPix,
                    onBack = { navController.popBackStack() },
                    onScanBarcode = {
                        navController.navigate(Screen.ScannerManual.createRoute(
                            backStackEntry.arguments?.getString("direcao") ?: ContaDirecao.PAGAR
                        ))
                    },
                    onScanPix = { navController.navigate(Screen.ScannerPix.route) },
                    onSaved = {
                        navController.navigate(Screen.Contas.route) {
                            popUpTo(Screen.Dashboard.route)
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Screen.ScannerPix.route) {
                BoletosScreen(
                    onBack = { navController.popBackStack() },
                    scanMode = PaymentScanMode.PIX,
                    onBoletoFound = { code ->
                        navController.previousBackStackEntry?.savedStateHandle?.set("pixCode", code)
                        navController.popBackStack()
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
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("acao") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) { backStackEntry ->
                val scannedPix by backStackEntry.savedStateHandle
                    .getStateFlow("pixCode", "")
                    .collectAsState()
                ContaDetalheScreen(
                    contaId = backStackEntry.arguments?.getString("id").orEmpty(),
                    initialAction = backStackEntry.arguments?.getString("acao"),
                    initialPix = scannedPix,
                    onScanPix = { navController.navigate(Screen.ScannerPix.route) },
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

        LaunchedEffect(sharedUris, unlocked, onboardingDone) {
            if (sharedUris.isNotEmpty() && unlocked && onboardingDone) {
                navController.navigate(Screen.SharedImport.route) {
                    launchSingleTop = true
                }
            }
        }

        LaunchedEffect(authCallbackPending, unlocked, onboardingDone) {
            if (authCallbackPending && unlocked && onboardingDone) {
                navController.navigate(Screen.ContaOnline.route) {
                    launchSingleTop = true
                }
                onAuthCallbackConsumed()
            }
        }
    }
    }
}
