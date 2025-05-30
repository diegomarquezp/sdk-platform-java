#!/bin/bash
set -exo pipefail
# This script should be run at the root of the repository.
# This script is used to, when a pull request changes the generation
# configuration (generation_config.yaml by default) or Dockerfile:
# 1. Find whether the last commit in this pull request contains changes to
# the generation configuration and Dockerfile and exit early if it doesn't have
# such a change since the generation result would be the same.
# 2. Compare generation configurations in the current branch (with which the
# pull request associated) and target branch (into which the pull request is
# merged);
# 3. Generate changed libraries using library_generation image;
# 4. Commit the changes to the pull request, if any.
# 5. Edit the PR body with generated pull request description, if applicable.

# The following commands need to be installed before running the script:
# 1. git
# 2. gh
# 3. docker
# 4. mvn

# The parameters of this script is:
# 1. target_branch, the branch into which the pull request is merged.
# 2. current_branch, the branch with which the pull request is associated.
# 3. [optional] image_tag, the tag of gcr.io/cloud-devrel-public-resources/java-library-generation.
# 4. [optional] generation_config, the path to the generation configuration,
# the default value is generation_config.yaml in the repository root.
# 5. [optional] showcase_mode, true if we wish to download the showcase api
# definitions, which are necessary for generating the showcase library.
while [[ $# -gt 0 ]]; do
key="$1"
case "${key}" in
  --target_branch)
    target_branch="$2"
    shift
    ;;
  --current_branch)
    current_branch="$2"
    shift
    ;;
  --image_tag)
    image_tag="$2"
    shift
    ;;
  --generation_config)
    generation_config="$2"
    shift
    ;;
  --showcase_mode)
    showcase_mode="$2"
    shift
    ;;
  *)
    echo "Invalid option: [$1]"
    exit 1
    ;;
esac
shift
done

if [ -z "${target_branch}" ]; then
  echo "missing required argument --target_branch"
  exit 1
fi

if [ -z "${current_branch}" ]; then
  echo "missing required argument --current_branch"
  exit 1
fi

if [ -z "${download_showcase}" ]; then
  download_showcase="false"
fi

if [ -z "${generation_config}" ]; then
  generation_config=generation_config.yaml
  echo "Use default generation config: ${generation_config}"
fi

if [ -z "${image_tag}" ]; then
  image_tag=$(grep "gapic_generator_version" "${generation_config}" | cut -d ':' -f 2 | xargs)
fi

workspace_name="/workspace"
baseline_generation_config="baseline_generation_config.yaml"
message="chore: generate libraries at $(date)"

git checkout "${target_branch}"
git checkout "${current_branch}"

# copy generation configuration from target branch to current branch.
git show "${target_branch}":"${generation_config}" > "${baseline_generation_config}"

# download api definitions from googleapis repository
googleapis_commitish=$(grep googleapis_commitish "${generation_config}" | cut -d ":" -f 2 | xargs)
api_def_dir=$(mktemp -d)
git clone https://github.com/googleapis/googleapis.git "${api_def_dir}"
pushd "${api_def_dir}"
git checkout "${googleapis_commitish}"
popd

# we also setup showcase
if [[ "${showcase_mode}" == "true" ]]; then
  source java-showcase/scripts/showcase_utilities.sh
  append_showcase_to_api_defs "${api_def_dir}"
fi

# get changed library list.
changed_libraries_file="$(mktemp)"
python hermetic_build/common/cli/get_changed_libraries.py create \
  --baseline-generation-config-path="${baseline_generation_config}" \
  --current-generation-config-path="${generation_config}" | tee "${changed_libraries_file}"
changed_libraries="$(cat "${changed_libraries_file}")"
echo "Changed libraries are: ${changed_libraries:-"No changed library"}."

# run hermetic code generation docker image.
docker run \
  --rm \
  -u "$(id -u):$(id -g)" \
  -v "$(pwd):${workspace_name}" \
  -v "${api_def_dir}:${workspace_name}/googleapis" \
  -e GENERATOR_VERSION="${image_tag}" \
  gcr.io/cloud-devrel-public-resources/java-library-generation:"${image_tag}" \
  --generation-config-path="${workspace_name}/${generation_config}" \
  --library-names="${changed_libraries}" \
  --api-definitions-path="${workspace_name}/googleapis"

python hermetic_build/release_note_generation/cli/generate_release_note.py generate \
  --baseline-generation-config-path="${baseline_generation_config}" \
  --current-generation-config-path="${generation_config}"

# remove api definitions after generation
rm -rf "${api_def_dir}"

# commit the change to the pull request.
rm -rdf output googleapis "${baseline_generation_config}"
git add --all -- ':!pr_description.txt' ':!hermetic_library_generation.sh' ':!hermetic_build'
changed_files=$(git diff --cached --name-only)
if [[ "${changed_files}" != "" ]]; then
    echo "Commit changes..."
    git commit -m "${message}"
    git push
else
    echo "There is no generated code change, skip commit."
fi

# set pr body if pr_description.txt is generated.
if [[ -f "pr_description.txt" ]]; then
  pr_num=$(gh pr list -s open -H "${current_branch}" -q . --json number | jq ".[] | .number")
  gh pr edit "${pr_num}" --body "$(cat pr_description.txt)"
fi
