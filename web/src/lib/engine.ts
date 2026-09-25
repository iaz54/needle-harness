export type Gate = "EXECUTE" | "CONFIRM" | "REFUSE";

export type FunctionCall = {
  name: string;
  arguments: Record<string, unknown>;
};

export type TravelMode = "driving" | "walking" | "bicycling" | "transit";
export type AvoidFlag = "tolls" | "highways" | "ferries";

export type RouteArgs = {
  origin: string;
  destination: string;
  waypoints: string[];
  mode: TravelMode;
  avoid: AvoidFlag[];
};

export type CompleteResult = {
  type: "call" | "respond";
  functionCalls: FunctionCall[];
  suppressedCalls: FunctionCall[];
  reasoning: string;
  confidence: number | null;
  prefillTps: number;
  decodeTps: number;
  peakRamMb: number;
  latencyMs: number;
  layer: number;
  gate: Gate;
  triggered: boolean;
};

const ROOMS: Record<string, string> = {
  living: "living",
  "living room": "living",
  lounge: "living",
  kitchen: "kitchen",
  bedroom: "bedroom",
  bed: "bedroom",
  office: "office",
  study: "office",
  bathroom: "bathroom",
  bath: "bathroom",
  garage: "garage",
  "garage door": "garage",
};

const ALIASES: Record<string, string> = {
  home: "Home",
  house: "Home",
  "my house": "Home",
  "my home": "Home",
  work: "Work",
  "the office": "Work",
  "my office": "Work",
  airport: "Airport",
  "the airport": "Airport",
  downtown: "Downtown",
  grocery: "grocery store",
  "the grocery": "grocery store",
  "grocery store": "grocery store",
  "the grocery store": "grocery store",
  gas: "gas station",
  "gas station": "gas station",
  "the gas station": "gas station",
};

const APPS: Record<string, { label: string; pkg: string; web: string }> = {
  spotify: {
    label: "Spotify",
    pkg: "com.spotify.music",
    web: "https://open.spotify.com",
  },
  youtube: {
    label: "YouTube",
    pkg: "com.google.android.youtube",
    web: "https://www.youtube.com",
  },
  camera: {
    label: "Camera",
    pkg: "com.google.android.GoogleCamera",
    web: "https://www.google.com/search?q=open+camera+android",
  },
  chrome: {
    label: "Chrome",
    pkg: "com.android.chrome",
    web: "https://www.google.com",
  },
  gmail: {
    label: "Gmail",
    pkg: "com.google.android.gm",
    web: "https://mail.google.com",
  },
  messages: {
    label: "Messages",
    pkg: "com.google.android.apps.messaging",
    web: "https://messages.google.com/web",
  },
  calendar: {
    label: "Calendar",
    pkg: "com.google.android.calendar",
    web: "https://calendar.google.com",
  },
  clock: {
    label: "Clock",
    pkg: "com.google.android.deskclock",
    web: "https://www.google.com/search?q=clock",
  },
  photos: {
    label: "Photos",
    pkg: "com.google.android.apps.photos",
    web: "https://photos.google.com",
  },
  whatsapp: {
    label: "WhatsApp",
    pkg: "com.whatsapp",
    web: "https://web.whatsapp.com",
  },
  instagram: {
    label: "Instagram",
    pkg: "com.instagram.android",
    web: "https://www.instagram.com",
  },
  calculator: {
    label: "Calculator",
    pkg: "com.google.android.calculator",
    web: "https://www.google.com/search?q=calculator",
  },
  files: {
    label: "Files",
    pkg: "com.google.android.documentsui",
    web: "https://drive.google.com",
  },
  "play store": {
    label: "Play Store",
    pkg: "com.android.vending",
    web: "https://play.google.com/store",
  },
  maps: {
    label: "Maps",
    pkg: "com.google.android.apps.maps",
    web: "https://www.google.com/maps",
  },
  settings: {
    label: "Settings",
    pkg: "com.android.settings",
    web: "https://www.google.com/search?q=android+settings",
  },
  phone: {
    label: "Phone",
    pkg: "com.google.android.dialer",
    web: "https://voice.google.com",
  },
};

const STOP_NAV =
  /\b(?:stop|cancel|end|quit)\s+(?:the\s+)?(?:nav|navigation|route|directions|trip)\b|\bstop navigating\b/i;

const COMMAND_START =
  /^(?:turn|switch|set|lock|unlock|dim|brighten|darken|open|launch|close|call|dial|phone|text|sms|message|email|e-mail|search|google|look|find|navigate|take|drive|walk|bike|play|pause|skip|resume|start|stop|enable|disable|mute|unmute|copy|add|create|schedule|flash|toggle|weather|forecast|calculate|compute|note|remember|memo)\b/i;

function ladderCap(layer: number) {
  if (layer <= 4) return 1;
  if (layer <= 8) return 2;
  if (layer <= 16) return 3;
  return 8;
}

function cleanPlace(s: string) {
  return s
    .replace(/\s+(?:please|now|today)$/i, "")
    .replace(/[.?!,]+$/g, "")
    .replace(/\s+/g, " ")
    .trim();
}

export function aliasPlace(s: string) {
  const c = cleanPlace(s);
  const key = c.toLowerCase().replace(/^(?:a|an)\s+/, "");
  return ALIASES[key] ?? ALIASES[c.toLowerCase()] ?? c;
}

function parseMode(text: string): TravelMode {
  if (/\b(?:walk|walking|on foot|by foot)\b/i.test(text)) return "walking";
  if (
    /\b(?:by\s+transit|public transit|by\s+subway|by\s+train|by\s+bus|take\s+(?:the\s+)?(?:subway|train|bus)|transit to|transit from)\b/i.test(
      text,
    )
  ) {
    return "transit";
  }
  if (/\b(?:by\s+bike|biking|bicycling|bicycle|bike to|bike from|bike me)\b/i.test(text)) {
    return "bicycling";
  }
  return "driving";
}

function parseAvoid(text: string): { avoid: AvoidFlag[]; rest: string } {
  const avoid = new Set<AvoidFlag>();
  let rest = text;
  const rules: [RegExp, AvoidFlag][] = [
    [/\b(?:avoid(?:ing)?|no|without)\s+tolls?\b/gi, "tolls"],
    [/\b(?:avoid(?:ing)?|no|without)\s+(?:the\s+)?(?:highways?|motorways?|freeways?)\b/gi, "highways"],
    [/\b(?:avoid(?:ing)?|no|without)\s+ferries\b/gi, "ferries"],
  ];
  for (const [re, flag] of rules) {
    if (re.test(rest)) {
      avoid.add(flag);
      rest = rest.replace(re, " ");
    }
  }
  rest = rest.replace(/\s+,/g, ",").replace(/,\s*,/g, ",").replace(/\s+/g, " ").trim();
  rest = rest.replace(/[,\s]+$/g, "").trim();
  return { avoid: [...avoid], rest };
}

function splitPlaces(s: string): string[] {
  return s
    .split(/\s*(?:,|&|\+|\band then\b|\band\b|\bthen\b|\bplus\b|->|→)\s*/i)
    .map((p) => p.replace(/^(?:(?:at|to|via|through|by|then|stop(?:ping)?(?:\s+at)?)\s+)+/i, "").trim())
    .map(aliasPlace)
    .filter((p) => p.length >= 2);
}

function hereOrigin(s: string) {
  const c = aliasPlace(s.replace(/^(?:a\s+)?(?:drive|walk|bike|trip|route|transit)\s+/i, ""));
  if (/^(?:here|current location|my location|where i am)$/i.test(c)) return "";
  return c;
}

export function parseRoute(raw: string): RouteArgs | null {
  if (STOP_NAV.test(raw) && !/\b(?:from|via|to)\b/i.test(raw)) return null;
  const { avoid, rest } = parseAvoid(raw);
  const mode = parseMode(rest);

  const fromTo = rest.match(/\bfrom\s+(.+?)\s+to\s+(.+)$/i);
  if (fromTo) {
    const origin = hereOrigin(fromTo[1]);
    const tail = fromTo[2];
    const via = tail.match(
      /^(.+?)\s+(?:via|through|stopping at|stops at|with (?:a )?stops? at)\s+(.+)$/i,
    );
    if (via) {
      const destination = aliasPlace(via[1]);
      const waypoints = splitPlaces(via[2]).slice(0, 9);
      if (destination.length >= 2) return { origin, destination, waypoints, mode, avoid };
    }
    const ordered = splitPlaces(tail);
    if (ordered.length >= 2) {
      return {
        origin,
        destination: ordered[ordered.length - 1]!,
        waypoints: ordered.slice(0, -1).slice(0, 9),
        mode,
        avoid,
      };
    }
    if (ordered.length === 1) {
      return { origin, destination: ordered[0]!, waypoints: [], mode, avoid };
    }
  }

  const onTheWay = rest.match(/\bstop(?:ping)?\s+at\s+(.+?)\s+on the way to\s+(.+)$/i);
  if (onTheWay) {
    const destination = aliasPlace(onTheWay[2] ?? "");
    if (destination.length >= 2) {
      return {
        origin: "",
        destination,
        waypoints: splitPlaces(onTheWay[1] ?? "").slice(0, 9),
        mode,
        avoid,
      };
    }
  }

  const toVia = rest.match(
    /\bto\s+(.+?)\s+(?:via|through|stopping at|with (?:a )?stops? at)\s+(.+)$/i,
  );
  if (toVia) {
    const destination = aliasPlace(toVia[1] ?? "");
    if (destination.length >= 2) {
      return {
        origin: "",
        destination,
        waypoints: splitPlaces(toVia[2] ?? "").slice(0, 9),
        mode,
        avoid,
      };
    }
  }

  const arrowBits = rest.split(/\s*(?:->|→)\s*/);
  if (arrowBits.length >= 2) {
    const places = arrowBits.map(aliasPlace).filter((p) => p.length >= 2);
    if (places.length >= 2) {
      return {
        origin: places[0]!,
        destination: places[places.length - 1]!,
        waypoints: places.slice(1, -1).slice(0, 9),
        mode,
        avoid,
      };
    }
  }

  const listed = rest.match(/\b(?:route|trip|itinerary)\b\s*[:\-]?\s+(.+)$/i);
  if (listed && /,| then | and |->|→/.test(listed[1] ?? "")) {
    const places = splitPlaces(listed[1] ?? "");
    if (places.length >= 2) {
      return {
        origin: places[0]!,
        destination: places[places.length - 1]!,
        waypoints: places.slice(1, -1).slice(0, 9),
        mode,
        avoid,
      };
    }
  }

  const start =
    /(?:navigate(?:\s+me)?(?:\s+to)?|start(?:ing)?\s+nav(?:igation)?(?:\s+to)?|take me(?:\s+to)?|directions(?:\s+to)?|route(?:\s+me)?(?:\s+to)?|(?:drive|walk|bike)(?:\s+me)?\s+to|nav\s+to)\s+(.+)/i.exec(
      rest,
    );
  if (!start) return null;
  if (STOP_NAV.test(rest)) return null;
  const places = splitPlaces(start[1] ?? "");
  if (places.length >= 2) {
    return {
      origin: "",
      destination: places[places.length - 1]!,
      waypoints: places.slice(0, -1).slice(0, 9),
      mode,
      avoid,
    };
  }
  const destination = places[0] ?? "";
  if (destination.length < 2) return null;
  return { origin: "", destination, waypoints: [], mode, avoid };
}

function isCommandStart(s: string) {
  return COMMAND_START.test(s.trim());
}

function splitAtoms(text: string) {
  return text
    .split(/\s*(?:;|\band then\b|,|\band\b|\bthen\b)\s*/i)
    .map((s) => s.trim())
    .filter(Boolean);
}

function mergeAtoms(atoms: string[]) {
  const merged: string[] = [];
  for (const atom of atoms) {
    const prev = merged[merged.length - 1];
    if (!prev || isCommandStart(atom)) {
      merged.push(atom);
      continue;
    }
    const prevIsRoute =
      /\bfrom\s+.+\sto\s+/i.test(prev) ||
      /\b(?:via|through|stopping at|stops at)\b/i.test(prev) ||
      /^(?:route|itinerary|trip|plan)\b/i.test(prev) ||
      /\b(?:navigate|directions|take me|(?:drive|walk|bike)(?:\s+me)?\s+to|nav\s+to)\b/i.test(prev);
    if (prevIsRoute) merged[merged.length - 1] = `${prev} and ${atom}`;
    else merged.push(atom);
  }
  return merged;
}

export function splitClauses(text: string): string[] {
  const atoms = splitAtoms(text);
  const merged = mergeAtoms(atoms);
  if (merged.length >= 2 && merged.every((a) => !isCommandStart(a))) {
    const asRoute = `route ${merged.join(", ")}`;
    if (parseRoute(asRoute)) return [asRoute];
  }
  return merged.length ? merged : [text.trim()].filter(Boolean);
}

function findRoom(text: string) {
  const lower = text.toLowerCase();
  const key = Object.keys(ROOMS)
    .sort((a, b) => b.length - a.length)
    .find((k) => new RegExp(`\\b${k}\\b`, "i").test(lower));
  return key ? ROOMS[key] : undefined;
}

function boolOnOff(text: string): boolean | null {
  const t = text.toLowerCase();
  if (/\bunlock\b|\bunmute\b/.test(t)) return false;
  if (/\block up\b|\block\b|\bmute\b/.test(t)) return true;
  if (/\b(off|disable|stop|close|pause)\b/.test(t) && !t.includes("turn on")) return false;
  if (/\b(on|enable|start|open|resume|play|dim|brighten|toggle)\b/.test(t)) return true;
  return null;
}

function numberIn(text: string, min: number, max: number): number | null {
  const deg = /(-?\d+(?:\.\d+)?)\s*°/.exec(text);
  if (deg) {
    const n = Math.round(Number(deg[1]));
    if (n >= min && n <= max) return n;
  }
  const directed = /(?:to|at|level|speed|volume|brightness|percent|%)\s*(-?\d+)/i.exec(text);
  if (directed) {
    const n = Number(directed[1]);
    if (n >= min && n <= max) return n;
  }
  for (const m of text.matchAll(/\b(\d{1,3})\b/g)) {
    const n = Number(m[1]);
    if (n >= min && n <= max) return n;
  }
  return null;
}

function timeIn(text: string): string | null {
  const ampm = /\b(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b/i.exec(text);
  if (ampm) {
    let h = Number(ampm[1]);
    const min = ampm[2] || "00";
    const mer = (ampm[3] ?? "").toLowerCase();
    if (mer === "pm" && h < 12) h += 12;
    if (mer === "am" && h === 12) h = 0;
    return `${String(h).padStart(2, "0")}:${min}`;
  }
  const hh = /\b([01]?\d|2[0-3]):([0-5]\d)\b/.exec(text);
  if (hh) return `${String(Number(hh[1])).padStart(2, "0")}:${hh[2]}`;
  return null;
}

function call(name: string, args: Record<string, unknown>, score: number) {
  return { call: { name, arguments: args } satisfies FunctionCall, score };
}

function fill(clause: string, layer: number) {
  const t = clause.toLowerCase();
  const room = findRoom(clause);
  const hits: { call: FunctionCall; score: number }[] = [];

  if (STOP_NAV.test(clause) && !parseRoute(clause)) {
    hits.push(call("stop_navigation", {}, 0.98));
  }

  const route = parseRoute(clause);
  if (route) {
    hits.push(
      call(
        "start_navigation",
        {
          origin: route.origin,
          destination: route.destination,
          waypoints: route.waypoints,
          mode: route.mode,
          avoid: route.avoid,
        },
        0.99,
      ),
    );
  }

  if (/\b(lights?|lamp|bulbs?|dim|brighten|darken)\b/i.test(clause)) {
    const on = boolOnOff(clause) ?? true;
    const args: Record<string, unknown> = { room: room ?? "living", on };
    if (t.includes("dim") && layer >= 5) args.brightness = 30;
    if ((t.includes("brighten") || t.includes("darken") === false) && t.includes("brighten") && layer >= 5) {
      args.brightness = 100;
    }
    hits.push(call("set_lights", args, 0.96));
  }

  if (/\bfan\b/i.test(t)) {
    const args: Record<string, unknown> = { room: room ?? "living", on: boolOnOff(clause) ?? true };
    const speed = numberIn(clause, 1, 3);
    if (speed != null && layer >= 5) args.speed = speed;
    hits.push(call("set_fan", args, 0.96));
  }

  if (/temp|thermostat|heat|cool|\bac\b|°|temperature/i.test(clause)) {
    const temp = numberIn(clause, 10, 32);
    if (temp != null) {
      const args: Record<string, unknown> = { temperature: temp };
      if (t.includes("heat") && layer >= 8) args.mode = "heat";
      if (/(cool|\bac\b)/.test(t) && layer >= 8) args.mode = "cool";
      hits.push(call("set_thermostat", args, 0.96));
    }
  }

  if (/lock|unlock|deadbolt/.test(t)) {
    const door = room && ["garage", "office", "living"].includes(room) ? room : "living";
    hits.push(call("lock_door", { door, locked: !t.includes("unlock") }, 0.96));
  }

  if (/\bbluetooth\b/i.test(t) && /\bsettings\b/i.test(t) === false && /\b(on|off|enable|disable|turn)\b/i.test(t)) {
    hits.push(call("set_bluetooth", { on: boolOnOff(clause) ?? true }, 0.95));
  }

  if (/\b(airplane|aeroplane) mode\b/i.test(t) && /\bsettings\b/i.test(t) === false) {
    hits.push(call("set_airplane", { on: boolOnOff(clause) ?? true }, 0.95));
  }

  if (t.includes("mute") && !t.includes("unmute")) hits.push(call("set_volume", { level: 0 }, 0.94));
  else if (t.includes("unmute")) hits.push(call("set_volume", { level: 40 }, 0.94));
  else if (t.includes("volume")) {
    const level = numberIn(clause, 0, 100);
    if (level != null) hits.push(call("set_volume", { level }, 0.94));
  }

  if (/screen brightness|display brightness/i.test(t)) {
    const level = numberIn(clause, 0, 100);
    if (level != null) hits.push(call("set_brightness", { level }, 0.94));
  }

  if (/do not disturb|don't disturb|\bdnd\b/i.test(t)) {
    hits.push(call("set_dnd", { on: boolOnOff(clause) ?? true }, 0.95));
  }

  if (/wi-?fi|wlan/i.test(t) && !/\bsettings\b/i.test(t)) {
    hits.push(call("set_wifi", { on: boolOnOff(clause) ?? true }, 0.95));
  }

  if (/flashlight|torch/i.test(t)) {
    hits.push(call("flash_torch", { on: boolOnOff(clause) ?? true }, 0.95));
  }

  if (t.includes("alarm")) {
    const time = timeIn(clause);
    if (time) hits.push(call("set_alarm", { time }, 0.96));
  }

  if (t.includes("timer")) {
    const minutes = numberIn(clause, 1, 180);
    if (minutes != null) hits.push(call("start_timer", { minutes }, 0.96));
  }

  if (/^play\s+\S.{2,}/i.test(clause.trim()) && !/\b(pause|skip|resume)\b/i.test(t)) {
    const q = clause.replace(/^play\s+/i, "").trim();
    hits.push(call("play_query", { query: q }, 0.93));
  } else if (t.includes("skip")) hits.push(call("play_media", { action: "skip" }, 0.9));
  else if (t.includes("pause")) hits.push(call("play_media", { action: "pause" }, 0.9));
  else if (/play|resume/.test(t)) hits.push(call("play_media", { action: "play" }, 0.9));

  const panel = settingsPanel(clause);
  if (panel) hits.push(call("open_settings", { panel }, 0.97));

  const app = matchApp(clause);
  if (app && !panel) hits.push(call("open_app", { app: app.key, label: app.label }, 0.96));

  const dial = /\b(?:call|dial|phone)\s+([+]?\d[\d\s().-]{2,}|\w[\w\s]{0,32})/i.exec(clause);
  if (dial && !/phone settings/i.test(clause)) {
    const who = (dial[1] ?? "").trim();
    hits.push(call("dial_phone", { number: who }, 0.97));
  }

  const sms =
    /\b(?:text|sms|message)\s+([+]?\d[\d\s().-]{4,}|[A-Za-z][\w\s]{1,24}?)(?:\s+(?:saying|that|:|-)\s+(.+))?$/i.exec(
      clause,
    );
  if (sms && !/message settings/i.test(clause)) {
    hits.push(
      call("send_sms", { number: (sms[1] ?? "").trim(), body: (sms[2] ?? "").trim() }, 0.97),
    );
  }

  const mail = /\b(?:email|e-mail)\s+(\S+@\S+)(?:\s+(?:about|saying|that|:)\s+(.+))?$/i.exec(clause);
  if (mail) {
    hits.push(
      call(
        "send_email",
        { to: mail[1], subject: (mail[2] ?? "Note from Latch").slice(0, 80), body: mail[2] ?? "" },
        0.97,
      ),
    );
  }

  if (/\b(?:near me|around me|nearby|on maps|in maps)\b/i.test(clause) || /\b(?:find|search maps)\b/i.test(clause)) {
    const q = clause
      .replace(/\b(?:find|search maps for|search for|look for|look up)\b/i, "")
      .replace(/\b(?:near me|around me|nearby|on maps|in maps)\b/i, "")
      .trim();
    if (q.length >= 2) hits.push(call("maps_search", { query: q }, 0.96));
  } else {
    const web = /\b(?:search|google|look up)\s+(?:for\s+|the web for\s+)?(.+)$/i.exec(clause);
    if (web && (web[1] ?? "").trim().length >= 2) {
      hits.push(call("web_search", { query: (web[1] ?? "").trim() }, 0.9));
    }
  }

  const url = /\b(?:open|go to)\s+(https?:\/\/\S+)/i.exec(clause);
  if (url) hits.push(call("open_url", { url: url[1] }, 0.98));

  const event = /\b(?:add|create|schedule)\s+(?:a\s+)?(?:calendar\s+)?(?:event|meeting|appointment)\s+(.+)$/i.exec(
    clause,
  );
  if (event) {
    hits.push(
      call("add_event", { title: (event[1] ?? "").trim(), time: timeIn(clause) }, 0.95),
    );
  }

  if (/\b(?:copy|clipboard)\b/i.test(t)) {
    const text = clause.replace(/\b(?:copy|to my clipboard|clipboard)\b/i, "").trim();
    if (text.length >= 1) hits.push(call("copy_text", { text }, 0.9));
  }

  if (/note|memo|remember/i.test(t) && !route) {
    hits.push(call("create_note", { title: clause.trim(), body: clause.trim() }, 0.86));
  }

  if (/weather|forecast/i.test(t)) {
    const city =
      /\b(?:in|for|at)\s+([A-Za-z][a-z]+(?:\s+[A-Za-z][a-z]+)?)/.exec(clause)?.[1] ?? "New York";
    hits.push(call("get_weather", { city }, 0.9));
  }

  const calc = /(?:calculate|compute|what(?:'s| is))\s+([\d.+\-*/() ]+)/i.exec(clause);
  if (calc) hits.push(call("calculate", { expression: (calc[1] ?? "").trim() }, 0.94));

  if (!hits.length) return null;
  hits.sort((a, b) => b.score - a.score);
  return hits[0]!;
}

function settingsPanel(clause: string): string | null {
  const pairs: [RegExp, string][] = [
    [/\bwi-?fi\b.*\bsettings\b|\bsettings\b.*\bwi-?fi\b|\bwlan settings\b/i, "wifi"],
    [/\bbluetooth\b.*\bsettings\b|\bsettings\b.*\bbluetooth\b/i, "bluetooth"],
    [/\b(?:location|gps)\b.*\bsettings\b|\bsettings\b.*\b(?:location|gps)\b/i, "location"],
    [/\b(?:sound|volume)\b.*\bsettings\b|\bsettings\b.*\b(?:sound|volume)\b/i, "sound"],
    [/\b(?:display|brightness)\b.*\bsettings\b|\bsettings\b.*\b(?:display|brightness)\b/i, "display"],
    [/\b(?:airplane|aeroplane)\b.*\bsettings\b|\bsettings\b.*\b(?:airplane|aeroplane)\b/i, "airplane"],
    [/\bnfc\b.*\bsettings\b|\bsettings\b.*\bnfc\b/i, "nfc"],
    [/\bbattery\b.*\bsettings\b|\bsettings\b.*\bbattery\b/i, "battery"],
    [/\bnotification\b.*\bsettings\b|\bsettings\b.*\bnotification\b/i, "notifications"],
    [/\b(?:date|time)\b.*\bsettings\b|\bsettings\b.*\b(?:date|time)\b/i, "date"],
    [/\b(?:apps?|application)\b.*\bsettings\b|\bsettings\b.*\b(?:apps?|application)\b/i, "apps"],
    [/\b(?:open|launch)\s+settings\b|\bsettings\b/i, "settings"],
  ];
  if (/\bopen bluetooth\b/i.test(clause)) return "bluetooth";
  if (!/\bsettings\b/i.test(clause)) return null;
  for (const [re, panel] of pairs) {
    if (re.test(clause)) return panel;
  }
  if (/\bsettings\b/i.test(clause)) return "settings";
  return null;
}

function matchApp(clause: string) {
  if (!/\b(?:open|launch|start)\b/i.test(clause)) return null;
  const keys = Object.keys(APPS).sort((a, b) => b.length - a.length);
  for (const key of keys) {
    if (new RegExp(`\\b${key}\\b`, "i").test(clause)) {
      return { key, ...APPS[key]! };
    }
  }
  return null;
}

export function appCatalog() {
  return APPS;
}

function pack(
  calls: FunctionCall[],
  suppressed: FunctionCall[],
  reasoning: string,
  confidence: number,
  triggered: boolean,
  layer: number,
  auto: number,
  prefill: number,
  decode: number,
  ram: number,
  start: number,
): CompleteResult {
  const gate: Gate =
    calls.length && (confidence >= auto || (triggered && confidence >= 0.55))
      ? "EXECUTE"
      : calls.length || suppressed.length
        ? "CONFIRM"
        : "REFUSE";
  return {
    type: calls.length ? "call" : "respond",
    functionCalls: calls,
    suppressedCalls: suppressed,
    reasoning,
    confidence: calls.length || suppressed.length ? confidence : null,
    prefillTps: prefill,
    decodeTps: decode,
    peakRamMb: ram,
    latencyMs: Math.max(1, Date.now() - start),
    layer,
    gate,
    triggered,
  };
}

export function complete(text: string, layer = 20, auto = 0.72): CompleteResult {
  const start = Date.now();
  const raw = text.trim();
  const depth = Math.min(20, Math.max(2, layer));
  const prefill = 4200 - depth * 90;
  const decode = 980 - depth * 18;
  const ram = 14 + Math.floor(depth * 0.7);
  if (!raw) {
    return pack([], [], "empty input", 0, false, depth, auto, prefill, decode, ram, start);
  }

  const calls: FunctionCall[] = [];
  const bits: string[] = [];
  let triggered = false;
  let confAcc = 0;
  for (const clause of splitClauses(raw)) {
    if (calls.length >= ladderCap(depth)) break;
    const hit = fill(clause, depth);
    if (!hit) continue;
    triggered = true;
    calls.push(hit.call);
    confAcc += hit.score;
    const args = JSON.stringify(hit.call.arguments);
    bits.push(`${hit.call.name} ${args}`);
  }
  let confidence = calls.length ? confAcc / calls.length : 0;
  if (depth <= 4) confidence = Math.max(0.1, confidence - 0.12);
  const suppressed: FunctionCall[] = [];
  let kept = calls;
  if (calls.length && confidence < 0.1 && !triggered) {
    suppressed.push(...calls);
    bits.push(`confidence ${confidence} < 0.1 — suppressed`);
    kept = [];
  }
  const reasoning = bits.join("; ") || "no tool matched — empty list, not a guess";
  return pack(kept, suppressed, reasoning, confidence, triggered, depth, auto, prefill, decode, ram, start);
}

export function safeCalc(expr: string): string {
  const cleaned = expr.replace(/[^\d.+\-*/() ]/g, "").trim();
  if (!cleaned || !/^[\d.+\-*/() ]+$/.test(cleaned)) return "invalid";
  try {
    const v = Function(`"use strict"; return (${cleaned})`)() as unknown;
    if (typeof v !== "number" || !Number.isFinite(v)) return "invalid";
    return String(Math.round(v * 1000) / 1000);
  } catch {
    return "invalid";
  }
}
