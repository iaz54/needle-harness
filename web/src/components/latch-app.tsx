import { useEffect, useState, type ReactNode } from "react";
import {
  ArrowDown,
  ArrowUp,
  Fan,
  Lightbulb,
  Lock,
  LockOpen,
  MapPin,
  Minus,
  Navigation,
  Plus,
  Trash2,
} from "lucide-react";
import type { RouteArgs } from "../lib/engine";
import type { Handoff } from "../lib/handoff";
import { AVOID_FLAGS, buildMapsUrl, routeReady, TRAVEL_MODES } from "../lib/maps";
import { useLatch } from "../lib/store";
import { cn } from "../lib/cn";

const SAMPLES = [
  "drive from home to JFK via a gas station and the pharmacy, avoid tolls",
  "walk from Washington Square to the Brooklyn Bridge via the High Line",
  "transit from Penn Station to the Met then Central Park",
  "take me home",
  "open wifi settings and turn on do not disturb",
  "text 9175550100 saying I'm on the way",
  "find late night pizza near me",
  "turn on the fan, set temperature to 20, turn on bedroom light",
];

const ROOMS = ["living", "kitchen", "bedroom", "office", "bathroom", "garage"];

const SETTINGS = [
  ["Wi-Fi", "open wifi settings"],
  ["Bluetooth", "open bluetooth settings"],
  ["Location", "open location settings"],
  ["Sound", "open sound settings"],
  ["Display", "open display settings"],
  ["Airplane", "open airplane settings"],
] as const;

export function LatchApp() {
  const input = useLatch((s) => s.input);
  const setInput = useLatch((s) => s.setInput);
  const run = useLatch((s) => s.run);
  const layer = useLatch((s) => s.layer);
  const setLayer = useLatch((s) => s.setLayer);
  const home = useLatch((s) => s.home);
  const draft = useLatch((s) => s.draft);
  const setDraft = useLatch((s) => s.setDraft);
  const places = useLatch((s) => s.places);
  const updatePlace = useLatch((s) => s.updatePlace);
  const last = useLatch((s) => s.last);
  const pending = useLatch((s) => s.pending);
  const confirm = useLatch((s) => s.confirm);
  const log = useLatch((s) => s.log);
  const handoffs = useLatch((s) => s.handoffs);
  const reset = useLatch((s) => s.reset);
  const toggleLight = useLatch((s) => s.toggleLight);
  const toggleFan = useLatch((s) => s.toggleFan);
  const toggleLock = useLatch((s) => s.toggleLock);
  const nudgeTemp = useLatch((s) => s.nudgeTemp);
  const [copied, setCopied] = useState(false);
  const [dial, setDial] = useState("");
  const [sms, setSms] = useState("");
  const [near, setNear] = useState("");

  useEffect(() => {
    void useLatch.persist.rehydrate();
  }, []);

  const url = routeReady(draft) ? buildMapsUrl(draft, places) : "";
  const ram = 14 + Math.floor(layer * 0.7);

  function patch(partial: Partial<RouteArgs>) {
    setDraft({ ...draft, ...partial });
  }

  function moveStop(index: number, dir: -1 | 1) {
    const next = [...draft.waypoints];
    const target = index + dir;
    if (target < 0 || target >= next.length) return;
    const current = next[index];
    const swap = next[target];
    if (current == null || swap == null) return;
    next[index] = swap;
    next[target] = current;
    patch({ waypoints: next });
  }

  async function copyLink() {
    if (!url) return;
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1600);
    } catch {
      setCopied(false);
    }
  }

  return (
    <main className="min-h-screen bg-bg text-fg">
      <div className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-4 py-6 sm:px-6 sm:py-8">
        <header className="flex flex-col gap-3">
          <p className="font-mono text-xs tracking-widest text-muted">NEEDLE 3 · JEV HARNESS</p>
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div>
              <h1 className="text-4xl font-medium tracking-tight">Latch</h1>
              <p className="mt-2 max-w-xl text-sm leading-normal text-muted">
                Plan a full Google Maps route, then hand the phone the rest: dial, text, settings,
                alarms. Tools only — if nothing matches, Latch refuses.
              </p>
            </div>
            <p className="font-mono text-xs text-faint">
              {layer}L · {ram} MB
            </p>
          </div>
        </header>

        <form
          className="flex flex-col gap-3 sm:flex-row"
          onSubmit={(event) => {
            event.preventDefault();
            run();
          }}
        >
          <label className="sr-only" htmlFor="command">
            Command
          </label>
          <input
            id="command"
            value={input}
            onChange={(event) => setInput(event.target.value)}
            placeholder="drive from home to JFK via a gas station, avoid tolls"
            className="h-12 min-w-0 flex-1 rounded-lg border border-line bg-surface px-4 text-base text-fg placeholder:text-faint"
          />
          <div className="flex gap-2">
            <button
              type="submit"
              className="h-12 min-w-24 rounded-lg bg-paper px-5 text-sm font-medium text-ink transition-opacity duration-150 hover:opacity-90"
            >
              Run
            </button>
            <button
              type="button"
              onClick={reset}
              className="h-12 rounded-lg border border-line px-4 text-sm text-muted"
            >
              Reset
            </button>
          </div>
        </form>

        <div className="-mx-4 flex gap-2 overflow-x-auto px-4 pb-1 sm:mx-0 sm:flex-wrap sm:px-0">
          {SAMPLES.map((sample) => (
            <button
              key={sample}
              type="button"
              onClick={() => run(sample)}
              className="h-11 shrink-0 rounded-full border border-line px-4 text-left text-xs text-muted"
            >
              {sample}
            </button>
          ))}
        </div>

        <label className="flex flex-col gap-2">
          <span className="font-mono text-xs text-faint">Model rung</span>
          <input
            type="range"
            min={2}
            max={20}
            step={2}
            value={layer}
            aria-label="Model rung"
            onChange={(event) => setLayer(Number(event.target.value))}
            className="accent-execute"
          />
        </label>

        <div className="grid items-start gap-6 lg:grid-cols-12">
          <section className="flex flex-col gap-4 rounded-xl border border-line bg-surface p-4 lg:col-span-7">
            <div className="flex items-start justify-between gap-3">
              <div>
                <h2 className="text-lg font-medium">Full route</h2>
                <p className="mt-1 text-sm text-muted">
                  Blank origin uses your current location. Up to nine stops. Maps starts navigation on
                  the whole path.
                </p>
              </div>
              <Navigation className="mt-1 size-5 shrink-0 text-execute" aria-hidden="true" />
            </div>

            <div role="radiogroup" aria-label="Travel mode" className="grid grid-cols-4 gap-1 rounded-xl bg-bg p-2">
              {TRAVEL_MODES.map((mode) => {
                const on = draft.mode === mode.id;
                return (
                  <button
                    key={mode.id}
                    type="button"
                    role="radio"
                    aria-checked={on}
                    onClick={() => patch({ mode: mode.id })}
                    className={cn(
                      "h-11 rounded-lg text-sm",
                      on ? "bg-paper font-medium text-ink" : "text-muted",
                    )}
                  >
                    {mode.label}
                  </button>
                );
              })}
            </div>

            <div className="flex flex-wrap gap-2">
              {AVOID_FLAGS.map((flag) => {
                const on = draft.avoid.includes(flag.id);
                return (
                  <button
                    key={flag.id}
                    type="button"
                    aria-pressed={on}
                    onClick={() =>
                      patch({
                        avoid: on
                          ? draft.avoid.filter((item) => item !== flag.id)
                          : [...draft.avoid, flag.id],
                      })
                    }
                    className={cn(
                      "h-11 rounded-full border px-4 text-sm",
                      on ? "border-execute text-execute" : "border-line text-muted",
                    )}
                  >
                    Avoid {flag.label.toLowerCase()}
                  </button>
                );
              })}
            </div>

            <PlaceField
              label="Origin"
              value={draft.origin}
              placeholder="Current location"
              onChange={(origin) => patch({ origin })}
            />

            <div className="flex flex-col gap-2">
              <div className="flex items-center justify-between">
                <p className="text-sm text-muted">Stops</p>
                <button
                  type="button"
                  onClick={() => patch({ waypoints: [...draft.waypoints, ""] })}
                  disabled={draft.waypoints.length >= 9}
                  className="inline-flex h-11 items-center gap-1 rounded-lg px-2 text-sm text-fg disabled:opacity-40"
                >
                  <Plus className="size-4" aria-hidden="true" />
                  Add stop
                </button>
              </div>
              {draft.waypoints.length === 0 ? (
                <p className="rounded-lg border border-dashed border-line px-3 py-4 text-sm text-faint">
                  No stops yet. Add one, or say “via the pharmacy”.
                </p>
              ) : (
                draft.waypoints.map((stop, index) => (
                  <div key={`${index}-${draft.waypoints.length}`} className="flex gap-2">
                    <input
                      aria-label={`Stop ${index + 1}`}
                      value={stop}
                      onChange={(event) => {
                        const waypoints = [...draft.waypoints];
                        waypoints[index] = event.target.value;
                        patch({ waypoints });
                      }}
                      placeholder={`Stop ${index + 1}`}
                      className="h-11 min-w-0 flex-1 rounded-md border border-line bg-bg px-3 text-sm"
                    />
                    <IconButton label="Move stop up" onClick={() => moveStop(index, -1)} disabled={index === 0}>
                      <ArrowUp className="size-4" />
                    </IconButton>
                    <IconButton
                      label="Move stop down"
                      onClick={() => moveStop(index, 1)}
                      disabled={index === draft.waypoints.length - 1}
                    >
                      <ArrowDown className="size-4" />
                    </IconButton>
                    <IconButton
                      label="Remove stop"
                      onClick={() => patch({ waypoints: draft.waypoints.filter((_, i) => i !== index) })}
                    >
                      <Trash2 className="size-4" />
                    </IconButton>
                  </div>
                ))
              )}
            </div>

            <PlaceField
              label="Destination"
              value={draft.destination}
              placeholder="Where to"
              onChange={(destination) => patch({ destination })}
            />

            <Itinerary route={draft} />

            <div className="flex flex-col gap-2 sm:flex-row">
              <a
                href={url || undefined}
                target="_blank"
                rel="noreferrer"
                aria-disabled={!url}
                className={cn(
                  "inline-flex h-12 flex-1 items-center justify-center rounded-lg bg-execute px-4 text-sm font-medium text-ink",
                  !url && "pointer-events-none opacity-40",
                )}
              >
                Open full route in Google Maps
              </a>
              <button
                type="button"
                onClick={() => void copyLink()}
                disabled={!url}
                className="h-12 rounded-lg border border-line px-4 text-sm text-fg disabled:opacity-40"
              >
                {copied ? "Copied" : "Copy link"}
              </button>
            </div>
            {draft.waypoints.length > 9 ? (
              <p className="text-sm text-confirm">Maps keeps the first nine stops.</p>
            ) : null}

            <div className="grid gap-3 sm:grid-cols-2">
              {places.map((place) => (
                <label key={place.label} className="flex flex-col gap-1">
                  <span className="font-mono text-xs text-faint">{place.label}</span>
                  <input
                    value={place.query}
                    onChange={(event) => updatePlace(place.label, event.target.value)}
                    placeholder={place.label === "Airport" ? "JFK Airport" : "Address Maps should use"}
                    className="h-11 rounded-md border border-line bg-bg px-3 text-sm placeholder:text-faint"
                  />
                </label>
              ))}
            </div>
            <p className="text-xs text-faint">
              Empty nicknames pass the name through. On the phone, Google Maps still resolves saved Home
              and Work.
            </p>
          </section>

          <div className="flex flex-col gap-6 lg:col-span-5">
            {last ? <ResultCard /> : null}
            {pending ? (
              <section className="flex flex-col gap-3 rounded-xl border border-confirm bg-surface p-4">
                <p className="font-mono text-xs tracking-widest text-confirm">CONFIRM</p>
                <p className="text-base">Needle is not sure enough to run this alone.</p>
                {(pending.functionCalls.length ? pending.functionCalls : pending.suppressedCalls).map(
                  (call, index) => (
                    <p key={`${call.name}-${index}`} className="font-mono text-xs text-muted">
                      {call.name} {JSON.stringify(call.arguments)}
                    </p>
                  ),
                )}
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => confirm(false)}
                    className="h-11 rounded-lg border border-line px-4 text-sm text-muted"
                  >
                    Block
                  </button>
                  <button
                    type="button"
                    onClick={() => confirm(true)}
                    className="h-11 rounded-lg bg-execute px-4 text-sm font-medium text-ink"
                  >
                    Execute
                  </button>
                </div>
              </section>
            ) : null}

            <HandoffList items={handoffs} />

            <section className="flex flex-col gap-4 rounded-xl border border-line bg-surface p-4">
              <h2 className="text-lg font-medium">Phone</h2>
              <form
                className="flex gap-2"
                onSubmit={(event) => {
                  event.preventDefault();
                  if (dial.trim()) run(`call ${dial.trim()}`);
                }}
              >
                <input
                  value={dial}
                  onChange={(event) => setDial(event.target.value)}
                  inputMode="tel"
                  placeholder="Call a number"
                  aria-label="Number to call"
                  className="h-11 min-w-0 flex-1 rounded-md border border-line bg-bg px-3 text-sm"
                />
                <button type="submit" className="h-11 rounded-lg bg-paper px-4 text-sm font-medium text-ink">
                  Dial
                </button>
              </form>
              <form
                className="flex gap-2"
                onSubmit={(event) => {
                  event.preventDefault();
                  if (sms.trim()) run(`text ${sms.trim()}`);
                }}
              >
                <input
                  value={sms}
                  onChange={(event) => setSms(event.target.value)}
                  placeholder="Text 9175550100 saying on my way"
                  aria-label="Text message"
                  className="h-11 min-w-0 flex-1 rounded-md border border-line bg-bg px-3 text-sm"
                />
                <button type="submit" className="h-11 rounded-lg border border-line px-4 text-sm">
                  Text
                </button>
              </form>
              <form
                className="flex gap-2"
                onSubmit={(event) => {
                  event.preventDefault();
                  if (near.trim()) run(`find ${near.trim()} near me`);
                }}
              >
                <input
                  value={near}
                  onChange={(event) => setNear(event.target.value)}
                  placeholder="Find pizza near me"
                  aria-label="Maps search"
                  className="h-11 min-w-0 flex-1 rounded-md border border-line bg-bg px-3 text-sm"
                />
                <button type="submit" className="h-11 rounded-lg border border-line px-4 text-sm">
                  Maps
                </button>
              </form>
              <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
                {SETTINGS.map(([label, phrase]) => (
                  <button
                    key={label}
                    type="button"
                    onClick={() => run(phrase)}
                    className="h-11 rounded-lg border border-line text-sm text-muted"
                  >
                    {label}
                  </button>
                ))}
              </div>
              <p className="font-mono text-xs leading-relaxed text-faint">
                vol {home.phone.volume} · screen {home.phone.brightness} · wifi {home.phone.wifi ? "on" : "off"} ·
                bt {home.phone.bluetooth ? "on" : "off"} · dnd {home.phone.dnd ? "on" : "off"} · torch{" "}
                {home.phone.torch ? "on" : "off"}
                {home.phone.alarm ? ` · alarm ${home.phone.alarm}` : ""}
                {home.phone.timerMin ? ` · timer ${home.phone.timerMin}m` : ""}
              </p>
            </section>

            <section className="flex flex-col gap-3 rounded-xl border border-line bg-surface p-4">
              <div className="flex items-center justify-between gap-3">
                <h2 className="text-lg font-medium">House</h2>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    aria-label="Cooler"
                    onClick={() => nudgeTemp(-1)}
                    className="inline-flex size-11 items-center justify-center rounded-lg border border-line"
                  >
                    <Minus className="size-4" />
                  </button>
                  <p className="min-w-16 text-center font-mono text-sm text-execute">
                    {home.temperature}°C
                  </p>
                  <button
                    type="button"
                    aria-label="Warmer"
                    onClick={() => nudgeTemp(1)}
                    className="inline-flex size-11 items-center justify-center rounded-lg border border-line"
                  >
                    <Plus className="size-4" />
                  </button>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-2">
                {ROOMS.map((id) => {
                  const room = home.rooms[id];
                  if (!room) return null;
                  return (
                    <article
                      key={id}
                      className={cn(
                        "flex flex-col gap-3 rounded-lg border bg-bg p-3",
                        room.light ? "border-paper/40" : "border-line",
                      )}
                    >
                      <div>
                        <h3 className="text-sm font-medium">{room.label}</h3>
                        <p className="mt-1 font-mono text-xs text-faint">
                          {room.light ? `${room.brightness}%` : "dark"} · fan {room.fan ? `x${room.fanSpeed}` : "off"}
                        </p>
                      </div>
                      <div className="flex gap-2">
                        <IconButton label={`${room.label} light`} onClick={() => toggleLight(id)} pressed={room.light}>
                          <Lightbulb className="size-4" />
                        </IconButton>
                        <IconButton label={`${room.label} fan`} onClick={() => toggleFan(id)} pressed={room.fan}>
                          <Fan className="size-4" />
                        </IconButton>
                        <IconButton label={`${room.label} lock`} onClick={() => toggleLock(id)} pressed={room.locked}>
                          {room.locked ? <Lock className="size-4" /> : <LockOpen className="size-4" />}
                        </IconButton>
                      </div>
                    </article>
                  );
                })}
              </div>
              {home.notes.slice(0, 2).map((note) => (
                <p key={note.title} className="text-sm text-muted">
                  <span className="text-fg">{note.title}. </span>
                  {note.body}
                </p>
              ))}
            </section>

            {log.length ? (
              <section className="flex flex-col gap-2">
                <h2 className="font-mono text-xs tracking-widest text-faint">LOG</h2>
                {log.map((line, index) => (
                  <p key={`${line}-${index}`} className="font-mono text-xs leading-relaxed text-muted">
                    {line}
                  </p>
                ))}
              </section>
            ) : null}
          </div>
        </div>
      </div>
    </main>
  );
}

function PlaceField({
  label,
  value,
  placeholder,
  onChange,
}: {
  label: string;
  value: string;
  placeholder: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className="flex flex-col gap-1">
      <span className="text-sm text-muted">{label}</span>
      <input
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        className="h-11 rounded-md border border-line bg-bg px-3 text-sm placeholder:text-faint"
      />
    </label>
  );
}

function Itinerary({ route }: { route: RouteArgs }) {
  const stops = [
    route.origin.trim() || "Current location",
    ...route.waypoints.map((stop) => stop.trim()).filter(Boolean),
    route.destination.trim() || "Destination",
  ];
  return (
    <ol className="flex flex-col">
      {stops.map((stop, index) => (
        <li key={`${stop}-${index}`} className="flex gap-3">
          <div className="flex flex-col items-center">
            <MapPin className={cn("size-4", index === stops.length - 1 ? "text-execute" : "text-faint")} />
            {index < stops.length - 1 ? <span className="w-px flex-1 bg-line" /> : null}
          </div>
          <p className={cn("pb-3 text-sm", index === stops.length - 1 ? "text-fg" : "text-muted")}>{stop}</p>
        </li>
      ))}
    </ol>
  );
}

function IconButton({
  label,
  onClick,
  disabled,
  pressed,
  children,
}: {
  label: string;
  onClick: () => void;
  disabled?: boolean;
  pressed?: boolean;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      aria-pressed={pressed}
      disabled={disabled}
      onClick={onClick}
      className={cn(
        "inline-flex size-11 items-center justify-center rounded-md border border-line text-fg disabled:opacity-30",
        pressed && "border-execute text-execute",
      )}
    >
      {children}
    </button>
  );
}

function ResultCard() {
  const last = useLatch((s) => s.last);
  if (!last) return null;
  const tone =
    last.gate === "EXECUTE" ? "text-execute" : last.gate === "CONFIRM" ? "text-confirm" : "text-refuse";
  const pct = last.confidence == null ? 0 : Math.round(last.confidence * 100);
  return (
    <section className="flex flex-col gap-2 rounded-xl border border-line bg-surface p-4">
      <p className={cn("font-mono text-xs", tone)}>
        {last.gate} · {pct}% · {last.latencyMs}ms · {last.layer}L
      </p>
      <p className="text-sm text-muted">{last.reasoning}</p>
    </section>
  );
}

function HandoffList({ items }: { items: Handoff[] }) {
  if (!items.length) return null;
  return (
    <section className="flex flex-col gap-2">
      {items.map((item) => (
        <article key={`${item.title}-${item.detail}`} className="flex flex-col gap-2 rounded-xl border border-line bg-surface p-4">
          <div>
            <h3 className="text-sm font-medium">{item.title}</h3>
            <p className="mt-1 text-sm text-muted">{item.detail}</p>
          </div>
          <div className="flex flex-col gap-2 sm:flex-row">
            <a
              href={item.href}
              target={item.kind === "web" ? "_blank" : undefined}
              rel="noreferrer"
              className="inline-flex h-11 items-center justify-center rounded-lg bg-paper px-4 text-sm font-medium text-ink"
            >
              {item.kind === "android" ? "Open on Android" : "Open"}
            </a>
            {item.androidHref !== item.href ? (
              <a
                href={item.androidHref}
                className="inline-flex h-11 items-center justify-center rounded-lg border border-line px-4 text-sm text-muted"
              >
                Android intent
              </a>
            ) : null}
          </div>
        </article>
      ))}
    </section>
  );
}
