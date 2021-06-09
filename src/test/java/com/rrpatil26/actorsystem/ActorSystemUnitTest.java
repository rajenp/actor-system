package com.rrpatil26.actorsystem;

import com.rrpatil26.actorsystem.client.ActorSystem;
import com.rrpatil26.actorsystem.client.ActorSystemExceptions.ActorMailboxFullException;
import com.rrpatil26.actorsystem.client.ActorSystemExceptions.NoSuchActorException;
import com.rrpatil26.actorsystem.client.ActorSystemExceptions.SystemOfflineException;
import com.rrpatil26.actorsystem.client.ActorSystemExceptions.SystemOverloadedException;
import com.rrpatil26.actorsystem.client.ActorSystemFactory;
import com.rrpatil26.actorsystem.client.Message;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
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
      Assert.assertNotNull(actorSystem.registerActor(1, message -> {
      }));
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
      throws SystemOfflineException, SystemOverloadedException, ActorMailboxFullException, ExecutionException, InterruptedException {
    String actorAddress = actorSystem.registerActor(1, message -> {
    });
    String actor2Address = actorSystem.registerActor(1, message -> {
    });

    Future<Boolean> status = actorSystem.shutdown();
    Assert.assertTrue(actorSystem.isShutdown());
    Assert.assertTrue(status.get());
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
