const normalize = (value) =>
  String(value || "")
    .toLowerCase()
    .replace(/[^a-z0-9]/g, "");
export function catalogForMod(mod, catalog) {
  const ids = [mod.id, mod.folder].filter(Boolean).map(normalize);
  const exact = catalog.filter((item) =>
    [item.id, item.folderName, item.folder]
      .filter(Boolean)
      .some((value) => ids.includes(normalize(value))),
  );
  if (exact.length === 1) return exact[0];
  if (exact.length > 1) return null;
  const byName = catalog.filter(
    (item) => normalize(item.name) === normalize(mod.name),
  );
  return byName.length === 1 ? byName[0] : null;
}
export function hasUpdate(mod, item) {
  if (typeof item?.updateAvailable === "boolean") return item.updateAvailable;
  if (!item?.version || !mod.version) return false;
  const parse = (value) => /^v?(\d+)\.(\d+)\.(\d+)$/.exec(String(value).trim());
  const old = parse(mod.version),
    next = parse(item.version);
  if (!old || !next) return false;
  for (let i = 1; i <= 3; i++) {
    if (+next[i] !== +old[i]) return +next[i] > +old[i];
  }
  return false;
}
export function readAppearance() {
  const defaults = {
    background: "classic",
    image: "",
    crt: true,
    intensity: 25,
  };
  try {
    const stored = JSON.parse(
      localStorage.getItem("mbm-appearance-v1") || "null",
    );
    if (!stored) return defaults;
    const image =
      typeof stored.image === "string" &&
      /^data:image\/(png|jpeg|webp);base64,/.test(stored.image)
        ? stored.image
        : "";
    const background = ["classic", "dark", "custom"].includes(stored.background)
      ? stored.background
      : "classic";
    return {
      background: background === "custom" && !image ? "classic" : background,
      image,
      crt: typeof stored.crt === "boolean" ? stored.crt : true,
      intensity: Number.isFinite(stored.intensity)
        ? Math.min(100, Math.max(0, stored.intensity))
        : 25,
    };
  } catch {
    return defaults;
  }
}
export function saveAppearance(value) {
  try {
    localStorage.setItem("mbm-appearance-v1", JSON.stringify(value));
    return true;
  } catch {
    return false;
  }
}

export function isBusy(state) {
  return Boolean(
    state.loading || state.operations?.length || state.operation?.active,
  );
}
export function canInstall(item) {
  return Boolean(
    item.source === "BMI" ||
    item.downloadUrl ||
    item.versions?.some((version) => version.downloadUrl),
  );
}

// A name is deliberately not an identity: several unrelated mods share short
// names.  We only collapse entries when the catalog gives us the same source
// id, or a canonical GitHub repository URL.
const repositoryKey = (value) => {
  try {
    const url = new URL(String(value || ""));
    if (url.hostname.toLowerCase() !== "github.com") return "";
    const [owner, repository] = url.pathname.split("/").filter(Boolean);
    return owner && repository
      ? `github:${owner.toLowerCase()}/${repository.toLowerCase().replace(/\.git$/, "")}`
      : "";
  } catch {
    return "";
  }
};
export function catalogIdentity(item) {
  const repository = repositoryKey(
    item.repository || item.homepage || item.downloadUrl,
  );
  const source = normalize(item.source), id = normalize(item.id);
  return repository || (source && id ? `source:${source}:${id}` : "");
}
export function uniqueCatalog(items) {
  const selected = new Map();
  for (const [index, item] of (items || []).entries()) {
    const key = catalogIdentity(item) || `unverified:${index}`;
    const previous = selected.get(key);
    if (
      !previous ||
      (!previous.installed && item.installed) ||
      (!previous.installed &&
        !item.installed &&
        (!previous.summary || previous.summary.length < 12) &&
        item.summary)
    ) {
      selected.set(key, item);
    }
  }
  return [...selected.values()];
}
export function catalogRequirements(item) {
  const requirements = [...(item?.dependencies || [])];
  if (item?.requiresSteamodded) requirements.unshift("Steamodded");
  if (item?.requiresTalisman) requirements.push("Talisman");
  return [...new Set(requirements)];
}
