package com.rrpatil26.actorsystem.client;

import com.rrpatil26.actorsystem.impl.ActorSystemImpl;

public final class ActorSystemFactory {

  public static ActorSystem newInstance(int size) {
    return new ActorSystemImpl(size);
  }
}
