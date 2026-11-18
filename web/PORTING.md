# Ahorra, vaca — versión web (PWA para iPhone)

Port de la app Android (Kotlin/Compose, en `../ref/kt`) a React + TypeScript. Se instala en iPhone desde Safari
("Compartir → Añadir a pantalla de inicio"). Todo en **español**. Reglas de producto: `../ref/CLAUDE.md` (obligatorias).
Capturas de referencia de la app Android: `C:\Users\Empleado Oct 2023\trabajo pablo\caudal\screenshots\*.png` (solo leer).

## Estructura
- `src/data/models.ts` — tipos (Account, Expense…, Settings). `src/data/dates.ts` — fechas tipo java.time (LocalDate {year,month,day}, YearMonth, epochDay, dayLabel, monthLabel…). `src/data/format.ts` — dinero/números en español.
- `src/data/store.ts` — base de datos local. Leer: `useDB(s => s.accounts)` (selector que devuelva una referencia estable, NO objetos/arrays nuevos — para derivados usa useMemo), `useSettings()`, `getState()`. Escribir: `db.saveExpense(...)`, `db.deleteExpense(id)`, `db.saveIncome`, `db.saveTransfer`, `db.payBill(bill)`, `db.saveAccount`, `db.setBalance`, `db.saveCategories`, `db.saveCrypto/Stock`, `db.setSettings({...})`, `db.setRefund`, `db.saveSplit`… (mismas reglas de saldo que FinanceDao). Mensajes: `toast('texto')`.
- `src/domain/*.ts` — lógica pura portada 1:1 de `domain/*.kt` (mismos nombres). `src/domain/planning.ts` — `usePlanning()` (equivale a `vm.planning`).
- `src/data/services/*` — precios (CoinGecko/Yahoo), noticias, buzz, con hooks `useAssetHistory`, `useNews`… y acciones `refreshCrypto/refreshStocks`.
- `src/ui/components.tsx` — piezas comunes (equivalen a Components.kt): `Card`, `CardHead`, `SpottedCard` (tarjeta tinta con manchas), `SectionLabel`, `EmptyState`, `IconTile`, `Initials`, `AccountLogo`, `Avatar`, `Dot`, `ListRow`, `ProgressLine`, `StackedBar`, `LineChart`, `Donut`, `Bars`, `Button`, `IconButton`, `Pill`, `Segmented`, `Switch`, `TextField`, `AmountField`, `BigAmountInput`, `SelectField`, `Sheet`, `Overlay`, `ConfirmDialog`, `Money`, `BigAmount`, `AnimatedAmount`, `useMoney()` (respeta "ocultar importes"), `NotAdvice`, `parseAmount`, `amountInput`, `colorFor`, `SERIES_COLORS`, `gainClass`.
- `src/ui/icons.tsx` — `<Icon name="..."/>` (Material). Si falta un icono, añádelo al mapa P (trazado de Material Icons).
- `src/ui/header.tsx` — `ScreenHeader` (título grande + botones ojo/+/noticias/ajustes) y `HeaderActions`.
- `src/ui/nav.tsx` — `useNav()`: `setTab`, `openInvest(kind)`, `open({name:'account', accountId})`, `back()`, `replace()`, `edit({name:'expense', expense})`, `closeSheet()`. Rutas tipadas en `OverlayRoute` / `SheetRoute`.
- `src/ui/registry.tsx` — qué componente pinta cada pestaña / pantalla / hoja (archivo y nombre de export fijos).
- `src/ui/theme.css` — clases (`.screen`, `.card`, `.list-item`, `.pill`, `.btn`, `.field`, `.hscroll`, `.grid2`, `.hero-num`, `.mid-num`…) y variables de color (`--ink`, `--muted`, `--brand`, `--gain`, `--loss`, `--chip`, `--card`, marca: `--prado`, `--ubre`, `--heno`, `--tinta`, `--leche`, `--pasto`…).

## Convenciones
- Pestañas: componente sin props que devuelve `<div className="screen"> <ScreenHeader title="…"/> … </div>`.
- Pantallas superpuestas: reciben `{ route }` y devuelven `<Overlay title="…" onClose={nav.back}>…</Overlay>`.
- Hojas: reciben `{ route, onClose }` y devuelven `<Sheet title="…" onClose={onClose}>…</Sheet>`; al guardar, llamar a `db.*` y luego `onClose()`. Para abrir otra hoja desde una hoja: `nav.edit({...})` (sustituye).
- Regla de 8 puntos (márgenes 8/16/24/32, radios 16/24/32). Textos e importes idénticos a Android. Números con `useMoney().fmt`.
- Nada de IA, nada de anuncios, nada de recomendar comprar/vender (`<NotAdvice/>` donde Android lo muestra).
- Lo que en iPhone no puede existir (leer notificaciones de pagos, huella, bloquear capturas, widget) se omite o se sustituye: p. ej. "pegar el texto de la notificación del banco" (`PastePaymentSheet`, usa `domain/PaymentParser.ts`).
- Comprueba con `npx tsc --noEmit -p .` (desde `web/`): cero errores en tus archivos.
