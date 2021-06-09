package com.rrpatil26.actorsystem.common;

import java.util.NoSuchElementException;

public final class ActorSystemExceptions {

  public static class NoSuchActorException extends NoSuchElementException {

    public NoSuchActorException(String message) {
      super(message);
    }
  }

  public static class SystemOverloadedException extends Exception {

    public SystemOverloadedException(String message) {
      super(message);
    }
  }


  public static class ActorMailboxFullException extends Exception {

    public ActorMailboxFullException(String message) {
      super(message);
    }
  }

  public static class SystemOfflineException extends IllegalStateException {

    public SystemOfflineException(String message) {
      super(message);
    }
  }
}
