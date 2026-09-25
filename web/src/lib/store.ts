import { create } from "zustand";
import { persist } from "zustand/middleware";
import { complete, type CompleteResult, type FunctionCall, type RouteArgs } from "./engine";
import { handoffFor, routeFromCall, type Handoff } from "./handoff";
import { applyCall, emptyHome, type HomeState } from "./home";
import { emptyRoute, type SavedPlace } from "./maps";

const PLACES: SavedPlace[] = [
  { label: "Home", query: "" },
  { label: "Work", query: "" },
  { label: "Airport", query: "" },
  { label: "Grocery", query: "" },
];

type LatchState = {
  home: HomeState;
  places: SavedPlace[];
  draft: RouteArgs;
  input: string;
  layer: number;
  last: CompleteResult | null;
  pending: CompleteResult | null;
  log: string[];
  handoffs: Handoff[];
  setInput: (value: string) => void;
  setLayer: (layer: number) => void;
  setDraft: (draft: RouteArgs) => void;
  updatePlace: (label: string, query: string) => void;
  run: (text?: string) => void;
  confirm: (yes: boolean) => void;
  reset: () => void;
  toggleLight: (id: string) => void;
  toggleFan: (id: string) => void;
  toggleLock: (id: string) => void;
  nudgeTemp: (delta: number) => void;
};

function commit(home: HomeState, calls: FunctionCall[], places: SavedPlace[], draft: RouteArgs) {
  let next = home;
  const messages: string[] = [];
  const handoffs: Handoff[] = [];
  let route = draft;
  for (const call of calls) {
    const applied = applyCall(next, call);
    next = applied.home;
    messages.push(applied.message);
    const handoff = handoffFor(call, places);
    if (handoff) handoffs.push(handoff);
    if (call.name === "start_navigation") route = routeFromCall(call);
    if (call.name === "stop_navigation") route = emptyRoute();
    if (call.name === "copy_text" && typeof call.arguments.text === "string" && typeof navigator !== "undefined") {
      void navigator.clipboard?.writeText(call.arguments.text).catch(() => undefined);
    }
  }
  return { home: next, messages, handoffs, draft: route };
}

export const useLatch = create<LatchState>()(
  persist(
    (set, get) => ({
      home: emptyHome(),
      places: PLACES,
      draft: emptyRoute(),
      input: "",
      layer: 20,
      last: null,
      pending: null,
      log: [],
      handoffs: [],
      setInput: (input) => set({ input }),
      setLayer: (layer) => set({ layer }),
      setDraft: (draft) => set({ draft }),
      updatePlace: (label, query) =>
        set({
          places: get().places.map((place) => (place.label === label ? { ...place, query } : place)),
        }),
      run: (text) => {
        const spoken = (text ?? get().input).trim();
        if (!spoken) return;
        const result = complete(spoken, get().layer);
        if (result.gate === "EXECUTE") {
          const applied = commit(get().home, result.functionCalls, get().places, get().draft);
          set({
            input: "",
            last: result,
            pending: null,
            home: applied.home,
            handoffs: applied.handoffs,
            draft: applied.draft,
            log: [`EXECUTE · ${applied.messages.join(" → ") || result.reasoning}`, ...get().log].slice(0, 8),
          });
          return;
        }
        if (result.gate === "CONFIRM") {
          set({ input: "", last: result, pending: result });
          return;
        }
        set({
          input: "",
          last: result,
          pending: null,
          log: [`REFUSE · ${result.reasoning}`, ...get().log].slice(0, 8),
        });
      },
      confirm: (yes) => {
        const pending = get().pending;
        if (!pending) return;
        if (!yes) {
          set({ pending: null, log: ["blocked", ...get().log].slice(0, 8) });
          return;
        }
        const calls = pending.functionCalls.length ? pending.functionCalls : pending.suppressedCalls;
        const applied = commit(get().home, calls, get().places, get().draft);
        set({
          pending: null,
          home: applied.home,
          handoffs: applied.handoffs,
          draft: applied.draft,
          log: [`EXECUTE · ${applied.messages.join(" → ")}`, ...get().log].slice(0, 8),
        });
      },
      reset: () =>
        set({
          home: emptyHome(),
          draft: emptyRoute(),
          input: "",
          last: null,
          pending: null,
          log: [],
          handoffs: [],
        }),
      toggleLight: (id) => {
        const room = get().home.rooms[id];
        if (!room) return;
        const applied = applyCall(get().home, {
          name: "set_lights",
          arguments: { room: id, on: !room.light },
        });
        set({ home: applied.home });
      },
      toggleFan: (id) => {
        const room = get().home.rooms[id];
        if (!room) return;
        const applied = applyCall(get().home, {
          name: "set_fan",
          arguments: { room: id, on: !room.fan },
        });
        set({ home: applied.home });
      },
      toggleLock: (id) => {
        const room = get().home.rooms[id];
        if (!room) return;
        const applied = applyCall(get().home, {
          name: "lock_door",
          arguments: { door: id, locked: !room.locked },
        });
        set({ home: applied.home });
      },
      nudgeTemp: (delta) => {
        const temperature = Math.min(32, Math.max(10, get().home.temperature + delta));
        const applied = applyCall(get().home, {
          name: "set_thermostat",
          arguments: { temperature },
        });
        set({ home: applied.home });
      },
    }),
    {
      name: "latch-needle",
      skipHydration: true,
      partialize: (state) => ({
        home: state.home,
        places: state.places,
        draft: state.draft,
        layer: state.layer,
        log: state.log,
      }),
    },
  ),
);
