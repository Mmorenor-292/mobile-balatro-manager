const mockState = {
  connected: true,
  providerDetected: true,
  loading: false,
  operation: { active: false, kind: "", itemId: "", source: "", label: "" },
  folder: "ASET/Mods",
  gameFile: "Balatro.exe · Steam library detected",
  nativeCompatibility: "unsupported",
  version: "2.0.1",
  channel: "beta",
  canUndo: true,
  counts: { active: 12, hidden: 6, problems: 2 },
  mods: [
    { folder: "smods-1.0.0", name: "Steamodded", version: "1.0.0", hidden: false, severity: "ok", diagnostics: ["Required framework"], dependencies: [] },
    { folder: "HandyBalatro", name: "Handy", version: "1.5.2", hidden: false, severity: "info", diagnostics: ["Depends on Steamodded"], dependencies: ["Steamodded"] },
    { folder: "Pokermon", name: "Pokermon", version: "0.8.1", hidden: false, severity: "info", diagnostics: ["Depends on Steamodded"], dependencies: ["Steamodded"] },
    { folder: "BetterDescriptions", name: "Better Descriptions", version: "1.1.0", hidden: true, severity: "ok", diagnostics: ["Hidden"], dependencies: [] },
    { folder: "CustomJokersPack", name: "Custom Jokers Pack", version: "2.3.0", hidden: false, severity: "error", diagnostics: ["Missing dependency: LovelyUI"], dependencies: ["LovelyUI"] },
    { folder: "VisualOverhaul", name: "Visual Overhaul", version: "1.0.4", hidden: false, severity: "warning", diagnostics: ["Corrupted metadata"], dependencies: [] },
    { id: "balatro_imm", folder: "imm", name: "imm", version: "2.5.1", hidden: false, severity: "ok", diagnostics: ["No issues detected"], dependencies: ["Steamodded"] },
  ],
  recovery: {
    active: false,
    step: 0,
    totalSteps: 0,
    suspects: [],
    testing: [],
    complete: false,
    culprit: "",
  },
  history: [
    { id: "demo", label: "Before Quick Rescue", createdAt: "Today, 11:14", entries: 18 },
  ],
  backupHistory: [
    { id: "demo", label: "Before Quick Rescue", createdAt: "Today, 11:14", entries: "18 mods · reversible" },
  ],
  installHistory: [
    { id: "install-demo", name: "Handy", version: "1.5.2", createdAt: "Yesterday, 20:08" },
  ],
  catalog: [
    {
      id: "MobileLikeDragging",
      source: "BMI",
      name: "Mobile Like Dragging",
      author: "Community",
      version: "2.0.1",
      summary: "Touch-friendly dragging controls for Balatro.",
      categories: ["Quality of Life"],
      downloads: 24810,
      installed: false,
      compatibility: "unknown",
      requiresSteamodded: true,
    },
    {
      id: "Handy",
      source: "Thunderstore",
      name: "Handy",
      author: "SleepyG11",
      version: "1.5.2",
      installedVersion: "1.5.2",
      latestVersion: "1.6.0",
      updateAvailable: true,
      versions: [
        { version: "1.6.0", downloadUrl: "https://example.invalid/handy-1.6.0.zip" },
        { version: "1.5.2", downloadUrl: "https://example.invalid/handy-1.5.2.zip" },
      ],
      summary: "A collection of useful gameplay shortcuts and controls.",
      categories: ["Quality of Life"],
      downloads: 68220,
      installed: true,
      compatibility: "unknown",
      requiresSteamodded: true,
    },
    {
      id: "JokerDisplay",
      source: "BMI",
      name: "Joker Display",
      author: "nh6574",
      version: "1.8.4",
      summary: "Shows the values and effects of your jokers.",
      categories: ["Quality of Life", "Technical"],
      downloads: 51500,
      installed: false,
      compatibility: "unknown",
      requiresSteamodded: true,
    },
    {
      id: "awesome:Firch/Bunco",
      source: "Awesome Balatro",
      name: "Bunco",
      author: "Firch",
      version: "main",
      summary: "A real GitHub repository from the Awesome Balatro collection. MBM can install its release or source archive.",
      downloadUrl: "https://github.com/Firch/Bunco/archive/refs/heads/main.zip",
      versions: [{ version: "main", downloadUrl: "https://github.com/Firch/Bunco/archive/refs/heads/main.zip" }],
      categories: ["Community"],
      downloads: 0,
      installed: false,
      compatibility: "unknown",
      requiresSteamodded: false,
      homepage: "https://github.com/jie65535/awesome-balatro",
    },
  ],
  catalogSources: ["Balatro Mod Index", "Thunderstore", "Awesome Balatro"],
  message: "",
  saveProfiles: ["Root folder", "Profile 1"],
};

let listener = null;

// Android WebView versions bundled with older API images (for example the
// API 31 image's Chromium 91) do not expose structuredClone yet. The bridge
// state is JSON data only, so a JSON clone is a safe compatibility fallback.
const cloneState = (value) => {
  if (typeof globalThis.structuredClone === "function") return globalThis.structuredClone(value);
  return JSON.parse(JSON.stringify(value));
};

let state = cloneState(mockState);

export function subscribe(next) {
  if (!window.AndroidBridge?.invoke) {
    state = cloneState(mockState);
  }
  listener = next;
  window.__nativeReceive = (payload) => {
    const parsed = typeof payload === "string" ? JSON.parse(payload) : payload;
    listener?.(parsed);
  };
  return () => {
    listener = null;
    delete window.__nativeReceive;
  };
}

function emitMock(patch = {}) {
  state = { ...state, ...patch };
  queueMicrotask(() => listener?.(cloneState(state)));
}

export function invoke(method, payload = {}) {
  if (window.AndroidBridge?.invoke) {
    window.AndroidBridge.invoke(method, JSON.stringify(payload));
    return;
  }

  if (method === "getState" || method === "refresh") {
    emitMock();
  } else if (method === "toggleMod") {
    const mods = state.mods.map((mod) =>
      mod.folder === payload.folder ? { ...mod, hidden: payload.hidden } : mod,
    );
    const active = mods.filter((mod) => !mod.hidden).length;
    const hidden = mods.length - active;
    emitMock({ mods, counts: { ...state.counts, active, hidden }, message: "Mod state updated" });
  } else if (method === "toggleMods") {
    const folders = new Set(payload.folders || []);
    const mods = state.mods.map((mod) => folders.has(mod.folder) ? { ...mod, hidden: Boolean(payload.hidden) } : mod);
    const active = mods.filter((mod) => !mod.hidden).length;
    emitMock({ mods, counts: { ...state.counts, active, hidden: mods.length - active }, canUndo: true, message: "Selected mods updated. Backup saved." });
  } else if (method === "quickRescue") {
    const mods = state.mods.map((mod) => ({
      ...mod,
      hidden: !mod.folder.toLowerCase().includes("smods"),
    }));
    emitMock({
      mods,
      counts: { ...state.counts, active: 1, hidden: mods.length - 1 },
      canUndo: true,
      message: "Safe Mode applied. You can test Balatro now.",
    });
  } else if (method === "undo") {
    state = cloneState(mockState);
    emitMock({ message: "Snapshot restored" });
  } else if (method === "beginIsolation") {
    emitMock({
      recovery: {
        active: true,
        step: 1,
        totalSteps: 4,
        suspects: ["Handy", "Pokermon", "Visual Overhaul", "Custom Jokers Pack"],
        testing: ["Handy", "Pokermon"],
        complete: false,
        culprit: "",
      },
    });
  } else if (method === "isolationResult") {
    const suspects = payload.opened
      ? state.recovery.suspects.slice(2)
      : state.recovery.testing;
    emitMock({
      recovery: {
        ...state.recovery,
        step: state.recovery.step + 1,
        suspects,
        testing: suspects.slice(0, Math.max(1, Math.ceil(suspects.length / 2))),
        complete: suspects.length === 1,
        culprit: suspects.length === 1 ? suspects[0] : "",
      },
    });
  } else if (method === "chooseFolder") {
    emitMock({ connected: true, folder: "ASET/Mods", message: "Folder connected" });
  } else if (method === "saveSnapshot") {
    const item = { id: `snapshot-${Date.now()}`, label: "Manual backup", createdAt: "Just now", entries: `${state.mods.length} mods · reversible` };
    emitMock({ canUndo: true, backupHistory: [item, ...(state.backupHistory || [])], history: [item, ...(state.history || [])], message: "Backup saved" });
  } else if (method === "deleteMod") {
    const mods = state.mods.filter((mod) => mod.folder !== payload.folder);
    const active = mods.filter((mod) => !mod.hidden).length;
    emitMock({ mods, counts: { ...state.counts, active, hidden: mods.length - active }, message: "Mod was permanently deleted." });
  } else if (method === "deleteMods") {
    const folders = new Set(payload.folders || []);
    const mods = state.mods.filter((mod) => !folders.has(mod.folder));
    const active = mods.filter((mod) => !mod.hidden).length;
    emitMock({ mods, counts: { ...state.counts, active, hidden: mods.length - active }, message: "Selected mods were permanently deleted." });
  } else if (method === "importMod") {
    emitMock({ message: "Choose a ZIP or folder from device storage." });
  } else if (method === "importModFolder") {
    emitMock({ message: "Choose a mod folder from device storage." });
  } else if (method === "chooseSaveFolder") {
    emitMock({ saveFolder: "Connected save folder", saveFileCount: 2, saveProfiles: ["Root folder", "Profile 1"], message: "Save folder connected" });
  } else if (method === "chooseSaveTarget") {
    emitMock({ saveTargetFolder: "Connected target folder", message: "Save target connected" });
  } else if (["pairDesktop", "selectSteamGame", "detectNative", "selectNativeApk", "buildSteam", "buildNative", "shareArtifact", "installArtifact", "previewSave", "importSave", "importDesktopSave", "exportSave", "exportHistory", "viewInstall", "repairImmVersion", "resetSettings", "setHistoryRetention", "deleteHistoryEntry"].includes(method)) {
    const messages = {
      pairDesktop: "Desktop paired on local network",
      selectSteamGame: "Game file selected",
      detectNative: "Native copy checked",
      selectNativeApk: "APK selected for preflight",
      buildSteam: "Steam build started",
      buildNative: "Native build queued",
      shareArtifact: "APK saved to the share sheet",
      installArtifact: "Install package ready",
      previewSave: "Save preview created",
      importSave: "Saves imported with a reversible backup",
      importDesktopSave: "Desktop saves imported with a reversible backup",
      exportSave: "Local save backup exported",
      exportHistory: "History export created",
      viewInstall: "Installation details opened",
      repairImmVersion: "IMM fixed for Balatro mobile version strings. Restart Balatro before opening IMM.",
      resetSettings: "Preferences reset",
      setHistoryRetention: "History retention updated",
      deleteHistoryEntry: "History entry removed",
    };
    emitMock({ message: messages[method] || "Done" });
  } else if (method === "restoreSnapshot") {
    emitMock({ message: "Snapshot restored" });
  } else if (method === "loadCatalog") {
    emitMock({ message: "Catalog updated" });
  } else if (method === "installCatalogMod" || method === "updateCatalogMod") {
    const kind = method === "updateCatalogMod" ? "update" : "install";
    emitMock({
      operation: { active: true, kind, itemId: payload.id, source: payload.source, label: kind === "update" ? "Updating mod…" : "Installing mod…" },
      message: kind === "update" ? "Updating mod…" : "Installing mod…",
    });
    setTimeout(() => {
      const installedVersion = payload.version || state.catalog.find((item) => item.id === payload.id && item.source === payload.source)?.latestVersion || "latest";
      const catalog = state.catalog.map((item) => item.id === payload.id && item.source === payload.source ? { ...item, installed: true, installedVersion, updateAvailable: false } : item);
      emitMock({ catalog, operation: { active: false, kind: "", itemId: "", source: "", label: "" }, message: kind === "update" ? `Mod updated to ${installedVersion} and enabled.` : `Mod installed at ${installedVersion} and enabled.` });
    }, 500);
  }
}

export { mockState };
