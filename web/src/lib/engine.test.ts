import assert from "node:assert/strict";
import test from "node:test";
import { complete, parseRoute, splitClauses } from "./engine.ts";

test("single destination still routes", () => {
  const route = parseRoute("navigate to the airport");
  assert.equal(route?.destination, "Airport");
  assert.equal(route?.mode, "driving");
  assert.equal(route?.origin, "");
  assert.deepEqual(route?.waypoints, []);
});

test("full route with via, and, and avoid", () => {
  const text = "drive from home to JFK via a gas station and the pharmacy, avoid tolls";
  assert.equal(splitClauses(text).length, 1);
  const route = parseRoute(splitClauses(text)[0]!);
  assert.equal(route?.origin, "Home");
  assert.equal(route?.destination, "JFK");
  assert.deepEqual(route?.waypoints, ["gas station", "the pharmacy"]);
  assert.deepEqual(route?.avoid, ["tolls"]);
  assert.equal(route?.mode, "driving");
});

test("walk via one stop", () => {
  const route = parseRoute("walk from Washington Square to the Brooklyn Bridge via the High Line");
  assert.equal(route?.mode, "walking");
  assert.equal(route?.origin, "Washington Square");
  assert.equal(route?.destination, "the Brooklyn Bridge");
  assert.deepEqual(route?.waypoints, ["the High Line"]);
});

test("transit then-chain keeps order", () => {
  const text = "transit from Penn Station to the Met then Central Park";
  const route = parseRoute(splitClauses(text)[0]!);
  assert.equal(route?.mode, "transit");
  assert.equal(route?.origin, "Penn Station");
  assert.deepEqual(route?.waypoints, ["the Met"]);
  assert.equal(route?.destination, "Central Park");
});

test("comma itinerary", () => {
  const route = parseRoute(splitClauses("route: Home, Whole Foods, JFK")[0]!);
  assert.equal(route?.origin, "Home");
  assert.deepEqual(route?.waypoints, ["Whole Foods"]);
  assert.equal(route?.destination, "JFK");
});

test("house clauses stay separate", () => {
  const clauses = splitClauses("turn on the fan, set temperature to 10°, turn on bedroom light");
  assert.equal(clauses.length, 3);
  const result = complete("turn on the fan, set temperature to 10°, turn on bedroom light");
  assert.equal(result.gate, "EXECUTE");
  assert.deepEqual(
    result.functionCalls.map((c) => c.name),
    ["set_fan", "set_thermostat", "set_lights"],
  );
});

test("stop navigation does not start a route", () => {
  const result = complete("stop navigation");
  assert.equal(result.functionCalls[0]?.name, "stop_navigation");
});

test("android system tools", () => {
  const wifi = complete("open wifi settings and turn on do not disturb");
  assert.deepEqual(
    wifi.functionCalls.map((c) => c.name),
    ["open_settings", "set_dnd"],
  );
  assert.equal(wifi.functionCalls[0]?.arguments.panel, "wifi");
  const call = complete("call 311");
  assert.equal(call.functionCalls[0]?.name, "dial_phone");
  const sms = complete("text 9175550100 saying I'm on the way");
  assert.equal(sms.functionCalls[0]?.name, "send_sms");
  assert.match(String(sms.functionCalls[0]?.arguments.body), /on the way/);
  const near = complete("find late night pizza near me");
  assert.equal(near.functionCalls[0]?.name, "maps_search");
  const alarm = complete("set an alarm for 6:30am");
  assert.equal(alarm.functionCalls[0]?.arguments.time, "06:30");
  const app = complete("open spotify");
  assert.equal(app.functionCalls[0]?.name, "open_app");
});

test("unknown prose refuses", () => {
  const result = complete("the curtains look tired");
  assert.equal(result.gate, "REFUSE");
  assert.equal(result.functionCalls.length, 0);
});
