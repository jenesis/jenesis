Jenesis
======= 

## Distributed Unit Testing for JUnit

#### Meanings
   - Running multiple unit tests in parallel on multiple hosts / JVMs / OS'
   - Running the same unit test in parallel on multiple hosts / JVMs / OS'
   - Running a multi-node unit test on multiple hosts / JVMs
   - Benchmarking the same unit test in parallel on multiple hosts / JVMs / OS'

#### Use cases
   - Speed up of huge test suites by parallelizing execution of the tests
   - Stress- and / or Load-testing
   - Testing distributed / scalable applications
   - Testing cluster solutions

#### Goals
This framework will offer a basic framework to make distributed unit tests possible. It'll provides
a common lifecycle for those tests and an API to hook in your own lifecycle extensions that need to
take place to set up, execute and tear down tests.
It will also provide a transparent communication layer where you can integrate special serialization
strategies and to communicate between the test runner and the remote test agents.

#### Non Goals
At the moment the goal is not to build a solution to speed up tests by running them in parallel on
multiple test slaves. There are interesting / working solutions for that already (like Test Load-
Balancer TLB - http://test-load-balancer.github.io).

#### Motivation
A lot of middleware / application frameworks provide support for running distributed unit tests
but there are nearly no frameworks for a general purpose or they seem to have stopped development
a long time ago.
To provide developers and vendors to build reliable but distributed unit tests and applications.

#### Description
This is a fast written, mindmap-alike list of thoughts / ideas. At the moment this is not meant to
be complete or all those things have to be implemented.
   - Support for fork-strategies (local, remote, cloud, custom)
   - Support to shutdown processes gracefully and sigkill them ungracefully
   - Transparent communication between test runner and workers
   - Support for hooking into different phases and extend the base framework
   - Executing remote actions and waiting for events to happen
   - Support for timeouts
   - Support for JVM settings and changing these settings from JVM to JVM
   - Support for repeatable runs of the same test
   - Support for multiple operating systems (for native operations)
   - Automatic building of classpath
   - Support for distributing required files to worker nodes
   - Resolving artifacts from Maven repositories
   - Packaging API to wrap classes / dependencies in custom packages
   - Some kind of remote classloading


