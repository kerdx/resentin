// creditsAudio.js — Resentin's port of grappa-irc/cicchetto's credits synth
// (cicchetto/src/lib/creditsAudio.ts). A synthesised chiptune, zero audio
// assets: the whole point (per cic's own header) is that Star Wars/Mario are
// copyrighted and grappa ships this as a public PWA + .deb, so the roll's
// soundtrack is WebAudio oscillators, note for note the same score cic plays.
//
// This is a de-typed, comment-trimmed transliteration of the TypeScript —
// the musical DATA (movements, gain budget, timings) and the scheduling
// logic are unchanged. See the original for the full design history.

const SEMITONE = {
  C: 0, "C#": 1, D: 2, "D#": 3, E: 4, F: 5,
  "F#": 6, G: 7, "G#": 8, A: 9, "A#": 10, B: 11,
};

function midiOf(note) {
  const octave = Number(note.slice(-1));
  const pitchClass = note.slice(0, -1);
  return (octave + 1) * 12 + SEMITONE[pitchClass];
}

function hzOfMidi(midi) {
  return 440 * 2 ** ((midi - 69) / 12);
}

const PITCH_CLASS_COUNT = 12;

function pitchClassOf(midi) {
  return ((midi % PITCH_CLASS_COUNT) + PITCH_CLASS_COUNT) % PITCH_CLASS_COUNT;
}

/** The nearest note BELOW `midi` that belongs to `chord`, or null. */
function chordToneBelow(midi, chord) {
  const classes = chord.map((name) => SEMITONE[name]);
  for (let candidate = midi - 1; candidate >= midi - PITCH_CLASS_COUNT; candidate -= 1) {
    if (classes.includes(pitchClassOf(candidate))) return candidate;
  }
  return null;
}

/** The chord spelled upward from root in octave: root, third, fifth, root+8ve. */
function arpeggioFrom(chord, octave) {
  const notes = [midiOf(`${chord[0]}${octave}`)];
  for (let i = 1; i <= chord.length; i += 1) {
    const wanted = SEMITONE[chord[i % chord.length]];
    let next = (notes[notes.length - 1] ?? 0) + 1;
    while (pitchClassOf(next) !== wanted) next += 1;
    notes.push(next);
  }
  return notes;
}

// ---------------------------------------------------------------------------
// The score — six movements of eight bars, all in A minor's orbit. Bars 1-4
// state, bars 5-8 answer. Movement one is cic's original #1916 phrase, note
// for note; the rest turn the suite over on each pass of the roll.
// ---------------------------------------------------------------------------

const MOVEMENTS = [
  {
    name: "opening",
    duty: 0.5, second: "none", secondDuty: 0.25,
    drums: ["hat", "hat", "snare", "hat", "hat", "hat", "snare", "hat"],
    bars: [
      { lead: ["A4", "C5", "E5", "A5", "E5", "C5", "A4", "B4"], bass: ["A2", "A2", "E3", "A2"], chord: ["A", "C", "E"] },
      { lead: ["F4", "A4", "C5", "F5", "C5", "A4", "F4", "G4"], bass: ["F2", "F2", "C3", "F2"], chord: ["F", "A", "C"] },
      { lead: ["E4", "G4", "C5", "E5", "C5", "G4", "E4", "F4"], bass: ["C3", "C3", "G2", "C3"], chord: ["C", "E", "G"] },
      { lead: ["D4", "G4", "B4", "D5", "B4", "G4", "D4", "E4"], bass: ["G2", "G2", "D3", "G2"], chord: ["G", "B", "D"] },
      { lead: ["A5", "E5", "C5", "A4", "C5", "E5", "A5", "G5"], bass: ["A2", "E3", "A2", "A2"], chord: ["A", "C", "E"] },
      { lead: ["A5", "F5", "C5", "A4", "C5", "F5", "A5", "G5"], bass: ["F2", "C3", "F2", "F2"], chord: ["F", "A", "C"] },
      { lead: ["D5", "A4", "F4", "D4", "F4", "A4", "D5", "C5"], bass: ["D3", "A2", "D3", "D3"], chord: ["D", "F", "A"] },
      { lead: ["E5", "B4", "G#4", "E4", "G#4", "B4", "E5", "D5"], bass: ["E3", "B2", "E3", "E3"], chord: ["E", "G#", "B"] },
    ],
  },
  {
    name: "swing",
    duty: 0.25, second: "harmony", secondDuty: 0.25,
    drums: ["hat", null, "snare", "hat", "hat", null, "snare", "hat"],
    bars: [
      { lead: ["D5", "-", "F5", null, "E5", "D5", "-", "A4"], bass: ["D3", null, null, "D3", "A2", null, "D3", null], chord: ["D", "F", "A"] },
      { lead: ["A#4", "-", "D5", null, "F5", "D5", "-", "C5"], bass: ["A#2", null, null, "A#2", "F2", null, "A#2", null], chord: ["A#", "D", "F"] },
      { lead: ["C5", "-", "A4", null, "F4", "A4", "-", "C5"], bass: ["F2", null, null, "F2", "C3", null, "F2", null], chord: ["F", "A", "C"] },
      { lead: ["E5", "-", "G4", null, "C5", "E5", "-", "D5"], bass: ["C3", null, null, "C3", "G2", null, "C3", null], chord: ["C", "E", "G"] },
      { lead: ["G4", "-", "A#4", null, "D5", "G4", "-", "F4"], bass: ["G2", null, null, "G2", "D3", null, "G2", null], chord: ["G", "A#", "D"] },
      { lead: ["C5", "-", "E5", null, "G4", "C5", "-", "B4"], bass: ["C3", null, null, "C3", "G2", null, "C3", null], chord: ["C", "E", "G"] },
      { lead: ["F5", "-", "C5", null, "A4", "F4", "-", "G4"], bass: ["F2", null, null, "F2", "C3", null, "F2", null], chord: ["F", "A", "C"] },
      { lead: ["D5", "-", "A4", null, "F4", "D5", "-", "E5"], bass: ["D3", null, null, "D3", "A2", null, "D3", null], chord: ["D", "F", "A"] },
    ],
  },
  {
    name: "descent",
    duty: 0.125, second: "arp", secondDuty: 0.125,
    drums: ["hat", null, null, null, "snare", null, null, "hat"],
    bars: [
      { lead: ["A4", "-", "C5", "E5"], bass: ["A2", "-", "-", "E3"], chord: ["A", "C", "E"] },
      { lead: ["G4", "-", "B4", "D5"], bass: ["G2", "-", "-", "D3"], chord: ["G", "B", "D"] },
      { lead: ["F4", "-", "A4", "C5"], bass: ["F2", "-", "-", "C3"], chord: ["F", "A", "C"] },
      { lead: ["E4", "-", "G#4", "B4"], bass: ["E3", "-", "-", "B2"], chord: ["E", "G#", "B"] },
      { lead: ["C5", "-", "E5", "G5"], bass: ["C3", "-", "-", "G2"], chord: ["C", "E", "G"] },
      { lead: ["A#4", "-", "D5", "F5"], bass: ["A#2", "-", "-", "F2"], chord: ["A#", "D", "F"] },
      { lead: ["D5", "-", "F5", "A5"], bass: ["D3", "-", "-", "A2"], chord: ["D", "F", "A"] },
      { lead: ["B4", "-", "G#4", "E4"], bass: ["E3", "-", "-", "B2"], chord: ["E", "G#", "B"] },
    ],
  },
  {
    name: "finale",
    duty: 0.25, second: "harmony", secondDuty: 0.5,
    drums: ["hat", "hat", "hat", "hat", "snare", "hat", "hat", "hat", "hat", "hat", "hat", "hat", "snare", "hat", "snare", "hat"],
    bars: [
      { lead: ["C5", "E5", "G5", "E5", "C5", "G4", "C5", "E5", "G5", "A5", "G5", "E5", "C5", "E5", "D5", "E5"], bass: ["C3", "G2", "C3", "G2", "C3", "G2", "C3", "G2"], chord: ["C", "E", "G"] },
      { lead: ["B4", "D5", "G5", "D5", "B4", "G4", "B4", "D5", "G5", "A5", "G5", "D5", "B4", "D5", "A4", "B4"], bass: ["G2", "D3", "G2", "D3", "G2", "D3", "G2", "D3"], chord: ["G", "B", "D"] },
      { lead: ["A4", "C5", "E5", "C5", "A4", "E4", "A4", "C5", "E5", "A5", "E5", "C5", "A4", "C5", "B4", "C5"], bass: ["A2", "E3", "A2", "E3", "A2", "E3", "A2", "E3"], chord: ["A", "C", "E"] },
      { lead: ["F4", "A4", "C5", "A4", "F4", "C4", "F4", "A4", "C5", "F5", "C5", "A4", "F4", "A4", "G4", "A4"], bass: ["F2", "C3", "F2", "C3", "F2", "C3", "F2", "C3"], chord: ["F", "A", "C"] },
      { lead: ["D5", "F5", "A5", "F5", "D5", "A4", "D5", "F5", "A5", "G5", "A5", "F5", "D5", "F5", "E5", "F5"], bass: ["D3", "A2", "D3", "A2", "D3", "A2", "D3", "A2"], chord: ["D", "F", "A"] },
      { lead: ["G4", "B4", "D5", "B4", "G4", "D4", "G4", "B4", "D5", "E5", "D5", "B4", "G4", "B4", "A4", "B4"], bass: ["G2", "D3", "G2", "D3", "G2", "D3", "G2", "D3"], chord: ["G", "B", "D"] },
      { lead: ["C5", "G4", "E5", "G4", "C5", "E4", "G4", "C5", "E5", "G5", "E5", "C5", "G4", "C5", "B4", "C5"], bass: ["C3", "G2", "C3", "G2", "C3", "G2", "C3", "G2"], chord: ["C", "E", "G"] },
      { lead: ["E4", "G#4", "B4", "E5", "G#5", "E5", "B4", "G#4", "E4", "B4", "E5", "G#5", "E5", "B4", "D5", "B4"], bass: ["E3", "B2", "E3", "B2", "E3", "B2", "E3", "B2"], chord: ["E", "G#", "B"] },
    ],
  },
  {
    name: "vigil",
    duty: 0.5, second: "harmony", secondDuty: 0.125,
    drums: ["hat", null, "snare", null],
    bars: [
      { lead: ["A4", "E5"], bass: ["A2", "E3"], chord: ["A", "C", "E"] },
      { lead: ["C5", "G4"], bass: ["C3", "G2"], chord: ["C", "E", "G"] },
      { lead: ["F4", "C5"], bass: ["F2", "C3"], chord: ["F", "A", "C"] },
      { lead: ["D5", "B4"], bass: ["G2", "D3"], chord: ["G", "B", "D"] },
      { lead: ["C5", "A4"], bass: ["A2", "E3"], chord: ["A", "C", "E"] },
      { lead: ["D5", "F5"], bass: ["D3", "A2"], chord: ["D", "F", "A"] },
      { lead: ["B4", "G#4"], bass: ["E3", "B2"], chord: ["E", "G#", "B"] },
      { lead: ["A4", "-"], bass: ["A2", "-"], chord: ["A", "C", "E"] },
    ],
  },
  {
    name: "pursuit",
    duty: 0.375, second: "none", secondDuty: 0.125,
    drums: ["hat", null, "hat", "snare", null, "hat", "hat", null, "hat", "snare", null, "hat"],
    bars: [
      { lead: ["A4", "C5", "E5", "A5", "-", null, "E5", "C5", "A4", "-", "B4", null], bass: ["A2", null, "E3", null, "A2", null], chord: ["A", "C", "E"] },
      { lead: ["E5", "A5", "E5", "C5", "-", null, "A4", "B4", "C5", "-", "E5", null], bass: ["A2", null, "E3", null, "A2", null], chord: ["A", "C", "E"] },
      { lead: ["D5", "F5", "A5", "D5", "-", null, "A4", "F4", "D4", "-", "E4", null], bass: ["D3", null, "A2", null, "D3", null], chord: ["D", "F", "A"] },
      { lead: ["E5", "B4", "G#4", "E4", "-", null, "G#4", "B4", "E5", "-", "D5", null], bass: ["E3", null, "B2", null, "E3", null], chord: ["E", "G#", "B"] },
      { lead: ["A4", "B4", "C5", "E5", "-", null, "C5", "B4", "A4", "-", "G4", null], bass: ["A2", null, "E3", null, "A2", null], chord: ["A", "C", "E"] },
      { lead: ["F4", "A4", "C5", "F5", "-", null, "C5", "A4", "F4", "-", "G4", null], bass: ["F2", null, "C3", null, "F2", null], chord: ["F", "A", "C"] },
      { lead: ["A4", "D5", "F5", "A5", "-", null, "F5", "D5", "A4", "-", "G4", null], bass: ["D3", null, "A2", null, "D3", null], chord: ["D", "F", "A"] },
      { lead: ["G#4", "B4", "E5", "G#5", "-", null, "E5", "B4", "G#4", "-", "D5", null], bass: ["E3", null, "B2", null, "E3", null], chord: ["E", "G#", "B"] },
    ],
  },
];

/** The manifesto's own theme — quarter-note grid, no percussion at all. */
const MANIFESTO_MOVEMENT = {
  name: "manifesto",
  duty: 0.125, second: "none", secondDuty: 0.125,
  drums: [null],
  bars: [
    { lead: ["A4", "-", "-", "E4"], bass: ["A2", "-", "-", "-"], chord: ["A", "C", "E"] },
    { lead: ["G4", "-", "-", "D4"], bass: ["G2", "-", "-", "-"], chord: ["G", "B", "D"] },
    { lead: ["F4", "-", "-", "C4"], bass: ["F2", "-", "-", "-"], chord: ["F", "A", "C"] },
    { lead: ["E4", "-", "-", "B3"], bass: ["E3", "-", "-", "-"], chord: ["E", "G#", "B"] },
  ],
};

/** The closing cadence — one bar, five notes, played once, no drums. */
const CADENCE_BAR = {
  name: "cadence",
  duty: 0.5, second: "none", secondDuty: 0.25,
  drums: [null],
  bars: [
    { lead: ["A4", "C5", "E5", "-", "D5", "-", "A4", "-"], bass: ["A2", null, "E3", null, "F2", null, "A2", "-"], chord: ["A", "C", "E"] },
  ],
};

const STEP_S = 0.24;
export const BAR_S = 8 * STEP_S;
export const MOVEMENT_COUNT = MOVEMENTS.length;
export const BAR_COUNT = MOVEMENTS[0].bars.length;
export const PHRASE_S = BAR_COUNT * BAR_S;

const ARP_S = STEP_S / 2;
const HAT_S = 0.03;
const SNARE_S = 0.12;

// Gain budget — quiet on purpose, this opens unasked for. PEAK_GAIN is the
// master; every voice below declares its peak relative to it.
export const PEAK_GAIN = 0.06;
const LEAD_PEAK = 0.74;
const LEAD_SOLO_PEAK = 1;
const HARMONY_PEAK = 0.26;
const ARP_PEAK = 0.2;
const BASS_PEAK = 0.24;
const HAT_PEAK = 0.05;
const SNARE_PEAK = 0.12;

const MUTE_RAMP_S = 0.02;
const CROSSFADE_S = 0.9;
const GAIN_FLOOR = 0.0001;
const DECAY_FRACTION = 0.9;
const ATTACK_S = 0.006;

const TIME_EPS = 1e-9;

function soundLine(line) {
  const slotS = BAR_S / line.length;
  const out = [];
  line.forEach((step, i) => {
    const at = i * slotS;
    if (step === null) return;
    if (step === "-") {
      const held = out[out.length - 1];
      if (held !== undefined && Math.abs(held.at + held.durS - at) < TIME_EPS) {
        held.durS += slotS;
      }
      return;
    }
    out.push({ midi: midiOf(step), at, durS: slotS });
  });
  return out;
}

function wrap(index, length) {
  return ((index % length) + length) % length;
}

function movementAtIndex(index) {
  return MOVEMENTS[wrap(index, MOVEMENT_COUNT)] ?? MOVEMENTS[0];
}

export function creditsBar(index, movement = 0) {
  return barEvents(movementAtIndex(movement), index);
}

export function creditsMovementName(index) {
  return movementAtIndex(index).name;
}

export function creditsManifestoBar(index) {
  return barEvents(MANIFESTO_MOVEMENT, index);
}

export function creditsCadence() {
  return barEvents(CADENCE_BAR, 0);
}

function barEvents(score, index) {
  const bar = score.bars[wrap(index, score.bars.length)] ?? score.bars[0];
  const events = [];
  const leadPeak = score.second === "none" ? LEAD_SOLO_PEAK : LEAD_PEAK;
  const lead = soundLine(bar.lead);

  for (const note of lead) {
    events.push({
      voice: "lead", hz: hzOfMidi(note.midi), duty: score.duty,
      at: note.at, durS: note.durS, decayS: note.durS * DECAY_FRACTION, peak: leadPeak,
    });
  }

  if (score.second === "harmony") {
    for (const note of lead) {
      const below = chordToneBelow(note.midi, bar.chord);
      if (below === null) continue;
      events.push({
        voice: "harmony", hz: hzOfMidi(below), duty: score.secondDuty,
        at: note.at, durS: note.durS, decayS: note.durS * DECAY_FRACTION, peak: HARMONY_PEAK,
      });
    }
  }

  if (score.second === "arp") {
    const figure = arpeggioFrom(bar.chord, 4);
    const steps = Math.round(BAR_S / ARP_S);
    for (let i = 0; i < steps; i += 1) {
      events.push({
        voice: "arp", hz: hzOfMidi(figure[i % figure.length] ?? figure[0] ?? 0), duty: score.secondDuty,
        at: i * ARP_S, durS: ARP_S, decayS: ARP_S * DECAY_FRACTION, peak: ARP_PEAK,
      });
    }
  }

  for (const note of soundLine(bar.bass)) {
    events.push({
      voice: "bass", hz: hzOfMidi(note.midi), duty: null,
      at: note.at, durS: note.durS, decayS: note.durS * DECAY_FRACTION, peak: BASS_PEAK,
    });
  }

  const drumSlotS = BAR_S / score.drums.length;
  score.drums.forEach((drum, i) => {
    if (drum === null) return;
    const durS = drum === "hat" ? HAT_S : SNARE_S;
    events.push({
      voice: drum, hz: null, duty: null,
      at: i * drumSlotS, durS, decayS: durS * DECAY_FRACTION, peak: drum === "hat" ? HAT_PEAK : SNARE_PEAK,
    });
  });

  return events;
}

// ---------------------------------------------------------------------------
// Scheduling
// ---------------------------------------------------------------------------

const NOISE_S = 1;
const LOOKAHEAD_S = 0.35;
const PUMP_MS = 100;
const PULSE_HARMONICS = 24;

function fadeBus(bus, to, now) {
  const from = bus.gain.value;
  bus.gain.cancelScheduledValues(now);
  bus.gain.setValueAtTime(from, now);
  bus.gain.linearRampToValueAtTime(to, now + CROSSFADE_S);
}

function makeNoiseBuffer(ctx) {
  const frames = Math.max(1, Math.floor(ctx.sampleRate * NOISE_S));
  const buffer = ctx.createBuffer(1, frames, ctx.sampleRate);
  const data = buffer.getChannelData(0);
  for (let i = 0; i < frames; i += 1) data[i] = Math.random() * 2 - 1;
  return buffer;
}

function pulseWave(ctx, duty) {
  if (typeof ctx.createPeriodicWave !== "function") return null;
  const real = new Float32Array(PULSE_HARMONICS + 1);
  const imag = new Float32Array(PULSE_HARMONICS + 1);
  for (let n = 1; n <= PULSE_HARMONICS; n += 1) {
    imag[n] = (2 / (n * Math.PI)) * Math.sin(n * Math.PI * duty);
  }
  try {
    return ctx.createPeriodicWave(real, imag);
  } catch {
    return null;
  }
}

/**
 * Starts the soundtrack on `ctx`, which this function then OWNS — `stop()`
 * closes it. `movementAt` reads which pass of the roll is on screen; the
 * suite follows it, so the music turns over exactly when the titles do.
 */
export function startCreditsArpeggio(ctx, muted, movementAt = () => 0) {
  const master = ctx.createGain();
  master.gain.value = muted ? 0 : PEAK_GAIN;
  master.connect(ctx.destination);

  const buses = {
    suite: ctx.createGain(),
    manifesto: ctx.createGain(),
    cadence: ctx.createGain(),
  };
  for (const [name, bus] of Object.entries(buses)) {
    bus.gain.value = name === "suite" ? 1 : 0;
    bus.connect(master);
  }

  const voices = [];
  const waves = new Map();
  let noise = null;
  let timer = null;
  let stopped = false;
  let barIndex = 0;
  let movement = 0;
  let nextBarAt = 0;
  let piece = "suite";

  const waveFor = (duty) => {
    const cached = waves.get(duty);
    if (cached !== undefined) return cached;
    const built = pulseWave(ctx, duty);
    waves.set(duty, built);
    return built;
  };

  const keep = (source, env, stopAt) => {
    source.stop(stopAt);
    voices.push(source);
    source.onended = () => {
      const i = voices.indexOf(source);
      if (i !== -1) voices.splice(i, 1);
      source.disconnect();
      env.disconnect();
    };
  };

  const scheduleEvent = (event, base, bus) => {
    const at = base + event.at;
    if (event.hz === null && noise === null) return;

    const env = ctx.createGain();
    env.gain.setValueAtTime(GAIN_FLOOR, at);
    env.gain.exponentialRampToValueAtTime(event.peak, at + Math.min(ATTACK_S, event.durS * 0.2));
    env.gain.exponentialRampToValueAtTime(GAIN_FLOOR, at + event.decayS);
    env.connect(bus);

    if (event.hz === null && noise !== null) {
      const burst = ctx.createBufferSource();
      burst.buffer = noise;
      burst.connect(env);
      burst.start(at, Math.random() * Math.max(0, NOISE_S - event.durS));
      keep(burst, env, at + event.durS);
      return;
    }

    const osc = ctx.createOscillator();
    const wave = event.duty === null || event.duty === 0.5 ? null : waveFor(event.duty);
    if (wave !== null) {
      osc.setPeriodicWave(wave);
    } else {
      osc.type = event.duty === null ? "triangle" : "square";
    }
    osc.frequency.value = event.hz ?? 0;
    osc.connect(env);
    osc.start(at);
    keep(osc, env, at + event.durS);
  };

  const readMovement = () => {
    try {
      const next = movementAt();
      return Number.isFinite(next) ? next : movement;
    } catch {
      return movement;
    }
  };

  const pump = () => {
    if (stopped) return;
    if (nextBarAt < ctx.currentTime) nextBarAt = ctx.currentTime;
    while (nextBarAt < ctx.currentTime + LOOKAHEAD_S) {
      if (piece === "cadence" && barIndex > 0) break;

      let events;
      if (piece === "suite") {
        const wanted = wrap(readMovement(), MOVEMENT_COUNT);
        if (wanted !== movement) {
          movement = wanted;
          barIndex = 0;
        }
        events = creditsBar(barIndex, movement);
      } else if (piece === "manifesto") {
        events = creditsManifestoBar(barIndex);
      } else {
        events = creditsCadence();
      }

      const bus = buses[piece];
      for (const event of events) scheduleEvent(event, nextBarAt, bus);
      barIndex += 1;
      nextBarAt += BAR_S;
    }
    timer = setTimeout(pump, PUMP_MS);
  };

  try {
    if (ctx.state === "suspended") void ctx.resume();
    noise = makeNoiseBuffer(ctx);
    nextBarAt = ctx.currentTime;
    pump();
  } catch {
    // A refused graph leaves the modal silent, not broken.
  }

  return {
    setMuted: (next) => {
      if (stopped) return;
      master.gain.setTargetAtTime(next ? 0 : PEAK_GAIN, ctx.currentTime, MUTE_RAMP_S);
    },

    setPiece: (next) => {
      if (stopped || next === piece) return;
      const now = ctx.currentTime;
      fadeBus(buses[piece], 0, now);
      fadeBus(buses[next], 1, now);

      piece = next;
      barIndex = 0;
      nextBarAt = now;
      if (timer !== null) clearTimeout(timer);
      timer = null;
      pump();
    },

    stop: () => {
      if (stopped) return;
      stopped = true;
      if (timer !== null) clearTimeout(timer);
      timer = null;
      const at = ctx.currentTime;
      master.gain.cancelScheduledValues(at);
      for (const bus of Object.values(buses)) bus.gain.cancelScheduledValues(at);
      for (const voice of voices) {
        try {
          voice.stop();
        } catch {
          // Already stopped, or never started.
        }
        voice.disconnect();
      }
      voices.length = 0;
      waves.clear();
      for (const bus of Object.values(buses)) bus.disconnect();
      master.disconnect();
      void ctx.close();
    },
  };
}
