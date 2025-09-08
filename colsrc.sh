#!/usr/bin/env bash
# Universal source collector script: colsrc.sh
# Recursively scans a project, respects .gitignore directories, and concatenates source files into a single output file.
# Author: Junie (JetBrains)

set -euo pipefail
shopt -s extglob
IFS=$'\n\t'

print_help() {
  cat <<'EOF'
Usage: ./colsrc.sh [OPTIONS]

Collects source code files into a single output file named source-code-[YYYYMMDD-HHMMSS].txt.

Options (named only):
  --root DIR               Root directory to scan (default: current directory).
  --patterns PAT ...       One or more filename patterns to include. Examples:
                           .py  .java  "*.kt"  "*Test.java"  Dockerfile  Makefile
                           Note: plain extensions like .py are normalized to *.py
  --exclude-dirs DIR ...   One or more relative directory names to exclude (in addition to .gitignore and defaults).
  --output-dir DIR         Directory to place the output file (default: --root).
  -h, --help               Show this help message and exit.

Behavior:
  - Recursively scans the root directory.
  - Reads every .gitignore under the root and excludes directories listed there.
    Only directory entries are considered; wildcard file patterns are ignored.
  - Also excludes a built-in set of common build/cache/vendor directories for
    Java/Kotlin/Python/JS/TS/Go, etc.
  - Produces UTF-8 text output with file separators.

Examples:
  ./colsrc.sh --patterns .py --patterns Dockerfile
  ./colsrc.sh --root hunty-interview-ui --patterns .ts .tsx --exclude-dirs dist build
  ./colsrc.sh --patterns "*.kt" "*Test.java" --output-dir /tmp
EOF
}

# Default values
ROOT_DIR="$(pwd)"
OUTPUT_DIR=""

# Arrays
PATTERNS=()
EXCLUDE_DIRS_USER=()

# Built-in default directories to exclude
# Keep names relative (we will construct find -path "*/NAME/*" and -name matches)
EXCLUDE_DIRS_DEFAULT=(
  .git
  .gitlab
  .hg
  .svn
  .idea
  .vscode
  .gradle
  .mvn
  node_modules
  bower_components
  .yarn
  .pnpm-store
  .next
  .nuxt
  build
  out
  bin
  dist
  target
  classes
  vendor
  coverage
  reports
  .temp
  .tmp
  .cache
  .mypy_cache
  .pytest_cache
  .tox
  __pycache__
  .venv
  venv
  .env
  .envs
  pkg/mod
)

# Argument parsing (named only)
if [[ $# -eq 0 ]]; then
  print_help
  exit 0
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    --root)
      [[ $# -lt 2 ]] && { echo "Error: --root requires a value" >&2; exit 2; }
      ROOT_DIR="$2"; shift 2 ;;
    --patterns)
      # Allow multiple --patterns occurrences, each can be a single token.
      [[ $# -lt 2 ]] && { echo "Error: --patterns requires a value" >&2; exit 2; }
      PATTERNS+=("$2"); shift 2 ;;
    --exclude-dirs)
      [[ $# -lt 2 ]] && { echo "Error: --exclude-dirs requires a value" >&2; exit 2; }
      EXCLUDE_DIRS_USER+=("$2"); shift 2 ;;
    --output-dir)
      [[ $# -lt 2 ]] && { echo "Error: --output-dir requires a value" >&2; exit 2; }
      OUTPUT_DIR="$2"; shift 2 ;;
    -h|--help)
      print_help; exit 0 ;;
    --*)
      echo "Error: Unknown option: $1" >&2; exit 2 ;;
    *)
      echo "Error: Positional arguments are not supported. Use named options. Got: $1" >&2
      exit 2 ;;
  esac
done

# Resolve directories
ROOT_DIR="$(cd "$ROOT_DIR" 2>/dev/null && pwd || true)"
if [[ -z "$ROOT_DIR" || ! -d "$ROOT_DIR" ]]; then
  echo "Error: --root directory not found" >&2
  exit 2
fi

if [[ -z "$OUTPUT_DIR" ]]; then
  OUTPUT_DIR="$ROOT_DIR"
else
  OUTPUT_DIR="$(mkdir -p "$OUTPUT_DIR" && cd "$OUTPUT_DIR" && pwd)"
fi

# Normalize patterns: convert plain extensions like .py to *.py; leave others as-is
NORMALIZED_PATTERNS=()
for p in "${PATTERNS[@]:-}"; do
  [[ -z "${p:-}" ]] && continue
  if [[ "$p" == .* && "$p" != *"/"* && "$p" != *"*"* && "$p" != *"?"* ]]; then
    NORMALIZED_PATTERNS+=("*${p}")
  else
    NORMALIZED_PATTERNS+=("$p")
  fi
done

if [[ ${#NORMALIZED_PATTERNS[@]} -eq 0 ]]; then
  echo "Error: At least one --patterns must be provided" >&2
  exit 2
fi

# Gather .gitignore directory entries under ROOT_DIR
GITIGNORE_FILES=()
while IFS= read -r _gi; do
  GITIGNORE_FILES+=("$_gi")
done < <(find "$ROOT_DIR" -type f -name .gitignore -print)
GITIGNORE_DIRS=()
for gi in "${GITIGNORE_FILES[@]:-}"; do
  while IFS= read -r line || [[ -n "$line" ]]; do
    # Trim whitespace
    line="${line%%$'\r'}"
    line="${line##+([[:space:]])}"
    line="${line%%+([[:space:]])}"
    # Skip comments and blanks
    [[ -z "$line" || "$line" == \#* ]] && continue
    # Skip negations and wildcard-heavy patterns
    [[ "$line" == !* ]] && continue
    # Only accept entries WITHOUT any globbing characters; treat as directory/file path literal
    if [[ "$line" != *"*"* && "$line" != *"?"* && "$line" != *"["* && "$line" != *"]"* ]]; then
      # Remove trailing slash for directories
      entry="${line%/}"
      [[ -z "$entry" ]] && continue
      # If entry is an absolute path in gitignore (rare), make it relative by stripping leading slashes
      entry="${entry#/}"
      # Use only the first path segment as a directory name to prune broadly
      topseg="${entry%%/*}"
      [[ -n "$topseg" ]] && GITIGNORE_DIRS+=("$topseg")
    fi
  done < "$gi"
  # Also consider sibling .git/info/exclude? not required here
done

# Deduplicate exclusion directory names and filter out empties
ALL_EXCLUDES=("${EXCLUDE_DIRS_DEFAULT[@]:-}" "${GITIGNORE_DIRS[@]:-}" "${EXCLUDE_DIRS_USER[@]:-}")
UNIQ_EXCLUDES=()
seen=""
for d in "${ALL_EXCLUDES[@]:-}"; do
  [[ -z "${d:-}" ]] && continue
  # normalize to simple name (strip leading ./)
  dn="${d#./}"
  # Never exclude canonical source directory names
  if [[ "$dn" == "src" ]]; then
    continue
  fi
  # Keep as-is; we will construct path-based pruning.
  if [[ ",$seen," != *",$dn,"* ]]; then
    UNIQ_EXCLUDES+=("$dn")
    seen+="$dn,"
  fi
done

# Build find prune expression in portable way
PRUNE_EXPR=()
firstp=1
for d in "${UNIQ_EXCLUDES[@]}"; do
  [[ "$d" == \#* || -z "$d" ]] && continue
  if [[ $firstp -eq 1 ]]; then
    PRUNE_EXPR+=( -path "*/$d" -o -path "*/$d/*" -o -name "$d" )
    firstp=0
  else
    PRUNE_EXPR+=( -o -path "*/$d" -o -path "*/$d/*" -o -name "$d" )
  fi
done

# Ensure prune expr is not empty
if [[ $firstp -eq 1 ]]; then
  PRUNE_EXPR=( -false )
fi

# Build include expression for file names
INCLUDE_EXPR=()
first=1
for p in "${NORMALIZED_PATTERNS[@]}"; do
  if [[ $first -eq 1 ]]; then
    INCLUDE_EXPR+=( -name "$p" )
    first=0
  else
    INCLUDE_EXPR+=( -o -name "$p" )
  fi
  # Also support exact basename if pattern looks like unqualified name without wildcards
  if [[ "$p" != *"*"* && "$p" != *"?"* && "$p" != *"["* ]]; then
    INCLUDE_EXPR+=( -o -iname "$p" )
  fi
done

# Compose output file path
timestamp="$(date +%Y%m%d-%H%M%S)"
OUTPUT_FILE="$OUTPUT_DIR/source-code-$timestamp.txt"

echo "[colsrc] Root       : $ROOT_DIR"
echo "[colsrc] Output file: $OUTPUT_FILE"
echo "[colsrc] Patterns   : ${NORMALIZED_PATTERNS[*]}"

# Build the find command pieces safely
# Initialize/clear the output file without adding any header metadata
: > "$OUTPUT_FILE"

FILES=()
while IFS= read -r f; do
  FILES+=("$f")
done < <( \
  find "$ROOT_DIR" \
    \( "${PRUNE_EXPR[@]}" \) -prune -o \
    \( -type f \( "${INCLUDE_EXPR[@]}" \) \) -print 2>/dev/null \
)

# Sort files for deterministic output (note: uses newline as separator)
FILES_SORTED=$(printf '%s\n' "${FILES[@]:-}" | LC_ALL=C sort)

count=0
while IFS= read -r f; do
  [[ -z "$f" ]] && continue
  # Append with separators
  printf '\n\n===== FILE: %s =====\n' "${f#$ROOT_DIR/}" >> "$OUTPUT_FILE"
  # Print with cat to preserve content, assuming UTF-8 where possible
  cat "$f" >> "$OUTPUT_FILE" || true
  count=$((count+1))
done <<< "$FILES_SORTED"

echo "[colsrc] Files collected: $count"
echo "[colsrc] Done. Output: $OUTPUT_FILE"
