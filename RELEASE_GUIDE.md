# Release Guide for Lyocell

This guide explains how to publish new releases of Lyocell to Homebrew and Scoop.

## Prerequisites

### 1. Repository Setup

You need to create/verify these GitHub repositories:

- **`wilhg/homebrew-lyocell`** - Homebrew tap repository
- **`wilhg/lyocell-scoop`** - Scoop bucket repository

Both repositories should be public and initialized with a README.

### 2. GitHub Token

`GITHUB_TOKEN` is enough for the main release, but cross-repo pushes (Homebrew/Scoop) require a PAT exposed as `JRELEASER_GITHUB_TOKEN`:

1. GitHub Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Scopes: `repo`, `workflow`
3. Add the secret **`JRELEASER_GITHUB_TOKEN`** to `wilhg/lyocell`
4. Confirm the token has access to `wilhg/homebrew-lyocell` and `wilhg/lyocell-scoop`

### 3. Repository Structure

#### Homebrew Tap (`wilhg/homebrew-lyocell`)
```
wilhg/homebrew-lyocell/
├── README.md
└── Formula/
    └── lyocell.rb  (will be auto-generated)
```

#### Scoop Bucket (`wilhg/lyocell-scoop`)
```
lyocell-scoop/
├── README.md
└── bucket/
    └── lyocell.json  (will be auto-generated)
```

## Release Process

### Step 1: Update Version

Update the version in `build.gradle`:

```gradle
version = '0.3.3'  // Update this
```

### Step 2: Update Changelog

Update `CHANGELOG.md` with the new version:

```markdown
## [0.3.3] - 2025-12-25

### Added
- ...

### Fixed
- ...
```

### Step 3: Commit Changes

```bash
git add build.gradle CHANGELOG.md
git commit -m "Bump version to 0.3.3"
git push origin main
```

### Step 4: Create and Push Tag

```bash
git tag v0.3.3
git push origin v0.3.3
```

This will automatically trigger the release workflow which will:

1. ✅ Build native binaries for Linux, macOS, and Windows
2. ✅ Create a GitHub release with the binaries attached
3. ✅ Update the Homebrew tap with the new formula
4. ✅ Update the Scoop bucket with the new manifest

### Step 5: Verify the Release

1. Check the GitHub Actions tab for the workflow status
2. Verify the release is created: https://github.com/wilhg/lyocell/releases
3. Test installation:

**macOS:**
```bash
brew tap wilhg/lyocell
brew install lyocell
lyocell --version
```

**Windows:**
```powershell
scoop bucket add lyocell https://github.com/wilhg/lyocell-scoop
scoop install lyocell
lyocell --version
```

**Linux:**
```bash
# Download the binary from the release page
wget https://github.com/wilhg/lyocell/releases/download/v0.3.3/lyocell-linux-amd64
chmod +x lyocell-linux-amd64
./lyocell-linux-amd64 --version
```

## Troubleshooting

### Workflow Fails to Push to homebrew-lyocell or lyocell-scoop

**Problem:** `remote: Permission to wilhg/homebrew-lyocell.git denied`

**Solution:** Ensure `JRELEASER_GITHUB_TOKEN` exists with `repo`+`workflow` scopes and is granted access to `wilhg/homebrew-lyocell` and `wilhg/lyocell-scoop`.

### Homebrew Formula Fails to Install

**Problem:** `SHA256 mismatch`

**Solution:** The workflow automatically calculates SHA256 checksums. If you manually edit the formula, ensure checksums match the actual binaries.

### Scoop Manifest Fails to Install

**Problem:** Binary not found or wrong path

**Solution:** Ensure the `bin` field in the manifest points to the correct filename: `lyocell-windows-amd64.exe`

## Manual Release (Alternative)

If you prefer to release manually:

### 1. Build Binaries Locally

```bash
# On macOS
./gradlew nativeCompile
cp build/native/nativeCompile/lyocell release-files/lyocell-macos-aarch64

# On Linux
./gradlew nativeCompile
cp build/native/nativeCompile/lyocell release-files/lyocell-linux-amd64

# On Windows
./gradlew nativeCompile
copy build\native\nativeCompile\lyocell.exe release-files\lyocell-windows-amd64.exe
```

### 2. Create Release with GitHub CLI

```bash
gh release create v0.3.3 \
  release-files/lyocell-linux-amd64 \
  release-files/lyocell-macos-aarch64 \
  release-files/lyocell-windows-amd64.exe \
  --title "Lyocell 0.3.3" \
  --notes "See CHANGELOG.md for details"
```

### 3. Update Homebrew Tap Manually

```bash
git clone https://github.com/wilhg/homebrew-lyocell.git
cd homebrew-lyocell
# Edit Formula/lyocell.rb with new version and SHA256
git commit -am "Update lyocell to 0.3.3"
git push
```

### 4. Update Scoop Bucket Manually

```bash
git clone https://github.com/wilhg/lyocell-scoop.git
cd lyocell-scoop
# Edit bucket/lyocell.json with new version and SHA256
git commit -am "Update lyocell to 0.3.3"
git push
```

## Tips

- **Testing Before Release:** Create a pre-release tag (e.g., `v0.3.3-rc1`) to test the workflow without affecting production users.
- **Rollback:** If a release has issues, you can delete the tag and release, fix the issue, and re-release with the same version.
- **Binary Size:** The current binaries are optimized for size using `-Os` flag. They should be around 50-100MB each.

## Next Steps

Consider adding:

1. **Code signing** for macOS binaries (requires Apple Developer account)
2. **Windows signing** for better Windows Defender compatibility
3. **Linux package repositories** (APT, YUM) for easier installation
4. **Docker images** for containerized environments
5. **Checksum verification** in the workflow for additional security

