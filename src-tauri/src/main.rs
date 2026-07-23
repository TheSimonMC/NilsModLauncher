#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod installer;
mod launch;
mod manifest;
mod minecraft;

use installer::{InstallOptions, InstallResult};
use launch::{LaunchOptions, LaunchResult};
use manifest::ManifestModel;
use serde::Serialize;

const LAUNCHER_VERSION: &str = "1.0.4";
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
fn install_version(
    window: tauri::Window,
    options: InstallOptions,
) -> Result<InstallResult, String> {
    let selected_version = options.version.clone();
    let manifest_url = options.manifest_url.clone();
    let result = installer::install(window.clone(), options, DEFAULT_MANIFEST_URL)?;
    let manifest =
        manifest::load_manifest(manifest_url.as_deref().unwrap_or(DEFAULT_MANIFEST_URL))?;
    if let Some(entry) = manifest.latest.get(&selected_version) {
        if entry.kind == "fabric" {
            launch::ensure_java_for_minecraft_version(&window, &entry.minecraft_version)?;
        }
    }
    Ok(result)
}

#[tauri::command]
fn launch_version(window: tauri::Window, options: LaunchOptions) -> Result<LaunchResult, String> {
    launch::launch(window, options, DEFAULT_MANIFEST_URL)
}

#[tauri::command]
fn open_instance_folder(version: String) -> Result<(), String> {
    let path = minecraft::nilsmod_game_dir(&version)?;
    std::fs::create_dir_all(&path).map_err(|err| err.to_string())?;
    open::that(path).map_err(|err| err.to_string())
}

fn main() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![
            app_info,
            load_manifest,
            install_version,
            launch_version,
            open_instance_folder,
        ])
        .run(tauri::generate_context!())
        .expect("error while running NilsMod Launcher");
}
