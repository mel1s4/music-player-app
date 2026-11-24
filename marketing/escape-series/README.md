# Escape Series — Music Player / Viroz Studio

Cinco piezas cuadradas de **1080 × 1080 px** para presentar Music Player sin revelarlo hasta la
última imagen. La serie empieza como una guía de autonomía digital y cierra con el producto.

| # | Archivo | Papel | Titular |
|---|---|---|---|
| 01 | [01-aplicaciones.png](01-aplicaciones.png) | Portada de la serie | APLICACIONES PARA ESCAPAR DE LAS GRANDES CORPORACIONES |
| 02 | [02-sin-cuentas.png](02-sin-cuentas.png) | Principio 01 | SI NO HAY CUENTA, NO HAY PERFIL. |
| 03 | [03-sin-nube.png](03-sin-nube.png) | Principio 02 | TUS ARCHIVOS NO NECESITAN UN SERVIDOR. |
| 04 | [04-sin-anuncios.png](04-sin-anuncios.png) | Principio 03 | SI LA APP ES GRATIS, EL PRODUCTO ERES TÚ. |
| 05 | [05-music-player.png](05-music-player.png) | Revelación | Y TU MÚSICA TAMBIÉN PUEDE ESCAPAR. |

## Sistema visual (Viroz Studio)

| Token | Valor |
|---|---|
| Fondo | `#05070c` |
| Tinta | `#eef6ff` |
| Cuerpo | `#aebdcc` |
| Acento cian | `#0ebbee` |
| Línea | `#232d3b` |
| Retícula | `#0d141d` (54 pt) |
| Número fantasma | `#0a1520` |

- **Display:** Chakra Petch Bold (titulares, versalitas, tracking −25).
- **Cuerpo:** Inter Regular 23 / 34.
- **Metadatos:** JetBrains Mono (etiquetas, contador, pie, CTA).
- **Logos de apps:** `assets/logos/` — NewPipe (PNG oficial), Syncthing, F-Droid y Transmission
  (SVG oficiales), a 150 pt, con pie en JetBrains Mono.
- **Logotipo:** `../facebook/assets/viroz-studio-logo.png`, **solo en la pieza 05**.
- **Márgenes:** 64 pt; línea superior con segmento cian de 140 pt; esquinas cian de 24 pt.
- **Pie:** de 01 a 04 solo `ESCAPE / GUÍA DE AUTONOMÍA` y `NN / 05`; la 05 lleva
  `VIROZSTUDIO.COM` y `DESARROLLADO POR VIROZ STUDIO`.
- **Marca:** las piezas 01–04 no llevan logotipo ni nombre de estudio; la marca aparece únicamente
  en la 05, junto al crédito «Desarrollado por Viroz Studio».
- **Serie:** cada pieza lleva su número fantasma (01–04) o el dispositivo (05) y un avance
  `SIGUIENTE: …` que prepara la revelación.

## Contenido

- **01** abre la serie como guía: cinco piezas, tres principios, ninguna cuenta.
- **02–04** ilustran cada principio con software libre real y su logotipo: NewPipe, Syncthing,
  F-Droid y Transmission. Ninguna pieza nombra a Music Player ni a Viroz Studio.
- **05** es la única que lo revela: titular, tres argumentos, CTA de descarga, captura real de la
  app (tema oscuro, `docs/screenshots/current/02-songs/03-songs-dark.png`) y el crédito
  **Desarrollado por Viroz Studio**.

## Reconstruir

El documento nativo vive en `~/Documents/apps_to_escape-series.vectorcraft` (VectorCraft, 5 mesas
de 1080 pt). `build.js` lo genera completo contra la API de comandos de VectorCraft:

```sh
# en VectorCraft: Script > Other Script…  (o command `script.run`)
```

Cambiar textos, tamaños o el orden de las piezas es editar los objetos `boards` de `build.js` y
volver a ejecutarlo; el script crea un documento nuevo de 5 mesas y no toca el original
`apps_to_escape.vectorcraft`.
