import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import "./styles.css";
import fancyLogo from "./assets/NilsMod_Fancy.png";
import compactLogo from "./assets/NilsMod_Logo_Vanilla.png";

type ModKind = "fabric" | "legacy";

interface ExtraMod {
  id: string;
  name: string;
  version: string;
  fileName: string;
  url: string;
  sha256?: string;
  sha512?: string;
  defaultEnabled?: boolean;
}

interface VersionEntry {
  type: ModKind;
  minecraftVersion: string;
  fabricLoader?: string;
  nilsmodVersion: string;
  modUrl?: string;
  patcherUrl?: string;
  sha256?: string;
  sha512?: string;
  requiredMods?: ExtraMod[];
  optionalMods?: ExtraMod[];
}

interface ManifestModel {
  launcherVersion: string;
  latest: Record<string, VersionEntry>;
}

interface AppInfo {
  launcherVersion: string;
  defaultManifestUrl: string;
  minecraftDir: string;
  platform: string;
}

interface InstallResult {
  profileName: string;
  versionId: string;
  gameDir: string;
  installedFiles: string[];
}

interface LaunchResult {
  profileName: string;
  versionId: string;
  gameDir: string;
  javaPath: string;
  accountName: string;
  pid?: number;
}

interface LauncherState {
  info: AppInfo | null;
  manifest: ManifestModel | null;
  selectedVersion: string;
  optional: Record<string, boolean>;
  busy: boolean;
}

const state: LauncherState = {
  info: null,
  manifest: null,
  selectedVersion: "1.21.11",
  optional: {},
  busy: false,
};

const app = document.querySelector<HTMLDivElement>("#app");
if (!app) {
  throw new Error("Missing app root");
}

app.innerHTML = `
  <main class="shell">
    <section class="hero">
      <div class="brand">
        <img src="${compactLogo}" class="brand-icon" alt="NilsMod" />
        <div>
          <img src="${fancyLogo}" class="brand-wordmark" alt="NilsMod Launcher" />
          <p>Cross-platform NilsMod launcher</p>
        </div>
      </div>
      <div class="badge">
        <span>Launcher</span>
        <strong id="launcherVersion">1.0.3</strong>
      </div>
    </section>

    <section class="grid">
      <article class="panel install-panel">
        <div class="panel-head">
          <h1>Installation</h1>
          <span id="platformText">Windows / Linux / macOS</span>
        </div>

        <label class="field">
          <span>Manifest URL</span>
          <input id="manifestUrl" spellcheck="false" />
        </label>

        <div class="row">
          <label class="field compact">
            <span>Minecraft Version</span>
            <select id="versionSelect"></select>
          </label>
          <button id="refreshManifest" class="secondary">Manifest neu laden</button>
        </div>

        <div id="modList" class="mod-list"></div>

        <div class="actions">
          <button id="launchButton" class="primary">NilsMod starten</button>
          <button id="installButton" class="secondary">Nur installieren</button>
          <button id="openFolderButton" class="secondary">Instanz-Ordner oeffnen</button>
        </div>
      </article>

      <aside class="panel status-panel">
        <div class="panel-head">
          <h2>Status</h2>
          <span id="installState">Bereit</span>
        </div>
        <dl class="facts">
          <div>
            <dt>Minecraft</dt>
            <dd id="minecraftDir">...</dd>
          </div>
          <div>
            <dt>Profil</dt>
            <dd id="profileName">NilsMod</dd>
          </div>
          <div>
            <dt>NilsMod</dt>
            <dd id="modVersion">...</dd>
          </div>
        </dl>
        <div class="progress"><div id="progressBar"></div></div>
        <pre id="log"></pre>
      </aside>
    </section>
  </main>
`;

const $ = <T extends HTMLElement>(selector: string) => {
  const element = document.querySelector<T>(selector);
  if (!element) {
    throw new Error(`Missing element ${selector}`);
  }
  return element;
};

const manifestInput = $("#manifestUrl") as HTMLInputElement;
const versionSelect = $("#versionSelect") as HTMLSelectElement;
const modList = $("#modList");
const logBox = $("#log");
const installState = $("#installState");
const progressBar = $("#progressBar");

function appendLog(message: string) {
  const time = new Date().toLocaleTimeString();
  logBox.textContent += `[${time}] ${message}\n`;
  logBox.scrollTop = logBox.scrollHeight;
}

function setBusy(busy: boolean, label = busy ? "Arbeite..." : "Bereit") {
  state.busy = busy;
  installState.textContent = label;
  document.body.classList.toggle("busy", busy);
  progressBar.style.width = busy ? "68%" : "100%";
}

function selectedEntry(): VersionEntry | null {
  if (!state.manifest) {
    return null;
  }
  return state.manifest.latest[state.selectedVersion] ?? null;
}

function renderVersions() {
  versionSelect.innerHTML = "";
  if (!state.manifest) {
    return;
  }
  const versions = Object.keys(state.manifest.latest).sort().reverse();
  for (const version of versions) {
    const option = document.createElement("option");
    option.value = version;
    option.textContent = version;
    versionSelect.append(option);
  }
  if (!versions.includes(state.selectedVersion)) {
    state.selectedVersion = versions[0] ?? "";
  }
  versionSelect.value = state.selectedVersion;
}

function renderMods() {
  const entry = selectedEntry();
  if (!entry) {
    modList.innerHTML = `<div class="empty">Kein Manifest geladen.</div>`;
    return;
  }

  const required = entry.requiredMods ?? [];
  const optional = entry.optionalMods ?? [];
  $("#profileName").textContent = `NilsMod ${entry.minecraftVersion}`;
  $("#modVersion").textContent = `${entry.nilsmodVersion} / ${entry.type}`;

  const requiredHtml = [
    `<div class="mod-card locked">
      <div><strong>NilsMod</strong><span>${entry.nilsmodVersion}</span></div>
      <em>immer aktiv</em>
    </div>`,
    ...required.map(
      (mod) => `<div class="mod-card locked">
        <div><strong>${mod.name}</strong><span>${mod.version}</span></div>
        <em>benoetigt</em>
      </div>`,
    ),
  ].join("");

  const optionalHtml = optional
    .map((mod) => {
      if (state.optional[mod.id] === undefined) {
        state.optional[mod.id] = mod.defaultEnabled ?? true;
      }
      const checked = state.optional[mod.id] ? "checked" : "";
      return `<label class="mod-card selectable">
        <div><strong>${mod.name}</strong><span>${mod.version}</span></div>
        <input type="checkbox" data-mod="${mod.id}" ${checked} />
      </label>`;
    })
    .join("");

  modList.innerHTML = `<h3>Pflichtmods</h3>${requiredHtml}<h3>Optionale Mods</h3>${optionalHtml || `<div class="empty">Keine optionalen Mods.</div>`}`;
}

async function loadManifest() {
  setBusy(true, "Manifest laedt...");
  try {
    state.manifest = await invoke<ManifestModel>("load_manifest", {
      manifestUrl: manifestInput.value.trim() || null,
    });
    appendLog(`Manifest geladen: Launcher ${state.manifest.launcherVersion}`);
    renderVersions();
    renderMods();
  } catch (error) {
    appendLog(`Manifest-Fehler: ${String(error)}`);
  } finally {
    setBusy(false);
  }
}

async function boot() {
  await listen<string>("install-log", (event) => appendLog(event.payload));
  state.info = await invoke<AppInfo>("app_info");
  manifestInput.value = state.info.defaultManifestUrl;
  $("#launcherVersion").textContent = state.info.launcherVersion;
  $("#platformText").textContent = state.info.platform;
  $("#minecraftDir").textContent = state.info.minecraftDir;
  appendLog("NilsMod Launcher bereit.");
  await loadManifest();
}

versionSelect.addEventListener("change", () => {
  state.selectedVersion = versionSelect.value;
  renderMods();
});

modList.addEventListener("change", (event) => {
  const target = event.target as HTMLInputElement;
  if (target?.dataset.mod) {
    state.optional[target.dataset.mod] = target.checked;
  }
});

$("#refreshManifest").addEventListener("click", () => void loadManifest());

$("#launchButton").addEventListener("click", async () => {
  if (!state.manifest || state.busy) {
    return;
  }
  setBusy(true, "Startet...");
  logBox.textContent = "";
  try {
    const result = await invoke<LaunchResult>("launch_version", {
      options: {
        version: state.selectedVersion,
        manifestUrl: manifestInput.value.trim() || null,
        includeSodium: state.optional.sodium ?? true,
        includeVoiceChat: state.optional["simple-voice-chat"] ?? true,
      },
    });
    appendLog(`Gestartet: ${result.profileName}`);
    appendLog(`Account: ${result.accountName}`);
    appendLog(`Version: ${result.versionId}`);
    appendLog(`Java: ${result.javaPath}`);
    appendLog(`GameDir: ${result.gameDir}`);
    if (result.pid) {
      appendLog(`Prozess: ${result.pid}`);
    }
  } catch (error) {
    appendLog(`Start fehlgeschlagen: ${String(error)}`);
  } finally {
    setBusy(false);
  }
});

$("#installButton").addEventListener("click", async () => {
  if (!state.manifest || state.busy) {
    return;
  }
  setBusy(true, "Installiert...");
  logBox.textContent = "";
  try {
    const result = await invoke<InstallResult>("install_version", {
      options: {
        version: state.selectedVersion,
        manifestUrl: manifestInput.value.trim() || null,
        includeSodium: state.optional.sodium ?? true,
        includeVoiceChat: state.optional["simple-voice-chat"] ?? true,
      },
    });
    appendLog(`Fertig: ${result.profileName}`);
    appendLog(`Version: ${result.versionId}`);
    appendLog(`GameDir: ${result.gameDir}`);
    appendLog(`Dateien: ${result.installedFiles.length}`);
  } catch (error) {
    appendLog(`Installation fehlgeschlagen: ${String(error)}`);
  } finally {
    setBusy(false);
  }
});

$("#openFolderButton").addEventListener("click", async () => {
  try {
    await invoke("open_instance_folder", { version: state.selectedVersion });
  } catch (error) {
    appendLog(`Ordner konnte nicht geoeffnet werden: ${String(error)}`);
  }
});

void boot();
