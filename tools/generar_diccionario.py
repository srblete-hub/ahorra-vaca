"""Convierte el diccionario de comercios (JSON) en un fichero Kotlin, quitando palabras genéricas y ambiguas."""
import io, json, re, sys, unicodedata
from collections import defaultdict

src, out = sys.argv[1], sys.argv[2]
data = json.load(io.open(src, encoding="utf-8"))

def norm(t):
    t = unicodedata.normalize("NFD", t.lower())
    t = "".join(c for c in t if unicodedata.category(c) != "Mn")
    return re.sub(r"[^a-z0-9]+", " ", t).strip()

TAG = {
    "Supermercado (necesidad)": "SUPER", "Restaurantes y bares": "FOOD", "Transporte (necesidad)": "TRANSPORT",
    "Casa y facturas (necesidad)": "HOME", "Ropa": "CLOTHES", "Compras": "SHOPPING", "Salud y deporte (necesidad)": "HEALTH",
    "Ocio": "LEISURE", "Viajes": "TRAVEL", "Deporte": "SPORT", "Regalos": "GIFTS", "Formación (necesidad)": "EDUCATION",
    "Mascotas (necesidad)": "PETS",
}
# De las categorías que la app no tiene, solo frases claras y a su sitio.
EXTRA_PHRASES = {
    "TRANSPORT": ["seguro coche", "seguro moto", "asistencia carretera"],
    "HEALTH": ["seguro salud", "seguro dental", "seguro medico"],
    "HOME": ["seguro hogar", "seguro alquiler", "seguro impago", "seguro comunidad"],
    "PETS": ["seguro mascota", "seguro mascotas"],
    "TRAVEL": ["seguro viaje", "seguro de viaje", "seguro cancelacion"],
    "EDUCATION": ["guarderia infantil", "escuela infantil", "comedor escolar", "material escolar", "libros escolares", "actividades extraescolares", "campamento urbano", "campamento verano"],
}

# Palabras sueltas que no dicen nada por sí solas (o salen de partir nombres compuestos).
GENERIC = set("""
online store shop shops market markets center centre centro tienda tiendas servicio servicios service services express home house club city plaza central
food daily real group grupo company compania empresa empresas sociedad spain espana espanola europa europe international internacional general nacional
madrid barcelona valencia sevilla bilbao malaga palma murcia alicante zaragoza toledo cartagena usera asturiana andaluza levante norte north
pepe juan pablo carmen dani tomas marcial guedes lucio ramon isabel manolo carlos maria santa paul jack jones michael karl roberto ben
super maxi mini plus pro smart fresh fresc fresco frescos bio eco ecologico ecologica ecologicos gourmet premium outlet direct directa directo
compra compras pago pagos cargo abono recibo factura cuota tasa tarifa gastos gasto coste pedido pedidos reparto reserva reservas
mesa casa hogar familia family amigos todo todos cien cinco five four seven dias dia diario diaria semanal mensual anual horas
nuevo nueva new good best first quick fast low cost daily line mall shopping retail electro mobile movil phone online-shop
box park plaza world mundo universal social local barrio municipal publico publica privado privada personal general
sala salon area zona punto parque arena station estacion terminal planta planeta star stars sol luna mar
deporte deportes sports sport running training football futbol music musica arte cultural cultura ocio fiesta fiestas
store tienda vintage moda fashion style estilo boutique accesorios complementos productos producto material materiales
alimentos alimentacion alimentaria bebidas comida comidas menu buffet libre take away delivery domicilio llevar
seguro seguros salud medico medica clinica centro hospital universitario
kids man woman hombre mujer infantil escolar escolares joven jovenes urbana urbano interurbano regional
the and del las los una uno con para por sin sobre entre desde hasta
""".split())

# Revisadas a mano: cortas o con otro sentido (Mastercard, Caja Rural, Costa Coffee, nombres de persona…).
REVIEW_DROP = set("""
about banana cole golden island lounge mars only pedro river secret sons vero polo hierro locker armour axel lauren
acceso book clase grado master prueba street wall unir
artist blues feliz granja griego hindu indias irish jazz junior just manger miss muerde nero news nomad romana shack valor vasco wine johns mels jerrys carls turco alsea rapida
caja card cards lista papel party helio
david jean louis marco rural mental
bill energy factor fijo bienes monde
alley bits cool dice epic fest isla live loro magica medusa mitica office port rock romano sound terra theme tonic venue vina visita golem soleil
hotel aves collar
back depot expert foto manga normal planet poly prive privee tinta vente wish drim
artes arts duet fees green holmes indoor liga nevada pool sierra
amara arama diplo gust hiper hyper indio jamon latino lupa piedra polaco proxim rumano ruso suma unic urban
aceite azul cambio carga garage public radio road share supra union vert zurich
agency coche costa duty france guest minor moto rental royal united
transfer transferencia bizum tarjeta pago cargo recibo compra ingreso abono devolucion reembolso
""".split())
GENERIC |= REVIEW_DROP

per_cat = defaultdict(set)
token_cats = defaultdict(set)
for cat, words in data["categories"].items():
    tag = TAG.get(cat)
    if not tag: continue
    phrases = [norm(w) for w in words if " " in norm(w)]
    phrase_tokens = {t for p in phrases for t in p.split()}
    for w in words:
        n = norm(w)
        if not n: continue
        if " " in n:
            per_cat[tag].add(n)
            continue
        if len(n) < 4 or n.isdigit() or n in GENERIC: continue
        # Trozo de una frase de la misma lista ("king" de "burger king"): solo si es largo (una marca o palabra clara).
        if n in phrase_tokens and len(n) < 6: continue
        per_cat[tag].add(n)
for tag, ph in EXTRA_PHRASES.items():
    per_cat[tag].update(norm(p) for p in ph)
for tag, ws in per_cat.items():
    for w in ws: token_cats[w].add(tag)
# Lo que sale en dos categorías a la vez es ambiguo: fuera.
ambiguous = {w for w, cs in token_cats.items() if len(cs) > 1}
for tag in per_cat: per_cat[tag] -= ambiguous

frags = defaultdict(set)
for cat, fs in data.get("fragments", {}).items():
    tag = TAG.get(cat)
    if tag:
        for f in fs:
            n = norm(f)
            if len(n) >= 4 and " " not in n: frags[tag].add(n)

def kt_list(ws):
    ws = sorted(ws)
    return "".join("            " + ", ".join('"%s"' % w for w in ws[i:i + 8]) + ",\n" for i in range(0, len(ws), 8))

body = ["package com.caudal.finanzas.domain", "",
        "/**",
        " * Diccionario amplio de comercios y palabras por tipo de gasto (España), importado de",
        " * diccionario_comercios_finanzas.json y filtrado: sin palabras genéricas ni las que salen en dos tipos a la vez.",
        " * Se suma a las palabras de [SpendTag]. Generado: no editar a mano.",
        " */",
        "object MerchantDictionary {",
        "    val WORDS: Map<SpendTag, List<String>> = mapOf("]
for tag in sorted(per_cat):
    body.append("        SpendTag.%s to listOf(\n%s        )," % (tag, kt_list(per_cat[tag])))
body.append("    )")
body.append("")
body.append("    /** Trozos de palabra (\"cinem\", \"hamburg\"…) para nombres pegados. */")
body.append("    val FRAGMENTS: List<Pair<String, SpendTag>> = listOf(")
for tag in sorted(frags):
    for f in sorted(frags[tag]):
        body.append('        "%s" to SpendTag.%s,' % (f, tag))
body.append("    )")
body.append("}")
io.open(out, "w", encoding="utf-8", newline="\n").write("\n".join(body) + "\n")
print("palabras:", {t: len(v) for t, v in sorted(per_cat.items())}, "ambiguas quitadas:", len(ambiguous), "trozos:", sum(len(v) for v in frags.values()))
print("ambiguas:", sorted(ambiguous)[:60])
