import type { AvoidFlag, FunctionCall, TravelMode } from "./engine";
import { safeCalc } from "./engine";

export type RoomState = {
  id: string;
  label: string;
  light: boolean;
  brightness: number;
  fan: boolean;
  fanSpeed: number;
  locked: boolean;
};

export type PhoneState = {
  volume: number;
  brightness: number;
  dnd: boolean;
  wifi: boolean;
  bluetooth: boolean;
  airplane: boolean;
  torch: boolean;
  alarm: string | null;
  timerMin: number | null;
  nowPlaying: string | null;
  playing: boolean;
  navActive: boolean;
  navOrigin: string;
  navDestination: string | null;
  navWaypoints: string[];
  navMode: TravelMode;
  navAvoid: AvoidFlag[];
  navOptimize: boolean;
};

export type Note = { title: string; body: string };

export type HomeState = {
  rooms: Record<string, RoomState>;
  temperature: number;
  mode: string;
  phone: PhoneState;
  notes: Note[];
  revision: number;
};

const ROOM_DEFS = [
  ["living", "Living room"],
  ["kitchen", "Kitchen"],
  ["bedroom", "Bedroom"],
  ["office", "Office"],
  ["bathroom", "Bathroom"],
  ["garage", "Garage"],
] as const;

export function emptyHome(): HomeState {
  const rooms: Record<string, RoomState> = {};
  for (const [id, label] of ROOM_DEFS) {
    rooms[id] = {
      id,
      label,
      light: false,
      brightness: 70,
      fan: false,
      fanSpeed: 2,
      locked: id === "garage",
    };
  }
  return {
    rooms,
    temperature: 21,
    mode: "auto",
    phone: {
      volume: 40,
      brightness: 55,
      dnd: false,
      wifi: true,
      bluetooth: true,
      airplane: false,
      torch: false,
      alarm: null,
      timerMin: null,
      nowPlaying: null,
      playing: false,
      navActive: false,
      navOrigin: "",
      navDestination: null,
      navWaypoints: [],
      navMode: "driving",
      navAvoid: [],
      navOptimize: false,
    },
    notes: [
      { title: "Filter change", body: "Kitchen fridge water filter due next month" },
      { title: "Garage code", body: "Guest keypad is rotated every quarter" },
    ],
    revision: 0,
  };
}

function withPhone(home: HomeState, patch: Partial<PhoneState>): HomeState {
  return { ...home, phone: { ...home.phone, ...patch }, revision: home.revision + 1 };
}

function withRoom(home: HomeState, id: string, patch: Partial<RoomState>): HomeState {
  const current = home.rooms[id] ?? home.rooms.living;
  if (!current) return home;
  return {
    ...home,
    rooms: { ...home.rooms, [current.id]: { ...current, ...patch } },
    revision: home.revision + 1,
  };
}

function str(v: unknown, fallback = "") {
  return typeof v === "string" ? v : fallback;
}

function num(v: unknown, fallback = 0) {
  return typeof v === "number" ? v : fallback;
}

function bool(v: unknown, fallback = false) {
  return typeof v === "boolean" ? v : fallback;
}

function strList(v: unknown): string[] {
  return Array.isArray(v) ? v.filter((x): x is string => typeof x === "string") : [];
}

export function applyCall(home: HomeState, call: FunctionCall): { home: HomeState; message: string } {
  const a = call.arguments;
  switch (call.name) {
    case "set_lights": {
      const id = str(a.room, "living");
      const room = home.rooms[id] ?? home.rooms.living!;
      const brightness = a.brightness == null ? room.brightness : num(a.brightness, room.brightness);
      const on = bool(a.on, true);
      return {
        home: withRoom(home, room.id, { light: on, brightness }),
        message: `${room.label} lights ${on ? `on ${brightness}%` : "off"}`,
      };
    }
    case "set_fan": {
      const id = str(a.room, "living");
      const room = home.rooms[id] ?? home.rooms.living!;
      const speed = a.speed == null ? room.fanSpeed : num(a.speed, room.fanSpeed);
      const on = bool(a.on, true);
      return {
        home: withRoom(home, room.id, { fan: on, fanSpeed: speed }),
        message: `${room.label} fan ${on ? `on x${speed}` : "off"}`,
      };
    }
    case "set_thermostat": {
      const temperature = num(a.temperature, home.temperature);
      const mode = str(a.mode, home.mode);
      return {
        home: { ...home, temperature, mode, revision: home.revision + 1 },
        message: `thermostat ${temperature}°C ${mode}`,
      };
    }
    case "lock_door": {
      const id = str(a.door, "living");
      const room = home.rooms[id] ?? home.rooms.living!;
      const locked = bool(a.locked, true);
      return {
        home: withRoom(home, room.id, { locked }),
        message: `${room.label} ${locked ? "locked" : "unlocked"}`,
      };
    }
    case "set_volume":
      return {
        home: withPhone(home, { volume: num(a.level, 0) }),
        message: `volume ${num(a.level, 0)}`,
      };
    case "set_brightness":
      return {
        home: withPhone(home, { brightness: num(a.level, 0) }),
        message: `screen ${num(a.level, 0)}`,
      };
    case "set_dnd":
      return { home: withPhone(home, { dnd: bool(a.on, true) }), message: `dnd ${bool(a.on, true)}` };
    case "set_wifi":
      return { home: withPhone(home, { wifi: bool(a.on, true) }), message: `wifi ${bool(a.on, true)}` };
    case "set_bluetooth":
      return {
        home: withPhone(home, { bluetooth: bool(a.on, true) }),
        message: `bluetooth ${bool(a.on, true)}`,
      };
    case "set_airplane":
      return {
        home: withPhone(home, { airplane: bool(a.on, true) }),
        message: `airplane ${bool(a.on, true)}`,
      };
    case "flash_torch":
      return { home: withPhone(home, { torch: bool(a.on, true) }), message: `torch ${bool(a.on, true)}` };
    case "set_alarm":
      return { home: withPhone(home, { alarm: str(a.time) }), message: `alarm ${str(a.time)}` };
    case "start_timer":
      return {
        home: withPhone(home, { timerMin: num(a.minutes, 1) }),
        message: `timer ${num(a.minutes, 1)}m`,
      };
    case "play_media": {
      const action = str(a.action, "play");
      const nowPlaying = action === "skip" ? "Next track" : home.phone.nowPlaying ?? "Queue";
      return {
        home: withPhone(home, { playing: action !== "pause", nowPlaying }),
        message: `${action} ${nowPlaying}`,
      };
    }
    case "play_query": {
      const query = str(a.query);
      return {
        home: withPhone(home, { playing: true, nowPlaying: query }),
        message: `play ${query}`,
      };
    }
    case "start_navigation": {
      const destination = str(a.destination);
      const origin = str(a.origin);
      const waypoints = strList(a.waypoints);
      const mode = (str(a.mode, "driving") || "driving") as TravelMode;
      const avoid = strList(a.avoid) as AvoidFlag[];
      const optimize = a.optimize === true;
      const via = waypoints.length ? ` via ${waypoints.join(" → ")}` : "";
      const from = origin ? `${origin} → ` : "";
      return {
        home: withPhone(home, {
          navActive: true,
          navOrigin: origin,
          navDestination: destination,
          navWaypoints: waypoints,
          navMode: mode,
          navAvoid: avoid,
          navOptimize: optimize,
        }),
        message: `route ${mode} ${from}${destination}${via}${optimize ? " · optimized" : ""}`,
      };
    }
    case "stop_navigation":
      return {
        home: withPhone(home, {
          navActive: false,
          navOrigin: "",
          navDestination: null,
          navWaypoints: [],
          navMode: "driving",
          navAvoid: [],
          navOptimize: false,
        }),
        message: "navigation stopped",
      };
    case "create_note": {
      const title = str(a.title, "Note");
      const body = str(a.body, title);
      return {
        home: { ...home, notes: [{ title, body }, ...home.notes].slice(0, 12), revision: home.revision + 1 },
        message: `note ${title}`,
      };
    }
    case "get_weather": {
      const city = str(a.city, "New York");
      const temp = 18 + (city.length % 14);
      return { home: { ...home, revision: home.revision + 1 }, message: `${city} ${temp}°C` };
    }
    case "calculate": {
      const value = safeCalc(str(a.expression));
      return { home: { ...home, revision: home.revision + 1 }, message: `calc ${value}` };
    }
    case "open_settings":
      return { home: { ...home, revision: home.revision + 1 }, message: `settings ${str(a.panel, "settings")}` };
    case "open_app":
      return { home: { ...home, revision: home.revision + 1 }, message: `open ${str(a.label, str(a.app))}` };
    case "dial_phone":
      return { home: { ...home, revision: home.revision + 1 }, message: `dial ${str(a.number)}` };
    case "send_sms":
      return { home: { ...home, revision: home.revision + 1 }, message: `sms ${str(a.number)}` };
    case "send_email":
      return { home: { ...home, revision: home.revision + 1 }, message: `email ${str(a.to)}` };
    case "web_search":
      return { home: { ...home, revision: home.revision + 1 }, message: `search ${str(a.query)}` };
    case "maps_search":
      return { home: { ...home, revision: home.revision + 1 }, message: `maps ${str(a.query)}` };
    case "open_url":
      return { home: { ...home, revision: home.revision + 1 }, message: `url ${str(a.url)}` };
    case "add_event":
      return { home: { ...home, revision: home.revision + 1 }, message: `event ${str(a.title)}` };
    case "copy_text":
      return { home: { ...home, revision: home.revision + 1 }, message: "copied" };
    default:
      return { home, message: "unknown tool" };
  }
}
