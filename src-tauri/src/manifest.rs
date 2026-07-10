use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;

const FALLBACK_MANIFEST: &str = include_str!("../../manifest/nilsmod-manifest.example.json");

#[derive(Debug, Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ManifestModel {
    pub launcher_version: String,
    pub latest: BTreeMap<String, VersionEntry>,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct VersionEntry {
    #[serde(rename = "type")]
    pub kind: String,
    pub minecraft_version: String,
    pub fabric_loader: Option<String>,
    pub nilsmod_version: String,
    pub mod_url: Option<String>,
    pub patcher_url: Option<String>,
    pub sha256: Option<String>,
    pub sha512: Option<String>,
    #[serde(default)]
    pub required_mods: Vec<ExtraMod>,
    #[serde(default)]
    pub optional_mods: Vec<ExtraMod>,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ExtraMod {
    pub id: String,
    pub name: String,
    pub version: String,
    pub file_name: String,
    pub url: String,
    pub sha256: Option<String>,
    pub sha512: Option<String>,
    #[serde(default)]
    pub default_enabled: bool,
}

pub fn load_manifest(url: &str) -> Result<ManifestModel, String> {
    if !url.starts_with("https://") {
        return parse_fallback(format!("Manifest URL must use HTTPS: {url}"));
    }

    let client = reqwest::blocking::Client::builder()
        .user_agent("NilsModLauncher/1.0.1")
        .build()
        .map_err(|err| err.to_string())?;

    match client.get(url).send() {
        Ok(response) if response.status().is_success() => response
            .json::<ManifestModel>()
            .map_err(|err| format!("Manifest JSON is invalid: {err}")),
        Ok(response) => parse_fallback(format!("Manifest request returned {}", response.status())),
        Err(err) => parse_fallback(format!("Manifest request failed: {err}")),
    }
}

fn parse_fallback(reason: String) -> Result<ManifestModel, String> {
    eprintln!("{reason}; using bundled manifest.");
    serde_json::from_str(FALLBACK_MANIFEST)
        .map_err(|err| format!("{reason}; bundled manifest is invalid: {err}"))
}
