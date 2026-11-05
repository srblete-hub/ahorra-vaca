# Ahorra, vaca — app Android de finanzas personales

App para Android (Kotlin + Jetpack Compose + Material3 + Room) de Pablo, que vive en España. Todo en **español**.
Paquete: `com.caudal.finanzas` (el proyecto se llamó antes "Caudal").

## Reglas fijas (no cambiarlas sin que Pablo lo pida)
- **Todo gratis.** Nada de APIs de pago, créditos ni claves. Fuentes usadas: CoinGecko (gratis, limita consultas → hay espaciado de 2,5 s, caché de 60 s y reintento en `PriceService.get`), Yahoo Finance (acciones), Reddit `/hot.json`, RSS de Cinco Días, El País, Expansión, BeInCrypto y Google Noticias.
- **Sin IA dentro de la app.** Todos los textos y consejos salen de reglas fijas y cálculos con los datos del usuario.
- **Números precisos y coherentes.** Nada de "te quedan X" contando ahorros: la **liquidez es solo la cuenta del banco** (tipo `BANK`); los ahorros (Robinhood) no se tocan.
- Nunca recomendar comprar o vender inversiones; avisos de "no es una recomendación".
- Sin anuncios en las noticias (filtro en `NewsService.isAd`).
- Supabase (amigos/chat) está sin configurar: la clave anon vale, **nunca** pedir ni aceptar la service_role.
- No editar los `.kt` (UTF-8) con `Get-Content/Set-Content` de PowerShell: rompe tildes. Usar el editor o Python con `encoding='utf-8'`.

## Situación de Pablo (datos de ejemplo para entender el diseño)
- Cuenta principal Santander; ahorro en Robinhood al 5 % (a veces pasa por Revolut).
- Nómina fija días 1–5 (unos 250 € llegan hacia el 20); YouTube variable hacia el día 22, el importe se confirma hacia el día 3 (se pone en "¿Cuánto este mes?").
- Alquiler como gasto fijo. Comparte gastos con su amigo Juanpa (usan Tricount).

## Pantallas principales
- **Inicio**: "En el banco" arriba, cuadros "Gasto de la semana" y "Presión de gasto" (presión = gastado hoy / lo que toca hoy), avisos de límites, últimos movimientos, reparto, objetivos, edad financiera (más edad = mejor).
  Se quitaron a petición de Pablo: los círculos Estabilidad/Control/Progreso y el "Consejo de hoy".
- **Patrimonio**: variación con desglose al tocarla; intereses compuestos reales por cuenta (`InterestEarned`).
- **Movimientos** (antes "Gastos"): entró/salió, en qué puedes gastar, en qué se ha ido (tocar = pagos), cuentas con amigos + Tricount, ingresos.
- **Inversión**: Cripto (precio de la moneda en **dólares**, lo tuyo en **euros**) y Acciones; mercado populares aunque no tengas; ficha con gráfico, velas, estadísticas, por qué se habla, dónde comprar; deslizar para refrescar.
- **Perfil**: tick azul de verificado, tarjeta "Tu Wrapped" (degradado azul solo en la tarjeta; dentro, colores variados) con ficha para compartir.
- **Noticias** (icono arriba, junto a Ajustes): estilo TikTok, sin repetir las vistas, botón "¿Cómo me afecta?" (`NewsImpactAnalyzer`, reglas fijas).
- Botón **+**: transferencia entre cuentas, me deben/debo, gasto, ingreso, cuenta, cripto, acción, gasto fijo.

## Código
- `domain/`: lógica pura (Planning/BankPlan, Budget, Pulse, Signals, Goals, Wrapped, Debts, NewsImpact…). Con pruebas en `app/src/test`.
- `data/`: Room (versión 8), `Prefs` (SharedPreferences), `PriceService`, `NewsService`.
- `ui/`: Compose. `MainScreen.kt` gestiona pestañas, overlays (`Overlay`) y hojas (`Editing`).
- `AppTourTest` (Robolectric + Roborazzi) recorre toda la app y saca capturas en `screenshots/`. Si cambias textos que el recorrido busca, actualízalo.

## Compilar
Requiere JDK 21 (Android Studio lo trae). En Windows:
```
$env:JAVA_HOME="<ruta al JDK 21>"
.\gradlew.bat testDebugUnitTest assembleRelease
```
La APK sale en `app/build/outputs/apk/release/app-release.apk`; se copia como `Ahorra-vaca.apk` en la raíz para pasarla al móvil. Las pruebas completas tardan ~10 min.
