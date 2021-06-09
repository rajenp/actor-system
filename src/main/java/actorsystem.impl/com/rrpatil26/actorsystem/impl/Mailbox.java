package com.rrpatil26.actorsystem.impl;

import com.rrpatil26.actorsystem.common.ActorSystemExceptions.ActorMailboxFullException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

interface Mailbox<T> {

  int getMaxCapacity();

  boolean hasUnread();

  T getNextMessage() throws InterruptedException;

  boolean addToMailbox(T message) throws ActorMailboxFullException, InterruptedException;
}

/**
 * Thread safe FIFO Mailbox implementation using ArrayBlockingQueue.
 */
class FifoMailbox<T> implements Mailbox<T> {

  private final BlockingQueue<T> queue;
  private int maxCapacity;

  FifoMailbox(int size) {
    this(new ArrayBlockingQueue<>(size, true));
    maxCapacity = size;
  }

  FifoMailbox(BlockingQueue<T> queue) {
    this.queue = queue;
  }

  @Override
  public int getMaxCapacity() {
    return this.maxCapacity;
  }

  @Override
  public boolean hasUnread() {
    return !queue.isEmpty();
  }

  @Override
  public T getNextMessage() throws InterruptedException {
    return queue.take();
  }

  @Override
  public boolean addToMailbox(T message) throws ActorMailboxFullException, InterruptedException {
    if (queue.size() == maxCapacity) {
      throw new ActorMailboxFullException("Mailbox is full. Can't take anymore messages");
    }
    queue.put(message);
    return true;
  }
}