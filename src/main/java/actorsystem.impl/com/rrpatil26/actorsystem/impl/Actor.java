package com.rrpatil26.actorsystem.impl;

import com.rrpatil26.actorsystem.common.ActorSystemExceptions.ActorMailboxFullException;
import com.rrpatil26.actorsystem.common.Message;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Logger;

interface Actor extends Runnable {

  String getAddress();

  boolean addNewMessage(Message message) throws ActorMailboxFullException;

  boolean hasAnyPendingTask();

  void shutdown();
}

final class ActorImpl implements Actor {

  private static final Logger logger = Logger.getLogger(ActorImpl.class.getCanonicalName());
  private final String address;
  private final Mailbox<Message> mailbox;
  private final Consumer<Message> handler;
  private final AtomicReference<Thread> runnerThread = new AtomicReference<>();
  private final ThreadLocal<Boolean> isBusy = ThreadLocal.withInitial(() -> false);

  ActorImpl(String address, Consumer<Message> handler, Mailbox<Message> mailbox) {
    this.address = address;
    this.handler = handler;
    this.mailbox = mailbox;
    logger.fine(String.format("Actor created: %s", address));
  }

  @Override
  public String getAddress() {
    return address;
  }

  @Override
  public boolean addNewMessage(Message message)
      throws ActorMailboxFullException {
    try {
      if (!mailbox.addToMailbox(message)) {
        logger.warning(
            String.format("Actor %s received messages more that its capacity %s", address,
                mailbox.getMaxCapacity()));
        throw new ActorMailboxFullException(
            "Actor mailbox full. Retry later: " + mailbox.getMaxCapacity());
      }
    } catch (InterruptedException e) {
      throw new ActorMailboxFullException(
          "Something went wrong in delivering message to: " + address);
    }
    return true;
  }

  @Override
  public synchronized boolean hasAnyPendingTask() {
    return mailbox.hasUnread() || isBusy.get();
  }

  private void processMessage() throws InterruptedException {
    // Should block until next message
    Message message = mailbox.getNextMessage();
    if (message != null) {
      isBusy.set(true);
      handler.accept(message);
      isBusy.set(false);
    }
  }

  @Override
  public void shutdown() {
    logger.fine(String.format("Actor shutting down: %s", address));
    // Ensure we are shutting it down once there are no more pending messages
    assert (!mailbox.hasUnread());
    if (runnerThread.get() != null) {
      // Interrupt message processor thread
      runnerThread.get().interrupt();
    }
  }

  @Override
  public void run() {
    // Name runner thread so that we can allow messages from known actors only after shutdown
    Thread.currentThread().setName(address);
    runnerThread.set(Thread.currentThread());
    try {
      //Keep processing one message at a time until Interrupted
      while (!runnerThread.get().isInterrupted()) {
        processMessage();
      }
    } catch (InterruptedException e) {
      logger.warning(
          String.format("Actor %s received an interrupt. Housekeeping now. %s", address, e));
    } finally {
      // If it got interrupted, process all remaining tasks and then stop the thread
      while (mailbox.hasUnread()) {
        try {
          processMessage();
        } catch (InterruptedException e) {
          logger
              .warning(
                  String.format("Something went wrong during housekeeping %s. %s", address, e));
        }
      }
    }
  }
}