---
project.description: Durable State with Akka Persistence enables actors to persist its state for recovery on failure or when migrated within a cluster.
---
# Durable State

## Module info

To use Akka Persistence, add the module to your project:

@@dependency[sbt,Maven,Gradle] {
  bomGroup=com.typesafe.akka bomArtifact=akka-bom_$scala.binary.version$ bomVersionSymbols=AkkaVersion
  symbol1=AkkaVersion
  value1="$akka.version$"
  group=com.typesafe.akka
  artifact=akka-persistence-typed_$scala.binary.version$
  version=AkkaVersion
  group2=com.typesafe.akka
  artifact2=akka-persistence-testkit_$scala.binary.version$
  version2=AkkaVersion
  scope2=test
}

You also have to select journal plugin and optionally snapshot store plugin, see 
@ref:[Persistence Plugins](../persistence-plugins.md).

@@project-info{ projectId="akka-persistence-typed" }

## Introduction

This model of Akka Persistence enables a stateful actor / entity to store the full state afer processing each command instead of using event sourcing. This reduces the conceptual complexity and can be a handy tool for simple use cases. Very much like a CRUD based operation, the API is conceptually simple - a function from current state and incoming command to the next state which replaces the current state in the database. 

```
(State, Command) => State
```

The current state is always stored in the database. Akka Persistence would read that state and store it in memory. After processing of the command is finished, the new state will be stored in the database. The processing of the next command will not start until the state has been successfully stored in the database.

The database specific implementations can be added to existing Akka Persistence plugin implementations, starting with the JDBC plugin. The plugin would serialize the state and store as a blob with the persistenceId as the primary key. 

## Example and core API

Let's start with a simple example that models a counter using an Akka persistent actor. The minimum required for a @apidoc[DurableStateBehavior] is:

Scala
:  @@snip [DurableStatePersistentBehaviorCompileOnly.scala](/akka-persistence-typed/src/test/scala/docs/akka/persistence/typed/DurableStatePersistentBehaviorCompileOnly.scala) { #structure }

Java
:  @@snip [DurableStatePersistentBehaviorTest.java](/akka-persistence-typed/src/test/java/jdocs/akka/persistence/typed/DurableStatePersistentBehaviorTest.java) { #structure }

The first important thing to notice is the `Behavior` of a persistent actor is typed to the type of the `Command`
because this is the type of message a persistent actor should receive. In Akka this is now enforced by the type system.

The components that make up a `DurableStateBehavior` are:

* `persistenceId` is the stable unique identifier for the persistent actor.
* `emptyState` defines the `State` when the entity is first created e.g. a Counter would start with 0 as state.
* `commandHandler` defines how to handle commands and map to appropriate effects e.g. persisting state and replying to actors.

Next we'll discuss each of these in detail.

### PersistenceId

The @apidoc[akka.persistence.typed.PersistenceId] is the stable unique identifier for the persistent actor in the backend
durabe state store.

@ref:[Cluster Sharding](cluster-sharding.md) is typically used together with `DurableStateBehavior` to ensure
that there is only one active entity for each `PersistenceId` (`entityId`).

The `entityId` in Cluster Sharding is the business domain identifier of the entity. The `entityId` might not
be unique enough to be used as the `PersistenceId` by itself. For example two different types of
entities may have the same `entityId`. To create a unique `PersistenceId` the `entityId` should be prefixed
with a stable name of the entity type, which typically is the same as the `EntityTypeKey.name` that
is used in Cluster Sharding. There are @scala[`PersistenceId.apply`]@java[`PersistenceId.of`] factory methods
to help with constructing such `PersistenceId` from an `entityTypeHint` and `entityId`.

The default separator when concatenating the `entityTypeHint` and `entityId` is `|`, but a custom separator
is supported.

The @ref:[Persistence example in the Cluster Sharding documentation](cluster-sharding.md#persistence-example)
illustrates how to construct the `PersistenceId` from the `entityTypeKey` and `entityId` provided by the
`EntityContext`.

A custom identifier can be created with `PersistenceId.ofUniqueId`.  

### Command handler

The command handler is a function with 2 parameters, the current `State` and the incoming `Command`.

A command handler returns an `Effect` directive that defines what state, if any, to persist. 
Effects are created using @java[a factory that is returned via the `Effect()` method] @scala[the `Effect` factory].

The two most commonly used effects are: 

* `persist` will persist state atomically, i.e. all state will be stored or none of them are stored if there is an error
* `none` no state to be persisted, for example a read-only command

More effects are explained in @ref:[Effects and Side Effects](#effects-and-side-effects).

In addition to returning the primary `Effect` for the command `DurableStateBehavior`s can also 
chain side effects that are to be performed after successful persist which is achieved with the `thenRun`
function e.g @scala[`Effect.persist(..).thenRun`]@java[`Effect().persist(..).thenRun`].

### Completing the example

Let's fill in the details of the example.

Commands:

Scala
:  @@snip [DurableStatePersistentBehaviorCompileOnly.scala](/akka-persistence-typed/src/test/scala/docs/akka/persistence/typed/DurableStatePersistentBehaviorCompileOnly.scala) { #command }

Java
:  @@snip [DurableStatePersistentBehaviorTest.java](/akka-persistence-typed/src/test/java/jdocs/akka/persistence/typed/DurableStatePersistentBehaviorTest.java) { #command }

State is a storage for the latest value of the counter.

Scala
:  @@snip [DurableStatePersistentBehaviorCompileOnly.scala](/akka-persistence-typed/src/test/scala/docs/akka/persistence/typed/DurableStatePersistentBehaviorCompileOnly.scala) { #state }

Java
:  @@snip [DurableStatePersistentBehaviorTest.java](/akka-persistence-typed/src/test/java/jdocs/akka/persistence/typed/DurableStatePersistentBehaviorTest.java) { #state }

The command handler handles the commands `Increment`, `IncrementBy` and `GetValue`. 

* `Increment` increments the counter by `1` and persists the updated value as an effect in the State
* `IncrementBy` increments the counter by the value passed to it and persists the updated value as an effect in the State
* `GetValue` retrieves the value of the counter from the State and replies with it to the actor passed in

Scala
:  @@snip [DurableStatePersistentBehaviorCompileOnly.scala](/akka-persistence-typed/src/test/scala/docs/akka/persistence/typed/DurableStatePersistentBehaviorCompileOnly.scala) { #command-handler }

Java
:  @@snip [DurableStatePersistentBehaviorTest.java](/akka-persistence-typed/src/test/java/jdocs/akka/persistence/typed/DurableStatePersistentBehaviorTest.java) { #command-handler }

@scala[These are used to create a `DurableStateBehavior`:]
@java[These are defined in an `DurableStateBehavior`:]

Scala
:  @@snip [DurableStatePersistentBehaviorCompileOnly.scala](/akka-persistence-typed/src/test/scala/docs/akka/persistence/typed/DurableStatePersistentBehaviorCompileOnly.scala) { #behavior }

Java
:  @@snip [DurableStatePersistentBehaviorTest.java](/akka-persistence-typed/src/test/java/jdocs/akka/persistence/typed/DurableStatePersistentBehaviorTest.java) { #behavior }

## Effects and Side Effects

A command handler returns an `Effect` directive that defines what state, if any, to persist. 
Effects are created using @java[a factory that is returned via the `Effect()` method] @scala[the `Effect` factory]
and can be one of: 

* `persist` will persist state atomically, i.e. all states are stored or none of them are stored if there is an error
* `none` no state to be persisted, for example a read-only command
* `unhandled` the command is unhandled (not supported) in current state
* `stop` stop this actor
* `stash` the current command is stashed
* `unstashAll` process the commands that were stashed with @scala[`Effect.stash`]@java[`Effect().stash`]
* `reply` send a reply message to the given `ActorRef`

Note that only one of those can be chosen per incoming command. It is not possible to both persist and say none/unhandled.

In addition to returning the primary `Effect` for the command `DurableStateBehavior`s can also 
chain side effects that are to be performed after successful persist which is achieved with the `thenRun`
function that runs the callback passed to it e.g @scala[`Effect.persist(..).thenRun`]@java[`Effect().persist(..).thenRun`]. 

All `thenRun` registered callbacks are executed sequentially after successful execution of the persist statement
(or immediately, in case of `none` and `unhandled`).

In addition to `thenRun` the following actions can also be performed after successful persist:

* `thenStop` the actor will be stopped
* `thenUnstashAll` process the commands that were stashed with @scala[`Effect.stash`]@java[`Effect().stash`]
* `thenReply` send a reply message to the given `ActorRef`

In the example below, we use a different constructor of `DurableStateBehavior.withEnforcedReplies`, which creates
a `Behavior` for a persistent actor that ensures that replies to commands are not forgotten. Hence it will be
a compilation error if the returned effect isn't a `ReplyEffect`.

Instead of `Increment` we will have a new command `IncrementWithConfirmation` that, along with persistence will also
send an acknowledgement as a reply to the `ActorRef` passed in the command. 

Example of effects and side-effects:

Scala
:  @@snip [DurableStatePersistentBehaviorCompileOnly.scala](/akka-persistence-typed/src/test/scala/docs/akka/persistence/typed/DurableStatePersistentBehaviorCompileOnly.scala) { #effects }

Java
:  @@snip [DurableStatePersistentBehaviorTest.java](/akka-persistence-typed/src/test/java/jdocs/akka/persistence/typed/DurableStatePersistentBehaviorTest.java) { #effects }

Most of the time this will be done with the `thenRun` method on the `Effect` above. You can factor out
common side effects into functions and reuse for several commands. For example:

Scala
:  @@snip [PersistentActorCompileOnlyTest.scala](/akka-persistence-typed/src/test/scala/akka/persistence/typed/scaladsl/PersistentActorCompileOnlyTest.scala) { #commonChainedEffects }

Java
:  @@snip [PersistentActorCompileOnlyTest.java](/akka-persistence-typed/src/test/java/akka/persistence/typed/javadsl/PersistentActorCompileOnlyTest.java) { #commonChainedEffects }

### Side effects ordering and guarantees

Any side effects are executed on an at-most-once basis and will not be executed if the persist fails.

Side effects are not run when the actor is restarted or started again after being stopped.

The side effects are executed sequentially, it is not possible to execute side effects in parallel, unless they
call out to something that is running concurrently (for example sending a message to another actor).

It's possible to execute a side effect before persisting the state, but that can result in that the
side effect is performed but the state is not stored if the persist fails.

## Cluster Sharding and DurableStateBehavior

In a use case where the number of persistent actors needed is higher than what would fit in the memory of one node or
where resilience is important so that if a node crashes the persistent actors are quickly started on a new node and can
resume operations @ref:[Cluster Sharding](cluster-sharding.md) is an excellent fit to spread persistent actors over a
cluster and address them by id.

The `DurableStateBehavior` can then be run as with any plain actor as described in @ref:[actors documentation](actors.md),
but since Akka Persistence is based on the single-writer principle the persistent actors are typically used together
with Cluster Sharding. For a particular `persistenceId` only one persistent actor instance should be active at one time.
Cluster Sharding ensures that there is only one active entity for each id. 

## Accessing the ActorContext

If the @apidoc[DurableStateBehavior] needs to use the @apidoc[typed.*.ActorContext], for example to spawn child actors, it can be obtained by
wrapping construction with `Behaviors.setup`:

Scala
:  @@snip [DurableStatePersistentBehaviorCompileOnly.scala](/akka-persistence-typed/src/test/scala/docs/akka/persistence/typed/DurableStatePersistentBehaviorCompileOnly.scala) { #actor-context }

Java
:  @@snip [BasicPersistentBehaviorTest.java](/akka-persistence-typed/src/test/java/jdocs/akka/persistence/typed/DurableStatePersistentBehaviorTest.java) { #actor-context }

## Changing Behavior

After processing a message, actors are able to return the `Behavior` that is used
for the next message.

As you can see in the above examples this is not supported by persistent actors. Instead, the state is
persisted as an `Effect` by the `commandHandler`. 

The reason a new behavior can't be returned is that behavior is part of the actor's
state and must also carefully be reconstructed during recovery from the persisted state. This would imply
that the state needs to be encoded such that the behavior can also be restored from it. 
That would be very prone to mistakes and thus not allowed in Akka Persistence.

For basic actors you can use the same set of command handlers independent of what state the entity is in.
For more complex actors it's useful to be able to change the behavior in the sense
that different functions for processing commands may be defined depending on what state the actor is in.
This is useful when implementing finite state machine (FSM) like entities.

The next example demonstrates how to define different behavior based on the current `State`. It shows an actor that
represents the state of a blog post. Before a post is started the only command it can process is to `AddPost`.
Once it is started then one can look it up with `GetPost`, modify it with `ChangeBody` or publish it with `Publish`.

The state is captured by:

Scala
:  @@snip [BlogPostEntityDurableState.scala](/akka-persistence-typed/src/test/scala/docs/akka/persistence/typed/BlogPostEntityDurableState.scala) { #state }

Java
:  @@snip [BlogPostEntityDurableState.java](/akka-persistence-typed/src/test/java/jdocs/akka/persistence/typed/BlogPostEntityDurableState.java) { #state }

The commands, of which only a subset are valid depending on the state:

Scala
:  @@snip [BlogPostEntityDurableState.scala](/akka-persistence-typed/src/test/scala/docs/akka/persistence/typed/BlogPostEntityDurableState.scala) { #commands }

Java
:  @@snip [BlogPostEntityDurableState.java](/akka-persistence-typed/src/test/java/jdocs/akka/persistence/typed/BlogPostEntityDurableState.java) { #commands }

@java[The command handler to process each command is decided by the state class (or state predicate) that is
given to the `forStateType` of the `CommandHandlerBuilder` and the match cases in the builders.]
@scala[The command handler to process each command is decided by first looking at the state and then the command.
It typically becomes two levels of pattern matching, first on the state and then on the command.]
Delegating to methods is a good practice because the one-line cases give a nice overview of the message dispatch.

Scala
:  @@snip [BlogPostEntityDurableState.scala](/akka-persistence-typed/src/test/scala/docs/akka/persistence/typed/BlogPostEntityDurableState.scala) { #command-handler }

Java
:  @@snip [BlogPostEntityDurableState.java](/akka-persistence-typed/src/test/java/jdocs/akka/persistence/typed/BlogPostEntityDurableState.java) { #command-handler }

And finally the behavior is created @scala[from the `DurableStateBehavior.apply`]:

Scala
:  @@snip [BlogPostEntityDurableState.scala](/akka-persistence-typed/src/test/scala/docs/akka/persistence/typed/BlogPostEntityDurableState.scala) { #behavior }

Java
:  @@snip [BlogPostEntityDurableState.java](/akka-persistence-typed/src/test/java/jdocs/akka/persistence/typed/BlogPostEntityDurableState.java) { #behavior }

This can be taken one or two steps further by defining the command handlers in the state class as
illustrated in @ref:[command handlers in the state](persistence-style-durable-state.md#command-handlers-in-the-state).

There is also an example illustrating an @ref:[optional initial state](persistence-style-durable-state.md#optional-initial-state).