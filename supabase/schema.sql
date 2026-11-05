-- Caudal · parte social
-- Pégalo entero en Supabase → SQL Editor → New query → Run. Se puede ejecutar más de una vez.
--
-- Qué guarda el servidor: tu @, nombre, foto, nivel, puntos y los logros que decidas compartir.
-- Nunca guarda saldos, cuentas, movimientos ni importes.

-- ---------- Perfiles ----------
create table if not exists public.profiles (
  id          uuid primary key references auth.users on delete cascade,
  handle      text unique not null check (handle ~ '^[a-z0-9._]{3,20}$'),
  name        text not null default '',
  avatar_url  text,
  level       int  not null default 1,
  level_name  text not null default 'Semilla',
  points      int  not null default 0,
  verified    boolean not null default false,
  updated_at  timestamptz not null default now()
);
alter table public.profiles enable row level security;

drop policy if exists "perfiles visibles" on public.profiles;
create policy "perfiles visibles" on public.profiles for select to authenticated using (true);
drop policy if exists "crear mi perfil" on public.profiles;
create policy "crear mi perfil" on public.profiles for insert to authenticated with check (auth.uid() = id and verified = false);
drop policy if exists "editar mi perfil" on public.profiles;
create policy "editar mi perfil" on public.profiles for update to authenticated using (auth.uid() = id) with check (auth.uid() = id);

-- El verificado azul solo se pone desde el panel de Supabase: si lo intenta cambiar un usuario, se mantiene.
create or replace function public.keep_verified() returns trigger language plpgsql as $$
begin
  if auth.role() = 'authenticated' then new.verified := old.verified; end if;
  new.updated_at := now();
  return new;
end $$;
drop trigger if exists profiles_keep_verified on public.profiles;
create trigger profiles_keep_verified before update on public.profiles for each row execute function public.keep_verified();

-- ---------- Amistades ----------
create table if not exists public.friendships (
  requester  uuid not null references public.profiles(id) on delete cascade,
  addressee  uuid not null references public.profiles(id) on delete cascade,
  status     text not null default 'pending' check (status in ('pending', 'accepted')),
  created_at timestamptz not null default now(),
  primary key (requester, addressee),
  check (requester <> addressee)
);
alter table public.friendships enable row level security;

drop policy if exists "ver mis amistades" on public.friendships;
create policy "ver mis amistades" on public.friendships for select to authenticated using (auth.uid() in (requester, addressee));
drop policy if exists "enviar solicitud" on public.friendships;
create policy "enviar solicitud" on public.friendships for insert to authenticated with check (auth.uid() = requester and status = 'pending');
drop policy if exists "aceptar solicitud" on public.friendships;
create policy "aceptar solicitud" on public.friendships for update to authenticated using (auth.uid() = addressee) with check (status = 'accepted');
drop policy if exists "quitar amistad" on public.friendships;
create policy "quitar amistad" on public.friendships for delete to authenticated using (auth.uid() in (requester, addressee));

-- ---------- Logros compartidos ----------
create table if not exists public.achievements (
  user_id        uuid not null references public.profiles(id) on delete cascade,
  achievement_id text not null,
  title          text not null,
  icon           text not null,
  unlocked_at    timestamptz not null,
  primary key (user_id, achievement_id)
);
alter table public.achievements enable row level security;

drop policy if exists "ver logros de amigos" on public.achievements;
create policy "ver logros de amigos" on public.achievements for select to authenticated using (
  user_id = auth.uid() or exists (
    select 1 from public.friendships f
    where f.status = 'accepted'
      and ((f.requester = auth.uid() and f.addressee = user_id) or (f.addressee = auth.uid() and f.requester = user_id))
  )
);
drop policy if exists "mis logros" on public.achievements;
create policy "mis logros" on public.achievements for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

-- ---------- Fotos de perfil ----------
insert into storage.buckets (id, name, public) values ('avatars', 'avatars', true) on conflict (id) do nothing;
drop policy if exists "subir mi foto" on storage.objects;
create policy "subir mi foto" on storage.objects for insert to authenticated
  with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists "cambiar mi foto" on storage.objects;
create policy "cambiar mi foto" on storage.objects for update to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

-- ---------- Tu verificado azul ----------
-- Cuando hayas creado tu cuenta en la app, ejecuta esto cambiando el @:
--   update public.profiles set verified = true where handle = 'tu_handle';
