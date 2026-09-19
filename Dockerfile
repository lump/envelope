# syntax=docker/dockerfile:1
#
# Envelope server, self-contained: the war is built inside the image from the
# source tree, so nothing on the host but docker is needed -- there is no system
# maven here, and the one IntelliJ carries is a detail nobody else should need.
#
# Built and pushed by ./build.sh (a copy of ~/lump/web/build.sh; the image name
# and version come from .image.name and the three version files).  The image is
# tagged <branch>-<major>.<minor>.<patch>, assembled by build.sh from
# .Major.version, .Minor.version and .Patch.version -- bump whichever one the
# change warrants, and bump the patch at least when this file changes.
#
# The database is not in here.  The runtime expects DAO_HIBERNATE_CONNECTION_*
# from the environment -- see docker/compose.yml for the names, and
# docker/swarm-stack.yml for how the config project's stack supplies them.

# ---- build ------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
COPY src src
COPY web web
# Read by the build into revision.properties, so the running application can
# report its own version.  There is no .git in here -- .dockerignore keeps the
# context to what the build needs -- so these three files are the only thing the
# image knows about which build it is.
COPY .Major.version .Minor.version .Patch.version ./
# the local repository is a BuildKit cache, so only the first build downloads
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests

# ---- run --------------------------------------------------------------------
FROM tomcat:11.0-jdk25-temurin

# This runs behind the front stack's HAProxy.  Without this valve Tomcat sees the
# proxy's address and port, and the front page would tell visitors to point the
# client at swarm-internal names.  With it, X-Forwarded-For / -Proto / -Host are
# believed, so the page names the host the visitor actually reached.  The port
# comes from X-Forwarded-Port when the front end sends it, and otherwise is
# assumed to be the scheme's own -- which is right on 443, wrong on anything else.
RUN sed -i 's|</Host>|  <Valve className="org.apache.catalina.valves.RemoteIpValve" remoteIpHeader="x-forwarded-for" protocolHeader="x-forwarded-proto" hostHeader="x-forwarded-host" portHeader="x-forwarded-port" />\n      </Host>|' conf/server.xml

# log4j1.compatibility lets log4j-1.2-api read the war's own log4j.properties
# instead of wanting a log4j2 config; headless because there is no display and
# the shared code drags in Swing classes.  -Xmx is deployment's to set.
ENV CATALINA_OPTS="-Djava.awt.headless=true -Dlog4j1.compatibility=true -Dlog4j.configuration=log4j.properties"

COPY --from=build /src/target/envelope.war webapps/envelope.war

# Readiness, not liveness: /info/ready opens a database session and runs a
# query, so a container that cannot reach its database is unhealthy rather than
# "healthy" with every login failing.  The temurin image has bash but no
# curl/wget/nc, so probe via /dev/tcp.
HEALTHCHECK --interval=15s --timeout=10s --start-period=60s --retries=8 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080; printf "GET /envelope/info/ready HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3; grep -q "^ready:" <&3'

EXPOSE 8080
