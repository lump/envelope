#!/usr/bin/env bash
set -e
set -o pipefail

_no_cache=0

_name="lump/unknown"
_image_name_file=".image.name"
if [[ -s "${_image_name_file}" ]]; then
    _name=$(cat "${_image_name_file}")
fi

# The version is major.minor.patch, each held in its own file at the top of the
# tree, so any one of them can be bumped depending on what the change was.  The
# same three files are read by the build into the artifact, which is how the
# running application knows its own version.
#
# Projects that have not split their version keep a single .Dockerfile.version,
# and are handled by the fallback below, so this stays a drop-in for all of them.
_version_part() {
    if [[ -s "${1}" ]]; then
        tr -d '[:space:]' < "${1}"
    else
        printf '%s' "${2}"
    fi
}

if [[ -s ".Minor.version" ]]; then
    _version="$(_version_part .Major.version 0).$(_version_part .Minor.version 0).$(_version_part .Patch.version 0)"
elif [[ -s ".Dockerfile.version" ]]; then
    _version=$(cat ".Dockerfile.version")
else
    _version=0
fi

# Parse arguments
while [[ "${1:-}" == -* ]]; do
    case "${1}" in
        --push|-p)
            AUTOPUSH=1
            shift
            ;;
        --no-push|--nopush|-P)
            AUTOPUSH=2
            shift
            ;;
        --no-cache|--nocache|-C)
            _no_cache=1
            shift
            ;;
        *)
            echo "Unknown option: ${1}" >&2
            exit 1
            ;;
    esac
done

# Determine branch name — use env var in CI, fall back to git
_branch="${GITHUB_REF_NAME:-$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "local")}"
_tag="${_name}:${_branch}-${_version}"
_registry="registry.lump"
_docker_build=(docker build)
if [[ "${_no_cache}" -eq 1 ]]; then
    _docker_build+=(--no-cache)
fi

# Build
_docker_build+=(-t "${_tag}" .)
DOCKER_BUILDKIT=1 "${_docker_build[@]}"

# Tag with latest variants
docker image tag "${_tag}" "${_name}:latest"
docker image tag "${_tag}" "${_registry}/${_tag}"
docker image tag "${_name}:latest" "${_registry}/${_name}:latest"

# Push
if [[ "${AUTOPUSH:-0}" -eq 1 ]]; then
    docker image push "${_registry}/${_tag}"
    docker image push "${_registry}/${_name}:latest"
elif [[ "${AUTOPUSH:-0}" -eq 2 ]]; then
    echo "${_tag}"
    echo "${_name}:latest"
    echo skipping push for "${_registry}/${_tag}" >&2
else
    read -p "Push ${_tag} to ${_registry}? (Y)? " -n 1 -r
    echo
    if [[ "${REPLY}" =~ ^([Yy[:space:]])?$ ]]; then
        docker image push "${_registry}/${_tag}"
        docker image push "${_registry}/${_name}:latest"
    else
        echo "skipping push:" >&2
        echo "  docker image push \"${_registry}/${_tag}\"" >&2
        echo "  docker image push \"${_registry}/${_name}:latest\"" >&2
    fi
fi
