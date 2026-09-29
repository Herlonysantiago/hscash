create table if not exists public.activity_logs (
    id text primary key,
    space_id uuid not null references public.spaces(id) on delete cascade,
    account_legacy_id text,
    action text not null,
    title text not null,
    details text,
    actor_id uuid references public.profiles(id) on delete set null,
    actor_name text,
    created_at timestamptz not null default now()
);

create index if not exists activity_logs_space_created_idx
    on public.activity_logs (space_id, created_at desc);

create index if not exists activity_logs_account_idx
    on public.activity_logs (space_id, account_legacy_id);

alter table public.activity_logs enable row level security;

drop policy if exists activity_logs_read_member on public.activity_logs;
create policy activity_logs_read_member on public.activity_logs
for select to authenticated using (public.is_space_member(space_id));

drop policy if exists activity_logs_create_editor on public.activity_logs;
create policy activity_logs_create_editor on public.activity_logs
for insert to authenticated with check (
    public.can_edit_space(space_id)
    and actor_id = auth.uid()
);

drop policy if exists activity_logs_update_own on public.activity_logs;
create policy activity_logs_update_own on public.activity_logs
for update to authenticated using (
    public.can_edit_space(space_id)
    and actor_id = auth.uid()
) with check (
    public.can_edit_space(space_id)
    and actor_id = auth.uid()
);

grant select, insert, update on public.activity_logs to authenticated;

comment on table public.activity_logs is 'Linha do tempo de alteracoes feitas nas contas e espacos do HS Cash.';
comment on column public.activity_logs.account_legacy_id is 'ID local da conta usado pelo app para vincular historico entre dispositivos.';
