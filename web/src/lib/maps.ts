import type { AvoidFlag, RouteArgs, TravelMode } from "./engine";

export const TRAVEL_MODES: { id: TravelMode; label: string }[] = [
  { id: "driving", label: "Drive" },
  { id: "walking", label: "Walk" },
  { id: "bicycling", label: "Bike" },
  { id: "transit", label: "Transit" },
];

export const AVOID_FLAGS: { id: AvoidFlag; label: string }[] = [
  { id: "tolls", label: "Tolls" },
  { id: "highways", label: "Highways" },
  { id: "ferries", label: "Ferries" },
];

export type SavedPlace = { label: string; query: string };

export function expandPlace(label: string, places: SavedPlace[]) {
  const trimmed = label.trim();
  if (!trimmed) return "";
  const hit = places.find((p) => p.label.toLowerCase() === trimmed.toLowerCase() && p.query.trim());
  return hit ? hit.query.trim() : trimmed;
}

export function buildMapsUrl(route: RouteArgs, places: SavedPlace[]) {
  const destination = expandPlace(route.destination, places);
  const origin = expandPlace(route.origin, places);
  const waypoints = route.waypoints
    .map((w) => expandPlace(w, places))
    .filter(Boolean)
    .slice(0, 9);
  const parts = [
    "api=1",
    `destination=${encodeURIComponent(destination)}`,
    `travelmode=${route.mode}`,
    "dir_action=navigate",
  ];
  if (origin) parts.push(`origin=${encodeURIComponent(origin)}`);
  if (waypoints.length) {
    parts.push(`waypoints=${waypoints.map((w) => encodeURIComponent(w)).join("|")}`);
  }
  if (route.avoid.length) parts.push(`avoid=${route.avoid.join("|")}`);
  return `https://www.google.com/maps/dir/?${parts.join("&")}`;
}

export function mapsSearchUrl(query: string) {
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(query)}`;
}

export function androidMapsIntent(httpsUrl: string) {
  const u = new URL(httpsUrl);
  return `intent://${u.host}${u.pathname}${u.search}#Intent;scheme=https;package=com.google.android.apps.maps;end`;
}

export function emptyRoute(): RouteArgs {
  return { origin: "", destination: "", waypoints: [], mode: "driving", avoid: [] };
}

export function routeReady(route: RouteArgs) {
  return route.destination.trim().length >= 2;
}
