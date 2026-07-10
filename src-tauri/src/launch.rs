use crate::installer::{self, InstallOptions};
use crate::manifest;
use crate::minecraft;
use flate2::read::GzDecoder;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use sha1::{Digest, Sha1};
use std::collections::HashMap;
use std::env;
use std::fs;
use std::io::{Read, Write};
use std::path::{Path, PathBuf};
use std::process::Command;
use tauri::{Emitter, Window};

const VERSION_MANIFEST_URL: &str =
    "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
const ZULU_PACKAGES_API: &str = "https://api.azul.com/metadata/v1/zulu/packages/";

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LaunchOptions {
    pub version: String,
    pub manifest_url: Option<String>,
    pub include_sodium: bool,
    pub include_voice_chat: bool,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LaunchResult {
    pub profile_name: String,
    pub version_id: String,
    pub game_dir: String,
    pub java_path: String,
    pub account_name: String,
    pub pid: Option<u32>,
}

struct VersionTree {
    version_id: String,
    minecraft_version: String,
    version_type: String,
    main_class: String,
    libraries: Vec<Value>,
    jvm_args: Vec<Value>,
    game_args: Vec<Value>,
    asset_index: Value,
    client_download: Value,
    logging: Option<Value>,
}

struct Artifact {
    path: String,
    url: String,
    sha1: Option<String>,
}

struct AuthInfo {
    name: String,
    uuid: String,
    access_token: String,
    xuid: String,
    client_id: String,
}

pub fn launch(
    window: Window,
    options: LaunchOptions,
    default_manifest_url: &str,
) -> Result<LaunchResult, String> {
    log(&window, "Installation wird geprueft...");
    let install_result = installer::install(
        window.clone(),
        InstallOptions {
            version: options.version.clone(),
            manifest_url: options.manifest_url.clone(),
            include_sodium: options.include_sodium,
            include_voice_chat: options.include_voice_chat,
        },
        default_manifest_url,
    )?;

    let manifest_url = options.manifest_url.as_deref().unwrap_or(default_manifest_url);
    let launcher_manifest = manifest::load_manifest(manifest_url)?;
    let entry = launcher_manifest
        .latest
        .get(&options.version)
        .ok_or_else(|| format!("Version {} was not found in manifest", options.version))?;

    if entry.kind != "fabric" {
        return Err("Direktstart ist aktuell nur fuer Fabric-Versionen verfuegbar.".to_string());
    }

    log(&window, "Minecraft Runtime-Dateien werden vorbereitet...");
    ensure_minecraft_version_json(&entry.minecraft_version)?;
    let version_tree = load_version_tree(&install_result.version_id, &entry.minecraft_version)?;
    let client_jar = ensure_client_jar(&version_tree)?;
    let libraries = ensure_libraries(&window, &version_tree)?;
    let natives_dir = extract_natives(&window, &version_tree, &libraries)?;
    ensure_assets(&window, &version_tree)?;
    let logging_arg = ensure_logging(&window, &version_tree)?;

    let auth = read_active_account(&window)?;
    let java_path = ensure_zulu_java(&window, &entry.minecraft_version)?;
    let classpath = build_classpath(&libraries, &client_jar);
    let game_dir = minecraft::nilsmod_game_dir(&entry.minecraft_version)?;
    fs::create_dir_all(&game_dir).map_err(|err| err.to_string())?;

    let mut replacements = HashMap::new();
    replacements.insert("auth_player_name".to_string(), auth.name.clone());
    replacements.insert("version_name".to_string(), version_tree.version_id.clone());
    replacements.insert(
        "game_directory".to_string(),
        game_dir.display().to_string(),
    );
    replacements.insert(
        "assets_root".to_string(),
        minecraft::minecraft_dir()?.join("assets").display().to_string(),
    );
    replacements.insert(
        "game_assets".to_string(),
        minecraft::minecraft_dir()?.join("assets").display().to_string(),
    );
    replacements.insert(
        "assets_index_name".to_string(),
        asset_index_id(&version_tree.asset_index)?,
    );
    replacements.insert("auth_uuid".to_string(), auth.uuid.clone());
    replacements.insert("auth_access_token".to_string(), auth.access_token.clone());
    replacements.insert(
        "auth_session".to_string(),
        format!("token:{}:{}", auth.access_token, auth.uuid),
    );
    replacements.insert("clientid".to_string(), auth.client_id.clone());
    replacements.insert("auth_xuid".to_string(), auth.xuid.clone());
    replacements.insert("user_type".to_string(), "msa".to_string());
    replacements.insert("user_properties".to_string(), "{}".to_string());
    replacements.insert("version_type".to_string(), version_tree.version_type.clone());
    replacements.insert("launcher_name".to_string(), "NilsMod Launcher".to_string());
    replacements.insert("launcher_version".to_string(), "1.0.2".to_string());
    replacements.insert(
        "natives_directory".to_string(),
        natives_dir.display().to_string(),
    );
    replacements.insert("classpath".to_string(), classpath);

    let mut args = Vec::new();
    args.push("-Xms512M".to_string());
    args.push("-Xmx4G".to_string());
    if let Some(arg) = logging_arg {
        args.push(arg);
    }
    push_arguments(&mut args, &version_tree.jvm_args, &replacements, true);
    args.push(version_tree.main_class.clone());
    push_arguments(&mut args, &version_tree.game_args, &replacements, false);

    log(&window, "NilsMod wird gestartet...");
    let mut command = Command::new(&java_path);
    command.args(&args).current_dir(&game_dir);
    let child = command
        .spawn()
        .map_err(|err| format!("Java konnte Minecraft nicht starten: {err}"))?;

    Ok(LaunchResult {
        profile_name: install_result.profile_name,
        version_id: version_tree.version_id,
        game_dir: game_dir.display().to_string(),
        java_path: java_path.display().to_string(),
        account_name: auth.name,
        pid: Some(child.id()),
    })
}

fn ensure_minecraft_version_json(version: &str) -> Result<(), String> {
    let version_dir = minecraft::minecraft_dir()?.join("versions").join(version);
    let version_file = version_dir.join(format!("{version}.json"));
    if version_file.exists() {
        return Ok(());
    }

    fs::create_dir_all(&version_dir).map_err(|err| err.to_string())?;
    let manifest: Value = http_client()?
        .get(VERSION_MANIFEST_URL)
        .send()
        .map_err(|err| format!("Minecraft version manifest request failed: {err}"))?
        .error_for_status()
        .map_err(|err| format!("Minecraft version manifest returned an error: {err}"))?
        .json()
        .map_err(|err| format!("Minecraft version manifest JSON is invalid: {err}"))?;

    let version_url = manifest["versions"]
        .as_array()
        .and_then(|versions| {
            versions
                .iter()
                .find(|item| item["id"].as_str() == Some(version))
        })
        .and_then(|item| item["url"].as_str())
        .ok_or_else(|| format!("Minecraft version {version} was not found by Mojang"))?;

    download_url(version_url, &version_file, None)
}

fn load_version_tree(version_id: &str, minecraft_version: &str) -> Result<VersionTree, String> {
    let child = read_version_json(version_id)?;
    let parent_id = child["inheritsFrom"].as_str().unwrap_or(minecraft_version);
    let parent = read_version_json(parent_id)?;

    let mut libraries = Vec::new();
    if let Some(parent_libs) = parent["libraries"].as_array() {
        libraries.extend(parent_libs.iter().cloned());
    }
    if let Some(child_libs) = child["libraries"].as_array() {
        libraries.extend(child_libs.iter().cloned());
    }

    let mut jvm_args = Vec::new();
    let mut game_args = Vec::new();
    if let Some(args) = parent["arguments"]["jvm"].as_array() {
        jvm_args.extend(args.iter().cloned());
    }
    if let Some(args) = child["arguments"]["jvm"].as_array() {
        jvm_args.extend(args.iter().cloned());
    }
    if let Some(args) = parent["arguments"]["game"].as_array() {
        game_args.extend(args.iter().cloned());
    }
    if let Some(args) = child["arguments"]["game"].as_array() {
        game_args.extend(args.iter().cloned());
    }

    Ok(VersionTree {
        version_id: version_id.to_string(),
        minecraft_version: minecraft_version.to_string(),
        version_type: child["type"]
            .as_str()
            .or_else(|| parent["type"].as_str())
            .unwrap_or("release")
            .to_string(),
        main_class: child["mainClass"]
            .as_str()
            .or_else(|| parent["mainClass"].as_str())
            .ok_or("Version JSON is missing mainClass")?
            .to_string(),
        libraries,
        jvm_args,
        game_args,
        asset_index: parent["assetIndex"].clone(),
        client_download: parent["downloads"]["client"].clone(),
        logging: parent["logging"]["client"].as_object().map(|_| parent["logging"]["client"].clone()),
    })
}

fn read_version_json(version_id: &str) -> Result<Value, String> {
    let file = minecraft::minecraft_dir()?
        .join("versions")
        .join(version_id)
        .join(format!("{version_id}.json"));
    let text = fs::read_to_string(&file)
        .map_err(|err| format!("Version JSON {} konnte nicht gelesen werden: {err}", file.display()))?;
    serde_json::from_str(&text).map_err(|err| format!("Version JSON {} ist ungueltig: {err}", file.display()))
}

fn ensure_client_jar(tree: &VersionTree) -> Result<PathBuf, String> {
    let jar = minecraft::minecraft_dir()?
        .join("versions")
        .join(&tree.minecraft_version)
        .join(format!("{}.jar", tree.minecraft_version));
    if jar.exists() {
        return Ok(jar);
    }
    let url = tree.client_download["url"]
        .as_str()
        .ok_or("Minecraft client download URL is missing")?;
    let sha1 = tree.client_download["sha1"].as_str();
    download_url(url, &jar, sha1)?;
    Ok(jar)
}

fn ensure_libraries(window: &Window, tree: &VersionTree) -> Result<Vec<PathBuf>, String> {
    let mut paths = Vec::new();
    for lib in &tree.libraries {
        if !rules_allow(lib.get("rules")) {
            continue;
        }
        let Some(artifact) = library_artifact(lib)? else {
            continue;
        };
        if !native_artifact_allowed(&artifact.path) {
            continue;
        }
        let path = minecraft::minecraft_dir()?.join("libraries").join(&artifact.path);
        if !path.exists() {
            let label = lib["name"].as_str().unwrap_or(&artifact.path);
            log(window, &format!("Library wird geladen: {label}"));
            download_url(&artifact.url, &path, artifact.sha1.as_deref())?;
        }
        paths.push(path);
    }
    Ok(paths)
}

fn native_artifact_allowed(path: &str) -> bool {
    if !path.contains("natives") {
        return true;
    }

    let arch = env::consts::ARCH;
    if path.contains("arm64") || path.contains("aarch_64") {
        return arch == "aarch64";
    }
    if path.contains("x86.jar") || path.contains("i386") {
        return arch == "x86";
    }
    if path.contains("x86_64") {
        return arch == "x86_64";
    }
    true
}

fn library_artifact(lib: &Value) -> Result<Option<Artifact>, String> {
    if let Some(path) = lib["downloads"]["artifact"]["path"].as_str() {
        let url = lib["downloads"]["artifact"]["url"]
            .as_str()
            .ok_or("Library artifact URL is missing")?;
        return Ok(Some(Artifact {
            path: path.to_string(),
            url: url.to_string(),
            sha1: lib["downloads"]["artifact"]["sha1"].as_str().map(str::to_string),
        }));
    }

    let Some(name) = lib["name"].as_str() else {
        return Ok(None);
    };
    let Some(base_url) = lib["url"].as_str() else {
        return Ok(None);
    };
    let path = maven_path(name)?;
    Ok(Some(Artifact {
        url: format!("{}{}", base_url.trim_end_matches('/'), format!("/{path}")),
        path,
        sha1: lib["sha1"].as_str().map(str::to_string),
    }))
}

fn maven_path(name: &str) -> Result<String, String> {
    let parts: Vec<&str> = name.split(':').collect();
    if parts.len() < 3 {
        return Err(format!("Invalid Maven coordinate: {name}"));
    }
    let group = parts[0].replace('.', "/");
    let artifact = parts[1];
    let version = parts[2];
    let classifier = parts.get(3).copied();
    let file = match classifier {
        Some(classifier) => format!("{artifact}-{version}-{classifier}.jar"),
        None => format!("{artifact}-{version}.jar"),
    };
    Ok(format!("{group}/{artifact}/{version}/{file}"))
}

fn extract_natives(
    window: &Window,
    tree: &VersionTree,
    libraries: &[PathBuf],
) -> Result<PathBuf, String> {
    let natives_dir = minecraft::nilsmod_game_dir(&tree.minecraft_version)?
        .join("natives")
        .join(&tree.version_id);
    fs::create_dir_all(&natives_dir).map_err(|err| err.to_string())?;

    for path in libraries {
        let name = path.file_name().and_then(|name| name.to_str()).unwrap_or("");
        if !name.contains("natives") {
            continue;
        }
        log(window, &format!("Natives werden entpackt: {name}"));
        extract_native_jar(path, &natives_dir)?;
    }

    Ok(natives_dir)
}

fn extract_native_jar(jar: &Path, target: &Path) -> Result<(), String> {
    let file = fs::File::open(jar).map_err(|err| err.to_string())?;
    let mut archive = zip::ZipArchive::new(file).map_err(|err| err.to_string())?;
    for index in 0..archive.len() {
        let mut entry = archive.by_index(index).map_err(|err| err.to_string())?;
        let name = entry.name().replace('\\', "/");
        if name.ends_with('/') || name.starts_with("META-INF/") || name.contains("..") {
            continue;
        }
        let out = target.join(&name);
        if let Some(parent) = out.parent() {
            fs::create_dir_all(parent).map_err(|err| err.to_string())?;
        }
        let mut out_file = fs::File::create(out).map_err(|err| err.to_string())?;
        std::io::copy(&mut entry, &mut out_file).map_err(|err| err.to_string())?;
    }
    Ok(())
}

fn ensure_assets(window: &Window, tree: &VersionTree) -> Result<(), String> {
    let id = asset_index_id(&tree.asset_index)?;
    let index_url = tree.asset_index["url"]
        .as_str()
        .ok_or("Asset index URL is missing")?;
    let index_sha1 = tree.asset_index["sha1"].as_str();
    let index_path = minecraft::minecraft_dir()?
        .join("assets")
        .join("indexes")
        .join(format!("{id}.json"));
    if !index_path.exists() {
        log(window, "Asset-Index wird geladen...");
        download_url(index_url, &index_path, index_sha1)?;
    }

    let text = fs::read_to_string(&index_path).map_err(|err| err.to_string())?;
    let index: Value = serde_json::from_str(&text).map_err(|err| err.to_string())?;
    let Some(objects) = index["objects"].as_object() else {
        return Ok(());
    };

    let mut missing = 0usize;
    for object in objects.values() {
        let Some(hash) = object["hash"].as_str() else {
            continue;
        };
        let path = minecraft::minecraft_dir()?
            .join("assets")
            .join("objects")
            .join(&hash[..2])
            .join(hash);
        if path.exists() {
            continue;
        }
        missing += 1;
        if missing == 1 || missing % 100 == 0 {
            log(window, &format!("Assets werden geladen... {missing}"));
        }
        let url = format!("https://resources.download.minecraft.net/{}/{}", &hash[..2], hash);
        download_url(&url, &path, Some(hash))?;
    }

    Ok(())
}

fn ensure_logging(window: &Window, tree: &VersionTree) -> Result<Option<String>, String> {
    let Some(logging) = &tree.logging else {
        return Ok(None);
    };
    let Some(argument) = logging["argument"].as_str() else {
        return Ok(None);
    };
    let Some(url) = logging["file"]["url"].as_str() else {
        return Ok(None);
    };
    let Some(id) = logging["file"]["id"].as_str() else {
        return Ok(None);
    };
    let path = minecraft::minecraft_dir()?.join("assets").join("log_configs").join(id);
    if !path.exists() {
        log(window, "Logging-Konfiguration wird geladen...");
        download_url(url, &path, logging["file"]["sha1"].as_str())?;
    }
    Ok(Some(argument.replace("${path}", &path.display().to_string())))
}

fn asset_index_id(asset_index: &Value) -> Result<String, String> {
    asset_index["id"]
        .as_str()
        .map(str::to_string)
        .ok_or("Asset index id is missing".to_string())
}

fn read_active_account(window: &Window) -> Result<AuthInfo, String> {
    let file = minecraft::minecraft_dir()?.join("launcher_accounts.json");
    let text = fs::read_to_string(&file).map_err(|err| {
        format!(
            "launcher_accounts.json konnte nicht gelesen werden. Bitte einmal im offiziellen Launcher anmelden: {err}"
        )
    })?;
    let root: Value = serde_json::from_str(&text)
        .map_err(|err| format!("launcher_accounts.json ist ungueltig: {err}"))?;
    let active_id = root["activeAccountLocalId"]
        .as_str()
        .ok_or("Kein aktiver Minecraft Account gefunden.")?;
    let account = &root["accounts"][active_id];
    let profile = &account["minecraftProfile"];
    let name = profile["name"]
        .as_str()
        .ok_or("Aktiver Account hat keinen Minecraft Namen.")?
        .to_string();
    let uuid = profile["id"]
        .as_str()
        .ok_or("Aktiver Account hat keine Minecraft UUID.")?
        .to_string();
    let access_token = account["accessToken"].as_str().unwrap_or("0").to_string();
    let xuid = account["remoteId"].as_str().unwrap_or("").to_string();
    let client_id = root["mojangClientToken"]
        .as_str()
        .unwrap_or("nilsmod-launcher")
        .to_string();

    if let Some(expires) = account["accessTokenExpiresAt"].as_str() {
        if expires < chrono::Utc::now().to_rfc3339().as_str() {
            log(
                window,
                "Hinweis: Der gespeicherte Minecraft Access-Token wirkt abgelaufen. Das Spiel startet trotzdem; Multiplayer kann einen frischen Login brauchen.",
            );
        }
    }

    Ok(AuthInfo {
        name,
        uuid,
        access_token,
        xuid,
        client_id,
    })
}

fn ensure_zulu_java(window: &Window, minecraft_version: &str) -> Result<PathBuf, String> {
    let java_major = zulu_java_major(minecraft_version);
    let runtime_base = minecraft::minecraft_dir()?
        .join("nilsmod")
        .join("launcher")
        .join("runtimes");
    let runtime_dir = runtime_base.join(format!(
        "zulu-jre{}-{}-{}",
        java_major,
        zulu_os(),
        zulu_arch()
    ));

    if let Some(java) = find_java_binary(&runtime_dir) {
        return Ok(java);
    }

    log(window, &format!("Zulu Java {java_major} wird vorbereitet..."));
    fs::create_dir_all(&runtime_base).map_err(|err| err.to_string())?;
    let package = resolve_zulu_package(java_major)?;
    let archive_name = package["name"]
        .as_str()
        .unwrap_or("zulu-jre21")
        .to_string();
    let package_url = package["download_url"]
        .as_str()
        .ok_or("Zulu package has no download_url")?;
    let archive_path = runtime_base.join(&archive_name);

    log(window, &format!("Zulu Runtime wird geladen: {archive_name}"));
    download_url(package_url, &archive_path, None)?;

    let new_dir = runtime_dir.with_extension("new");
    if new_dir.exists() {
        remove_dir_inside(&new_dir, &runtime_base)?;
    }
    fs::create_dir_all(&new_dir).map_err(|err| err.to_string())?;

    log(window, "Zulu Runtime wird entpackt...");
    extract_runtime_archive(&archive_path, &new_dir)?;
    let java = find_java_binary(&new_dir)
        .ok_or("Zulu Runtime wurde entpackt, aber Java wurde nicht gefunden.")?;

    if runtime_dir.exists() {
        remove_dir_inside(&runtime_dir, &runtime_base)?;
    }
    fs::rename(&new_dir, &runtime_dir).map_err(|err| err.to_string())?;

    let final_java = runtime_dir.join(
        java.strip_prefix(&new_dir)
            .map_err(|err| err.to_string())?,
    );
    Ok(final_java)
}

fn resolve_zulu_package(java_major: u32) -> Result<Value, String> {
    let url = format!(
        "{ZULU_PACKAGES_API}?java_version={java_major}&os={}&arch={}&archive_type={}&java_package_type=jre&release_status=ga&availability_types=CA&latest=true&page=1&page_size=20",
        zulu_os(),
        zulu_arch(),
        zulu_archive_type()
    );
    let packages: Vec<Value> = http_client()?
        .get(url)
        .send()
        .map_err(|err| format!("Zulu metadata request failed: {err}"))?
        .error_for_status()
        .map_err(|err| format!("Zulu metadata returned an error: {err}"))?
        .json()
        .map_err(|err| format!("Zulu metadata JSON is invalid: {err}"))?;

    packages
        .iter()
        .find(|package| package_preferred(package))
        .cloned()
        .or_else(|| {
            packages
                .iter()
                .find(|package| package_usable(package))
                .cloned()
        })
        .or_else(|| packages.first().cloned())
        .ok_or_else(|| format!("Keine passende Zulu Java {java_major} Runtime gefunden."))
}

fn zulu_java_major(minecraft_version: &str) -> u32 {
    let parts: Vec<u32> = minecraft_version
        .split('.')
        .filter_map(|part| part.parse::<u32>().ok())
        .collect();
    if parts.first() != Some(&1) {
        return 21;
    }

    let minor = parts.get(1).copied().unwrap_or(0);
    let patch = parts.get(2).copied().unwrap_or(0);
    if minor >= 21 || (minor == 20 && patch >= 5) {
        21
    } else if minor >= 18 {
        17
    } else if minor == 17 {
        16
    } else {
        8
    }
}

fn package_preferred(package: &Value) -> bool {
    let name = package["name"].as_str().unwrap_or("").to_ascii_lowercase();
    package_usable(package) && name.contains("-ca-jre")
}

fn package_usable(package: &Value) -> bool {
    let name = package["name"].as_str().unwrap_or("").to_ascii_lowercase();
    !name.contains("-fx-") && !name.contains("crac") && !name.contains("musl")
}

fn zulu_os() -> &'static str {
    if cfg!(target_os = "windows") {
        "windows"
    } else if cfg!(target_os = "macos") {
        "macos"
    } else {
        "linux"
    }
}

fn zulu_arch() -> &'static str {
    match env::consts::ARCH {
        "aarch64" => "arm64",
        "x86" => "x86",
        _ => "x64",
    }
}

fn zulu_archive_type() -> &'static str {
    if cfg!(target_os = "windows") {
        "zip"
    } else {
        "tar.gz"
    }
}

fn extract_runtime_archive(archive: &Path, target: &Path) -> Result<(), String> {
    if zulu_archive_type() == "zip" {
        extract_zip(archive, target)
    } else {
        extract_tar_gz(archive, target)
    }
}

fn extract_zip(archive: &Path, target: &Path) -> Result<(), String> {
    let file = fs::File::open(archive).map_err(|err| err.to_string())?;
    let mut zip = zip::ZipArchive::new(file).map_err(|err| err.to_string())?;
    for index in 0..zip.len() {
        let mut entry = zip.by_index(index).map_err(|err| err.to_string())?;
        let Some(safe_name) = entry.enclosed_name().map(|path| path.to_owned()) else {
            continue;
        };
        let out = target.join(safe_name);
        if entry.is_dir() {
            fs::create_dir_all(&out).map_err(|err| err.to_string())?;
            continue;
        }
        if let Some(parent) = out.parent() {
            fs::create_dir_all(parent).map_err(|err| err.to_string())?;
        }
        let mut out_file = fs::File::create(out).map_err(|err| err.to_string())?;
        std::io::copy(&mut entry, &mut out_file).map_err(|err| err.to_string())?;
    }
    Ok(())
}

fn extract_tar_gz(archive: &Path, target: &Path) -> Result<(), String> {
    let file = fs::File::open(archive).map_err(|err| err.to_string())?;
    let decoder = GzDecoder::new(file);
    let mut archive = tar::Archive::new(decoder);
    archive.unpack(target).map_err(|err| err.to_string())
}

fn find_java_binary(root: &Path) -> Option<PathBuf> {
    let exe = if cfg!(target_os = "windows") {
        "javaw.exe"
    } else {
        "java"
    };
    let mut stack = vec![root.to_path_buf()];
    while let Some(dir) = stack.pop() {
        let entries = fs::read_dir(dir).ok()?;
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_dir() {
                stack.push(path);
                continue;
            }
            if path.file_name().and_then(|name| name.to_str()) == Some(exe) {
                return Some(path);
            }
        }
    }
    None
}

fn remove_dir_inside(path: &Path, parent: &Path) -> Result<(), String> {
    let absolute_parent = parent.canonicalize().map_err(|err| err.to_string())?;
    if path.exists() {
        let absolute_path = path.canonicalize().map_err(|err| err.to_string())?;
        if !absolute_path.starts_with(&absolute_parent) {
            return Err(format!("Refusing to remove {}", absolute_path.display()));
        }
        fs::remove_dir_all(&absolute_path).map_err(|err| err.to_string())?;
    }
    Ok(())
}

fn build_classpath(libraries: &[PathBuf], client_jar: &Path) -> String {
    let sep = if cfg!(target_os = "windows") { ";" } else { ":" };
    libraries
        .iter()
        .chain(std::iter::once(client_jar))
        .map(|path| path.display().to_string())
        .collect::<Vec<_>>()
        .join(sep)
}

fn push_arguments(
    target: &mut Vec<String>,
    source: &[Value],
    replacements: &HashMap<String, String>,
    allow_feature_rules: bool,
) {
    for item in source {
        match item {
            Value::String(text) => target.push(replace_vars(text, replacements)),
            Value::Object(object) => {
                if !rules_allow_with_features(object.get("rules"), allow_feature_rules) {
                    continue;
                }
                match object.get("value") {
                    Some(Value::String(text)) => target.push(replace_vars(text, replacements)),
                    Some(Value::Array(values)) => {
                        for value in values {
                            if let Some(text) = value.as_str() {
                                target.push(replace_vars(text, replacements));
                            }
                        }
                    }
                    _ => {}
                }
            }
            _ => {}
        }
    }
}

fn replace_vars(text: &str, replacements: &HashMap<String, String>) -> String {
    let mut out = text.to_string();
    for (key, value) in replacements {
        out = out.replace(&format!("${{{key}}}"), value);
    }
    out
}

fn rules_allow(rules: Option<&Value>) -> bool {
    rules_allow_with_features(rules, true)
}

fn rules_allow_with_features(rules: Option<&Value>, allow_features: bool) -> bool {
    let Some(Value::Array(rules)) = rules else {
        return true;
    };
    let mut allowed = false;
    for rule in rules {
        if !rule_matches(rule, allow_features) {
            continue;
        }
        allowed = rule["action"].as_str() == Some("allow");
    }
    allowed
}

fn rule_matches(rule: &Value, allow_features: bool) -> bool {
    if rule.get("features").is_some() && !allow_features {
        return false;
    }

    if let Some(os) = rule.get("os") {
        if let Some(name) = os["name"].as_str() {
            if name != current_os_name() {
                return false;
            }
        }
        if let Some(arch) = os["arch"].as_str() {
            if arch == "x86" && env::consts::ARCH != "x86" {
                return false;
            }
        }
    }
    true
}

fn current_os_name() -> &'static str {
    if cfg!(target_os = "windows") {
        "windows"
    } else if cfg!(target_os = "macos") {
        "osx"
    } else {
        "linux"
    }
}

fn http_client() -> Result<reqwest::blocking::Client, String> {
    reqwest::blocking::Client::builder()
        .user_agent("NilsModLauncher/1.0.2")
        .build()
        .map_err(|err| err.to_string())
}

fn download_url(url: &str, target: &Path, expected_sha1: Option<&str>) -> Result<(), String> {
    if let Some(expected) = expected_sha1 {
        if target.exists() && verify_sha1(target, expected).unwrap_or(false) {
            return Ok(());
        }
    } else if target.exists() {
        return Ok(());
    }

    if let Some(parent) = target.parent() {
        fs::create_dir_all(parent).map_err(|err| err.to_string())?;
    }

    let tmp = target.with_extension("download");
    let mut response = http_client()?
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
        file.write_all(&buffer[..read]).map_err(|err| err.to_string())?;
    }
    file.flush().map_err(|err| err.to_string())?;

    if let Some(expected) = expected_sha1 {
        if !verify_sha1(&tmp, expected)? {
            let _ = fs::remove_file(&tmp);
            return Err(format!("SHA1 check failed for {}", target.display()));
        }
    }

    if target.exists() {
        fs::remove_file(target).map_err(|err| err.to_string())?;
    }
    fs::rename(tmp, target).map_err(|err| err.to_string())
}

fn verify_sha1(path: &Path, expected: &str) -> Result<bool, String> {
    let bytes = fs::read(path).map_err(|err| err.to_string())?;
    let mut hasher = Sha1::new();
    hasher.update(&bytes);
    let actual = hex::encode(hasher.finalize());
    Ok(actual.eq_ignore_ascii_case(expected))
}

fn log(window: &Window, message: &str) {
    let _ = window.emit("install-log", message.to_string());
}
