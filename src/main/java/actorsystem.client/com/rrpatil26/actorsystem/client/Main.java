package com.rrpatil26.actorsystem.client;

import com.rrpatil26.actorsystem.common.ActorSystem;
import com.rrpatil26.actorsystem.common.ActorSystemExceptions.ActorMailboxFullException;
import com.rrpatil26.actorsystem.common.ActorSystemExceptions.SystemOverloadedException;
import com.rrpatil26.actorsystem.common.Message;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Main {

  public static void main(String[] args)
      throws SystemOverloadedException, ActorMailboxFullException {
    ActorSystem actorSystem = ActorSystemFactory.newInstance(10);

    // Add printer
    String printerAddress = actorSystem
        .newActorRegistrationBuilder()
        .withMailboxSize(5)
        .withMessageHandler(System.out::println).register();

    // Add logger
    String loggerAddress = actorSystem
        .newActorRegistrationBuilder()
        .withMailboxSize(2)
        .withMessageHandler(message -> {
          // Logger
          System.out.println("Logging message: " + message);
        }).register();

    // Add decrypter
    String decrypterAddress = actorSystem
        .newActorRegistrationBuilder()
        .withMailboxSize(2)
        .withMessageHandler(message -> {
          // Decrypt
          System.out.println("Decrypting message: " + message);
          try {
            actorSystem.sendMessage(loggerAddress, new Message<String>(
                "decrypt(" + message.getPayload() + ")"));
            actorSystem.sendMessage(printerAddress, new Message<String>(
                "print(" + message.getPayload() + ")"));
          } catch (ActorMailboxFullException e) {
            // Handle what to do when actor couldn't process the message
            e.printStackTrace();
          }
        }).register();

    String base64encoder = actorSystem.newActorRegistrationBuilder().withMailboxSize(3)
        .withMessageHandler(message -> {
          String payload = (String) message.getPayload();
          // Encode
          String encoded = Base64.getEncoder().encodeToString(payload.getBytes(
              StandardCharsets.UTF_16));
          System.out
              .println("Encoded: " + payload + " to: " + encoded);
          try {
            // Try to late deliver this after system might have been asked to shutdown. This should work
            Thread.sleep(10000);
            actorSystem.sendMessage(loggerAddress,
                new Message<>("This is delayed and internal message."));
          } catch (ActorMailboxFullException | InterruptedException e) {
            // Handle what to do when actor couldn't process the message
            e.printStackTrace();
          }
        }).register();

    String base64decoder = actorSystem.newActorRegistrationBuilder().withMailboxSize(2)
        .withMessageHandler(message -> {
          String payload = (String) message.getPayload();
          // Encode
          String decoded = new String(Base64.getDecoder().decode(payload),
              StandardCharsets.UTF_16);
          System.out
              .println("Decoded: " + payload + " to: " + decoded);
          try {
            actorSystem.sendMessage(loggerAddress,
                new Message<>("decoded(" + decoded + ")"));
          } catch (ActorMailboxFullException e) {
            // Handle what to do when actor couldn't process the message
            e.printStackTrace();
          }
        }).register();

    try {
      actorSystem
          .sendMessage(decrypterAddress, new Message<>("ABC"));
      actorSystem
          .sendMessage(base64encoder, new Message<>("Rajendra"));
      actorSystem.sendMessage(base64decoder,
          new Message<>(
              Base64.getEncoder().encodeToString("Rajendra".getBytes(
                  StandardCharsets.UTF_16))));

    } catch (ActorMailboxFullException e) {
      e.printStackTrace();
    } finally {
      actorSystem.shutdown();
    }
    // This should fail and report that it can't accept new messages
    actorSystem
        .sendMessage(printerAddress, new Message<>("This is the message after shutdown"));
  }
}
