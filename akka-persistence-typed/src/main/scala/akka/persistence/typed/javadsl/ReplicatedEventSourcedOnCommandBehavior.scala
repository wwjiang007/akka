/*
 * Copyright (C) 2020-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.typed.javadsl

import akka.actor.typed.BackoffSupervisorStrategy
import akka.actor.typed.Behavior
import akka.actor.typed.TypedActorContext
import akka.annotation.InternalApi
import akka.persistence.typed.internal.ReplicationContextImpl

import java.util.Optional

/**
 * Base class for replicated event sourced behaviors for projects built with Java 17 or newer where message handling
 * can be done using switch pattern match.
 *
 * For building replicated event sourced actors with Java versions before 17, see [[ReplicatedEventSourcedBehavior]]
 */
abstract class ReplicatedEventSourcedOnCommandBehavior[Command, Event, State](
    replicationContext: ReplicationContext,
    onPersistFailure: Optional[BackoffSupervisorStrategy])
    extends EventSourcedOnCommandBehavior[Command, Event, State](replicationContext.persistenceId, onPersistFailure) {

  def this(replicationContext: ReplicationContext) = this(replicationContext, Optional.empty())

  /**
   * Override and return false to disable events being published to the system event stream as
   * [[akka.persistence.typed.PublishedEvent]] after they have been persisted.
   */
  def withEventPublishing: Boolean = true

  protected def getReplicationContext(): ReplicationContext = replicationContext

  /**
   * INTERNAL API: DeferredBehavior init, not for user extension
   */
  @InternalApi override def apply(context: TypedActorContext[Command]): Behavior[Command] = {
    createEventSourcedBehavior()
    // context not user extendable so there should never be any other impls
      .withReplication(replicationContext.asInstanceOf[ReplicationContextImpl])
      .withEventPublishing(withEventPublishing)
  }
}
