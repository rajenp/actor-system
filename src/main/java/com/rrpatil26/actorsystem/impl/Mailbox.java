package com.rrpatil26.actorsystem.impl;

import com.rrpatil26.actorsystem.client.ActorSystemExceptions.ActorMailboxFullException;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

interface Mailbox<T> {

  int getSize();

  boolean hasUnread();

  T getNextMessage();

  boolean addToMailbox(T message) throws ActorMailboxFullException;
}

class FifoMailbox<T> implements Mailbox<T> {

  private int maxCapacity;

  private final Queue<T> queue;

  FifoMailbox(int size) {
    this(new ArrayBlockingQueue<>(size, true));
    maxCapacity = size;
  }

  FifoMailbox(Queue<T> queue) {
    this.queue = queue;
  }

  @Override
  public int getSize() {
    return this.maxCapacity;
  }

  @Override
  public boolean hasUnread() {
    return !queue.isEmpty();
  }

  @Override
  public T getNextMessage() {
    return queue.poll();
  }

  @Override
  public boolean addToMailbox(T message) throws ActorMailboxFullException {
    if (queue.size() == maxCapacity) {
      throw new ActorMailboxFullException("Mailbox is full. Can't take anymore messages");
    }
    return queue.offer(message);
  }
}