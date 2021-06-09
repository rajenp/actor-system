package com.rrpatil26.actorsystem;

import com.rrpatil26.actorsystem.client.ActorSystem;
import com.rrpatil26.actorsystem.client.ActorSystemExceptions.SystemOverloadedException;
import com.rrpatil26.actorsystem.client.ActorSystemFactory;
import com.rrpatil26.actorsystem.client.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ActorSystemThreadedTest {

  private static final int MAX_ACTORS = 10;
  private static final int MAX_MAILBOX_SIZE = 100;
  private static final int MESSAGES_UPPER_BOUND = 100000;
  private ActorSystem actorSystem;

  @Before
  public void setUp() {
    actorSystem = ActorSystemFactory.newInstance(MAX_ACTORS);
  }

  @Test
  public void testActorSystem_underMultiThreadedScenario()
      throws SystemOverloadedException, ExecutionException, InterruptedException {
    // Setup
    List<String> actorAddresses = new ArrayList<>();
    AtomicInteger countOfMessagesProcessed = new AtomicInteger();
    for (int i = 0; i < MAX_ACTORS; i++) {
      actorAddresses.add(actorSystem.registerActor(MAX_MAILBOX_SIZE, message -> {
        // Track how many messages processed by the system
        countOfMessagesProcessed.getAndIncrement();
      }));
    }

    // Act
    ExecutorService scheduler = Executors.newCachedThreadPool();
    int numberOfMessagesToSend = Math.abs(new Random().nextInt(MESSAGES_UPPER_BOUND));
    List<Future<Boolean>> futures = new ArrayList<>();
    for (int i = 0; i < numberOfMessagesToSend; i++) {
      // Distribute messages randomly or evenly
      String addr = actorAddresses.get(Math.abs(new Random().nextInt()) % actorAddresses.size());
      Callable<Boolean> messengerTask = () -> {
        try {
          return actorSystem.sendMessage(addr, new Message("Hello to: " + addr));
        } catch (Exception e) {
          //e.printStackTrace();
        }
        return false;
      };
      futures.add(scheduler.submit(messengerTask));
    }
    scheduler.shutdown();

    // Collect how many messages went through successfully
    int countOfSuccessfullySentMessages = 0;
    for (Future<Boolean> future : futures) {
      if (future.get()) {
        countOfSuccessfullySentMessages++;
      }
    }

    //Assert/Verify - potentially sending all messages succeed (in an ideal situation)
    Assert.assertTrue(countOfSuccessfullySentMessages <= numberOfMessagesToSend);

    // Shutdown and ensure all messages get processed - even if are in the queue
    Future<Boolean> status = actorSystem.shutdown();
    Assert.assertTrue(status.get());
    // No messages should be lost or unprocessed. Messages processed == messages sent count
    Assert.assertEquals(countOfMessagesProcessed.get(), countOfSuccessfullySentMessages);
  }

}
