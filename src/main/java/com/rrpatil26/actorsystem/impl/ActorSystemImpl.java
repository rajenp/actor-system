package com.rrpatil26.actorsystem.impl;

import com.rrpatil26.actorsystem.client.ActorSystem;
import com.rrpatil26.actorsystem.client.ActorSystemExceptions.ActorMailboxFullException;
import com.rrpatil26.actorsystem.client.ActorSystemExceptions.NoSuchActorException;
import com.rrpatil26.actorsystem.client.ActorSystemExceptions.SystemOfflineException;
import com.rrpatil26.actorsystem.client.ActorSystemExceptions.SystemOverloadedException;
import com.rrpatil26.actorsystem.client.Message;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class ActorSystemImpl implements ActorSystem, ActorFactory {

  private final ExecutorService service;
  private final Map<String, Actor> actors;
  private final int maxAllowedActors;
  private final AtomicBoolean isShutdown = new AtomicBoolean(false);

  private static final Logger logger = Logger.getLogger(ActorSystemImpl.class.getCanonicalName());

  public ActorSystemImpl(int size) {
    this.maxAllowedActors = size;
    this.service = Executors.newFixedThreadPool(maxAllowedActors);
    this.actors = new ConcurrentHashMap<>();
  }

  @Override
  public String registerActor(int mailboxSize, Consumer<Message> messageConsumer)
      throws SystemOverloadedException {
    if (actors.size() == maxAllowedActors) {
      logger.warning("Actors pool is full. System is receiving too many new actors.");
      throw new SystemOverloadedException(
          "Can't take more actors. Already at max capacity: " + maxAllowedActors);
    }
    Actor actor = newActor(mailboxSize,
        messageConsumer);
    actors.put(actor.getAddress(), actor);
    service.execute(actor);
    return actor.getAddress();
  }

  @Override
  public boolean sendMessage(String address, Message message)
      throws NoSuchActorException, NoSuchActorException, ActorMailboxFullException {
    // Allow only internal messages (from known/existing actors) if System has been shutdown
    String name = Thread.currentThread().getName();
    if (this.isShutdown() && !actors.containsKey(name)) {
      logger.info("System has been shutdown. Will only serve pending requests");
      throw new SystemOfflineException("System has been shutdown.");
    }
    if (!actors.containsKey(address)) {
      logger.warning(String.format("Actor %s is not known to the system", address));
      throw new NoSuchActorException("Actor not found: " + address);
    }
    Actor actor = actors.get(address);
    return actor.addNewMessage(message);
  }

  @Override
  public Future<Boolean> shutdown() {
    isShutdown.set(true);
    CompletableFuture<Boolean> future = new CompletableFuture<>();
    //Cleanup thread
    new Thread(() -> {
      logger.info("Actor System is shutting down");
      service.shutdown();
      while (actors.values().stream().anyMatch(Actor::hasAnyPendingTask)) {
        try {
          // Give it some time and recheck if it's done
          Thread.sleep(100);
        } catch (InterruptedException e) {
          logger.warning(String.format("Shutdown interrupted. %s", e));
          break;
        }
      }
      for (Actor actor : actors.values()) {
        actor.shutdown();
      }

      try {
        service.awaitTermination(60, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        logger.info("Actor System shutdown timed out.");
        // Mark dirty/forced shutdown
        future.complete(false);
      } finally {
        service.shutdownNow();
        assert service.isTerminated();
      }
      // Mark clean shutdown
      future.complete(true);
      actors.clear();
    }).start();
    return future;
  }

  @Override
  public boolean isShutdown() {
    return isShutdown.get();
  }

  @Override
  public Actor newActor(int mailboxSize, Consumer<Message> messageConsumer) {
    return new ActorImpl(UUID.randomUUID().toString(), messageConsumer,
        new FifoMailbox<Message>(mailboxSize));
  }
}

interface Actor extends Runnable {

  String getAddress();

  boolean addNewMessage(Message message) throws ActorMailboxFullException;

  boolean hasAnyPendingTask();

  void shutdown();
}

class ActorImpl implements Actor {

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
