#!/usr/bin/env bash
# Local (non-Docker) toolchain for this workstation. Source before any build command:
#   source scripts/env.sh
# Docker Compose remains the documented default; these paths exist because Docker
# is not installed on the dev machine used to build Bookero.

export JAVA_HOME="/c/Users/adjei/tools/jdk-21.0.5+11"
export MAVEN_HOME="/c/Users/adjei/tools/apache-maven-3.9.9"
export PGBIN="/c/Program Files/PostgreSQL/18/bin"
export PYTHON_BIN="/c/Users/adjei/AppData/Local/Programs/Python/Python313/python.exe"
export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:/c/Program Files/nodejs:$PGBIN:$PATH"

# Local Postgres cluster lives in .localdb (gitignored) on 5433 so it cannot
# collide with the workstation's own PostgreSQL service on 5432.
export PGHOST=127.0.0.1
export PGPORT=5433
export PGUSER=bookero
export PGDATABASE=bookero
export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:5433/bookero"
export SPRING_DATASOURCE_USERNAME=bookero
export SPRING_DATASOURCE_PASSWORD=bookero
export DATABASE_URL="postgresql://bookero@127.0.0.1:5433/bookero"
export ANALYTICS_BASE_URL="http://localhost:8001"
export JWT_SECRET="bookero-dev-secret-change-me-32chars"

# 8080 (pgAdmin) and 3000 are already taken on this workstation, so local runs
# shift the API and web ports. Docker Compose still exposes the canonical ones.
export SERVER_PORT=8090
export API_PORT=8090
export ANALYTICS_PORT=8001
export WEB_PORT=3100
export NEXT_PUBLIC_API_URL="http://localhost:8090"
export NEXT_PUBLIC_ANALYTICS_URL="http://localhost:8001"
export CORS_ALLOWED_ORIGINS="http://localhost:3000,http://localhost:3100"
