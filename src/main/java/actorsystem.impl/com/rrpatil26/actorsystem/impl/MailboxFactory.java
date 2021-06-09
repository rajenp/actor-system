package com.rrpatil26.actorsystem.impl;

public interface MailboxFactory<T> {

  Mailbox<T> newMailbox(int mailboxSize);

}
