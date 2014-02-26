Jenesis
======= 

## Distributed Unit Testing for JUnit

#### Possible Meanings to You
   - Running multiple unit tests in parallel on multiple hosts / JVMs / OS'
   - Running the same unit test in parallel on multiple hosts / JVMs / OS'
   - Running a multi-node unit test on multiple hosts / JVMs
   - Benchmarking the same unit test in parallel on multiple hosts / JVMs / OS'

#### Use cases for above Meanings
   - Speed up of huge test suites by parallelizing execution of the tests
   - Stress- and / or Load-testing
   - Testing distributed / scalable applications
   - Testing cluster solutions

#### Disclaimer
This project is not sponsored or started by nor affiliated to Hazelcast, Inc in any possible way.
It is a personal, spare-time project started to prevent people from always reinventing the wheel
as we currently do again at Hazelcast, because there is not yet a solution to be ready-to-use.

#### Goals
Jenesis is meant to be a foundation for application or framework vendors to provide a solution for
distributed unit tests. It will not be limited to be used with a single framework underneath but
to provide a common API that is extensible to the vendors needs.
It'll provides a common lifecycle for those tests and an API to hook in your own lifecycle extensions 
that need to take place to set up, execute and tear down tests or tested applications or frameworks.
This is very much what the Maven plugin concept looks like. A lifecycle is a basic set of phases and
the vendor can extend phases to add additional functionality like adding special dependencies or
adding virtual machines to be started up before running tests.

It will also provide a transparent communication layer where you can integrate special serialization
strategies and to communicate between the test runner and the remote test agents. This layer is meant
to be immune to network failures and my communicate on a different network to the general tests.

The current approach is to integrate it with JUnit but possibly TestNG or other frameworks are added
in the future.

#### Non Goals
At the moment the goal is not to build a solution to speed up tests by running standard JUnit tests
in parallel on multiple test slaves. There are interesting / working solutions for that already
(like [Test Load-Balancer TLB](http://test-load-balancer.github.io)) but it might be possible to
implement those features on top of the goals of this proposal.

#### Motivation
A lot of middleware / application frameworks provide support for running distributed unit tests
but there are mostly no frameworks for a general purpose or they seem to have stopped development
a long time ago.
To provide developers and vendors to build reliable but distributed unit tests and applications.

Even if foundation itself is depending on JUnit and is build on Java there are lots of other JVM
based languages that can be used to write tests or if you have an external system but a Java client
you'll be able to build stress tests on top of those solutions.

#### License
As the license of the contributed sourcecode the [Apache License 2](http://www.apache.org/licenses/LICENSE-2.0.html)
is chosen. This means that all code base is permitted to be used in closed source applications and 
can be bundled with those, too. In addition to that it also is possible to mix it up with other 
licenses, open and closed source ones.

For documentation a suitable license will be chosen from the Creative Commons licenses. A possible
candidate would be: [CC BY 4.0](http://creativecommons.org/licenses/by/4.0/).
I permits commercial usage and distributing changed documentations but does not prevent relicensing.
This is pretty much the same terms as AL2 for software but I'm free for suggestions on documentation
licensing :-)

#### Project name
Jenesis is a pun of the "Tree of Life" from the "Book of Genesis" and the typical Java J-beginning
project names. The "tree of life" is common a symbol for knowledge but as a tree it also stands for
stableness (stability) and a long life.

Additionally if a tree grows more branches will come up and so goes to any application. Those branches
need to be tested and for a distributed or scalable application it means the bigger it get over time,
by acquiring the more users for example, the more nodes you eventually will have. Even those needs to
be tested to make sure scaling works for your case and here we are on Jenesis again.

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

#### Status?
I started an implementation a few days ago and all basic ideas seem to work. The "prototype" is
an implementation to distribute Hazelcast tests on multiple JVMs but currently only on the local
machine of the test runner. It forks out JVMs, make them building a cluster and executes cluster
operations on those cluster nodes.

The idea was to have a small version of our heartattack solution and to eventually integrate both
but eventually it will be another highly vendor integrated solution. At Hazelcast we are currently
going this way because we need to have those tests now.

For a more general purpose I want to start this project to give other the chance to benefit from
this. That means there is not yet any sourcecode to share on this project since I want to find out
if there is any interest in such a project or not.

#### You want to contribute?
Due to the fact it's not cool to make those kind of projects alone and since there are a lot of
different requirements I'm looking for people that want to help and work on this together.

To start you can contribute by adding ideas, requirements, recommendations as a ticket to the Github
issue tracker, you can also contribute by adding code via Pull Requests.
In addition to that there might be a mailing list (Google Group) if there is enough interest in the
project itself.
Even commit rights to this repository may be granted for highly interested people.

Let's do this together! If you want to contact me you can do this via Twitter 
[@noctarius2k](https://twitter.com/noctarius2k) or using the mail address me[at]noctariusDOTcom.

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