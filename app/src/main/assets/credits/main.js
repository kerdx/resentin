// main.js — the credits roll's orchestrator. Vanilla-JS equivalent of
// cicchetto's CreditsModal.tsx (Solid + CSS-Animation-phase introspection),
// simplified to a single requestAnimationFrame loop that drives the scroll
// position directly instead of restarting a CSS animation with a
// per-pass-measured duration. Same sequence, same rain/audio behavior;
// ponytail: dropped the "hold to read" touch-pause and the reading-speed-vs-
// content-height pacing model — a fixed px/s crawl, which is what cic's own
// pace amounts to for a set of typical length.

import { startCreditsArpeggio, creditsMovementName, MOVEMENT_COUNT } from "./creditsAudio.js";
import {
  CREDITS_COW,
  CREDITS_SPECIAL_THANKS,
  RESENTIN_THANKS,
  createProseDeck,
  CREDITS_HEART,
  CREDITS_FINALE_LINE,
  CREDITS_CLOSE_LABEL,
  CREDITS_MANIFESTO,
  CREDITS_MANIFESTO_ATTRIBUTION,
} from "./creditsText.js";
import { mountMatrixRain, CREDITS_RAIN_LOOK, CREDITS_RAIN_BURST_LOOK } from "./matrixRain.js";

const params = new URLSearchParams(location.search);
const RESENTIN_VERSION = params.get("version") ?? "";

const ROLL_SPEED_PX_S = 42; // cic's own roll settles around ~34-40px/s.
const INTERLUDE_MS = 3200; // the parked "pure rain" pause between passes.
const BLOCK_FADE_START = 0.9; // fraction of the travel where a block dissolves.

const $roll = document.getElementById("roll");
const $viewport = document.getElementById("viewport");
const $canvas = document.getElementById("rain-canvas");
const $movement = document.getElementById("movement-label");
const $muteBtn = document.getElementById("mute-btn");
const $closeBtn = document.getElementById("close-btn");

let muted = localStorage.getItem("credits-muted") === "1";
$muteBtn.setAttribute("aria-pressed", String(muted));

let arpeggio = null;
let movementIndex = 0;
let phase = "traveling"; // "traveling" | "interlude" | "ended"
let fadingBlock = null; // the DOM node currently mid-dissolve, or null
let travelY = 0; // px translated up from the start position, this pass
let travelDistance = 0; // this pass's total travel (viewport + roll height)
let lastFrameAt = 0;

const deck = createProseDeck();
let stage = "block"; // "block" -> "prose" -> "manifesto" -> "ended"

function closeCredits() {
  if (window.ResentinCredits?.close) {
    window.ResentinCredits.close();
    return;
  }
  // Fallback for opening this page outside the Android WebView bridge.
  document.body.classList.add("credits-closed");
}

$closeBtn.addEventListener("click", closeCredits);
$muteBtn.addEventListener("click", () => {
  muted = !muted;
  localStorage.setItem("credits-muted", muted ? "1" : "0");
  $muteBtn.setAttribute("aria-pressed", String(muted));
  arpeggio?.setMuted(muted);
});

function personRow(name, nick, commits) {
  const li = document.createElement("li");
  li.className = "credits-person";
  const label = nick ? `${nick} (${name})` : name;
  li.innerHTML = `<span class="credits-person-name">${escapeHtml(label)}</span><span class="credits-person-count">${commits}</span>`;
  return li;
}

function escapeHtml(s) {
  return s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}

function thanksList(entries) {
  const ul = document.createElement("ul");
  ul.className = "credits-thanks";
  for (const { who, why } of entries) {
    const li = document.createElement("li");
    li.innerHTML = `<span class="credits-thanks-who">${escapeHtml(who)}</span> — <span class="credits-thanks-why">${escapeHtml(why)}</span>`;
    ul.appendChild(li);
  }
  return ul;
}

/** Resentin's own block, shown FIRST — title, version, the contributor roll
 * baked in by the resentin-credits GitHub Actions step (credits-data.js). */
function buildResentinBlock() {
  const wrap = document.createElement("div");
  wrap.innerHTML = `
    <h1 class="credits-title">RESENTIN</h1>
    ${RESENTIN_VERSION ? `<p class="credits-version">v${escapeHtml(RESENTIN_VERSION)}</p>` : ""}
    <h2 class="credits-heading">contributors</h2>
  `;
  const list = document.createElement("ul");
  list.className = "credits-list";
  const credits = window.RESENTIN_CREDITS;
  const contributors = Array.isArray(credits?.contributors) ? credits.contributors : [];
  if (contributors.length === 0) {
    const p = document.createElement("p");
    p.className = "credits-empty";
    p.textContent = "(contributor roll unavailable in this build)";
    wrap.appendChild(p);
  } else {
    for (const c of contributors) list.appendChild(personRow(c.name, c.nick, c.commits));
    wrap.appendChild(list);
  }
  wrap.appendChild(thanksList(RESENTIN_THANKS));
  return wrap;
}

/** grappa-irc's own block — the cow and the special thanks, ported verbatim
 * from cicchetto. No contributor roll here: that is grappa-irc's OWN repo
 * history, which this build has no access to — showing a roll frozen at
 * whatever day this page was last copied over would only rot silently. */
function buildGrappaBlock() {
  const wrap = document.createElement("div");
  wrap.innerHTML = `
    <h2 class="credits-heading">and the server it talks to</h2>
    <h1 class="credits-title">GRAPPA-IRC</h1>
    <pre class="credits-cow">${escapeHtml(CREDITS_COW)}</pre>
  `;
  wrap.appendChild(thanksList(CREDITS_SPECIAL_THANKS));
  return wrap;
}

function buildProseSet(set) {
  const wrap = document.createElement("div");
  wrap.className = "credits-prose";
  const h = document.createElement("h3");
  h.className = "credits-prose-title";
  h.textContent = set.title;
  wrap.appendChild(h);
  for (const para of set.paragraphs) {
    const p = document.createElement("p");
    p.textContent = para;
    wrap.appendChild(p);
  }
  return wrap;
}

function buildManifesto() {
  const wrap = document.createElement("div");
  wrap.className = "credits-manifesto";
  wrap.innerHTML = `
    <p class="credits-manifesto-text">${escapeHtml(CREDITS_MANIFESTO)}</p>
    <p class="credits-manifesto-attribution">${escapeHtml(CREDITS_MANIFESTO_ATTRIBUTION)}</p>
  `;
  return wrap;
}

function buildFinale() {
  const wrap = document.createElement("div");
  wrap.innerHTML = `
    <div class="credits-heart">${CREDITS_HEART}</div>
    <p class="credits-finale-line">${escapeHtml(CREDITS_FINALE_LINE)}</p>
  `;
  const btn = document.createElement("button");
  btn.type = "button";
  btn.className = "credits-close-cta";
  btn.textContent = CREDITS_CLOSE_LABEL;
  btn.addEventListener("click", closeCredits);
  wrap.appendChild(btn);
  return wrap;
}

/**
 * Builds the content of the NEXT pass and advances `stage` for the pass
 * after it. Returns `null` once there is nothing left to travel with — the
 * caller then shows the static ending instead of starting another pass.
 */
function buildNextScreen() {
  if (stage === "block") {
    const block = document.createElement("div");
    block.className = "credits-block";
    block.appendChild(buildResentinBlock());
    block.appendChild(buildGrappaBlock());
    fadingBlock = block;
    stage = "prose";
    return block;
  }

  if (stage === "prose") {
    fadingBlock = null;
    const set = deck.draw();
    movementIndex += 1;
    if ($movement) $movement.textContent = creditsMovementName(movementIndex % MOVEMENT_COUNT);
    if (deck.exhausted()) stage = "manifesto";
    return buildProseSet(set);
  }

  if (stage === "manifesto") {
    fadingBlock = null;
    stage = "ended";
    arpeggio?.setPiece("manifesto");
    if ($movement) $movement.textContent = "";
    return buildManifesto();
  }

  return null; // stage === "ended": nothing more to travel with
}

function enterEnded() {
  phase = "ended";
  $viewport.classList.add("ended");
  $roll.classList.add("ended");
  $roll.style.transform = "none";
  $roll.innerHTML = "";
  $roll.appendChild(buildFinale());
  arpeggio?.setPiece("cadence");
}

function startPass() {
  const content = buildNextScreen();
  if (content === null) {
    enterEnded();
    return;
  }
  $roll.innerHTML = "";
  $roll.appendChild(content);
  travelY = 0;
  const vh = window.innerHeight;
  const rollH = Math.max($roll.scrollHeight, 1);
  travelDistance = vh + rollH;
  $roll.style.transform = `translateY(${vh}px)`;
  phase = "traveling";
}

function tick(now) {
  requestAnimationFrame(tick);
  if (phase !== "traveling") return;
  if (lastFrameAt === 0) lastFrameAt = now;
  const dtMs = now - lastFrameAt;
  lastFrameAt = now;

  const vh = window.innerHeight;
  travelY += (ROLL_SPEED_PX_S * dtMs) / 1000;
  $roll.style.transform = `translateY(${vh - travelY}px)`;

  if (fadingBlock !== null) {
    const progress = travelY / travelDistance;
    if (progress >= BLOCK_FADE_START) {
      const fadeProgress = Math.min(1, (progress - BLOCK_FADE_START) / (1 - BLOCK_FADE_START));
      fadingBlock.style.opacity = String(1 - fadeProgress);
    }
  }

  if (travelY >= travelDistance) {
    phase = "interlude";
    setTimeout(startPass, INTERLUDE_MS);
  }
}

function rainLook() {
  if (phase === "interlude" || phase === "ended") return CREDITS_RAIN_BURST_LOOK;
  if (fadingBlock !== null) {
    const progress = travelDistance > 0 ? travelY / travelDistance : 0;
    if (progress >= BLOCK_FADE_START) return CREDITS_RAIN_BURST_LOOK;
  }
  return CREDITS_RAIN_LOOK;
}

mountMatrixRain($canvas, rainLook);

if (!window.matchMedia?.("(prefers-reduced-motion: reduce)")?.matches) {
  requestAnimationFrame(tick);
  startPass();
} else {
  // Reduced motion: render the whole sequence as one static, scrollable
  // column instead of animating any of it.
  $viewport.classList.add("ended");
  $roll.classList.add("ended");
  const block = document.createElement("div");
  block.className = "credits-block";
  block.appendChild(buildResentinBlock());
  block.appendChild(buildGrappaBlock());
  $roll.appendChild(block);
  let set = deck.draw();
  while (set !== null && !deck.exhausted()) {
    $roll.appendChild(buildProseSet(set));
    set = deck.draw();
  }
  if (set !== null) $roll.appendChild(buildProseSet(set));
  $roll.appendChild(buildManifesto());
  $roll.appendChild(buildFinale());
}

// Audio — WebView autoplay is allowed because the Android host disables the
// user-gesture requirement (this page only ever opens from an explicit tap
// on /credits). A refused AudioContext leaves the roll silent, not broken.
try {
  const Ctx = window.AudioContext || window.webkitAudioContext;
  if (Ctx) {
    const ctx = new Ctx();
    arpeggio = startCreditsArpeggio(ctx, muted, () => movementIndex % MOVEMENT_COUNT);
  }
} catch {
  // No audio in this build target; the visual roll still runs.
}
