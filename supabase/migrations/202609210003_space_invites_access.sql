-- Permite que o destinatário visualize e aceite/recuse o próprio convite.
-- Também libera somente os nomes dos perfis que compartilham algum espaço.

-- Limpa convites enviados por engano para o próprio e-mail do remetente.
update public.space_invites i
set status = 'CANCELLED'
from auth.users u
where u.id = i.invited_by
  and i.status = 'PENDING'
  and lower(i.email) = lower(coalesce(u.email, ''));

create or replace function public.prevent_self_space_invite()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    inviter_email text;
begin
    select u.email into inviter_email
    from auth.users u
    where u.id = new.invited_by;

    if lower(new.email) = lower(coalesce(inviter_email, '')) then
        raise exception 'Nao e permitido convidar o proprio e-mail';
    end if;
    return new;
end;
$$;

drop trigger if exists space_invites_prevent_self on public.space_invites;
create trigger space_invites_prevent_self
before insert or update of email, invited_by on public.space_invites
for each row execute function public.prevent_self_space_invite();

create or replace function public.shares_space_with(other_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.spaces s
        left join public.space_members current_member
          on current_member.space_id = s.id and current_member.user_id = auth.uid()
        left join public.space_members other_member
          on other_member.space_id = s.id and other_member.user_id = other_user_id
        where s.deleted_at is null
          and (s.owner_id = auth.uid() or current_member.user_id = auth.uid())
          and (s.owner_id = other_user_id or other_member.user_id = other_user_id)
    );
$$;

revoke all on function public.shares_space_with(uuid) from public;
grant execute on function public.shares_space_with(uuid) to authenticated;

drop policy if exists profiles_read_shared_space on public.profiles;
create policy profiles_read_shared_space on public.profiles
for select to authenticated using (
    id = auth.uid() or public.shares_space_with(id)
);

drop policy if exists spaces_read_invitee on public.spaces;
create policy spaces_read_invitee on public.spaces
for select to authenticated using (
    exists (
        select 1
        from public.space_invites i
        where i.space_id = id
          and i.status = 'PENDING'
          and i.expires_at > now()
          and lower(i.email) = lower(coalesce(auth.jwt() ->> 'email', ''))
    )
);

drop policy if exists members_accept_own_invite on public.space_members;
create policy members_accept_own_invite on public.space_members
for insert to authenticated with check (
    user_id = auth.uid()
    and role <> 'OWNER'
    and exists (
        select 1
        from public.space_invites i
        where i.space_id = space_id
          and i.status = 'PENDING'
          and i.expires_at > now()
          and i.role = role
          and lower(i.email) = lower(coalesce(auth.jwt() ->> 'email', ''))
    )
);

drop policy if exists invites_respond_recipient on public.space_invites;
create policy invites_respond_recipient on public.space_invites
for update to authenticated
using (
    status = 'PENDING'
    and expires_at > now()
    and lower(email) = lower(coalesce(auth.jwt() ->> 'email', ''))
)
with check (
    lower(email) = lower(coalesce(auth.jwt() ->> 'email', ''))
    and status in ('ACCEPTED', 'CANCELLED')
);
