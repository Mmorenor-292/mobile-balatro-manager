import { useEffect, useState } from "react";
import {
  Bot, Bug, CheckCircle2, ChevronRight, Clock3, Code2, FileArchive, FileText,
  FolderOpen, History, LoaderCircle, LockKeyhole, Monitor, Paperclip,
  Send, Settings, ShieldCheck, Sparkles, Wrench,
} from "lucide-react";
import { initialState, invoke, subscribe } from "./bridge";

const tasks = [
  { id: "analyze-crash", label: "Analyze crash", icon: Bug, tone: "red" },
  { id: "repair-incompatibility", label: "Repair incompatibility", icon: Wrench, tone: "gold" },
  { id: "create-mod", label: "Create mod", icon: Code2, tone: "cyan" },
  { id: "review-mods", label: "Review Mods folder", icon: FolderOpen, tone: "mint" },
];

export default function App() {
  const [state, setState] = useState(initialState);
  const [address, setAddress] = useState("10.0.2.2:17171");
  const [code, setCode] = useState("");
  const [prompt, setPrompt] = useState("");
  const [task, setTask] = useState("analyze-crash");
  const [screen, setScreen] = useState("assistant");
  const [reviewOpen, setReviewOpen] = useState(false);

  useEffect(() => {
    const stop = subscribe((next) => {
      setState((old) => ({ ...old, ...next }));
      if (next.job?.status === "completed") setReviewOpen(true);
    });
    invoke("getState");
    return stop;
  }, []);

  const run = () => invoke("runAssistant", { task, prompt: prompt.trim() });
  const paired = Boolean(state.paired);
  const busy = Boolean(state.operation?.active);

  return <main className="assistant-shell">
    <header className="topbar"><div><Bot /><h1>Balatro AI Assistant</h1></div><span className={paired ? "status paired" : "status"}>{paired ? "Paired" : "Offline"}</span></header>

    {screen === "assistant" && <section className="screen assistant-screen">
      <div className="model-row"><Bot /><strong>GPT-5.6 Terra · High</strong><span>Default</span></div>
      <section className="pair-panel" aria-label="Desktop pairing">
        <Monitor />
        <label><span>Helper address</span><input aria-label="Helper address" value={address} onChange={(event) => setAddress(event.target.value)} placeholder="192.168.1.20:17171" /></label>
        <label className="code-field"><span>Pairing code</span><input aria-label="Pairing code" inputMode="numeric" maxLength={6} value={code} onChange={(event) => setCode(event.target.value.replace(/\D/g, "").slice(0, 6))} placeholder="000000" /></label>
        <button type="button" disabled={busy || code.length !== 6} onClick={() => invoke("pair", { address, code })}>{paired ? "Re-pair" : "Pair"}</button>
        <p><ShieldCheck /> Local pairing only. Your OAuth session never leaves the desktop.</p>
      </section>

      <section className="composer">
        <label htmlFor="assistant-prompt">What should I repair or build?</label>
        <textarea id="assistant-prompt" value={prompt} maxLength={8000} onChange={(event) => setPrompt(event.target.value)} placeholder="Describe the crash, incompatibility, or mod you want…" />
        <div className="attachments">
          <button type="button" disabled={busy} onClick={() => invoke("pickCrash")}><FileText />{state.crashAttachment || "Attach crash log"}</button>
          <button type="button" disabled={busy} onClick={() => invoke("pickMods")}><FileArchive />{state.modsAttachment || "Attach Mods ZIP"}</button>
        </div>
      </section>

      <div className="task-grid" aria-label="Assistant task">
        {tasks.map(({ id, label, icon: Icon, tone }) => <button key={id} type="button" className={`${tone} ${task === id ? "selected" : ""}`} aria-pressed={task === id} onClick={() => setTask(id)}><Icon /><span>{label}</span></button>)}
      </div>

      <button className="run-button" type="button" disabled={!paired || busy} onClick={run}>{busy ? <><LoaderCircle className="spin" />{state.operation.label}</> : <><Send />Run assistant</>}</button>

      {state.job && <button className="result-row" type="button" onClick={() => setReviewOpen(true)}><Code2 /><span><strong>{state.job.result?.title || "Assistant result"}</strong><small>{state.job.result?.summary || state.job.status}</small></span><ChevronRight /></button>}
      <div className="approval-note"><LockKeyhole /><div><strong>Nothing changes without your approval</strong><span>Codex works on a staging copy. Review and export before importing.</span></div></div>
    </section>}

    {screen === "workspace" && <section className="screen simple-screen"><FolderOpen /><h2>Workspace</h2><p>Attachments are copied to private staging on the paired desktop.</p><Attachment label="Crash log" value={state.crashAttachment} icon={FileText} /><Attachment label="Mods archive" value={state.modsAttachment} icon={FileArchive} /></section>}
    {screen === "history" && <section className="screen simple-screen"><History /><h2>History</h2>{state.job ? <button className="result-row" type="button" onClick={() => setReviewOpen(true)}><Clock3 /><span><strong>{state.job.result?.title}</strong><small>Latest local session</small></span><ChevronRight /></button> : <p>No assistant sessions yet.</p>}</section>}
    {screen === "settings" && <section className="screen simple-screen"><Settings /><h2>Settings</h2><div className="setting-row"><span>Model</span><strong>GPT-5.6 Terra</strong></div><div className="setting-row"><span>Reasoning</span><strong>High</strong></div><div className="setting-row"><span>Execution</span><strong>Desktop OAuth</strong></div><p className="settings-note">The APK stores only the paired helper address and short-lived token. It never stores ChatGPT credentials.</p></section>}

    {reviewOpen && state.job && <ReviewSheet job={state.job} onClose={() => setReviewOpen(false)} onExport={() => invoke("exportArtifact", { id: state.job.id })} />}
    {state.message && <div className="toast" role="status" key={state.message}>{state.message}</div>}
    <nav className="bottom-nav" aria-label="Primary navigation">{[["assistant","Assistant",Bot],["workspace","Workspace",FolderOpen],["history","History",History],["settings","Settings",Settings]].map(([id,label,Icon]) => <button key={id} type="button" className={screen === id ? "active" : ""} onClick={() => setScreen(id)}><Icon/><span>{label}</span></button>)}</nav>
  </main>;
}

function Attachment({ label, value, icon: Icon }) { return <div className="attachment-row"><Icon /><span><strong>{label}</strong><small>{value || "Not attached"}</small></span>{value && <CheckCircle2 />}</div>; }

function ReviewSheet({ job, onClose, onExport }) {
  const result = job.result || {};
  return <div className="sheet-backdrop" role="presentation"><section className="review-sheet" role="dialog" aria-modal="true" aria-label="Review assistant result"><div className="sheet-handle"/><header><div><Sparkles/><h2>{result.title || "Review changes"}</h2></div><button type="button" onClick={onClose}>Close</button></header><p>{result.summary}</p><ResultList title="Diagnosis" items={result.diagnosis}/><ResultList title="Proposed changes" items={result.changes}/><ResultList title="Warnings" items={result.warnings}/><ResultList title="Next steps" items={result.nextSteps}/>{job.artifactReady && <button className="export-button" type="button" onClick={onExport}><Paperclip/>Export proposed ZIP</button>}<div className="approval-note"><LockKeyhole/><div><strong>Still unchanged</strong><span>Exporting creates a ZIP. Import it separately only after you approve it.</span></div></div></section></div>;
}

function ResultList({ title, items }) { if (!items?.length) return null; return <section className="result-list"><h3>{title}</h3><ul>{items.map((item,index) => <li key={`${title}-${index}`}>{item}</li>)}</ul></section>; }
