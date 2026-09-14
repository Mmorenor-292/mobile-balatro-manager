import { useEffect, useRef, useState } from "react";
import {
  ArrowLeft,
  ArrowRight,
  ArrowUpFromLine,
  BookOpen,
  Check,
  ChevronDown,
  ChevronRight,
  Ellipsis,
  FolderOpen,
  House,
  Image as ImageIcon,
  List,
  LoaderCircle,
  Palette,
  Play,
  RefreshCw,
  Search,
  Settings,
  SlidersHorizontal,
  Trash2,
  X,
} from "lucide-react";
import { invoke, mockState, subscribe } from "./bridge";
import {
  catalogForMod,
  isBusy,
  canInstall,
  hasUpdate,
  readAppearance,
  saveAppearance,
  catalogRequirements,
  uniqueCatalog,
} from "./presentation";
const tabs = [
  ["home", "Home", House],
  ["mods", "Mods", List],
  ["discover", "Descubrir", Search],
];
const filters = [
  ["all", "Todos"],
  ["active", "Activos"],
  ["inactive", "Inactivos"],
  ["updates", "Actualización disponible"],
];
const classicBackground = "./wallpapers/classic-teal.png";

export default function App() {
  const [state, setState] = useState(() =>
    window.AndroidBridge?.invoke
      ? { connected: false, mods: [], catalog: [], loading: true }
      : mockState,
  );
  const [screen, setScreen] = useState("home"),
    [query, setQuery] = useState(""),
    [filter, setFilter] = useState("all");
  const [dialog, setDialog] = useState(null),
    [appearance, setAppearance] = useState(readAppearance),
    [notice, setNotice] = useState("");
  const [catalogCategory, setCatalogCategory] = useState("all"),
    [catalogSource, setCatalogSource] = useState("all"),
    [hideInstalled, setHideInstalled] = useState(false),
    [catalogLimit, setCatalogLimit] = useState(12);
  const catalogChecked = useRef(0),
    touchStart = useRef(null);
  useEffect(() => {
    const unsubscribe = subscribe((next) => {
      setState((previous) => ({ ...previous, ...next }));
      if (next.message) setNotice(next.message);
    });
    invoke("getState");
    return unsubscribe;
  }, []);
  useEffect(() => {
    if (!notice) return;
    const timer = setTimeout(() => setNotice(""), 5500);
    return () => clearTimeout(timer);
  }, [notice]);
  useEffect(() => {
    const back = (event) => {
      if (dialog || screen !== "home") {
        event.preventDefault();
        if (dialog) setDialog(null);
        else setScreen("home");
      }
    };
    window.addEventListener("androidback", back);
    return () => window.removeEventListener("androidback", back);
  }, [dialog, screen]);
  function checkCatalog(force = false) {
    if (force || Date.now() - catalogChecked.current > 300000) {
      catalogChecked.current = Date.now();
      invoke("loadCatalog");
    }
  }
  function navigate(next, nextFilter = "all") {
    setScreen(next);
    setFilter(nextFilter);
    setQuery("");
    if (next !== "home") checkCatalog();
    if (next === "discover") setCatalogLimit(12);
  }
  function updateAppearance(next) {
    const merged = { ...appearance, ...next };
    if (saveAppearance(merged)) setAppearance(merged);
    else
      setNotice("No se pudo guardar el fondo. Prueba una imagen más pequeña.");
  }
  const mods = state.mods || [],
    catalog = state.catalog || [];
  const updates = mods.filter((mod) =>
    hasUpdate(mod, catalogForMod(mod, catalog)),
  );
  const active = mods.filter((mod) => !mod.hidden).length,
    normalized = query.trim().toLocaleLowerCase("es");
  const visibleMods = mods.filter(
    (mod) =>
      (!normalized ||
        (mod.name || mod.folder)
          .toLocaleLowerCase("es")
          .includes(normalized)) &&
      (filter === "all" ||
        (filter === "active" && !mod.hidden) ||
        (filter === "inactive" && mod.hidden) ||
        (filter === "updates" && hasUpdate(mod, catalogForMod(mod, catalog)))),
  );
  const wallpaper =
    appearance.background === "custom"
      ? appearance.image
      : appearance.background === "classic"
        ? classicBackground
        : "";
  const catalogEntries = uniqueCatalog(catalog);
  const catalogCategories = [
    ...new Set(catalogEntries.flatMap((item) => item.categories || [])),
  ].sort((a, b) => a.localeCompare(b, "es"));
  const catalogSources = [
    ...new Set(catalogEntries.map((item) => item.source).filter(Boolean)),
  ].sort((a, b) => a.localeCompare(b, "es"));
  const visibleCatalog = catalogEntries
    .filter((item) => {
      const searchable = `${item.name || ""} ${item.author || ""}`
        .toLocaleLowerCase("es")
        .includes(normalized);
      return (
        searchable &&
        (catalogCategory === "all" ||
          item.categories?.includes(catalogCategory)) &&
        (catalogSource === "all" || item.source === catalogSource) &&
        (!hideInstalled || !item.installed)
      );
    })
    .sort((a, b) => (a.name || "").localeCompare(b.name || "", "es"));
  return (
    <main
      className={`app-shell ${appearance.crt ? "crt-enabled" : ""}`}
      style={{ "--crt-opacity": (appearance.intensity / 100) * 0.45 }}
    >
      <div
        className="wallpaper-layer"
        style={
          wallpaper ? { backgroundImage: `url("${wallpaper}")` } : undefined
        }
        aria-hidden="true"
      />
      <div className="crt-layer" aria-hidden="true" />
      <header className="topbar">
        <h1>
          {screen === "home"
            ? "Home"
            : screen === "mods"
              ? "Mods"
              : "Descubrir"}
        </h1>
        <button
          className={`pixel icon-button appearance-trigger ${screen === "home" ? "" : "push-right"}`}
          aria-label="Abrir apariencia"
          onClick={() => setDialog({ type: "appearance" })}
        >
          <Palette />
        </button>
        {screen === "home" && (
          <button
            className={`pixel folder-status ${state.connected ? "connected" : ""}`}
            onClick={() => invoke("chooseFolder", { automatic: true })}
            aria-label={
              state.connected
                ? "Cambiar carpeta de mods"
                : "Conectar carpeta de mods"
            }
          >
            {state.connected ? <Check /> : <FolderOpen />}
            <span>{state.connected ? "Mods Folder" : "Conectar carpeta"}</span>
          </button>
        )}
      </header>
      {screen !== "discover" && (
        <section className="stats" aria-label="Resumen de mods">
          <Stat
            tone="blue"
            value={mods.length}
            label="Instalados"
            onClick={() => navigate("mods")}
          />
          <Stat
            tone="green"
            value={active}
            label="Activos"
            onClick={() => navigate("mods", "active")}
          />
          <Stat
            tone="red"
            value={mods.length - active}
            label="Inactivos"
            onClick={() => navigate("mods", "inactive")}
          />
        </section>
      )}
      {screen === "home" && (
        <section className="home-screen">
          {updates.length > 0 && (
            <button
              className="pixel update-callout"
              onClick={() => navigate("mods", "updates")}
            >
              <ArrowUpFromLine className="gold-icon" />
              <span>
                <strong>
                  {updates.length}{" "}
                  {updates.length === 1
                    ? "mod tiene una actualización"
                    : "mods tienen una actualización"}
                </strong>
                <small>Revisar en Mods →</small>
              </span>
              <ChevronRight />
            </button>
          )}
          {!state.connected && !isBusy(state) && (
            <button
              className="pixel update-callout"
              onClick={() => invoke("chooseFolder", { automatic: true })}
            >
              <FolderOpen />
              <span>
                <strong>Conecta tu carpeta de mods</strong>
                <small>Recordaremos el acceso en este dispositivo</small>
              </span>
              <ChevronRight />
            </button>
          )}
          <div className="home-actions">
            <HomeAction
              icon={Settings}
              label="Gestionar mis mods"
              onClick={() => navigate("mods")}
            />
            <HomeAction
              icon={BookOpen}
              label="Explorar catálogo"
              onClick={() => navigate("discover")}
            />
            <HomeAction
              icon={Play}
              label="Abrir Balatro"
              onClick={() => invoke("launchBalatro")}
              external
            />
          </div>
        </section>
      )}
      {screen === "mods" && (
        <section
          className="mods-screen"
          onTouchStart={(event) => {
            touchStart.current =
              window.scrollY < 2 ? event.touches[0].clientY : null;
          }}
          onTouchEnd={(event) => {
            if (
              touchStart.current !== null &&
              event.changedTouches[0].clientY - touchStart.current > 95
            ) {
              invoke("refresh");
              checkCatalog(true);
            }
            touchStart.current = null;
          }}
        >
          <SearchField
            value={query}
            onChange={setQuery}
            placeholder="Buscar por nombre…"
          />
          <div className="filters" aria-label="Filtrar mods">
            {filters.map(([id, label]) => (
              <button
                key={id}
                className={`pixel ${filter === id ? "selected blue" : ""}`}
                aria-pressed={filter === id}
                onClick={() => setFilter(id)}
              >
                {label}
              </button>
            ))}
          </div>
          {filter === "updates" && (
            <div className="pixel updates-summary">
              <span>
                <strong>{updates.length}</strong> {updates.length === 1 ? "actualización pendiente" : "actualizaciones pendientes"}
              </span>
              <button
                className="pixel"
                disabled={isBusy(state)}
                onClick={() => {
                  invoke("refresh");
                  checkCatalog(true);
                }}
              >
                <RefreshCw /> Buscar de nuevo
              </button>
            </div>
          )}
          <div className="mod-list">
            {visibleMods.map((mod) => {
              const item = catalogForMod(mod, catalog);
              return (
                <article className="pixel mod-card" key={mod.folder}>
                  <div className="mod-heading">
                    <ModIcon name={mod.name || mod.folder} />
                    <div className="mod-name">
                      <h2>{mod.name || mod.folder}</h2>
                      <span>
                        {mod.author && mod.author !== "unknown author"
                          ? mod.author
                          : "Mod local"}
                      </span>
                    </div>
                    <label className="switch-label">
                      <input
                        type="checkbox"
                        role="switch"
                        aria-label={`Activar ${mod.name}`}
                        checked={!mod.hidden}
                        disabled={isBusy(state) || !state.connected}
                        onChange={(event) =>
                          invoke("toggleMod", {
                            folder: mod.folder,
                            hidden: !event.target.checked,
                          })
                        }
                      />
                      <span className="switch-track" aria-hidden="true" />
                      <span>{mod.hidden ? "Inactivo" : "Activo"}</span>
                    </label>
                  </div>
                  <div className="mod-controls">
                    <button
                      className="pixel version-button"
                      aria-label={`Elegir versión de ${mod.name}`}
                      onClick={() => setDialog({ type: "version", mod, item })}
                    >
                      {mod.version || "Sin versión"}
                      <ChevronDown />
                    </button>
                    <div className="mod-actions">
                      {hasUpdate(mod, item) && (
                        <button
                          className="pixel icon-button"
                          disabled={isBusy(state) || !state.connected}
                          aria-label={`Actualizar ${mod.name}`}
                          onClick={() =>
                            setDialog({ type: "update", mod, item })
                          }
                        >
                          <RefreshCw />
                        </button>
                      )}
                      <button
                        className="pixel icon-button"
                        aria-label={`Opciones de ${mod.name}`}
                        onClick={() =>
                          setDialog({ type: "details", mod, item })
                        }
                      >
                        <Ellipsis />
                      </button>
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
          {!visibleMods.length && !isBusy(state) && (
            <div className="pixel empty-state">
              <List />
              <h2>
                {mods.length
                  ? "Sin coincidencias"
                  : "Tu colección empieza aquí"}
              </h2>
              <p>
                {mods.length
                  ? "Prueba otro nombre o filtro."
                  : "Conecta tu carpeta o descubre tu primer mod."}
              </p>
              <button
                className="pixel blue"
                onClick={() =>
                  mods.length
                    ? (setQuery(""), setFilter("all"))
                    : navigate("discover")
                }
              >
                {mods.length ? "Limpiar filtros" : "Descubrir mods"}
              </button>
            </div>
          )}
          <button
            className="text-button refresh-foot"
            disabled={isBusy(state)}
            onClick={() => {
              invoke("refresh");
              checkCatalog(true);
            }}
          >
            <RefreshCw /> Actualizar lista
          </button>
        </section>
      )}
      {screen === "discover" && (
        <section className="discover-screen">
          <SearchField
            value={query}
            onChange={setQuery}
            placeholder="Buscar por nombre o autor…"
          />
          <div className="catalog-category-row" aria-label="Categorías">
            {["all", ...catalogCategories.slice(0, 3)].map((category) => (
              <button
                key={category}
                className={`pixel ${catalogCategory === category ? "selected blue" : ""}`}
                aria-pressed={catalogCategory === category}
                onClick={() => {
                  setCatalogCategory(category);
                  setCatalogLimit(12);
                }}
              >
                {category === "all" ? "Todos" : category}
              </button>
            ))}
          </div>
          <details className="pixel catalog-filters">
            <summary><SlidersHorizontal /> Filtrar catálogo</summary>
            <label>
              Fuente
              <select
                aria-label="Fuente del catálogo"
                value={catalogSource}
                onChange={(event) => {
                  setCatalogSource(event.target.value);
                  setCatalogLimit(12);
                }}
              >
                <option value="all">Todas las fuentes</option>
                {catalogSources.map((source) => <option key={source}>{source}</option>)}
              </select>
            </label>
            <label className="catalog-check">
              <input
                type="checkbox"
                checked={hideInstalled}
                onChange={(event) => {
                  setHideInstalled(event.target.checked);
                  setCatalogLimit(12);
                }}
              />
              Ocultar instalados
            </label>
            <p>Ordenado por nombre. Los repositorios repetidos se muestran una sola vez.</p>
          </details>
          <div className="catalog-toolbar">
            <span>{visibleCatalog.length} de {catalogEntries.length} mods</span>
            <button
              className="pixel icon-button"
              aria-label="Actualizar catálogo"
              disabled={isBusy(state)}
              onClick={() => checkCatalog(true)}
            >
              <RefreshCw />
            </button>
          </div>
          <div className="mod-list">
            {visibleCatalog
              .slice(0, catalogLimit)
              .map((item) => (
                <article
                  className="pixel catalog-card"
                  key={`${item.source}:${item.id}`}
                >
                  <div className="catalog-heading">
                    <ModIcon name={item.name} />
                    <div>
                      <h2>{item.name}</h2>
                      <small>
                        {item.author} · {item.source}
                      </small>
                    </div>
                  </div>
                  <p>{item.summary}</p>
                  <div className="catalog-actions">
                    <span>{item.version}</span>
                    <button
                      className="pixel blue"
                      onClick={() => setDialog({ type: "catalog-detail", item })}
                    >
                      {item.installed ? "Instalado · Ver" : "Ver mod →"}
                    </button>
                  </div>
                </article>
              ))}
          </div>
          {visibleCatalog.length > catalogLimit && (
            <button
              className="pixel load-more"
              onClick={() => setCatalogLimit((value) => value + 12)}
            >
              Cargar más resultados
            </button>
          )}
          {catalogEntries.length > 0 && !visibleCatalog.length && !isBusy(state) && (
            <div className="pixel empty-state">
              <Search />
              <h2>Sin resultados</h2>
              <p>Prueba otra búsqueda o limpia los filtros.</p>
              <button className="pixel blue" onClick={() => {
                setQuery(""); setCatalogCategory("all"); setCatalogSource("all"); setHideInstalled(false);
              }}>Limpiar filtros</button>
            </div>
          )}
          {!catalogEntries.length && !isBusy(state) && (
            <div className="pixel empty-state">
              <Search />
              <h2>El catálogo no está disponible</h2>
              <button className="pixel blue" onClick={() => checkCatalog(true)}>
                Reintentar
              </button>
            </div>
          )}
        </section>
      )}
      <nav className="bottom-nav" aria-label="Navegación principal">
        {tabs.map(([id, label, Icon]) => (
          <button
            key={id}
            className={`pixel nav-item ${screen === id ? "selected blue" : ""}`}
            aria-current={screen === id ? "page" : undefined}
            onClick={() => navigate(id)}
          >
            <Icon fill={id === "home" ? "currentColor" : "none"} />
            <span>{label}</span>
          </button>
        ))}
      </nav>
      {isBusy(state) && (
        <div className="activity-status" role="status">
          <LoaderCircle className="spin" /> Procesando…
        </div>
      )}
      {notice && (
        <div className="pixel toast" role="status">
          <span>{notice}</span>
          <button aria-label="Cerrar aviso" onClick={() => setNotice("")}>
            <X />
          </button>
        </div>
      )}
      {dialog && (
        <Sheet
          title={
            dialog.type === "appearance"
              ? "Apariencia"
              : dialog.type === "version"
                ? "Elegir versión"
              : dialog.type === "install"
                  ? "Instalar mod"
                  : dialog.type === "update"
                    ? "Actualizar mod"
                    : dialog.type === "catalog-detail"
                      ? "Ficha del mod"
                      : dialog.mod.name
          }
          onClose={() => setDialog(null)}
        >
          {dialog.type === "appearance" ? (
            <Appearance
              value={appearance}
              onChange={updateAppearance}
              onError={setNotice}
              onClose={() => setDialog(null)}
            />
          ) : (
            <ModSheet
              dialog={dialog}
              state={state}
              onClose={() => setDialog(null)}
            />
          )}
        </Sheet>
      )}
    </main>
  );
}
function Stat({ tone, value, label, onClick }) {
  return (
    <button
      className={`pixel stat ${tone}`}
      aria-label={`${value} ${label}`}
      onClick={onClick}
    >
      <strong>{value}</strong>
      <span>{label}</span>
    </button>
  );
}
function HomeAction({ icon: Icon, label, onClick, external }) {
  return (
    <button className="pixel home-action" onClick={onClick}>
      <Icon fill={Icon === Play ? "currentColor" : "none"} />
      <span>{label}</span>
      {external ? (
        <span className="external-arrow">↗</span>
      ) : (
        <ArrowRight className="action-arrow" />
      )}
    </button>
  );
}
function SearchField({ value, onChange, placeholder }) {
  return (
    <label className="pixel search-field">
      <Search />
      <input
        aria-label={placeholder.replace("…", "")}
        type="search"
        placeholder={placeholder}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}
function ModIcon({ name }) {
  const letters = name === "JokerDisplay" ? "Jd" : name?.slice(0, 1) || "?";
  const tone =
    name === "Handy" ? "red" : name === "Ortalab" ? "purple" : "blue";
  return (
    <div className={`pixel mod-icon ${tone}`} aria-hidden="true">
      {letters}
    </div>
  );
}
function Sheet({ title, children, onClose }) {
  const ref = useRef(null),
    closeRef = useRef(onClose);
  useEffect(() => {
    closeRef.current = onClose;
  }, [onClose]);
  useEffect(() => {
    const previous = document.activeElement,
      overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    ref.current?.focus();
    const keydown = (event) => {
      if (event.key === "Escape") {
        event.preventDefault();
        closeRef.current();
      }
      if (event.key === "Tab") {
        const nodes = [
          ...ref.current.querySelectorAll(
            'button:not(:disabled),input:not(:disabled):not([tabindex="-1"]),select,a[href]',
          ),
        ];
        if (!nodes.length) {
          event.preventDefault();
          return;
        }
        const first = nodes[0],
          last = nodes[nodes.length - 1];
        if (
          event.shiftKey &&
          (document.activeElement === first ||
            document.activeElement === ref.current)
        ) {
          event.preventDefault();
          last.focus();
        } else if (
          !event.shiftKey &&
          (document.activeElement === last ||
            document.activeElement === ref.current)
        ) {
          event.preventDefault();
          first.focus();
        }
      }
    };
    document.addEventListener("keydown", keydown);
    return () => {
      document.body.style.overflow = overflow;
      document.removeEventListener("keydown", keydown);
      previous?.focus();
    };
  }, []);
  return (
    <div
      className="sheet-backdrop"
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <section
        ref={ref}
        className="pixel sheet"
        role="dialog"
        aria-modal="true"
        aria-labelledby="sheet-title"
        tabIndex={-1}
      >
        <div className="sheet-handle" />
        <header className="sheet-header">
          <h2 id="sheet-title">{title}</h2>
          <button
            className="icon-button"
            aria-label="Cerrar panel"
            onClick={onClose}
          >
            <X />
          </button>
        </header>
        {children}
      </section>
    </div>
  );
}
function Appearance({ value, onChange, onError, onClose }) {
  const fileInput = useRef(null);
  const [reading, setReading] = useState(false);
  async function readFile(event) {
    const input = event.target,
      file = input.files?.[0];
    if (!file) return;
    if (
      !["image/png", "image/jpeg", "image/webp"].includes(file.type) ||
      file.size > 2500000
    ) {
      onError("Elige una imagen PNG, JPG o WebP de hasta 2,5 MB.");
      input.value = "";
      return;
    }
    setReading(true);
    try {
      const image = await new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result);
        reader.onerror = reject;
        reader.readAsDataURL(file);
      });
      onChange({ background: "custom", image });
    } catch {
      onError("No se pudo leer la imagen. El fondo anterior se conserva.");
    } finally {
      setReading(false);
      input.value = "";
    }
  }
  return (
    <div className="appearance-content">
      <fieldset>
        <legend>Fondo</legend>
        <div className="background-options">
          {[
            ["classic", "Clásico"],
            ["dark", "Oscuro"],
            ["custom", "Personalizado"],
          ].map(([id, label]) => (
            <button
              key={id}
              className="background-choice"
              aria-pressed={value.background === id}
              onClick={() =>
                id === "custom" && !value.image
                  ? fileInput.current.click()
                  : onChange({ background: id })
              }
            >
              <span
                className={`pixel background-thumb ${value.background === id ? "chosen" : ""}`}
                style={
                  id === "classic"
                    ? { backgroundImage: `url(${classicBackground})` }
                    : id === "custom" && value.image
                      ? { backgroundImage: `url(${value.image})` }
                      : undefined
                }
              >
                {id === "custom" && !value.image && <ImageIcon />}
              </span>
              <span>{label}</span>
            </button>
          ))}
        </div>
        <input
          ref={fileInput}
          className="sr-only"
          tabIndex={-1}
          type="file"
          accept="image/png,image/jpeg,image/webp"
          aria-label="Imagen de fondo"
          onChange={readFile}
        />
        <button
          className="pixel choose-image"
          disabled={reading}
          onClick={() => fileInput.current.click()}
        >
          {reading ? "Leyendo imagen…" : "Elegir imagen"}
        </button>
      </fieldset>
      <section className="crt-settings">
        <label className="crt-toggle">
          <strong>Filtro CRT</strong>
          <span className="switch-label">
            <input
              type="checkbox"
              role="switch"
              aria-label="Filtro CRT"
              checked={value.crt}
              onChange={(event) => onChange({ crt: event.target.checked })}
            />
            <span className="switch-track" aria-hidden="true" />
          </span>
        </label>
        <label className={`intensity ${value.crt ? "" : "disabled"}`}>
          <span>Intensidad</span>
          <div>
            <input
              type="range"
              aria-label="Intensidad CRT"
              min="0"
              max="100"
              value={value.intensity}
              disabled={!value.crt}
              onChange={(event) =>
                onChange({ intensity: Number(event.target.value) })
              }
              style={{ "--range": `${value.intensity}%` }}
            />
            <output>{value.intensity} %</output>
          </div>
        </label>
        <p>Desactívalo para una imagen limpia.</p>
      </section>
      <button className="pixel green done-button" onClick={onClose}>
        Listo
      </button>
    </div>
  );
}
function RequirementBlock({ requirements }) {
  if (!requirements.length) return <p className="requirement-note">Sin requisitos declarados por la fuente.</p>;
  return (
    <div className="pixel requirement-block">
      <strong>Requiere</strong>
      <span>{requirements.join(" · ")}</span>
    </div>
  );
}
function ModSheet({ dialog, state, onClose }) {
  const { mod, type } = dialog;
  const item =
    state.catalog?.find(
      (candidate) =>
        candidate.id === dialog.item?.id &&
        candidate.source === dialog.item?.source,
    ) || dialog.item;
  const [selected, setSelected] = useState("");
  const versions = item?.versions?.length
    ? item.versions
    : item
      ? [
          {
            version: item.latestVersion || item.version,
            downloadUrl: item.downloadUrl,
          },
        ]
      : [];
  const current = item?.installedVersion || mod?.version;
  const chosen =
    versions.find((version) => version.version === selected) || versions[0];
  const act = (method, release) => {
    invoke(method, {
      id: item.id,
      source: item.source,
      ...(release
        ? { version: release.version, downloadUrl: release.downloadUrl || "" }
        : {}),
    });
    onClose();
  };
  const blocked = !state.connected || isBusy(state);
  const requirements = catalogRequirements(item);
  const changes = item?.changes || item?.releaseNotes || item?.changelog;
  if (type === "catalog-detail")
    return (
      <div className="mod-sheet-content catalog-detail-content">
        <h3>{item.name}</h3>
        <p className="source-line">{item.author || "Autor no indicado"} · {item.source}</p>
        <p>{item.description || item.summary || "La fuente no incluyó una descripción."}</p>
        <div className="pixel version-row">
          <Check />
          <span>
            {chosen?.version || item.version || "Sin versión identificada"}
            <small>{chosen?.prerelease ? "Preliminar" : "Publicación disponible"}</small>
          </span>
        </div>
        {versions.length > 1 && (
          <label className="release-picker">
            Versión a instalar
            <select
              aria-label={`Versión de ${item.name}`}
              value={chosen?.version || ""}
              disabled={isBusy(state)}
              onChange={(event) => setSelected(event.target.value)}
            >
              {versions.map((release) => (
                <option key={release.version} value={release.version}>
                  {release.version}{release.prerelease ? " · preliminar" : ""}
                </option>
              ))}
            </select>
          </label>
        )}
        {item.source === "BMI" && versions.length <= 1 && (
          <button
            className="pixel"
            disabled={isBusy(state)}
            onClick={() => invoke("loadCatalogVersions", { id: item.id, source: item.source })}
          >
            Cargar versiones publicadas
          </button>
        )}
        <RequirementBlock requirements={requirements} />
        <p className="compatibility-note">
          Compatibilidad móvil: {item.compatibility === "supported" ? "indicada por la fuente" : "sin verificar"}.
        </p>
        {changes && <p><strong>Novedades:</strong> {changes}</p>}
        {item.homepage && (
          <button className="pixel" onClick={() => invoke("openCatalogSource", { id: item.id, source: item.source })}>
            Ver publicación original ↗
          </button>
        )}
        <button
          className="pixel green done-button"
          disabled={item.installed || blocked || !chosen || !canInstall({ ...item, downloadUrl: chosen?.downloadUrl, versions: [] })}
          onClick={() => act("installCatalogMod", chosen)}
        >
          {item.installed ? "Ya está instalado" : `Instalar ${chosen?.version || item.version || "mod"}`}
        </button>
        <p>Se añadirá a Mods. Revisa el resultado al terminar.</p>
      </div>
    );
  if (type === "install" || type === "update")
    return (
      <div className="mod-sheet-content">
        <h3>{item.name}</h3>
        <p>
          {type === "update"
            ? `${current} → ${item.latestVersion || item.version}`
            : `Versión ${item.version}`}
        </p>
        <p>
          La descarga se inspeccionará antes de instalarse. Revisa el estado del
          mod al terminar.
        </p>
        {type === "update" && <RequirementBlock requirements={requirements} />}
        {type === "update" && changes && <p><strong>Novedades:</strong> {changes}</p>}
        {type === "update" && !changes && <p>La fuente no publicó un resumen de cambios para esta versión.</p>}
        <button
          className="pixel green done-button"
          disabled={blocked}
          onClick={() =>
            act(type === "update" ? "updateCatalogMod" : "installCatalogMod")
          }
        >
          {type === "update" ? "Actualizar" : "Instalar"}
        </button>
      </div>
    );
  if (type === "version")
    return (
      <div className="mod-sheet-content">
        <h3>{mod.name}</h3>
        <div className="pixel version-row">
          <Check />
          <span>
            {current || "Sin versión"}
            <small>Instalada actualmente</small>
          </span>
        </div>
        {item && versions.length > 0 && (
          <>
            <label className="release-picker">
              Versión disponible
              <select
                aria-label={`Versión de ${mod.name}`}
                value={chosen?.version || ""}
                disabled={isBusy(state)}
                onChange={(event) => setSelected(event.target.value)}
              >
                {versions.map((release) => (
                  <option key={release.version} value={release.version}>
                    {release.version}
                    {release.version === current ? " · instalada" : ""}
                  </option>
                ))}
              </select>
            </label>
            <button
              className="pixel green"
              disabled={
                blocked ||
                !chosen ||
                chosen.version === current ||
                !canInstall({
                  ...item,
                  downloadUrl: chosen?.downloadUrl,
                  versions: [],
                })
              }
              onClick={() => act("updateCatalogMod", chosen)}
            >
              Instalar versión seleccionada
            </button>
          </>
        )}
        {item?.source === "BMI" && versions.length <= 1 && (
          <button
            className="pixel"
            disabled={isBusy(state)}
            onClick={() =>
              invoke("loadCatalogVersions", {
                id: item.id,
                source: item.source,
              })
            }
          >
            Cargar versiones publicadas
          </button>
        )}
        <p>
          {item
            ? "Puedes volver a una versión anterior o probar otra publicación cuando la fuente la incluya."
            : "No se encontró una fuente única para este mod."}
        </p>
        {item?.updateState === "unknown" && <p>{item.updateReason}</p>}
        {item?.homepage && (
          <button className="pixel" onClick={() => act("openCatalogSource")}>
            Ver fuente ↗
          </button>
        )}
        <button className="text-button" onClick={onClose}>
          <ArrowLeft /> Volver a Mods
        </button>
      </div>
    );
  return (
    <div className="mod-sheet-content">
      <p>
        {mod.description && mod.description !== "No description available."
          ? mod.description
          : `${mod.name} · ${mod.version}`}
      </p>
      <p>
        {mod.author && mod.author !== "unknown author"
          ? mod.author
          : "Autor no indicado"}
      </p>
      {mod.dependencies?.length > 0 && (
        <p>Dependencias: {mod.dependencies.join(", ")}</p>
      )}
      <button
        className="pixel danger-action"
        disabled={blocked}
        onClick={() => {
          if (window.confirm(`¿Desinstalar ${mod.name}?`)) {
            invoke("deleteMod", { folder: mod.folder });
            onClose();
          }
        }}
      >
        <Trash2 /> Desinstalar mod
      </button>
    </div>
  );
}
