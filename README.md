# HS Cash

HS Cash e um aplicativo Android para leitura, cadastro e controle de boletos e contas a pagar.

O mesmo repositorio tambem contem a base do painel web em [`web/`](web/README.md). Android (`app/`), web (`web/`) e Supabase (`supabase/`) permanecem separados, compartilhando o mesmo produto e backend.

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
- Cadastro de clientes e cobrancas por WhatsApp.
- Lista de cobrancas em aberto para contas a receber.
- Relatorio mensal com exportacao em PDF.
- Lixeira para restaurar contas removidas.
- Notificacoes de vencimento e resumos diarios configuraveis.
- Exportacao das contas em CSV.
- Backup automatico local em JSON.
- Bloqueio do app por senha e desbloqueio opcional por biometria.
- Anexo e abertura de comprovantes em PDF, JPG, PNG ou outros formatos suportados.
- Icone personalizado do app.

> Recomendacao: para maior seguranca, mantenha uma copia dos backups em local seguro, como nuvem, pendrive, computador ou outro aparelho.

## Estrutura Principal

```text
app/       # aplicativo Android
web/       # painel web React/Vite
supabase/  # schema, RLS e migracoes do backend compartilhado

app/src/main/java/hssolutions/hscash/
  MainActivity.kt
  ui/
    BarcodeAnalyzer.kt
    BoletoInfo.kt
    BoletosScreen.kt
    ContasScreen.kt
    ContaDetalheScreen.kt
    ContaStorage.kt
    ContaUiUtils.kt
    DashboardScreen.kt
    ExportHelper.kt
    HistoricoScreen.kt
    NotificationScheduler.kt
    ReceiptHelper.kt
```

## Tecnologias

- Kotlin
- Jetpack Compose
- CameraX
- ML Kit Barcode Scanning
- Navigation Compose
- Room para armazenamento local das contas
- SharedPreferences para preferencias do app
- Android Notifications
- FileProvider para exportacao CSV e abertura de comprovantes

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

## Privacidade

Um rascunho inicial esta em [PRIVACY_POLICY.md](PRIVACY_POLICY.md).

Antes de publicar, atualize o contato oficial da HS Solutions e confira se o texto reflete todas as funcionalidades ativas no app.

## Observacoes

Os dados sao salvos localmente no aparelho. No momento, o app nao usa servidor externo nem login.

Alguns boletos de arrecadacao/convenio podem nao trazer vencimento no codigo. Nesses casos, o app solicita a data manualmente antes de salvar.
