package com.rrpatil26.actorsystem.common;

import com.rrpatil26.actorsystem.common.ActorSystemExceptions.SystemOverloadedException;
import java.util.function.Consumer;

public interface ActorRegistrationBuilder {

  ActorRegistrationBuilder withMailboxSize(int mailboxSize);

  ActorRegistrationBuilder withMessageHandler(Consumer<Message> messageHandler);

  String register() throws IllegalArgumentException, SystemOverloadedException;
}