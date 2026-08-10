# Docker support files

Reserved for Dockerfiles and helper scripts that are not owned by a single service —
for example the ClamAV sidecar (Milestone 4/6) and any production-only compose overlay.

Each deployable service keeps its own `Dockerfile` next to its `pom.xml`, and the
development stack is defined in the repository-root `docker-compose.yml`.
