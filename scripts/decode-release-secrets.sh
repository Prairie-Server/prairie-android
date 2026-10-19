#!/usr/bin/env bash
# Writes the release signing key and Firebase config from CI secrets so Gradle
# can build signed release artifacts. Used by release.yml.

set -euo pipefail

# Gradle reads the passwords and alias itself; check them here so a missing
# secret fails now rather than at the signing step of a long build.
# PRAIRIE_RELEASE_KEY_PASSWORD is optional (it defaults to the keystore password).
for name in PRAIRIE_RELEASE_KEYSTORE_B64 PRAIRIE_RELEASE_KEYSTORE_PASSWORD PRAIRIE_RELEASE_KEY_ALIAS; do
  if [[ -z "${!name:-}" ]]; then
    echo "::error::${name} secret is not set."
    exit 1
  fi
done
keystore="${RUNNER_TEMP}/prairie-release.jks"
printf '%s' "${PRAIRIE_RELEASE_KEYSTORE_B64}" | base64 -d > "${keystore}"
echo "PRAIRIE_RELEASE_KEYSTORE=${keystore}" >> "${GITHUB_ENV}"

# Firebase config for push notifications. The app works without it (push
# registration is skipped), but release builds should ship with push working.
# Encode with: base64 < androidApp/google-services.json | tr -d '\n'
if [[ -n "${PRAIRIE_GOOGLE_SERVICES_JSON_B64:-}" ]]; then
  printf '%s' "${PRAIRIE_GOOGLE_SERVICES_JSON_B64}" | base64 -d > androidApp/google-services.json
else
  echo "::warning::PRAIRIE_GOOGLE_SERVICES_JSON_B64 is not set; building without push notifications."
fi
