package com.rrpatil26.actorsystem.impl;

import com.rrpatil26.actorsystem.common.ActorSystem;
import com.rrpatil26.actorsystem.common.ActorSystemExceptions.ActorMailboxFullException;
import com.rrpatil26.actorsystem.common.ActorSystemExceptions.NoSuchActorException;
import com.rrpatil26.actorsystem.common.ActorSystemExceptions.SystemOfflineException;
import com.rrpatil26.actorsystem.common.ActorSystemExceptions.SystemOverloadedException;
import com.rrpatil26.actorsystem.common.Message;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ActorSystemImpl implements ActorSystem, ActorFactory, MailboxFactory<Message> {

  private static final Logger logger = Logger.getLogger(ActorSystemImpl.class.getCanonicalName());
  private final ExecutorService service;
  private final Map<String, Actor> actors;
  private final int maxAllowedActors;
  private final AtomicBoolean isShutdown = new AtomicBoolean(false);

  public ActorSystemImpl(int size) {
    this.maxAllowedActors = size;
    this.service = Executors.newFixedThreadPool(maxAllowedActors);
    this.actors = new ConcurrentHashMap<>();
  }

  @Override
  public String registerActor(int mailboxSize, Consumer<Message> messageConsumer)
      throws SystemOverloadedException {
    if (isShutdown()) {
      logger.info("System has been shutdown.");
      throw new SystemOfflineException("System has been shutdown.");
    }
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
      throws NoSuchActorException, ActorMailboxFullException {
    // Allow only internal messages (from known/existing actors) if System has been shutdown
    boolean isInternalMessage = actors.containsKey(Thread.currentThread().getName());
    if (this.isShutdown() && !isInternalMessage) {
      logger.warning("System has been shutdown. Will only serve pending requests");
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
    if (isShutdown.get()) {
      return CompletableFuture.completedFuture(service.isShutdown());
    }
    isShutdown.set(true);
    service.shutdown();
    logger.info("Actor System is shutting down");
    CompletableFuture<Boolean> future = new CompletableFuture<>();

    //Cleanup thread
    try {
      Thread cleaner = new Thread(awaitBusyActorsAndShutdownCleaner(future));
      cleaner.start();
      // What if cleaner gets stuck? Wait 30sec and kill
      cleaner.join(30000);
    } catch (InterruptedException e) {
      logger.info("Cleaner thread took longer. Terminating now.");
    } finally {
      future.complete(false);
    }
    return future;
  }

  @Override
  public boolean isShutdown() {
    return isShutdown.get();
  }

  @Override
  public Actor newActor(int mailboxSize, Consumer<Message> messageConsumer) {
    return new ActorImpl(UUID.randomUUID().toString(), messageConsumer,
        newMailbox(mailboxSize));
  }

  @Override
  public Mailbox<Message> newMailbox(int mailboxSize) {
    return new FifoMailbox<>(mailboxSize);
  }

  private Runnable awaitBusyActorsAndShutdownCleaner(CompletableFuture<Boolean> status) {
    return () -> {
      try {
        synchronized (actors) {
          List<Actor> busyActors = actors.values().stream().filter(Actor::hasAnyPendingTask)
              .collect(
                  Collectors.toUnmodifiableList());
          int attempts = 4;
          while (busyActors.size() > 0 && attempts-- != 0) {
            logger.info(String.format("[%d]: Some actors are still busy:", attempts) + busyActors);
            try {
              // Give it some time and recheck if it's done
              Thread.sleep(100);
            } catch (InterruptedException e) {
              logger.warning(String.format("Shutdown interrupted. %s", e));
              break;
            }
          }
          service.awaitTermination(10000, TimeUnit.MILLISECONDS);
          for (Actor actor : actors.values()) {
            actor.shutdown();
          }
        }
        // Mark clean shutdown
        status.complete(true);
      } catch (InterruptedException e) {
        logger.info("Actor System shutdown timed out.");
        // Mark dirty/forced shutdown
      } finally {
        actors.clear();
        service.shutdownNow();
        status.complete(service.isTerminated());
      }
    };
  }
}


