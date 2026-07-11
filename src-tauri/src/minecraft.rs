use chrono::Utc;
use serde_json::{json, Map, Value};
use std::env;
use std::fs;
use std::path::{Path, PathBuf};

pub fn minecraft_dir() -> Result<PathBuf, String> {
    if cfg!(target_os = "windows") {
        let appdata = env::var_os("APPDATA").ok_or("APPDATA is not set")?;
        return Ok(PathBuf::from(appdata).join(".minecraft"));
    }

    if cfg!(target_os = "macos") {
        let home = dirs::home_dir().ok_or("Home directory was not found")?;
        return Ok(home.join("Library").join("Application Support").join("minecraft"));
    }

    let home = dirs::home_dir().ok_or("Home directory was not found")?;
    Ok(home.join(".minecraft"))
}

pub fn nilsmod_game_dir(version: &str) -> Result<PathBuf, String> {
    Ok(minecraft_dir()?.join("nilsmod").join(version))
}

pub fn mods_dir(version: &str) -> Result<PathBuf, String> {
    Ok(nilsmod_game_dir(version)?.join("mods"))
}

pub fn launcher_state_file() -> Result<PathBuf, String> {
    Ok(minecraft_dir()?
        .join("nilsmod")
        .join("launcher")
        .join("local-version.json"))
}

pub fn install_fabric_version(minecraft_version: &str, loader_version: &str) -> Result<String, String> {
    let version_id = format!("fabric-loader-{loader_version}-{minecraft_version}");
    let version_dir = minecraft_dir()?.join("versions").join(&version_id);
    fs::create_dir_all(&version_dir).map_err(|err| err.to_string())?;

    let url = format!(
        "https://meta.fabricmc.net/v2/versions/loader/{}/{}/profile/json",
        urlencoding::encode(minecraft_version),
        urlencoding::encode(loader_version)
    );
    let client = reqwest::blocking::Client::builder()
        .user_agent("NilsModLauncher/1.0.3")
        .build()
        .map_err(|err| err.to_string())?;
    let mut profile = client
        .get(url)
        .send()
        .map_err(|err| format!("Fabric metadata request failed: {err}"))?
        .error_for_status()
        .map_err(|err| format!("Fabric metadata returned an error: {err}"))?
        .json::<Value>()
        .map_err(|err| format!("Fabric metadata JSON is invalid: {err}"))?;

    profile["id"] = json!(version_id);
    let file = version_dir.join(format!("{version_id}.json"));
    write_json_pretty(&file, &profile)?;
    Ok(version_id)
}

pub fn update_launcher_profile(
    minecraft_version: &str,
    version_id: &str,
    game_dir: &Path,
) -> Result<String, String> {
    let mc_dir = minecraft_dir()?;
    fs::create_dir_all(&mc_dir).map_err(|err| err.to_string())?;
    let profiles_file = mc_dir.join("launcher_profiles.json");
    let mut root = if profiles_file.exists() {
        fs::copy(&profiles_file, profiles_file.with_extension("json.bak"))
            .map_err(|err| format!("Could not back up launcher_profiles.json: {err}"))?;
        let text = fs::read_to_string(&profiles_file).map_err(|err| err.to_string())?;
        serde_json::from_str::<Value>(&text).unwrap_or_else(|_| json!({}))
    } else {
        json!({})
    };

    if !root.is_object() {
        root = json!({});
    }

    let object = root.as_object_mut().ok_or("launcher_profiles root is not an object")?;
    let profiles = object
        .entry("profiles")
        .or_insert_with(|| Value::Object(Map::new()));
    if !profiles.is_object() {
        *profiles = Value::Object(Map::new());
    }

    let profile_id = format!("nilsmod-{minecraft_version}");
    let now = Utc::now().to_rfc3339_opts(chrono::SecondsFormat::Millis, true);
    profiles.as_object_mut().unwrap().insert(
        profile_id,
        json!({
            "created": now,
            "gameDir": game_dir.display().to_string(),
            "icon": "Furnace",
            "javaArgs": "-Xmx4G",
            "lastUsed": now,
            "lastVersionId": version_id,
            "name": format!("NilsMod {minecraft_version}"),
            "type": "custom"
        }),
    );

    write_json_pretty(&profiles_file, &root)?;
    Ok(format!("NilsMod {minecraft_version}"))
}

pub fn write_json_pretty(path: &Path, value: &Value) -> Result<(), String> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|err| err.to_string())?;
    }
    let text = serde_json::to_string_pretty(value).map_err(|err| err.to_string())?;
    fs::write(path, text).map_err(|err| err.to_string())
}
