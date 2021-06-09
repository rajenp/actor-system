package com.rrpatil26.actorsystem.common;

import com.rrpatil26.actorsystem.common.ActorSystemExceptions.ActorMailboxFullException;
import com.rrpatil26.actorsystem.common.ActorSystemExceptions.NoSuchActorException;
import com.rrpatil26.actorsystem.common.ActorSystemExceptions.SystemOfflineException;
import com.rrpatil26.actorsystem.common.ActorSystemExceptions.SystemOverloadedException;
import com.rrpatil26.actorsystem.common.Message;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public interface ActorSystem {

  /**
   * Registers new Actor into the system to collect and process specific messages.
   *
   * @param mailboxSize Number of messages actors inbox can handle at a time
   * @param messageConsumer Client callback to process the message received by this Actor
   * @return String representation of UUID as a unique address assigned to this new Actor
   * @throws SystemOverloadedException If System is already loaded and have no capacity left
   */
  String registerActor(int mailboxSize, Consumer<com.rrpatil26.actorsystem.common.Message> messageConsumer)
      throws SystemOverloadedException;

  /**
   * Sends a message to an Actor in the system identified by given unique address.
   *
   * @param address Target/receiver Actors Unique address
   * @param message The message payload that will be delivered to actors mailbox
   * @return True if message successfully delivered to target Actor, false otherwise
   * @throws ActorMailboxFullException when Actor Mailbox is full
   * @throws SystemOfflineException when System has been shutdown
   * @throws NoSuchActorException when no such actor with that address
   */
  boolean sendMessage(String address, Message message)
      throws NoSuchActorException, ActorMailboxFullException, SystemOfflineException;

  /**
   * Shuts down Actor System such that stops accepting new messages/actors and only completes
   * previously scheduled messages for existing actors.
   *
   * @return Future<Boolean> With status true if clean shutdown or false if it took longer than
   * 1minute and forced shutdown was needed
   */
  Future<Boolean> shutdown();

  /**
   * @return True if System has been shutdown.
   */
  boolean isShutdown();
}

