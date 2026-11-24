// Escape Series — five 1080 × 1080 boards for Viroz Studio / Music Player.
// Viroz Studio design language: #05070c ground, Chakra Petch display, Inter body, JetBrains Mono meta.

var W = 1080;
var GAP = 100;

var BG = "#05070c";
var INK = "#eef6ff";
var MUTED = "#aebdcc";
var DIM = "#596a7e";
var CYAN = "#0ebbee";
var LINE = "#232d3b";
var GRID = "#0d141d";
var NUM = "#0a1520";
var SOFT = "#d7e3ef";

var LOGO = "/Users/melisaviroz/Documents/Development/music-player/marketing/facebook/assets/viroz-studio-logo.png";
var LOGO_NEWPIPE = "/Users/melisaviroz/Documents/Development/music-player/marketing/escape-series/assets/logos/newpipe.png";
var LOGO_SYNCTHING = "/Users/melisaviroz/Documents/Development/music-player/marketing/escape-series/assets/logos/syncthing.svg";
var LOGO_FDROID = "/Users/melisaviroz/Documents/Development/music-player/marketing/escape-series/assets/logos/fdroid.svg";
var LOGO_TRANSMISSION = "/Users/melisaviroz/Documents/Development/music-player/marketing/escape-series/assets/logos/transmission.svg";
var SHOT = "/Users/melisaviroz/Documents/Development/music-player/docs/screenshots/current/02-songs/03-songs-dark.png";

vc.run("file.new", {
  title: "Escape Series",
  width: W,
  height: W,
  units: "Pixels",
  artboards: 5,
  artboardLayout: { layout: "row", columns: 5, spacing: GAP },
  colorMode: "rgb",
  rasterEffectsPpi: 300,
  backgroundContents: "transparent"
});

var count = 0;
function run(id, params) { count++; return vc.run(id, params); }

function paintFill(fill) {
  if (fill === "none") run("paint.setFill", { none: true });
  else run("paint.setFill", { color: fill });
  run("paint.setStroke", { none: true });
}

function rect(x, y, w, h, fill, radius) {
  var p = { x: x, y: y, width: w, height: h };
  if (radius) p.radius = radius;
  var r = run("shape.rectangle", p);
  paintFill(fill);
  return r.id;
}

function pathFilled(d, fill) {
  var r = run("path.create", { d: d });
  paintFill(fill);
  return r.id;
}

function glow(cx, cy, rx, color, alpha) {
  var r = run("shape.ellipse", { x: cx - rx, y: cy - rx, width: rx * 2, height: rx * 2 });
  run("paint.setFill", {
    gradient: {
      kind: "radial",
      stops: [
        { offset: 0, color: color, opacity: alpha },
        { offset: 0.55, color: color, opacity: alpha * 0.35 },
        { offset: 1, color: color, opacity: 0 }
      ],
      start: [cx, cy],
      end: [cx + rx, cy]
    }
  });
  run("paint.setStroke", { none: true });
  return r.id;
}

function text(x, y, value, o) {
  o = o || {};
  var params = {
    x: x,
    y: y,
    text: value,
    size: o.size || 18,
    font: o.font || "Inter",
    style: o.style || "Regular",
    color: o.color || INK,
    justify: o.justify || "left"
  };
  if (o.width) params.area = { width: o.width, height: o.height || 2400, fit: "autoHeight" };
  var r = run("text.create", params);
  if (o.tracking || o.leading) {
    run("text.setStyle", { id: r.id, tracking: o.tracking || 0, leading: o.leading || "auto" });
  }
  return r;
}

var boards = [
  {
    n: "01",
    meta2: "GUÍA / AUTONOMÍA DIGITAL",
    eyebrow: "GUÍA / AUTONOMÍA DIGITAL",
    headline: [["APLICACIONES", INK], ["PARA ESCAPAR", INK], ["DE LAS GRANDES", INK], ["CORPORACIONES", CYAN]],
    hsize: 74,
    body: "Cinco piezas sobre el software que devuelve tus datos a tus dispositivos: sin cuentas, sin servidores ajenos y sin anuncios.",
    bullets: ["Software libre siempre que se pueda", "Funciona sin conexión", "Tus archivos, en tu equipo"],
    next: "SIGUE: SIN CUENTAS · SIN NUBE · SIN ANUNCIOS"
  },
  {
    n: "02",
    meta2: "GUÍA / AUTONOMÍA DIGITAL",
    eyebrow: "PRINCIPIO 01 / SIN CUENTAS",
    headline: [["SI NO HAY", INK], ["CUENTA,", INK], ["NO HAY PERFIL.", CYAN]],
    hsize: 88,
    body: "NewPipe abre YouTube sin registro, sin historial y sin un perfil que vender. Nada que iniciar sesión, nada que perder.",
    bullets: ["Sin registro ni historial", "Suscripciones guardadas en tu teléfono", "Reproduce en segundo plano"],
    next: "SIGUIENTE: SIN NUBE",
    apps: [{ path: LOGO_NEWPIPE, rect: [760, 330, 195, 195] }],
    appCaption: "NEWPIPE / CLIENTE LIBRE"
  },
  {
    n: "03",
    meta2: "GUÍA / AUTONOMÍA DIGITAL",
    eyebrow: "PRINCIPIO 02 / SIN NUBE",
    headline: [["TUS ARCHIVOS", INK], ["NO NECESITAN", INK], ["UN SERVIDOR.", CYAN]],
    hsize: 88,
    body: "Syncthing sincroniza tus carpetas directamente entre tus equipos. Sin cuenta, sin límite de espacio y sin que nadie más las lea.",
    bullets: ["Sincronización directa entre dispositivos", "Sin cuota de almacenamiento", "Cifrado de extremo a extremo"],
    next: "SIGUIENTE: SIN ANUNCIOS",
    apps: [{ path: LOGO_SYNCTHING, rect: [760, 330, 195, 195] }],
    appCaption: "SYNCTHING / SINCRONIZACIÓN LIBRE"
  },
  {
    n: "04",
    meta2: "GUÍA / AUTONOMÍA DIGITAL",
    eyebrow: "PRINCIPIO 03 / SIN ANUNCIOS",
    headline: [["SI LA APP ES", INK], ["GRATIS, EL", INK], ["PRODUCTO ERES TÚ.", CYAN]],
    hsize: 88,
    body: "F-Droid distribuye software libre sin rastreadores y Transmission descarga sin ventanas ni suscripciones. La alternativa existe, y suele ser más rápida.",
    bullets: ["Sin rastreadores ni publicidad", "Código abierto y auditable", "Descargas directas, sin cuentas"],
    next: "SIGUIENTE: LA ÚLTIMA PIEZA",
    apps: [
      { path: LOGO_FDROID, rect: [690, 350, 150, 150] },
      { path: LOGO_TRANSMISSION, rect: [880, 350, 150, 150] }
    ],
    appCaption: "F-DROID · TRANSMISSION"
  },
  {
    n: "05",
    meta2: "MUSIC PLAYER / ANDROID",
    eyebrow: "MUSIC PLAYER / ANDROID",
    reveal: true,
    headline: [["Y TU MÚSICA", INK], ["TAMBIÉN PUEDE", INK], ["ESCAPAR.", CYAN]],
    hsize: 62,
    body: "Music Player es un reproductor para Android que trabaja con tus carpetas: escanea tu biblioteca, reproduce en segundo plano y no sube nada a ningún servidor.",
    bullets: ["Sin cuenta. Sin anuncios. Sin nube.", "Reproducción en segundo plano", "Android 8.0+ · v0.6.1"],
    cta: "DESCARGA GRATIS",
    url: "github.com/mel1s4/music-player-app"
  }
];

for (var i = 0; i < boards.length; i++) {
  var b = boards[i];
  var ax = i * (W + GAP);
  var copyW = b.reveal ? 500 : 540;

  rect(ax, 0, W, W, BG);
  glow(ax + (b.reveal ? 800 : 880), 250, 500, CYAN, 0.15);

  var d = "";
  for (var x = 54; x < W; x += 54) d += "M" + (ax + x) + " 0 h1 v" + W + " h-1 z ";
  for (var yy = 54; yy < W; yy += 54) d += "M" + ax + " " + yy + " h" + W + " v1 h-" + W + " z ";
  pathFilled(d, GRID);

  if (!b.reveal) {
    text(ax + 1030, 520, b.n, {
      size: 430, font: "Chakra Petch", style: "Bold", color: NUM, justify: "right", tracking: -40
    });
  }

  if (b.reveal) {
    run("file.place", { path: LOGO, rect: [ax + 64, 46, 116, 44] });
    text(ax + 200, 76, "VIROZ STUDIO", { size: 12, font: "JetBrains Mono", style: "Medium", color: "#dfeaf7", tracking: 120 });
    text(ax + 200, 96, b.meta2, { size: 9.5, font: "JetBrains Mono", style: "Regular", color: "#6f8094", tracking: 160 });
  } else {
    text(ax + 64, 78, "GUÍA / AUTONOMÍA DIGITAL", { size: 12, font: "JetBrains Mono", style: "Medium", color: "#dfeaf7", tracking: 150 });
  }
  text(ax + 1016, 78, b.n + " / 05", { size: 17, font: "Chakra Petch", style: "SemiBold", color: INK, justify: "right", tracking: 60 });

  rect(ax + 64, 140, 952, 1, LINE);
  rect(ax + 64, 139, 140, 3, CYAN);

  rect(ax + 64, 196, 9, 9, CYAN);
  text(ax + 84, 205, b.eyebrow, { size: 12, font: "JetBrains Mono", style: "Medium", color: CYAN, tracking: 150 });

  var size = b.hsize;
  var lead = Math.round(size * 0.94);
  var baseline = 300 + Math.round(size * 0.76);
  for (var l = 0; l < b.headline.length; l++) {
    text(ax + 64, baseline, b.headline[l][0], {
      size: size, font: "Chakra Petch", style: "Bold", color: b.headline[l][1], leading: lead, tracking: -25
    });
    baseline += lead;
  }

  var lastBaseline = baseline - lead;
  var y = lastBaseline + 38;
  rect(ax + 64, y, 74, 3, CYAN);
  y += 46;

  var body = text(ax + 64, y, b.body, { size: 23, font: "Inter", color: MUTED, width: copyW, leading: 34 });
  y = body.bounds.y + body.bounds.height + 62;

  for (var k = 0; k < b.bullets.length; k++) {
    rect(ax + 64, y - 10.5, 7, 7, CYAN);
    text(ax + 86, y, b.bullets[k], { size: 18, font: "Inter", color: SOFT });
    y += 40;
  }

  if (b.cta) {
    var cy = 852;
    rect(ax + 64, cy, 330, 56, CYAN);
    text(ax + 64 + 165, cy + 35, b.cta, {
      size: 17, font: "Chakra Petch", style: "Bold", color: BG, justify: "center", tracking: 110
    });
    text(ax + 64, cy + 88, b.url, { size: 11, font: "JetBrains Mono", color: "#718296" });
  }

  if (b.next) {
    rect(ax + 64, 950, 7, 7, "#2b3a4a");
    text(ax + 84, 958, b.next, { size: 10.5, font: "JetBrains Mono", color: "#5b6d80", tracking: 130 });
    rect(ax + 64, 985, 952, 1, "#161d27");
  }

  if (b.reveal) {
    text(ax + 64, 1032, "VIROZSTUDIO.COM", { size: 10, font: "JetBrains Mono", color: DIM, tracking: 130 });
    text(ax + 540, 1032, "DESARROLLADO POR VIROZ STUDIO", { size: 10, font: "JetBrains Mono", color: "#8fa3b8", tracking: 130, justify: "center" });
  } else {
    text(ax + 64, 1032, "ESCAPE / GUÍA DE AUTONOMÍA", { size: 10, font: "JetBrains Mono", color: DIM, tracking: 130 });
  }
  text(ax + 1016, 1032, b.n + " / 05", { size: 10, font: "JetBrains Mono", color: DIM, tracking: 130, justify: "right" });

  rect(ax + 40, 162, 1.5, 24, CYAN);
  rect(ax + 40, 162, 24, 1.5, CYAN);
  rect(ax + 1016, 968, 1.5, 24, CYAN);
  rect(ax + 992, 990.5, 24, 1.5, CYAN);

  if (b.apps) {
    for (var a = 0; a < b.apps.length; a++) {
      var ap = b.apps[a];
      run("file.place", { path: ap.path, rect: [ax + ap.rect[0], ap.rect[1], ap.rect[2], ap.rect[3]] });
    }
    text(ax + b.apps[0].rect[0], b.apps[0].rect[1] + 254, b.appCaption, {
      size: 10.5, font: "JetBrains Mono", color: "#5b6d80", tracking: 130
    });
  }

  if (b.reveal) {
    rect(ax + 632, 132, 384, 824, "#2a3442", 44);
    rect(ax + 633, 133, 382, 822, "#0b1018", 43);
    run("file.place", { path: SHOT, rect: [ax + 644, 144, 360, 800] });
  }
}

vc.log("commands run: " + count);
