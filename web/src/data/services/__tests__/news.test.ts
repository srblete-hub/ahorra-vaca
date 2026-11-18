import { describe, expect, it } from 'vitest'
import { freshNews, isAd, parse, type NewsItem } from '../news'

const RSS = `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
<channel>
  <title>Portada</title>
  <item>
    <title><![CDATA[El Ibex 35 cierra en máximos del año]]></title>
    <link>https://cincodias.elpais.com/mercados/2026-11-18/ibex.html</link>
    <pubDate>Wed, 18 Nov 2026 16:40:00 +0100</pubDate>
    <description><![CDATA[<p>La bolsa española <b>sube</b> un 1,2&nbsp;% impulsada por la banca.</p>]]></description>
    <media:thumbnail url="https://img.example.es/mini.jpg"/>
    <media:content url="https://img.example.es/grande.jpg" medium="image"/>
  </item>
  <item>
    <title>Bitcoin vuelve a los 90.000 dólares - Expansión</title>
    <link>https://news.google.com/rss/articles/abc</link>
    <pubDate>Wed, 18 Nov 2026 15:00:00 GMT</pubDate>
    <description>&lt;a href="x"&gt;Bitcoin vuelve a los 90.000 dólares&lt;/a&gt;</description>
    <source url="https://www.expansion.com">Expansión</source>
  </item>
  <item>
    <title>Contenido patrocinado: la cuenta que te paga más</title>
    <link>https://elpais.com/economia/cuenta.html</link>
    <description>Abre tu cuenta hoy</description>
  </item>
  <item>
    <title>Cómo ahorrar en la factura de la luz</title>
    <link>https://elpais.com/economia/luz.html</link>
    <category>Branded content</category>
  </item>
  <item>
    <title>Las mejores zapatillas para correr</title>
    <link>https://elpais.com/escaparate/zapatillas.html</link>
  </item>
  <item>
    <title>Sin enlace no vale</title>
  </item>
  <item>
    <title>BBVA &amp; Sabadell: lo que queda de la opa</title>
    <link>https://e00-expansion.uecdn.es/empresas/opa.html</link>
    <enclosure url="https://img.example.es/opa.jpg" type="image/jpeg" length="1"/>
    <description><![CDATA[<img src="https://img.example.es/otra.jpg"/> Los accionistas deciden.]]></description>
  </item>
</channel>
</rss>`

describe('parse (RSS)', () => {
  const items = parse(RSS, 'Bolsa')

  it('lee las noticias válidas y quita anuncios y las que no tienen enlace', () => {
    expect(items.map((n) => n.title)).toEqual([
      'El Ibex 35 cierra en máximos del año',
      'Bitcoin vuelve a los 90.000 dólares',
      'BBVA & Sabadell: lo que queda de la opa',
    ])
  })

  it('prefiere la foto grande, limpia el resumen y lee la fecha', () => {
    const n = items[0]
    expect(n.imageUrl).toBe('https://img.example.es/grande.jpg')
    expect(n.summary).toBe('La bolsa española sube un 1,2 % impulsada por la banca.')
    expect(n.source).toBe('Cinco Días')
    expect(n.topic).toBe('Bolsa')
    expect(n.epochMs).toBe(Date.UTC(2026, 10, 18, 15, 40))
  })

  it('separa el medio de los titulares de Google Noticias y descarta el resumen que repite el titular', () => {
    const n = items[1]
    expect(n.source).toBe('Expansión')
    expect(n.summary).toBe('')
    expect(n.imageUrl).toBeNull()
  })

  it('usa la foto del enclosure de imagen y el medio sale del enlace', () => {
    const n = items[2]
    expect(n.imageUrl).toBe('https://img.example.es/opa.jpg')
    expect(n.source).toBe('Expansión')
    expect(n.summary).toBe('Los accionistas deciden.')
  })

  it('entiende la fecha sin zona de Investing (UTC)', () => {
    const xml = '<rss><channel><item><title>¿Por qué sube hoy Nvidia?</title><link>https://es.investing.com/news/x</link><pubDate>2026-11-18 09:37:35</pubDate></item></channel></rss>'
    const [n] = parse(xml, 'Bolsa')
    expect(n.epochMs).toBe(Date.UTC(2026, 10, 18, 9, 37, 35))
    expect(n.source).toBe('Investing.com')
  })

  it('aguanta XML mal formado (un & suelto)', () => {
    const bad = '<rss><channel><item><title>Repsol & Iberdrola suben</title><link>https://elpais.com/a.html</link></item></channel></rss>'
    expect(parse(bad, 'Economía').map((n) => n.title)).toEqual(['Repsol & Iberdrola suben'])
  })
})

describe('isAd', () => {
  const n = (title: string, url = 'https://elpais.com/economia/x.html', summary = ''): Pick<NewsItem, 'title' | 'summary' | 'url'> => ({ title, url, summary })

  it('detecta palabras de anuncio como palabra entera (con tildes)', () => {
    expect(isAd(n('Gran oferta de hipotecas'))).toBe(true)
    expect(isAd(n('Última PROMOCIÓN del banco'))).toBe(true)
    expect(isAd(n('Black Friday: lo que hay que saber'))).toBe(true)
    expect(isAd(n('Titular normal', undefined, 'Artículo ofrecido por Marca'))).toBe(true)
  })

  it('no confunde palabras que solo contienen la de anuncio', () => {
    expect(isAd(n('La ofertante retira la opa'))).toBe(false)
    expect(isAd(n('El BCE sube los tipos'))).toBe(false)
    expect(isAd(n('Telefónica presenta resultados'))).toBe(false)
  })

  it('mira las categorías y la URL', () => {
    expect(isAd(n('Algo neutro'), 'Sponsored')).toBe(true)
    expect(isAd(n('Algo neutro', 'https://elpais.com/escaparate/x.html'))).toBe(true)
    expect(isAd(n('Algo neutro', 'https://x.com/notas/press-release-123'))).toBe(true)
  })
})

describe('freshNews', () => {
  const base: NewsItem = { title: '', source: 'El País', url: 'https://elpais.com/a', epochMs: 0, summary: '', topic: 'Economía', imageUrl: null }
  it('quita viejas y repetidas y ordena de nueva a vieja', () => {
    const now = Date.UTC(2026, 10, 18, 12)
    const list = freshNews([
      { ...base, title: 'Sube el paro', epochMs: now - 3600_000 },
      { ...base, title: 'Sube el paro!', epochMs: now - 7200_000 },
      { ...base, title: 'Noticia de anteayer', epochMs: now - 50 * 3600_000 },
      { ...base, title: 'Sin fecha', epochMs: 0 },
      { ...base, title: 'Lo último', epochMs: now - 60_000 },
    ], now)
    expect(list.map((x) => x.title)).toEqual(['Lo último', 'Sube el paro', 'Sin fecha'])
  })
})
