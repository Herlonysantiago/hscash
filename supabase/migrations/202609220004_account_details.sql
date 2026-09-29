alter table public.accounts
    add column if not exists subcategoria text,
    add column if not exists responsavel text,
    add column if not exists pago_por text;

comment on column public.accounts.subcategoria is 'Detalhamento opcional da categoria da conta';
comment on column public.accounts.responsavel is 'Pessoa ou equipe responsavel pela conta';
comment on column public.accounts.pago_por is 'Pessoa que confirmou ou realizou o pagamento';
