// GKME-Web — settings page builders (mirrors the 9 Android settings pages).

import { el, row, section, toggle, slider, select, button, colorInput } from "./dom.js";
import { showConfirm, showSponsorDialog } from "./dialog.js";
import { DISPLAY_MODES } from "./controls.js";
import { DEFAULT_APPEARANCE, normalizeButton } from "./layout.js";
import { DEFAULT_SETTINGS, savePresets, APP_VERSION } from "./state.js";
import { connectedGamepads, deviceMotorCount, gamepadActuator } from "./haptics.js";

function ensureAppearance(app) {
  if (!app.settings.appearance) {
    app.settings.appearance = JSON.parse(JSON.stringify(DEFAULT_APPEARANCE));
  }
  return app.settings.appearance;
}

function applyAppearance(app) {
  app.layout.setAppearance(ensureAppearance(app));
}

const POLL_RATES = [30, 45, 60, 90, 100, 120, 200, 250, 300, 500, 750, 1000];

export const PAGES = {
  connection(app, root) {
    const hz = POLL_RATES.includes(app.settings.pollingHz) ? app.settings.pollingHz : 120;
    root.appendChild(
      section("Connect", [
        row("Status", el("span", { text: app.conn.connected ? "Connected" : "Not connected", class: "statusval" })),
        row(
          "Polling rate",
          select(
            POLL_RATES.map((r) => [r, r + " Hz"]),
            String(hz),
            (v) => {
              app.settings.pollingHz = Number(v);
              app._restartSendLoop();
            }
          )
        ),
        el("div", { class: "btnrow" }, [
          button("Enter fullscreen", () => app._enterFullscreen()),
        ]),
      ])
    );
  },

  presets(app, root) {
    const build = () => {
      root.innerHTML = "";
      root.appendChild(el("div", { class: "presettitle", text: "Layout presets" }));

      const current = app.activePreset;
      const isBuiltIn = app.isBuiltInPreset(current);

      const chips = el("div", { class: "presetchips" });
      const chip = (label, fn) => {
        const b = button(label, fn);
        b.classList.add("presetchip");
        chips.appendChild(b);
        return b;
      };
      chip("New", async () => { if (await app.newPreset()) build(); });
      chip("Edit", () => app.enterEditMode());
      if (isBuiltIn) chip("Restore defaults", async () => { if (await app.restoreCurrentDefault()) build(); });
      chip("Copy", async () => { if (await app.copyCurrentPreset()) build(); });
      chip("Export", () => app.exportCurrentPreset());
      chips.appendChild(
        el("label", { class: "filebtn presetchip" }, [
          "Import",
          el("input", {
            type: "file",
            accept: ".json,application/json",
            onchange: async (e) => {
              const f = e.target.files[0];
              if (!f) return;
              if (await app.importPresetFromFile(f)) build();
              e.target.value = "";
            },
          }),
        ])
      );
      if (!isBuiltIn) {
        chip("Rename", async () => { if (await app.renameCurrentPreset()) build(); });
        chip("Delete", async () => { if (await app.deleteCurrentPreset()) build(); });
      }
      root.appendChild(chips);

      root.appendChild(el("div", { class: "presetcurrent", text: "Current preset: " + current }));

      const grid = el("div", { class: "presetgrid" });
      for (const name of app.presetNames()) {
        const active = name === app.activePreset;
        const canvas = el("canvas", { class: "presetpreview" });
        const card = el("div", { class: "presetcard" + (active ? " active" : "") }, [
          canvas,
          el("div", { class: "presetcardname", text: name }),
        ]);
        card.addEventListener("click", () => {
          if (name === app.activePreset) return;
          app._loadPreset(name);
          build();
        });
        grid.appendChild(card);
        requestAnimationFrame(() => drawPresetPreview(canvas, app.presets[name]));
      }
      root.appendChild(grid);
    };
    build();
  },

  physical(app, root) {
    const pads = [];
    if (navigator.getGamepads) {
      for (const p of navigator.getGamepads()) if (p && p.connected) pads.push(p);
    }
    root.appendChild(
      section("Physical controller (Gamepad API)", [
        row("Forward physical controller", toggle(app.settings.physical.forward, (v) => (app.settings.physical.forward = v))),
        row("Connected controllers", el("span", { class: "statusval", text: String(pads.length) })),
      ])
    );
    const listEl = el("div", { class: "padlist" });
    if (pads.length === 0) {
      listEl.appendChild(el("p", { class: "hint", text: "No physical controller detected. Connect one via Bluetooth/OTG, then press a controller button while in the browser to detect it." }));
    } else {
      for (const p of pads) {
        listEl.appendChild(el("div", { class: "padsrow", text: `${p.index}: ${p.id} (${p.mapping}) — ${p.buttons.length} buttons / ${p.axes.length} axes` }));
      }
    }
    root.appendChild(section("Device list", [listEl]));
    root.appendChild(
      el("p", { class: "hint", text: "Browsers can only read buttons, sticks, and triggers from standard-mapped controllers. Vendor features such as adaptive triggers, touchpads, and HD rumble cannot be forwarded." })
    );
  },

  vibration(app, root) {
    const build = () => {
      root.innerHTML = "";
      root.appendChild(section("Button vibration", [buttonVibrationControls(app, build)]));

      const hs = app.settings.haptics;
      const pads = connectedGamepads();
      if (!hs.device || typeof hs.device !== "object") hs.device = { type: "phone", controllerIndex: 0 };

      const options = [["phone", "Phone motor"]];
      pads.forEach((p, i) => options.push(["controller:" + i, gamepadLabel(p, i)]));
      options.push(["none", "None"]);

      let value = hs.device.type === "controller" ? "controller:" + (hs.device.controllerIndex | 0) : hs.device.type;
      if (!options.some(([v]) => v === value)) value = "phone";
      const shown = parseVibrationDevice(value, pads);

      const rows = [
        row("Receive host vibration", toggle(hs.gameVibrate, (v) => (hs.gameVibrate = v))),
        row(
          "Vibration device",
          select(options, value, (v) => {
            hs.device = parseVibrationDevice(v, pads);
            build();
          })
        ),
      ];
      if (deviceMotorCount(shown, pads) >= 2) {
        const isController = shown.type === "controller";
        rows.push(
          row(
            "Swap left/right motors",
            toggle(isController ? hs.swapControllerMotors : hs.swapPhoneMotors, (v) => {
              if (isController) hs.swapControllerMotors = v;
              else hs.swapPhoneMotors = v;
            })
          )
        );
      }

      root.appendChild(section("Game vibration", rows));
      root.appendChild(el("p", { class: "hint", text: hapticsHint(app, shown, pads) }));
    };
    build();
  },

  misc(app, root) {
    root.appendChild(
      section("Display", [
        row(
          "Button style",
          select(
            DISPLAY_MODES.map((m) => [m, m]),
            app.settings.displayMode,
            (v) => {
              app.settings.displayMode = v;
              app.layout.setDisplayMode(v);
            }
          )
        ),
        row("Keep screen on", toggle(app.settings.screen.keepAwake, (v) => {
          app.settings.screen.keepAwake = v;
          if (v) app._acquireWakeLock();
          else app._releaseWakeLock();
        })),
      ])
    );
    root.appendChild(
      section("Screen-off mode (simulated)", [
        button("Enter screen-off mode", () => {
          app.closeSettings();
          let overlay = document.getElementById("screenOff");
          if (!overlay) {
            overlay = el("div", { id: "screenOff", class: "screenoff" });
            overlay.addEventListener("click", () => overlay.remove());
            document.body.appendChild(overlay);
          }
          app._acquireWakeLock();
        }),
      ])
    );
    root.appendChild(
      section("Reset", [
        el("div", { class: "btnrow" }, [
          button("Reset all settings", async () => {
            if (!(await showConfirm("Reset all settings", "Restore default settings?", { positiveText: "Reset" }))) return;
            app.settings = JSON.parse(JSON.stringify(DEFAULT_SETTINGS));
            applyAppearance(app);
            app.layout.setDisplayMode(app.settings.displayMode);
            app._restartSendLoop();
            app._selectCategory("misc");
          }, "danger"),
          button("Reset current preset", async () => {
            if (!(await showConfirm("Reset current preset", "Restore the current preset to defaults?", { positiveText: "Reset" }))) return;
            app.presets[app.activePreset] = { version: 1, buttons: [] };
            save(app);
            app.layout.setPreset(app.presets[app.activePreset]);
          }, "danger"),
        ]),
      ])
    );
  },

  about(app, root) {
    root.appendChild(
      el("div", { class: "aboutpage" }, [
        el("img", { class: "abouticon", src: "assets/icon.png", alt: "App icon" }),
        el("div", { class: "aboutname", text: "GKME" }),
        el("div", { class: "aboutver", text: "Version " + APP_VERSION }),
        el("div", {
          class: "aboutdesc",
          text:
            "Author: 4zyz4  QQ group: 639317971\n\n" +
            "Source: https://github.com/4zyz4/gkme\n" +
            "Note: This app is not Emotion. Join QQ group 1045923515 to download the official Emotion app.",
        }),
        button("Donate", () => showSponsorDialog(), "aboutsponsor"),
      ])
    );
  },
};

function save(app) {
  savePresets(app.presets);
}

// ── Button vibration (Press / Release), mirrors the Android two-column layout ──

function buttonVibrationControls(app, rebuild) {
  const hs = app.settings.haptics;
  const column = (isPress, title) => {
    const typeKey = isPress ? "buttonPressType" : "buttonReleaseType";
    const durKey = isPress ? "buttonPressDuration" : "buttonReleaseDuration";
    const chips = el("div", { class: "vibchips" });
    for (const [val, text] of [["none", "None"], ["custom", "Custom"]]) {
      const chip = el("button", { class: "btn vibchip" + (hs[typeKey] === val ? " on" : ""), text });
      chip.addEventListener("click", () => {
        hs[typeKey] = val;
        rebuild();
      });
      chips.appendChild(chip);
    }
    const kids = [el("div", { class: "vibcoltitle", text: title }), chips];
    if (hs[typeKey] === "custom") {
      kids.push(
        row("Duration", slider(5, 60, 1, hs[durKey], (v) => (hs[durKey] = v), (v) => v + " ms"))
      );
    }
    return el("div", { class: "vibcol" }, kids);
  };
  return el("div", { class: "vibcols" }, [column(true, "Press"), column(false, "Release")]);
}

// ── Vibration device helpers ──

function gamepadLabel(pad, index) {
  const id = (pad && pad.id ? pad.id : "").trim();
  if (!id) return "Controller" + (index + 1);
  return id.length > 30 ? id.slice(0, 30) + "…" : id;
}

/** Parse a select value ("phone" | "controller:<i>" | "none") into a device object. */
function parseVibrationDevice(value, pads) {
  if (typeof value === "string" && value.startsWith("controller:")) {
    const index = Number(value.slice("controller:".length));
    if (pads[index]) return { type: "controller", controllerIndex: index };
  } else if (value === "none") {
    return { type: "none", controllerIndex: 0 };
  }
  return { type: "phone", controllerIndex: 0 };
}

function hapticsHint(app, device, pads) {
  if (device.type === "none") return "Game vibration output is disabled.";
  if (device.type === "controller") {
    const actuator = gamepadActuator(pads[device.controllerIndex | 0]);
    return actuator
      ? "Game vibration is sent to the selected controller using the Gamepad API dual-rumble effect (strong/weak motors)."
      : "The selected controller does not support vibration: the browser provides no vibrationActuator (some controllers require vendor drivers).";
  }
  return app.haptics.supported
    ? "The phone motor uses the browser Vibration API (mobile browsers only)."
    : "This browser does not support the Vibration API.";
}

// Miniature layout preview for a preset card (mirrors PresetPreviewView).
function drawPresetPreview(canvas, preset) {
  const raw = (preset && preset.buttons) || [];
  const cssW = Math.max(1, canvas.offsetWidth);
  const cssH = Math.max(1, canvas.offsetHeight);
  const dpr = Math.max(1, window.devicePixelRatio || 1);
  canvas.width = Math.round(cssW * dpr);
  canvas.height = Math.round(cssH * dpr);
  const ctx = canvas.getContext("2d");
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, cssW, cssH);
  if (!raw.length) return;

  const gridW = 120;
  const gridH = 53;
  const scale = Math.min(cssW / gridW, cssH / gridH);
  const offX = (cssW - gridW * scale) / 2;
  const offY = (cssH - gridH * scale) / 2;

  for (const r of raw) {
    const b = normalizeButton(r);
    if (b.visible === false) continue;
    const base = b.id.split("_")[0];
    const bx = offX + b.x * scale;
    const by = offY + b.y * scale;
    const swapped = !b.lockAspect && (b.rotation === 90 || b.rotation === 270);
    const bw = (swapped ? b.h : b.w) * scale;
    const bh = (swapped ? b.w : b.h) * scale;
    if (base === "dpadPad" || base === "customKeypad") {
      const s = Math.min(bw, bh);
      ctx.beginPath();
      ctx.fillStyle = "#BBBBBB";
      ctx.arc(bx + bw / 2, by + bh / 2, s / 2, 0, Math.PI * 2);
      ctx.fill();
    } else if (b.lockAspect) {
      const r2 = Math.max(2, Math.min(b.w, b.h) * scale * 0.35);
      ctx.beginPath();
      ctx.fillStyle = "#AAAAAA";
      ctx.arc(bx + bw / 2, by + bh / 2, r2, 0, Math.PI * 2);
      ctx.fill();
    } else {
      ctx.fillStyle = "#BBBBBB";
      ctx.fillRect(bx, by, bw, bh);
    }
  }
}
