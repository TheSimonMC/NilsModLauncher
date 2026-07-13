import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import "./styles.css";
import fancyLogo from "./assets/NilsMod_Fancy.png";
import compactLogo from "./assets/NilsMod_Logo_Vanilla.png";

type ModKind = "fabric" | "legacy";
type Page = "play" | "mods" | "settings";

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
  page: Page;
}

const state: LauncherState = {
  info: null,
  manifest: null,
  selectedVersion: "1.21.11",
  optional: {},
  busy: false,
  page: "play",
};

const app = document.querySelector<HTMLDivElement>("#app");
if (!app) {
  throw new Error("Missing app root");
}

app.innerHTML = `
  <main class="launcher-shell">
    <aside class="sidebar">
      <div class="sidebar-brand">
        <img src="${compactLogo}" alt="" />
        <span>NilsMod</span>
      </div>

      <nav class="navigation" aria-label="Launcher navigation">
        <button class="nav-button active" data-page="play"><span class="nav-mark play-mark"></span>Spielen</button>
        <button class="nav-button" data-page="mods"><span class="nav-mark mods-mark"></span>Mods</button>
        <button class="nav-button" data-page="settings"><span class="nav-mark settings-mark"></span>Einstellungen</button>
      </nav>

      <div class="sidebar-foot">
        <div class="runtime-status">
          <span>MANAGED RUNTIME</span>
          <strong>Zulu Java + Fabric</strong>
        </div>
        <div class="version-line">Launcher <strong id="launcherVersion">1.0.4</strong></div>
      </div>
    </aside>

    <section class="workspace">
      <header class="topbar">
        <div>
          <span class="eyebrow">NILSMOD LAUNCHER</span>
          <h1 id="pageTitle">Spielen</h1>
        </div>
        <div id="installState" class="state-pill"><span></span>Bereit</div>
      </header>

      <section class="page active" data-page-panel="play">
        <section class="launch-stage">
          <div class="launch-copy">
            <img src="${fancyLogo}" class="hero-wordmark" alt="NilsMod" />
            <p>Dein verwaltetes Fabric-Profil mit eigener Java-Runtime und automatischen Mod-Updates.</p>
            <div class="profile-row">
              <label>
                <span>Minecraft Version</span>
                <select id="versionSelect"></select>
              </label>
              <button id="launchButton" class="primary launch-button"><span class="button-play"></span>NilsMod starten</button>
            </div>
          </div>
          <img src="${compactLogo}" class="stage-emblem" alt="" />
        </section>

        <section class="dashboard-grid">
          <div class="dashboard-section">
            <div class="section-heading">
              <div><span class="eyebrow">PROFIL</span><h2>Installation</h2></div>
              <button id="refreshManifest" class="text-button">Updates pruefen</button>
            </div>
            <dl class="facts">
              <div><dt>Profil</dt><dd id="profileName">NilsMod</dd></div>
              <div><dt>NilsMod</dt><dd id="modVersion">...</dd></div>
              <div><dt>Plattform</dt><dd id="platformText">Windows / Linux / macOS</dd></div>
              <div><dt>GameDir</dt><dd id="minecraftDir">...</dd></div>
            </dl>
          </div>

          <div class="dashboard-section activity-section">
            <div class="section-heading">
              <div><span class="eyebrow">AKTIVITAET</span><h2>Launcher-Log</h2></div>
            </div>
            <div class="progress"><div id="progressBar"></div></div>
            <pre id="log"></pre>
          </div>
        </section>
      </section>

      <section class="page" data-page-panel="mods" hidden>
        <header class="page-intro">
          <div><span class="eyebrow">PROFIL-INHALT</span><h2>Mods verwalten</h2></div>
          <p>Pflichtmods werden automatisch gepflegt. Optionale Mods lassen sich pro Startprofil deaktivieren.</p>
        </header>
        <div id="modList" class="mod-list"></div>
      </section>

      <section class="page" data-page-panel="settings" hidden>
        <header class="page-intro">
          <div><span class="eyebrow">LAUNCHER</span><h2>Installation und Dateien</h2></div>
          <p>Profil, Fabric Loader, Mods und die passende Zulu-Java-Version werden vom Launcher verwaltet.</p>
        </header>
        <div class="settings-list">
          <div class="settings-row">
            <div><strong>Profil installieren oder aktualisieren</strong><span>Laedt fehlende Dateien und entfernt deaktivierte optionale Mods.</span></div>
            <button id="installButton" class="secondary">Installieren</button>
          </div>
          <div class="settings-row">
            <div><strong>Instanz-Ordner</strong><span>Oeffnet das getrennte GameDir des ausgewaehlten Profils.</span></div>
            <button id="openFolderButton" class="secondary">Ordner oeffnen</button>
          </div>
          <div class="settings-row">
            <div><strong>Manifest aktualisieren</strong><span>Prueft Versionen, Loader und Mod-Downloads erneut.</span></div>
            <button id="settingsRefreshButton" class="secondary">Jetzt pruefen</button>
          </div>
        </div>
      </section>
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
  installState.lastChild!.textContent = label;
  installState.classList.toggle("working", busy);
  document.body.classList.toggle("busy", busy);
  progressBar.style.width = busy ? "68%" : "100%";
}

function switchPage(page: Page) {
  state.page = page;
  const labels: Record<Page, string> = { play: "Spielen", mods: "Mods", settings: "Einstellungen" };
  $("#pageTitle").textContent = labels[page];
  document.querySelectorAll<HTMLElement>("[data-page-panel]").forEach((panel) => {
    const active = panel.dataset.pagePanel === page;
    panel.hidden = !active;
    panel.classList.toggle("active", active);
  });
  document.querySelectorAll<HTMLButtonElement>("[data-page]").forEach((button) => {
    button.classList.toggle("active", button.dataset.page === page);
  });
}

function selectedEntry(): VersionEntry | null {
  return state.manifest?.latest[state.selectedVersion] ?? null;
}

function renderVersions() {
  versionSelect.innerHTML = "";
  if (!state.manifest) return;
  const versions = Object.keys(state.manifest.latest).sort().reverse();
  for (const version of versions) {
    const option = document.createElement("option");
    option.value = version;
    option.textContent = version;
    versionSelect.append(option);
  }
  if (!versions.includes(state.selectedVersion)) state.selectedVersion = versions[0] ?? "";
  versionSelect.value = state.selectedVersion;
}

function renderMods() {
  const entry = selectedEntry();
  if (!entry) {
    modList.innerHTML = `<div class="empty">Kein Manifest geladen.</div>`;
    return;
  }
  $("#profileName").textContent = `NilsMod ${entry.minecraftVersion}`;
  $("#modVersion").textContent = `${entry.nilsmodVersion} / ${entry.type}`;
  const required = entry.requiredMods ?? [];
  const optional = entry.optionalMods ?? [];
  const requiredHtml = [
    `<div class="mod-card locked"><div><strong>NilsMod</strong><span>${entry.nilsmodVersion}</span></div><em>immer aktiv</em></div>`,
    ...required.map((mod) => `<div class="mod-card locked"><div><strong>${mod.name}</strong><span>${mod.version}</span></div><em>benoetigt</em></div>`),
  ].join("");
  const optionalHtml = optional.map((mod) => {
    if (state.optional[mod.id] === undefined) state.optional[mod.id] = mod.defaultEnabled ?? true;
    return `<label class="mod-card selectable"><div><strong>${mod.name}</strong><span>${mod.version}</span></div><input type="checkbox" data-mod="${mod.id}" ${state.optional[mod.id] ? "checked" : ""} /></label>`;
  }).join("");
  modList.innerHTML = `<h3>Pflichtmods</h3>${requiredHtml}<h3>Optionale Mods</h3>${optionalHtml || `<div class="empty">Keine optionalen Mods.</div>`}`;
}

async function loadManifest() {
  setBusy(true, "Manifest laedt...");
  try {
    state.manifest = await invoke<ManifestModel>("load_manifest", { manifestUrl: null });
    appendLog(`Manifest geladen: Launcher ${state.manifest.launcherVersion}`);
    renderVersions();
    renderMods();
  } catch (error) {
    appendLog(`Manifest-Fehler: ${String(error)}`);
  } finally {
    setBusy(false);
  }
}

function installOptions() {
  return {
    version: state.selectedVersion,
    manifestUrl: null,
    includeSodium: state.optional.sodium ?? true,
    includeVoiceChat: state.optional["simple-voice-chat"] ?? true,
  };
}

async function boot() {
  await listen<string>("install-log", (event) => appendLog(event.payload));
  state.info = await invoke<AppInfo>("app_info");
  $("#launcherVersion").textContent = state.info.launcherVersion;
  $("#platformText").textContent = state.info.platform;
  $("#minecraftDir").textContent = state.info.minecraftDir;
  appendLog("NilsMod Launcher bereit.");
  await loadManifest();
}

document.querySelector(".navigation")!.addEventListener("click", (event) => {
  const button = (event.target as HTMLElement).closest<HTMLButtonElement>("[data-page]");
  if (button?.dataset.page) switchPage(button.dataset.page as Page);
});

versionSelect.addEventListener("change", () => {
  state.selectedVersion = versionSelect.value;
  renderMods();
});

modList.addEventListener("change", (event) => {
  const target = event.target as HTMLInputElement;
  if (target?.dataset.mod) state.optional[target.dataset.mod] = target.checked;
});

$("#refreshManifest").addEventListener("click", () => void loadManifest());
$("#settingsRefreshButton").addEventListener("click", () => void loadManifest());

$("#launchButton").addEventListener("click", async () => {
  if (!state.manifest || state.busy) return;
  setBusy(true, "Startet...");
  logBox.textContent = "";
  try {
    const result = await invoke<LaunchResult>("launch_version", { options: installOptions() });
    appendLog(`Gestartet: ${result.profileName}`);
    appendLog(`Account: ${result.accountName}`);
    appendLog(`Version: ${result.versionId}`);
    appendLog(`Java: ${result.javaPath}`);
    appendLog(`GameDir: ${result.gameDir}`);
    if (result.pid) appendLog(`Prozess: ${result.pid}`);
  } catch (error) {
    appendLog(`Start fehlgeschlagen: ${String(error)}`);
  } finally {
    setBusy(false);
  }
});

$("#installButton").addEventListener("click", async () => {
  if (!state.manifest || state.busy) return;
  setBusy(true, "Installiert...");
  logBox.textContent = "";
  try {
    const result = await invoke<InstallResult>("install_version", { options: installOptions() });
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
