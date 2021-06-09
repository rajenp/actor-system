package com.rrpatil26.actorsystem.common;

public class Message<T> {

  private final T messageBody;

  public Message(T messageBody) {
    this.messageBody = messageBody;
  }

  public T getMessageBody() {
    return messageBody;
  }

  @Override
  public String toString() {
    return "Message{" +
        "messageBody='" + messageBody + '\'' +
        '}';
  }
}
