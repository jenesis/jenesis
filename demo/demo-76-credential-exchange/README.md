Credential exchange demo
========================

A CI job that resolves from a private repository, reads a cache server or publishes
a release needs a credential - and a credential that is stored as a secret of the CI
system is one that can leak, must be rotated and outlives the job. Most CI platforms
issue every job a signed *identity token* instead, which says which repository,
branch and workflow the job runs for. A server that trusts the platform exchanges
that token for a credential of its own that lives for minutes.

Jenesis makes that exchange itself. One setting names where:

    -Djenesis.cache.exchange=/api/token

At the first request a build sends the cache server, it posts the job's identity
token to that path on the server's own host, as OAuth 2.0 Token Exchange
([RFC 8693](https://www.rfc-editor.org/rfc/rfc8693)) lays the request out, and sends
the token the server issues as `Authorization: Bearer <token>` from then on. Nothing
is stored anywhere, and nothing else is installed.

A credential for each server
----------------------------

A build has five credentials, each with a setting of its own, so that the Maven
repository, the cache server and the repository a release goes to may be three
servers with three ways to sign in:

| credential of                          | stored token            | exchanged token            |
| -------------------------------------- | ----------------------- | -------------------------- |
| the Maven remotes, `jenesis.maven.uri` | `jenesis.maven.token`   | `jenesis.maven.exchange`   |
| the module remote, `jenesis.module.uri`| `jenesis.module.token`  | `jenesis.module.exchange`  |
| the cache server, `jenesis.cache.uri`  | `jenesis.cache.token`   | `jenesis.cache.exchange`   |
| a release of modules, `jenesis.release.uri` | `jenesis.release.token` | `jenesis.release.exchange` |
| a release to Maven, `jenesis.deploy.uri`    | `jenesis.deploy.token`  | `jenesis.deploy.exchange`  |

A credential has one source: naming a token and an exchange for the same one is
refused, naming both.

On GitHub Actions
-----------------

GitHub issues a job its identity token on request, once the workflow allows it with
`id-token: write`. Jenesis asks for it, so the workflow holds no secret at all:

    jobs:
      build:
        runs-on: ubuntu-latest
        permissions:
          contents: read
          id-token: write
        steps:
          - uses: actions/checkout@v4
          - uses: actions/setup-java@v4
            with:
              distribution: temurin
              java-version: 25
          - run: >
              java
              -Djenesis.maven.uri=https://repository.example.com/repository/acme/maven/
              -Djenesis.maven.exchange=/api/token
              -Djenesis.cache.uri=https://repository.example.com/build/acme/
              -Djenesis.cache.exchange=/api/token
              build/jenesis/Make.java

The token is requested for an *audience*, which the server checks: by default the
scheme, host and port of the exchange, `https://repository.example.com` here.
`-Djenesis.maven.audience=<audience>` names another where the server's trust expects
one.

On any other platform
---------------------

Elsewhere the platform hands the job its token in an environment variable or a file,
and a setting says which. The setting names where the token is read from and never
the token, so that no token is a value a log or the `configuration` selector prints:

    -Djenesis.exchange.identity=env CI_JOB_JWT          # a variable
    -Djenesis.exchange.identity=file /var/run/secrets/tokens/jenesis   # a file

`jenesis.exchange.identity` is read for every credential. Where two servers expect
tokens of two audiences, `jenesis.maven.identity`, `jenesis.cache.identity` and so on
name one each. Without any of them the variable `JENESIS_IDENTITY_TOKEN` is read, so
that on GitLab the job only declares it:

    build:
      id_tokens:
        JENESIS_IDENTITY_TOKEN:
          aud: https://repository.example.com
      script:
        - java -Djenesis.maven.uri=https://repository.example.com/repository/acme/maven/
               -Djenesis.maven.exchange=/api/token build/jenesis/Make.java

A file is read anew for every exchange, so a token that Kubernetes rotates in a
projected volume is followed.

What keeps the tokens where they belong
---------------------------------------

- The exchange is on the server the credential is for. The setting is a path on that
  host; a URL is accepted where it names the same scheme, host and port, and any
  other is refused before anything is sent. The identity token goes to that address
  and nowhere else, and the issued token to that host alone.
- The connection is `https`. Plaintext needs `jenesis.repository.insecure`, or
  `jenesis.cache.insecure` for a cache server, as a stored token does.
- Only the command line and your own `~/.jenesis/jenesis.properties` name an
  exchange, an identity or a helper. A project's `jenesis.properties` naming one is
  refused, and a repository that a project's file chose is never exchanged with.
- A token is asked for at the first request that needs it, once for a build, and
  again when the `expires_in` of the answer has run out. A build that needs no
  server asks for none, and neither does one under `jenesis.repository.offline`.
- A build in a container, under `jenesis.project.docker`, is handed none of these
  settings.

A resolution or a release that cannot exchange its token fails, naming the server
and the `error` it answered. A cache server that cannot be signed in to is a cache
that misses: the build runs every step itself and passes.

Jenesis Repository
------------------

[Jenesis Repository](https://jenesis.build/repository/) serves the exchange at
`/api/token` and admits a token by a trust that names the issuer, the audience and
the subject it accepts. For GitHub Actions the issuer is
`https://token.actions.githubusercontent.com`, the audience the address of the
server and the subject, for instance, `repo:acme/shop:ref:refs/heads/main`. The one
setting above is the whole configuration; the request names the repository or the
cache as its `resource`, from which the server derives the tenant.

JFrog Artifactory
-----------------

Artifactory exchanges at `/access/api/v1/oidc/token`, reads the request as JSON
rather than as a form, and wants the name of the OIDC integration it is configured
with as `provider_name`. Three settings describe that:

    -Djenesis.maven.uri=https://acme.jfrog.io/artifactory/libs-release/
    -Djenesis.maven.exchange=/access/api/v1/oidc/token
    -Djenesis.maven.encoding=json
    -Djenesis.maven.parameters=provider_name=github-oidc,resource=

`jenesis.maven.parameters` adds members to the request, and `resource=` leaves out
the one Artifactory does not define. Where the integration names an audience, set it
with `jenesis.maven.audience`. The same three settings under `jenesis.deploy.*` sign
a release in.

Any other server
----------------

Where a server signs a job in by an API of its own, a *helper* stands in: a program
you name, which Jenesis runs with the address the credential is for as its last
argument, and whose first printed line is the token.

    -Djenesis.maven.helper=/usr/local/bin/issue-token --realm build

    #!/bin/sh
    # issue-token --realm build https://repository.example.com/maven/
    curl -sf -X POST -H "X-Job-Token: $CI_JOB_JWT" "https://sso.example.com/issue?for=$3"

The helper runs at the first request that needs the credential, once for a build,
with the environment of the build. What it prints is sent as
`Authorization: Bearer <token>` and never printed; printing nothing sends no
credential, and any exit code but 0 fails what asked for it.

Run it
------

The demo needs no CI system and no server: `build/Demo.java` starts a stand-in on
this machine that keeps a cache in memory, answers only a request carrying a token it
issued, and issues one at `/api/token` for the identity token of a file. It then
builds this project three times from scratch, twice signing in by the exchange and
once by the helper of `build/Helper.java`:

    java build/Demo.java

    build 1 -> 0: 1 exchange, 0 steps loaded, 21 steps stored
    build 2 -> 0: 1 exchange, 21 steps loaded, 0 steps stored
    build 3 -> 0: 0 exchanges, 21 steps loaded, 0 steps stored, asked with the token of the helper
    requests the server refused for want of a token: 0
    requests besides the exchange that carried the identity token: 0

Each build exchanged once, however many requests it sent. The first stored what it
built, the second was served all of it, and the third was served the same with a
token no exchange issued.

Layout
------

    demo/demo-76-credential-exchange
    |-- build
    |   |-- Demo.java        the stand-in server and the three builds
    |   |-- Helper.java      a credential helper
    |   `-- jenesis          symlink to ../../../sources/build/jenesis
    `-- sources
        |-- module-info.java     module demo.exchange, no dependencies
        `-- sample/Sample.java
