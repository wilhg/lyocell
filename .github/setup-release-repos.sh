#!/bin/bash
set -e

# This script helps you set up the Homebrew tap and Scoop bucket repositories
# It creates the necessary directory structure and initializes them with README files

HOMEBREW_REPO="lyocell-tap"
SCOOP_REPO="lyocell-scoop"
GITHUB_USER="wilhg"

echo "🚀 Lyocell Release Repositories Setup"
echo "======================================"
echo ""

# Check if gh CLI is installed
if ! command -v gh &> /dev/null; then
    echo "❌ GitHub CLI (gh) is not installed."
    echo "   Please install it: https://cli.github.com/"
    exit 1
fi

# Check if authenticated
if ! gh auth status &> /dev/null; then
    echo "❌ Not authenticated with GitHub CLI."
    echo "   Please run: gh auth login"
    exit 1
fi

echo "✅ GitHub CLI is installed and authenticated"
echo ""

# Setup Homebrew Tap Repository
echo "📦 Setting up Homebrew Tap Repository..."
echo "Repository: $GITHUB_USER/$HOMEBREW_REPO"

if gh repo view "$GITHUB_USER/$HOMEBREW_REPO" &> /dev/null; then
    echo "✅ Repository already exists: $GITHUB_USER/$HOMEBREW_REPO"
else
    echo "Creating repository..."
    gh repo create "$GITHUB_USER/$HOMEBREW_REPO" --public --description "Homebrew tap for Lyocell" || {
        echo "❌ Failed to create repository. Please create it manually at:"
        echo "   https://github.com/new"
    }
fi

# Clone and setup Homebrew tap
if [ -d "../$HOMEBREW_REPO" ]; then
    echo "⚠️  Directory ../$HOMEBREW_REPO already exists. Skipping clone."
else
    echo "Cloning repository..."
    cd ..
    git clone "https://github.com/$GITHUB_USER/$HOMEBREW_REPO.git" || {
        echo "❌ Failed to clone. Make sure the repository exists."
        exit 1
    }
    cd "$HOMEBREW_REPO"
    
    # Create Formula directory
    mkdir -p Formula
    
    # Copy README if it doesn't exist
    if [ ! -f "README.md" ]; then
        cp "../lyocell/.github/HOMEBREW_TAP_README.md" "README.md"
    fi
    
    # Create a placeholder formula
    cat > Formula/lyocell.rb << 'EOF'
class Lyocell < Formula
  desc "High-performance load testing tool (k6 clone) on Java 25 & GraalVM"
  homepage "https://github.com/wilhg/lyocell"
  version "0.0.0"
  license "MIT"

  def install
    puts "This formula will be automatically updated by the release workflow."
    puts "Please wait for the first release to be published."
  end

  test do
    system "#{bin}/lyocell", "--version"
  end
end
EOF
    
    # Commit and push
    git add .
    git commit -m "Initial setup of Homebrew tap" || true
    git push
    
    cd ../lyocell
fi

echo "✅ Homebrew tap setup complete!"
echo ""

# Setup Scoop Bucket Repository
echo "📦 Setting up Scoop Bucket Repository..."
echo "Repository: $GITHUB_USER/$SCOOP_REPO"

if gh repo view "$GITHUB_USER/$SCOOP_REPO" &> /dev/null; then
    echo "✅ Repository already exists: $GITHUB_USER/$SCOOP_REPO"
else
    echo "Creating repository..."
    gh repo create "$GITHUB_USER/$SCOOP_REPO" --public --description "Scoop bucket for Lyocell" || {
        echo "❌ Failed to create repository. Please create it manually at:"
        echo "   https://github.com/new"
    }
fi

# Clone and setup Scoop bucket
if [ -d "../$SCOOP_REPO" ]; then
    echo "⚠️  Directory ../$SCOOP_REPO already exists. Skipping clone."
else
    echo "Cloning repository..."
    cd ..
    git clone "https://github.com/$GITHUB_USER/$SCOOP_REPO.git" || {
        echo "❌ Failed to clone. Make sure the repository exists."
        exit 1
    }
    cd "$SCOOP_REPO"
    
    # Create bucket directory
    mkdir -p bucket
    
    # Copy README if it doesn't exist
    if [ ! -f "README.md" ]; then
        cp "../lyocell/.github/SCOOP_BUCKET_README.md" "README.md"
    fi
    
    # Create a placeholder manifest
    cat > bucket/lyocell.json << 'EOF'
{
  "version": "0.0.0",
  "description": "High-performance load testing tool (k6 clone) on Java 25 & GraalVM",
  "homepage": "https://github.com/wilhg/lyocell",
  "license": "MIT",
  "url": "https://github.com/wilhg/lyocell/releases/download/v0.0.0/lyocell-windows-amd64.exe",
  "hash": "0000000000000000000000000000000000000000000000000000000000000000",
  "bin": "lyocell-windows-amd64.exe",
  "checkver": {
    "github": "https://github.com/wilhg/lyocell"
  },
  "autoupdate": {
    "url": "https://github.com/wilhg/lyocell/releases/download/v$version/lyocell-windows-amd64.exe"
  }
}
EOF
    
    # Commit and push
    git add .
    git commit -m "Initial setup of Scoop bucket" || true
    git push
    
    cd ../lyocell
fi

echo "✅ Scoop bucket setup complete!"
echo ""

echo "🎉 All done! Next steps:"
echo ""
echo "1. Create a Personal Access Token (PAT) on GitHub:"
echo "   https://github.com/settings/tokens/new"
echo "   - Select scopes: repo, workflow"
echo ""
echo "2. Add the token as a secret to your lyocell repository:"
echo "   https://github.com/$GITHUB_USER/lyocell/settings/secrets/actions"
echo "   - Name: PAT_TOKEN"
echo "   - Value: <paste your token>"
echo ""
echo "3. Update version in build.gradle and CHANGELOG.md"
echo ""
echo "4. Create and push a version tag:"
echo "   git tag v0.2.22"
echo "   git push origin v0.2.22"
echo ""
echo "5. The release workflow will automatically:"
echo "   - Build native binaries for all platforms"
echo "   - Create a GitHub release"
echo "   - Update both Homebrew tap and Scoop bucket"
echo ""
echo "For detailed instructions, see RELEASE_GUIDE.md"

