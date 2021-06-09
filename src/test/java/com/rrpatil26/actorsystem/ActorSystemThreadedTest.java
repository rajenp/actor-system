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

  private ActorSystem actorSystem;
  private static final int MAX_ACTORS = 10;

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
      actorAddresses.add(actorSystem.registerActor(100, message -> {
        // Track how many messages processed by the system
        countOfMessagesProcessed.getAndIncrement();
      }));
    }

    // Act
    ExecutorService scheduler = Executors.newCachedThreadPool();
    int numberOfMessagesToSend = Math.abs(new Random().nextInt(1000));
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

    int countOfSuccessfullySentMessages = 0;
    for (Future<Boolean> future : futures) {
      if (future.get()) {
        countOfSuccessfullySentMessages++;
      }
    }

    //Assert/Verify - sending all succeeded
    Assert.assertEquals(countOfSuccessfullySentMessages, numberOfMessagesToSend);

    // Shutdown and ensure all messages get processed - even if are in the queue
    Future<Boolean> status = actorSystem.shutdown();
    Assert.assertTrue(status.get());
    // Ensure total messages processed by system match the sent messages count
    Assert.assertEquals(countOfMessagesProcessed.get(), countOfSuccessfullySentMessages);
  }

}
