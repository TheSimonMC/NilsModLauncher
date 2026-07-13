use crate::manifest::{self, ExtraMod, VersionEntry};
use crate::minecraft;
use serde::{Deserialize, Serialize};
use serde_json::json;
use sha2::{Digest, Sha256, Sha512};
use std::fs;
use std::io::{Read, Write};
use std::path::Path;
use tauri::{Emitter, Window};

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct InstallOptions {
    pub version: String,
    pub manifest_url: Option<String>,
    pub include_sodium: bool,
    pub include_voice_chat: bool,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct InstallResult {
    pub profile_name: String,
    pub version_id: String,
    pub game_dir: String,
    pub installed_files: Vec<String>,
}

pub fn install(
    window: Window,
    options: InstallOptions,
    default_manifest_url: &str,
) -> Result<InstallResult, String> {
    let manifest_url = options
        .manifest_url
        .as_deref()
        .unwrap_or(default_manifest_url);
    log(&window, "Manifest wird geladen...");
    let manifest = manifest::load_manifest(manifest_url)?;
    let entry = manifest
        .latest
        .get(&options.version)
        .ok_or_else(|| format!("Version {} was not found in manifest", options.version))?;

    match entry.kind.as_str() {
        "fabric" => install_fabric(&window, &options, entry),
        "legacy" => install_legacy(&window, &options, entry),
        other => Err(format!("Unsupported version type: {other}")),
    }
}

fn install_fabric(
    window: &Window,
    options: &InstallOptions,
    entry: &VersionEntry,
) -> Result<InstallResult, String> {
    let mods_dir = minecraft::mods_dir(&entry.minecraft_version)?;
    let game_dir = minecraft::nilsmod_game_dir(&entry.minecraft_version)?;
    fs::create_dir_all(&mods_dir).map_err(|err| err.to_string())?;

    let mut installed_files = Vec::new();
    let mod_url = entry
        .mod_url
        .as_ref()
        .ok_or("Fabric entry is missing modUrl")?;

    log(window, "NilsMod.jar wird heruntergeladen...");
    let nilsmod_path = mods_dir.join("NilsMod.jar");
    download_checked(
        window,
        mod_url,
        &nilsmod_path,
        entry.sha256.as_deref(),
        entry.sha512.as_deref(),
    )?;
    installed_files.push(nilsmod_path.display().to_string());

    for required in &entry.required_mods {
        log(window, &format!("{} wird installiert...", required.name));
        let path = mods_dir.join(&required.file_name);
        download_extra(window, required, &path)?;
        installed_files.push(path.display().to_string());
    }

    for optional in &entry.optional_mods {
        let enabled = match optional.id.as_str() {
            "sodium" => options.include_sodium,
            "simple-voice-chat" => options.include_voice_chat,
            _ => optional.default_enabled,
        };

        let path = mods_dir.join(&optional.file_name);
        if enabled {
            log(window, &format!("{} wird installiert...", optional.name));
            download_extra(window, optional, &path)?;
            installed_files.push(path.display().to_string());
        } else if path.exists() {
            fs::remove_file(&path).map_err(|err| err.to_string())?;
            log(
                window,
                &format!("{} wurde deaktiviert und entfernt.", optional.name),
            );
        }
    }

    let loader = entry
        .fabric_loader
        .as_deref()
        .ok_or("Fabric entry is missing fabricLoader")?;
    log(window, "Fabric Loader-Profil wird geschrieben...");
    let version_id = minecraft::install_fabric_version(&entry.minecraft_version, loader)?;
    let profile_name =
        minecraft::update_launcher_profile(&entry.minecraft_version, &version_id, &game_dir)?;

    write_local_state(entry)?;
    log(window, "Installation abgeschlossen.");

    Ok(InstallResult {
        profile_name,
        version_id,
        game_dir: game_dir.display().to_string(),
        installed_files,
    })
}

fn install_legacy(
    window: &Window,
    options: &InstallOptions,
    entry: &VersionEntry,
) -> Result<InstallResult, String> {
    let patcher_url = entry
        .patcher_url
        .as_ref()
        .ok_or("Legacy entry is missing patcherUrl")?;
    let client_dir = minecraft::nilsmod_game_dir(&options.version)?.join("client");
    fs::create_dir_all(&client_dir).map_err(|err| err.to_string())?;
    let patcher = client_dir.join("NilsMod-1.8.9-patcher.jar");

    log(window, "Legacy-Patcher wird vorbereitet...");
    download_checked(
        window,
        patcher_url,
        &patcher,
        entry.sha256.as_deref(),
        entry.sha512.as_deref(),
    )?;
    log(
        window,
        "Legacy 1.8.9 patch installation is prepared, but patch application is not implemented yet.",
    );

    Ok(InstallResult {
        profile_name: "NilsMod 1.8.9".to_string(),
        version_id: "legacy-prepared".to_string(),
        game_dir: client_dir.display().to_string(),
        installed_files: vec![patcher.display().to_string()],
    })
}

fn download_extra(window: &Window, extra: &ExtraMod, target: &Path) -> Result<(), String> {
    download_checked(
        window,
        &extra.url,
        target,
        extra.sha256.as_deref(),
        extra.sha512.as_deref(),
    )
}

fn download_checked(
    window: &Window,
    url: &str,
    target: &Path,
    sha256: Option<&str>,
    sha512: Option<&str>,
) -> Result<(), String> {
    if !url.starts_with("https://") {
        return Err(format!("Download URL must use HTTPS: {url}"));
    }

    if let Some(parent) = target.parent() {
        fs::create_dir_all(parent).map_err(|err| err.to_string())?;
    }

    let current_ok = target.exists() && verify_file(target, sha256, sha512).unwrap_or(false);
    if current_ok {
        log(window, &format!("{} ist aktuell.", target.display()));
        return Ok(());
    }

    let tmp = target.with_extension("download");
    let client = reqwest::blocking::Client::builder()
        .user_agent("NilsModLauncher/1.0.4")
        .build()
        .map_err(|err| err.to_string())?;
    let mut response = client
        .get(url)
        .send()
        .map_err(|err| format!("Download failed: {err}"))?
        .error_for_status()
        .map_err(|err| format!("Download returned an error: {err}"))?;

    let mut file = fs::File::create(&tmp).map_err(|err| err.to_string())?;
    let mut buffer = [0_u8; 64 * 1024];
    loop {
        let read = response.read(&mut buffer).map_err(|err| err.to_string())?;
        if read == 0 {
            break;
        }
        file.write_all(&buffer[..read])
            .map_err(|err| err.to_string())?;
    }
    file.flush().map_err(|err| err.to_string())?;

    if !verify_file(&tmp, sha256, sha512)? {
        let _ = fs::remove_file(&tmp);
        return Err(format!("Hash check failed for {}", target.display()));
    }

    if target.exists() {
        fs::remove_file(target).map_err(|err| err.to_string())?;
    }
    fs::rename(&tmp, target).map_err(|err| err.to_string())?;
    Ok(())
}

fn verify_file(path: &Path, sha256: Option<&str>, sha512: Option<&str>) -> Result<bool, String> {
    let bytes = fs::read(path).map_err(|err| err.to_string())?;

    if let Some(expected) = sha256 {
        let mut hasher = Sha256::new();
        hasher.update(&bytes);
        let actual = hex::encode(hasher.finalize());
        if !actual.eq_ignore_ascii_case(expected) {
            return Ok(false);
        }
    }

    if let Some(expected) = sha512 {
        let mut hasher = Sha512::new();
        hasher.update(&bytes);
        let actual = hex::encode(hasher.finalize());
        if !actual.eq_ignore_ascii_case(expected) {
            return Ok(false);
        }
    }

    Ok(sha256.is_some() || sha512.is_some())
}

fn write_local_state(entry: &VersionEntry) -> Result<(), String> {
    let path = minecraft::launcher_state_file()?;
    let state = json!({
        "minecraftVersion": entry.minecraft_version,
        "nilsmodVersion": entry.nilsmod_version,
        "installedAt": chrono::Utc::now().to_rfc3339()
    });
    minecraft::write_json_pretty(&path, &state)
}

fn log(window: &Window, message: &str) {
    let _ = window.emit("install-log", message.to_string());
}
