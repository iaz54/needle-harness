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

function modeLetter(mode: TravelMode) {
  if (mode === "walking") return "w";
  if (mode === "bicycling") return "b";
  if (mode === "transit") return "r";
  return "d";
}

function avoidLetters(avoid: AvoidFlag[]) {
  return avoid.map((flag) => (flag === "tolls" ? "t" : flag === "highways" ? "h" : "f")).join("");
}

function optimizedUrl(origin: string, destination: string, waypoints: string[], route: RouteArgs) {
  const query = new URLSearchParams();
  query.set("api", "1");
  if (origin) query.set("origin", origin);
  query.set("destination", destination);
  query.set("travelmode", route.mode);
  if (route.avoid.length) query.set("avoid", route.avoid.join("|"));
  const stops = waypoints.map((stop) => encodeURIComponent(stop)).join("|");
  return `https://www.google.com/maps/dir/?${query.toString()}&waypoints=optimize:true|${stops}`;
}

function stopsOf(route: RouteArgs, places: SavedPlace[]) {
  const destination = expandPlace(route.destination, places);
  const origin = expandPlace(route.origin, places);
  const waypoints = route.waypoints
    .map((w) => expandPlace(w, places))
    .filter(Boolean)
    .slice(0, 9);
  return { origin, destination, waypoints };
}

export function buildMapsUrl(route: RouteArgs, places: SavedPlace[]) {
  const { origin, destination, waypoints } = stopsOf(route, places);
  if (route.optimize && waypoints.length) return optimizedUrl(origin, destination, waypoints, route);
  const ordered = [origin, ...waypoints, destination].filter(Boolean);
  if (ordered.length >= 2) {
    const path = ordered.map((stop) => encodeURIComponent(stop)).join("/");
    const query = new URLSearchParams();
    query.set("travelmode", route.mode);
    if (route.avoid.length) query.set("avoid", route.avoid.join("|"));
    return `https://www.google.com/maps/dir/${path}?${query.toString()}`;
  }
  const query = new URLSearchParams();
  query.set("api", "1");
  query.set("destination", destination);
  query.set("travelmode", route.mode);
  query.set("dir_action", "navigate");
  if (route.avoid.length) query.set("avoid", route.avoid.join("|"));
  return `https://www.google.com/maps/dir/?${query.toString()}`;
}

/** Phone Maps fills one destination box unless stops are chained with +to:. */
export function androidDirectionsUrl(route: RouteArgs, places: SavedPlace[]) {
  const { origin, destination, waypoints } = stopsOf(route, places);
  if (route.optimize && waypoints.length) return optimizedUrl(origin, destination, waypoints, route);
  const chain = [...waypoints, destination].filter(Boolean);
  if (chain.length <= 1 && !origin) {
    const avoid = avoidLetters(route.avoid);
    return `google.navigation:q=${encodeURIComponent(destination)}&mode=${modeLetter(route.mode)}${avoid ? `&avoid=${avoid}` : ""}`;
  }
  const daddr = chain.map((stop) => encodeURIComponent(stop)).join("+to:");
  const saddr = origin ? `&saddr=${encodeURIComponent(origin)}` : "";
  return `https://maps.google.com/maps?f=d${saddr}&daddr=${daddr}&dirflg=${avoidLetters(route.avoid)}${modeLetter(route.mode)}`;
}

export function mapsSearchUrl(query: string) {
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(query)}`;
}

export function androidMapsIntent(httpsUrl: string) {
  const u = new URL(httpsUrl);
  return `intent://${u.host}${u.pathname}${u.search}#Intent;scheme=https;package=com.google.android.apps.maps;end`;
}

export function emptyRoute(): RouteArgs {
  return { origin: "", destination: "", waypoints: [], mode: "driving", avoid: [], optimize: false };
}

export function routeReady(route: RouteArgs) {
  return route.destination.trim().length >= 2;
}
