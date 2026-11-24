// Branding cleanup + app marks for the Escape Series (edits the open document in place).

var W = 1080;
var AX = [0, 1180, 2360, 3540, 4720];

var DIM = "#596a7e";
var HEAD = "#dfeaf7";
var CAP = "#5b6d80";

var LOGO_NEWPIPE = "/Users/melisaviroz/Documents/Development/music-player/marketing/escape-series/assets/logos/newpipe.png";
var LOGO_SYNCTHING = "/Users/melisaviroz/Documents/Development/music-player/marketing/escape-series/assets/logos/syncthing.svg";
var LOGO_FDROID = "/Users/melisaviroz/Documents/Development/music-player/marketing/escape-series/assets/logos/fdroid.svg";
var LOGO_TRANSMISSION = "/Users/melisaviroz/Documents/Development/music-player/marketing/escape-series/assets/logos/transmission.svg";

function run(id, params) { return vc.run(id, params); }

function text(x, y, value, o) {
  o = o || {};
  var r = run("text.create", {
    x: x, y: y, text: value,
    size: o.size || 10,
    font: o.font || "JetBrains Mono",
    style: o.style || "Regular",
    color: o.color || HEAD,
    justify: o.justify || "left"
  });
  if (o.tracking) run("text.setStyle", { id: r.id, tracking: o.tracking });
  return r.id;
}

function stripArea(ax, x, y, w, h) {
  var found = run("document.find", { within: [ax + x, y, w, h], limit: 100 });
  var kill = [];
  for (var i = 0; i < found.matches.length; i++) {
    var m = found.matches[i];
    if (!/\/ 05$/.test(m.name)) kill.push(m.id);
  }
  if (kill.length) run("layer.delete", { ids: kill });
  return kill.length;
}

var removed = 0;

// 01–04: no logo, no studio name, no studio footer
for (var b = 0; b < 4; b++) {
  var ax = AX[b];
  removed += stripArea(ax, 0, 30, W, 80);      // header (logo + wordmark + subtitle)
  removed += stripArea(ax, 0, 1000, W, 50);    // footer (url + credit)

  text(ax + 64, 78, "GUÍA / AUTONOMÍA DIGITAL", { size: 12, style: "Medium", color: HEAD, tracking: 150 });
  text(ax + 64, 1032, "ESCAPE / GUÍA DE AUTONOMÍA", { size: 10, color: DIM, tracking: 130 });
}

// 05: keep the studio, add the credit line
var credit = run("document.find", { within: [4720, 1000, 1080, 50], text: "HECHO CON CUIDADO", limit: 100 });
if (credit.matches.length) {
  run("text.setText", { ids: [credit.matches[0].id], text: "DESARROLLADO POR VIROZ STUDIO" });
  run("paint.setFill", { ids: [credit.matches[0].id], color: "#8fa3b8" });
}

// app marks + captions
run("file.place", { path: LOGO_NEWPIPE, rect: [AX[1] + 760, 570, 150, 150] });
text(AX[1] + 760, 762, "NEWPIPE / CLIENTE LIBRE", { size: 10.5, color: CAP, tracking: 130 });

run("file.place", { path: LOGO_SYNCTHING, rect: [AX[2] + 760, 570, 150, 150] });
text(AX[2] + 760, 762, "SYNCTHING / SINCRONIZACIÓN LIBRE", { size: 10.5, color: CAP, tracking: 130 });

run("file.place", { path: LOGO_FDROID, rect: [AX[3] + 690, 570, 150, 150] });
run("file.place", { path: LOGO_TRANSMISSION, rect: [AX[3] + 862, 570, 150, 150] });
text(AX[3] + 690, 762, "F-DROID · TRANSMISSION", { size: 10.5, color: CAP, tracking: 130 });

vc.log("removed objects: " + removed);
