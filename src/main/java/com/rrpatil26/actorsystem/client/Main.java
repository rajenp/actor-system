package com.rrpatil26.actorsystem.client;

import com.rrpatil26.actorsystem.client.ActorSystemExceptions.ActorMailboxFullException;
import com.rrpatil26.actorsystem.client.ActorSystemExceptions.SystemOverloadedException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Main {

  public static void main(String[] args)
      throws SystemOverloadedException, ActorMailboxFullException {
    ActorSystem actorSystem = ActorSystemFactory.newInstance(10);

    // Add printer
    String printerAddress = actorSystem.registerActor(5, message -> {
      // Printer
      System.out.println("Printing message: " + message);
    });

    // Add logger
    String loggerAddress = actorSystem.registerActor(2, message -> {
      // Logger
      System.out.println("Logging message: " + message);
    });

    // Add decrypter
    String decrypterAddress = actorSystem.registerActor(2, message -> {
      // Decrypt
      System.out.println("Decrypting message: " + message);
      try {
        actorSystem.sendMessage(loggerAddress, new Message("decrypt(" + message.messageBody + ")"));
        actorSystem.sendMessage(printerAddress, new Message("print(" + message.messageBody + ")"));
      } catch (ActorMailboxFullException e) {
        // Handle what to do when actor couldn't process the message
        e.printStackTrace();
      }
    });

    String base64encoder = actorSystem.registerActor(2, message -> {
      // Encode
      String encoded = Base64.getEncoder().encodeToString(message.messageBody.getBytes(
          StandardCharsets.UTF_16));
      System.out
          .println("Encoded: " + encoded);
      try {
        // Try to late deliver this after system might have been asked to shutdown. This should work
        Thread.sleep(10000);
        actorSystem.sendMessage(loggerAddress, new Message("encoded(" + encoded + ")"));
      } catch (ActorMailboxFullException | InterruptedException e) {
        // Handle what to do when actor couldn't process the message
        e.printStackTrace();
      }
    });

    String base64decoder = actorSystem.registerActor(2, message -> {
      // Encode
      String decoded = new String(Base64.getDecoder().decode(message.messageBody),
          StandardCharsets.UTF_16);
      System.out
          .println("Decoded: " + decoded);
      try {
        actorSystem.sendMessage(loggerAddress, new Message("decoded(" + decoded + ")"));
      } catch (ActorMailboxFullException e) {
        // Handle what to do when actor couldn't process the message
        e.printStackTrace();
      }
    });

    try {
      actorSystem.sendMessage(decrypterAddress, new Message("ABC"));
      actorSystem.sendMessage(base64encoder, new Message("Rajendra"));
      actorSystem.sendMessage(base64decoder,
          new Message(Base64.getEncoder().encodeToString("Rajendra".getBytes(
              StandardCharsets.UTF_16))));

    } catch (ActorMailboxFullException e) {
      e.printStackTrace();
    } finally {
      actorSystem.shutdown();
    }
    // This should fail and report that it can't accept new messages
    actorSystem.sendMessage(printerAddress, new Message("This is the message after shutdown"));
  }
}
