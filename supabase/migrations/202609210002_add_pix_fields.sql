-- Adiciona os dados Pix às contas já existentes sem apagar ou alterar registros.
alter table public.accounts
    add column if not exists pix_copia_cola text,
    add column if not exists pix_chave text,
    add column if not exists pix_recebedor text,
    add column if not exists pix_txid text,
    add column if not exists pix_agendado boolean not null default false,
    add column if not exists pix_agendamento_status text;

alter table public.accounts
    drop constraint if exists accounts_pix_agendamento_status_check;

alter table public.accounts
    add constraint accounts_pix_agendamento_status_check
    check (
        pix_agendamento_status is null
        or pix_agendamento_status in ('AGENDADO', 'EXECUTADO', 'FALHOU')
    );

create index if not exists accounts_space_pix_txid_idx
    on public.accounts (space_id, pix_txid)
    where pix_txid is not null;
