package com.rrpatil26.actorsystem.common;

public class Message<T> {

  private final T payload;

  public Message(T payload) {
    this.payload = payload;
  }

  public T getPayload() {
    return payload;
  }

  @Override
  public String toString() {
    return "Message{" +
        "payload='" + payload + '\'' +
        '}';
  }
}
