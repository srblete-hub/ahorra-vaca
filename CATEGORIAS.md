# Categorías y reglas de detección

Así decide la app el tipo de cada gasto, sin IA y en este orden:

1. **Lo que tú ya elegiste:** si alguna vez cambiaste el tipo de un comercio, se usa siempre ese.
2. **Palabras y marcas** de la lista de abajo (como palabra entera; si hay varias, gana la frase más larga: "uber eats" va antes que "uber").
3. **Trozos dentro de una palabra** (para nombres pegados como "Neocine" o "BurgerLab").
4. Si nada encaja: **Otros**.

Se generó a partir de `domain/Spend.kt`: si añades marcas allí, vuelve a generarlo.

## Supermercado (necesidad)

ahorramas, alcampo, aldi, aldi supermercados, alimentacion, alimerka, badulaque, bm supermercados, bm urban, bon area, bonarea, bonpreu, caprabo, carniceria, carrefour, carrefour express, carrefour market, cash fresh, charcuteria, claudio, colmado, compra semanal, condis, consum, consum cooperativa, costco, coviran, covirán, dia, dia market, e leclerc, el jamon, eroski, eroski center, family cash, froiz, frutas, fruteria, gadis, getir, glovo super, gorillas, hiperber, hipercor, hiperdino, hipermercado, horno, la compra, la sirena, leclerc, lidl, lidl supermercados, makro, mas y mas, maskom, masymas, mercado, mercadona, mercadona sa, panaderia, pasteleria, pescaderia, plusfresc, polleria, simply, spar, super, supercor, supercor exprés, supermercado, supersol, tu super, ultramarinos, uvesco, vegalsa, verduleria

## Restaurantes y bares

100 montaditos, 365 obrador, almuerzo, arroceria, asador, bar, bar , bocateria, bodega, brasa, brunch, burger, burger king, burrito, cafe, cafe bar, cafeteria, café, carls jr, cena, cerveceria, cervecerias, cervezas, chiringuito, chocolateria, churreria, churros, cien montaditos, comida, copas, costa coffee, creperia, deliveroo, desayuno, domino s, domino s pizza, dominos, dominos pizza, durum, five guys, foodhall, foster, foster s hollywood, fosters, ginos, glovo, gofres, goiko, goiko grill, granier, granja, hamburgueseria, heladeria, honest greens, just eat, kebab, kfc, la mafia, la sureña, la tagliatella, lizarran, llaollao, manolo bakes, marisqueria, mcdonald, mcdonalds, meson, montaditos, panaderia cafeteria, panaria, pans, pans company, papa johns, pasteleria, pizza, pizza hut, pizzeria, poke, popeyes, ramen, restaurante, rodilla, sibuya, smoy, starbucks, subway, sushi, taberna, taco bell, tacos, tagliatella, tapas, taqueria, tasca, telepizza, terraza, tgb, tgi fridays, tim hortons, tommy mel s, uber eats, udon, vermuteria, vicio, vips, vips club, vips smart, wok, yogurteria

## Transporte (necesidad)

abertis, abono, abono transporte, acciona, alsa, aparcamiento, autobus, autopista, avanza, ballenoil, bicimad, bicing, bizkaibus, blablacar, bolt, bonarea energia, bonarea gasolinera, bp, bus, cabify, carburante, cargador, cepsa, cercanias, combustible, cooltra, dgt, diesel, elparking, empark, emt, esso, estacion de servicio, feu vert, flixbus, free now, freenow, galp, gasoil, gasolina, gasolinera, iryo, itv, lavacoches, lavado, lime, meroil, metro, metro de madrid, midas, moeve, monbus, multa trafico, neumaticos, norauto, ora, ouigo, parclick, parking, parquimetro, peaje, petronor, petroprix, plenoil, q8, radio taxi, renfe, repsol, rodalies, saba, seguro coche, seguro del coche, ser madrid, share now, shell, taller, tamoil, tarjeta transporte, taxi, telpark, tmb, trainline, tussam, uber, via t, wible, yego, zity, zona azul

## Casa y facturas (necesidad)

adamo, administrador de fincas, agua, aguas de, aigues de barcelona, aldro, alquiler, avatel, basura, bauhaus, brico, bricodepot, bricomart, canal de isabel ii, comunidad, comunidad propietarios, conforama, digi, el corte ingles hogar, emasesa, endesa, factor energia, fibra, finetwork, gas, h m home, hipoteca, holaluz, iberdrola, ibi, ikea, internet, jazztel, jysk, kave home, leroy merlin, limpieza, linea directa hogar, lowi, lucera, luz, maisons du monde, mapfre hogar, masmovil, movil, movistar, naturgy, o2, octopus energy, orange, pepephone, prosegur, repsol electricidad, repsol luz, securitas direct, seguro hogar, simyo, sklum, som energia, telefono, totalenergies, vodafone, yoigo, zara home

## Suscripciones

1password, adobe, amazon prime, anthropic, apple music, apple one, atresplayer, audible, bumble, canva, chatgpt, claude, crunchyroll, dazn, deezer, disney, dropbox, duolingo, expressvpn, filmin, github, google one, hbo, hbo max, icloud, kindle unlimited, max, microsoft 365, midjourney, movistar plus, netflix, nintendo, nordvpn, notion, office 365, openai, patreon, playstation, prime video, ps plus, skyshowtime, spotify, storytel, strava, suscripcion, tidal, tinder, twitch, twitter, x corp, x premium, xbox, youtube premium

## Ropa

about you, abrigo, adidas, and other stories, arket, asics, asos, axel arigato, bape, bañador, bdk, bershka, bimba lola, bimba y lola, blue banana, bluebanana, bolso, botas, calcetines, calvin klein, calzedonia, camisa, camiseta, camisetas, carhartt, chaqueta, cinturon, clarks, cole buxton, converse, cortefiel, corteiz, courir, desigual, dr martens, ecoalf, el ganso, ellesse, eme studio, eme studios, essentials, falda, fear of god, foot locker, golden goose, gorra, guess, h m, hackett, hoff, hugo boss, intimissimi, jd sports, jersey, kiabi, kith, lacoste, lefties, levi s, levis, lounge underwear, mango, massimo dutti, misako, monki, new balance, nike, nude project, nudeproject, oysho, palace, pantalon, pantalones, parfois, pdpaola, pedro del hierro, pepe jeans, polo club, primark, pull and bear, pull bear, pull&bear, puma, ralph lauren, reebok, represent, ropa, scalpers, scuffers, sepiia, sfera, shein, sidiver, silbon, singularu, skechers, snipes, springfield, stradivarius, stussy, sudadera, supreme, textil, tezenis, timberland, tommy hilfiger, tous, trapstar, two jeys, twojeys, uniqlo, uterque, vans, vaqueros, veja, vestido, victoria s secret, vinted, weekday, women secret, zalando, zapateria, zapatillas, zapatos, zara

## Compras

action, aliexpress, amazon, apple com, apple store, auriculares, carrefour online, compras, consola, corte ingles, decathlon, druni, el corte, el corte ingles, fnac, game, media markt, mediamarkt, miravia, mochila, movil, normal, pccomponentes, portatil, primor, sephora, temu, tienda, tiger, wallapop, worten

## Salud y deporte (necesidad)

adeslas, afflelou, alain afflelou, analisis, asisa, atida, barber, barberia, barbershop, botica, cigna, clinica, clinica dental, dentista, dentix, depilacion, dietista, dkv, dosfarma, estetica, farmacia, farmacias, farmaciasdirect, federopticos, fiatc, fisio, fisioterapeuta, fisioterapia, general optica, hm hospitales, hospital, jean louis david, laboratorio, lentillas, llongueras, logopeda, manicura, mapfre salud, marco aldany, masaje, medico, mifarma, multiopticas, nails, nutricionista, oculista, optica, opticalia, osteopata, parafarmacia, peluqueria, podologia, podologo, primor, promofarma, psicologia, psicologo, quiron, quironsalud, ribera, sanitas, sanitas dental, seguro medico, spa, terapia, visionlab, vitaldent, vithas

## Ocio

acuario, apuestas, aqualand, aquopolis, arenal sound, artesiete, atrapalo, bbk live, bet365, betfair, billar, bioparc, bolera, botellon, bowling, casino, chupito, cine, cinebox, cines, cines la dehesa, cines verdi, cinesa, cocktail, coctel, codere, comedia, concierto, conciertos, copas, cubata, dardos, dice, dice fm, discoteca, el corte ingles entradas, entrada, entradas, entradas com, epic games, escape, escape room, eventbrite, exposicion, faunia, festival, fever, fiesta, filmoteca, fnac espectaculos, game, game stop, giglon, gin tonic, golem, isla magica, juego, juegos, karaoke, karting, kinepolis, laser, laser tag, loro parque, loteria, loterias, mad cool, medusa, mk2, monologo, multicines, museo, museo del prado, musical, neocine, neocines, nintendo eshop, ocine, odeon, opera, paintball, parque, parque acuatico, parque de atracciones, parque warner, playstation store, pokerstars, port aventura, portaventura, primavera sound, pub, recreativos, reina sofia, renoir, resurrection fest, salon de juegos, sonar, songkick, sportium, steam, taquilla, taquilla com, teatro, terra mitica, thyssen, ticketea, ticketmaster, twitch bits, videojuego, videojuegos, viña rock, warner, wegow, xbox store, yelmo, yelmo cines, zoo, zoo aquarium

## Viajes

aena, aeropuerto, air europa, air france, airbnb, albergue, alquiler coche, apartamento, avis, barcelo, binter, booking, booking com, camping, centauro, destinia, duty free, easyjet, edreams, europcar, expedia, goldcar, halcon viajes, hertz, hilton, hostal, hostel, hotel, hotels com, iberia, iberia express, ibis, iryo, klm, logitravel, lufthansa, maleta, marriott, melia, nh hotel, okmobility, ouigo, parador, record go, rent a car, riu, rumbo, ryanair, seguro de viaje, sixt, trainline, transavia, trivago, vacaciones, viaje, viajes el corte ingles, volotea, vrbo, vueling, vuelo, wizz air

## Deporte

alquiler de pista, altafit, anytime, anytime fitness, artes marciales, baloncesto, baqueira, basic fit, basic-fit, basicfit, bici, boulder, box crossfit, boxeo, carrera popular, ciclismo, club deportivo, crossfit, crossfit box, decathlon, deporte, deportes, dir, dorsal, dreamfit, duet fit, entrenador, entrenamiento, escalada, esqui, fitbit, fitness, fitness park, forfait, formigal, futbito, futbol, futbol 7, futbol sala, gimnasio, go fit, golf, grandvalira, gym, holmes place, ifitness, inscripcion, inscripcion carrera, kickboxing, kitesurf, liga, maraton, mc fit, mcfit, metropolitan, natacion, o2 centro wellness, ocio deportivo, padel, padelnuestro, pilates, piscina, pista, playtomic, polideportivo, rocodromo, rugby, runnea, running, sierra nevada, ski, smartfit, spinning, sprinter, sputnik climbing, squash, surf, synergym, tenis, triatlon, vivagym, voley, yoga

## Regalos

boda, cumpleanos, detalle, flores, floristeria, regalo, regalos

## Formación (necesidad)

academia, coursera, curso, libreria, libro, libros, matricula, platzi, udemy, universidad

## Mascotas (necesidad)

gato, kiwoko, mascota, perro, pienso, tiendanimal, veterinario

## Trozos de palabra

| Si una palabra contiene… | Tipo |
|---|---|
| cine | Ocio |
| teatr | Ocio |
| concert | Ocio |
| ticket | Ocio |
| burger | Restaurantes y bares |
| pizz | Restaurantes y bares |
| sushi | Restaurantes y bares |
| kebab | Restaurantes y bares |
| taqueri | Restaurantes y bares |
| restaur | Restaurantes y bares |
| cafeter | Restaurantes y bares |
| cervecer | Restaurantes y bares |
| bocater | Restaurantes y bares |
| churrer | Restaurantes y bares |
| gasolin | Transporte |
| parking | Transporte |
| aparcam | Transporte |
| taxi | Transporte |
| farmac | Salud y deporte |
| dental | Salud y deporte |
| optica | Salud y deporte |
| peluquer | Salud y deporte |
| barber | Salud y deporte |
| fitness | Deporte |
| padel | Deporte |
| gimnas | Deporte |
| supermerc | Supermercado |
| hipermerc | Supermercado |
| fruter | Supermercado |
| carnicer | Supermercado |
| panader | Supermercado |
| zapater | Ropa |
| textil | Ropa |
| hotel | Viajes |
| hostal | Viajes |
| airline | Viajes |
| veterin | Mascotas |
| libreri | Formación |
| floris | Regalos |
