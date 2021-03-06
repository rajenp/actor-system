package com.rrpatil26.actorsystem.test;

import com.rrpatil26.actorsystem.client.ActorSystemFactory;
import com.rrpatil26.actorsystem.common.ActorSystem;
import com.rrpatil26.actorsystem.common.ActorSystemExceptions.ActorMailboxFullException;
import com.rrpatil26.actorsystem.common.ActorSystemExceptions.NoSuchActorException;
import com.rrpatil26.actorsystem.common.ActorSystemExceptions.SystemOfflineException;
import com.rrpatil26.actorsystem.common.ActorSystemExceptions.SystemOverloadedException;
import com.rrpatil26.actorsystem.common.Message;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ActorSystemUnitTest {

  private ActorSystem actorSystem;

  @Before
  public void setUp() {
    actorSystem = ActorSystemFactory.newInstance(10);
  }

  @Test
  public void testActorRegistration_success() throws SystemOverloadedException {
    int count = 10;
    while (count-- > 0) {
      Assert.assertNotNull(actorSystem.newActorRegistrationBuilder().withMailboxSize(1)
          .withMessageHandler(message -> {
          }).register());
    }
  }

  @Test(expected = SystemOverloadedException.class)
  public void testActorRegistration_failsWhenCapacityExceeds() throws SystemOverloadedException {
    int count = 100;
    while (count-- > 0) {
      Assert.assertNotNull(actorSystem.registerActor(1, message -> {
      }));
    }
  }

  @Test
  public void testSendMessage_toValidActorAddressWorks()
      throws SystemOverloadedException, ActorMailboxFullException {
    String actorAddress = actorSystem.registerActor(1, message -> {
    });

    Assert.assertTrue(actorSystem.sendMessage(actorAddress, new Message("Hello")));
  }

  @Test(expected = NoSuchActorException.class)
  public void testSendMessage_toInvalidActorAddressFails()
      throws ActorMailboxFullException {
    Assert.assertTrue(actorSystem.sendMessage(UUID.randomUUID().toString(), new Message("Hello")));
  }

  @Test()
  public void testShutdown()
      throws SystemOfflineException, SystemOverloadedException, ExecutionException, InterruptedException, TimeoutException {
    actorSystem.registerActor(1, message -> {
    });
    actorSystem.registerActor(1, message -> {
    });

    Future<Boolean> status = actorSystem.shutdown();
    Assert.assertTrue(actorSystem.isShutdown());
    Assert.assertTrue(status.get(5, TimeUnit.SECONDS));
  }

  @Test(expected = SystemOfflineException.class)
  public void testShutdown_doesNotAcceptNewMessages()
      throws SystemOfflineException, SystemOverloadedException, ActorMailboxFullException {
    String actorAddress = actorSystem.registerActor(1, message -> {
    });
    actorSystem.shutdown();
    Assert.assertTrue(actorSystem.isShutdown());
    actorSystem.sendMessage(actorAddress, new Message("Hello"));
  }

  @Test(expected = ActorMailboxFullException.class)
  public void testSendMessage_actorMailboxFull()
      throws SystemOfflineException, SystemOverloadedException, ActorMailboxFullException {
    String actorAddress = actorSystem.registerActor(1, message -> {
    });

    actorSystem.sendMessage(actorAddress, new Message("One"));
    actorSystem.sendMessage(actorAddress, new Message("Two"));
    actorSystem.sendMessage(actorAddress, new Message("Three"));
  }
}
