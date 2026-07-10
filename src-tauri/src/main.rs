mod installer;
mod manifest;
mod minecraft;

use installer::{InstallOptions, InstallResult};
use manifest::ManifestModel;
use serde::Serialize;

const LAUNCHER_VERSION: &str = "1.0.1";
const DEFAULT_MANIFEST_URL: &str =
    "https://github.com/TheSimonMC/NilsModLauncher/releases/latest/download/nilsmod-manifest.json";

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct AppInfo {
    launcher_version: &'static str,
    default_manifest_url: &'static str,
    minecraft_dir: String,
    platform: &'static str,
}

#[tauri::command]
fn app_info() -> Result<AppInfo, String> {
    Ok(AppInfo {
        launcher_version: LAUNCHER_VERSION,
        default_manifest_url: DEFAULT_MANIFEST_URL,
        minecraft_dir: minecraft::minecraft_dir()?.display().to_string(),
        platform: std::env::consts::OS,
    })
}

#[tauri::command]
fn load_manifest(manifest_url: Option<String>) -> Result<ManifestModel, String> {
    manifest::load_manifest(manifest_url.as_deref().unwrap_or(DEFAULT_MANIFEST_URL))
}

#[tauri::command]
fn install_version(window: tauri::Window, options: InstallOptions) -> Result<InstallResult, String> {
    installer::install(window, options, DEFAULT_MANIFEST_URL)
}

#[tauri::command]
fn open_instance_folder(version: String) -> Result<(), String> {
    let path = minecraft::nilsmod_game_dir(&version)?;
    std::fs::create_dir_all(&path).map_err(|err| err.to_string())?;
    open::that(path).map_err(|err| err.to_string())
}

#[tauri::command]
fn open_minecraft_launcher() -> Result<(), String> {
    minecraft::open_minecraft_launcher()
}

fn main() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![
            app_info,
            load_manifest,
            install_version,
            open_instance_folder,
            open_minecraft_launcher
        ])
        .run(tauri::generate_context!())
        .expect("error while running NilsMod Launcher");
}
