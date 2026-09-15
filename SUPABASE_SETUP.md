# Preparacao do Supabase para o HS Cash

O aplicativo continua completamente offline enquanto as credenciais abaixo
estiverem vazias. Nunca coloque uma `service_role` key no Android.

## 1. Criar o projeto gratuito

1. Crie um projeto em https://supabase.com/dashboard.
2. No SQL Editor, execute
   `supabase/migrations/202609140001_initial_cloud_schema.sql`.
3. Em **Authentication > Providers**, mantenha Email habilitado.
4. Em **Authentication > URL Configuration**, configure os links do aplicativo
   apenas quando a tela de login por link ou Google for implementada.

## 2. Conectar somente esta maquina

Acrescente ao `local.properties` da raiz do projeto:

```properties
SUPABASE_URL=https://SEU-PROJETO.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_SUA_CHAVE
```

O `local.properties` ja esta ignorado pelo Git. A configuracao aceita tambem as
mesmas variaveis de ambiente no servidor de compilacao.

## 3. Modelo de seguranca preparado

- Cada dado pertence a um espaco.
- Um espaco so aparece para o dono ou para um membro convidado.
- `OWNER`, `ADMIN` e `EDITOR` podem alterar dados; `VIEWER` somente consulta.
- Boletos e comprovantes ficam em bucket privado.
- O caminho de arquivo deve ser
  `<space_id>/<account_id>/<nome-do-arquivo>`.
- Exclusoes de contas podem ser sincronizadas por `deleted_at` antes da limpeza
  definitiva.

## Proximas etapas antes de enviar dados reais

1. Tela de cadastro/login e recuperacao de senha.
2. Criacao e selecao de espacos.
3. Aceite seguro de convites.
4. Migracao local com fila de sincronizacao e resolucao de conflitos.
5. Upload/download de PDFs e comprovantes.
6. Testes com dois usuarios em aparelhos diferentes.

