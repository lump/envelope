Envelope

This is an envelope-based budget.  The idea is that you pre-allocate
funds into the budget, and you should only spend money in a category
if there are available funds to cover it.  All of the balances in
accounts should add up and reconcile to the current balance of the
bank account you're budgeting.

## How it is put together

Three tiers, which run in three different places:

    +---------------------------+   https    +------------------------+   jdbc   +-----------------+
    |  Swing client             | ---------> |  Tomcat (the server)   | -------> |  MariaDB        |
    |  a runnable jar, on any   |            |  a prepared image, in  |          |  on infra the   |
    |  machine that has Java    |            |  Docker Swarm          |          |  swarm can reach|
    +---------------------------+            +------------------------+          +-----------------+

The client is a desktop application.  It runs wherever there is a Java 25 or
later runtime -- Windows, macOS, Linux -- and speaks to the server over HTTP
or HTTPS.  It is not installed; it is downloaded from the server and run.

The server is a Tomcat webapp, built into a self-contained image and deployed
as a service in Docker Swarm behind the front stack's proxy.  It is the only
thing that talks to the database.  The image contains everything it needs,
including the client jar it hands out.

The database is MariaDB, running natively on infrastructure the swarm can
reach.  It is not part of the stack: the stack is told where it is and how to
authenticate, and the infrastructure provisions the schema and credentials.

## Getting and running the client

Open the server's front page in a browser:

    https://<server>/envelope/

It offers envelope-client.jar and shows the exact server URL to give it.
With Java 25 or later installed:

    java -jar envelope-client.jar

or double-click the jar.  On first run it asks for the server; paste the URL
from the front page, then log in.  Nothing else needs installing: the client
carries the private certificate authority the servers are signed by, so it
trusts them on any machine.

## Deploying the server

The image is built from this repository and pushed to the registry:

    ./build.sh            # builds, tags registry.lump/lump/envelope:<branch>-<version>, offers to push
    ./build.sh --no-push  # just build

The build runs Maven inside the image, so the machine building it needs only
docker.  Bump .Dockerfile.version when the Dockerfile changes.

docker/swarm-stack.yml is the service definition for the config project's
stack.  It expects the database to be supplied from outside, through these
variables, which the config project's environment files provide (the
password from their encrypted secret block):

    ENVELOPE_DB_HOST    ENVELOPE_DB_PORT    ENVELOPE_DB_NAME
    ENVELOPE_DB_USER    ENVELOPE_DB_PASSWORD
    ENVELOPE_ADMIN_USER ENVELOPE_ADMIN_PASSWORD
    ENVELOPE_HEAP

The service's health check is /envelope/info/ready, which opens a database
session and runs a query -- so a container that cannot reach its database is
unhealthy, and says why, rather than reported healthy with every login
failing.  /envelope/info/ping only says Tomcat is up.

## The database

The schema is sql/bootstrap-mysql.sql.  On the swarm the config project
bootstraps an empty database from it and grants its own credentials; the
grant at the end of that file is the development default and is not what the
swarm uses.  Changes to an existing database go in sql/migrations/, numbered,
and are applied by hand.

sql/migrate.pl imports a budget from the legacy "budgets" database; see the
header of that file for how to point it at a source and a destination.

## Developing

For working on the code there is a single-machine stack: Tomcat in a
container reading a war bind-mounted from target/, so a change is a rebuild
and a container restart, against a MariaDB on the same host.

    mvn package                          # target/envelope.war and target/envelope-client.jar
    cd docker && docker compose up -d    # Tomcat on http://localhost:7041/envelope/
    java -jar target/envelope-client.jar

Load sql/bootstrap-mysql.sql into the local MariaDB first.  It seeds two
accounts -- change them after first login:

    admin / envelope
    guest / guest

CLAUDE.md describes the architecture, the protocol, and the traps in detail.
