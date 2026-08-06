import { useEffect, useMemo, useState } from "react";
import {
  ArchiveRestore,
  ArrowDownAZ,
  ArrowLeft,
  Check,
  ChevronRight,
  CircleAlert,
  CircleArrowUp,
  Download,
  FileArchive,
  Filter,
  FolderOpen,
  HardDrive,
  History,
  House,
  Info,
  LayoutGrid,
  LifeBuoy,
  LoaderCircle,
  PackageSearch,
  Puzzle,
  RefreshCw,
  Save,
  Search,
  Settings2,
  Share2,
  ShieldCheck,
  Smartphone,
  Trash2,
  Undo2,
  Upload,
} from "lucide-react";
import { invoke, mockState, subscribe } from "./bridge";

const filters = ["all", "active", "inactive"];
const wallpaperOptions = [
  { id: "blueprint", label: "Blueprint Clean", image: "/wallpapers/blueprint-clean.png", tone: "blue" },
  { id: "brainstorm", label: "Brainstorm Clean", image: "/wallpapers/brainstorm-clean.png", tone: "cream" },
  { id: "menu", label: "Balatro Menu", image: "", tone: "menu" },
  { id: "graphite", label: "Graphite Solid", image: "", tone: "graphite" },
];

function readWallpaper() {
  try { return window.localStorage.getItem("bmm-wallpaper") || "graphite"; } catch { return "graphite"; }
}

function readStoredFlag(key) {
  try { return window.localStorage.getItem(key) === "true"; } catch { return false; }
}

function readRetention() {
  try { return window.localStorage.getItem("bmm-history-retention") || "20"; } catch { return "20"; }
}

function App() {
  const [state, setState] = useState(mockState);
  const [screen, setScreen] = useState("home");
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("all");
  const [historyTab, setHistoryTab] = useState("backups");
  const [wallpaper, setWallpaper] = useState(readWallpaper);
  const [advancedMode, setAdvancedMode] = useState(() => readStoredFlag("bmm-advanced"));
  const [crashReports, setCrashReports] = useState(() => readStoredFlag("bmm-crash-opt-in"));
  const [historyRetention, setHistoryRetention] = useState(readRetention);

  useEffect(() => {
    const unsubscribe = subscribe((next) => setState((previous) => ({ ...previous, ...next })));
    invoke("getState");
    return unsubscribe;
  }, []);

  useEffect(() => {
    try { window.localStorage.setItem("bmm-wallpaper", wallpaper); } catch { /* native WebView may disable storage */ }
    document.documentElement.dataset.wallpaper = wallpaper;
  }, [wallpaper]);

  useEffect(() => {
    try { window.localStorage.setItem("bmm-advanced", String(advancedMode)); } catch { /* native WebView may disable storage */ }
    document.documentElement.dataset.advanced = String(advancedMode);
  }, [advancedMode]);

  useEffect(() => {
    try { window.localStorage.setItem("bmm-crash-opt-in", String(crashReports)); } catch { /* native WebView may disable storage */ }
  }, [crashReports]);

  useEffect(() => {
    try { window.localStorage.setItem("bmm-history-retention", historyRetention); } catch { /* native WebView may disable storage */ }
  }, [historyRetention]);

  const changeHistoryRetention = (value) => {
    setHistoryRetention(value);
    invoke("setHistoryRetention", { limit: Number(value) });
  };

  const navigate = (next) => {
    setScreen(next);
    setQuery("");
  };

  return (
    <main className="app-shell">
      <div className="wallpaper-layer" aria-hidden="true" />
      <div className="noise" aria-hidden="true" />
      <Header state={state} onNavigate={navigate} />
      {screen === "home" && <HomeScreen state={state} onNavigate={navigate} />}
      {screen === "steam" && <SteamWizard state={state} onBack={() => navigate("home")} onSaves={() => navigate("saves")} />}
      {screen === "native" && <NativeWizard state={state} onBack={() => navigate("home")} onSteam={() => navigate("steam")} />}
      {screen === "mods" && (
        <ModsScreen state={state} query={query} setQuery={setQuery} filter={filter} setFilter={setFilter} onViewHistory={() => { setHistoryTab("installs"); navigate("history"); }} />
      )}
      {screen === "discover" && <DiscoverScreen state={state} />}
      {screen === "saves" && <SavesScreen state={state} />}
      {screen === "history" && <HistoryScreen state={state} tab={historyTab} setTab={setHistoryTab} />}
      {screen === "settings" && <SettingsScreen wallpaper={wallpaper} setWallpaper={setWallpaper} advancedMode={advancedMode} setAdvancedMode={setAdvancedMode} crashReports={crashReports} setCrashReports={setCrashReports} historyRetention={historyRetention} onRetentionChange={changeHistoryRetention} />}
      {screen === "help" && <HelpScreen onNavigate={navigate} />}
      {screen === "about" && <AboutScreen state={state} />}
      {screen !== "home" && <BackBar onBack={() => navigate("home")} />}
      <BottomNav screen={screen} onNavigate={navigate} />
      {state.loading && <LoadingOverlay />}
      {state.message && <Toast key={state.message} message={state.message} />}
    </main>
  );
}

function Header({ state, onNavigate }) {
  return (
    <header className="topbar">
      <div>
        <button className="brand-button" type="button" onClick={() => onNavigate("home")}>
          <span className="brand-prefix">MBM -</span><span>MOBILE</span><span>BALATRO MANAGER</span>
        </button>
        <button className="connection" type="button" onClick={() => invoke("chooseFolder", { automatic: true })}>
          <span className={state.connected ? "status-dot connected" : "status-dot"} />
          {state.connected ? "Connected" : "Connect Mods folder"}
        </button>
      </div>
      <button id="open-settings" className="icon-button" type="button" aria-label="Open settings" onClick={() => onNavigate("settings")}>
        <Settings2 aria-hidden="true" />
      </button>
    </header>
  );
}

function HomeScreen({ state, onNavigate }) {
  return (
    <section className="screen home-screen">
      <section className="mode-grid" aria-label="Create a mobile build">
        <button className="mode-card steam-mode" type="button" aria-label="Import game from Steam" onClick={() => onNavigate("steam")}>
          <HardDrive aria-hidden="true" />
          <span><strong>Import game from Steam</strong></span>
          <ChevronRight aria-hidden="true" />
        </button>
        <button className="mode-card native-mode" type="button" aria-label="Import from phone APK" onClick={() => onNavigate("native")}>
          <Smartphone aria-hidden="true" />
          <span><strong>Import from phone APK</strong></span>
          <ChevronRight aria-hidden="true" />
        </button>
      </section>

      <section className="quick-grid" aria-label="Library shortcuts">
        <QuickAction icon={LayoutGrid} label="Mods" detail={`${state.counts?.active || 0} active`} onClick={() => onNavigate("mods")} tone="mint" />
        <QuickAction icon={PackageSearch} label="Discover" detail="Curated sources" onClick={() => onNavigate("discover")} tone="blue" />
        <QuickAction icon={Save} label="Saves" detail="Import or export" onClick={() => onNavigate("saves")} tone="gold" />
        <QuickAction icon={History} label="History" detail="Backups + installs" onClick={() => onNavigate("history")} tone="red" />
      </section>

    </section>
  );
}

function QuickAction({ icon: Icon, label, detail, onClick, tone }) {
  return <button className={`quick-action ${tone}`} type="button" onClick={onClick}><Icon aria-hidden="true" /><span><strong>{label}</strong><small>{detail}</small></span><ChevronRight aria-hidden="true" /></button>;
}

function WizardShell({ title, eyebrow, step, steps, children, onBack }) {
  return <section className="screen wizard-screen">
    <div className="wizard-heading"><button className="back-button" type="button" onClick={onBack} aria-label="Back"><ArrowLeft /></button><div><p className="eyebrow">{eyebrow}</p><h2>{title}</h2></div></div>
    <div className="wizard-steps" aria-label={`Step ${step} of ${steps.length}`}>
      {steps.map((label, index) => <span key={label} className={index + 1 === step ? "current" : index + 1 < step ? "done" : ""}><i>{index + 1 < step ? <Check /> : index + 1}</i>{label}</span>)}
    </div>
    {children}
  </section>;
}

function SteamWizard({ state, onBack, onSaves }) {
  const [step, setStep] = useState(1);
  const [buildRequested, setBuildRequested] = useState(false);
  const [importSaves, setImportSaves] = useState(true);
  const [profile, setProfile] = useState("mobile");
  const [helperAddress, setHelperAddress] = useState("");
  const [pairCode, setPairCode] = useState("");
  const manifestGame = useMemo(() => { try { const parsed = JSON.parse(state.desktopManifest || "{}"); return parsed.games?.[0] || null; } catch { return null; } }, [state.desktopManifest]);
  const manifestMods = useMemo(() => { try { return JSON.parse(state.desktopManifest || "{}").mods || {}; } catch { return {}; } }, [state.desktopManifest]);
  const manifestSaves = useMemo(() => { try { return JSON.parse(state.desktopManifest || "{}").saves || {}; } catch { return {}; } }, [state.desktopManifest]);
  const builderAvailable = useMemo(() => { try { return Boolean(JSON.parse(state.desktopManifest || "{}").builderAvailable); } catch { return false; } }, [state.desktopManifest]);
  const desktopBuild = useMemo(() => { try { return JSON.parse(state.desktopBuild || "{}"); } catch { return {}; } }, [state.desktopBuild]);
  const steps = ["Connect", "Select", "Configure", "Build"];
  const next = () => setStep((value) => Math.min(4, value + 1));
  const previous = () => setStep((value) => Math.max(1, value - 1));
  useEffect(() => { window.scrollTo(0, 0); }, [step]);
  const build = () => { setBuildRequested(true); invoke("buildSteam", { profile, importSaves }); };
  return <WizardShell title="Steam port" eyebrow="LOCAL DESKTOP ROUTE" step={step} steps={steps} onBack={onBack}>
    {step === 1 && <WizardPanel icon={HardDrive} title="Connect your desktop" text="MBM uses a tiny LAN-only helper. It scans only the Steam library you approve; nothing is uploaded."><button className="secondary-button" type="button" onClick={next}>I already have a .zip or .love file</button><Checklist items={["Install the portable MBM helper on Windows", "Run it and copy the local address plus six-digit code", "Keep your phone and PC on the same Wi‑Fi"]} /><div className="pair-fields"><label className="field-label">Helper address<input value={helperAddress} onChange={(event) => setHelperAddress(event.target.value)} placeholder="http://192.168.1.20:19077" inputMode="url" /></label><label className="field-label">Pairing code<input value={pairCode} onChange={(event) => setPairCode(event.target.value.replace(/\D/g, "").slice(0, 6))} placeholder="123456" inputMode="numeric" /></label></div><button className="primary-button" type="button" onClick={() => { invoke("pairDesktop", { address: helperAddress, code: pairCode }); next(); }}>Pair desktop</button><button className="primary-button" type="button" onClick={next}>Continue</button>{state.desktopPaired && <InfoCallout>Desktop paired. The helper manifest is ready for the next step.</InfoCallout>}</WizardPanel>}
    {step === 2 && <WizardPanel icon={FolderOpen} title="Choose the game copy" text="The helper detects Steam libraries and Balatro. If detection fails, choose a .love, .zip, or folder and MBM will send only that source to the paired PC."><div className="file-choice"><FileArchive /><div><strong>{state.steamSourceUploaded ? state.steamSourceName : (manifestGame?.name || state.gameFile || "Balatro.exe / Balatro.love")}</strong><small>{state.steamSourceUploaded ? "Uploaded to the paired helper · temporary build source" : (manifestGame ? `${manifestGame.version || "version unknown"} · ${manifestGame.architecture || "architecture unknown"}` : "Detected or selected locally · version check required")}</small></div><button className="icon-button bordered" type="button" onClick={() => invoke("selectSteamGame")} aria-label="Choose file"><FolderOpen /></button></div><div className="source-actions"><button className="secondary-button" type="button" onClick={() => invoke("selectSteamGame")}>Choose .love or .zip</button><button className="secondary-button" type="button" onClick={() => invoke("selectSteamFolder")}>Choose folder</button></div><button className="primary-button" type="button" onClick={next}>Continue</button>{manifestGame?.executable && <InfoCallout>Detected executable: {manifestGame.executable}</InfoCallout>}{state.steamSourceUploaded && <InfoCallout>Manual source ready. The next build uses it instead of the detected game.</InfoCallout>}{builderAvailable ? <InfoCallout>Builder ready on the paired PC. We also check Steamodded, Lovely, mods, architecture, and version before packaging.</InfoCallout> : <InfoCallout>Builder not detected yet. Place the bundled Maker beside the MBM helper and restart it.</InfoCallout>}{manifestMods.available && <InfoCallout>Desktop scan: {manifestMods.folders?.length || 0} mod folders found{manifestMods.frameworks?.length ? ` · frameworks: ${manifestMods.frameworks.join(", ")}` : ""}.</InfoCallout>}<button className="text-button" type="button" onClick={previous}>Back</button></WizardPanel>}
    {step === 3 && <WizardPanel icon={Settings2} title="Make it yours" text="Choose a safe profile, review mods, and decide what happens to your progress."><label className="field-label">Build profile<select value={profile} onChange={(event) => setProfile(event.target.value)}><option value="mobile">Mobile safe</option><option value="safe">Safe mode</option><option value="custom">Custom</option></select></label><div className="option-list"><label><input type="checkbox" checked={importSaves} onChange={(event) => setImportSaves(event.target.checked)} /><span><strong>Do you want to import your progress?</strong><small>{manifestSaves.available ? `${manifestSaves.files || 0} desktop save files found. Review conflicts and create a backup before copying.` : "You can review the source profile and conflicts in Saves before applying."}</small></span></label><label><input type="checkbox" defaultChecked /><span><strong>Keep mods reversible</strong><small>Disable incompatible mods instead of deleting them.</small></span></label></div>{manifestSaves.available && <><InfoCallout>Desktop saves: {manifestSaves.files || 0} file(s){manifestSaves.profiles?.length ? ` · profiles ${manifestSaves.profiles.join(", ")}` : ""}. Nothing is imported until you explicitly review or apply it.</InfoCallout><button className="secondary-button" type="button" onClick={onSaves}>Review saves and conflicts</button></>}{manifestMods.folders?.length > 0 && <div className="wizard-mod-review"><strong>Mods found on desktop</strong>{manifestMods.folders.slice(0, 8).map((folder) => <span key={folder}>{folder}</span>)}{manifestMods.folders.length > 8 && <small>+{manifestMods.folders.length - 8} more in Mods</small>}</div>}{manifestMods.frameworks?.length > 0 && <InfoCallout>Frameworks detected: {manifestMods.frameworks.join(", ")}. Review dependencies before disabling them.</InfoCallout>}<InfoCallout>Active mods: {state.counts?.active || 0}. You can change individual mods later in Library.</InfoCallout><button className="primary-button" type="button" onClick={next}>Review build</button><button className="text-button" type="button" onClick={previous}>Back</button></WizardPanel>}
    {step === 4 && <WizardPanel icon={Download} title="Build and install" text="The paired helper builds locally from the selected Steam copy or manual source. MBM never presents a placeholder artifact.">{!buildRequested && <><div className="review-card"><Check /><div><strong>{builderAvailable ? "Ready to build" : "Builder setup needed"}</strong><small>{builderAvailable ? `${profile} profile · progress ${importSaves ? "included after review" : "not imported"}` : "The helper must report the upstream Maker as ready before a build can start."}</small></div></div><button className="primary-button" type="button" disabled={!builderAvailable} onClick={build}>Build APK</button></>}{buildRequested && <><InfoCallout>{desktopBuild.status === "completed" ? "Verified APK ready. Install it here, save it, or share it." : desktopBuild.status === "failed" ? (desktopBuild.error || "Desktop build failed.") : `Desktop build ${desktopBuild.status || "queued"}. Keep the helper running and this screen open.`}</InfoCallout>{desktopBuild.status === "completed" && <div className="artifact-actions"><button className="primary-button" type="button" onClick={() => invoke("installArtifact")}>Install on this phone</button><button className="secondary-button" type="button" onClick={() => invoke("downloadDesktopArtifact")}>Save APK</button><button className="secondary-button" type="button" onClick={() => invoke("shareArtifact")}>Share APK</button></div>}</>}<button className="text-button" type="button" onClick={previous}>Back</button></WizardPanel>}
  </WizardShell>;
}

function NativeWizard({ state, onBack, onSteam }) {
  const [step, setStep] = useState(1);
  const steps = ["Detect", "Preflight", "Result"];
  const supported = state.nativeCompatibility === "supported";
  const personalSource = state.nativeCompatibility === "playstore-source";
  const desktopManifest = useMemo(() => { try { return JSON.parse(state.desktopManifest || "{}"); } catch { return {}; } }, [state.desktopManifest]);
  const nativeBuilderReady = Boolean(desktopManifest.nativeBuilderAvailable);
  const desktopBuild = useMemo(() => { try { return JSON.parse(state.desktopBuild || "{}"); } catch { return {}; } }, [state.desktopBuild]);
  useEffect(() => { invoke("detectNative"); }, []);
  useEffect(() => { window.scrollTo(0, 0); }, [step]);
  return <WizardShell title="Native Android" eyebrow="PLAY STORE / APK ROUTE" step={step} steps={steps} onBack={onBack}>
    {step === 1 && <WizardPanel icon={Smartphone} title="Find your installed copy" text="MBM checks for Balatro as soon as this screen opens. Detection is read-only: it never bypasses DRM, licensing, or the Play Store signature. You can also select a user-owned APK as a fallback source."><button className="primary-button" type="button" onClick={() => setStep(2)}>Review detected copy</button><button className="secondary-button" type="button" onClick={() => { invoke("selectNativeApk"); setStep(2); }}>Select APK or split APK</button>{state.nativePreflight && !state.nativePreflight.startsWith("Select an APK") && <InfoCallout>{state.nativePreflight}</InfoCallout>}<InfoCallout>{personalSource ? "This official copy can be copied read-only into a separate personal build. The Play Store app itself will not be changed." : "Install the official Play Store Balatro and launch it once, or choose a user-owned APK, to continue."}</InfoCallout></WizardPanel>}
    {step === 2 && <WizardPanel icon={ShieldCheck} title="Safety preflight" text="MBM verifies the package identity and then prepares an isolated copy. The new package is separate from the official Play Store install."><div className="checklist-card"><CheckRow label="Package and version" status={personalSource ? "Detected" : "Review"} /><CheckRow label="Source APK" status={personalSource ? "Read-only" : "Select"} /><CheckRow label="Original app" status={personalSource ? "Untouched" : "Review"} /><CheckRow label="Mod support" status={personalSource && nativeBuilderReady ? "Ready" : personalSource ? "Pair helper" : supported ? "Review" : "Blocked"} danger={!personalSource && !supported} /></div>{state.nativePreflight && <InfoCallout>{state.nativePreflight}</InfoCallout>}{personalSource && !nativeBuilderReady && <InfoCallout>Pair the Windows helper first. It must report “personal Play Store builder ready” before MBM can create the APK.</InfoCallout>}<button className="primary-button" type="button" onClick={() => setStep(3)}>{personalSource ? "Continue" : supported ? "Continue" : "See safe fallback"}</button><button className="text-button" type="button" onClick={() => setStep(1)}>Back</button></WizardPanel>}
    {step === 3 && <WizardPanel icon={personalSource ? Check : CircleAlert} title={personalSource ? "Create personal mod-capable copy" : "Native patch not safe"} text={personalSource ? "MBM sends the installed base APK over your local paired connection, builds a separate com.unofficial.balatro package, and leaves the Play Store app untouched." : "This copy cannot be patched safely. Use the Steam/local route instead."}>{personalSource ? <><InfoCallout>The helper uses the open-source portrait builder, bundles Lovely and Steamodded, and returns a signed APK for your phone. Your source APK stays temporary and is never uploaded to Drive.</InfoCallout><button className="primary-button" type="button" disabled={!state.desktopPaired || !nativeBuilderReady} onClick={() => invoke("buildNativePersonal")}>{desktopBuild.status === "running" ? "Building personal copy…" : "Create personal APK"}</button>{desktopBuild.status && <InfoCallout>{desktopBuild.status === "completed" ? "Personal APK built. Install it here, save it, or share it." : desktopBuild.status === "failed" ? (desktopBuild.error || "Personal build failed.") : `Personal build ${desktopBuild.status}. Keep the helper running and this screen open.`}</InfoCallout>}{desktopBuild.status === "completed" && <div className="artifact-actions"><button className="primary-button" type="button" onClick={() => invoke("installArtifact")}>Install on this phone</button><button className="secondary-button" type="button" onClick={() => invoke("downloadDesktopArtifact")}>Save APK</button><button className="secondary-button" type="button" onClick={() => invoke("shareArtifact")}>Share APK</button></div>}</> : <><div className="fallback-card"><HardDrive /><div><strong>Use Steam / local route</strong><small>It is the supported path for modded Balatro on Android.</small></div></div><button className="primary-button" type="button" onClick={onSteam}>Go to Steam port</button></>}<button className="text-button" type="button" onClick={() => setStep(2)}>Back</button></WizardPanel>}
  </WizardShell>;
}

function WizardPanel({ icon: Icon, title, text, children }) { return <section className="wizard-panel"><div className="panel-icon"><Icon /></div><h3>{title}</h3><p className="panel-text">{text}</p>{children}</section>; }
function Checklist({ items }) { return <ul className="check-list">{items.map((item) => <li key={item}><Check />{item}</li>)}</ul>; }
function InfoCallout({ children }) { return <div className="info-callout"><Info /> <span>{children}</span></div>; }
function CheckRow({ label, status, danger }) { return <div className={`check-row ${danger ? "danger" : ""}`}><span>{label}</span><strong>{status}</strong></div>; }
function modCategory(mod) { const text = `${mod.name || ""} ${mod.folder || ""} ${mod.description || ""}`.toLocaleLowerCase("en"); if (/steamodded|smods|lovely|framework|technical|debug|performance|imm/.test(text)) return "Technical"; if (/visual|texture|skin|theme|shader|display/.test(text)) return "Visual"; if (/content|pokemon|pokermon|joker|deck|card|edition/.test(text)) return "Content"; return "Quality of Life"; }
function activeOperations(state) { return Array.isArray(state.operations) ? state.operations : state.operation?.active ? [state.operation] : []; }
function operationText(operation) {
  if (!operation) return "";
  const label = operation.label || "Working…";
  return operation.status === "queued" ? `Queued · ${label.replace(/…$/, "")}` : label;
}
function ModsScreen({ state, query, setQuery, filter, setFilter, onViewHistory }) {
  const [selected, setSelected] = useState(() => new Set());
  const [category, setCategory] = useState("All");
  const [sort, setSort] = useState("name");
  const visibleMods = useMemo(() => (state.mods || []).filter((mod) => {
    const matchesFilter = filter === "all" || (filter === "active" ? !mod.hidden : mod.hidden);
    const matchesCategory = category === "All" || modCategory(mod) === category;
    const normalized = query.trim().toLocaleLowerCase("en");
    return matchesFilter && matchesCategory && (!normalized || [mod.name, mod.folder, mod.version, ...(mod.diagnostics || [])].join(" ").toLocaleLowerCase("en").includes(normalized));
  }).sort((a, b) => sort === "updated" ? (b.modifiedAt || 0) - (a.modifiedAt || 0) : sort === "status" ? Number(a.hidden) - Number(b.hidden) : (a.name || a.folder).localeCompare(b.name || b.folder)), [category, filter, query, sort, state.mods]);
  const selectedFolders = [...selected].filter((folder) => visibleMods.some((mod) => mod.folder === folder));
  const clearSelection = () => setSelected(new Set());
  const updateSelection = (folder, checked) => setSelected((current) => { const next = new Set(current); if (checked) next.add(folder); else next.delete(folder); return next; });
  const bulkToggle = (hidden) => { if (selectedFolders.length) invoke("toggleMods", { folders: selectedFolders, hidden }); };
  const bulkDelete = () => { if (!selectedFolders.length) return; if (window.confirm(`Permanently delete ${selectedFolders.length} selected mod(s)? This cannot be undone.`)) invoke("deleteMods", { folders: selectedFolders }); };
  const operations = activeOperations(state);
  const operationActive = operations.length > 0;
  const exclusiveActive = operations.some((operation) => operation.exclusive);
  const importing = operations.find((operation) => operation.kind === "import");
  const updatingAll = operations.find((operation) => operation.kind === "update-all");
  const cleaning = operations.find((operation) => operation.kind === "cleanup");
  const askUpdateAll = () => {
    if (!state.updatesAvailable) return invoke("updateAllMods");
    if (window.confirm(`Update all ${state.updatesAvailable} catalog-matched mod(s)? Each mod uses rollback storage while it updates.`)) invoke("updateAllMods");
  };
  const askClean = () => {
    if (window.confirm("Permanently remove known MBM leftovers and OS junk? Real mods, disabled mods and backups will not be touched.")) invoke("cleanAllJunk");
  };
  return <section className="screen screen-mods"><div className="screen-heading"><div><p className="eyebrow">LIBRARY</p><h2>Mods</h2></div><div className="screen-actions"><button className="icon-button bordered" type="button" aria-label={importing ? "Importing mod" : "Import mod ZIP"} disabled={operationActive} onClick={() => invoke("importMod")}>{importing ? <LoaderCircle className="spin" /> : <Upload />}<span className="sr-only">{importing ? "Importing mod" : "Import mod ZIP"}</span></button><button className="icon-button bordered" type="button" aria-label="Import mod folder" disabled={operationActive} onClick={() => invoke("importModFolder")}><FolderOpen /><span className="sr-only">Import mod folder</span></button>{state.desktopPaired && state.desktopModSummary?.available && <button className="icon-button bordered" type="button" aria-label="Import desktop mods" title="Import desktop mods" disabled={operationActive} onClick={() => invoke("importDesktopMods")}><HardDrive /><span className="sr-only">Import desktop mods</span></button>}</div></div><div className="health-card compact-health"><div className="stat-grid"><Stat value={state.counts?.active || 0} label="active" tone="success" /><Stat value={state.counts?.hidden || 0} label="inactive" /><Stat value={visibleMods.length} label="installed" tone="info" /></div><div className="health-actions"><button type="button" aria-label="Save backup" title="Save backup" disabled={operationActive} onClick={() => invoke("saveSnapshot")}><Save /></button><button type="button" aria-label="Refresh mods" title="Refresh mods" disabled={operationActive} onClick={() => invoke("refresh")}><RefreshCw /></button><button type="button" aria-label="Undo last change" title="Undo last change" onClick={() => invoke("undo")} disabled={operationActive || !state.canUndo}><Undo2 /></button></div><div className="maintenance-actions"><button type="button" className="update-all-button" disabled={operationActive || !state.connected} onClick={askUpdateAll}>{updatingAll ? <LoaderCircle className="spin" /> : <CircleArrowUp />}<span>{updatingAll ? operationText(updatingAll) : `Update all${state.updatesAvailable ? ` (${state.updatesAvailable})` : ""}`}</span></button><button type="button" className="clean-junk-button" disabled={operationActive || !state.connected} onClick={askClean}>{cleaning ? <LoaderCircle className="spin" /> : <Trash2 />}<span>{cleaning ? operationText(cleaning) : `Clean junk${state.junkCount ? ` (${state.junkCount})` : ""}`}</span></button></div></div><div className="search-row"><label className="search-box"><Search /><span className="sr-only">Search mods</span><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="SEARCH MODS" /></label><button className="icon-button bordered" type="button" aria-label="Open filter options" onClick={() => setFilter(filter === "all" ? "active" : filter === "active" ? "inactive" : "all")}><Settings2 /></button></div><div className="filter-strip mod-filter-strip"><div className="select-control"><Filter aria-hidden="true" /><select value={category} onChange={(event) => setCategory(event.target.value)} aria-label="Filter mods by category"><option>All</option><option>Technical</option><option>Content</option><option>Visual</option><option>Quality of Life</option></select></div><div className="select-control"><ArrowDownAZ aria-hidden="true" /><select value={sort} onChange={(event) => setSort(event.target.value)} aria-label="Sort mods"><option value="name">Name</option><option value="updated">Last updated</option><option value="status">Active first</option></select></div></div><div className="segments" role="tablist" aria-label="Filter mods">{filters.map((value) => <button key={value} type="button" role="tab" aria-selected={filter === value} className={filter === value ? "selected" : ""} onClick={() => setFilter(value)}>{value}</button>)}</div>{selectedFolders.length > 0 && <div className="selection-actions"><span>{selectedFolders.length} selected</span><button type="button" disabled={operationActive} onClick={() => bulkToggle(false)}>Enable</button><button type="button" disabled={operationActive} onClick={() => bulkToggle(true)}>Disable</button><button type="button" disabled={operationActive} onClick={bulkDelete}>Delete selected</button><button type="button" disabled={operationActive} onClick={clearSelection}>Clear</button></div>}<div className="list-heading"><p>{visibleMods.length} mods</p>{operations.length > 0 && <span className="queue-summary">{operations.filter((operation) => operation.status === "queued").length} queued</span>}</div><div className="mod-list">{visibleMods.map((mod) => <ModCard key={mod.folder} mod={mod} catalog={state.catalog || []} operations={operations} globalBusy={exclusiveActive} selected={selected.has(mod.folder)} onSelect={updateSelection} onViewHistory={onViewHistory} />)}{visibleMods.length === 0 && <div className="empty-state"><Search /><h2>No mods found</h2><p>Try another filter or search term.</p></div>}</div></section>;
}
function Stat({ value, label, tone = "muted" }) { return <div className={`stat ${tone}`}><strong>{value}</strong><span>{label}</span></div>; }
function normalizeIdentity(value) { return String(value || "").toLocaleLowerCase("en").replace(/[^a-z0-9]/g, ""); }
function catalogForMod(mod, catalog) {
  const identities = new Set([mod.id, mod.name, mod.folder].map(normalizeIdentity).filter(Boolean));
  return catalog.find((item) => [item.id, item.name, item.folderName].map(normalizeIdentity).some((value) => identities.has(value))) || null;
}
function releaseList(item) {
  const releases = Array.isArray(item?.versions) ? item.versions.filter((release) => release?.version) : [];
  if (releases.length) return releases;
  if (!item) return [];
  return [{ version: item.latestVersion || item.version || "latest", downloadUrl: item.downloadUrl || item.downloadURL || "" }];
}
function ModCard({ mod, catalog, operations, globalBusy, selected, onSelect, onViewHistory }) {
  const [expanded, setExpanded] = useState(false);
  const [selectedVersionOverride, setSelectedVersionOverride] = useState("");
  const active = !mod.hidden;
  const image = mod.thumbnail || mod.icon || "";
  const framework = /steamodded|lovely|framework/i.test(`${mod.name} ${mod.folder}`);
  const imm = /(^|\W)imm($|\W)|balatro_imm/i.test(`${mod.id || ""} ${mod.name} ${mod.folder}`);
  const catalogItem = catalogForMod(mod, catalog);
  const releases = releaseList(catalogItem);
  const defaultVersion = catalogItem?.latestVersion || catalogItem?.version || releases[0]?.version || "";
  const selectedVersion = releases.some((release) => release.version === selectedVersionOverride) ? selectedVersionOverride : defaultVersion;
  const selectedRelease = releases.find((release) => release.version === selectedVersion);
  const operation = operations.find((candidate) => (candidate.itemId === catalogItem?.id && candidate.source === catalogItem?.source) || candidate.itemId === mod.folder);
  const busy = Boolean(operation);
  const blocked = globalBusy || busy;
  const askDelete = () => {
    const dependencyText = (mod.dependents || []).length ? `\nDependents: ${mod.dependents.join(", ")}.` : "";
    const frameworkText = framework ? "\nThis looks like a framework; dependent mods may stop working." : "";
    if (window.confirm(`Permanently delete ${mod.name}? This cannot be undone.${dependencyText}${frameworkText}`)) invoke("deleteMod", { folder: mod.folder });
  };
  const update = () => catalogItem && invoke("updateCatalogMod", { id: catalogItem.id, source: catalogItem.source, version: selectedVersion, downloadUrl: selectedRelease?.downloadUrl || selectedRelease?.downloadURL || catalogItem.downloadUrl || catalogItem.downloadURL || "" });
  const canLoadVersions = catalogItem?.source === "BMI" && releases.length <= 1;
  const loadVersions = () => catalogItem && invoke("loadCatalogVersions", { id: catalogItem.id, source: catalogItem.source });
  return <article className={`mod-card ${active ? "mod-active" : "mod-inactive"}`}>
    <div className="mod-icon">{image ? <img src={image} alt={`${mod.name} icon`} /> : <Puzzle />}</div>
    <div className="mod-copy"><h2>{mod.name}</h2><p>{mod.version || "unknown version"} · {mod.folder}</p>{operation && <span className={`mod-operation ${operation.status}`}><LoaderCircle className={operation.status === "running" ? "spin" : ""} />{operationText(operation)}</span>}{mod.diagnostics?.[0] && mod.severity === "error" && <span className="diagnostic error" title={mod.diagnostics[0]}><CircleAlert />{mod.diagnostics[0]}</span>}{expanded && <div className="mod-details"><p>{mod.description || "No description available."}</p><p><strong>Dependencies:</strong> {(mod.dependencies || []).join(", ") || "None listed"}</p>{catalogItem ? <div className="installed-version-tools"><div className="catalog-version-row"><span>Current: <strong>{mod.version}</strong></span><span>Catalog: <strong>{catalogItem.versionKind === "source-revision" ? `Latest source · ${catalogItem.latestVersion || catalogItem.version}` : catalogItem.latestVersion || catalogItem.version}</strong></span></div>{catalogItem.updateState === "unknown" && <p className="version-status unknown">{catalogItem.updateReason}</p>}{canLoadVersions && <button className="load-versions-button" type="button" disabled={blocked} onClick={loadVersions}>{busy ? <><LoaderCircle className="spin" /> Loading versions…</> : "Load published versions"}</button>}<label className="catalog-version-picker">Target version<select aria-label={`Version for installed ${mod.name}`} value={selectedVersion} disabled={blocked} onChange={(event) => setSelectedVersionOverride(event.target.value)}>{releases.map((release) => <option key={release.version} value={release.version}>{catalogItem.versionKind === "source-revision" ? `Latest source · ${release.version}` : release.version}{release.version === mod.version ? " (installed)" : release.version === defaultVersion ? " (latest)" : ""}</option>)}</select></label><button className="version-update-button" type="button" disabled={blocked || !selectedVersion} onClick={update}>{busy ? <><LoaderCircle className={operation.status === "running" ? "spin" : ""} /> {operationText(operation)}</> : selectedVersion === mod.version ? "Reinstall selected version" : `Update to ${catalogItem.versionKind === "source-revision" ? "latest source" : selectedVersion}`}</button></div> : <p className="catalog-unmatched">No catalog releases matched this local mod yet.</p>}<div className="detail-actions"><button type="button" onClick={onViewHistory}>Installation history</button>{mod.website && <button type="button" onClick={() => invoke("openModWebsite", { url: mod.website })}>Source / license</button>}{imm && <button type="button" disabled={blocked} onClick={() => invoke("repairImmVersion", { folder: mod.folder })}>Fix IMM mobile version</button>}<button type="button" className="danger-action" disabled={blocked} onClick={askDelete}>Delete permanently</button></div></div>}</div>
    <div className="mod-actions"><label className="mod-select"><span className="sr-only">Select {mod.name}</span><input type="checkbox" checked={selected} disabled={blocked} onChange={(event) => onSelect(mod.folder, event.target.checked)} /></label><button className="icon-button mini" type="button" aria-label={`Options for ${mod.name}`} title="Mod options" onClick={() => setExpanded((value) => !value)}><Settings2 /></button><label className="switch"><span className="sr-only">{active ? `Disable ${mod.name}` : `Enable ${mod.name}`}</span><input type="checkbox" checked={active} disabled={blocked} onChange={(event) => invoke("toggleMod", { folder: mod.folder, hidden: !event.target.checked })} /><span className="switch-track"><span /></span></label></div>
  </article>;
}

function DiscoverScreen({ state }) {
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState("popular");
  const [category, setCategory] = useState("All");
  const [source, setSource] = useState("All");
  useEffect(() => { invoke("loadCatalog"); }, []);
  const categories = ["All", "Quality of Life", "Content", "Technical", "Visual", "Community"];
  const sources = ["All", "BMI", "Thunderstore", "Awesome Balatro"];
  const results = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase("en");
    return [...(state.catalog || [])]
      .filter((item) => (source === "All" || item.source === source)
        && (category === "All" || item.categories?.includes(category))
        && (!normalized || [item.name, item.author, item.summary, ...(item.categories || [])].join(" ").toLocaleLowerCase("en").includes(normalized)))
      .sort((a, b) => sort === "name" ? a.name.localeCompare(b.name) : sort === "updated" ? (b.updatedAt || "").localeCompare(a.updatedAt || "") : (b.downloads || 0) - (a.downloads || 0));
  }, [category, query, sort, source, state.catalog]);
  const operations = activeOperations(state);
  const globalBusy = operations.some((operation) => operation.exclusive);
  return <section className="screen discover-screen"><div className="screen-heading"><div><p className="eyebrow">CURATED SOURCES</p><h2>Discover</h2></div><button className="icon-button bordered" type="button" aria-label="Refresh catalog" disabled={operations.length > 0} onClick={() => invoke("loadCatalog")}><RefreshCw /></button></div><p className="lead">Browse and install new mods</p><div className="search-row"><label className="search-box"><Search /><span className="sr-only">Search catalog</span><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="SEARCH THE CATALOG" /></label><button className="icon-button bordered" type="button" aria-label="Filter and sort" onClick={() => setSort(sort === "popular" ? "updated" : sort === "updated" ? "name" : "popular")}><Settings2 /></button></div><div className="filter-strip"><div className="select-control"><Filter aria-hidden="true" /><select value={category} onChange={(event) => setCategory(event.target.value)} aria-label="Filter by category">{categories.map((item) => <option key={item}>{item}</option>)}</select></div><div className="select-control"><ArrowDownAZ aria-hidden="true" /><select value={sort} onChange={(event) => setSort(event.target.value)} aria-label="Sort catalog"><option value="popular">Popular</option><option value="updated">Last updated</option><option value="name">Name</option></select></div></div><div className="source-tabs" role="tablist" aria-label="Catalog source">{sources.map((value) => <button key={value} type="button" role="tab" aria-selected={source === value} className={source === value ? "selected" : ""} onClick={() => setSource(value)}>{value === "All" ? "All" : value === "BMI" ? "Mod Index" : value}</button>)}</div><div className="catalog-list">{results.map((item) => <CatalogCard key={`${item.source}:${item.id}`} item={item} connected={state.connected} operations={operations} globalBusy={globalBusy} />)}{results.length === 0 && <div className="empty-state"><PackageSearch /><h2>No catalog matches</h2><p>Try another category or source.</p></div>}</div></section>;
}
function CatalogCard({ item, connected, operations, globalBusy }) {
  const image = item.thumbnailUrl || item.thumbnail || "";
  const releases = Array.isArray(item.versions) && item.versions.length
    ? item.versions
    : [{ version: item.latestVersion || item.version || "latest", downloadUrl: item.downloadUrl || item.downloadURL || "" }];
  const defaultVersion = item.latestVersion || item.version || releases[0].version;
  const [selectedVersionOverride, setSelectedVersionOverride] = useState("");
  const selectedVersion = releases.some((release) => release.version === selectedVersionOverride)
    ? selectedVersionOverride
    : defaultVersion;
  const installable = item.source === "BMI" || releases.some((release) => Boolean(release.downloadUrl || release.downloadURL)) || Boolean(item.downloadUrl || item.downloadURL);
  const sourceOnly = item.source === "Awesome Balatro" && !installable;
  const currentVersion = item.installedVersion || "not installed";
  const latestVersion = item.latestVersion || item.version || "unknown";
  const updateAvailable = Boolean(item.installed && item.updateAvailable);
  const action = item.installed ? "updateCatalogMod" : "installCatalogMod";
  const operation = operations.find((candidate) => candidate.itemId === item.id && candidate.source === item.source);
  const busy = Boolean(operation);
  const blocked = globalBusy || busy;
  const label = busy
    ? operationText(operation)
    : sourceOnly
      ? "Source only"
      : item.installed
        ? (selectedVersion === currentVersion
          ? "Reinstall"
          : updateAvailable && selectedVersion === latestVersion
            ? "Update"
            : "Switch version")
        : "Install";
  const runInstall = () => invoke(action, { id: item.id, source: item.source, version: selectedVersion, downloadUrl: releases.find((release) => release.version === selectedVersion)?.downloadUrl || item.downloadUrl || item.downloadURL || "" });
  const canLoadVersions = item.source === "BMI" && releases.length <= 1;
  const loadVersions = () => invoke("loadCatalogVersions", { id: item.id, source: item.source });
  return <article className="catalog-card">
    {image ? <img className="catalog-thumb" src={image} alt={`${item.name} icon`} loading="lazy" /> : <div className="catalog-thumb puzzle-thumb"><Puzzle /></div>}
    <div className="catalog-topline"><span className="source-badge">{item.source === "BMI" ? "Mod Index" : item.source}</span><span>{item.source === "Awesome Balatro" ? `${item.downloads?.toLocaleString("en-US") || 0} stars` : `${item.downloads?.toLocaleString("en-US") || 0} downloads`}</span></div>
    <h3>{item.name}</h3>
    <p className="catalog-author">{item.author}</p>
    <div className="catalog-version-row"><span>Installed: <strong>{currentVersion}</strong></span><span>Latest published: <strong>{item.versionKind === "source-revision" ? `source · ${latestVersion}` : latestVersion}</strong></span></div>
    <p className="catalog-summary">{item.summary || "No description provided."}</p>
    <div className="catalog-meta"><span>{item.categories?.[0] || "Community"}</span>{item.requiresSteamodded && <span>Requires Steamodded</span>}{updateAvailable && <span className="catalog-update-available">Update available</span>}{item.installed && item.updateState === "unknown" && <span className="catalog-update-unknown" title={item.updateReason}>Version check needed</span>}</div>
    {canLoadVersions && <button className="load-versions-button" type="button" disabled={blocked} onClick={loadVersions}>{busy ? <><LoaderCircle className="spin" /> Loading versions…</> : "Load published versions"}</button>}
    <label className="catalog-version-picker">Version<select aria-label={`Version for ${item.name}`} value={selectedVersion} disabled={blocked} onChange={(event) => setSelectedVersionOverride(event.target.value)}>{releases.map((release) => <option key={release.version} value={release.version}>{item.versionKind === "source-revision" ? `Latest source · ${release.version}` : release.version}{release.version === currentVersion ? " (installed)" : release.version === latestVersion ? " (latest)" : ""}</option>)}</select></label>
    <div className="catalog-actions"><button className={item.installed ? "installed-button" : "install-button"} type="button" disabled={!installable || !connected || blocked} title={sourceOnly ? "This entry has no downloadable archive yet." : !connected ? "Connect your Mods folder before installing." : undefined} onClick={runInstall}>{busy && <LoaderCircle className={operation.status === "running" ? "spin" : ""} />}{label}</button>{item.homepage && <button className="icon-button mini" type="button" aria-label={`Open source for ${item.name}`} onClick={() => invoke("openModWebsite", { url: item.homepage })}><ChevronRight /></button>}</div>
  </article>;
}

function SavesScreen({ state }) {
  const [option, setOption] = useState("review");
  const [saveProfile, setSaveProfile] = useState("Root folder");
  const [desktopProfile, setDesktopProfile] = useState("");
  const options = [{ id: "selected", label: "Yes, import this profile folder", detail: "The source folder you select is treated as the profile to import." }, { id: "all", label: "Yes, import all compatible files", detail: "Copy every bounded source file into the destination and keep a restore point." }, { id: "none", label: "No, start clean", detail: "Build without copying existing progress." }, { id: "review", label: "Review files first", detail: "Inspect the bounded file list before deciding." }];
  const connected = Boolean(state.saveFolder);
  const targetConnected = Boolean(state.saveTargetFolder);
  const importing = option === "selected" || option === "all";
  const localProfiles = [...new Set(["Root folder", ...(Array.isArray(state.saveProfiles) ? state.saveProfiles : [])])];
  const desktop = state.desktopSaveSummary || {};
  const desktopProfiles = Array.isArray(desktop.profiles) ? desktop.profiles : [];
  const desktopReady = Boolean(state.desktopPaired && desktop.available);
  return <section className="screen saves-screen"><div className="screen-heading"><div><p className="eyebrow">PROGRESS</p><h2>Saves</h2></div><Save /></div><p className="lead">Progress stays local. Every import creates a reversible backup and shows conflicts before applying.</p><div className="save-status"><ShieldCheck /><div><strong>{connected ? `${state.saveFileCount || 0} save files connected` : "Save folder not connected"}</strong><small>{connected ? "Source ready for a bounded preview or ZIP export." : "Choose the folder that contains your local Balatro saves."}</small></div><button className="icon-button bordered" type="button" aria-label="Choose save folder" onClick={() => invoke("chooseSaveFolder")}><FolderOpen /></button></div><div className="save-status"><FolderOpen /><div><strong>{targetConnected ? "Import target connected" : "Import target not connected"}</strong><small>{targetConnected ? "Conflicts will be shown before files are replaced." : "Choose the destination profile folder used by the installed game."}</small></div><button className="icon-button bordered" type="button" aria-label="Choose save target" onClick={() => invoke("chooseSaveTarget")}><FolderOpen /></button></div>{connected && <label className="field-label save-profile-field">Source profile<select aria-label="Save profile" value={saveProfile} onChange={(event) => setSaveProfile(event.target.value)}>{localProfiles.map((profile) => <option key={profile}>{profile}</option>)}</select></label>}{desktopReady && <div className="save-status desktop-save-status"><HardDrive /><div><strong>{desktop.files || 0} desktop save files found</strong><small>{desktopProfiles.length ? `Profiles available: ${desktopProfiles.join(", ")}.` : "Steam progress is ready for a bounded local transfer."} The helper sends only the selected archive over your LAN.</small></div>{desktopProfiles.length > 0 && <select aria-label="Desktop profile" value={desktopProfile} onChange={(event) => setDesktopProfile(event.target.value)}><option value="">All profiles</option>{desktopProfiles.map((profile) => <option key={profile} value={profile}>Profile {profile}</option>)}</select>}</div>}<h3 className="section-label">When creating an APK…</h3><div className="option-list save-options">{options.map((item) => <label key={item.id}><input type="radio" name="save-option" checked={option === item.id} onChange={() => setOption(item.id)} /><span><strong>{item.label}</strong><small>{item.detail}</small></span></label>)}</div><div className="save-actions"><button className="primary-button" type="button" onClick={() => invoke("previewSave", { option, profile: saveProfile })}>Preview saves</button>{importing && <button className="primary-button" type="button" onClick={() => invoke("importSave", { option, profile: saveProfile })}>Import saves</button>}{desktopReady && <button className="primary-button" type="button" disabled={!targetConnected} onClick={() => invoke("importDesktopSave", { profile: desktopProfile })}>Import desktop saves</button>}<button className="secondary-button" type="button" onClick={() => invoke("exportSave")}>Export local backup</button></div></section>;
}

function HistoryScreen({ state, tab, setTab }) {
  const entries = tab === "backups" ? (state.backupHistory || state.history || []) : (state.installHistory || []);
  return <section className="screen history-screen">
    <div className="screen-heading"><div><p className="eyebrow">AUDIT TRAIL</p><h2>History</h2></div><button className="icon-button bordered" type="button" aria-label="Export history" onClick={() => invoke("exportHistory")}><Download /></button></div>
    <div className="history-tabs" role="tablist"><button type="button" className={tab === "backups" ? "selected" : ""} onClick={() => setTab("backups")}>Backup History</button><button type="button" className={tab === "installs" ? "selected" : ""} onClick={() => setTab("installs")}>Installation History</button></div>
    <p className="lead">Restore backups, reinstall catalog releases, export records, or remove history without touching the other tab.</p>
    <div className="history-list">{entries.map((item) => {
      const installEntry = tab === "installs";
      const canReinstall = installEntry && ["install", "update"].includes(item.kind);
      const title = item.label || item.name || "Untitled change";
      const details = tab === "backups" ? `${item.createdAt || item.date || "Unknown date"} · ${item.entries || 0} mods/files · ${item.profile || "local profile"} · ${item.size ? `${item.size} bytes` : item.source || "local"}` : `${item.createdAt || item.date || "Unknown date"} · ${item.version || "version unknown"} · ${item.source || item.kind || "local"} · ${item.result || "recorded"}${item.checksum ? ` · ${item.checksum}` : ""}${item.error ? ` · ${item.error}` : ""}`;
      return <article key={item.id}><div className="history-icon">{tab === "backups" ? <ArchiveRestore /> : <Download />}</div><div><h3>{title}</h3><p>{details}</p></div><div className="history-actions">
        {canReinstall && <button type="button" onClick={() => invoke("reinstallInstall", { id: item.id })}>Reinstall</button>}
        {tab === "backups" && <button type="button" onClick={() => invoke("restoreSnapshot", { id: item.id })}>Restore</button>}
        <button type="button" onClick={() => invoke("exportHistory", { id: item.id, kind: tab })}>Export</button><button type="button" onClick={() => invoke("deleteHistoryEntry", { id: item.id, kind: tab })}>Remove</button>
      </div></article>;
    })}{entries.length === 0 && <div className="empty-state"><History /><h2>No history yet</h2><p>Backups and installations will appear here.</p></div>}</div>
  </section>;
}

function SettingsScreen({ wallpaper, setWallpaper, advancedMode, setAdvancedMode, crashReports, setCrashReports, historyRetention, onRetentionChange }) {
  return <section className="screen settings-screen">
    <div className="screen-heading"><div><p className="eyebrow">PREFERENCES</p><h2>Settings</h2></div><Settings2 /></div>
    <h3 className="section-label">Background</h3>
    <p className="lead">Choose a clean wallpaper. The center is intentionally quiet so buttons stay readable.</p>
    <div className="wallpaper-grid">{wallpaperOptions.map((item) => <button key={item.id} type="button" className={`wallpaper-option ${item.tone} ${wallpaper === item.id ? "selected" : ""}`} onClick={() => setWallpaper(item.id)} style={item.image ? { backgroundImage: `url(${item.image})` } : undefined}><span>{item.label}</span>{wallpaper === item.id && <Check />}</button>)}</div>
    <div className="settings-card"><ShieldCheck /><div><strong>Connected catalog</strong><small>Browse trusted indexes and refresh release metadata whenever you want the latest versions.</small></div><Check /></div>
    <label className="settings-card settings-toggle"><input type="checkbox" checked={advancedMode} onChange={(event) => setAdvancedMode(event.target.checked)} /><Settings2 /><div><strong>Advanced mode</strong><small>Show technical compatibility details and recovery context when available.</small></div></label>
    <label className="settings-card settings-toggle"><input type="checkbox" checked={crashReports} onChange={(event) => setCrashReports(event.target.checked)} /><CircleAlert /><div><strong>Local crash reports (opt-in)</strong><small>Off by default. Reports stay on this device and are exported only when you press an export button.</small></div></label>
    <label className="field-label settings-retention">History retention<select aria-label="History retention" value={historyRetention} onChange={(event) => onRetentionChange(event.target.value)}><option value="10">Keep 10 records</option><option value="20">Keep 20 records</option><option value="50">Keep 50 records</option><option value="100">Keep 100 records</option><option value="0">Keep all records</option></select></label>
    {advancedMode && <InfoCallout>Advanced mode is enabled. Technical package, ABI, checksum, and helper details appear only where they are relevant.</InfoCallout>}
    <button className="secondary-button" type="button" onClick={() => invoke("resetSettings")}>Reset app preferences</button>
  </section>;
}
function HelpScreen({ onNavigate }) { return <section className="screen help-screen"><div className="screen-heading"><div><p className="eyebrow">SUPPORT</p><h2>Help</h2></div><LifeBuoy /></div><div className="help-card"><h3>Something broke?</h3><p>Create one privacy-filtered ZIP with your mod inventory, versions, dependencies, catalog matches, install receipts, scan errors and useful text files. It never includes the game APK or save data.</p><div className="diagnostic-actions"><button className="secondary-button" type="button" onClick={() => invoke("saveDiagnosticZip")}><FileArchive /> Save diagnostic ZIP</button><button className="secondary-button" type="button" onClick={() => invoke("shareDiagnosticZip")}><Share2 /> Share via Telegram</button></div><button className="text-button" type="button" onClick={() => invoke("exportReport")}>Export small JSON report</button></div><div className="help-card"><h3>Native copy not supported?</h3><p>This copy cannot be patched safely. Use the Steam/local route instead. MBM will never bypass DRM or signatures.</p></div><div className="help-card"><h3>About MBM</h3><p>Mobile Balatro Manager is an independent companion for user-owned Balatro mod folders. Mods are never executed by the manager during inspection.</p><button className="secondary-button" type="button" onClick={() => onNavigate("about")}>About & licenses</button></div></section>; }

function AboutScreen({ state }) { const channel = state.channel === "production" ? "production" : "beta"; return <section className="screen help-screen"><div className="screen-heading"><div><p className="eyebrow">TRANSPARENCY</p><h2>About & licenses</h2></div><Info /></div><div className="help-card"><h3>MBM - Mobile Balatro Manager</h3><p>Version {state.version || "2.0.1"} · {channel} channel</p><p>Independent companion for user-owned Balatro mod folders. It does not include Balatro, commercial game files, saves, credentials, or signing keys.</p></div><div className="help-card"><h3>Open-source components</h3><p>Android WebView, React, Lucide icons, AndroidX DocumentFile, and the optional Balatro Mobile Maker are attributed in the public release folder. Third-party tools keep their original licenses.</p></div><div className="help-card"><h3>Privacy promise</h3><p>Pairing is LAN-only and consent-based. Catalog requests are initiated by you. No game files, saves, Steam credentials, or Google credentials are uploaded to a cloud service.</p></div></section>; }

function BackBar({ onBack }) { return <div className="back-bar"><button type="button" onClick={onBack}><ArrowLeft /> Home</button></div>; }
function BottomNav({ screen, onNavigate }) { const items = [["home", "Home", House], ["mods", "Mods", LayoutGrid], ["discover", "Discover", PackageSearch], ["history", "History", History], ["help", "Help", LifeBuoy]]; return <nav className="bottom-nav" aria-label="Primary navigation">{items.map(([value, label, Icon]) => <button key={value} className={screen === value ? "active" : ""} type="button" onClick={() => onNavigate(value)}><Icon /><span>{label}</span></button>)}</nav>; }
function LoadingOverlay() { return <div className="loading-overlay" role="status"><LoaderCircle /><span>Checking local files…</span></div>; }
function Toast({ message }) {
  const [visible, setVisible] = useState(true);
  useEffect(() => {
    const timeout = window.setTimeout(() => setVisible(false), 4000);
    return () => window.clearTimeout(timeout);
  }, []);
  return visible ? <div className="toast" role="status">{message}</div> : null;
}

export default App;
