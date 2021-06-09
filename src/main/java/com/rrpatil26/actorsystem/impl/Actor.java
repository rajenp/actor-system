package com.rrpatil26.actorsystem.impl;

import com.rrpatil26.actorsystem.client.ActorSystemExceptions.ActorMailboxFullException;
import com.rrpatil26.actorsystem.client.Message;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.logging.Logger;

interface Actor extends Runnable {

  String getAddress();

  boolean addNewMessage(Message message) throws ActorMailboxFullException;

  boolean hasAnyPendingTask();

  void shutdown();
}

final class ActorImpl implements Actor {

  private final String address;
  private final Mailbox<Message> mailbox;
  private final Consumer<Message> handler;

  private final Lock lock;
  private final Condition hasNewMessageInTheMailbox;
  private final AtomicReference<Thread> runnerThread = new AtomicReference<>();

  private static final Logger logger = Logger.getLogger(ActorImpl.class.getCanonicalName());

  ActorImpl(String address, Consumer<Message> handler, Mailbox<Message> mailbox) {
    this.address = address;
    this.handler = handler;

    this.mailbox = mailbox;
    lock = new ReentrantLock();

    hasNewMessageInTheMailbox = lock.newCondition();
    logger.fine(String.format("Actor created: %s", address));
  }

  @Override
  public String getAddress() {
    return address;
  }

  @Override
  public boolean addNewMessage(Message message)
      throws ActorMailboxFullException {
    lock.lock();
    try {
      // Adding new message can fail if actor mailbox is full
      if (!mailbox.addToMailbox(message)) {
        logger.warning(
            String.format("Actor %s received messages more that its capacity %s", address,
                mailbox.getSize()));
        throw new ActorMailboxFullException(
            "Actor mailbox full. Retry later: " + mailbox.getSize());
      }
      hasNewMessageInTheMailbox.signal();
    } finally {
      lock.unlock();
    }
    return true;
  }

  @Override
  public synchronized boolean hasAnyPendingTask() {
    return mailbox.hasUnread();
  }

  private void processMessage() throws InterruptedException {
    lock.lock();
    try {
      // Wait until there is any new message in the mailbox
      while (!mailbox.hasUnread()) {
        try {
          hasNewMessageInTheMailbox.await();
        } catch (InterruptedException ex) {
          throw new InterruptedException();
        }
      }
      Message message = mailbox.getNextMessage();
      if (message != null) {
        handler.accept(message);
      }
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void shutdown() {
    // Ensure we are shutting it down once there are no more pending messages
    assert (!mailbox.hasUnread());
    if (runnerThread.get() != null) {
      // Stop message processor thread interrupt
      runnerThread.get().interrupt();
      logger.fine(String.format("Actor shutting down: %s", address));
    }
  }

  @Override
  public void run() {
    // Name know runner so that we can allow pending messages even after shutdown is initiated
    Thread.currentThread().setName(address);
    runnerThread.set(Thread.currentThread());
    try {
      //Keep processing one message at a time until shutdown
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