// matrixRain.js — vanilla-JS port of cicchetto's MatrixRain.tsx: decorative
// amber character rain behind the credits roll. Canvas, not DOM, ~15fps.

const GLYPHS = "01アイウエオカキクケコサシスセソタチツテトナニヌネノ<>[]{}/\\|=+*#$%&";
const FONT_SIZE = 14;
const FRAME_MS = 66;
const DESCENDER = 4;
const TRAIL_RGB = "255, 176, 0";

/**
 * Mounts the rain into `canvas`, sized off its parent element. `look()` is
 * called once per drawn frame and must return
 * `{ glyphAlpha, fadeAlpha, leader, rowsPerFrame }`.
 * Returns a `stop()` that cancels the loop and disconnects the observer.
 */
function mountMatrixRain(canvas, look) {
  const ctx = canvas.getContext("2d");
  if (ctx === null) return () => {};

  const mq = window.matchMedia?.("(prefers-reduced-motion: reduce)") ?? null;
  if (mq?.matches === true) return () => {};

  let columns = [];
  let heads = [];
  const resize = () => {
    const box = canvas.parentElement;
    if (box === null) return;
    canvas.width = box.clientWidth;
    canvas.height = box.clientHeight;
    const count = Math.ceil(canvas.width / FONT_SIZE);
    columns = new Array(count).fill(0);
    heads = new Array(count).fill(null);
    ctx.font = `${FONT_SIZE}px monospace`;
  };
  resize();

  const observer = new ResizeObserver(resize);
  if (canvas.parentElement !== null) observer.observe(canvas.parentElement);

  let raf = 0;
  let last = 0;
  const draw = (now) => {
    raf = requestAnimationFrame(draw);
    if (now - last < FRAME_MS) return;
    last = now;

    const l = look();
    const trail = `rgba(${TRAIL_RGB}, ${l.glyphAlpha})`;

    ctx.fillStyle = `rgba(0, 0, 0, ${l.fadeAlpha})`;
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    for (let i = 0; i < columns.length; i++) {
      const row = columns[i] ?? 0;
      const y = Math.round(row) * FONT_SIZE;
      const x = i * FONT_SIZE;
      const glyph = GLYPHS[Math.floor(Math.random() * GLYPHS.length)] ?? "0";

      if (l.leader === null) {
        ctx.fillStyle = trail;
        ctx.fillText(glyph, x, y);
      } else {
        const head = heads[i] ?? null;
        if (head !== null && head.y !== y) {
          ctx.fillStyle = "#000";
          ctx.fillRect(x, head.y - FONT_SIZE, FONT_SIZE, FONT_SIZE + DESCENDER);
          ctx.fillStyle = trail;
          ctx.fillText(head.glyph, x, head.y);
        }
        ctx.fillStyle = l.leader;
        ctx.fillText(glyph, x, y);
        heads[i] = { y, glyph };
      }

      columns[i] = y > canvas.height && Math.random() > 0.975 ? 0 : row + l.rowsPerFrame;
    }
  };
  raf = requestAnimationFrame(draw);

  const onReducedChange = (e) => {
    if (e.matches) cancelAnimationFrame(raf);
  };
  mq?.addEventListener("change", onReducedChange);

  return () => {
    cancelAnimationFrame(raf);
    observer.disconnect();
    mq?.removeEventListener("change", onReducedChange);
  };
}

/** The two looks cicchetto's credits roll switches between (#1807/#1929). */
const CREDITS_RAIN_LOOK = { glyphAlpha: 0.3, fadeAlpha: 0.06, leader: "rgba(255, 232, 176, 0.95)", rowsPerFrame: 0.7 };
const CREDITS_RAIN_BURST_LOOK = { glyphAlpha: 0.45, fadeAlpha: 0.05, leader: "rgba(255, 255, 255, 1)", rowsPerFrame: 1 };
