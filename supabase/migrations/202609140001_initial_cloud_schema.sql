-- HS Cash: estrutura inicial multiusuario.
-- Execute pelo SQL Editor de um projeto Supabase vazio.

create extension if not exists pgcrypto;

create type public.space_type as enum ('PESSOAL', 'FAMILIA', 'EMPRESA');
create type public.member_role as enum ('OWNER', 'ADMIN', 'EDITOR', 'VIEWER');
create type public.invite_status as enum ('PENDING', 'ACCEPTED', 'CANCELLED', 'EXPIRED');
create type public.attachment_type as enum ('BOLETO', 'COMPROVANTE');

create table public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    nome text not null default '',
    avatar_path text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.spaces (
    id uuid primary key default gen_random_uuid(),
    nome text not null check (char_length(trim(nome)) between 1 and 80),
    tipo public.space_type not null default 'PESSOAL',
    icone text not null default 'wallet',
    cor text not null default '#0B3D6E',
    imagem_path text,
    owner_id uuid not null references auth.users(id) on delete restrict,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

create table public.space_members (
    space_id uuid not null references public.spaces(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    role public.member_role not null default 'VIEWER',
    created_at timestamptz not null default now(),
    primary key (space_id, user_id)
);

create table public.space_invites (
    id uuid primary key default gen_random_uuid(),
    space_id uuid not null references public.spaces(id) on delete cascade,
    email text not null,
    role public.member_role not null default 'EDITOR'
        check (role <> 'OWNER'),
    status public.invite_status not null default 'PENDING',
    invited_by uuid not null references auth.users(id) on delete cascade,
    expires_at timestamptz not null default (now() + interval '7 days'),
    accepted_at timestamptz,
    created_at timestamptz not null default now()
);

create unique index space_invites_one_pending_per_email
    on public.space_invites (space_id, lower(email))
    where status = 'PENDING';

create table public.accounts (
    id uuid primary key default gen_random_uuid(),
    space_id uuid not null references public.spaces(id) on delete cascade,
    legacy_id text,
    codigo text not null default '',
    banco text not null default '',
    valor_centavos bigint not null check (valor_centavos >= 0),
    vencimento date not null,
    tipo text not null default '',
    fornecedor text not null default '',
    categoria text not null default 'Outros',
    status text not null default 'PENDENTE'
        check (status in ('PENDENTE', 'PAGO', 'EXCLUIDO')),
    pago_em timestamptz,
    forma_pagamento text,
    observacao_pagamento text,
    recorrente boolean not null default false,
    recorrencia text not null default 'Mensal',
    direcao text not null default 'PAGAR'
        check (direcao in ('PAGAR', 'RECEBER')),
    cliente_whatsapp text,
    created_by uuid not null default auth.uid() references auth.users(id) on delete restrict,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    unique (space_id, legacy_id),
    unique (id, space_id)
);

create index accounts_space_updated_idx on public.accounts (space_id, updated_at);
create index accounts_space_due_idx on public.accounts (space_id, vencimento);

create table public.attachments (
    id uuid primary key default gen_random_uuid(),
    space_id uuid not null references public.spaces(id) on delete cascade,
    account_id uuid not null,
    tipo public.attachment_type not null,
    storage_path text not null unique,
    mime_type text not null,
    tamanho_bytes bigint check (tamanho_bytes is null or tamanho_bytes >= 0),
    created_by uuid not null default auth.uid() references auth.users(id) on delete restrict,
    created_at timestamptz not null default now(),
    deleted_at timestamptz,
    foreign key (account_id, space_id)
        references public.accounts(id, space_id) on delete cascade
);

create or replace function public.touch_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create trigger profiles_touch_updated_at before update on public.profiles
for each row execute function public.touch_updated_at();
create trigger spaces_touch_updated_at before update on public.spaces
for each row execute function public.touch_updated_at();
create trigger accounts_touch_updated_at before update on public.accounts
for each row execute function public.touch_updated_at();

create or replace function public.prevent_space_owner_change()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    if new.owner_id <> old.owner_id then
        raise exception 'O proprietario do espaco nao pode ser alterado diretamente';
    end if;
    return new;
end;
$$;

create trigger spaces_prevent_owner_change before update on public.spaces
for each row execute function public.prevent_space_owner_change();

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.profiles (id, nome)
    values (new.id, coalesce(new.raw_user_meta_data ->> 'name', ''))
    on conflict (id) do nothing;
    return new;
end;
$$;

create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_user();

-- Funcoes SECURITY DEFINER evitam recursao nas policies de membros.
create or replace function public.is_space_member(requested_space_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.spaces s
        left join public.space_members m
          on m.space_id = s.id and m.user_id = auth.uid()
        where s.id = requested_space_id
          and s.deleted_at is null
          and (s.owner_id = auth.uid() or m.user_id is not null)
    );
$$;

create or replace function public.can_edit_space(requested_space_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.spaces s
        left join public.space_members m
          on m.space_id = s.id and m.user_id = auth.uid()
        where s.id = requested_space_id
          and s.deleted_at is null
          and (s.owner_id = auth.uid() or m.role in ('OWNER', 'ADMIN', 'EDITOR'))
    );
$$;

create or replace function public.can_manage_space(requested_space_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.spaces s
        left join public.space_members m
          on m.space_id = s.id and m.user_id = auth.uid()
        where s.id = requested_space_id
          and s.deleted_at is null
          and (s.owner_id = auth.uid() or m.role in ('OWNER', 'ADMIN'))
    );
$$;

revoke all on function public.is_space_member(uuid) from public;
revoke all on function public.can_edit_space(uuid) from public;
revoke all on function public.can_manage_space(uuid) from public;
grant execute on function public.is_space_member(uuid) to authenticated;
grant execute on function public.can_edit_space(uuid) to authenticated;
grant execute on function public.can_manage_space(uuid) to authenticated;

alter table public.profiles enable row level security;
alter table public.spaces enable row level security;
alter table public.space_members enable row level security;
alter table public.space_invites enable row level security;
alter table public.accounts enable row level security;
alter table public.attachments enable row level security;

create policy profiles_read_own on public.profiles
for select to authenticated using (id = auth.uid());
create policy profiles_update_own on public.profiles
for update to authenticated using (id = auth.uid()) with check (id = auth.uid());

create policy spaces_read_member on public.spaces
for select to authenticated using (public.is_space_member(id));
create policy spaces_create_own on public.spaces
for insert to authenticated with check (owner_id = auth.uid());
create policy spaces_update_manager on public.spaces
for update to authenticated using (public.can_manage_space(id))
with check (public.can_manage_space(id));

create policy members_read_member on public.space_members
for select to authenticated using (public.is_space_member(space_id));
create policy members_create_manager on public.space_members
for insert to authenticated with check (
    public.can_manage_space(space_id) and role <> 'OWNER'
);
create policy members_update_manager on public.space_members
for update to authenticated using (public.can_manage_space(space_id))
with check (public.can_manage_space(space_id) and role <> 'OWNER');
create policy members_delete_manager on public.space_members
for delete to authenticated using (
    public.can_manage_space(space_id) and role <> 'OWNER'
);

create policy invites_read_manager_or_recipient on public.space_invites
for select to authenticated using (
    public.can_manage_space(space_id)
    or lower(email) = lower(coalesce(auth.jwt() ->> 'email', ''))
);
create policy invites_create_manager on public.space_invites
for insert to authenticated with check (
    public.can_manage_space(space_id)
    and invited_by = auth.uid()
    and role <> 'OWNER'
);
create policy invites_update_manager on public.space_invites
for update to authenticated using (public.can_manage_space(space_id))
with check (public.can_manage_space(space_id));

create policy accounts_read_member on public.accounts
for select to authenticated using (public.is_space_member(space_id));
create policy accounts_create_editor on public.accounts
for insert to authenticated with check (
    public.can_edit_space(space_id) and created_by = auth.uid()
);
create policy accounts_update_editor on public.accounts
for update to authenticated using (public.can_edit_space(space_id))
with check (public.can_edit_space(space_id));
create policy accounts_delete_editor on public.accounts
for delete to authenticated using (public.can_edit_space(space_id));

create policy attachments_read_member on public.attachments
for select to authenticated using (public.is_space_member(space_id));
create policy attachments_create_editor on public.attachments
for insert to authenticated with check (
    public.can_edit_space(space_id) and created_by = auth.uid()
);
create policy attachments_update_editor on public.attachments
for update to authenticated using (public.can_edit_space(space_id))
with check (public.can_edit_space(space_id));
create policy attachments_delete_editor on public.attachments
for delete to authenticated using (public.can_edit_space(space_id));

grant select, update on public.profiles to authenticated;
grant select, insert, update, delete on public.spaces to authenticated;
grant select, insert, update, delete on public.space_members to authenticated;
grant select, insert, update, delete on public.space_invites to authenticated;
grant select, insert, update, delete on public.accounts to authenticated;
grant select, insert, update, delete on public.attachments to authenticated;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
    'hs-cash-documents',
    'hs-cash-documents',
    false,
    20971520,
    array['application/pdf', 'image/jpeg', 'image/png', 'image/webp']
)
on conflict (id) do nothing;

-- Todo arquivo deve usar: <space_id>/<account_id>/<nome-do-arquivo>.
create policy documents_read_member on storage.objects
for select to authenticated using (
    bucket_id = 'hs-cash-documents'
    and exists (
        select 1 from public.spaces s
        where s.id::text = (storage.foldername(name))[1]
          and public.is_space_member(s.id)
    )
);
create policy documents_create_editor on storage.objects
for insert to authenticated with check (
    bucket_id = 'hs-cash-documents'
    and exists (
        select 1 from public.spaces s
        where s.id::text = (storage.foldername(name))[1]
          and public.can_edit_space(s.id)
    )
);
create policy documents_update_editor on storage.objects
for update to authenticated using (
    bucket_id = 'hs-cash-documents'
    and exists (
        select 1 from public.spaces s
        where s.id::text = (storage.foldername(name))[1]
          and public.can_edit_space(s.id)
    )
);
create policy documents_delete_editor on storage.objects
for delete to authenticated using (
    bucket_id = 'hs-cash-documents'
    and exists (
        select 1 from public.spaces s
        where s.id::text = (storage.foldername(name))[1]
          and public.can_edit_space(s.id)
    )
);
