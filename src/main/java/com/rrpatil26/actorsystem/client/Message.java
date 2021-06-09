package com.rrpatil26.actorsystem.client;

public class Message {

  String messageBody;

  public Message(String messageBody) {
    this.messageBody = messageBody;
  }

  @Override
  public String toString() {
    return "Message{" +
        "messageBody='" + messageBody + '\'' +
        '}';
  }
}
