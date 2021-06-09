package com.rrpatil26.actorsystem.impl;

import com.rrpatil26.actorsystem.client.Message;
import java.util.function.Consumer;

public interface ActorFactory {

  Actor newActor(int mailboxSize, Consumer<Message> messageConsumer);

}
