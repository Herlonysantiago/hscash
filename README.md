# HS Cash

HS Cash e um aplicativo Android para leitura, cadastro e controle de boletos e contas a pagar.

O app permite escanear boletos pela camera, digitar codigo de barras manualmente, extrair dados como banco, valor e vencimento, salvar contas localmente e acompanhar vencimentos em um dashboard.

## Funcionalidades

- Leitura de boleto por camera com ML Kit.
- Entrada manual de codigo de barras ou linha digitavel.
- Identificacao de banco emissor, valor e vencimento.
- Campo manual de vencimento quando o boleto nao informa data.
- Cadastro de fornecedor e categoria.
- Lista de contas salvas com busca e filtros.
- Status de conta: pendente, atrasado e pago.
- Edicao, exclusao e marcacao de conta como paga.
- Historico de contas pagas.
- Dashboard financeiro com total em aberto, pago no mes e proximos vencimentos.
- Notificacoes de vencimento um dia antes e no dia.
- Exportacao das contas em CSV.
- Icone personalizado do app.

## Estrutura Principal

```text
app/src/main/java/hssolutions/hscash/
  MainActivity.kt
  ui/
    BarcodeAnalyzer.kt
    BoletoInfo.kt
    BoletosScreen.kt
    ContaDetalheScreen.kt
    ContaStorage.kt
    ContaUiUtils.kt
    DashboardScreen.kt
    EstoqueScreen.kt
    ExportHelper.kt
    HistoricoScreen.kt
    NotificationScheduler.kt
```

## Tecnologias

- Kotlin
- Jetpack Compose
- CameraX
- ML Kit Barcode Scanning
- Navigation Compose
- SharedPreferences para armazenamento local
- Android Notifications
- FileProvider para exportacao CSV

## Como Compilar

No Android Studio, abra a pasta:

```text
C:\Users\MERCADO\AndroidStudioProjects\Hscash
```

Depois execute:

```text
Build > Make Project
```

Ou pelo terminal:

```powershell
.\gradlew.bat :app:assembleDebug
```

O APK de debug sera gerado em:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Permissoes

O app usa:

- Camera: para leitura de boletos.
- Notificacoes: para alertas de vencimento.

## Observacoes

Os dados sao salvos localmente no aparelho. No momento, o app nao usa servidor externo nem login.

Alguns boletos de arrecadacao/convenio podem nao trazer vencimento no codigo. Nesses casos, o app solicita a data manualmente antes de salvar.
