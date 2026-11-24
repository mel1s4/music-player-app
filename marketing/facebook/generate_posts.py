#!/usr/bin/env python3
"""Generate 1080x1080 Spanish Facebook posts for Music Player / Viroz Studio."""
from pathlib import Path
import html
import subprocess
import json

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "source"
IMAGES = ROOT / "images"
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
SOURCE.mkdir(exist_ok=True)
IMAGES.mkdir(exist_ok=True)

POSTS = [
    dict(
        slug="01-local-first", index="01", accent="#0EBBEE", rgb="14,187,238", side="right",
        screenshot="02-songs/01-songs-library.png",
        eyebrow="MUSIC PLAYER / BIBLIOTECA LOCAL",
        headline="TU MÚSICA.<br>SIN NUBES.<br><em>SIN CUENTAS.</em>",
        body="Un reproductor para Android que trabaja con tus carpetas. Escanea tu biblioteca, reproduce en segundo plano y escucha sin subir nada a ningún servidor.",
        bullets=["Tu biblioteca vive en tu teléfono", "Reproducción en segundo plano", "Sin nube, sin registro, sin anuncios"],
        size=76,
        caption="Tu música no necesita una nube.\n\nMusic Player es un reproductor para Android que trabaja con tus carpetas: escanea tu biblioteca, reproduce en segundo plano y no sube nada a ningún servidor.\n\nSin cuenta. Sin anuncios. Sin nube.",
    ),
    dict(
        slug="02-privacy", index="02", accent="#22E584", rgb="34,229,132", side="left",
        screenshot="04-files/01-files-root.png",
        eyebrow="PRIVACIDAD / PERMISOS MÍNIMOS",
        headline="NADA SALE<br>DE TU<br><em>TELÉFONO.</em>",
        body="Sin cuenta, sin anuncios y sin permiso de red. La app solo ve las carpetas que le concedes mediante el selector de Android.",
        bullets=["Cero anuncios y cero rastreo", "Acceso solo a tus carpetas", "Todo se queda en el dispositivo"],
        size=76,
        caption="Tu biblioteca es tuya. Punto.\n\nSin cuenta, sin anuncios y sin permiso de red. Music Player solo accede a las carpetas que tú eliges con el selector de Android, y todo lo que guarda se queda en tu teléfono.",
    ),
    dict(
        slug="03-sorting", index="03", accent="#2B8CFF", rgb="43,140,255", side="right",
        screenshot="02-songs/02-sort-menu.png",
        eyebrow="ORDEN / 4 CRITERIOS",
        headline="TU BIBLIOTECA,<br><em>A TU ORDEN.</em>",
        body="Ordena canciones, álbumes, artistas y carpetas por fecha, título, artista o duración. Salta por letra cuando la lista se hace larga.",
        bullets=["Fecha, título, artista o duración", "Barra alfabética en las listas", "El mismo orden en toda la biblioteca"],
        size=72,
        caption="¿Por dónde empiezo? Por donde tú quieras.\n\nOrdena canciones, álbumes, artistas y carpetas por fecha de añadido, título, artista o duración. Las listas largas incluyen una barra alfabética para saltar directo a la letra que buscas.",
    ),
    dict(
        slug="04-starred", index="04", accent="#EDBB30", rgb="237,187,48", side="left",
        screenshot="03-albums-artists/01-albums-starred.png",
        eyebrow="COLECCIONES / DESTACADAS",
        headline="FIJA LO QUE<br><em>MÁS SUENA.</em>",
        body="Marca tus álbumes y artistas favoritos con una estrella. Aparecen arriba, en una sección plegable, sin perder el orden alfabético.",
        bullets=["Estrella desde el editor", "Sección plegable en el archivo", "Se recuerda entre sesiones"],
        size=76,
        caption="Lo que más escuchas, siempre a mano.\n\nAbre el editor de un álbum o artista y márcalo con una estrella. Las colecciones destacadas aparecen en una sección plegable al inicio de Álbumes y Artistas, sin romper el orden alfabético.",
    ),
    dict(
        slug="05-folders", index="05", accent="#B06BFF", rgb="176,107,255", side="right",
        screenshot="04-files/03-folder-actions.png",
        eyebrow="ARCHIVOS / CONTROL TOTAL",
        headline="TUS CARPETAS,<br><em>BAJO CONTROL.</em>",
        body="Explora el árbol de archivos, fija carpetas y mueve, desglosa o reordena su contenido sin salir de la app.",
        bullets=["Ruta de carpetas navegable", "Mover y desglosar carpetas", "Reordenar el contenido"],
        size=70,
        caption="Tus archivos, sin perder el hilo.\n\nNavega por carpetas con una ruta clara, fija las que más usas y usa el menú de cada carpeta para reordenar su contenido, moverla o desglosarla en la carpeta padre.",
    ),
    dict(
        slug="06-cover-scrubber", index="06", accent="#FF2D55", rgb="255,45,85", side="left",
        screenshot="06-playback/02-now-playing-waveform.png",
        eyebrow="REPRODUCCIÓN / FORMA DE ONDA",
        headline="EL TIEMPO,<br>ANTES DE LA<br><em>PORTADA.</em>",
        body="La barra de progreso es una forma de onda real. Está antes de la carátula, con el tiempo visible, y puedes tocar o arrastrar para recorrer la canción.",
        bullets=["Forma de onda real", "Tiempo a la vista", "Toca o arrastra para buscar"],
        size=68,
        caption="El tiempo, antes de la portada.\n\nLa barra de progreso de Music Player es una forma de onda real y ahora vive antes de la carátula. Toca o arrastra para buscar dentro de la canción: los picos y valles muestran la energía de cada tramo.",
    ),
    dict(
        slug="07-tags", index="07", accent="#0EBBEE", rgb="14,187,238", side="right",
        screenshot="07-editing/02-edit-song-tags.png",
        eyebrow="EDICIÓN / ETIQUETAS",
        headline="ETIQUETA UNA VEZ.<br><em>ENCUENTRA SIEMPRE.</em>",
        body="Edita metadatos reales, crea etiquetas y añade las ocho más usadas con un toque. Las listas se actualizan al instante.",
        bullets=["Metadatos ID3, Vorbis y MP4", "Ocho etiquetas frecuentes a un toque", "Listas instantáneas"],
        size=62,
        caption="Etiquetar no debería ser un trabajo.\n\nMusic Player edita metadatos reales y guarda tus etiquetas al instante. En el editor, las ocho etiquetas más usadas de tu biblioteca aparecen como botones: un toque y listo.",
    ),
    dict(
        slug="08-search", index="08", accent="#2B8CFF", rgb="43,140,255", side="left",
        screenshot="09-search/02-search-results.png",
        eyebrow="BÚSQUEDA / GESTO DE ARRASTRE",
        headline="TIRA.<br>BUSCA.<br><em>SUENA.</em>",
        body="Tira hacia abajo desde el inicio de cualquier pantalla para abrir la búsqueda. Canciones, álbumes y artistas en un solo lugar.",
        bullets=["Búsqueda unificada", "Filtros por tipo", "Gesto de arrastre"],
        size=82,
        caption="Tira, busca, suena.\n\nDesde el inicio de cualquier pantalla, tira hacia abajo para abrir la búsqueda. Primero verás una pista con la barra de progreso; si sigues tirando, la búsqueda se revela. Si sueltas antes, vuelve atrás.\n\nEncuentra canciones, álbumes y artistas al instante, con filtros por tipo y resultados mientras escribes.",
    ),
    dict(
        slug="09-bulk-selection", index="09", accent="#22E584", rgb="34,229,132", side="right",
        screenshot="08-selection-tools/01-bulk-selection.png",
        eyebrow="SELECCIÓN / ACCIONES EN LOTE",
        headline="SELECCIONA.<br>ACTÚA.<br><em>LISTO.</em>",
        body="Mantén presionada cualquier canción para editar en lote: artista, álbum, etiquetas, lista, compresión o borrado.",
        bullets=["Selección en cualquier lista", "Edición por lotes", "Confirmaciones claras"],
        size=82,
        caption="Una canción, dos, o toda la lista.\n\nMantén presionada cualquier fila para activar la selección. Desde la barra inferior puedes definir artista o álbum, añadir etiquetas, sumar a una lista, comprimir o borrar en lote.",
    ),
    dict(
        slug="10-free-space", index="10", accent="#EDBB30", rgb="237,187,48", side="left",
        screenshot="08-selection-tools/03-free-up-space.png",
        eyebrow="ALMACENAMIENTO / COMPRIMIR",
        headline="MÁS MÚSICA.<br><em>MENOS ESPACIO.</em>",
        body="Encuentra las pistas que más pesan, elige el bitrate y convierte a AAC. Conserva el original si quieres.",
        bullets=["Ahorro estimado por pista", "De 96 a 192 kbps", "Original opcional"],
        size=70,
        caption="Haz sitio sin borrar recuerdos.\n\nOrdena tu biblioteca por tamaño, elige las pistas más pesadas y conviértelas a AAC con el bitrate que prefieras. La app te muestra el ahorro estimado y puedes conservar el archivo original.",
    ),
    dict(
        slug="11-personalization", index="11", accent="#B06BFF", rgb="176,107,255", side="right",
        screenshot="10-settings/02-settings-dark.png",
        eyebrow="PERSONALIZACIÓN / TU MANO",
        headline="A TU MANO.<br><em>A TU TEMA.</em>",
        body="Tema claro, oscuro o del sistema. La barra inferior se adapta a tu mano dominante y el icono de la app también es tuyo.",
        bullets=["Tres modos de tema", "Navegación espejada", "Cinco iconos"],
        size=76,
        caption="La app se adapta a ti, no al revés.\n\nElige tema claro, oscuro o del sistema. Activa la mano dominante para espejar la barra inferior y elige entre cinco estilos de icono para la pantalla de inicio.\n\nTu teléfono, tu forma de escuchar.",
    ),
    dict(
        slug="12-download", index="12", accent="#0EBBEE", rgb="14,187,238", side="left",
        screenshot="01-home/01-dashboard.png",
        eyebrow="GRATIS / CÓDIGO ABIERTO / ANDROID 8+",
        headline="TU BIBLIOTECA<br>LOCAL<br><em>TE ESPERA.</em>",
        body="Gratis, de código abierto y sin ataduras. Descarga Music Player y vuelve a escuchar lo que ya es tuyo.",
        bullets=["Android 8.0 o superior", "Gratis y código abierto", "Hecho por Viroz Studio"],
        size=76,
        caption="Tu biblioteca local te espera.\n\nMusic Player es gratis y de código abierto. Descarga el APK para Android 8.0 o superior y empieza a escuchar tus archivos sin cuentas ni nubes.\n\nHecho con cuidado por Viroz Studio.",
    ),
]

TEMPLATE = """<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<title>{title}</title>
<style>
@font-face {{ font-family:'Chakra Petch'; src:url('../assets/fonts/ChakraPetch-Regular.ttf'); font-weight:400; }}
@font-face {{ font-family:'Chakra Petch'; src:url('../assets/fonts/ChakraPetch-SemiBold.ttf'); font-weight:600; }}
@font-face {{ font-family:'Chakra Petch'; src:url('../assets/fonts/ChakraPetch-Bold.ttf'); font-weight:700; }}
@font-face {{ font-family:'Inter'; src:url('../assets/fonts/Inter-Variable.ttf'); font-weight:100 900; }}
@font-face {{ font-family:'JetBrains Mono'; src:url('../assets/fonts/JetBrainsMono-Variable.ttf'); font-weight:100 800; }}
* {{ box-sizing:border-box; }}
html, body {{ margin:0; width:1080px; height:1080px; overflow:hidden; background:#05070c; }}
body {{ color:#eef6ff; font-family:'Inter', sans-serif; -webkit-font-smoothing:antialiased; }}
.canvas {{ position:relative; width:1080px; height:1080px; overflow:hidden;
  background:
    radial-gradient(900px 650px at 83% 24%, rgba({rgb},.17), transparent 68%),
    radial-gradient(700px 700px at 6% 96%, rgba(43,140,255,.12), transparent 70%),
    #05070c; }}
.grid {{ position:absolute; inset:0; opacity:.62;
  background-image:
    linear-gradient(rgba(238,246,255,.055) 1px, transparent 1px),
    linear-gradient(90deg, rgba(238,246,255,.055) 1px, transparent 1px),
    linear-gradient(rgba(238,246,255,.045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(238,246,255,.045) 1px, transparent 1px);
  background-size:54px 54px, 54px 54px, 216px 216px, 216px 216px;
  mask-image:linear-gradient(180deg, rgba(0,0,0,.9), transparent 82%); }}
.vignette {{ position:absolute; inset:0; background:radial-gradient(circle at 50% 45%, transparent 30%, rgba(0,0,0,.52) 100%); }}
.scan {{ position:absolute; inset:0; background:repeating-linear-gradient(0deg, transparent 0 3px, rgba(0,0,0,.13) 3px 4px); opacity:.28; }}
.bg-number {{ position:absolute; right:{number_right}px; top:135px; font-family:'Chakra Petch'; font-weight:700; font-size:470px; line-height:1; letter-spacing:-.06em; color:rgba({rgb},.045); }}
header {{ position:absolute; left:64px; top:46px; right:64px; height:72px; display:flex; align-items:center; }}
.brand {{ width:164px; height:auto; display:block; }}
.meta {{ margin-left:22px; font-family:'JetBrains Mono'; font-size:12px; line-height:1.65; letter-spacing:.14em; color:#dfeaf7; }}
.meta small {{ display:block; color:#6f8094; font-size:10px; letter-spacing:.17em; }}
.counter {{ margin-left:auto; font-family:'Chakra Petch'; font-weight:600; font-size:18px; letter-spacing:.08em; color:#eef6ff; }}
.counter span {{ color:rgba({rgb},.95); }}
.topline {{ position:absolute; left:64px; right:64px; top:140px; height:1px; background:rgba(238,246,255,.13); }}
.topline:before {{ content:''; position:absolute; left:0; top:-1px; width:140px; height:3px; background:{accent}; box-shadow:0 0 20px rgba({rgb},.6); }}
.copy {{ position:absolute; top:184px; width:{copy_width}px; height:752px; display:flex; flex-direction:column; {copy_pos} }}
.eyebrow {{ display:flex; align-items:center; gap:10px; font-family:'JetBrains Mono'; font-size:12px; letter-spacing:.17em; color:{accent}; text-transform:uppercase; }}
.eyebrow .tick {{ width:9px; height:9px; background:{accent}; box-shadow:0 0 15px rgba({rgb},.8); }}
.eyebrow .dim {{ color:#6c7c90; }}
h1 {{ margin:26px 0 0; font-family:'Chakra Petch'; font-weight:700; font-size:{headline_size}px; line-height:.94; letter-spacing:-.018em; text-transform:uppercase; color:#eef6ff; }}
h1 em {{ font-style:normal; color:{accent}; }}
.rule {{ width:74px; height:3px; margin:28px 0 22px; background:{accent}; box-shadow:0 0 18px rgba({rgb},.55); }}
p {{ margin:0; max-width:530px; font-size:25px; line-height:1.42; letter-spacing:-.01em; color:#aebdcc; }}
ul {{ list-style:none; padding:0; margin:26px 0 0; display:grid; gap:10px; }}
li {{ position:relative; padding-left:23px; font-size:18px; line-height:1.35; color:#d7e3ef; }}
li:before {{ content:''; position:absolute; left:0; top:6px; width:7px; height:7px; background:{accent}; }}
.cta-wrap {{ margin-top:auto; }}
.cta {{ display:inline-flex; align-items:center; gap:16px; padding:17px 22px; background:{accent}; color:#05070c; font-family:'Chakra Petch'; font-weight:700; font-size:17px; letter-spacing:.11em; box-shadow:0 0 30px rgba({rgb},.28); }}
.cta .arrow {{ font-size:24px; line-height:.7; }}
.url {{ margin-top:11px; font-family:'JetBrains Mono'; font-size:11px; letter-spacing:.03em; color:#718296; }}
.device {{ position:absolute; top:150px; width:368px; height:792px; {device_pos} }}
.device-glow {{ position:absolute; inset:-36px; background:radial-gradient(ellipse at 50% 55%, rgba({rgb},.25), transparent 68%); filter:blur(12px); }}
.device-body {{ position:absolute; inset:0; padding:10px; border-radius:46px;
  background:linear-gradient(145deg,#1d2634 0%,#090d14 48%,#151e2b 100%);
  border:1px solid rgba(196,216,238,.26);
  box-shadow:0 38px 90px rgba(0,0,0,.72), 0 0 0 1px rgba(0,0,0,.65), 0 0 55px rgba({rgb},.16); }}
.screen {{ display:block; width:348px; height:772px; object-fit:cover; border-radius:37px; background:#0b1018; }}
.reflection {{ position:absolute; left:10px; top:10px; width:348px; height:772px; border-radius:37px; pointer-events:none;
  background:linear-gradient(125deg, rgba(255,255,255,.12), transparent 24%, transparent 62%, rgba(255,255,255,.035)); }}
.corner {{ position:absolute; width:24px; height:24px; border-color:rgba({rgb},.65); border-style:solid; }}
.corner.tl {{ left:40px; top:162px; border-width:1px 0 0 1px; }}
.corner.br {{ right:{corner_right}px; bottom:104px; border-width:0 1px 1px 0; }}
footer {{ position:absolute; left:64px; right:64px; bottom:38px; display:flex; align-items:center; font-family:'JetBrains Mono'; font-size:10px; letter-spacing:.13em; color:#596a7e; }}
footer .mid {{ margin-left:auto; margin-right:auto; }}
footer .ver {{ color:rgba({rgb},.82); }}
</style>
</head>
<body>
<div class="canvas">
  <div class="grid"></div>
  <div class="bg-number">{index}</div>
  <div class="vignette"></div>
  <div class="scan"></div>
  <header>
    <img class="brand" src="../assets/viroz-studio-logo.png" alt="Viroz Studio">
    <div class="meta">VIROZ STUDIO<small>MUSIC PLAYER / ANDROID</small></div>
    <div class="counter">{index} <span>/ 12</span></div>
  </header>
  <div class="topline"></div>
  <main class="copy">
    <div class="eyebrow"><span class="tick"></span>{eyebrow}<span class="dim">/ {index}</span></div>
    <h1>{headline}</h1>
    <div class="rule"></div>
    <p>{body}</p>
    <ul>{bullets}</ul>
    <div class="cta-wrap">
      <div class="cta">DESCARGA GRATIS <span class="arrow">↗</span></div>
      <div class="url">github.com/mel1s4/music-player-app</div>
    </div>
  </main>
  <div class="device">
    <div class="device-glow"></div>
    <div class="device-body"><img class="screen" src="{screenshot}" alt="{alt}"><div class="reflection"></div></div>
  </div>
  <div class="corner tl"></div><div class="corner br"></div>
  <footer><span>VIROZSTUDIO.COM</span><span class="mid">HECHO CON CUIDADO / MX</span><span class="ver">v0.6.1</span></footer>
</div>
</body>
</html>
"""

for post in POSTS:
    side = post["side"]
    copy_pos = "left:64px;" if side == "right" else "left:462px;"
    device_pos = "right:64px;" if side == "right" else "left:64px;"
    copy_width = 548 if side == "right" else 550
    number_right = 52 if side == "right" else 650
    corner_right = 64
    screenshot_rel = "../../../docs/screenshots/current/" + post["screenshot"]
    screenshot = (SOURCE / screenshot_rel).resolve()
    if not screenshot.exists():
        raise SystemExit(f"missing screenshot {screenshot}")
    bullets = "".join(f"<li>{b}</li>" for b in post["bullets"])
    doc = TEMPLATE.format(
        title=f"Music Player {post['index']} — Viroz Studio",
        index=post["index"], accent=post["accent"], rgb=post["rgb"],
        eyebrow=post["eyebrow"], headline=post["headline"], body=post["body"],
        bullets=bullets, headline_size=post["size"], copy_width=copy_width,
        copy_pos=copy_pos, device_pos=device_pos, number_right=number_right,
        corner_right=corner_right, screenshot=screenshot_rel, alt=post["slug"],
    )
    html_path = SOURCE / f"{post['slug']}.html"
    html_path.write_text(doc)
    out = IMAGES / f"{post['slug']}.png"
    cmd = [CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars",
           "--default-background-color=00000000", "--force-device-scale-factor=1",
           "--window-size=1080,1080", "--virtual-time-budget=2500",
           f"--user-data-dir=/tmp/viroz-fb-chrome-{post['index']}",
           f"--screenshot={out}", html_path.as_uri()]
    print("rendering", post["slug"])
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    print("  ->", out)
print("done")
