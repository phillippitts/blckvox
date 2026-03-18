#!/usr/bin/env bash
#
# Configures git to use the project's hooks/ directory for git hooks.
# This is a one-time setup per clone.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$REPO_ROOT"
git config core.hooksPath hooks/
echo "Git hooks installed. Using hooks/ directory for git hooks."
