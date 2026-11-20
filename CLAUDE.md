# Ahorra, vaca — app Android de finanzas personales

App para Android (Kotlin + Jetpack Compose + Material3 + Room) de Pablo, que vive en España. Todo en **español**.
Paquete: `com.caudal.finanzas` (el proyecto se llamó antes "Caudal").

## Reglas fijas (no cambiarlas sin que Pablo lo pida)
- **Todo gratis.** Nada de APIs de pago, créditos ni claves. Fuentes usadas: CoinGecko (gratis, limita consultas → hay espaciado de 2,5 s, caché de 60 s y reintento en `PriceService.get`), Yahoo Finance (acciones), Reddit `/hot.json`, RSS de Cinco Días, El País, Expansión, BeInCrypto y Google Noticias.
- **Sin IA dentro de la app.** Todos los textos y consejos salen de reglas fijas y cálculos con los datos del usuario.
  Única excepción, aprobada por Pablo: leer el texto de la foto de un ticket con ML Kit (`TicketOcr`, dentro del móvil, sin internet). Lo que pone lo interpretan reglas (`SharedReader`).
- **Números precisos y coherentes.** Nada de "te quedan X" contando ahorros: la **liquidez es solo la cuenta del banco** (tipo `BANK`); los ahorros (Robinhood) no se tocan.
- Nunca recomendar comprar o vender inversiones; avisos de "no es una recomendación".
- Sin anuncios en las noticias (filtro en `NewsService.isAd`).
- Supabase (amigos/chat) está sin configurar: la clave anon vale, **nunca** pedir ni aceptar la service_role.
- No editar los `.kt` (UTF-8) con `Get-Content/Set-Content` de PowerShell: rompe tildes. Usar el editor o Python con `encoding='utf-8'`.

## Diseño (UI/UX)
- **Regla de los 8 puntos**: márgenes, espacios, radios y tamaños en múltiplos de 8 (8, 16, 24, 32, 40, 48…); medio paso de 4 solo para detalles pequeños. Iconos de 16/24/32/40/48. Tarjetas con radio 24 y relleno 16–24.
- Estilo de referencia: tarjetas blancas redondeadas, números grandes, colores suaves por categoría, iconos/logos en círculo.

## Situación de Pablo (datos de ejemplo para entender el diseño)
- Cuenta principal Santander; ahorro en Robinhood al 5 % (a veces pasa por Revolut).
- Nómina fija días 1–5 (unos 250 € llegan hacia el 20); YouTube variable hacia el día 22, el importe se confirma hacia el día 3 (se pone en "¿Cuánto este mes?").
- Alquiler como gasto fijo. Comparte gastos con su amigo Juanpa (usan Tricount).

## Pantallas principales
- **Inicio** (en este orden): "En el banco", viaje, últimos movimientos, suscripciones (justo debajo), pregunta de Revolut, Pepe, edad financiera,
  panel "Gasto de la semana" + "Ritmo del mes" (frente a tu mes típico), avisos, "Tus límites del mes" por categoría, recibos, lectura semanal, hucha, retos.
  Se quitaron a petición de Pablo: círculos Estabilidad/Control/Progreso, "Consejo de hoy", "Presión de gasto", el plan "Hasta tu nómina",
  y la morralla (cuentas, cripto, objetivos, "¿Sabías que…?", "¿Lo compro o no?", "Este mes").
- **Categorías**: reglas en `domain/Spend.kt` (palabras/marcas + trozos de palabra); la lista legible está en `CATEGORIAS.md` (generada). Hay categoría **Ropa** aparte de Compras.
- **Lectura semanal** (`WeeklyRead`): los gastos "a golpes" (transporte, ropa, viajes…) se miran por meses y solo avisan si hay un pago anormalmente grande o muchos más pagos; los de diario, frente a la mediana semanal.
- **Avisos de pagos**: nunca se leen correo ni mensajería (`PaymentParser.NEVER_APPS`, `EMAIL_LIKE`); las devoluciones del banco entran como ingreso.
- **Recibos y suscripciones**: se descuentan solos el día del cobro (`BillCharger`), sin duplicar con el aviso del banco.
- **Juntar pagos**: en Movimientos, mantener pulsado, seleccionar y "Juntar" ("Cine jueves").
- **Edad financiera**: incluye la comparación con la riqueza neta mediana de España (EFF 2022 del Banco de España: 20.000 € los hogares de menos de 35 años; 142.700 € todos).
- **Patrimonio**: variación con desglose al tocarla; intereses compuestos reales por cuenta (`InterestEarned`). Los objetivos de ahorro (colchón) solo cuentan ahorro, no cripto ni acciones.
- **Movimientos** (antes "Gastos"): entró/salió, en qué puedes gastar, en qué se ha ido (tocar = pagos), cuentas con amigos + Tricount, ingresos.
- **Inversión**: Cripto (precio de la moneda en **dólares**, lo tuyo en **euros**) y Acciones; mercado populares aunque no tengas; ficha con gráfico, velas, estadísticas, por qué se habla, dónde comprar; deslizar para refrescar.
- **Perfil**: tick azul de verificado, tarjeta "Tu Wrapped" (degradado azul solo en la tarjeta; dentro, colores variados) con ficha para compartir.
- **Noticias** (icono arriba, junto a Ajustes): estilo TikTok, sin repetir las vistas, botón "¿Cómo me afecta?" (`NewsImpactAnalyzer`, reglas fijas).
- Botón **+**: transferencia entre cuentas, me deben/debo, gasto, ingreso, cuenta, cripto, acción, gasto fijo, modo viaje.
- **Modo viaje** (`Trips`, en `domain/Extras.kt`): lo gastado en los días del viaje cuenta como fijo (`Budgeting.isFixed`) para no desvirtuar la media.
- También en Inicio: suscripciones, hucha de redondeo (semana cerrada) y retos de la vaca. En Movimientos → Resumen: frente al mes anterior (mismos días).
- Fuera de la app: widget (`VacaWidget`), atajos del icono (`res/xml/shortcuts.xml`), compartir texto/ticket, y avisos (`BillWatch`, con la alarma de cada hora: cobros, pruebas gratis, poco saldo y resumen de la noche a las 21:00).

## Código
- `domain/`: lógica pura (Planning/BankPlan, Budget, Pulse, Signals, Goals, Wrapped, Debts, NewsImpact…). Con pruebas en `app/src/test`.
- `data/`: Room (versión 9), `Prefs` (SharedPreferences), `PriceService`, `NewsService`.
- `ui/`: Compose. `MainScreen.kt` gestiona pestañas, overlays (`Overlay`) y hojas (`Editing`).
- `AppTourTest` (Robolectric + Roborazzi) recorre toda la app y saca capturas en `screenshots/`. Si cambias textos que el recorrido busca, actualízalo.

## Compilar
Requiere JDK 21 (Android Studio lo trae). En Windows:
```
$env:JAVA_HOME="<ruta al JDK 21>"
.\gradlew.bat testDebugUnitTest assembleRelease
```
La APK sale en `app/build/outputs/apk/release/app-release.apk`; se copia como `Ahorra-vaca.apk` en la raíz para pasarla al móvil. Las pruebas completas tardan ~10 min.

**Firma para el móvil:** con minSdk 29, Gradle y `apksigner` solo ponen la firma v3, y algunos instaladores dan
"There was a problem while parsing the package". Hay que volver a firmar la APK con v1+v2+v3 (clave de debug) al copiarla:
```
java -jar <sdk>/build-tools/36.0.0/lib/apksigner.jar sign --ks ~/.android/debug.keystore --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey --min-sdk-version 21 --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out AhorraVaca.apk app/build/outputs/apk/release/app-release.apk
```
(`apksigner.bat` falla por los espacios de la ruta: usar el `.jar`). Solo se incluyen arm64-v8a y armeabi-v7a.
