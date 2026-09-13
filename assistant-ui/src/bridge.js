export const initialState = {
  paired: false,
  address: "",
  assistantAvailable: true,
  model: "gpt-5.6-terra",
  reasoning: "high",
  crashAttachment: "",
  modsAttachment: "",
  operation: { active: false, label: "" },
  job: null,
  message: "Pair with BMM Helper on your desktop to begin.",
};

let state = { ...initialState };
let listener = null;

export function subscribe(next) {
  listener = next;
  window.__assistantReceive = (payload) => {
    const parsed = typeof payload === "string" ? JSON.parse(payload) : payload;
    listener?.(parsed);
  };
  if (!window.AssistantBridge?.invoke) state = { ...initialState };
  return () => { listener = null; delete window.__assistantReceive; };
}

function emit(patch) {
  state = { ...state, ...patch };
  queueMicrotask(() => listener?.(JSON.parse(JSON.stringify(state))));
}

export function invoke(method, payload = {}) {
  if (window.AssistantBridge?.invoke) {
    window.AssistantBridge.invoke(method, JSON.stringify(payload));
    return;
  }
  if (method === "getState") emit({});
  if (method === "pair") emit({ paired: true, address: payload.address, message: "Desktop paired. Codex OAuth stays on the desktop." });
  if (method === "pickCrash") emit({ crashAttachment: "balatro-crash.log", message: "Crash log attached." });
  if (method === "pickMods") emit({ modsAttachment: "Mods.zip", message: "Mods ZIP attached and inspected." });
  if (method === "runAssistant") {
    emit({ operation: { active: true, label: "Terra is inspecting the staging copy…" }, message: "Terra is inspecting the staging copy…" });
    setTimeout(() => emit({
      operation: { active: false, label: "" },
      job: {
        id: "demo-job", status: "completed", artifactReady: payload.task !== "analyze-crash",
        result: {
          status: payload.task === "create-mod" ? "created" : payload.task === "analyze-crash" ? "analyzed" : "repaired",
          title: payload.task === "create-mod" ? "New mod scaffold ready" : "Patch ready for review",
          summary: "The assistant found a bounded compatibility issue and prepared a reversible proposal.",
          diagnosis: ["The crash occurs while a mod loader reads incompatible metadata."],
          changes: ["Prepared a staging-only text patch; original files remain unchanged."],
          warnings: ["Test the exported ZIP in a backup profile first."],
          nextSteps: ["Review the proposal, export it, then import with Mobile Balatro Manager."],
        },
      },
      message: "Patch ready for review.",
    }), 650);
  }
  if (method === "exportArtifact") emit({ message: "Repaired ZIP saved from the review screen." });
}
