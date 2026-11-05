package com.caudal.finanzas

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.caudal.finanzas.data.Account
import com.caudal.finanzas.data.AccountType
import com.caudal.finanzas.data.CryptoHolding
import com.caudal.finanzas.data.Expense
import com.caudal.finanzas.data.Income
import com.caudal.finanzas.data.IncomeSource
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.caudal.finanzas.data.FinanceDatabase
import com.caudal.finanzas.data.NetWorthSnapshot
import com.caudal.finanzas.data.Prefs
import com.caudal.finanzas.data.StockHolding
import com.caudal.finanzas.payments.PaymentParser
import com.caudal.finanzas.payments.PaymentRecorder
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import kotlin.math.sin

/**
 * Recorre la app real en el PC (sin emulador): crea la cuenta, mete datos de ejemplo, simula un
 * pago con Google Wallet, visita cada pantalla y guarda una captura de cada una en caudal/screenshots/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h880dp-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class, ExperimentalTestApi::class)
class AppTourTest {
    // La parte social va contra un servidor en memoria (sin internet).
    init {
        com.caudal.finanzas.social.Social.overrideForTests(com.caudal.finanzas.social.InMemorySocialBackend())
        // Historial de precios inventado (sin internet): cada moneda con su forma.
        com.caudal.finanzas.data.PriceService.statsOverride = { _ ->
            com.caudal.finanzas.data.CoinStats(2, 3.2e11, 1.4e10, 2710.0, 2598.0, 4100.0, -35.4, 3.1, -6.8, 22.5, 1.2e8, null)
        }
        com.caudal.finanzas.data.PriceService.trendingOverride = {
            listOf(com.caudal.finanzas.data.TrendingCoin("solana", "SOL", "Solana", 5, 6.2), com.caudal.finanzas.data.TrendingCoin("pepe", "PEPE", "Pepe", 30, -4.0))
        }
        com.caudal.finanzas.data.PriceService.redditOverride = { _ ->
            listOf(
                com.caudal.finanzas.data.RedditPost("Solana breakout: new ATH incoming, very bullish", 900, 120, "https://www.reddit.com/r/x/1"),
                com.caudal.finanzas.data.RedditPost("SOL adoption keeps growing after the ETF approval", 400, 80, "https://www.reddit.com/r/x/2"),
                com.caudal.finanzas.data.RedditPost("Bitcoin holds 60k while ETF inflows rise", 650, 140, "https://www.reddit.com/r/x/3"),
                com.caudal.finanzas.data.RedditPost("Dogecoin crash: another dump, bearish week", 300, 60, "https://www.reddit.com/r/x/4"),
            )
        }
        com.caudal.finanzas.data.PriceService.ohlcOverride = { id, _ ->
            val seed = Math.floorMod(id.hashCode(), 7)
            val k = when (id) { "bitcoin" -> 600.0; "ethereum" -> 26.0; "solana" -> 1.6; else -> 1.0 }
            (0 until 30).map { i ->
                val o = 100 + i * 0.4 + sin(i / (2.0 + seed)) * 6
                val c = o + sin(i * 1.7 + seed) * 3
                com.caudal.finanzas.data.Candle(o * k, (maxOf(o, c) + 1.5) * k, (minOf(o, c) - 1.5) * k, c * k)
            }
        }
        com.caudal.finanzas.data.NewsService.override = {
            val now = System.currentTimeMillis()
            listOf(
                com.caudal.finanzas.data.NewsItem("Ethereum cae un 7 % tras la salida masiva de fondos cotizados", "Expansión", "https://example.com/1", now - 40 * 60_000, "La segunda criptomoneda se deja más de un 7 % en una jornada de ventas generalizadas.", "Cripto"),
                com.caudal.finanzas.data.NewsItem("El BCE baja los tipos de interés 0,25 puntos", "Cinco Días", "https://example.com/2", now - 3 * 3600_000, "", "Economía"),
                com.caudal.finanzas.data.NewsItem("Inditex dispara su beneficio un 9 % en el trimestre", "El País", "https://example.com/3", now - 5 * 3600_000, "", "Empresas"),
            )
        }
        com.caudal.finanzas.data.PriceService.exchangesOverride = { _ ->
            listOf(
                com.caudal.finanzas.data.CoinMarket("Binance", "SOL/USDT", "https://www.binance.com/", true),
                com.caudal.finanzas.data.CoinMarket("Coinbase Exchange", "SOL/EUR", "https://www.coinbase.com/", true),
                com.caudal.finanzas.data.CoinMarket("Kraken", "SOL/EUR", "https://www.kraken.com/", true),
                com.caudal.finanzas.data.CoinMarket("Bitvavo", "SOL/EUR", null, false),
            )
        }
        com.caudal.finanzas.data.PriceService.marketsOverride = {
            listOf(
                com.caudal.finanzas.data.MarketCoin("bitcoin", "BTC", "Bitcoin", 61234.5, 1.3, (0 until 24).map { 60000 + it * 50 + sin(it / 2.0) * 300 }),
                com.caudal.finanzas.data.MarketCoin("ethereum", "ETH", "Ethereum", 2512.4, -4.2, (0 until 24).map { 2620 - it * 4.5 + sin(it / 3.0) * 20 }),
                com.caudal.finanzas.data.MarketCoin("solana", "SOL", "Solana", 152.1, 3.1, (0 until 24).map { 147 + it * 0.2 + sin(it / 2.5) * 2 }),
                com.caudal.finanzas.data.MarketCoin("dogecoin", "DOGE", "Dogecoin", 0.16, -1.0, (0 until 24).map { 0.162 - it * 0.0001 }),
            )
        }
        com.caudal.finanzas.data.PriceService.historyOverride = { id, days ->
            val seed = Math.floorMod(id.hashCode(), 7)
            (0 until 40).map { i -> 100 + i * (if (seed % 2 == 0) 0.6 else -0.35) + sin(i / (3.0 + seed)) * 6 }.map { it * when (id) { "bitcoin" -> 600.0; "ethereum" -> 26.0; "solana" -> 1.6; "AAPL" -> 2.1; else -> 1.0 } }
        }
    }

    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val dir = System.getProperty("screenshots.dir") ?: "build/screenshots"
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun shot(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("$dir/$name.png")
    }

    private fun typePin(pin: String) = pin.forEach { compose.onNodeWithText(it.toString()).performClick() }
    private fun tab(label: String) { closeCelebration(); compose.onNodeWithContentDescription(label).performClick(); compose.waitForIdle() }
    private fun exists(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    private fun back() { compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; compose.waitForIdle() }

    /** Cierra la celebración de logros si está abierta (y la fotografía la primera vez). */
    private var celebrationShot = false
    private fun closeCelebration() {
        compose.waitForIdle()
        if (exists("¡Genial!")) {
            if (!celebrationShot) { shot("04y_logro"); celebrationShot = true }
            compose.onNodeWithText("¡Genial!").performClick()
            compose.waitForIdle()
        }
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text, substring = true))
        compose.waitForIdle()
    }

    @Test
    fun tour() {
        // --- Crear cuenta ---
        shot("01_bienvenida")
        compose.onNode(hasSetTextAction()).performTextInput("Alex")
        compose.onNodeWithText("Empezar").performClick()
        shot("02_crear_pin")
        typePin("123456"); compose.waitForIdle()
        typePin("123456"); compose.waitForIdle()
        if (exists("Ahora no")) {
            shot("03_activar_huella")
            compose.onNodeWithText("Ahora no").performClick()
        }
        // --- Asistente inicial ---
        compose.waitUntil(5_000) { exists("Tu dinero en el banco") }
        shot("04_asistente_banco_vacio")
        // Si se salta el asistente, Inicio sigue dejando meter el dinero y volver al paso a paso.
        compose.onNodeWithText("Saltar todo").performClick()
        compose.waitUntil(5_000) { exists("¿Cuánto tienes ahora mismo?") }
        shot("04z_inicio_sin_datos")
        compose.onNodeWithText("Hacerlo paso a paso").performClick()
        compose.waitUntil(5_000) { exists("Tu dinero en el banco") }
        val bbvaBefore = 3240.55
        seed()
        compose.waitUntil(5_000) { exists("Santander") }
        closeCelebration()
        shot("04a_asistente_banco")
        compose.onNodeWithText("Siguiente").performClick()
        compose.waitUntil(5_000) { exists("Lo que tienes guardado fuera") }
        shot("04b_asistente_ahorro")
        compose.onNodeWithText("Siguiente").performClick()
        compose.onNodeWithText("Siguiente").performClick()
        compose.waitUntil(5_000) { exists("Lo que cobras") }
        shot("04c_asistente_ingresos")
        compose.onNodeWithText("Siguiente").performClick()
        compose.waitUntil(5_000) { exists("¿Cómo quieres repartirlo?") }
        shot("04d_asistente_reparto")
        compose.onNodeWithText("Siguiente").performClick()
        compose.waitUntil(5_000) { exists("¡Listo!") }
        shot("04e_asistente_listo")
        compose.onNodeWithText("¡A ahorrar!").performClick()

        // --- Al principio: objetivos y cuán ahorrador quieres ser ---
        compose.waitUntil(5_000) { exists("Tu forma de ahorrar") }
        compose.onNodeWithText("Viajar").performClick()
        compose.onNodeWithText("Ahorrador").performClick()
        shot("04f_perfil_ahorrador")
        compose.onNodeWithText("Fijar mis porcentajes").performScrollTo().performClick()
        compose.waitUntil(5_000) { !exists("Tu forma de ahorrar") }

        // --- Un pago con tarjeta simulado ---
        val (recorded, duplicate, bbvaAfter) = runBlocking {
            val payment = PaymentParser.parse("Mercadona", "23,45 € con Visa ••1234", isWallet = true)!!
            val first = PaymentRecorder.record(app, payment, "Google Wallet")
            // El banco avisa del mismo pago un momento después: no debe apuntarse dos veces.
            val second = PaymentRecorder.record(app, PaymentParser.parse("BBVA", "Compra en MERCADONA por 23,45 EUR con tu tarjeta", isWallet = false)!!, "BBVA")
            val bbva = FinanceDatabase.get(app).dao().accounts().first().first { it.name == "Santander" }.balance
            Triple(first, second, bbva)
        }
        assertNotNull("El pago con tarjeta se tiene que apuntar", recorded)
        assertNull("El aviso repetido del banco no se apunta otra vez", duplicate)
        assertEquals("Se clasifica solo", "SUPER", recorded!!.tag)
        assertEquals("El pago se descuenta de la cuenta elegida", bbvaBefore - 23.45, bbvaAfter, 0.001)

        // Una transferencia mueve el dinero entre cuentas sin cambiar el total.
        runBlocking {
            val dao = FinanceDatabase.get(app).dao()
            val before = dao.accounts().first()
            val cartera = before.first { it.name == "Cartera" }
            val hucha = before.first { it.name == "Hucha de casa" }
            dao.saveTransfer(com.caudal.finanzas.data.Transfer(fromAccountId = cartera.id, toAccountId = hucha.id, amount = 20.0, note = "A la hucha", epochDay = LocalDate.now().toEpochDay()))
            val after = dao.accounts().first()
            assertEquals(65.0, after.first { it.id == cartera.id }.balance, 0.001)
            assertEquals(660.0, after.first { it.id == hucha.id }.balance, 0.001)
            assertEquals(before.sumOf { it.balance }, after.sumOf { it.balance }, 0.001)
        }

        compose.waitUntil(5_000) { exists("PRESIÓN") }
        closeCelebration()
        shot("05_inicio")
        compose.onNodeWithText("LA SEMANA", substring = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitUntil(5_000) { exists("Ideal: ~") }
        shot("28b_panel_ficha_semana")
        compose.onNodeWithText("Entendido").performScrollTo().performClick()
        compose.waitUntil(5_000) { !exists("Ideal: ~") }
        compose.onNodeWithText("PRESIÓN", substring = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitUntil(5_000) { exists("1,0 lo de hoy") }
        shot("28c_panel_ficha_presion")
        compose.onNodeWithText("Entendido").performScrollTo().performClick()
        compose.waitUntil(5_000) { !exists("1,0 lo de hoy") }
        compose.waitUntil(5_000) { exists("PRESIÓN") }
        compose.onNodeWithContentDescription("Noticias").performClick()
        compose.waitUntil(5_000) { exists("Ethereum cae") }
        shot("30_noticias")
        compose.onNodeWithText("¿Cómo me\nafecta?").performClick()
        compose.waitUntil(5_000) { exists("¿Cómo puede afectarte?") }
        shot("30b_noticia_me_afecta")
        compose.onNodeWithText("Entendido").performScrollTo().performClick()
        compose.waitUntil(5_000) { !exists("¿Cómo puede afectarte?") }
        compose.onRoot().performTouchInput { swipeUp() }; compose.waitForIdle()
        compose.onRoot().performTouchInput { swipeUp() }; compose.waitForIdle()
        compose.onRoot().performTouchInput { swipeUp() }; compose.waitForIdle()
        compose.waitUntil(5_000) { exists("Te has quedado sin noticias") }
        shot("30c_noticias_fin")
        compose.onNodeWithContentDescription("Cerrar").performClick()
        compose.waitUntil(5_000) { exists("PRESIÓN") }
        scrollTo("Últimos movimientos")
        shot("05_movimientos")
        scrollTo("Tu reparto")
        shot("05a_reparto")
        scrollTo("Robinhood este mes")
        shot("05a2_reparto_robinhood")
        scrollTo("Puedes gastar")
        shot("05b_inicio_gastar")
        scrollTo("este mes para gastar")
        shot("05d_rueda")
        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp(startY = centerY + 300f, endY = centerY - 300f) }
        compose.waitForIdle()
        shot("05d2_rueda_lista")
        scrollTo("Consejos para llegar")
        shot("05e_objetivo")
        scrollTo("EDAD FINANCIERA")
        compose.onNodeWithText("EDAD FINANCIERA").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitUntil(5_000) { exists("RITMO DE MADURACIÓN") }
        shot("26_edad_financiera")
        scrollTo("QUÉ TE SUMA")
        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
        compose.waitForIdle()
        shot("26b_edad_factores")
        back()
        compose.waitUntil(5_000) { exists("PRESIÓN") }

        // --- ¿Me lo compro? ---
        scrollTo("¿Me lo compro?")
        compose.onNodeWithText("¿Me lo compro?").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitUntil(5_000) { exists("¿Cuánto cuesta?") }
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("180")
        compose.waitUntil(5_000) { exists("COMPRARLO AHORA") }
        shot("27_me_lo_compro")
        back()
        compose.waitUntil(5_000) { exists("PRESIÓN") }
        scrollTo("¿SABÍAS QUE")
        shot("05c_inicio_intereses")
        scrollTo("Gasto detectado")
        shot("05d_inicio_mes")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithContentDescription("Ocultar importes").performClick()
        shot("06_inicio_importes_ocultos")
        compose.onNodeWithContentDescription("Mostrar importes").performClick()

        // --- Tu futuro ---
        scrollTo("Tu futuro")
        compose.onNodeWithText("Tu futuro").performClick()
        compose.waitUntil(5_000) { exists("Lo que viene") }
        shot("06b_futuro")
        scrollTo("Total:")
        shot("06c_futuro_fijos")
        back()
        compose.waitUntil(5_000) { exists("PRESIÓN") }

        // --- Una cuenta por dentro + transferir ---
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Santander"))
        compose.waitForIdle()
        compose.onAllNodesWithText("Santander").onFirst().performClick()
        compose.waitUntil(5_000) { exists("Ajustar saldo") }
        shot("19_cuenta")
        compose.onNodeWithText("Transferir").performClick()
        compose.waitUntil(5_000) { exists("Hacia") }
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("150")
        shot("19b_transferir")
        compose.onNodeWithContentDescription("Cerrar").performClick()
        compose.waitUntil(5_000) { !exists("Hacia") }
        back()
        compose.waitUntil(5_000) { exists("PRESIÓN") }

        tab("Patrimonio")
        compose.waitUntil(5_000) { exists("PATRIMONIO NETO") }
        shot("07_patrimonio")
        compose.onAllNodesWithText("  ›", substring = true).onFirst().performClick()
        compose.waitUntil(5_000) { exists("Por qué ha cambiado") }
        shot("07b_patrimonio_por_que")
        compose.onNodeWithText("Entendido").performScrollTo().performClick()
        compose.waitUntil(5_000) { !exists("Por qué ha cambiado") }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
        shot("08_patrimonio_cuentas")

        // Cuenta remunerada: calculadora de intereses.
        compose.onNodeWithText("Robinhood").performClick()
        compose.waitUntil(5_000) { exists("Ajustar saldo") }
        shot("08a_cuenta_ing")
        compose.onNodeWithText("Editar").performClick()
        compose.waitUntil(5_000) { exists("SI VAS METIENDO DINERO") }
        compose.onNode(hasSetTextAction() and hasText("Cantidad fija al mes")).performTextInput("200")
        compose.onNode(hasSetTextAction() and hasText("Mínimo")).performTextInput("50")
        compose.onNode(hasSetTextAction() and hasText("Máximo")).performTextInput("300")
        compose.onNodeWithText("Entonces te daría", substring = true).performScrollTo()
        shot("08b_intereses")
        compose.onNodeWithContentDescription("Cerrar").performScrollTo().performClick()
        compose.waitUntil(5_000) { !exists("SI VAS METIENDO DINERO") }
        back()
        compose.waitUntil(5_000) { exists("PATRIMONIO NETO") }

        tab("Movimientos")
        compose.waitUntil(5_000) { exists("Ha entrado") }
        shot("09_presupuesto")
        scrollTo("Ver tu futuro")
        shot("09a_presupuesto_fijos")
        scrollTo("¿Cuánto quieres ahorrar?")
        shot("09b_presupuesto_reparto")

        // --- En qué se ha ido y análisis ---
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        scrollTo("¿En qué se ha ido?")
        shot("09c_gastos_en_que")
        scrollTo("Cuentas con amigos")
        shot("09e_amigos")
        scrollTo("Análisis completo")
        shot("09d_gastos_donde")
        compose.onNodeWithText("Análisis completo").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitUntil(5_000) { exists("Tus números, explicados") }
        closeCelebration()
        shot("23_analisis_salud")
        scrollTo("Dinero recuperable"); shot("23a_recuperable")
        scrollTo("Gastos recurrentes"); shot("23a1_recurrentes")
        scrollTo("Tus recibos, semana a semana"); shot("23a3_recibos")
        scrollTo("Reparte tu nómina"); shot("23a4_nomina")
        scrollTo("Comparado contigo mismo"); shot("23a2_anomalias")
        scrollTo("TU COSTE DE VIDA REAL"); shot("23b_analisis_coste")
        scrollTo("TUS HÁBITOS DE GASTO"); shot("23c_analisis_habitos")
        scrollTo("MÁQUINA DEL TIEMPO"); shot("23d_analisis_maquina")
        scrollTo("GASTOS QUE PUEDES COMPARTIR"); shot("23e_analisis_compartir")
        scrollTo("¿Y SI…?"); shot("23f_analisis_ysi")
        scrollTo("Simulador de decisiones")
        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onNodeWithText("Simulador de decisiones").performClick()
        compose.waitUntil(5_000) { exists("¿Qué pasaría si…?") }
        shot("24_simulador")
        scrollTo("Compartir vivienda"); shot("24b_simulador_opciones")
        back()
        compose.waitUntil(5_000) { exists("Ha entrado") }


        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithContentDescription("Añadir").performClick()
        compose.waitUntil(5_000) { exists("¿Qué quieres añadir?") }
        shot("10_anadir")
        compose.onNodeWithText("Ingreso").performClick()
        compose.waitUntil(5_000) { exists("Nuevo ingreso") }
        shot("10b_nuevo_ingreso")
        compose.onNodeWithContentDescription("Cerrar").performClick()
        compose.waitUntil(5_000) { !exists("Nuevo ingreso") }
        compose.onNodeWithContentDescription("Añadir").performClick()
        compose.waitUntil(5_000) { exists("¿Qué quieres añadir?") }
        compose.onNodeWithText("Gasto").performClick()
        compose.waitUntil(5_000) { exists("Nuevo gasto") }
        shot("11_nuevo_gasto")
        compose.onNodeWithContentDescription("Cerrar").performClick()
        compose.waitUntil(5_000) { !exists("Nuevo gasto") }

        tab("Inversión")
        compose.waitUntil(5_000) { exists("Bitcoin") }
        compose.waitUntil(5_000) { exists("Favoritas") }
        shot("12_cripto")
        compose.onNodeWithText("¿De qué se está hablando?").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitUntil(5_000) { exists("MÁS RUIDO POSITIVO") }
        shot("12f_de_que_se_habla")
        compose.onNodeWithText("Ver gráfico, por qué se habla", substring = true).performScrollTo().performClick()
        compose.waitUntil(5_000) { exists("Precio de") }
        shot("12h_buzz_ficha")
        scrollTo("Por qué se habla de ella"); shot("12i_buzz_por_que")
        scrollTo("Dónde se puede comprar"); compose.waitUntil(5_000) { exists("Binance") }; shot("12j_buzz_donde")
        back()
        compose.waitUntil(5_000) { exists("Favoritas") }
        scrollTo("Activos")
        shot("12a_cripto_activos")
        scrollTo("Las más populares")
        shot("12g_cripto_mercado")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        scrollTo("Activos")
        compose.onAllNodesWithText("Ethereum").onFirst().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitUntil(5_000) { exists("Precio de Ethereum") }
        shot("12c_ficha_linea")
        compose.onNodeWithText("Velas").performClick()
        compose.waitForIdle()
        shot("12d_ficha_velas")
        scrollTo("Estadísticas")
        shot("12e_ficha_estadisticas")
        back()
        compose.waitUntil(5_000) { exists("Favoritas") }
        compose.onAllNodesWithText("Bitcoin").onFirst().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitUntil(5_000) { exists("Precio de Bitcoin") }
        compose.onNodeWithContentDescription("Editar Bitcoin").performClick()
        compose.waitUntil(5_000) { exists("¿Cuánto dinero tienes en Bitcoin?") }
        shot("12b_cripto_euros")
        compose.onNodeWithContentDescription("Cerrar").performClick()
        compose.waitUntil(5_000) { !exists("¿Cuánto dinero tienes en Bitcoin?") }
        back()
        compose.waitUntil(5_000) { exists("Favoritas") }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onAllNodesWithText("Acciones").onFirst().performClick()
        compose.waitUntil(5_000) { exists("AAPL") }
        shot("13_acciones")
        scrollTo("Las más populares")
        shot("13b_acciones_mercado")
        compose.onNodeWithText("Netflix").performClick()
        compose.waitUntil(5_000) { exists("No la tienes") }
        shot("13c_accion_sin_tener")
        back()
        compose.waitUntil(5_000) { !exists("No la tienes") }

        tab("Perfil")
        compose.waitUntil(5_000) { exists("TU NIVEL") }
        shot("17_perfil")
        compose.onNodeWithText("TU WRAPPED").performClick()
        compose.waitUntil(5_000) { exists("Hola,") }
        shot("29_wrapped_1")
        var page = 2
        while (!exists("TU FICHA DEL MES") && page < 12) {
            compose.onRoot().performTouchInput { swipeLeft() }
            compose.waitForIdle()
            shot("29_wrapped_$page")
            page++
        }
        compose.waitUntil(5_000) { exists("TU FICHA DEL MES") }
        shot("29z_wrapped_ficha")
        compose.onNodeWithContentDescription("Cerrar").performClick()
        compose.waitUntil(5_000) { exists("TU NIVEL") }
        scrollTo("Amigos")
        shot("17b_perfil_logros")

        // --- Amigos ---
        scrollTo("Crear cuenta o entrar")
        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onNodeWithText("Crear cuenta o entrar").performClick()
        compose.waitUntil(5_000) { exists("Tu cuenta de Ahorra, vaca") }
        compose.onNode(hasSetTextAction() and hasText("Tu email")).performTextInput("alex@correo.com")
        shot("18_cuenta")
        compose.onNodeWithText("Enviarme el código").performClick()
        compose.waitUntil(5_000) { exists("Mira tu correo") }
        compose.onNode(hasSetTextAction() and hasText("Código")).performTextInput("123456")
        compose.onNodeWithText("Entrar").performClick()
        compose.waitUntil(5_000) { exists("Buscar amigos") }
        closeCelebration()
        scrollTo("TUS AMIGOS")
        // Hasta el final, para que nada quede debajo de la barra flotante.
        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
        compose.waitForIdle()
        shot("18a_amigos")
        compose.onNodeWithText("Aceptar").performClick()
        compose.waitUntil(5_000) { exists("TUS AMIGOS (2)") }

        scrollTo("Buscar amigos")
        compose.onNodeWithText("Buscar amigos").performClick()
        compose.onNode(hasSetTextAction() and hasText("@ de tu amigo")).performTextInput("lau")
        compose.waitUntil(5_000) { exists("@laura.ahorra") }
        shot("18b_buscar")
        compose.onNodeWithContentDescription("Añadir a laura.ahorra").performClick()
        compose.waitUntil(5_000) { exists("Enviada") }
        compose.onNodeWithContentDescription("Cerrar").performClick()
        compose.waitUntil(5_000) { !exists("@ de tu amigo") }

        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onNodeWithText("Ana").performClick()
        compose.waitUntil(5_000) { exists("Racha de 3") }
        shot("18c_amigo")
        compose.onNodeWithContentDescription("Cerrar").performClick()
        compose.waitUntil(5_000) { !exists("SUS LOGROS") }

        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Verificado").fetchSemanticsNodes().isNotEmpty() }
        shot("18d_perfil_verificado")

        compose.onNodeWithContentDescription("Ajustes").performClick()
        compose.waitUntil(5_000) { exists("Pagos con tarjeta") }
        shot("14_ajustes")

        // --- Bloqueo (desde Ajustes) ---
        scrollTo("Bloquear ahora")
        compose.onNodeWithText("Bloquear ahora").performClick()
        compose.waitUntil(5_000) { exists("Hola, Alex") }
        shot("15_bloqueo")
        typePin("000000"); compose.waitForIdle()
        shot("16_pin_incorrecto")
        assertTrue(exists("PIN incorrecto. Te quedan 4 intentos."))
        typePin("123456")
        compose.waitUntil(5_000) { exists("PRESIÓN") }

        // --- Modo oscuro ---
        org.robolectric.RuntimeEnvironment.setQualifiers("+night")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(5_000) { exists("PRESIÓN") }
        closeCelebration()
        shot("20_oscuro_inicio")
        scrollTo("Puedes gastar")
        shot("20b_oscuro_gastar")
        tab("Patrimonio")
        shot("21_oscuro_patrimonio")
        tab("Perfil")
        compose.waitUntil(5_000) { exists("TU NIVEL") }
        shot("22_oscuro_perfil")
    }

    private fun seed() = runBlocking {
        val prefs = Prefs.get(app)
        val dao = FinanceDatabase.get(app).dao()
        listOf(
            Account(name = "Santander", type = AccountType.BANK, balance = 3240.55),
            Account(name = "Robinhood", type = AccountType.BANK_SAVINGS, balance = 8500.0, interestRate = 5.0, autoInterest = true),
            Account(name = "Hucha de casa", type = AccountType.HOME_SAVINGS, balance = 640.0),
            Account(name = "Cartera", type = AccountType.CASH, balance = 85.0),
            Account(name = "PayPal", type = AccountType.WALLET, balance = 212.4),
            Account(name = "Plan de pensiones", type = AccountType.FUND, balance = 4300.0, note = "Indexa"),
        ).forEach { dao.upsertAccount(it) }
        val bbvaId = dao.accounts().first().first { it.name == "Santander" }.id
        prefs.setCardPayments(true)
        prefs.setCardAccount(bbvaId)
        prefs.setHandle("alex.ahorra")

        // Gastos fijos: el alquiler ya está pagado este mes (hay un gasto "Alquiler" de 650 €).
        val now0 = LocalDate.now()
        dao.upsertBill(com.caudal.finanzas.data.RecurringBill(name = "Alquiler", amount = 650.0, day = 28, categoryId = 1, accountId = bbvaId))
        dao.upsertBill(com.caudal.finanzas.data.RecurringBill(name = "Netflix", amount = 13.99, day = 15, categoryId = 2, accountId = bbvaId))
        dao.upsertBill(com.caudal.finanzas.data.RecurringBill(name = "Gimnasio", amount = 39.9, day = 20, categoryId = 2, accountId = bbvaId))
        dao.upsertBill(com.caudal.finanzas.data.RecurringBill(name = "Luz", amount = 68.0, day = 25, categoryId = 1, accountId = bbvaId))
        dao.upsertBill(com.caudal.finanzas.data.RecurringBill(name = "Seguro del coche", amount = 420.0, day = 10, yearly = true, month = now0.plusMonths(1).monthValue, categoryId = 1, accountId = bbvaId))
        // Mes pasado, para "¿Dónde se ha ido tu dinero?".
        val last = java.time.YearMonth.now().minusMonths(1)
        dao.upsertExpense(Expense(categoryId = 1, amount = 650.0, note = "Alquiler", epochDay = last.atDay(3).toEpochDay()))
        dao.upsertExpense(Expense(categoryId = 2, amount = 25.0, note = "Cine", epochDay = last.atDay(2).toEpochDay()))
        dao.upsertIncome(Income(sourceId = null, amount = 1650.0, note = "Nómina", epochDay = last.atDay(2).toEpochDay(), accountId = null))

        // Ingresos: nómina fija del 1 al 5, autónomo variable y YouTube el 22.
        dao.upsertIncomeSource(IncomeSource(name = "Nómina", variable = false, amount = 1650.0, dayFrom = 1, dayTo = 5, accountId = bbvaId))
        dao.upsertIncomeSource(IncomeSource(name = "Autónomo", variable = true, amount = 400.0, dayFrom = 10, dayTo = 15, accountId = bbvaId))
        dao.upsertIncomeSource(IncomeSource(name = "Resto de nómina", variable = false, amount = 250.0, dayFrom = 20, dayTo = 25, accountId = bbvaId))
        dao.upsertIncomeSource(IncomeSource(name = "YouTube", variable = true, amount = 250.0, dayFrom = 22, dayTo = 22, accountId = bbvaId))
        val ids = dao.incomeSources().first().associate { it.name to it.id }
        val thisMonth = java.time.YearMonth.now()
        // Historial (sin tocar saldos): la media de YouTube sale 346,67 € y la del autónomo 400 €.
        listOf(280.0, 410.0, 350.0).forEachIndexed { i, a ->
            dao.upsertIncome(Income(sourceId = ids["YouTube"], amount = a, note = "YouTube", epochDay = thisMonth.minusMonths(i + 1L).atDay(22).toEpochDay(), accountId = null))
        }
        listOf(500.0, 300.0).forEachIndexed { i, a ->
            dao.upsertIncome(Income(sourceId = ids["Autónomo"], amount = a, note = "Autónomo", epochDay = thisMonth.minusMonths(i + 1L).atDay(12).toEpochDay(), accountId = null))
        }
        dao.upsertIncome(Income(sourceId = ids["Nómina"], amount = 1650.0, note = "Nómina", epochDay = thisMonth.atDay(minOf(2, LocalDate.now().dayOfMonth)).toEpochDay(), accountId = null))

        val now = System.currentTimeMillis()
        dao.upsertCrypto(CryptoHolding(coinId = "bitcoin", symbol = "BTC", name = "Bitcoin", amount = 0.0425, avgBuyPrice = 52000.0, price = 61850.0, updatedAt = now))
        dao.upsertCrypto(CryptoHolding(coinId = "ethereum", symbol = "ETH", name = "Ethereum", amount = 0.8, avgBuyPrice = 2900.0, price = 2650.0, updatedAt = now))
        dao.upsertCrypto(CryptoHolding(coinId = "solana", symbol = "SOL", name = "Solana", amount = 12.0, avgBuyPrice = 120.0, price = 168.0, updatedAt = now))
        dao.upsertStock(StockHolding(ticker = "AAPL", name = "Apple", shares = 10.0, avgBuyPrice = 165.0, price = 214.3, updatedAt = now))
        dao.upsertStock(StockHolding(ticker = "SAN.MC", name = "Banco Santander", shares = 300.0, avgBuyPrice = 4.1, price = 7.85, updatedAt = now))
        dao.upsertStock(StockHolding(ticker = "VWCE.DE", name = "Vanguard FTSE All-World", shares = 25.0, avgBuyPrice = 105.0, price = 132.4, updatedAt = now))

        // Historia de patrimonio de 200 días para el gráfico.
        val today = LocalDate.now().toEpochDay()
        for (d in 200 downTo 1) {
            val t = 200 - d
            dao.upsertSnapshot(NetWorthSnapshot(today - d, 24000.0 + t * 35 + sin(t / 9.0) * 600))
        }

        fun day(back: Int) = LocalDate.now().let { it.minusDays(back.toLong().coerceAtMost(it.dayOfMonth - 1L)).toEpochDay() }
        listOf(
            Expense(categoryId = 1, amount = 62.15, note = "Luz", epochDay = day(2), createdAt = now - 200_000_000),
            Expense(categoryId = 2, amount = 13.99, note = "Netflix", epochDay = day(2), createdAt = now - 190_000_000),
            Expense(categoryId = 2, amount = 89.95, note = "Zapatillas", epochDay = day(1), createdAt = now - 90_000_000),
            Expense(categoryId = 2, amount = 48.0, note = "Cena con amigos", epochDay = day(0), createdAt = now - 3_600_000),
            Expense(categoryId = 3, amount = 300.0, note = "Traspaso a ahorro", epochDay = day(3), createdAt = now - 310_000_000),
        ).forEach { dao.upsertExpense(it) }

        // Dos meses más de historia con comercios reales, para la clasificación y los análisis.
        val merchants = listOf(
            "Mercadona" to 64.3, "Glovo" to 23.9, "Renfe" to 18.6, "Zara" to 45.95, "Bar Manolo" to 14.5, "Repsol" to 52.0,
            "Cinesa" to 19.8, "Farmacia" to 12.4, "Lidl" to 38.7, "Amazon" to 29.99, "Restaurante Goiko" to 41.0, "Mercadona" to 71.2,
        )
        for (back in 1..2) {
            val m = java.time.YearMonth.now().minusMonths(back.toLong())
            merchants.forEachIndexed { i, (n, a) ->
                val dd = ((i * 5 + back * 2) % m.lengthOfMonth()) + 1
                val need = n in listOf("Mercadona", "Lidl", "Renfe", "Repsol", "Farmacia")
                dao.upsertExpense(Expense(categoryId = if (need) 1L else 2L, amount = a * (if (back == 1) 1.2 else 1.0), note = n, epochDay = m.atDay(dd).toEpochDay()))
            }
            if (back == 2) {
                dao.upsertExpense(Expense(categoryId = 1, amount = 650.0, note = "Alquiler", epochDay = m.atDay(3).toEpochDay()))
                dao.upsertIncome(Income(sourceId = null, amount = 1650.0, note = "Nómina", epochDay = m.atDay(2).toEpochDay(), accountId = null))
            }
        }
        dao.upsertExpense(Expense(categoryId = 2, amount = 136.0, note = "Ryanair", epochDay = java.time.YearMonth.now().minusMonths(1).atDay(18).toEpochDay()))
        // Lo pagaste hace casi un año: el radar lo avisa.
        dao.upsertExpense(Expense(categoryId = 1, amount = 186.0, note = "Seguro hogar Mapfre", epochDay = LocalDate.now().minusDays(340).toEpochDay()))
        // La cena se repartió entre 3 y uno ya ha pagado.
        val dinner = dao.allExpenses().first().first { it.note == "Cena con amigos" }
        dao.upsertSplit(com.caudal.finanzas.data.Split(dinner.id, 3, 1, "Laura y Marcos"))
        prefs.setGoals(
            com.caudal.finanzas.domain.GoalList.format(
                listOf(
                    com.caudal.finanzas.domain.Goal("Colchón para el piso", 40000.0, LocalDate.now(), LocalDate.now().plusYears(5)),
                    com.caudal.finanzas.domain.Goal("Viaje a Japón", 3500.0, LocalDate.now(), LocalDate.now().plusYears(1)),
                ),
            ),
        )
        // Fugas y anomalías: comisión, cafés repetidos, Spotify que sube y un cargo duplicado.
        for (back in 1..3) {
            val m = java.time.YearMonth.now().minusMonths(back.toLong())
            dao.upsertExpense(Expense(categoryId = 1, amount = 4.5, note = "Comisión mantenimiento", epochDay = m.atDay(1).toEpochDay()))
            dao.upsertExpense(Expense(categoryId = 2, amount = 10.99, note = "Spotify", epochDay = m.atDay(8).toEpochDay()))
            (0 until 5).forEach { k -> dao.upsertExpense(Expense(categoryId = 2, amount = 3.6, note = "Starbucks", epochDay = m.atDay(4 + k * 5).toEpochDay())) }
        }
        dao.upsertExpense(Expense(categoryId = 2, amount = 12.99, note = "Spotify", epochDay = day(1)))
        dao.upsertExpense(Expense(categoryId = 2, amount = 29.99, note = "Amazon", epochDay = day(1)))
        dao.upsertExpense(Expense(categoryId = 2, amount = 29.99, note = "Amazon", epochDay = day(0)))
        listOf(0, 1, 1).forEach { b -> dao.upsertExpense(Expense(categoryId = 2, amount = 2.2, note = "Monster Energy", epochDay = day(b), accountId = bbvaId)) }
        prefs.setBirthYear(LocalDate.now().year - 29)
        prefs.setDayChanges(mapOf("ETH" to -4.2, "BTC" to 1.3, "SOL" to 3.1, "AAPL" to 0.8, "SAN.MC" to -0.6))
        prefs.setBankPlan(bbvaId, dao.accounts().first().first { it.name == "Robinhood" }.id, 25.0)
    }
}
