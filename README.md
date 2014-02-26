Jenesis
======= 

## Distributed Unit Testing for JUnit

#### Possible Meanings to You
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
The current approach is to integrate it with JUnit but possibly TestNG or other frameworks are added
in the future.

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
   - Piping console / logger output to file / test runner console / null

#### API ideas
This API is not meant to be a fully thought-through thing it is more like a basic idea on how such
stuff could look like:
```java
@RemoteAgent(agentClass = LoggingRemoteAgent.class)
public class DistributedExmapleTestCase {
   
   @Test(timeout = 60000)
   @RemoteTest(initialForks = 5, identification = "Node-${forkId}")
   public void testDistributed(RemoteContext remoteContext) {
      RemoteProcess remoteProcess = remoteContext.startJVM(); // Using defaults from this JVM
      remoteProcess.waitForStartup(10, TimeUnit.SECONDS);

      remoteContext.sendAction(new SomeRemoteAction(), 10); // To all processes

      remoteProcess.sigkill(); // Kill hard

      // ...
   }
}

public class SomeRemoteAction extends SynchronousRemoteAction<Void> {
   
   @Override
   public Void evaluate(CommandContext commandContext) throws Exception {
      Object[] arguments = commandContext.getArguments();
      int seconds = (Integer) arguments[0];

      System.out.print("Waiting ");
      for (int i = 0; i < seconds; i++) {
         System.out.print(".");
         TimeUnit.SECONDS.sleep(1);
      }
      System.out.println(" done.");
      return null;
   }
}

public class LoggingRemoteAgent extends RemoteAgent {
   
   private final Logger logger = LoggerFactory.getLogger(LoggingRemoteAgent.class);

   @Override
   public void preparationPhase(ExecutionContext executionContext) {
      logger.info("Preparation phase started: " + executionContext.getIdentification());
      super.preparationPhase(executionContext);
   }

   @Override
   public List<Dependency> resolveDependencyPhase(ExecutionContext executionContext) {
      logger.info("Resolving dependencies: " + executionContext.getIdentification());
      return super.resolveDependencyPhase(executionContext);
   }

   // ... further phases
}
```