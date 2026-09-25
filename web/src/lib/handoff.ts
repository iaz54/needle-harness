import { appCatalog, type FunctionCall } from "./engine";
import type { RouteArgs } from "./engine";
import { androidMapsIntent, buildMapsUrl, mapsSearchUrl, type SavedPlace } from "./maps";

export type Handoff = {
  title: string;
  detail: string;
  href: string;
  androidHref: string;
  kind: "web" | "android";
};

const SETTINGS: Record<string, string> = {
  wifi: "android.settings.WIFI_SETTINGS",
  bluetooth: "android.settings.BLUETOOTH_SETTINGS",
  location: "android.settings.LOCATION_SOURCE_SETTINGS",
  sound: "android.settings.SOUND_SETTINGS",
  display: "android.settings.DISPLAY_SETTINGS",
  airplane: "android.settings.AIRPLANE_MODE_SETTINGS",
  nfc: "android.settings.NFC_SETTINGS",
  battery: "android.settings.BATTERY_SAVER_SETTINGS",
  notifications: "android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS",
  date: "android.settings.DATE_SETTINGS",
  apps: "android.settings.APPLICATION_SETTINGS",
  settings: "android.settings.SETTINGS",
};

function settingsIntent(panel: string) {
  const action = SETTINGS[panel] ?? SETTINGS.settings;
  return `intent:#Intent;action=${action};end`;
}

function digits(raw: string) {
  const d = raw.replace(/[^\d+]/g, "");
  return d.length >= 3 ? d : "";
}

function alarmIntent(time: string) {
  const [h, m] = time.split(":");
  return `intent:#Intent;action=android.intent.action.SET_ALARM;i.android.intent.extra.alarm.HOUR=${Number(h)};i.android.intent.extra.alarm.MINUTES=${Number(m)};S.android.intent.extra.alarm.MESSAGE=Latch;end`;
}

function timerIntent(minutes: number) {
  return `intent:#Intent;action=android.intent.action.SET_TIMER;i.android.intent.extra.alarm.LENGTH=${minutes * 60};S.android.intent.extra.alarm.MESSAGE=Latch;end`;
}

function appIntent(pkg: string) {
  return `intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;package=${pkg};end`;
}

export function routeFromCall(call: FunctionCall): RouteArgs {
  const a = call.arguments;
  const list = (v: unknown) => (Array.isArray(v) ? v.filter((x): x is string => typeof x === "string") : []);
  const mode = a.mode;
  return {
    origin: typeof a.origin === "string" ? a.origin : "",
    destination: typeof a.destination === "string" ? a.destination : "",
    waypoints: list(a.waypoints),
    mode: mode === "walking" || mode === "bicycling" || mode === "transit" ? mode : "driving",
    avoid: list(a.avoid).filter(
      (x): x is RouteArgs["avoid"][number] => x === "tolls" || x === "highways" || x === "ferries",
    ),
  };
}

export function handoffFor(call: FunctionCall, places: SavedPlace[]): Handoff | null {
  const a = call.arguments;
  const str = (k: string) => (typeof a[k] === "string" ? a[k] : "");
  switch (call.name) {
    case "start_navigation": {
      const route = routeFromCall(call);
      if (route.destination.trim().length < 2) return null;
      const href = buildMapsUrl(route, places);
      const stops = route.waypoints.length ? `${route.waypoints.length} stop${route.waypoints.length === 1 ? "" : "s"} · ` : "";
      return {
        title: "Open full route in Google Maps",
        detail: `${stops}${route.mode}${route.avoid.length ? ` · avoid ${route.avoid.join(", ")}` : ""}`,
        href,
        androidHref: androidMapsIntent(href),
        kind: "web",
      };
    }
    case "maps_search": {
      const href = mapsSearchUrl(str("query"));
      return {
        title: "Search Google Maps",
        detail: str("query"),
        href,
        androidHref: androidMapsIntent(href),
        kind: "web",
      };
    }
    case "web_search": {
      const href = `https://www.google.com/search?q=${encodeURIComponent(str("query"))}`;
      return { title: "Search the web", detail: str("query"), href, androidHref: href, kind: "web" };
    }
    case "open_url":
      return { title: "Open link", detail: str("url"), href: str("url"), androidHref: str("url"), kind: "web" };
    case "dial_phone": {
      const number = str("number");
      const tel = digits(number);
      const href = tel ? `tel:${tel}` : "https://voice.google.com";
      return {
        title: tel ? `Call ${tel}` : `Call ${number}`,
        detail: tel ? "Opens the dialer" : "No number parsed — pick the contact on the phone",
        href,
        androidHref: href,
        kind: "web",
      };
    }
    case "send_sms": {
      const number = str("number");
      const body = str("body");
      const tel = digits(number);
      const href = `sms:${tel || ""}?body=${encodeURIComponent(body)}`;
      return {
        title: "Send a text",
        detail: body || number,
        href,
        androidHref: href,
        kind: "web",
      };
    }
    case "send_email": {
      const href = `mailto:${str("to")}?subject=${encodeURIComponent(str("subject"))}&body=${encodeURIComponent(str("body"))}`;
      return { title: "Compose email", detail: str("to"), href, androidHref: href, kind: "web" };
    }
    case "open_settings": {
      const panel = str("panel") || "settings";
      return {
        title: `Open ${panel} settings`,
        detail: "Android settings panel",
        href: settingsIntent(panel),
        androidHref: settingsIntent(panel),
        kind: "android",
      };
    }
    case "set_wifi":
      return {
        title: "Open Wi-Fi settings",
        detail: "Android will not toggle Wi-Fi silently",
        href: settingsIntent("wifi"),
        androidHref: settingsIntent("wifi"),
        kind: "android",
      };
    case "set_bluetooth":
      return {
        title: "Open Bluetooth settings",
        detail: "Confirm the radio on the phone",
        href: settingsIntent("bluetooth"),
        androidHref: settingsIntent("bluetooth"),
        kind: "android",
      };
    case "set_airplane":
      return {
        title: "Open airplane mode",
        detail: "Android settings panel",
        href: settingsIntent("airplane"),
        androidHref: settingsIntent("airplane"),
        kind: "android",
      };
    case "set_dnd":
      return {
        title: "Open Do Not Disturb",
        detail: "Notification policy access",
        href: settingsIntent("notifications"),
        androidHref: settingsIntent("notifications"),
        kind: "android",
      };
    case "set_volume":
      return {
        title: "Open sound settings",
        detail: "Stream volume on the phone",
        href: settingsIntent("sound"),
        androidHref: settingsIntent("sound"),
        kind: "android",
      };
    case "set_brightness":
      return {
        title: "Open display settings",
        detail: "Screen brightness",
        href: settingsIntent("display"),
        androidHref: settingsIntent("display"),
        kind: "android",
      };
    case "set_alarm": {
      const time = str("time");
      return {
        title: `Set alarm ${time}`,
        detail: "Clock app on Android",
        href: alarmIntent(time),
        androidHref: alarmIntent(time),
        kind: "android",
      };
    }
    case "start_timer": {
      const minutes = typeof a.minutes === "number" ? a.minutes : 1;
      return {
        title: `Start ${minutes}m timer`,
        detail: "Clock app on Android",
        href: timerIntent(minutes),
        androidHref: timerIntent(minutes),
        kind: "android",
      };
    }
    case "open_app": {
      const key = str("app");
      const app = appCatalog()[key];
      if (!app) return null;
      return {
        title: `Open ${app.label}`,
        detail: app.pkg,
        href: app.web,
        androidHref: appIntent(app.pkg),
        kind: "web",
      };
    }
    case "play_query": {
      const q = str("query");
      const href = `https://www.youtube.com/results?search_query=${encodeURIComponent(q)}`;
      return { title: "Play on YouTube", detail: q, href, androidHref: href, kind: "web" };
    }
    case "add_event": {
      const title = str("title");
      const href = `https://calendar.google.com/calendar/render?action=TEMPLATE&text=${encodeURIComponent(title)}`;
      const androidHref = `intent:#Intent;action=android.intent.action.INSERT;type=vnd.android.cursor.item/event;S.title=${encodeURIComponent(title)};end`;
      return { title: "Add calendar event", detail: title, href, androidHref, kind: "web" };
    }
    default:
      return null;
  }
}
